package com.gmail.dengtao.joe.transport.session;

/**
 * Represents the type of idleness of {@link Session}.  There are two types of idleness:
 * <ul>
 *   <li>{@link #READ_IDLE} - No data is coming from the remote peer.</li>
 *   <li>{@link #WRITE_IDLE} - Session is not writing any data.</li>
 * </ul>
 * <p>
 * Idle time settings are all disabled by default.  You can enable them
 * using {@link Session#setIdleTime(IdleStatus,long)}.
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public enum IdleStatus {
	
	/** Represents the session status that no data is coming from the remote peer. */
	READ_IDLE,
	/** Represents the session status that the session is not writing any data. */
	WRITE_IDLE
	
}