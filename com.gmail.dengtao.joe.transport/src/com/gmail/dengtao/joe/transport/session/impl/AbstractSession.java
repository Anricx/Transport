package com.gmail.dengtao.joe.transport.session.impl;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.Session;

/**
 * Session's common methods implementation.
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
abstract class AbstractSession implements Session {
	/**
	 * Default size to create ALL incoming and outgoing buffers; this equates to the maximum size of
	 * message that can be sent or received.
	 * <P>
	 * NB: this variable is ONLY checked at construction time; you must alter it BEFORE constructing
	 * an instance of a server if you want it to have effect (or use the alternative constructor that
	 * allows you to specify a custom value for that instance only).
	 */
	public static final int DEFAULT_BUFFER_SIZE = 512;
	protected int sendBufferSize = DEFAULT_BUFFER_SIZE; // use 538 ~ 548, recommend 538 for the best performance.

	protected SocketAddress localSocketAddress;
	protected SocketAddress remoteSocketAddress;
	
	// write idle in milliseconds
	protected long idleTimeForRead = 0;
	// read idle in milliseconds
	protected long idleTimeForWrite = 0;
	// Session Create Time
	protected long createTime;
	// Session latest read data time
	protected long latestReadTime;
	// Session latest write data time
	protected long latestWriteTime;
	// Session latest active time
	protected long activeTime;
	// Mark whether this session is open
	protected boolean open = false;
	// Mark whether this session is closed
	protected boolean closed = false;
	
	// value map key-value
	protected Map<String, Object> sessionValueMap = new ConcurrentHashMap<String, Object>();

	/**
	 * Get remoteSocketAddress of this session
	 * @return remoteSocketAddress of this session
	 * @since 1.0
	 */
	public SocketAddress getRemoteSocketAddress() {
		return remoteSocketAddress;
	}
	
	/**
	 * Set the remoteSocketAddress of this session
	 * @param remoteSocketAddress
	 * @since 1.0
	 */
	public void setRemoteSocketAddress(final SocketAddress remoteSocketAddress) {
		this.remoteSocketAddress = remoteSocketAddress;
	}

	/**
	 * Get the localSocketAddress of this session
	 * @return localSocketAddress
	 * @since 1.0
	 */
	public SocketAddress getLocalSocketAddress() {
		return localSocketAddress;
	}

	/**
	 * Set the localSocketAddress of this sessions
	 * @param localSocketAddress
	 * @since 1.0
	 */
	public void setLocalSocketAddress(final SocketAddress localSocketAddress) {
		this.localSocketAddress = localSocketAddress;
	}

	/**
	 * Get session create time
	 * @return this session create time in milliseconds
	 * @since 1.0
	 */
	public long getCreateTime() {
		return createTime;
	}

	/**
	 * Get lasest active milliseconds time
	 * @return this session lasest active time in milliseconds
	 * @since 1.0
	 */
	public long getActiveTime() {
		return activeTime;
	}

	@Override
	public void setIdleTime(final IdleStatus status, final long idleTime) {
		if (idleTime < 0) {
			throw new IllegalArgumentException("Illegal idle time: " + idleTime);
		}
		if (status == IdleStatus.READ_IDLE) {
		    idleTimeForRead = idleTime;
		} else if (status == IdleStatus.WRITE_IDLE) {
		    idleTimeForWrite = idleTime;
		} else {
		    throw new IllegalArgumentException("Unknown idle status: " + status);
	    }
	}
	
	@Override
	public long getIdleTime(final IdleStatus status) {
        if (status == IdleStatus.READ_IDLE) {
            return idleTimeForRead;
        }
        if (status == IdleStatus.WRITE_IDLE) {
            return idleTimeForWrite;
        }
        throw new IllegalArgumentException("Unknown idle status: " + status);
    }

	public long getLatestReadTime() {
		return latestReadTime;
	}

	public void setLatestReadTime(final long latestReadTime) {
		if (this.latestReadTime < latestReadTime) {
			this.latestReadTime = latestReadTime;
		}
	}

	public long getLatestWriteTime() {
		return latestWriteTime;
	}

	public void setLatestWriteTime(final long latestWriteTime) {
		if (this.latestWriteTime < latestWriteTime) {
			this.latestWriteTime = latestWriteTime;
		}
	}

	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	/**
	 * Active this session, so that this Session would not be remove by SessionLifeCycle.
	 * 
	 * @throws IllegalStateException
	 * @since 1.0
	 */
	public void active() throws IllegalStateException {
		if (!closed) {
			activeTime = System.currentTimeMillis();
		} else {
			throw new IllegalStateException("this session has been closed!");
		}
	}
	@Override
	public Object getAttribute(final String name) {
		return sessionValueMap.get(name);
	}

	@Override
	public void setAttribute(final String name, final Object value) {
		if (!open) {
			throw new IllegalStateException("Session is not open or has been closed!");
		}
		sessionValueMap.put(name, value);
	}

	@Override
	public void removeAttribute(final String name) {
		if (!open) {
			throw new IllegalStateException("Session is not open or has been closed!");
		}
		sessionValueMap.remove(name);
	}

	@Override
	public int getSendBufferSize() {
		return sendBufferSize;
	}

	@Override
	public void setSendBufferSize(int sendBufferSize) {
		if (sendBufferSize < 1)
		    throw new IllegalArgumentException();
		this.sendBufferSize = sendBufferSize;
	}
}
