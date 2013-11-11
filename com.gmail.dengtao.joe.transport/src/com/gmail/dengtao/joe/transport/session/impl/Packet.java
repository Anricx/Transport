package com.gmail.dengtao.joe.transport.session.impl;

import java.nio.ByteBuffer;

/**
 * Pakcet
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
class Packet {

	private Object data;
	private ByteBuffer buf;
	
	public Packet(final Object data) {
		super();
		this.data = data;
		if (data instanceof byte[]) {
			buf = ByteBuffer.wrap((byte[]) data);
		} else if (data instanceof ByteBuffer) {
			buf = (ByteBuffer) data;
		} else {
			buf = ByteBuffer.wrap(data.toString().getBytes());
		}
	}

	public Object getData() {
		return data;
	}

	public ByteBuffer getBuf() {
		return buf;
	}
	
}
