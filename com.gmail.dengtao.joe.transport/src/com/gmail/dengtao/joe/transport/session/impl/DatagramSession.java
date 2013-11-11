package com.gmail.dengtao.joe.transport.session.impl;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.LinkedList;

import com.gmail.dengtao.joe.transport.Pushable;
import com.gmail.dengtao.joe.transport.filter.FilterChain;

/**
 * datagram's session implementation
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public class DatagramSession extends AbstractSession {
	
	// send data list.
	private LinkedList<Packet> sendList = new LinkedList<Packet>();
	private final Object sendLock = new Object();
		
	private DatagramSocket socket;

	private Pushable pushable;
	private DatagramChannel channel;
	private FilterChain filterChain;

	public DatagramSession() {
		long now = System.currentTimeMillis();
		createTime = now;
		activeTime = now;
		latestReadTime = now;
		latestWriteTime = now;
	}
	
	public DatagramSession(final DatagramChannel channel, final Pushable pushable, final FilterChain filterChain) {
		this();
		this.channel = channel;
		if (channel != null) socket = channel.socket();
		else socket = null;
		this.pushable = pushable;
		if (channel != null && channel.socket() != null && !channel.socket().isClosed()) {
			localSocketAddress = channel.socket().getLocalSocketAddress();
			remoteSocketAddress = channel.socket().getRemoteSocketAddress();
		} else {
			localSocketAddress = null;
			remoteSocketAddress = null;
		}
		this.filterChain = filterChain;
	}
	
	public DatagramSession(final DatagramChannel channel, final Pushable pushable, final FilterChain filterChain, final SocketAddress localSocketAddress, SocketAddress remoteSocketAddress) {
		this();
		this.channel = channel;
		if (channel != null) socket = channel.socket();
		else socket = null;
		this.pushable = pushable;
		this.filterChain = filterChain;
		this.localSocketAddress = localSocketAddress;
		this.remoteSocketAddress = remoteSocketAddress;
	}
	
	public DatagramSession(final DatagramChannel channel, final Pushable pushable, final FilterChain filterChain, final SocketAddress remoteSocketAddress) {
		this();
		this.channel = channel;
		if (channel != null) socket = channel.socket();
		else socket = null;
		this.pushable = pushable;
		this.filterChain = filterChain;
		this.remoteSocketAddress = remoteSocketAddress;
	}

	/**
	 * Set the DatagramChannel of this session
	 * @param channel
	 * @since 1.0
	 */
	public void setChannel(final DatagramChannel channel) {
		this.channel = channel;
		if (channel != null) socket = channel.socket();
		else socket = null;
	}
	
	/**
	 * Get current session's socket
	 * @return
	 */
	public DatagramSocket getSocket() {
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
				pushable.add(this);
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
				} catch (PortUnreachableException e) {
					sendList.poll();
					// some other I/O error occurs
					filterChain.fireDataNotSent(this, packet.getData());
					filterChain.fireExceptionCaught(this, e);
					this.close();
				} catch (Exception e) {
					sendList.poll();
					// some other I/O error occurs
					filterChain.fireDataNotSent(this, packet.getData());
					filterChain.fireExceptionCaught(this, e);
				}
			}
			if (sendList.isEmpty()) {
				pushable.remove(this);
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
	private int write(final ByteBuffer buf) throws Exception {
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
		
		return channel.send(_buf, remoteSocketAddress);
	}

	@Override
	public void open() {
		if (open) {
			throw new IllegalStateException("this session has already been opened.");
		}
		closed = false;
		open = true;
		filterChain.fireSessionOpened(this);
	}
	
	@Override
	public void close() {
		if (!closed) {
			closed = true;
			open = false;
			try {
				// fire session closed
				filterChain.fireSessionClosed(this);
			} catch (Exception e) {
				filterChain.fireExceptionCaught(this, e);
			}
		}
	}

	@Override
	public String toString() {
		return "DatagramSession [local=" + localSocketAddress
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
