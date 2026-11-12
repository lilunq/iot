package com.sysware.xian.service;

import com.sysware.xian.swcloud.domain.AjaxResultVO;
import com.sysware.xian.swcloud.domain.RosterPageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 值班人员查询服务
 * <p>
 * 调用新接口 POST {rosterBaseUrl}/digitalStation/getData
 * 参数：type=gbzb, dateStr=当日日期（yyyy-MM-dd）
 * 返回 data 数组，每条含 membername / depname / membertype / area 字段
 * <p>
 * 筛选逻辑：area 或 depname 包含排除关键词的记录被去掉，仅保留西安东站相关值班人员
 *
 * @Author: LiYanRu
 * @date: 2026-07-16
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class RosterService {

    private final RestTemplate restTemplate;

    @Value("${ai.roster-url:http://172.12.20.12:19001}")
    private String rosterBaseUrl;

    /**
     * 排除关键词：area 或 depname 包含其中任一关键词的记录被去掉
     */
    private static final Set<String> ROSTER_EXCLUDE_KEYWORDS = Set.of(
            "西安北站", "西安车站", "西安站",
            "信息（西安车站）", "信息（西安北站）",
            "司机班西安车站", "司机班西安北站",
            "西安客运", "西安北客运",
            "西安北运转", "西安运转",
            "售票车间", "行包车间"
    );

    /**
     * 分页查询值班人员（兼容旧调用，内部调用新接口）
     *
     * @param request 查询参数（使用 dutyDateStart 或当日日期作为 dateStr）
     * @return 上游返回的 AjaxResultVO（含 Status/msg/code/data），data 已筛选
     */
    public AjaxResultVO queryPage(RosterPageRequest request) {
        // 1. 填充日期默认值
        request.fillDefaultDate();

        // 2. 构建 dateStr（yyyy-MM-dd 格式）
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        // 优先使用 request 中的日期
        if (request.getDutyDateStart() != null && !request.getDutyDateStart().isBlank()) {
            String raw = request.getDutyDateStart();
            // 兼容旧格式 yyyyMMdd → 转为 yyyy-MM-dd
            if (raw.length() == 8 && !raw.contains("-")) {
                dateStr = raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8);
            } else {
                dateStr = raw;
            }
        }

        // 3. 构建 form-urlencoded 参数
        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("type", "gbzb");
        formParams.add("dateStr", dateStr);

        // 4. 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formParams, headers);

        // 5. 发起请求
        String url = rosterBaseUrl + "/duty/listDuty";
        log.debug("查询值班人员: url={}, params={}", url, formParams);

        try {
            ResponseEntity<AjaxResultVO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    AjaxResultVO.class
            );
            AjaxResultVO body = response.getBody();
            // 6. 排除关键词过滤暂时停用：不过滤人员，全部保留（如需恢复，调用 filterRosterData(body)）
            // return filterRosterData(body);
            return body;
        } catch (Exception e) {
            log.error("查询值班人员失败", e);
            throw new RuntimeException("查询值班人员失败: " + e.getMessage());
        }
    }

    /**
     * 筛选值班人员数据，去掉 area 或 depname 包含排除关键词的记录
     *
     * @param body 上游返回的 AjaxResultVO
     * @return 筛选后的 AjaxResultVO（data 已更新）
     */
    @SuppressWarnings("unchecked")
    private AjaxResultVO filterRosterData(AjaxResultVO body) {
        if (body == null) {
            return null;
        }
        Object dataObj = body.get("data");
        if (!(dataObj instanceof List<?> rawList)) {
            return body;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String area = map.get("area") != null ? map.get("area").toString() : "";
            String depname = map.get("depname") != null ? map.get("depname").toString() : "";
            String combined = area + depname;
            // 如果 area 或 depname 包含任一排除关键词，则跳过
            boolean excluded = ROSTER_EXCLUDE_KEYWORDS.stream().anyMatch(combined::contains);
            if (!excluded) {
                filtered.add((Map<String, Object>) item);
            }
        }
        log.debug("值班人员筛选: 原始 {} 条, 筛选后 {} 条", rawList.size(), filtered.size());
        body.put("data", filtered);
        body.put("count", filtered.size());
        return body;
    }
}
