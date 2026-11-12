package com.sysware.swcloud.domian;

/**
 * <b>控制模式[scada_control_model]数据实体对象</b>
 * <p>
 * 注意:此文件由平台自动生成-禁止手工修改。
 * </p>
 * db_version V8.7.0.0
 * @author sysware
 * @date 2025-09-04 13:22:04
 */
public class ScadaControlModelEntity {

	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	private String id;	
	/**
	 * name
	 */
	private String name;	
	/**
	 * 逻辑控制模式0：禁用1：启用
	 */
	private Integer logic_mode;	
	/**
	 * 数据类型
	 */
	private Integer logic_data_type;	
	/**
	 * 数据key
	 */
	private String logic_data_key;	
	/**
	 * 触发值
	 */
	private Double logic_trigger_value;	
	/**
	 * 条件成立：再次触发延时时间
	 */
	private Integer logic_in_time_delay;	
	/**
	 * 条件不成立：再次触发延时时间
	 */
	private Integer logic_out_time_delay;	
	/**
	 * 条件不成立：是否启用再次触发
	 */
	private Integer logic_out_again_enable;	
	/**
	 * 条件成立：是否再次触发
	 */
	private Integer logic_in_again_enable;	
	/**
	 * 时间控制模式0：禁用1：启用
	 */
	private Integer time_mode;	
	/**
	 * 时间段json
	 */
	private String time_json;	
	/**
	 * 场景ids
	 */
	private String scene_ids;	
	/**
	 * 是否启用
	 */
	private Integer is_enabled;	
	/**
	 * 逻辑延时参数json格式
	 */
	private String logic_delay_param;	

	
	/**
	 * id
	 * 
	 * @return id
	 */
	public String getId() {
		return id;
	}
	
	/**
	 * name
	 * 
	 * @return name
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * 逻辑控制模式0：禁用1：启用
	 * 
	 * @return logic_mode
	 */
	public Integer getLogic_mode() {
		return logic_mode;
	}
	
	/**
	 * 数据类型
	 * 
	 * @return logic_data_type
	 */
	public Integer getLogic_data_type() {
		return logic_data_type;
	}
	
	/**
	 * 数据key
	 * 
	 * @return logic_data_key
	 */
	public String getLogic_data_key() {
		return logic_data_key;
	}
	
	/**
	 * 触发值
	 * 
	 * @return logic_trigger_value
	 */
	public Double getLogic_trigger_value() {
		return logic_trigger_value;
	}
	
	/**
	 * 条件成立：再次触发延时时间
	 * 
	 * @return logic_in_time_delay
	 */
	public Integer getLogic_in_time_delay() {
		return logic_in_time_delay;
	}
	
	/**
	 * 条件不成立：再次触发延时时间
	 * 
	 * @return logic_out_time_delay
	 */
	public Integer getLogic_out_time_delay() {
		return logic_out_time_delay;
	}
	
	/**
	 * 条件不成立：是否启用再次触发
	 * 
	 * @return logic_out_again_enable
	 */
	public Integer getLogic_out_again_enable() {
		return logic_out_again_enable;
	}
	
	/**
	 * 条件成立：是否再次触发
	 * 
	 * @return logic_in_again_enable
	 */
	public Integer getLogic_in_again_enable() {
		return logic_in_again_enable;
	}
	
	/**
	 * 时间控制模式0：禁用1：启用
	 * 
	 * @return time_mode
	 */
	public Integer getTime_mode() {
		return time_mode;
	}
	
	/**
	 * 时间段json
	 * 
	 * @return time_json
	 */
	public String getTime_json() {
		return time_json;
	}
	
	/**
	 * 场景ids
	 * 
	 * @return scene_ids
	 */
	public String getScene_ids() {
		return scene_ids;
	}
	
	/**
	 * 是否启用
	 * 
	 * @return is_enabled
	 */
	public Integer getIs_enabled() {
		return is_enabled;
	}
	
