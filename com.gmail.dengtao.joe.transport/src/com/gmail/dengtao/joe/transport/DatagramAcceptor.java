package com.gmail.dengtao.joe.transport;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.gmail.dengtao.joe.transport.filter.FilterChain;
import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.impl.DatagramSession;

/**
 * DatagramAcceptor is used to recive/send udp data.
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public class DatagramAcceptor extends AbstractAcceptor implements Pushable {

	private DatagramChannel channel; 	// a selectable channel for datagram-oriented sockets. 
	private DatagramSocket socket; 		// a socket for sending and receiving datagram packets. 
	private Selector selector; 			// a selectable channel's registration with a selector is represented by a SelectionKey object. A selector maintains three sets of selection keys: 
    
	private DaemonThreadFactory threadFactory = new DaemonThreadFactory("DatagramAcceptorSessionLifecycle");
	private ScheduledExecutorService sessionLifecycleExecutor;  // Session LifeCycle Scheduled
	private long sessionLifeCyclePeriod = 500;	// Session LifeCycle check perid, ms
	
	private Map<SocketAddress, DatagramSession> sessionCache = new ConcurrentHashMap<SocketAddress, DatagramSession>();
	private List<DatagramSession> needPushSessions = new ArrayList<DatagramSession>();
	private final Object pushLock = new Object();
	
	/**
	 * Create a Datagram Acceptor, default selectTimeout is 0ms(selector will be blocked) and default sessionLifeTime is 60000ms.
	 * @param host DatagramSocket will bind this address.
	 * @param port DatagramSocket will bind this port. 
	 */
	public DatagramAcceptor(String host, int port) {
		this.host = host;
		this.port = port;
		filterChain = new FilterChain();
	}

	/**
	 * Create a Datagram Acceptor with specific selectTimeout and sessionLifeTime
	 * @param host DatagramSocket will bind this address.
	 * @param port DatagramSocket will bind this port. 
	 * @param selectTimeout If positive, block for up to timeout milliseconds, more or less, while waiting for a channel to become ready; if zero, block indefinitely; must not be negative
	 */
	public DatagramAcceptor(String host, int port, int selectTimeout) {
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
		channel = DatagramChannel.open();
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
				throw new IllegalStateException("DatagramChannel has not been initialized, call 'init()' method before!");
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
			
			// Datagram channels support reading and writing, 
			// so only support (SelectionKey.OP_READ | SelectionKey.OP_WRITE). 
			channel.register(selector, SelectionKey.OP_READ);
			
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
						filterChain.fireExceptionCaught(new DatagramSession(channel, this, filterChain, null), e);	// fire exception caught
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
	private void handleSelectionKey(SelectionKey key, DatagramChannel channel) {
		
		// WRITABLE key; handle write data
		if (key.isValid() && key.isWritable()) {
			synchronized (pushLock) {
				List<DatagramSession> tempList = new ArrayList<DatagramSession>();
				tempList.addAll(needPushSessions);
				for (DatagramSession session : tempList) {
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
			}
		}
		
		// READABLE key; handle available data
		if (key.isValid() && key.isReadable()) {
			DatagramSession session = new DatagramSession(channel, this, filterChain);
			try {
				byte[] data = null;
				SocketAddress remoteSocketAddress = null;
				SocketAddress localSocketAddress = null;
				
				try {
					synchronized (receiveBufferLock) {
						receiveBuffer.clear();
						remoteSocketAddress = channel.receive(receiveBuffer);
						if (channel.socket() != null) {
							// Returns the address of the endpoint this socket is bound to, or null if it is not bound yet.
							localSocketAddress = channel.socket().getLocalSocketAddress();
						}
						receiveBuffer.flip();
						data = new byte[receiveBuffer.limit() - receiveBuffer.position()];
						receiveBuffer.get(data);
					}
				} catch (IOException e) {
					// read error
					filterChain.fireExceptionCaught(session, e);
					session.close();
					return;
				}
				session.setLocalSocketAddress(localSocketAddress);
				session.setRemoteSocketAddress(remoteSocketAddress);;
				if (remoteSocketAddress == null) {
					filterChain.fireExceptionCaught(session, new IllegalStateException("Invalid remote address! Session:" + session));	// fire exception caught
				} else if (!sessionCache.containsKey(remoteSocketAddress)) {
					sessionCache.put(remoteSocketAddress, session);
					filterChain.fireSessionCreated(session);	// fire session created
					
					session.active();
					session.open();	// fire session opened
				} else {
					session = sessionCache.get(remoteSocketAddress);
				}
				if (session != null && session.isOpen()) {
					session.active();
					if (data != null && data.length > 0) {
						// set latest read time 
						session.setLatestReadTime(System.currentTimeMillis());
						filterChain.fireDataReceived(session, data);
						return;
					}
				}
			} catch (Exception e) {
				filterChain.fireExceptionCaught(session, e);	// fire exception caught
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
				socket.close();
			}
			/* close clients */
			for (Iterator<Entry<SocketAddress, DatagramSession>> iter = sessionCache.entrySet().iterator(); iter.hasNext();) {
				Entry<SocketAddress, DatagramSession> entry = iter.next();
				DatagramSession session = entry.getValue();
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
	public Map<SocketAddress, DatagramSession> getSessionCache() {
		return sessionCache;
	}
	
	@Override
	public void add(DatagramSession session) {
		try {
			synchronized (pushLock) {
				if (needPushSessions.contains(session)) return;
				needPushSessions.add(session);
				// regist write & read
				channel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
				selector.wakeup();
			}
		} catch (Exception e) {
			filterChain.fireExceptionCaught(session, e);
		}
	}

	@Override
	public void remove(DatagramSession session) {
		try {
			synchronized (pushLock) {
				if (!needPushSessions.contains(session)) return;
				needPushSessions.remove(session);
				// regist only read
				channel.register(selector, SelectionKey.OP_READ);
				selector.wakeup();
			}
		} catch (ClosedChannelException e) {
			/* Nothing... */
		}
	}

	/**
	 * Maintain sessionCache, remove (now - activeTime) >= sessionLifeTime or closed Session
	 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
	 * @version 1.0
	 * @since 1.0
	 * @date 2012-10-7
	 */
	class SessionLifeCycle extends TimerTask {
	
		@Override
		public void run() {
			long now = System.currentTimeMillis();
			for (Iterator<Entry<SocketAddress, DatagramSession>> iter = sessionCache.entrySet().iterator(); iter.hasNext();) {
				Entry<SocketAddress, DatagramSession> entry = iter.next();
				DatagramSession session = entry.getValue();
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
