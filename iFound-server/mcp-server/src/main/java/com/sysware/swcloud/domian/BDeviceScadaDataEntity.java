package com.sysware.swcloud.domian;

public class BDeviceScadaDataEntity{

	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	private String id;	
	/**
	 * device_id
	 */
	private String device_id;	
	/**
	 * 数据类型，这个是给采集设备使用的，标识是遥测还是遥信
            采集设备：3rtu，1遥测，2遥信；
	 */
	private String data_type;	
	/**
	 * rtu号
	 */
	private String data_key;	
	/**
	 * data_name
	 */
	private String data_name;	
	/**
	 * 告警类型
	 */
	private String event_type;	
	/**
	 * sort_no
	 */
	private Integer sort_no;	
	/**
	 * 数据源：0-scada采集，1-人工上报
	 */
	private Integer data_source;	
	/**
	 * 设备运行值标志，0：否，1：是，用于组态中获取设备对应运行数据的标志
	 */
	private Integer device_value_flag;	
	/**
	 * 受关注的数据标志： 0：否 1：是，用于对关注数据进行过滤
	 */
	private Integer attention_data_flag;	
	/**
	 * 别名/简称
	 */
	private String data_short_name;	
	/**
	 * 是否生成事项标志 0：否 1：是
	 */
	private Integer create_event_flag;	
	/**
	 * 事项弹窗标志0：否1：是
	 */
	private Integer event_popup_flag;	
	/**
	 * 事项确认：0：否1：是
	 */
	private Integer event_confirm_flag;	
	/**
	 * 是否生成恢复事项0：否1：是
	 */
	private Integer create_recover_event_flag;	
	/**
	 * 在线状态标志 0：否，1：是
	 */
	private Integer online_status_flag;	
	/**
	 * 数据扩展标志，默认-1无效
	 */
	private Integer data_ext_flag;	

	
	/**
	 * id
	 * 
	 * @return id
	 */
	public String getId() {
		return id;
	}
	
	/**
	 * device_id
	 * 
	 * @return device_id
	 */
	public String getDevice_id() {
		return device_id;
	}
	
	/**
	 * 数据类型，这个是给采集设备使用的，标识是遥测还是遥信
            采集设备：3rtu，1遥测，2遥信；
	 * 
	 * @return data_type
	 */
	public String getData_type() {
		return data_type;
	}
	
	/**
	 * rtu号
	 * 
	 * @return data_key
	 */
	public String getData_key() {
		return data_key;
	}
	
	/**
	 * data_name
	 * 
	 * @return data_name
	 */
	public String getData_name() {
		return data_name;
	}
	
