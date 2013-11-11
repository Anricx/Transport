package com.gmail.dengtao.joe.transport;

import java.nio.ByteBuffer;

import com.gmail.dengtao.joe.transport.filter.FilterChain;
import com.gmail.dengtao.joe.transport.handler.Handler;

/**
 * Connector's common methods implementation.
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
abstract class AbstarctConnector implements Connector {

	/**
	 * Default size to create ALL incoming and outgoing buffers; this equates to the maximum size of
	 * message that can be sent or received.
	 * <P>
	 * NB: this variable is ONLY checked at construction time; you must alter it BEFORE constructing
	 * an instance of a server if you want it to have effect (or use the alternative constructor that
	 * allows you to specify a custom value for that instance only).
	 */
	public static final int DEFAULT_BUFFER_SIZE = 512;
	protected int receiveBufferSize = DEFAULT_BUFFER_SIZE; // use 538 ~ 548, recommend 538 for the best performance.
	protected int sendBufferSize = DEFAULT_BUFFER_SIZE; // use 538 ~ 548, recommend 538 for the best performance.

	protected Handler handler; 			// Handles all I/O events fired by Acceptor

	protected final Object receiveBufferLock = new Object();	// ReceiveBuffer Lock
	protected final Object activeLock = new Object();	// active lock
	
	protected boolean active = false; 	// if the instance is active

	// reveive data buffer
	protected ByteBuffer receiveBuffer;
	
	// default FilterChain for this connector
	protected FilterChain filterChain;
	
	protected long selectTimeout = 100;			// If positive, block for up to timeout milliseconds, more or less, while waiting for a channel to become ready; if zero, block indefinitely; must not be negative

	/**
     * @return true if the instance is active
     * @since 1.0
     */
	public boolean isActive() {
		synchronized (activeLock) {
			return active;
		}
	}
	
	/**
	 * Get current selector's timeout
	 * @return current selector's timeout in milliseconds
	 * @since 1.0
	 */
	public long getSelectTimeout() {
		return selectTimeout;
	}

	/**
	 * Set selector's timeout
	 * @param selectTimeout milliseconds
	 * @since 1.0
	 */
	public void setSelectTimeout(long selectTimeout) {
		if (selectTimeout < 0) {
			throw new IllegalArgumentException("Negative timeout:" + selectTimeout);
		}
		this.selectTimeout = selectTimeout;
	}

	@Override
	public void setHandler(Handler handler) {
		if (handler == null) {
			throw new IllegalStateException(
                    "handler can't be null!");
		}
		this.handler = handler;
	}
	
	/**
     * @return The send buffer's size of this session, in bytes.
     * @since 1.0
     */
    public int getSendBufferSize() {
		return sendBufferSize;
	}
    
    /**
	 * Config the send buffer size of this session.
     * @param sendBufferSize capacity The send buffer's capacity, in bytes.
     * @throws IllegalArgumentException If the capacity is a negative integer
     * @since 1.0
     */
	public void setSendBufferSize(int sendBufferSize)  {
		if (sendBufferSize < 1)
		    throw new IllegalArgumentException();
		if (isActive()) {
			throw new IllegalStateException("Connector is already open! you should config this option before this connector is open!");
		}
		this.sendBufferSize = sendBufferSize;
	}
	
	/**
     * @return receiveBufferSize capacity of current buffer's capacity, in bytes
     * @since 1.0
     */
    public int getReceiveBufferSize() {
		return receiveBufferSize;
	}

	/**
     * Allocates a new byte buffer. 
     * The new buffer's position will be zero, its limit will be its capacity, and its mark will be undefined. It will have a backing array, and its array offset will be zero.
     * @param receiveBufferSize capacity The new buffer's capacity, in bytes
     * @throws IllegalArgumentException If the capacity is a negative integer
     * @since 1.0
     */
	public void setReceiveBufferSize(int receiveBufferSize) throws IllegalArgumentException  {
		synchronized (receiveBufferLock) {
			try {
				receiveBuffer = ByteBuffer.allocate(receiveBufferSize);
				this.receiveBufferSize = receiveBufferSize;
			} catch (IllegalArgumentException e) {
				throw e;
			}
		}
	}

	/**
	 * @return {@link FilterChain} for this Acceptor
	 * @since 1.0
	 */
	public FilterChain getFilterChain() {
		return filterChain;
	}
}