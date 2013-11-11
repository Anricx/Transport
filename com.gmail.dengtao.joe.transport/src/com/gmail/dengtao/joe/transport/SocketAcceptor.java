package com.gmail.dengtao.joe.transport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.gmail.dengtao.joe.transport.filter.FilterChain;
import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.impl.SocketSession;

/**
 * SocketAcceptor is used to recive/send tcp data.
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.1
 * @since 1.0
 */
public class SocketAcceptor extends AbstractAcceptor {
	
	private ServerSocketChannel channel; 	// a selectable channel for stream-oriented listening sockets. 
	private ServerSocket socket; 		// a socket for sending and receiving socket packets. 
	private Selector selector; 			// a selectable channel's registration with a selector is represented by a SelectionKey object. A selector maintains three sets of selection keys: 

	private DaemonThreadFactory threadFactory = new DaemonThreadFactory("SocketAcceptorSessionLifecycle");
	private ScheduledExecutorService sessionLifecycleExecutor;  // Session LifeCycle Scheduled
	private long sessionLifeCyclePeriod = 500;	// Session LifeCycle check perid, ms
	
	private Map<SocketChannel, SocketSession> sessionCache = new ConcurrentHashMap<SocketChannel, SocketSession>();
	
	/**
	 * Create a SocketAcceptor Acceptor, default selectTimeout is 0ms(selector will be blocked) and default sessionExaminePeriod is 1200000ms.
	 * @param host Socket will bind this address.
	 * @param port Socket will bind this port. 
	 */
	public SocketAcceptor(String host, int port) {
		this.host = host;
		this.port = port;
		filterChain = new FilterChain();
	}
	
