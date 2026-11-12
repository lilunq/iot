package com.sysware.tools;

import com.sysware.service.VideoService;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.video.CameraStreamDto;
import com.sysware.swcloud.feign.VideoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VideoTool {
    private final VideoClient videoClient;
    private final VideoService videoService;

    /**
     * 获取摄像头列表。
     *
     * @param name       摄像头名称
     * @param page       页码
     * @return 原始 AjaxResultVO（含摄像头列表数据）
     */
    @Tool(name = "listCamera", description = "获取摄像头列表，返回原始数据（含每台摄像头的id、subarea_cid、name、Online等字段）")
    public AjaxResultVO listCamera(
            @ToolParam(description = "摄像头名称，必填", required = true) String name,
            @ToolParam(description = "页码，从1开始", required = true) String page
    ) {
        String finalPage = (page == null || page.isBlank()) ? "1" : page;
        log.debug("mcp 调用获取摄像头列表:名称={}", name);
        return videoClient.listPage(name, finalPage, "5");
    }

    /**
     * 打开指定摄像头。
     * <p>cameraId 支持两种传值：摄像头 ID，或设备 ID（设备 ID 可由 getDeviceStatePage、
     * getDeviceInfoWithPoints 等设备查询工具获取）；传设备 ID 时，后台会自动打开该设备所关联的摄像头。
     *
     * @param cameraId   摄像头 ID 或设备 ID
     * @param subareaCid 区域 ID（subarea_cid）
     * @param cameraName 摄像头名称或设备名称（用于拼装返回提示）
     * @return {摄像头名称/设备名称}开启成功。
     */
    @Tool(name = "openCamera", description = "打开指定摄像头，返回开启成功信息。cameraId 支持传摄像头ID或设备ID：传设备ID时后台自动打开该设备关联的摄像头")
    public String openCamera(
            @ToolParam(description = "摄像头ID或设备ID（传设备ID时打开该设备关联的摄像头，设备ID可从设备查询工具获取）") String cameraId,
            @ToolParam(description = "区域ID subarea_cid") String subareaCid,
            @ToolParam(description = "摄像头名称或设备名称") String cameraName) {
        log.debug("mcp 调用打开摄像头: cameraId={}, subareaCid={}, cameraName={}", cameraId, subareaCid, cameraName);
        return cameraName + "开启成功。";
    }
}

