package com.gmail.dengtao.joe.transport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.gmail.dengtao.joe.transport.filter.FilterChain;
import com.gmail.dengtao.joe.transport.handler.Handler;
import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.Session;
import com.gmail.dengtao.joe.transport.session.impl.SocketSession;

/**
 * SocketMultiConnector is used to recive/send tcp data with multi server
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.1
 * @since 1.0
 */
public class SocketMultiConnector extends AbstarctConnector {

	private Selector selector; 		// a selectable channel's registration with a selector is represented by a SelectionKey object. A selector maintains three sets of selection keys: 

	private DaemonThreadFactory threadFactory = new DaemonThreadFactory("SocketMultiConnectorSessionLifecycle");
	private ScheduledExecutorService sessionLifecycleExecutor;  // Session LifeCycle Scheduled
	private long sessionLifeCyclePeriod = 500;	// Session LifeCycle check perid, ms

	private Map<SocketChannel, SocketSession> sessionCache = new ConcurrentHashMap<SocketChannel, SocketSession>();
	
	public SocketMultiConnector() {
		filterChain = new FilterChain();
	}

	@Override
	public void init() throws Exception {
		if (isActive()) {
			throw new IllegalStateException("This connector is active, if you want to re-init you should call close() method before this!");
		}
		// Release resouces...
		if (selector != null) try { selector.close(); selector = null; } catch (Exception e) { /** nothind... */ }
		if (sessionCache != null) try { sessionCache.clear(); } catch (Exception e) { /** nothind... */ }
		
		/* allocate receive buffer */
		synchronized (receiveBufferLock) {
			receiveBuffer = ByteBuffer.allocate(receiveBufferSize);
		}
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
	
	/**
	 * Attention! in this connector, Thread will not be blocked here.<br>
	 * and target will just be add to connect list.<br>
	 * call {@link #connect()} to connect to remot peer!
	 */
	public void open(String host, int port, Object lock) throws Exception {
		try {
			synchronized (activeLock) {

				if (selector == null) {
					throw new IllegalStateException("SocketChannel and etc. have not been initialized, call 'init()' method before!");
				}
				if (handler == null) {
					throw new IllegalStateException("The Handler for this Connector was not set! call 'setHandler(Handler handler)' to set Handler for this Connector.");
				} else {
					filterChain.setHandler(handler);
				}
				SocketAddress remote = new InetSocketAddress(InetAddress.getByName(host), port);
				SocketChannel channel = openNewChannel(false);
				SocketSession session = new SocketSession(channel, selector, filterChain, null, remote);
				
				sessionCache.put(channel, session);
				
				// fire session created.
				filterChain.fireSessionCreated(session);
				
				// connect
				channel.connect(session.getRemoteSocketAddress());
				// set local socket address
				if (channel.socket() != null) {
					session.setLocalSocketAddress(channel.socket().getLocalSocketAddress());
					session.setRemoteSocketAddress(channel.socket().getRemoteSocketAddress());
				} else {
					session.setLocalSocketAddress(null);
					session.setRemoteSocketAddress(null);
				}
				// Socket channels support connecting, reading, and writing, 
				// so this method returns (SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE). 
				channel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);
				selector.wakeup();
				
				if (lock != null) {
					synchronized (lock) {
						lock.notifyAll();
					}
				}
			}
		} catch (Exception e) {
			throw e;
		}
	}
	
	/**
	 * Connect to remot peers, {@link #open(String, int)} with.
	 * @throws Exception
	 * @since 1.0
	 */
	public void connect() throws Exception {
		if (isActive()) {
			throw new IllegalStateException("Connections are already conneded! can's open, please open before call'connect()' method!");
		} else {
			setActive(true);
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
					handleSelectionKey(key);	// Handle SelectionKey...
				}
			} catch (Exception e) {
				if (isActive()) {
					filterChain.fireExceptionCaught(null, e);	// fire exception caught
				}
			}
		}
	}

	/**
	 * Open a new channel
	 * @param blocking whether this channel blocking
	 * @return a new channel
	 * @throws IOException 
	 * @since 1.0
	 */
	private SocketChannel openNewChannel(boolean blocking) throws IOException {
		synchronized (SocketChannel.class) {
			/* Open Channel */
			SocketChannel channel = SocketChannel.open();
			/* Configure Non-Blocking */
			channel.configureBlocking(blocking);
			return channel;
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
			
			// get the channel for which this key was created. 
			// This method will continue to return the channel even after the key is cancelled. 
			SocketChannel channel = (SocketChannel) key.channel();
			
			SocketAddress localSocketAddress = null;
			SocketAddress remoteSocketAddress = null;
			if (channel.socket() != null) {
				// Returns the address of the endpoint this socket is bound to, or null if it is not bound yet.
				localSocketAddress = channel.socket().getLocalSocketAddress();
				// Returns the address of the endpoint this socket is connected to, or null if it is unconnected.
				remoteSocketAddress = channel.socket().getRemoteSocketAddress();
			}
			// Find sessin
			SocketSession session = sessionCache.get(channel);
			if (session != null) {
				// Tells whether or not a connection operation is in progress on this channel. 
				if (channel.isConnectionPending()) {  
					try {
						channel.finishConnect();
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
							
							session.open();
						} catch (ClosedChannelException e) {
							filterChain.fireExceptionCaught(session, e);
							session.close();
							return;
						}
					} catch (IOException e) {
						filterChain.fireExceptionCaught(session, e);
						session.close();
						return;
					} catch (Exception e) {
						filterChain.fireExceptionCaught(session, e);
						return;
					}
				}
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
			}
		}
		
		// READABLE key; handle available data
		if (key.isValid() && key.isReadable()) {
			// get the channel for which this key was created. 
			// This method will continue to return the channel even after the key is cancelled. 
			SocketChannel channel = (SocketChannel) key.channel();

			SocketSession session = sessionCache.get(channel);

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
							if ((readLen = channel.read(receiveBuffer)) > 0) {
								receiveBuffer.flip();
								data = new byte[receiveBuffer.limit() - receiveBuffer.position()];
								receiveBuffer.get(data);
							}
						}
					} catch (IOException e) {
						// read error
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
			/* Close selector */
			if (selector != null && selector.isOpen()) {
				try { selector.close(); } catch (IOException e) { /** nothing */ }
			}
			/* close sessions */
			for (Iterator<Entry<SocketChannel, SocketSession>> iter = sessionCache.entrySet().iterator(); iter.hasNext();) {
				Entry<SocketChannel, SocketSession> entry = iter.next(); 
				SocketChannel channel = entry.getKey();
				SocketSession session = entry.getValue();
				/* Close channel */
				if (channel != null && channel.isOpen()) {
					try { channel.close(); } catch (IOException e) { /** nothing */ }
				}
				if(session != null) {
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
			
			for (Iterator<Entry<SocketChannel, SocketSession>> iter = sessionCache.entrySet().iterator(); iter.hasNext();) {
				Entry<SocketChannel, SocketSession> entry = iter.next(); 
				SocketSession session = entry.getValue();
				if(session != null) {
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