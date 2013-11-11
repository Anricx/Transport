package com.gmail.dengtao.joe.transport.filter;

import com.gmail.dengtao.joe.transport.handler.Handler;
import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.Session;


/**
 * A filter which intercepts {@link Handler}  events like Servlet
 * filters.  Filters can be used for these purposes:
 * <ul>
 *   <li>Event logging,</li>
 *   <li>Performance measurement,</li>
 *   <li>Authorization,</li>
 *   <li>Overload control,</li>
 *   <li>Message transformation (e.g. encryption and decryption, ...),</li>
 *   <li>and many more.</li>
 * </ul>
 * <p>
 * <strong>You can implement your filters to filter message you recived.</strong>
 *
 * <h3>The Life Cycle</h3>
 * {@link Filter}s are activated only when they are inside {@link FilterChain}.
 * <p>
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public interface Filter {
	
	/**
     * Invoked by {@link FilterChain} when this filter
     * is added to a {@link FilterChain} at the first time, so you can
     * initialize shared resources.
     */
    void init();

    /**
     * Invoked by {@link FilterChain} when this filter
     * is not used by any {@link FilterChain} anymore, so you can destroy
     * shared resources.
     */
    void destroy();

	 /**
     * Filters {@link Handler#sessionCreated(Session)} event.
     */
    void sessionCreated(FilterEntity nextEntity, Session session)
            throws Exception;

    /**
     * Filters {@link Handler#sessionOpened(Session)} event.
     */
    void sessionOpened(FilterEntity nextEntity, Session session)
            throws Exception;
    
    /**
     * Filters {@link Handler#sessionIdle(Session, IdleStatus)} event.
     */
    void sessionIdle(FilterEntity nextEntity, Session session, IdleStatus status)
            throws Exception;
    
    /**
     * Filters {@link Handler#sessionClosed(Session)} event.
     */
    void sessionClosed(FilterEntity nextEntity, Session session)
            throws Exception;
	
    /**
     * Filters {@link Handler#exceptionCaught(Session,Throwable)}
     * event.
     */
    void exceptionCaught(FilterEntity nextEntity, Session session,
            Throwable cause);

    /**
     * Filters {@link Handler#dataReceived(Session,Object)}
     * event.
     */
    void dataReceived(FilterEntity nextEntity, Session session,
            Object data) throws Exception;
    
    /**
     * Send Data
     */
    void sendData(FilterEntity nextEntity, Session session,
            Object data) throws Exception;
    
    /**
     * Push data to remote peer.
     */
	void pushData(FilterEntity nextEntity, Session session) throws Exception;

    /**
     * Filters {@link Handler#dataNotSent(Session,Object)}
     * event.
     */
    void dataNotSent(FilterEntity nextEntity, Session session, Object data) throws Exception;
    
    /**
     * Filters {@link Handler#dataSent(Session,Object)}
     * event.
     */
    void dataSent(FilterEntity nextEntity, Session session, Object data) throws Exception;


}