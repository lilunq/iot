package com.sysware.controller;

import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.video.CameraStreamDto;
import com.sysware.service.VideoService;
import com.sysware.swcloud.feign.VideoClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@Tag(name = "视频接口", description = "")
@Slf4j
@RequiredArgsConstructor
public class VideoController {
    /**
     * 视频业务服务
     */
    private final VideoService videoService;

    /**
     * 视频远程调用客户端
     */
    private final VideoClient videoClient;

    /**
     * 获取指定 NVR 的 liveGBS token。
     *
     * @param nvr_id NVR 标识
     * @return token 查询结果
     */
    @Operation(summary = "获取liveGBSToken")
    @GetMapping("/nvr/getToken")
    public AjaxResultVO getVideoToken(@RequestParam(value = "nvr_id") String nvr_id) {
        Map<String, Object> param = new HashMap<>();
        param.put("nvrId", nvr_id);
        return videoClient.getToken(param);
    }

    /**
     * 根据名称模糊分页查询摄像头列表。
     *
     * @param name  摄像头名称
     * @param page  页码
     * @param limit 分页大小
     * @return 摄像头列表查询结果
     */
    @Operation(summary = "分页获取摄像头列表")
    @GetMapping("/camera/listPage")
    public AjaxResultVO listPage(
            @Parameter(description = "摄像头名称", example = "西安东站")
            @RequestParam(required = false) String name,
            @Parameter(description = "页码", example = "1")
            @RequestParam String page,
            @Parameter(description = "分页大小", example = "10")
            @RequestParam String limit) {
        log.info("mcp 调用获取摄像头列表:名称=" + name);
        return videoClient.listPage(name, page, limit);
    }

    /**
     * 获取摄像头视频流地址。
     * 根据请求参数调用 liveGBS 开流接口，并将返回结果中的 FLV、HLS、WEBRTC、WS-FLV
     * 地址统一替换为当前服务对外暴露的流媒体访问 IP。
     *
     * @param dto 请求参数
     * @return 视频流地址信息
     */
    @Operation(summary = "获取摄像头视频流接口")
    @PostMapping("/camera/stream/start")
    public AjaxResultVO streamStart(@RequestBody CameraStreamDto dto) {
        return AjaxResultVO.ok().data(videoService.streamStart(dto));
    }
}
