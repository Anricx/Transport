package com.gmail.dengtao.joe.transport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.gmail.dengtao.joe.transport.filter.FilterChain;
import com.gmail.dengtao.joe.transport.handler.Handler;
import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.Session;
import com.gmail.dengtao.joe.transport.session.impl.SocketSession;

/**
 * SocketConnector is used to recive/send tcp data.
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.1
 * @since 1.0
 */
public class SocketConnector extends AbstarctConnector {
	
	private SocketChannel channel; 	// a selectable channel for tcp sockets. 
	private Socket socket; 			// a socket for sending and receiving socket packets. 
	private Selector selector; 		// a selectable channel's registration with a selector is represented by a SelectionKey object. A selector maintains three sets of selection keys: 

	private SocketSession session;
	
	private DaemonThreadFactory threadFactory = new DaemonThreadFactory("SocketConnectorSessionLifecycle");
	private ScheduledExecutorService sessionLifecycleExecutor;  // Session LifeCycle Scheduled
	private long sessionLifeCyclePeriod = 500;	// Session LifeCycle check perid, ms
	
	public SocketConnector() {
		filterChain = new FilterChain();
	}

	@Override
	public void init() throws Exception {
		if (isActive()) {
			throw new IllegalStateException("This connector is active, if you want to re-init you should call close() method before this!");
		}
		// Release resouces...
		if (channel != null) try { channel.close(); channel = null; } catch (Exception e) { /** nothind... */ }
		if (socket != null) try { socket.close(); socket = null; } catch (Exception e) { /** nothind... */ }
		if (selector != null) try { selector.close(); selector = null; } catch (Exception e) { /** nothind... */ }

		synchronized (receiveBufferLock) {
			/* allocate receive buffer */
			receiveBuffer = ByteBuffer.allocate(receiveBufferSize);
		}
		/* Open Channel */
		channel = SocketChannel.open();
		/* Configure Non-Blocking */
		channel.configureBlocking(false);
		/* Open a selector */
		synchronized (Selector.class) {
			// Selector.open() isn't thread safe
            // http://bugs.sun.com/view_bug.do?bug_id=6427854
            // Affects 1.6.0_29, fixed in 1.7.0_01
			selector = Selector.open();
		}
	}

	@Override
	public void open(String host, int port) throws Exception {
		open(host, port, null);
	}
	
