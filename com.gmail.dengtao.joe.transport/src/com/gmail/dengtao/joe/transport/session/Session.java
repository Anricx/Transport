package com.gmail.dengtao.joe.transport.session;

/**
 * A handle which represents connection between two end-points regardless of
 * transport types.
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public interface Session {
	
	/**
     * Removes the object bound with the specified name from this session. If
     * the session does not have an object bound with the specified name, this
     * method does nothing.
     * 
     * @param name
     *            the name of the object to remove from this session
     * @exception IllegalStateException
     *                if this method is called on an invalidated session
     * @since 1.0
     */
    public void removeAttribute(final String name);
    
	/**
     * Returns the object bound with the specified name in this session, or
     * <code>null</code> if no object is bound under the name.
     * 
     * @param name
     *            a string specifying the name of the object
     * @return the object with the specified name
     * @exception IllegalStateException
     *                if this method is called on an invalidated session
     * @since 1.0
     */
    public Object getAttribute(final String name);
	
	/**
     * Binds an object to this session, using the name specified. If an object
     * of the same name is already bound to the session, the object is replaced.
     * @param name
     *            the name to which the object is bound; cannot be null
     * @param value
     *            the object to be bound
     * @exception IllegalStateException
     *                if this method is called on an invalidated session
     * @since 1.0
     */
    public void setAttribute(final String name, final Object value);
    
	/**
	 * Sets idle time for the specified type of idleness in milliseconds.
	 * @param status
	 * @param idleTime
	 * @since 1.0
	 */
	public void setIdleTime(final IdleStatus status, final long idleTime);
	
	/**
	 * @param status
	 * @return Idle time for the specified type of idleness in milliseconds.
	 * @since 1.0
	 */
	public long getIdleTime(final IdleStatus status);
	
	/**
     * @return The send buffer's size of this session, in bytes.
     * @since 1.0
     */
    public int getSendBufferSize();

	/**
	 * Config the send buffer size of this session.
     * @param sendBufferSize capacity The send buffer's capacity, in bytes.
     * @throws IllegalArgumentException If the capacity is a negative integer
     * @since 1.0
     */
	public void setSendBufferSize(int sendBufferSize);
	
	/**
	 * Add data to send list.<br>
	 * remember data will not be directly sent to remote peer, but was add to send queue.
	 * @param data
	 * @since 1.0
	 */
	public void send(final Object data);
	
	/**
	 * Close this session
	 * 
	 * @since 1.0
	 */
	public void close();

	/**
	 * Open this session for io transport
	 * 
	 * @since 1.0
	 */
	public void open();

	/**
	 * Try to push watie send data to remote peer, you do not need to call this method!
	 * 
	 * @since 1.0
	 */
	void pushData();

	/**
	 * Add data to send queue
	 * @param data
	 * @since 1.0
	 */
	void sendData(Object data);

	/**
	 * @return whether this is opened
	 * @since 1.0
	 */
	public boolean isOpen();

	/**
	 * @return whether this session is closed
	 * @since 1.0
	 */
	public boolean isClosed();

}