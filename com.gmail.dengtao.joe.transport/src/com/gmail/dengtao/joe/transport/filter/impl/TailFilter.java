package com.gmail.dengtao.joe.transport.filter.impl;

import com.gmail.dengtao.joe.transport.filter.Filter;
import com.gmail.dengtao.joe.transport.filter.FilterChain;
import com.gmail.dengtao.joe.transport.filter.FilterEntity;
import com.gmail.dengtao.joe.transport.handler.Handler;
import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.Session;

/**
 * Last filter of the whole FilterChain, this is directly used by {@link FilterChain}.
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public class TailFilter implements Filter {

	private Handler handler;
	
	@Override
	public void sessionCreated(final FilterEntity nextFilter, final Session session)
			throws Exception {
		if (handler != null) {
			handler.sessionCreated(session);
		}
	}

	@Override
	public void sessionOpened(final FilterEntity nextFilter, final Session session)
			throws Exception {
		if (handler != null) {
			handler.sessionOpened(session);
		}
	}

	@Override
	public void sessionIdle(final FilterEntity nextFilter, final Session session, final IdleStatus status)
			throws Exception {
		if (handler != null) {
			handler.sessionIdle(session, status);
		}
	}

	@Override
	public void sessionClosed(final FilterEntity nextFilter, final Session session)
			throws Exception {
		if (handler != null) {
			handler.sessionClosed(session);
		}
	}

	@Override
	public void exceptionCaught(final FilterEntity nextFilter, final Session session,
			Throwable cause) {
		if (handler != null) {
			handler.exceptionCaught(session, cause);	// notify exception caught
		}
	}

	@Override
	public void dataReceived(final FilterEntity nextFilter, final Session session,
			Object data) throws Exception {
		if (handler != null) {
			handler.dataReceived(session, data);
		}
	}

	@Override
	public void sendData(FilterEntity nextEntity, Session session, Object data)
			throws Exception {
		session.sendData(data);
	}
	
	@Override
	public void pushData(final FilterEntity nextEntity, final Session session)
			throws Exception {
		session.pushData();
	}

	@Override
	public void dataSent(final FilterEntity nextFilter, final Session session,
			Object data) throws Exception {
		if (handler != null) {
			handler.dataSent(session, data);
		}
	}

	@Override
	public void dataNotSent(FilterEntity nextEntity, Session session,
			Object data) throws Exception {
		if (handler != null) {
			handler.dataNotSent(session, data);
		}
	}

	public void setHandler(Handler handler) {
		this.handler = handler;
	}

	@Override
	public void init() {
		// do nothing
	}

	@Override
	public void destroy() {
		// do nothing
	}

}