	/**
	 * Create a SocketAcceptor Acceptor with specific selectTimeout and sessionExaminePeriod
	 * @param host Socket will bind this address.
	 * @param port Socket will bind this port. 
	 * @param selectTimeout If positive, block for up to timeout milliseconds, more or less, while waiting for a channel to become ready; if zero, block indefinitely; must not be negative
	 */
	public SocketAcceptor(String host, int port, int selectTimeout) {
		this(host, port);
		this.selectTimeout = selectTimeout;
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
		if (sessionCache != null) try { sessionCache.clear(); } catch (Exception e) { /** nothind... */ }
		
		synchronized (receiveBufferLock) {
			/* allocate receive buffer */
			receiveBuffer = ByteBuffer.allocate(receiveBufferSize);
		}
		/* Open Channel */
		channel = ServerSocketChannel.open();
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
	public void start() throws Exception {
		start(null);
	}
	
	@Override
	public void start(Object lock) throws Exception {
		try {
			if (isActive()) {
				throw new IllegalStateException("Acceptor is already started!");
			} else {
				setActive(true);
			}
			if (channel == null) {
				throw new IllegalStateException("ServerSocketChannel and etc. have not been initialized, call 'init()' method before!");
			}
			if (handler == null) {
				throw new IllegalStateException("The Handler for this Acceptor was not set! call 'setHandler(Handler handler)' to set Handler for this acceptor.");
			} else {
				filterChain.setHandler(handler);
			}
	
			SocketAddress address = new InetSocketAddress(InetAddress.getByName(host), port);
			// Configure the server socket,
			socket = channel.socket();
			synchronized (bindLock) {
				// Set the reuseAddress flag accordingly with the setting
				socket.setReuseAddress(isReuseAddress());
				// and bind.
				socket.bind(address);
			}
			
			// Server-socket channels only support the accepting of new connections, 
			// so wo should only register SelectionKey.OP_ACCEPT. 
			channel.register(selector, SelectionKey.OP_ACCEPT);
			
			if (lock != null) {
				synchronized (lock) {
					lock.notifyAll();
				}
			}
			
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
						handleSelectionKey(key, channel);	// Handle SelectionKey...
					}
				} catch (Exception e) {
					if (isActive()) {
						filterChain.fireExceptionCaught(new SocketSession(null, selector, filterChain, null), e);	// fire exception caught
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
	private void handleSelectionKey(SelectionKey key, ServerSocketChannel ssc) {
		
		// ACCEPTABLE key; accepting and adding new SocketChannel to selector for READ only
		if (key.isValid() && key.isAcceptable()) {
			SocketChannel sc = null;
			SocketSession session = new SocketSession(sc, selector, filterChain);
			try {
				// Accepts a connection made to this channel's socket. 
				sc = ssc.accept();
				session.setChannel(sc);
				session.setSendBufferSize(sendBufferSize);
				SocketAddress localSocketAddress = null;
				SocketAddress remoteSocketAddress = null;
				if (sc.socket() != null) {
					// Returns the address of the endpoint this socket is bound to, or null if it is not bound yet.
					localSocketAddress = sc.socket().getLocalSocketAddress();
					// Returns the address of the endpoint this socket is connected to, or null if it is unconnected.
					remoteSocketAddress = sc.socket().getRemoteSocketAddress();
				}
				session.setLocalSocketAddress(localSocketAddress);
				session.setRemoteSocketAddress(remoteSocketAddress);
				
				sessionCache.put(sc, session);
				filterChain.fireSessionCreated(session); // fire session created
				
				/* Configure Non-Blocking */
				sc.configureBlocking(false);
				// Socket channels support connecting, reading, and writing, 
				// so this method returns (SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE). 
				// sc.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ );
				sc.register(selector, SelectionKey.OP_READ );
				selector.wakeup();
				
				session.open();	// fire session opened
			} catch (Exception e) {
				filterChain.fireExceptionCaught(session, e); // fire exception caught
				return;
			}
		}
		
		// WRITABLE key; handle write data
		if (key.isValid() && key.isWritable()) {
			// get the channel for which this key was created. 
			// This method will continue to return the channel even after the key is cancelled. 
			SocketChannel sc = (SocketChannel) key.channel();
			SocketSession session = sessionCache.get(sc);
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
			// Returns the address of the endpoint this socket is connected to, or null if it is unconnected.
			SocketAddress remoteSocketAddress = sc.socket().getRemoteSocketAddress();
			SocketSession session = sessionCache.get(sc);
			
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
						// read error.
						filterChain.fireExceptionCaught(session, e);
						session.close();
						return;
					}
					if (readLen == -1) {
						// remote socket has closed
						session.close();
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
				filterChain.fireExceptionCaught(new SocketSession(sc, selector, filterChain, remoteSocketAddress), new IllegalStateException("can't read! channel session not register!"));
				return;
			}
		}
	}

	@Override
	public void stop() {
		if (isActive()) {
			setActive(false);
			// Cancel ScheduledExecutorService
			if (sessionLifecycleExecutor != null) {
				sessionLifecycleExecutor.shutdown();
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
			/* close clients */
			for (Iterator<Entry<SocketChannel, SocketSession>> iter = sessionCache.entrySet().iterator(); iter.hasNext();) {

				Entry<SocketChannel, SocketSession> entry = iter.next(); 
				SocketChannel channel = entry.getKey();
				SocketSession session = entry.getValue();
				/* Close channel */
				if (channel.isOpen()) {
					try { channel.close(); } catch (IOException e) { /** nothing */ }
				}
				if (session != null) {
					if (session.isClosed()) {
						iter.remove();
						continue;
					} else if (session.isOpen()) {
						session.close();
					}
				} else {
					iter.remove();
				}
			}
		}
	}
	
	private void setActive(boolean active) {
		synchronized (activeLock) {
			this.active = active;
		}
	}
	
	/**
	 * @return sessions in this acceptor
	 * @since 1.0
	 */
	public Map<SocketChannel, SocketSession> getSessionCache() {
		return sessionCache;
	}

	/**
	 * Maintain sessionCache, remove (now - activeTime) >= sessionLifeTime or closed Session
	 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
	 * @version 1.0
	 * @since 1.0
	 * @date 2012-10-7
	 */
	class SessionLifeCycle implements Runnable {

		@Override
		public void run() {
			long now = System.currentTimeMillis();
			Iterator<Entry<SocketChannel, SocketSession>> iter = sessionCache.entrySet().iterator(); 
			for ( ; iter.hasNext(); ) {
				Entry<SocketChannel, SocketSession> entry = iter.next(); 
				SocketSession session = entry.getValue();
				if (session != null) {
					if (session.isClosed()) {
						iter.remove();
						continue;
					} else if (session.isOpen()) {
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
				} else {
					iter.remove();
				}
			}
		}
		
	}

}
