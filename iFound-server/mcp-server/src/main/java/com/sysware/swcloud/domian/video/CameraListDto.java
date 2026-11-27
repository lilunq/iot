package com.sysware.swcloud.domian.video;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * @Description: 分页获取摄像头列表的请求参数
 **/
@Data
@Schema(name = "CameraListDto", description = "分页获取摄像头列表的请求参数")
public class CameraListDto {
    /**
     * 摄像头名称，支持模糊查询
     */
    @Schema(description = "摄像头名称，支持模糊查询", name = "name", example = "西安东站")
    private String name;
    /**
     * 页码
     */
    @Schema(description = "页码", name = "page", example = "1", requiredMode = REQUIRED)
    private String page;
    /**
     * 分页大小
     */
    @Schema(description = "分页大小", name = "limit", example = "10", requiredMode = REQUIRED)
    private String limit;
    /**
     * 在线状态过滤，传1表示只查询在线的摄像头
     */
    @Schema(description = "在线状态过滤，传1表示只查询在线的摄像头", name = "online", example = "1")
    private String online;
    /**
     * 设备ID集合，用于 ES 实体候选的批量状态过滤。
     */
    @Schema(description = "摄像头ID集合，非必填；与实体检索返回的业务ID对应", name = "ids")
    private List<String> ids;
}
