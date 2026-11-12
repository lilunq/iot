package com.sysware.swcloud.domian;

import lombok.Data;

import java.util.List;

@Data
public class BDeviceAllInfo {
    List<BDeviceData> bDeviceDataList;
    /**
     * 告警计数
     */
    private int alarmCount = 0;
    /**
     * 故障计数
     */
    private int faultCount = 0;
    /**
     * id
     */
    private String id;
    /**
     * org_id
     */
    private String org_id;
    /**
     * 系统cid
     */
    private String system_type;
    private String system_type_name;
    /**
     * 设备名称
     */
    private String device_name;
    /**
     * 设备型号cid
     */
    private String device_type;
    private String device_type_name;
    /**
     * 生产厂家
     */
    private String factory;
    /**
     * 安装日期
     */
    private String install_date;
    /**
     * 所在位置
     */
    private String location;
    /**
     * subarea_cid
     */
    private String subarea_cid;
    private String subarea_name;

}

@Data
class BDeviceData {
    String value;
    /**
     * 数据类型，3rtu，1遥测，2遥信；
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
     * 别名/简称
     */
    private String data_short_name;
    /**
     * 设备运行值标志，0：否，1：是，用于组态中获取设备对应运行数据的标志
     */
    private Integer device_value_flag;
    /**
     * 在线状态标志 0：否，1：是
     */
    private Integer online_status_flag;
}
