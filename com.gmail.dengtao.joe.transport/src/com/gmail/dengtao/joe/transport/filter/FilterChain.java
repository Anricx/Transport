package com.gmail.dengtao.joe.transport.filter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.gmail.dengtao.joe.transport.filter.impl.TailFilter;
import com.gmail.dengtao.joe.transport.handler.Handler;
import com.gmail.dengtao.joe.transport.session.IdleStatus;
import com.gmail.dengtao.joe.transport.session.Session;

/**
 * A container of {@link Filter}s that forwards {@link Handler} events
 * to the consisting filters and terminal {@link Handler} sequentially.
 * 
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @since 1.0
 * @version 1.0
 */
public class FilterChain {
	
	/** filter name to filter */
	private final Map<String, FilterEntity> filterEntites = new ConcurrentHashMap<String, FilterEntity>();
	/** The chain head */
    private final FilterEntity head;
    /** The chain tail */
    private final FilterEntity tail;
    /**  */
    private TailFilter tailFilter = new TailFilter();
    
    /**
     * Create a new default chain, associated with a session. It will only contain a
     * HeadFilter and a TailFilter.
     */
	public FilterChain() {
		head = new FilterEntity(null, null, "head", null);
        tail = new FilterEntity(head, null, "tail", tailFilter);
        head.setNextEntity(tail);
	}

	/**
	 * Adds the specified filter with the specified name at the beginning of this chain.
	 * @param name The filter's name
     * @param filter The filter to add
     * @throws IllegalArgumentException
	 * @since 1.0
	 */
	public synchronized void addFirst(String name, Filter filter) throws IllegalArgumentException {
		checkAddable(name);
		register(head, name, filter);
	}

	/**
	 * Adds the specified filter with the specified name at the end of this chain.
	 * 
	 * @param name The filter's name
     * @param filter The filter to add
     * @throws IllegalArgumentException
     * @since 1.0
	 */
	public synchronized void addLast(String name, Filter filter) throws IllegalArgumentException {
		checkAddable(name);
		register(tail.getPrevEntity(), name, filter);
	}

	/**
	 * Replace the specified filter with name in this chain
	 * @param name filter name need to be replace
	 * @param newFilter new filter
	 * @return true if replace is success
	 * @throws IllegalArgumentException the specified filter name is not registered in this chain.
	 * @since 1.0
	 */
	public synchronized boolean replace(String name, Filter newFilter) throws IllegalArgumentException {
		if (newFilter == null) {
			throw new IllegalArgumentException("Filter is null.");
		}
		FilterEntity entity = checkOldName(name);
		if (entity.getFilter() != null) {
			entity.getFilter().destroy();
		}
        entity.setFilter(newFilter);
        newFilter.init();
        return true;
    }
	
	/**
	 * Remove the specified filter with name in this chain
	 * @throws IllegalArgumentException the specified filter name is not registered in this chain.
	 * @param name
	 * @return true if remove is success
	 * @since 1.0
	 */
	public synchronized boolean remove(String name) throws IllegalArgumentException {
		FilterEntity entity = checkOldName(name);
        deregister(entity);
        return true;
    }
	
	/**
     * Deregister FilterEntity to this FilterChain
     * @param entity entity to be removed
     * @since 1.0
     */
	private void deregister(FilterEntity entity) {
		FilterEntity prevEntry = entity.getPrevEntity();
		FilterEntity nextEntry = entity.getNextEntity();
        prevEntry.setNextEntity(nextEntry);
        nextEntry.setPrevEntity(prevEntry);
        filterEntites.remove(entity.getName());
        if (entity.getFilter() != null) {
        	entity.getFilter().destroy();
	 	}
	}
	
