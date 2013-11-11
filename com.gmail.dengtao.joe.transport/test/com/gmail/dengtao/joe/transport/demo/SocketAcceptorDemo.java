package com.gmail.dengtao.joe.transport.demo;

import com.gmail.dengtao.joe.transport.SocketAcceptor;
import com.gmail.dengtao.joe.transport.demo.handler.DemoServerHandler;

public class SocketAcceptorDemo {
	
	public static void main(String[] args) {
		try {
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
			System.out.println("Listening /" + host + ":" + port);
			SocketAcceptor acceptor = new SocketAcceptor(host, port);
			acceptor.init();
			acceptor.setSelectTimeout(1);
			acceptor.setHandler(new DemoServerHandler());
			acceptor.start();
			System.out.println("Acceptor Stop!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
