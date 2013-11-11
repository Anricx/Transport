package com.gmail.dengtao.joe.transport;

import com.gmail.dengtao.joe.transport.handler.Handler;

/**
 * Define connector to connect remote peer
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public interface Connector {

	/**
	 * Initializaing resources, etc.
	 * 
	 * @throws Exception
	 * @since 1.0
	 */
	void init() throws Exception;
	
	/**
	 * Open connection to host:port<br>
	 * Thread will be blocked in here.
	 * @param host
	 * @param port
	 * @throws Exception
	 * @since 1.0
	 */
	void open(String host, int port) throws Exception;
	
	/**
	 * Open connection to host:port<br>
	 * Thread will be blocked in here.<br>
	 * notify lock when connector is serviceable.
	 * @param host
	 * @param port
	 * @param lock when this connector is serviceable lock will be notified!
	 * @throws Exception
	 * @since 1.0
	 */
	void open(String host, int port, Object lock) throws Exception;

	/**
	 * Release resources, etc.
	 * 
	 * @since 1.0
	 */
	void close();
	
	/**
	 * Set Handler for this connector
	 * 
	 * @param handler
	 * @since 1.0
	 */
	void setHandler(Handler handler);

}
