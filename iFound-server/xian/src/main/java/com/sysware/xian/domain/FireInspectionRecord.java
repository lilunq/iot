package com.sysware.xian.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消防巡查记录
 * <p>
 * 上游 /digitalStation/getData2(type=xfxc) 返回的大写字段映射。
 * 同一设备出现多条记录时ID相同、时间不同，按ID聚合取最新一条定性。
 */
@Data
public class FireInspectionRecord {

    /** 设备ID（同一设备各条记录相同） */
    private String id;

    /** 设备名称，如"306号墙壁栓" */
    private String name;

    /** 设备位置，如"西安东站-高架层-西南角旅客服务走廊" */
    private String location;

    /** 巡查人 */
    private String userName;

    /** 巡查时间，ISO格式如 2026-08-30T10:09:42.845297 */
    private String createTime;

    /** 巡视结果：1-正常，其他-异常 */
    private Integer result;

    /** 异常说明 */
    private String remark;

    /** 上游原始数据对象，用于dataSnapshot/resultData存档 */
    @JsonIgnore
    private Map<String, Object> raw;

    /** 是否正常（RESULT=1） */
    public boolean isNormal() {
        return result != null && result == 1;
    }

    /**
     * 解析巡查时间（ISO_LOCAL_DATE_TIME，兼容微秒），格式异常时返回null
     */
    public LocalDateTime createTimeValue() {
        if (createTime == null || createTime.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(createTime.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从上游返回的单条数据Map构建记录对象
     */
    public static FireInspectionRecord from(Map<String, Object> rawMap) {
        FireInspectionRecord record = new FireInspectionRecord();
        record.setRaw(rawMap);
        record.setId(str(rawMap.get("ID")));
        record.setName(str(rawMap.get("NAME")));
        record.setLocation(str(rawMap.get("LOCATION")));
        record.setUserName(str(rawMap.get("USER_NAME")));
        record.setCreateTime(str(rawMap.get("CREATE_TIME")));
        Object result = rawMap.get("RESULT");
        if (result instanceof Number num) {
            record.setResult(num.intValue());
        } else if (result != null) {
            try {
                record.setResult(Integer.parseInt(result.toString().trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        record.setRemark(str(rawMap.get("REMARK")));
        return record;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
