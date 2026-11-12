package com.sysware.swcloud.domian;

/**
 * 分页查询参数处理
 * @author yen
 *
 */
public class PageParam {
	private int page = 0;
	private int limit = 10;
	
	public PageParam(int page, int limit) {
		this.page = page ;
		this.limit = limit;
	}
	/**
	 * 检查收到的参数是否合理
	 * @return
	 */
	public boolean isValid() {
		if(page < 0 || limit <= 0) {
			return false;
		}
		return true;
	}
	

	
	public int getPageSize() {
		return limit;
	}
	//获取对应页码
	public int getStartPage() {
		return page;
	}
}
