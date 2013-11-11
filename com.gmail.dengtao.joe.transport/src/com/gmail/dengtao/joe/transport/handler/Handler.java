package com.gmail.dengtao.joe.transport.handler;

import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.Session;


/**
 * Handles all I/O events fired by Acceptor or Connector
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public interface Handler {
	
	/**
	 * Invoked from an I/O processor thread when a new connection has been created.
     * Because this method is supposed to be called from the same thread that
     * handles I/O of multiple sessions, please implement this method to perform
     * tasks that consumes minimal amount of time such as socket parameter
     * and user-defined session attribute initialization.
	 * @param session
	 * @since 1.0
	 */
    void sessionCreated(final Session session) throws Exception;

    /**
     * Invoked when a connection has been opened.  This method is invoked after
     * {@link #sessionCreated(Session)}. 
     * @param session
     * @since 1.0
     */
    void sessionOpened(final Session session) throws Exception;

    /**
     * Invoked when a connection is closed.
     * @param session
     * @since 1.0
     */
    void sessionClosed(final Session session) throws Exception;
    
    /**
     * Invoked with the related {@link IdleStatus} when a connection becomes idle.
     * @param session
     * @param status
     * @throws Exception
     * @since 1.0
     */
    public void sessionIdle(final Session session, final IdleStatus status) throws Exception;

    /**
     * Invoked when any exception is thrown by user {@link Handler}
     * implementation or by Acceptor.
     * @param session
     * @param cause
     * @since 1.0
     */
    void exceptionCaught(final Session session, final Throwable cause);

    /**
     * Invoked when data is received.
     * @param session 
     * @param data received data
     * @throws Exception
     * @since 1.0
     */
    void dataReceived(final Session session, final Object data) throws Exception;

    /**
     * Invoked when data written is failed.
     * @param session
     * @param data
     * @throws Exception
     * @since 1.0
     */
    void dataNotSent(final Session session, final Object data) throws Exception;
    
    /**
     * Invoked when data written is successfuly.
     * @param session 
     * @param data
     * @since 1.0
     */
    void dataSent(final Session session, final Object data) throws Exception;

}