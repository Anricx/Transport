package com.gmail.dengtao.joe.transport.filter;


/**
 * FilterEntity to stroe Filter relationships
 * @author <a href="mailto:joe.dengtao@gmail.com">DengTao</a>
 * @version 1.0
 * @since 1.0
 */
public class FilterEntity {

	/** Previous FilterEntity */
	private FilterEntity prevEntity;
	/** Next FilterEntity */
	private FilterEntity nextEntity;
	/** filter name */
	private String name;
	/** filter instances */
	private Filter filter;
	/** Next filter instances */
	private Filter nextFilter;

	public FilterEntity(FilterEntity prevEntry, FilterEntity nextEntry,
			String name, Filter filter) {
		if (name == null) {
			throw new IllegalArgumentException("name");
		}
		setPrevEntity(prevEntry);
		setNextEntity(nextEntry);
		this.name = name;
		setFilter(filter);
	}

	public FilterEntity getPrevEntity() {
		return prevEntity;
	}

	public void setPrevEntity(FilterEntity prevEntity) {
		this.prevEntity = prevEntity;
	}

	public FilterEntity getNextEntity() {
		return nextEntity;
	}

	public void setNextEntity(FilterEntity nextEntity) {
		this.nextEntity = nextEntity;
		if (nextEntity != null) {
			this.nextFilter = nextEntity.getFilter();
		} else {
			this.nextFilter = null;
		}
	}
	
	public String getName() {
		return name;
	}

	public void setFilter(Filter filter) {
		this.filter = filter;
	}

	public Filter getFilter() {
		return filter;
	}

	public Filter getNextFilter() {
		return nextFilter;
	}
}
