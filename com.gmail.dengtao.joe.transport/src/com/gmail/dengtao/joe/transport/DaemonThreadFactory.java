package com.gmail.dengtao.joe.transport;

import java.util.concurrent.ThreadFactory;

/**
 * Daemon Thread Factory.
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
class DaemonThreadFactory implements ThreadFactory {
    
    private String prefix;
    
    public DaemonThreadFactory(String prefix) {
        this.prefix = prefix;
    }
    
	/**
	 * Create new thread.
	 * @param runable a runnable to be executed by new thread instance
	 * @param name the new name for this thread.
	 * @return new thread constructed thread, or null if the request to create a thread is rejected
	 */
	public Thread newThread(Runnable runable, String name) {
        Thread thread = new Thread(runable);
        thread.setDaemon(true);
        if (name != null && name.length() > 0) thread.setName(name);
        return thread;
    }

	@Override
	public Thread newThread(Runnable runable) {
		return this.newThread(runable, prefix);
	}
    
}