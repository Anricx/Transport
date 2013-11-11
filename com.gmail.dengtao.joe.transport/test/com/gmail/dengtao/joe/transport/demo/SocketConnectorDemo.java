package com.gmail.dengtao.joe.transport.demo;

import com.gmail.dengtao.joe.transport.SocketConnector;
import com.gmail.dengtao.joe.transport.demo.handler.DemoClientHandler;

public class SocketConnectorDemo {
	
	public static void main(String[] args) throws Exception {
		
		if (args.length < 2) {
			System.out.println("Usages:host port");
			System.exit(-1);
		}
		String host = null;
		int port = 0;
		try {
			host = args[0];
			port = Integer.parseInt(args[1]);
		} catch (Exception e) {
			System.out.println("Usages:host port");
			System.exit(-1);
		}
		System.out.println("Connecting /" + host + ":" + port);
		
		SocketConnector connector = new SocketConnector();
		
		// sometimes, you should init before...
		connector.init();
		
		// Custom setting...
		connector.setSelectTimeout(100);
		connector.setReceiveBufferSize(1024);
		connector.setSendBufferSize(1024);
		
		// Inject the biz handler.
		connector.setHandler(new DemoClientHandler());

		// Open connection to host:port
		// Thread will be blocked in here.
		connector.open(host, port);
	}

}
