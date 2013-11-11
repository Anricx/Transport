package com.gmail.dengtao.joe.transport.filter.impl;

import com.gmail.dengtao.joe.transport.filter.Filter;
import com.gmail.dengtao.joe.transport.filter.FilterEntity;
import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.Session;

/**
 * Tail filter do nothing!
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 * @date 2012-10-10
 */
public class MyFilter implements Filter {

	@Override
	public void init() {
		// TODO Auto-generated method stub
		System.out.println("init");
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		System.out.println("destroy");
	}

	@Override
	public void sessionCreated(FilterEntity nextEntity, Session session)
			throws Exception {
		System.out.println("Fired!" + nextEntity.getName());
		nextEntity.getFilter().sessionCreated(nextEntity.getNextEntity(), session);
	}

	@Override
	public void sessionOpened(FilterEntity nextEntity, Session session)
			throws Exception {
		System.out.println("Fired!" + nextEntity.getName());
		nextEntity.getFilter().sessionOpened(nextEntity.getNextEntity(), session);
	}

	@Override
	public void sessionIdle(FilterEntity nextEntity, Session session,
			IdleStatus status) throws Exception {
		System.out.println("Fired!" + nextEntity.getName());
		nextEntity.getFilter().sessionIdle(nextEntity.getNextEntity(), session, status);
	}
	
	@Override
	public void sessionClosed(FilterEntity nextEntity, Session session)
			throws Exception {
		System.out.println("Fired!" + nextEntity.getName());
		nextEntity.getFilter().sessionClosed(nextEntity.getNextEntity(), session);
	}

	@Override
	public void exceptionCaught(FilterEntity nextEntity, Session session,
			Throwable cause) {
		System.out.println("exceptionCaught Fired!");
		nextEntity.getFilter().exceptionCaught(nextEntity.getNextEntity(), session, cause);
	}

	@Override
	public void dataReceived(FilterEntity nextEntity, Session session,
			Object data) throws Exception {
		System.out.println("Fired!" + nextEntity.getName());
		nextEntity.getFilter().dataReceived(nextEntity.getNextEntity(), session, data);		
	}

	@Override
	public void pushData(FilterEntity nextEntity, Session session)
			throws Exception {
		System.out.println("Fired!" + nextEntity.getName());
		nextEntity.getFilter().pushData(nextEntity.getNextEntity(), session);		
	}

	@Override
	public void dataNotSent(FilterEntity nextEntity, Session session,
			Object data) throws Exception {
		System.out.println("Fired!" + nextEntity.getName());
		nextEntity.getFilter().dataNotSent(nextEntity.getNextEntity(), session, data);
	}

	@Override
	public void dataSent(FilterEntity nextEntity, Session session, Object data)
			throws Exception {
		System.out.println("Fired!" + nextEntity.getName());
		nextEntity.getFilter().dataSent(nextEntity.getNextEntity(), session, data);
	}

	@Override
	public void sendData(FilterEntity nextEntity, Session session, Object data)
			throws Exception {
		// TODO Auto-generated method stub
		
	}

}
