package com.sysware.xian.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站房运行水平历史实体
 * 运行水平：正常、注意、紧急
 */
@Data
@TableName("tunnel_run_score")
public class TunnelRunScore {
    @TableId(type = IdType.AUTO)
    private String id;
    private String orgId;
    // 整体运行水平：正常、注意、紧急
    private String totalLevel;
    private String levelDesc;

    // 4个维度的运行水平
    private String deviceLevel;
    private String envLevel;
    private String energyLevel;
    private String passengerLevel;

    // 4个维度原始数据(JSON存储)
    private String deviceData;
    private String envData;
    private String energyData;
    private String passengerData;

    private LocalDateTime createTime;
}
