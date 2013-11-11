package com.gmail.dengtao.joe.transport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.gmail.dengtao.joe.transport.filter.FilterChain;
import com.gmail.dengtao.joe.transport.handler.Handler;
import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.Session;
import com.gmail.dengtao.joe.transport.session.impl.DatagramSession;

/**
 * DatagramMultiConnector is used to recive/send udp data with multi server
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public class DatagramMultiConnector extends AbstarctConnector implements Pushable {

	private Selector selector; 			// a selectable channel's registration with a selector is represented by a SelectionKey object. A selector maintains three sets of selection keys: 
	
	private DaemonThreadFactory threadFactory = new DaemonThreadFactory("DatagramMultiConnectorSessionLifecycle");
	private ScheduledExecutorService sessionLifecycleExecutor;  // Session LifeCycle Scheduled
	private long sessionLifeCyclePeriod = 500;	// Session LifeCycle check perid, ms
	
	private Map<DatagramChannel, DatagramSession> channelCache = new ConcurrentHashMap<DatagramChannel, DatagramSession>();
	private Map<DatagramSession, DatagramChannel> sessionCache = new ConcurrentHashMap<DatagramSession, DatagramChannel>();

	public DatagramMultiConnector() {
		filterChain = new FilterChain();
	}

	@Override
	public void init() throws Exception {
		if (isActive()) {
			throw new IllegalStateException("This connector is active, if you want to re-init you should call close() method before this!");
		}
		// Release resouces...
		if (selector != null) try { selector.close(); selector = null; } catch (Exception e) { /** nothind... */ }
		if (channelCache != null) try { channelCache.clear(); } catch (Exception e) { /** nothind... */ }
		if (sessionCache != null) try { sessionCache.clear(); } catch (Exception e) { /** nothind... */ }
		
		synchronized (receiveBufferLock) {
			/* allocate receive buffer */
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

	/**
	 * Attention! in this connector, Thread will not be blocked here.<br>
	 * and target will just be add to connect list.<br>
	 * call {@link #connect()} to connect to remot peer!
	 */
	public void open(String host, int port) throws Exception {
		
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
				DatagramChannel channel = openNewChannel(false);
				DatagramSession session = new DatagramSession(channel, this, filterChain, remote);
				
				channelCache.put(channel, session);
				sessionCache.put(session, channel);
				
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
				
				// Datagram channels support reading and writing, 
				// so only support (SelectionKey.OP_READ | SelectionKey.OP_WRITE). 
				channel.register(selector, SelectionKey.OP_READ);
				selector.wakeup();
				
				if (lock != null) {
					synchronized (lock) {
						lock.notifyAll();
					}
				}
				
				// fire session opened
				session.open();
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
		
		Iterator<SelectionKey> iter = null;
		for (; isActive();) {
			try {
				if (selector.select(selectTimeout) == 0) {
					continue;
				}
				iter = selector.selectedKeys().iterator();
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
	private DatagramChannel openNewChannel(boolean blocking) throws IOException {
		synchronized (DatagramChannel.class) {
			/* Open Channel */
			DatagramChannel channel = DatagramChannel.open();
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
		// WRITABLE key; handle write data
		if (key.isValid() && key.isWritable()) {
			// get the channel for which this key was created. 
			// This method will continue to return the channel even after the key is cancelled. 
			DatagramChannel channel = (DatagramChannel) key.channel();
			DatagramSession session = channelCache.get(channel);
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
			byte[] data = null;
			DatagramChannel channel = (DatagramChannel) key.channel();
			// Find sessin
			DatagramSession session = channelCache.get(channel);
			
			SocketAddress localSocketAddress = null;
			SocketAddress remoteSocketAddress = null;
			if (channel.socket() != null) {
				// Returns the address of the endpoint this socket is bound to, or null if it is not bound yet.
				localSocketAddress = channel.socket().getLocalSocketAddress();
			}
			
			if (session != null) {
				if (session.isOpen()) {
					try {
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
						session.setRemoteSocketAddress(remoteSocketAddress);
						session.setLocalSocketAddress(localSocketAddress);
						if (data != null && data.length > 0) {
							// set latest read time 
							session.setLatestReadTime(System.currentTimeMillis());
							filterChain.fireDataReceived(session, data);
						}
					} catch (Exception e) {
						filterChain.fireExceptionCaught(session, e);	// fire exception caught
						return;
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
			for (Iterator<Entry<DatagramChannel, DatagramSession>> iter = channelCache.entrySet().iterator(); iter.hasNext();) {
				Entry<DatagramChannel, DatagramSession> entry = iter.next(); 
				DatagramChannel channel = entry.getKey();
				DatagramSession session = entry.getValue();
				/* Close channel */
				if (channel.isOpen()) {
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
	
	@Override
	public void add(DatagramSession session) {
		DatagramChannel channel = sessionCache.get(session);
		if (channel != null) {
			try {
				// regist write & read
				channel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
				selector.wakeup();
			} catch (Exception e) {
				filterChain.fireExceptionCaught(session, e);
			}
		} else {
			session.close();
		}
	}

	@Override
	public void remove(DatagramSession session) {
		DatagramChannel channel = sessionCache.get(session);
		if (channel != null) {
			try {
				// regist only read
				channel.register(selector, SelectionKey.OP_READ);
				selector.wakeup();
			} catch (ClosedChannelException e) {
				/* Nothing... */
			}
		} else {
			session.close();
		}
	}

	/**
	 * Maintain session, fire {@link Handler#sessionIdle(Session, IdleStatus)};
	 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
	 * @version 1.0
	 * @since 1.0
	 * @date 2012-10-7
	 */
	class SessionLifeCycle extends TimerTask {

		@Override
		public void run() {
			long now = System.currentTimeMillis();
			
			for (Iterator<Entry<DatagramChannel, DatagramSession>> iter = channelCache.entrySet().iterator(); iter.hasNext();) {
				Entry<DatagramChannel, DatagramSession> entry = iter.next(); 
				DatagramChannel channel = entry.getKey();
				DatagramSession session = entry.getValue();
				if(session != null) {
					if (session.isClosed()) {
						iter.remove();
						sessionCache.remove(session);
						try { channel.close(); } catch (IOException e) { }
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
