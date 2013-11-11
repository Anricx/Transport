package com.gmail.dengtao.joe.transport.demo.handler;

import java.nio.ByteBuffer;

import com.gmail.dengtao.joe.transport.handler.Handler;
import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.Session;

public class DemoClientHandler implements Handler {
	
	@Override
	public void sessionCreated(Session session) throws Exception {
		System.out.println("Create:" + session);
		session.setIdleTime(IdleStatus.READ_IDLE, 10);
		session.setIdleTime(IdleStatus.WRITE_IDLE, 10);
	}
	
	@Override
	public void sessionOpened(Session session) throws Exception {
		System.out.println("Open:" + session);
		session.send(ByteBuffer.wrap((System.currentTimeMillis() + "A").getBytes()).array());
	}

	@Override
	public void sessionIdle(Session session, IdleStatus status)
			throws Exception {
		System.out.println("Idle:" + status);
		session.send(ByteBuffer.wrap((System.currentTimeMillis() + "A").getBytes()).array());
		session.send(ByteBuffer.wrap((System.currentTimeMillis() + "B").getBytes()).array());
		session.send(ByteBuffer.wrap((System.currentTimeMillis() + "C").getBytes()).array());
	}

	@Override
	public void sessionClosed(Session session) throws Exception {
		System.out.println("Closed:" + session);
	}
	
	@Override
	public void exceptionCaught(Session session, Throwable cause) {
		System.out.println("Exception:" + cause);
		cause.printStackTrace();
	}

	@Override
	public void dataReceived(Session session, Object data) throws Exception {
		System.out.println("Received:" + new String((byte[]) data));
	}

	@Override
	public void dataNotSent(Session session, Object data) throws Exception {
		System.out.println("NotSent:" + new String((byte[]) data));
	}

	@Override
	public void dataSent(Session session, Object data) throws Exception {
		System.out.println("Sent:" + new String((byte[]) data));
	}
}