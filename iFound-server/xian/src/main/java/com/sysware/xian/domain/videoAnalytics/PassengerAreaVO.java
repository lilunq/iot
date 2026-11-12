package com.sysware.xian.domain.videoAnalytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 客流区域视图对象
 */
@Data
@Schema(description = "客流区域")
public class PassengerAreaVO {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "区域名称")
    private String name;

    @Schema(description = "区域编码")
    private String code;

    @Schema(description = "区域面积（平方米）")
    private BigDecimal areaSqm;

    @Schema(description = "拥挤报警阈值百分比")
    private BigDecimal alarmRate;

    @Schema(description = "所属分组编码")
    private String groupCode;

    @Schema(description = "所属小组编码")
    private String teamCode;

    @Schema(description = "所属分组名称")
    private String groupName;

    @Schema(description = "摄像头编码")
    private String channelCode;

    @Schema(description = "通道名称")
    private String channelName;

    @Schema(description = "楼层")
    private String floorLevel;

    @Schema(description = "FLV 流地址")
    private String flvAddr;

    @Schema(description = "RTMP 流地址")
    private String rtmpAddr;

    @Schema(description = "RTSP 流地址")
    private String rtspAddr;

    @Schema(description = "WS-FLV 流地址")
    private String wsAddr;

    @Schema(description = "摄像头标定变换矩阵 (JSON 3x3)")
    private String camCalMatrix;

    @Schema(description = "电子围栏顶点坐标 (JSON: [{x,y}...])")
    private String camFencePoints;

    @Schema(description = "摄像头画面标定点 (JSON: [{x,y}...])")
    private String camImagePoint;

    @Schema(description = "平面图标定点 (JSON: [{x,y}...])")
    private String camDstPoint;

    @Schema(description = "排序号")
    private Integer sort;
}
