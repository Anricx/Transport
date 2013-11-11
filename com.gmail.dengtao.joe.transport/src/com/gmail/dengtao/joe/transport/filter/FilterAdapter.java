package com.gmail.dengtao.joe.transport.filter;

import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.Session;

/**
 * An adapter class for {@link Filter}.  You can extend
 * this class and selectively override required event filter methods only.  All
 * methods forwards events to the next filter by default.
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public class FilterAdapter implements Filter {

	@Override
	public void init() {
		// Empty...
	}

	@Override
	public void destroy() {
		// Empty...
	}

	@Override
	public void sessionCreated(FilterEntity nextEntity, Session session)
			throws Exception {
		nextEntity.getFilter().sessionCreated(nextEntity.getNextEntity(), session);		
	}

	@Override
	public void sessionOpened(FilterEntity nextEntity, Session session)
			throws Exception {
		nextEntity.getFilter().sessionOpened(nextEntity.getNextEntity(), session);		
	}

	@Override
	public void sessionIdle(FilterEntity nextEntity, Session session,
			IdleStatus status) throws Exception {
		nextEntity.getFilter().sessionIdle(nextEntity.getNextEntity(), session, status);		
	}

	@Override
	public void sessionClosed(FilterEntity nextEntity, Session session)
			throws Exception {
		nextEntity.getFilter().sessionClosed(nextEntity.getNextEntity(), session);		
	}

	@Override
	public void exceptionCaught(FilterEntity nextEntity, Session session,
			Throwable cause) {
		nextEntity.getFilter().exceptionCaught(nextEntity.getNextEntity(), session, cause);		
	}

	@Override
	public void dataReceived(FilterEntity nextEntity, Session session,
			Object data) throws Exception {
		nextEntity.getFilter().dataReceived(nextEntity.getNextEntity(), session, data);		
	}

	@Override
	public void sendData(FilterEntity nextEntity, Session session, Object data)
			throws Exception {
		nextEntity.getFilter().sendData(nextEntity.getNextEntity(), session, data);		
	}
	
	@Override
	public void pushData(FilterEntity nextEntity, Session session)
			throws Exception {
		nextEntity.getFilter().pushData(nextEntity.getNextEntity(), session);		
	}
	
	@Override
	public void dataSent(FilterEntity nextEntity, Session session,
			Object data) throws Exception {
		nextEntity.getFilter().dataSent(nextEntity.getNextEntity(), session, data);	
	}

	@Override
	public void dataNotSent(FilterEntity nextEntity, Session session,
			Object data) throws Exception {
		nextEntity.getFilter().dataNotSent(nextEntity.getNextEntity(), session, data);	
	}

}
