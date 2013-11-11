package com.gmail.dengtao.joe.transport.session.impl;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

import com.gmail.dengtao.joe.transport.filter.FilterChain;

/**
 * normal tcp socket session implementation
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public class SocketSession extends AbstractSession {

	// send data list.
	private LinkedList<Packet> sendList = new LinkedList<Packet>();
	private final Object sendLock = new Object();
	
	private SocketChannel channel;
	private Socket socket;
	private Selector selector;
	private FilterChain filterChain;
	
	private boolean needPush = false;
	
	public SocketSession() {
		long now = System.currentTimeMillis();
		createTime = now;
		activeTime = now;
		latestReadTime = now;
		latestWriteTime = now;
	}

	public SocketSession(final SocketChannel channel, final Selector selector, final FilterChain filterChain) {
		this();
		this.channel = channel;
		if (channel != null) socket = channel.socket();
		else socket = null;
		this.selector = selector;
		if (channel != null && channel.socket() != null && !channel.socket().isClosed()) {
			localSocketAddress = channel.socket().getLocalSocketAddress();
			remoteSocketAddress = channel.socket().getRemoteSocketAddress();
		} else {
			localSocketAddress = null;
			remoteSocketAddress = null;
		}
		this.filterChain = filterChain;
	}
	
	public SocketSession(final SocketChannel channel, final Selector selector, final FilterChain filterChain, final SocketAddress localSocketAddress, final SocketAddress remoteSocketAddress) {
		this();
		this.channel = channel;
		if (channel != null) socket = channel.socket();
		else socket = null;
		this.selector = selector;
		this.localSocketAddress = localSocketAddress;
		this.remoteSocketAddress = remoteSocketAddress;
		this.filterChain = filterChain;
	}

	public SocketSession(final SocketChannel channel, final Selector selector, final FilterChain filterChain, final SocketAddress remoteSocketAddress) {
		this();
		this.channel = channel;
		if (channel != null) socket = channel.socket();
		else socket = null;
		this.selector = selector;
		this.remoteSocketAddress = remoteSocketAddress;
		this.filterChain = filterChain;
	}

	/**
	 * Set the SocketChannel of this session
	 * @param channel
	 * @since 1.0
	 */
	public void setChannel(final SocketChannel channel) {
		this.channel = channel;
		if (channel != null) socket = channel.socket();
		else socket = null;
	}
	
	/**
	 * Get current session's socket
	 * @return
	 */
	public Socket getSocket() {
		return socket;
	}

	@Override
	public void send(final Object data) {
		if (!open) {
			throw new IllegalStateException("this session is not open!");
		}
		filterChain.fireSendData(this, data);
	}

	@Override
	public void sendData(Object data) {
		if (data != null) {
			Packet packet = new Packet(data);
			synchronized (sendLock) {
				sendList.add(packet);
				if (!needPush) {
					try {
						// regist write & read
						channel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
						selector.wakeup();
					} catch (Exception e) {
						sendList.poll();
						// some other I/O error occurs
						filterChain.fireDataNotSent(this, packet.getData());
						filterChain.fireExceptionCaught(this, e);
					}
				}
			}
		}
	}
	
	@Override
	public void pushData() {
		if (!open) {
			throw new IllegalStateException("this session is not open!");
		}
		synchronized (sendLock) {
			if (!sendList.isEmpty()) {
				// Retrieves, but does not remove, the first element of this list, or returns null if this list is empty.
				Packet packet = sendList.peekFirst();	
				ByteBuffer buf = packet.getBuf();
				try {
					int len = write(buf);
					if (len < 0) { 
						sendList.poll();
						// network error
						filterChain.fireDataNotSent(this, packet.getData());
						filterChain.fireExceptionCaught(this, new IOException("Network Error, Send data failed!"));
						this.close();
					} else {
						if (!buf.hasRemaining()) {
							// Retrieves and removes the head (first element) of this list
							sendList.poll();
							filterChain.fireDataSent(this, packet.getData());
						}
					}
				} catch (IOException e) {
					sendList.poll();
					// some other I/O error occurs
					filterChain.fireDataNotSent(this, packet.getData());
					filterChain.fireExceptionCaught(this, e);
				}
			}
			if (sendList.isEmpty()) {
				needPush = false;
				try {
					// regist only read
					channel.register(selector, SelectionKey.OP_READ);
					selector.wakeup();
				} catch (ClosedChannelException e) {
					/* Nothing... */
				} catch (Exception e) {
					filterChain.fireExceptionCaught(this, e);
				}
			}
		}
	}
	
	/**
	 * Write byte[] to remote.
	 * @param data bts need to be sent
	 * @return write data length
	 * @throws Exception
	 * @since 1.0
	 */
	private int write(final ByteBuffer buf) throws IOException {
		active();	// active this session
		
		// calculate the send buffer len.
		int bufLen = buf.limit() - buf.position();
		int _bufLen = sendBufferSize < bufLen ? sendBufferSize : bufLen;
		
		// create send buffer
		byte[] bts = new byte[_bufLen];
		buf.get(bts);
		ByteBuffer _buf = ByteBuffer.allocate(_bufLen);
		_buf.put(bts);
		_buf.flip();
		
		// set write time
		setLatestWriteTime(System.currentTimeMillis());
		
		// send data
		return channel.write(_buf);
	}

	@Override
	public void open() {
		if (open) {
			throw new IllegalStateException("Session has already been opened.");
		}
		open = true;
		closed = false;
		filterChain.fireSessionOpened(this);
	}

	@Override
	public void close() {
		if (!closed) {
			closed = true;
			open = false;
			sendList.clear();
			try {
				// close channel
				if (channel != null && channel.isOpen()) channel.close();
				// fire session closed
				filterChain.fireSessionClosed(this);
			} catch (Exception e) {
				filterChain.fireExceptionCaught(this, e);
			}
		}
	}
	
	@Override
	public String toString() {
		return "SocketSession [local=" + localSocketAddress
				+ ", remote=" + remoteSocketAddress
				+ ", open=" + open
				+ ", closed=" + closed
				+ ", create=" + createTime
				+ ", active=" + activeTime
				+ ", read=" + latestReadTime
				+ ", write=" + latestWriteTime
				+ ", readIdle=" + idleTimeForRead
				+ ", writeIdle=" + idleTimeForWrite + "]";
	}

}
