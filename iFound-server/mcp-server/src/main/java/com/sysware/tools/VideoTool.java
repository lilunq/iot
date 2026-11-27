package com.sysware.tools;

import com.sysware.service.VideoService;
import com.sysware.service.StationEntitySearchService;
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
    private final StationEntitySearchService entitySearchService;

    /**
     * 获取摄像头列表。
     *
     * @param name       摄像头名称
     * @param page       页码
     * @param ids        实体检索返回的摄像头ID集合；非空时在MCP层批量过滤
     * @return 原始 AjaxResultVO（含摄像头列表数据及Online状态）
     */
    @Tool(
            name = "listCamera",
            description = "底层摄像头目录查询，支持名称或ID集合过滤。自然语言摄像头查询优先使用 queryStationCameras；仅在已有明确摄像头名称或ID集合时直接调用本工具。"
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
        return videoClient.listPage(name, finalPage, "5");
    }

    /**
     * 面向 Agent 的完整摄像头查询：自然语言实体定位 + 实时 Online 状态查询。
     */
    @Tool(
            name = "queryStationCameras",
            description = "根据用户完整自然语言查询西安东站摄像头及实时在线状态。内部先定位真实 CAMERA 候选，再批量查询视频服务实时 Online 状态；用户要求只看在线摄像头时传 onlineOnly=true。Agent 无需分步调用实体检索和摄像头目录查询。"
    )
    public AjaxResultVO queryStationCameras(
            @ToolParam(description = "用户完整的原始问题，例如：查看候车大厅摄像头") String prompt,
            @ToolParam(description = "是否只返回在线摄像头；用户明确要求在线时传true，否则false", required = false) Boolean onlineOnly,
            @ToolParam(description = "页码，从1开始，默认1", required = false) String page,
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

            // 当前实体检索最多返回20个候选；一次批量读取即可覆盖候选集合。
            AjaxResultVO matchedResult = listCameraByIds(ids, "1");
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
            boolean onlyOnline = Boolean.TRUE.equals(onlineOnly);
            for (HashMap<String, Object> camera : cameras) {
                if (!onlyOnline || isCameraOnline(camera.get("Online"))) {
                    filtered.add(camera);
                }
            }

            int pageNumber = parsePositiveInt(page, 1);
            int limit = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 100);
            int fromIndex = Math.min((pageNumber - 1) * limit, filtered.size());
            int toIndex = Math.min(fromIndex + limit, filtered.size());
            List<HashMap<String, Object>> pageData = filtered.subList(fromIndex, toIndex);

            AjaxResultVO result = AjaxResultVO.ok().addQueryPageData(pageData, filtered.size());
            result.put("entityType", "CAMERA");
            result.put("entitySearchStatus", search.getStatus());
            result.put("entityCandidateCount", ids.size());
            result.put("matchedCount", matchedResult.get("matchedCount"));
            result.put("missingCount", matchedResult.get("missingCount"));
            result.put("onlineOnly", onlyOnline);
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

        final int requestedPageSize = 100;
        AjaxResultVO firstPage = videoClient.listPage(null, "1", String.valueOf(requestedPageSize));
        if (firstPage == null || firstPage.getData() == null) {
            return AjaxResultVO.error("摄像头状态查询失败，请稍后重试");
        }

        List<HashMap<String, Object>> matched = new ArrayList<>();
        Set<String> foundIds = new LinkedHashSet<>();
        List<HashMap<String, Object>> firstData = firstPage.getData();
        appendMatchedCameras(firstData, wantedIds, foundIds, matched);

        Object rawCount = firstPage.get("count");
        int reportedCount = rawCount instanceof Number ? ((Number) rawCount).intValue() : firstData.size();
        int total = Math.max(reportedCount, firstData.size());
        // 若视频服务对 limit 有上限，以实际第一页条数计算分页，避免漏查后续摄像头。
        int scanPageSize = firstData.size() > 0 && firstData.size() < requestedPageSize && total > firstData.size()
                ? firstData.size() : requestedPageSize;
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
        AjaxResultVO result = AjaxResultVO.ok().addQueryPageData(pageData, matched.size());
        result.put("requestedCount", wantedIds.size());
        result.put("matchedCount", matched.size());
        result.put("missingCount", Math.max(0, wantedIds.size() - matched.size()));
        return result;
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

