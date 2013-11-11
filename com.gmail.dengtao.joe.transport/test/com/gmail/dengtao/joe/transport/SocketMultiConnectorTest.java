package com.gmail.dengtao.joe.transport;

import org.junit.Test;

import com.gmail.dengtao.joe.transport.demo.handler.DemoClientHandler;

public class SocketMultiConnectorTest {

	@Test
	public void test() throws Exception {
		final SocketMultiConnector connector = new SocketMultiConnector();
		
		// sometimes, you should init before...
		connector.init();
		
		// Custom setting...
		connector.setSelectTimeout(100);
		connector.setReceiveBufferSize(1024);
		connector.setSendBufferSize(1024);

		// Inject the biz handler.
		connector.setHandler(new DemoClientHandler());
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					connector.connect();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}).start();
		
		connector.open("127.0.0.1", 7000);
		Thread.sleep(1000);
		connector.open("127.0.0.1", 7001);
		Thread.sleep(1000);
		connector.open("127.0.0.1", 7002);
		for(;;) { try { Thread.sleep(10000); } catch (InterruptedException e) { } }
	}

}
