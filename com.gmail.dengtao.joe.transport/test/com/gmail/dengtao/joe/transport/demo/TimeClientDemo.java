package com.gmail.dengtao.joe.transport.demo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import com.gmail.dengtao.joe.transport.DatagramConnector;
import com.gmail.dengtao.joe.transport.handler.HandlerAdapter;
import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.Session;

/**
 * Time client demo. get time form server.
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public class TimeClientDemo {

	private static String server = "time-nw.nist.gov";
	private static int port = 37;
	
	public static void main(String[] args) throws Exception {
		DatagramConnector connector = new DatagramConnector();
		
		// sometimes, you should init before...
		connector.init();
		
		// Custom setting...
		connector.setSelectTimeout(100);
		connector.setReceiveBufferSize(1024);
		connector.setSendBufferSize(1024);
		
		// Inject the biz handler.
		connector.setHandler(new TimeClientHandler());
		
		// Open connection to host:port
		// Thread will be blocked in here.
		connector.open(server, port);
	}
	
	static class TimeClientHandler extends HandlerAdapter {

		@Override
		public void sessionCreated(Session session) throws Exception {
			System.out.println("Create:" + session);
			session.setIdleTime(IdleStatus.READ_IDLE, 10);
			session.setIdleTime(IdleStatus.WRITE_IDLE, 1);
		}

		@Override
		public void sessionOpened(Session session) throws Exception {
			System.out.println("Open:" + session);
		}
		
		@Override
		public void sessionIdle(Session session, IdleStatus status)
				throws Exception {
			System.out.println("Idle:" + status);
			// send get time request.
			ByteBuffer buffer = ByteBuffer.allocate(8);
		    buffer.order(ByteOrder.BIG_ENDIAN);
		    // send a byte of data to the server
		    buffer.put((byte) 0);
		    buffer.flip();
		    session.send(buffer);
		}

		@Override
		public void dataReceived(Session session, Object data)
				throws Exception {
			ByteBuffer buffer = ByteBuffer.allocate(8);
			buffer.order(ByteOrder.BIG_ENDIAN);
			
			 // fill the first four bytes with zeros
		    buffer.putInt(0);
			buffer.put((byte[]) data);
		    buffer.flip();

		    System.out.println(Arrays.toString(buffer.array()));
		    
		    // convert seconds since 1900 to a java.util.Date
		    long secondsSince1900 = buffer.getLong();
		    long differenceBetweenEpochs = 2208988800L;
		    long secondsSince1970 = secondsSince1900 - differenceBetweenEpochs;
		    long msSince1970 = secondsSince1970 * 1000;
		    Date time = new Date(msSince1970);

		    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		    
		    System.out.println(sdf.format(time));
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
		public void dataNotSent(Session session, Object data) throws Exception {
			System.out.println("NotSent:" + new String((byte[]) data));
		}
		
		@Override
		public void dataSent(Session session, Object data) throws Exception {
			//ByteBuffer buffer = (ByteBuffer) data;
			//System.out.println("Sent:" + new String(buffer.array()));
		}
		
	}
}