	/**
     * Throws an exception when the specified filter name is not registered in this chain.
     *
     * @throws IllegalArgumentException the specified filter name is not registered in this chain.
     * @return An filter entity with the specified name.
     * @since 1.0
     */
    private FilterEntity checkOldName(String baseName) throws IllegalArgumentException {
    	FilterEntity e = filterEntites.get(baseName);
        if (e == null) {
            throw new IllegalArgumentException("Filter not found:" + baseName);
        }
        return e;
    }
	
    /**
     * Regitster FilterEntity to this FilterChain
     * @param prevEntry
     * @param name filter name
     * @param filter filter instances
     * @since 1.0
     */
	private void register(FilterEntity prevEntry, String name, Filter filter) {
		if (filter == null) {
			throw new IllegalArgumentException("Filter is null.");
		}
		FilterEntity newEntry = new FilterEntity(prevEntry, prevEntry.getNextEntity(), name, filter);
	 	prevEntry.getNextEntity().setPrevEntity(newEntry);
	 	prevEntry.setNextEntity(newEntry);
	 	filterEntites.put(name, newEntry);
	 	if (filter != null) {
	 		filter.init();
	 	}
	}

	/**
     * Checks the specified filter name is already taken and throws an exception if already taken.
     * @param name filter name
     * @throws IllegalArgumentException
     */
    private void checkAddable(String name) throws IllegalArgumentException {
        if (filterEntites.containsKey(name)) {
            throw new IllegalArgumentException(
                    "Other filter is using the same name '" + name + "'");
        }
    }
	
	/**
     * Fires a {@link Handler#sessionCreated(Session)} event. Most users don't need to
     * call this method at all. Please use this method only when you implement a new transport
     * or fire a virtual event.
     * @param session created session
     * @since 1.0
     */
    public void fireSessionCreated(Session session) {
    	callNextSessionCreated(head.getNextEntity(), session);
    }
    
    private void callNextSessionCreated(FilterEntity entity, Session session) {
        try {
        	Filter filter = entity.getFilter();
        	filter.sessionCreated(entity.getNextEntity(), session);
        } catch (Throwable e) {
            fireExceptionCaught(session, e);
        }
    }
    
    /**
     * Fires a {@link Handler#sessionOpened(Session)} event. Most users don't need to call
     * this method at all. Please use this method only when you implement a new transport or
     * fire a virtual event.
     * @param session opened session
     * @since 1.0
     */
    public void fireSessionOpened(Session session) {
    	callNextSessionOpened(head.getNextEntity(), session);
    }

    private void callNextSessionOpened(FilterEntity entity, Session session) {
        try {
        	Filter filter = entity.getFilter();
        	filter.sessionOpened(entity.getNextEntity(), session);
        } catch (Throwable e) {
            fireExceptionCaught(session, e);
        }
    }

    /**
     * Fires a {@link Handler#sessionIdle(Session, IdleStatus)} event. Most users don't need to call
     * this method at all. Please use this method only when you implement a new transport or
     * fire a virtual event.
     * @param session idle session
     * @param status idle status {@link IdleStatus#READ_IDLE} or {@link IdleStatus#WRITE_IDLE}
     * @since 1.0
     */
    public void fireSessionIdle(Session session, IdleStatus status) {
    	callNextSessionIdle(head.getNextEntity(), session, status);
    }

    private void callNextSessionIdle(FilterEntity entity, Session session, IdleStatus status) {
        try {
        	Filter filter = entity.getFilter();
        	filter.sessionIdle(entity.getNextEntity(), session, status);
        } catch (Throwable e) {
            fireExceptionCaught(session, e);
        }
    }
    
    /**
     * Fires a {@link Handler#sessionClosed(Session)} event. Most users don't need to call
     * this method at all. Please use this method only when you implement a new transport or
     * fire a virtual event.
     * @param session to be closed
     * @since 1.0
     */
    public void fireSessionClosed(Session session) {
    	callNextSessionClosed(head.getNextEntity(), session);
    }

