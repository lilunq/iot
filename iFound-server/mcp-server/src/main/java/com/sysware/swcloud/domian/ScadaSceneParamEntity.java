package com.sysware.swcloud.domian;

public class ScadaSceneParamEntity {

	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	private String id;	
	/**
	 * 场景id
	 */
	private String scene_id;	
	/**
	 * yc——yx id
	 */
	private String param_id;	
	/**
	 * 执行值
	 */
	private Integer value;	
	/**
	 * 参数状态
	 */
	private Integer param_type;	
	/**
	 * sort_no
	 */
	private Integer sort_no;	

	/**
	 * id
	 * 
	 * @return id
	 */
	public String getId() {
		return id;
	}
	
	/**
	 * 场景id
	 * 
	 * @return scene_id
	 */
	public String getScene_id() {
		return scene_id;
	}
	
	/**
	 * yc——yx id
	 * 
	 * @return param_id
	 */
	public String getParam_id() {
		return param_id;
	}
	
	/**
	 * 执行值
	 * 
	 * @return value
	 */
	public Integer getValue() {
		return value;
	}
	
	/**
	 * 参数状态
	 * 
	 * @return param_type
	 */
	public Integer getParam_type() {
		return param_type;
	}
	
	/**
	 * sort_no
	 * 
	 * @return sort_no
	 */
	public Integer getSort_no() {
		return sort_no;
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
	 * 场景id
	 * 
	 * @param scene_id
	 */
	public void setScene_id(String scene_id) {
		this.scene_id = scene_id;
	}
	
	/**
	 * yc——yx id
	 * 
	 * @param param_id
	 */
	public void setParam_id(String param_id) {
		this.param_id = param_id;
	}
	
	/**
	 * 执行值
	 * 
	 * @param value
	 */
	public void setValue(Integer value) {
		this.value = value;
	}
	
	/**
	 * 参数状态
	 * 
	 * @param param_type
	 */
	public void setParam_type(Integer param_type) {
		this.param_type = param_type;
	}
	
	/**
	 * sort_no
	 * 
	 * @param sort_no
	 */
	public void setSort_no(Integer sort_no) {
		this.sort_no = sort_no;
	}
	

}