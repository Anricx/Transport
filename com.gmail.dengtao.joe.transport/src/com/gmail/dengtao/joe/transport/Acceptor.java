package com.gmail.dengtao.joe.transport;

import com.gmail.dengtao.joe.transport.handler.Handler;

/**
 * Accepts incoming connection, communicates with clients, and fires events to Handlers. 
 * Then events for incoming connections will be sent to the specified default Handler. 
 * Threads accept incoming connections start automatically when start() is invoked.<br>
 * <pre>
 * <b>ATTENTIONS:</b><br>
 * 1.Before start() this Acceptor, you should call {@link #init()} to initlize resources and {@link #setHandler(Handler)} to set Handler for this Acceptor
 * 2.Thread will be blocked in start() method.</pre>
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public interface Acceptor {

	/**
	 * Initializaing resources, etc.
	 * 
	 * @throws Exception
	 * @since 1.0
	 */
	void init() throws Exception;
	
	/**
	 * Start service<br>
	 * Thread will be blocked in here.
	 * 
	 * @throws Exception
	 * @since 1.0
	 */
	void start() throws Exception;
	
	/**
	 * Start service<br>
	 * notify lock when acceptor is serviceable.
	 * @param lock when this acceptor is serviceable lock will be notified!
	 * @since 1.0
	 */
	void start(Object lock) throws Exception;

	/**
	 * Stop service and release resources, etc.
	 * 
	 * @since 1.0
	 */
	void stop();
	
	/**
	 * Set Handler for this acceptor
	 * 
	 * @param handler
	 * @since 1.0
	 */
	void setHandler(Handler handler);
	
}