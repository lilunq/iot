package com.sysware.swcloud.domian.video;

import com.sysware.controller.VideoController;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * @Description: 获取摄像头视频流的请求参数
 * @Author: LiYanRu
 * @date: 2026-06-24
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "CameraStreamDto", description = "获取摄像头视频流的请求参数")
public class CameraStreamDto {
    /**
     * 摄像头序列号
     */
    @Schema(description = "摄像头序列号", name = "serial", example = "DS-2CD2T47G2-L", requiredMode = REQUIRED)
    private String serial;
    /**
     * 视频流通道编号
     */
    @Schema(description = "视频流通道编号", name = "channel", example = "1", requiredMode = REQUIRED)
    private String channel;
    /**
     * 摄像头id
     */
    @Schema(description = "摄像头id", name = "nvrId", example = "32010000001310000001", requiredMode = REQUIRED)
    private String nvrId;
    /**
     * 通过 {@link VideoController#getVideoToken(String)} 接口获取的 token
     */
    @Schema(description = "通过 listCamera(name) 接口获取的 token", name = "token", example = "eyJhbGciOiJIUzI1NiJ9...", requiredMode = REQUIRED)
    private String token;
}