	/**
	 * 逻辑延时参数json格式
	 * 
	 * @return logic_delay_param
	 */
	public String getLogic_delay_param() {
		return logic_delay_param;
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
	 * name
	 * 
	 * @param name
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * 逻辑控制模式0：禁用1：启用
	 * 
	 * @param logic_mode
	 */
	public void setLogic_mode(Integer logic_mode) {
		this.logic_mode = logic_mode;
	}
	
	/**
	 * 数据类型
	 * 
	 * @param logic_data_type
	 */
	public void setLogic_data_type(Integer logic_data_type) {
		this.logic_data_type = logic_data_type;
	}
	
	/**
	 * 数据key
	 * 
	 * @param logic_data_key
	 */
	public void setLogic_data_key(String logic_data_key) {
		this.logic_data_key = logic_data_key;
	}
	
	/**
	 * 触发值
	 * 
	 * @param logic_trigger_value
	 */
	public void setLogic_trigger_value(Double logic_trigger_value) {
		this.logic_trigger_value = logic_trigger_value;
	}
	
	/**
	 * 条件成立：再次触发延时时间
	 * 
	 * @param logic_in_time_delay
	 */
	public void setLogic_in_time_delay(Integer logic_in_time_delay) {
		this.logic_in_time_delay = logic_in_time_delay;
	}
	
	/**
	 * 条件不成立：再次触发延时时间
	 * 
	 * @param logic_out_time_delay
	 */
	public void setLogic_out_time_delay(Integer logic_out_time_delay) {
		this.logic_out_time_delay = logic_out_time_delay;
	}
	
	/**
	 * 条件不成立：是否启用再次触发
	 * 
	 * @param logic_out_again_enable
	 */
	public void setLogic_out_again_enable(Integer logic_out_again_enable) {
		this.logic_out_again_enable = logic_out_again_enable;
	}
	
	/**
	 * 条件成立：是否再次触发
	 * 
	 * @param logic_in_again_enable
	 */
	public void setLogic_in_again_enable(Integer logic_in_again_enable) {
		this.logic_in_again_enable = logic_in_again_enable;
	}
	
	/**
	 * 时间控制模式0：禁用1：启用
	 * 
	 * @param time_mode
	 */
	public void setTime_mode(Integer time_mode) {
		this.time_mode = time_mode;
	}
	
	/**
	 * 时间段json
	 * 
	 * @param time_json
	 */
	public void setTime_json(String time_json) {
		this.time_json = time_json;
	}
	
	/**
	 * 场景ids
	 * 
	 * @param scene_ids
	 */
	public void setScene_ids(String scene_ids) {
		this.scene_ids = scene_ids;
	}
	
	/**
	 * 是否启用
	 * 
	 * @param is_enabled
	 */
	public void setIs_enabled(Integer is_enabled) {
		this.is_enabled = is_enabled;
	}
	
	/**
	 * 逻辑延时参数json格式
	 * 
	 * @param logic_delay_param
	 */
	public void setLogic_delay_param(String logic_delay_param) {
		this.logic_delay_param = logic_delay_param;
	}
	

	@Override
	public String toString () {
		return "ScadaControlModelEntity [id=" + id + ", name=" + name + ", logic_mode=" + logic_mode + ", logic_data_type=" + logic_data_type + ", logic_data_key=" + logic_data_key + ", logic_trigger_value=" + logic_trigger_value + ", logic_in_time_delay=" + logic_in_time_delay + ", logic_out_time_delay=" + logic_out_time_delay + ", logic_out_again_enable=" + logic_out_again_enable + ", logic_in_again_enable=" + logic_in_again_enable + ", time_mode=" + time_mode + ", time_json=" + time_json + ", scene_ids=" + scene_ids + ", is_enabled=" + is_enabled + ", logic_delay_param=" + logic_delay_param + "]";
	}
}