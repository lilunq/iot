package com.sysware.swcloud.domian;


/**
 * <b>事项数统计[b_device_event_count]数据实体对象</b>
 * <p>
 * 注意:此文件由平台自动生成-禁止手工修改。
 * </p>
 * db_version V8.7.0.0
 * @author sysware
 * @date 2024-05-27 11:22:58
 */
public class BDeviceEventCountEntity  {

	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	private String id;	
	/**
	 * 区域cid
	 */
	private String subarea_cid;	
	/**
	 * 日期
	 */
	private Integer date;	
	/**
	 * 设备id
	 */
	private String device_id;	
	/**
	 * 故障数
	 */
	private Integer fault_count;	
	/**
	 * 告警数
	 */
	private Integer alarm_count;	

	
	/**
	 * id
	 * 
	 * @return id
	 */
	public String getId() {
		return id;
	}
	
	/**
	 * 区域cid
	 * 
	 * @return subarea_cid
	 */
	public String getSubarea_cid() {
		return subarea_cid;
	}
	
	/**
	 * 日期
	 * 
	 * @return date
	 */
	public Integer getDate() {
		return date;
	}
	
	/**
	 * 设备id
	 * 
	 * @return device_id
	 */
	public String getDevice_id() {
		return device_id;
	}
	
	/**
	 * 故障数
	 * 
	 * @return fault_count
	 */
	public Integer getFault_count() {
		return fault_count;
	}
	
	/**
	 * 告警数
	 * 
	 * @return alarm_count
	 */
	public Integer getAlarm_count() {
		return alarm_count;
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
	 * 区域cid
	 * 
	 * @param subarea_cid
	 */
	public void setSubarea_cid(String subarea_cid) {
		this.subarea_cid = subarea_cid;
	}
	
	/**
	 * 日期
	 * 
	 * @param date
	 */
	public void setDate(Integer date) {
		this.date = date;
	}
	
	/**
	 * 设备id
	 * 
	 * @param device_id
	 */
	public void setDevice_id(String device_id) {
		this.device_id = device_id;
	}
	
	/**
	 * 故障数
	 * 
	 * @param fault_count
	 */
	public void setFault_count(Integer fault_count) {
		this.fault_count = fault_count;
	}
	
	/**
	 * 告警数
	 * 
	 * @param alarm_count
	 */
	public void setAlarm_count(Integer alarm_count) {
		this.alarm_count = alarm_count;
	}
	

}