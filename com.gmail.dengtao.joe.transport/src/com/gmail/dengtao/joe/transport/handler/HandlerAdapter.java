package com.gmail.dengtao.joe.transport.handler;

import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.Session;

/**
 * Empty Handler, do nothing!
 * 
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public class HandlerAdapter implements Handler {

	@Override
	public void sessionCreated(final Session session) 
			throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void sessionOpened(final Session session) 
			throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void sessionIdle(final Session session, final IdleStatus status)
			throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void sessionClosed(final Session session) 
			throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void exceptionCaught(final Session session, final Throwable cause) {
		// TODO Auto-generated method stub

	}

	@Override
	public void dataReceived(final Session session, final Object data)
			throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void dataNotSent(final Session session, final Object data) 
			throws Exception {
		// TODO Auto-generated method stub

	}
	
	@Override
	public void dataSent(final Session session, final Object data) 
			throws Exception {
		// TODO Auto-generated method stub

	}

}
