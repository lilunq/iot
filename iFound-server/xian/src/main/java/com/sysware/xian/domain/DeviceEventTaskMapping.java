package com.sysware.xian.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备事件与派单任务映射实体
 * <p>
 * 记录每条故障告警事项（BDeviceEventEntity）对应的 DispatchTask，
 * 便于后期追溯历史。同一设备多次故障追加同一任务时会产生多条映射。
 */
@Data
@TableName("device_event_task_mapping")
@Schema(description = "设备事件与派单任务映射")
public class DeviceEventTaskMapping {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private String id;

    /** 设备事件ID（BDeviceEventEntity.id） */
    @Schema(description = "设备事件ID")
    private String eventId;

    /** 派单任务ID（DispatchTask.id） */
    @Schema(description = "派单任务ID")
    private String taskId;

    /** 设备ID */
    @Schema(description = "设备ID")
    private String deviceId;

    /** 设备名称 */
    @Schema(description = "设备名称")
    private String deviceName;

    /** 事件信息 */
    @Schema(description = "事件信息")
    private String eventInfo;

    /** 发生时间 */
    @Schema(description = "事件发生时间")
    private String happenTime;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "映射记录创建时间")
    private LocalDateTime createTime;
}
