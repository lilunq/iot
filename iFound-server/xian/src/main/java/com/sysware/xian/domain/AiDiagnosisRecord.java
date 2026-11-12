package com.sysware.xian.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI诊断记录实体
 */
@Data
@TableName("ai_diagnosis_record")
public class AiDiagnosisRecord {

    @TableId(type = IdType.AUTO)
    private String id;
    /** 来源类型 ALARM告警设备/ISSUE待处理问题/COLLABORATION协同任务 */
    private String sourceType;
    /** 来源数据ID */
    private String sourceDataId;
    /** 来源数据JSON */
    private String sourceData;
    /** 设备实时数据JSON(前5条) */
    private String deviceRealtimeData;
    /** AI诊断结果JSON */
    private String diagnosisResult;
    /** 诊断人姓名 */
    private String diagnoseUser;
    /** 派发状态 0待派发 1已派发 */
    private Integer dispatchStatus;
    /** 派发人姓名 */
    private String dispatchUser;
    /** 派发时间 */
    private LocalDateTime dispatchTime;
    /** 备注 */
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
