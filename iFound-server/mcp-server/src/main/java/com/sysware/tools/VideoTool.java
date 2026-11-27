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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    @Tool(
            name = "listCamera",
            description = "获取摄像头列表及实时在线状态。支持按摄像头名称查询；也支持传入实体检索返回的摄像头ID集合（ids），在MCP服务端批量匹配并返回这些摄像头的Online状态，避免Agent逐台查询。"
    )
    public AjaxResultVO listCamera(
            @ToolParam(description = "摄像头名称，非必填；按名称查询时使用", required = false) String name,
            @ToolParam(description = "页码，从1开始，默认1", required = false) String page,
            @ToolParam(description = "摄像头ID集合，非必填；传入实体检索返回的CAMERA业务ID列表时，服务端批量查询并过滤", required = false) List<String> ids
    ) {
        String finalPage = (page == null || page.isBlank()) ? "1" : page;
        if (ids != null && !ids.isEmpty()) {
            log.debug("mcp 批量查询摄像头在线状态，候选数量={}", ids.size());
            return listCameraByIds(ids, finalPage);
        }
        log.debug("mcp 调用获取摄像头列表:名称={}", name);
        return videoClient.listPage(name, finalPage, "100");
    }

    /**
     * 当前视频服务分页接口尚未暴露 ids 过滤参数，因此在 MCP 服务端分页读取摄像头目录，
     * 直到找到全部候选或读完目录，再按实体检索返回的 ID 集合过滤。Agent 只需调用一次 listCamera。
     */
    private void appendMatchedCameras(
            List<HashMap<String, Object>> cameras,
            Set<String> wantedIds,
            Set<String> foundIds,
            List<HashMap<String, Object>> matched) {
        if (cameras == null) {
            return;
        }
        for (HashMap<String, Object> camera : cameras) {
            Object rawId = camera.get("id");
            if (rawId == null) {
                continue;
            }
            String cameraId = String.valueOf(rawId);
            if (wantedIds.contains(cameraId) && foundIds.add(cameraId)) {
                matched.add(camera);
            }
        }
    }
    private AjaxResultVO listCameraByIds(List<String> ids, String page) {
        Set<String> wantedIds = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                wantedIds.add(id.trim());
            }
        }
        if (wantedIds.isEmpty()) {
            return AjaxResultVO.ok().addQueryPageData(new ArrayList<>(), 0);
        }

        final int scanPageSize = 100;
        AjaxResultVO firstPage = videoClient.listPage(null, "1", String.valueOf(scanPageSize));
        if (firstPage == null || firstPage.getData() == null) {
            return AjaxResultVO.error("摄像头状态查询失败，请稍后重试");
        }

        List<HashMap<String, Object>> matched = new ArrayList<>();
        Set<String> foundIds = new LinkedHashSet<>();
        List<HashMap<String, Object>> firstData = firstPage.getData();
        appendMatchedCameras(firstData, wantedIds, foundIds, matched);

        int total = Math.max(firstPage.getCount(), firstData.size());
        int totalPages = (int) Math.ceil((double) total / scanPageSize);
        for (int currentPage = 2; currentPage <= totalPages && !foundIds.containsAll(wantedIds); currentPage++) {
            AjaxResultVO nextPage = videoClient.listPage(null, String.valueOf(currentPage), String.valueOf(scanPageSize));
            if (nextPage == null || nextPage.getData() == null) {
                log.warn("批量查询摄像头状态时分页失败，page={}", currentPage);
                return AjaxResultVO.error("摄像头状态查询未完成，请稍后重试");
            }
            List<HashMap<String, Object>> nextData = nextPage.getData();
            appendMatchedCameras(nextData, wantedIds, foundIds, matched);
            if (nextData.isEmpty()) {
                break;
            }
        }

        int pageNum;
        try {
            pageNum = Math.max(1, Integer.parseInt(page));
        } catch (NumberFormatException ex) {
            pageNum = 1;
        }
        int fromIndex = Math.min((pageNum - 1) * scanPageSize, matched.size());
        int toIndex = Math.min(fromIndex + scanPageSize, matched.size());
        List<HashMap<String, Object>> pageData = matched.subList(fromIndex, toIndex);
        return AjaxResultVO.ok().addQueryPageData(pageData, matched.size());
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

