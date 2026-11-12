package com.sysware.swcloud.domian;

public class ScadaSceneEntity {

	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	private String id;	
	/**
	 * org_id
	 */
	private String org_id;	
	/**
	 * 场景名称
	 */
	private String name;	
	/**
	 * cascade_id
	 */
	private String cascade_id;	
	/**
	 * 执行间隔
	 */
	private String execution_interval;	
	/**
	 * 是否可配置重复参数
	 */
	private Integer repeatability;	
	/**
	 * 执行顺序
	 */
	private Integer executor_state;	
	/**
	 * 执行失败,重新执行次数
	 */
	private Integer executor_fail_retry_count;	


	/**
	 * id
	 * 
	 * @return id
	 */
	public String getId() {
		return id;
	}
	
	/**
	 * org_id
	 * 
	 * @return org_id
	 */
	public String getOrg_id() {
		return org_id;
	}
	
	/**
	 * 场景名称
	 * 
	 * @return name
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * cascade_id
	 * 
	 * @return cascade_id
	 */
	public String getCascade_id() {
		return cascade_id;
	}
	
	/**
	 * 执行间隔
	 * 
	 * @return execution_interval
	 */
	public String getExecution_interval() {
		return execution_interval;
	}
	
	/**
	 * 是否可配置重复参数
	 * 
	 * @return repeatability
	 */
	public Integer getRepeatability() {
		return repeatability;
	}
	
	/**
	 * 执行顺序
	 * 
	 * @return executor_state
	 */
	public Integer getExecutor_state() {
		return executor_state;
	}
	
	/**
	 * 执行失败,重新执行次数
	 * 
	 * @return executor_fail_retry_count
	 */
	public Integer getExecutor_fail_retry_count() {
		return executor_fail_retry_count;
	}
	

	/**
	 * id
	 * 
	 * @param id
	 */
	public void setId(String id) {
		this.id = id;
	}
	
	/**
	 * org_id
	 * 
	 * @param org_id
	 */
	public void setOrg_id(String org_id) {
		this.org_id = org_id;
	}
	
	/**
	 * 场景名称
	 * 
	 * @param name
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * cascade_id
	 * 
	 * @param cascade_id
	 */
	public void setCascade_id(String cascade_id) {
		this.cascade_id = cascade_id;
	}
	
	/**
	 * 执行间隔
	 * 
	 * @param execution_interval
	 */
	public void setExecution_interval(String execution_interval) {
		this.execution_interval = execution_interval;
	}
	
	/**
	 * 是否可配置重复参数
	 * 
	 * @param repeatability
	 */
	public void setRepeatability(Integer repeatability) {
		this.repeatability = repeatability;
	}
	
	/**
	 * 执行顺序
	 * 
	 * @param executor_state
	 */
	public void setExecutor_state(Integer executor_state) {
		this.executor_state = executor_state;
	}
	
	/**
	 * 执行失败,重新执行次数
	 * 
	 * @param executor_fail_retry_count
	 */
	public void setExecutor_fail_retry_count(Integer executor_fail_retry_count) {
		this.executor_fail_retry_count = executor_fail_retry_count;
	}
	

}