    private void callNextSessionClosed(FilterEntity entity, Session session) {
        try {
        	Filter filter = entity.getFilter();
        	filter.sessionClosed(entity.getNextEntity(), session);
        } catch (Throwable e) {
            fireExceptionCaught(session, e);
        }
    }
    
    /**
     * Fires a {@link Handler#exceptionCaught(Session, Throwable)} event. Most users don't
     * need to call this method at all. Please use this method only when you implement a new
     * transport or fire a virtual event.
     * @param session the session whitch exception happened!
     * @param cause The exception cause
     */
    public void fireExceptionCaught(Session session, Throwable cause) {
        callNextExceptionCaught(head.getNextEntity(), session, cause);
    }

    private void callNextExceptionCaught(FilterEntity entity, Session session, Throwable cause) {
    	entity.getFilter().exceptionCaught(entity.getNextEntity(), session, cause);
    }
    
    /**
     * Fires a {@link Handler#dataReceived(Session, Object)} event. Most users don't need to
     * call this method at all. Please use this method only when you implement a new transport
     * or fire a virtual event.
     * 
     * @param data The received data
     */
    public void fireDataReceived(Session session, Object data) {
        callNextDataReceived(head.getNextEntity(), session, data);
    }

    private void callNextDataReceived(FilterEntity entity, Session session, Object data) {
        try {
        	entity.getFilter().dataReceived(entity.getNextEntity(), session, data);
        } catch (Throwable e) {
        	fireExceptionCaught(session, e);
        }
    }

    /**
     * Fires a {@link Filter#sendData(FilterEntity, Session, Object)} event. Most users don't need to
     * call this method at all. Please use this method only when you implement a new transport
     * or fire a virtual event.
     * @param session
     * @param data
     * @since 1.0
     */
	public void fireSendData(Session session, Object data) {
		callNextSendData(head.getNextEntity(), session, data);
	}
    
	private void callNextSendData(FilterEntity entity, Session session, Object data) {
        try {
        	entity.getFilter().sendData(entity.getNextEntity(), session, data);
        } catch (Throwable e) {
        	fireExceptionCaught(session, e);
        }
    }
    
    /**
     * Fires a {@link Filter#dataNotSent(FilterEntity, Session, Object)} event. Most users don't need to call
     * this method at all. Please use this method only when you implement a new transport or
     * fire a virtual event.
     */
    public void firePushData(Session session) {
    	callNextPushData(head.getNextEntity(), session);
    }

    private void callNextPushData(FilterEntity entity, Session session) {
        try {
        	entity.getFilter().pushData(entity.getNextEntity(), session);
        } catch (Throwable e) {
        	fireExceptionCaught(session, e);
        }
    }
    
    /**
     * Fires a {@link Handler#dataNotSent(Session, Object)} event. Most users don't need to call
     * this method at all. Please use this method only when you implement a new transport or
     * fire a virtual event.
     */
    public void fireDataNotSent(Session session, Object data) {
    	callNextDataNotSent(head.getNextEntity(), session, data);
    }

    private void callNextDataNotSent(FilterEntity entity, Session session, Object data) {
        try {
        	entity.getFilter().dataNotSent(entity.getNextEntity(), session, data);
        } catch (Throwable e) {
        	fireExceptionCaught(session, e);
        }
    }
    
    /**
     * Fires a {@link Handler#dataSent(Session, Object)} event. Most users don't need to call
     * this method at all. Please use this method only when you implement a new transport or
     * fire a virtual event.
     */
    public void fireDataSent(Session session, Object data) {
    	callNextDataSent(head.getNextEntity(), session, data);
    }

    private void callNextDataSent(FilterEntity entity, Session session, Object data) {
        try {
        	entity.getFilter().dataSent(entity.getNextEntity(), session, data);
        } catch (Throwable e) {
        	fireExceptionCaught(session, e);
        }
    }

	public void setHandler(Handler handler) {
		tailFilter.setHandler(handler);
	}

}