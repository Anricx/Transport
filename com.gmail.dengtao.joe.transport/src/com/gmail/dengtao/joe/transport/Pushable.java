package com.gmail.dengtao.joe.transport;

import com.gmail.dengtao.joe.transport.session.impl.DatagramSession;

/**
 * This interface was defined to provide method for {@link DatagramAcceptor}, {@link DatagramConnector} and {@link DatagramMultiConnector} to notify session has data to be sent or not.
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public interface Pushable {

	/**
	 * Notify this session has data to be sent.
	 * @param session
	 * @since 1.0
	 */
	public void add(DatagramSession session);
	
	/**
	 * Notify this session has no more data to be sent.
	 * @param session
	 * @since 1.0
	 */
	public void remove(DatagramSession session);
	
}