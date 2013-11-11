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
import java.util.Iterator;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.gmail.dengtao.joe.transport.filter.FilterChain;
import com.gmail.dengtao.joe.transport.handler.Handler;
import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.Session;
import com.gmail.dengtao.joe.transport.session.impl.DatagramSession;

/**
 * DatagramConnector is used to recive/send udp data.
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public class DatagramConnector extends AbstarctConnector implements Pushable {

	private DatagramChannel channel; 	// a selectable channel for datagram-oriented sockets. 
	private DatagramSocket socket; 		// a socket for sending and receiving datagram packets. 
	private Selector selector; 			// a selectable channel's registration with a selector is represented by a SelectionKey object. A selector maintains three sets of selection keys: 
	private DatagramSession session;

	private DaemonThreadFactory threadFactory = new DaemonThreadFactory("DatagramConnectorSessionLifecycle");
	private ScheduledExecutorService sessionLifecycleExecutor;  // Session LifeCycle Scheduled
	private long sessionLifeCyclePeriod = 500;	// Session LifeCycle check perid, ms
	
	private boolean needPush = false;
	
	public DatagramConnector() {
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
				throw new IllegalStateException("DatagramChannel has not been initialized, call 'init()' method before!");
			}
			if (handler == null) {
				throw new IllegalStateException("The Handler for this Connector was not set! call 'setHandler(Handler handler)' to set Handler for this Connector.");
			} else {
				filterChain.setHandler(handler);
			}
			
			SocketAddress remote = new InetSocketAddress(InetAddress.getByName(host), port);

			session = new DatagramSession(channel, this, filterChain, remote);
			// fire session created
			filterChain.fireSessionCreated(session);
			
			channel.connect(remote);
			// set local socket address
			if (channel.socket() != null) {
				session.setLocalSocketAddress(channel.socket().getLocalSocketAddress());
				session.setRemoteSocketAddress(channel.socket().getRemoteSocketAddress());
				socket = channel.socket();
			} else {
				session.setLocalSocketAddress(null);
				session.setRemoteSocketAddress(null);
			}
			
			// Datagram channels support reading and writing, 
			// so only support (SelectionKey.OP_READ | SelectionKey.OP_WRITE). 
			channel.register(selector, SelectionKey.OP_READ);
			
			if (lock != null) {
				synchronized (lock) {
					lock.notifyAll();
				}
			}
			
			session.active();
			// fire session opened
			session.open();

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
		// WRITABLE key; handle write data
		if (key.isValid() && key.isWritable()) {
			if (session != null) {
				if (session.isOpen() && needPush) {
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
			try {
				if (session.isOpen()) {
					session.active();
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
						this.close();
						return;
					}
					session.setRemoteSocketAddress(remoteSocketAddress);
					session.setLocalSocketAddress(localSocketAddress);
					if (data != null && data.length > 0) {
						// set latest read time 
						session.setLatestReadTime(System.currentTimeMillis());
						filterChain.fireDataReceived(session, data);
					}
				}
			} catch (Exception e) {
				filterChain.fireExceptionCaught(session, e);	// fire exception caught
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
				socket.close();
			}
		}
	}

	/**
     * @return channel for this datagram-oriented sockets. 
     * @since 1.0
     */
    public DatagramChannel getChannel() {
		return channel;
	}

    private void setActive(boolean active) {
		synchronized (activeLock) {
			this.active = active;
		}
	}
	
	@Override
	public void add(DatagramSession session) {
		if (!needPush) {
			try {
				needPush = true;
				// regist write & read
				channel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
				selector.wakeup();
			} catch (Exception e) {
				filterChain.fireExceptionCaught(session, e);
			}
		}
	}

	@Override
	public void remove(DatagramSession session) {
		try {
			needPush = false;
			// regist only read
			channel.register(selector, SelectionKey.OP_READ);
			selector.wakeup();
		} catch (ClosedChannelException e) {
			/* Nothing... */
		} catch (Exception e) {
			filterChain.fireExceptionCaught(session, e);
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
			//}
		}
		
	}
}
