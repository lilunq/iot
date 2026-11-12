package com.sysware.swcloud.domian;


/**
 * <b>设备信息[b_device_info]数据实体对象</b>
 * <p>
 * 注意:此文件由平台自动生成-禁止手工修改。
 * </p>
 * db_version V6.1.2.1
 * @author sysware
 * @date 2021-06-21 15:57:55
 */
public class BDeviceInfoEntity  {

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
	 * 所属系统： 系统用cid方式进行描述
	 */
	private String system_type;	
	/**
	 * 图片类型
	 */
	private String device_name;	
	/**
	 * 设备型号
	 */
	private String device_type;	
	/**
	 * 生产厂家
	 */
	private String factory;	
	/**
	 * 生产日期
	 */
	private String production_date;	
	/**
	 * 安装日期
	 */
	private String install_date;	
	/**
	 * 所在位置
	 */
	private String location;	
	/**
	 * sort_no
	 */
	private Integer sort_no;	
	/**
	 * subarea_cid
	 */
	private String subarea_cid;	
	/**
	 * nfc
	 */
	private String nfc;	
	/**
	 * 火灾报警设备扩展码：用于标识火灾报警系统的设备
	 */
	private String fire_key;	
	/**
	 * 厂家类型及id json
	 */
	private String device_fac_id;	
	/**
	 * subarea_id
	 */
	private String subarea_id;	

	
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
	 * 所属系统： 系统用cid方式进行描述
	 * 
	 * @return system_type
	 */
	public String getSystem_type() {
		return system_type;
	}
	
	/**
	 * 图片类型
	 * 
	 * @return device_name
	 */
	public String getDevice_name() {
		return device_name;
	}
	
	/**
	 * 设备型号
	 * 
	 * @return device_type
	 */
	public String getDevice_type() {
		return device_type;
	}
	
	/**
	 * 生产厂家
	 * 
	 * @return factory
	 */
	public String getFactory() {
		return factory;
	}
	
	/**
	 * 生产日期
	 * 
	 * @return production_date
	 */
	public String getProduction_date() {
		return production_date;
	}
	
	/**
	 * 安装日期
	 * 
	 * @return install_date
	 */
	public String getInstall_date() {
		return install_date;
	}
	
	/**
	 * 所在位置
	 * 
	 * @return location
	 */
	public String getLocation() {
		return location;
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
	 * subarea_cid
	 * 
	 * @return subarea_cid
	 */
	public String getSubarea_cid() {
		return subarea_cid;
	}
	
	/**
	 * nfc
	 * 
	 * @return nfc
	 */
	public String getNfc() {
		return nfc;
	}
	
	/**
	 * 火灾报警设备扩展码：用于标识火灾报警系统的设备
	 * 
	 * @return fire_key
	 */
	public String getFire_key() {
		return fire_key;
	}
	
	/**
	 * 厂家类型及id json
	 * 
	 * @return device_fac_id
	 */
	public String getDevice_fac_id() {
		return device_fac_id;
	}
	
	/**
	 * subarea_id
	 * 
	 * @return subarea_id
	 */
	public String getSubarea_id() {
		return subarea_id;
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
	 * 所属系统： 系统用cid方式进行描述
	 * 
	 * @param system_type
	 */
	public void setSystem_type(String system_type) {
		this.system_type = system_type;
	}
	
	/**
	 * 图片类型
	 * 
	 * @param device_name
	 */
	public void setDevice_name(String device_name) {
		this.device_name = device_name;
	}
	
	/**
	 * 设备型号
	 * 
	 * @param device_type
	 */
	public void setDevice_type(String device_type) {
		this.device_type = device_type;
	}
	
	/**
	 * 生产厂家
	 * 
	 * @param factory
	 */
	public void setFactory(String factory) {
		this.factory = factory;
	}
	
	/**
	 * 生产日期
	 * 
	 * @param production_date
	 */
	public void setProduction_date(String production_date) {
		this.production_date = production_date;
	}
	
	/**
	 * 安装日期
	 * 
	 * @param install_date
	 */
	public void setInstall_date(String install_date) {
		this.install_date = install_date;
	}
	
	/**
	 * 所在位置
	 * 
	 * @param location
	 */
	public void setLocation(String location) {
		this.location = location;
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
	 * subarea_cid
	 * 
	 * @param subarea_cid
	 */
	public void setSubarea_cid(String subarea_cid) {
		this.subarea_cid = subarea_cid;
	}
	
	/**
	 * nfc
	 * 
	 * @param nfc
	 */
	public void setNfc(String nfc) {
		this.nfc = nfc;
	}
	
	/**
	 * 火灾报警设备扩展码：用于标识火灾报警系统的设备
	 * 
	 * @param fire_key
	 */
	public void setFire_key(String fire_key) {
		this.fire_key = fire_key;
	}
	
	/**
	 * 厂家类型及id json
	 * 
	 * @param device_fac_id
	 */
	public void setDevice_fac_id(String device_fac_id) {
		this.device_fac_id = device_fac_id;
	}
	
	/**
	 * subarea_id
	 * 
	 * @param subarea_id
	 */
	public void setSubarea_id(String subarea_id) {
		this.subarea_id = subarea_id;
	}
	

}