	/**
	 * 告警类型
	 * 
	 * @return event_type
	 */
	public String getEvent_type() {
		return event_type;
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
	 * 数据源：0-scada采集，1-人工上报
	 * 
	 * @return data_source
	 */
	public Integer getData_source() {
		return data_source;
	}
	
	/**
	 * 设备运行值标志，0：否，1：是，用于组态中获取设备对应运行数据的标志
	 * 
	 * @return device_value_flag
	 */
	public Integer getDevice_value_flag() {
		return device_value_flag;
	}
	
	/**
	 * 受关注的数据标志： 0：否 1：是，用于对关注数据进行过滤
	 * 
	 * @return attention_data_flag
	 */
	public Integer getAttention_data_flag() {
		return attention_data_flag;
	}
	
	/**
	 * 别名/简称
	 * 
	 * @return data_short_name
	 */
	public String getData_short_name() {
		return data_short_name;
	}
	
	/**
	 * 是否生成事项标志 0：否 1：是
	 * 
	 * @return create_event_flag
	 */
	public Integer getCreate_event_flag() {
		return create_event_flag;
	}
	
	/**
	 * 事项弹窗标志0：否1：是
	 * 
	 * @return event_popup_flag
	 */
	public Integer getEvent_popup_flag() {
		return event_popup_flag;
	}
	
	/**
	 * 事项确认：0：否1：是
	 * 
	 * @return event_confirm_flag
	 */
	public Integer getEvent_confirm_flag() {
		return event_confirm_flag;
	}
	
	/**
	 * 是否生成恢复事项0：否1：是
	 * 
	 * @return create_recover_event_flag
	 */
	public Integer getCreate_recover_event_flag() {
		return create_recover_event_flag;
	}
	
	/**
	 * 在线状态标志 0：否，1：是
	 * 
	 * @return online_status_flag
	 */
	public Integer getOnline_status_flag() {
		return online_status_flag;
	}
	
	/**
	 * 数据扩展标志，默认-1无效
	 * 
	 * @return data_ext_flag
	 */
	public Integer getData_ext_flag() {
		return data_ext_flag;
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
	 * device_id
	 * 
	 * @param device_id
	 */
	public void setDevice_id(String device_id) {
		this.device_id = device_id;
	}
	
	/**
	 * 数据类型，这个是给采集设备使用的，标识是遥测还是遥信
            采集设备：3rtu，1遥测，2遥信；
	 * 
	 * @param data_type
	 */
	public void setData_type(String data_type) {
		this.data_type = data_type;
	}
	
	/**
	 * rtu号
	 * 
	 * @param data_key
	 */
	public void setData_key(String data_key) {
		this.data_key = data_key;
	}
	
	/**
	 * data_name
	 * 
	 * @param data_name
	 */
	public void setData_name(String data_name) {
		this.data_name = data_name;
	}
	
	/**
	 * 告警类型
	 * 
	 * @param event_type
	 */
	public void setEvent_type(String event_type) {
		this.event_type = event_type;
	}
	
	/**
	 * sort_no
	 * 
	 * @param sort_no
	 */
	public void setSort_no(Integer sort_no) {
		this.sort_no = sort_no;
	}
	
	/**
	 * 数据源：0-scada采集，1-人工上报
	 * 
	 * @param data_source
	 */
	public void setData_source(Integer data_source) {
		this.data_source = data_source;
	}
	
	/**
	 * 设备运行值标志，0：否，1：是，用于组态中获取设备对应运行数据的标志
	 * 
	 * @param device_value_flag
	 */
	public void setDevice_value_flag(Integer device_value_flag) {
		this.device_value_flag = device_value_flag;
	}
	
	/**
	 * 受关注的数据标志： 0：否 1：是，用于对关注数据进行过滤
	 * 
	 * @param attention_data_flag
	 */
	public void setAttention_data_flag(Integer attention_data_flag) {
		this.attention_data_flag = attention_data_flag;
	}
	
	/**
	 * 别名/简称
	 * 
	 * @param data_short_name
	 */
	public void setData_short_name(String data_short_name) {
		this.data_short_name = data_short_name;
	}
	
	/**
	 * 是否生成事项标志 0：否 1：是
	 * 
	 * @param create_event_flag
	 */
	public void setCreate_event_flag(Integer create_event_flag) {
		this.create_event_flag = create_event_flag;
	}
	
	/**
	 * 事项弹窗标志0：否1：是
	 * 
	 * @param event_popup_flag
	 */
	public void setEvent_popup_flag(Integer event_popup_flag) {
		this.event_popup_flag = event_popup_flag;
	}
	
	/**
	 * 事项确认：0：否1：是
	 * 
	 * @param event_confirm_flag
	 */
	public void setEvent_confirm_flag(Integer event_confirm_flag) {
		this.event_confirm_flag = event_confirm_flag;
	}
	
	/**
	 * 是否生成恢复事项0：否1：是
	 * 
	 * @param create_recover_event_flag
	 */
	public void setCreate_recover_event_flag(Integer create_recover_event_flag) {
		this.create_recover_event_flag = create_recover_event_flag;
	}
	
	/**
	 * 在线状态标志 0：否，1：是
	 * 
	 * @param online_status_flag
	 */
	public void setOnline_status_flag(Integer online_status_flag) {
		this.online_status_flag = online_status_flag;
	}
	
	/**
	 * 数据扩展标志，默认-1无效
	 * 
	 * @param data_ext_flag
	 */
	public void setData_ext_flag(Integer data_ext_flag) {
		this.data_ext_flag = data_ext_flag;
	}
	

}