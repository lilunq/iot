package com.sysware.xian.swcloud.domain;

import java.io.Serializable;
import java.util.List;


/**
 * 分页查询结果值对象
 * 
 * @author yening
 *
 */

public class PageVO implements Serializable{

	private static final long serialVersionUID = 1L;

	//列表分页查询的总记录数
	private Integer totalCount;
	
	//列表分页查询结果数据
	private List<?> list;
	
	public PageVO() {
		
	}
	
	public PageVO(List<?> list, int count) {
		this.list = list;
		this.totalCount = count;
	}

	public PageVO(List<?> list, long count) {
		this.list = list;
		this.totalCount = (int)count;
	}


	public Integer getTotalCount() {
		return totalCount;
	}



	public void setTotalCount(Integer totalCount) {
		this.totalCount = totalCount;
	}



	public List<?> getList() {
		return list;
	}

	public void setList(List<?> list) {
		this.list = list;
	}
	
}
