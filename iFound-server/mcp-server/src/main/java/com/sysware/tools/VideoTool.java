package com.sysware.tools;

import com.sysware.service.VideoService;
import com.sysware.service.StationEntitySearchService;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.video.CameraStreamDto;
import com.sysware.swcloud.domian.video.CameraListDto;
import com.sysware.swcloud.feign.VideoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class VideoTool {
    private final VideoClient videoClient;
    private final VideoService videoService;
    private final StationEntitySearchService entitySearchService;

    /**
     * 获取摄像头列表，支持按名称模糊查询和在线状态过滤。
     */
    @Tool(
            name = "listCamera",
            description = "获取摄像头列表，返回原始数据（含每台摄像头的id、name、Online等字段），可按摄像头名称模糊查询，可只查在线摄像头"
    )
    public AjaxResultVO listCamera(
            @ToolParam(description = "摄像头名称，非必填，支持模糊查询", required = false) String name,
            @ToolParam(description = "页码，从1开始，默认1", required = false) String pageNum,
            @ToolParam(description = "每页多少条，默认5", required = false) String pageSize,
            @ToolParam(description = "在线状态过滤，传1表示只查询在线的摄像头，非必填", required = false) String online
    ) {
        String finalPage = (pageNum == null || pageNum.isBlank()) ? "1" : pageNum;
        String finalPageSize = (pageSize == null || pageSize.isBlank()) ? "5" : pageSize;
        String finalOnline = (online == null || online.isBlank()) ? null : online.trim();
        log.debug("mcp 调用获取摄像头列表:name={}, page={}, limit={}, online={}",
                name, finalPage, finalPageSize, finalOnline);
        return videoClient.listPage(name, finalPage, finalPageSize, finalOnline,null);
    }

    /**
     * 面向 Agent 的完整摄像头查询：自然语言实体定位 + 实时 Online 状态查询。
     */
    @Tool(
            name = "queryStationCameras",
            description = "根据用户完整自然语言查询西安东站摄像头及实时在线状态。内部先定位真实 CAMERA 候选，再批量查询视频服务实时 Online 状态；用户明确要求在线时传 onlineStatus="1"，明确要求离线时传 onlineStatus="0"，否则不传。Agent 无需分步调用实体检索和摄像头目录查询。"
    )
    public AjaxResultVO queryStationCameras(
            @ToolParam(description = "用户完整的原始问题，例如：查看候车大厅摄像头") String prompt,
            @ToolParam(description = "在线状态过滤：1只查在线，0只查离线，不传表示全部", required = false) String onlineStatus,
            @ToolParam(description = "页码，从1开始，默认1", required = false) String pageNum,
            @ToolParam(description = "每页条数，默认20", required = false) Integer pageSize) {
        try {
            StationEntitySearchService.SearchResult search =
                    entitySearchService.searchIds(prompt, "CAMERA");
            List<String> ids = search.getIds();
            if (ids == null || ids.isEmpty()) {
                return AjaxResultVO.ok("未找到符合条件的摄像头")
                        .addQueryPageData(new ArrayList<>(), 0)
                        .put("entityCandidateCount", 0)
                        .put("entitySearchStatus", search.getStatus());
            }

            // IDs 由 MCP 内部 ES 检索产生，直接交给视频业务接口批量过滤；先查全量状态，再在 MCP 内筛选。
            CameraListDto query = new CameraListDto();
            query.setPage("1");
            query.setLimit(String.valueOf(ids.size()));
            query.setIds(ids);
            AjaxResultVO matchedResult = videoService.listPage(query);
            if (matchedResult == null || !matchedResult.isOk()) {
                return matchedResult == null
                        ? AjaxResultVO.error("摄像头实时状态查询失败，请稍后重试")
                        : matchedResult;
            }
            List<HashMap<String, Object>> cameras = matchedResult.getData();
            if (cameras == null) {
                cameras = new ArrayList<>();
            }

            List<HashMap<String, Object>> filtered = new ArrayList<>();
            String statusFilter = onlineStatus == null || onlineStatus.isBlank() ? null : onlineStatus.trim();
            if (statusFilter != null && !"0".equals(statusFilter) && !"1".equals(statusFilter)) {
                return AjaxResultVO.error(400, "在线状态仅允许传1（在线）、0（离线）或不传");
            }
            for (HashMap<String, Object> camera : cameras) {
                boolean online = isCameraOnline(camera.get("Online"));
                if (statusFilter == null || ("1".equals(statusFilter) && online)
                        || ("0".equals(statusFilter) && !online)) {
                    filtered.add(camera);
                }
            }

            int pageNumber = parsePositiveInt(pageNum, 1);
            int limit = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 100);
            int fromIndex = Math.min((pageNumber - 1) * limit, filtered.size());
            int toIndex = Math.min(fromIndex + limit, filtered.size());
            List<HashMap<String, Object>> pageData = filtered.subList(fromIndex, toIndex);

            AjaxResultVO result = AjaxResultVO.ok().addQueryPageData(pageData, filtered.size());
            result.put("entityType", "CAMERA");
            result.put("entitySearchStatus", search.getStatus());
            result.put("entityCandidateCount", ids.size());
            result.put("matchedCount", cameras.size());
            result.put("missingCount", Math.max(0, ids.size() - cameras.size()));
            result.put("count", filtered.size());
            result.put("page", pageNumber);
            result.put("limit", limit);
            result.put("onlineStatus", statusFilter);
            result.put("returnedCount", pageData.size());
            return result;
        } catch (IllegalArgumentException ex) {
            return AjaxResultVO.error(400, ex.getMessage());
        } catch (IllegalStateException ex) {
            log.warn("摄像头实体定位失败：{}", ex.getMessage());
            return AjaxResultVO.error(503, ex.getMessage());
        } catch (Exception ex) {
            log.error("摄像头自然语言查询失败", ex);
            return AjaxResultVO.error("摄像头查询失败，请稍后重试");
        }
    }

    private boolean isCameraOnline(Object onlineValue) {
        if (onlineValue instanceof Boolean value) {
            return value;
        }
        if (onlineValue instanceof Number value) {
            return value.intValue() == 1;
        }
        return onlineValue != null
                && ("true".equalsIgnoreCase(String.valueOf(onlineValue))
                || "1".equals(String.valueOf(onlineValue))
                || "online".equalsIgnoreCase(String.valueOf(onlineValue)));
    }

    private int parsePositiveInt(String value, int defaultValue) {
        try {
            return value == null || value.isBlank() ? defaultValue : Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
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