	@Override
	public void open(String host, int port, Object lock) throws Exception {
		try {
			if (isActive()) {
				throw new IllegalStateException("Connector is already connected!");
			} else {
				setActive(true);
			}
			if (channel == null) {
				throw new IllegalStateException("SocketChannel and etc. have not been initialized, call 'init()' method before!");
			}
			if (handler == null) {
				throw new IllegalStateException("The Handler for this Connector was not set! call 'setHandler(Handler handler)' to set Handler for this Connector.");
			} else {
				filterChain.setHandler(handler);
			}
			
			SocketAddress remote = new InetSocketAddress(InetAddress.getByName(host), port);
			channel.connect(remote);
			socket = channel.socket();
			// Socket channels support connecting, reading, and writing, 
			// so this method returns (SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE). 
			channel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ);
			
			if (lock != null) {
				synchronized (lock) {
					lock.notifyAll();
				}
			}
			
			session = new SocketSession(channel, selector, filterChain, remote);
			session.setSendBufferSize(sendBufferSize);
			// Schedule SessionCache LifyCycle ...
			sessionLifecycleExecutor = Executors.newScheduledThreadPool(8, threadFactory);  
			sessionLifecycleExecutor.scheduleAtFixedRate(new SessionLifeCycle(), sessionLifeCyclePeriod, sessionLifeCyclePeriod, TimeUnit.MILLISECONDS);  
			
			for (; isActive();) {
				try {
					if (selector.select(selectTimeout) == 0) {
						continue;
					}
					Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
					for( ; iter.hasNext(); iter.remove()) {
						SelectionKey key = iter.next();
						handleSelectionKey(key);	// Handle SelectionKey...
					}
				} catch (Exception e) {
					if (isActive()) {
						filterChain.fireExceptionCaught(session, e);	// fire exception caught
					}
				}
			}
		} catch (Exception e) {
			setActive(false);
			throw e;
		}
	}
	
	/**
	 * Handle SelectionKey process...
	 * @param selectionKey
	 * @param channel
	 * @throws IOException 
	 * @throws Exception 
	 * @since 1.0
	 */
	private void handleSelectionKey(SelectionKey key) {

		// CONNECTABLE key; calling finishConnect
		if (key.isValid() && key.isConnectable()) {
			
			// fire session created
			filterChain.fireSessionCreated(session);
			
			// get the channel for which this key was created. 
			// This method will continue to return the channel even after the key is cancelled. 
			SocketChannel sc = (SocketChannel) key.channel();
			// Tells whether or not a connection operation is in progress on this channel. 
			if (sc.isConnectionPending()) {  
				try {
					sc.finishConnect();
					SocketAddress localSocketAddress = null;
					SocketAddress remoteSocketAddress = null;
					if (channel.socket() != null) {
						// Returns the address of the endpoint this socket is bound to, or null if it is not bound yet.
						localSocketAddress = channel.socket().getLocalSocketAddress();
						// Returns the address of the endpoint this socket is connected to, or null if it is unconnected.
						remoteSocketAddress = channel.socket().getRemoteSocketAddress();
					}
					session.setLocalSocketAddress(localSocketAddress);
					session.setRemoteSocketAddress(remoteSocketAddress);
					
					try {
						channel.register(selector, SelectionKey.OP_READ);
						selector.wakeup();
						
						// open session
						session.open();
					} catch (ClosedChannelException e) {
						filterChain.fireExceptionCaught(session, e);
						session.close();
						this.close();
					}
				} catch (IOException e) {
					filterChain.fireExceptionCaught(session, e);
					session.close();
					this.close();
				} catch (Exception e) {
					filterChain.fireExceptionCaught(session, e);
					return;
				}
			}
		}
		
		// WRITABLE key; handle write data
		if (key.isValid() && key.isWritable()) {
			// get the channel for which this key was created. 
			// This method will continue to return the channel even after the key is cancelled. 
			SocketChannel sc = (SocketChannel) key.channel();
			if (session != null) {
				if (session.isOpen()) {
					session.active();	// active session
					try {
						filterChain.firePushData(session);
					} catch (Exception e) {
						filterChain.fireExceptionCaught(session, e);
						return;
					}
				}
			} else {
				// session is not register
				filterChain.fireExceptionCaught(new SocketSession(sc, selector, filterChain), new IllegalStateException("can't write! channel session not register!"));
				return;
			}
		}
		
		// READABLE key; handle available data
		if (key.isValid() && key.isReadable()) {
			// get the channel for which this key was created. 
			// This method will continue to return the channel even after the key is cancelled. 
			SocketChannel sc = (SocketChannel) key.channel();

			if (session != null) {
				if (session.isOpen()) {
					session.active();	// active session
					// Received data bytes
					byte[] data = null;
					// The number of bytes read, possibly zero, or -1 if the channel has reached end-of-stream
					int readLen = 0;
					try {
						synchronized (receiveBufferLock) {
							receiveBuffer.clear();
							if ((readLen = sc.read(receiveBuffer)) > 0) {
								receiveBuffer.flip();
								data = new byte[receiveBuffer.limit() - receiveBuffer.position()];
								receiveBuffer.get(data);
							}
						}
					} catch (IOException e) {
						// read error
						this.close();
						return;
					}
					if (readLen == -1) {
						// remote socket has closed
						this.close();
					} else {
						if (data != null && data.length > 0) {
							// set latest read time 
							session.setLatestReadTime(System.currentTimeMillis());
							filterChain.fireDataReceived(session, data);
						}
					}
				}
			} else {
				// session is not register
				filterChain.fireExceptionCaught(new SocketSession(sc, selector, filterChain), new IllegalStateException("can't read! channel session not register!"));
				return;
			}
		}
	}

	@Override
	public void close() {
		if (isActive()) {
			setActive(false);
			// Cancel ScheduledExecutorService
			if (sessionLifecycleExecutor != null) {
				sessionLifecycleExecutor.shutdown();
			}
			// close this session
			if (session != null && session.isOpen()) {
				session.close();
			}
			/* Close selector */
			if (selector != null && selector.isOpen()) {
				try { selector.close(); } catch (IOException e) { /** nothing */ }
			}
			/* Close channel */
			if (channel != null && channel.isOpen()) {
				try { channel.close(); } catch (IOException e) { /** nothing */ }
			}
			/* Close socket, Actually when channel was closed socket was also closed. */
			if (socket != null && !socket.isClosed()) {
				try { socket.close(); } catch (IOException e) { /** nothing */ }
			}
		}
	}
	
	/**
     * @return channel for this datagram-oriented sockets. 
     * @since 1.0
     */
    public SocketChannel getChannel() {
		return channel;
	}

	private void setActive(boolean active) {
		synchronized (activeLock) {
			this.active = active;
		}
	}
	
	/**
	 * Maintain session, fire {@link Handler#sessionIdle(Session, IdleStatus)};
	 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
	 * @version 1.0
	 * @since 1.0
	 * @date 2012-10-7
	 */
	class SessionLifeCycle implements Runnable {

		@Override
		public void run() {
			long now = System.currentTimeMillis();
			//synchronized (session) {
				if (session.isClosed()) {
					sessionLifecycleExecutor.shutdown();
					return;
				}
				if (session.isOpen()) {
					// check write idle
					if (session.getIdleTime(IdleStatus.WRITE_IDLE) > 0 && (now - session.getLatestWriteTime()) >= session.getIdleTime(IdleStatus.WRITE_IDLE)) {
						filterChain.fireSessionIdle(session, IdleStatus.WRITE_IDLE);
						session.setLatestWriteTime(now);
					}
					// check read idle
					if (session.getIdleTime(IdleStatus.READ_IDLE) > 0 && (now - session.getLatestReadTime()) >= session.getIdleTime(IdleStatus.READ_IDLE)) {
						filterChain.fireSessionIdle(session, IdleStatus.READ_IDLE);
						session.setLatestReadTime(now);
					}
				}
			}
		//}
		
	}

}
