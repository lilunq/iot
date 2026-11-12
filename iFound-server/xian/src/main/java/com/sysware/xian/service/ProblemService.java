package com.sysware.xian.service;

import com.sysware.xian.swcloud.domain.AjaxResultVO;
import com.sysware.xian.swcloud.domain.ProblemPageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 问题库查询服务
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemService {

    private final RestTemplate restTemplate;

    @Value("${ai.problem-url}")
    private String problemBaseUrl;

    /**
     * 分页查询问题库（内部优先按日期范围一次调用 /stationWtjc/list，失败回退逐天调用旧接口并合并结果）
     * <p>
     * data 原样透传上游数据，字段名与 /stationWtjc/list 返回一致（checks/grade/property/duty_ks_name/
     * check_by/duty_name/correct_condition/finish_time/create_time/stat_date/id）；逐天回退时字段名归一化为同一套命名。
     *
     * @param request 查询参数（dateStart/dateEnd 为 yyyyMMdd 格式）
     * @return AjaxResultVO（Status/msg/code 与上游一致，data 为问题列表，count 为总条数）
     */
    public AjaxResultVO queryPage(ProblemPageRequest request) {
        // 1. 填充日期默认值
        request.fillDefaultDate();

        // 2. 按日期范围查询问题库，合并结果
        List<Map<String, Object>> allData = fetchByDateRange(request.getDateStart(), request.getDateEnd());

        // 3. 组装返回结果
        AjaxResultVO result = AjaxResultVO.ok();
        result.addQueryPageData(allData, allData.size());
        return result;
    }

    /**
     * 按单天查询问题库数据（旧接口，现作为日期段接口 /stationWtjc/list 的回退路径）
     * 调用上游接口 POST /digitalStation/getData2，参数 dateStr + type=wtjc
     *
     * @param dateStr 日期（yyyy-MM-dd 格式）
     * @return 当天问题列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> queryByDate(String dateStr) {
        // 1. 构建 form-urlencoded 参数
        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("dateStr", dateStr);
        formParams.add("type", "wtjc");

        // 2. 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formParams, headers);

        // 3. 发起请求
        String url = problemBaseUrl + "/digitalStation/getData2";
        log.debug("查询问题库(单天): url={}, dateStr={}", url, dateStr);

        try {
            ResponseEntity<AjaxResultVO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    AjaxResultVO.class
            );
            AjaxResultVO body = response.getBody();
            if (body == null || !body.isOk()) {
                log.warn("查询问题库(单天)失败: dateStr={}, msg={}", dateStr, body != null ? body.getMsg() : "无响应");
                return List.of();
            }
            Object dataObj = body.get("data");
            if (dataObj instanceof List<?> list) {
                // 旧接口返回大写字段，归一化为 /stationWtjc/list 的字段名，保持两条路径返回结构一致
                List<Map<String, Object>> mapped = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> raw) {
                        mapped.add(mapGetData2Fields((Map<String, Object>) raw));
                    }
                }
                return mapped;
            }
            return List.of();
        } catch (Exception e) {
            log.error("查询问题库(单天)失败: dateStr={}", dateStr, e);
            return List.of();
        }
    }

    /**
     * 按日期范围查询问题库数据并合并结果
     * <p>
     * 优先一次调用日期段接口 POST /stationWtjc/list（start_date/end_date，yyyyMMdd），
     * 调用失败或响应异常时回退为逐天调用旧接口 /digitalStation/getData2 并合并。
     *
     * @param dateStart 起始日期（yyyyMMdd 格式）
     * @param dateEnd   截止日期（yyyyMMdd 格式）
     * @return 合并后的问题列表
     */
    public List<Map<String, Object>> fetchByDateRange(String dateStart, String dateEnd) {
        List<Map<String, Object>> rangeData = queryByDateRangeUpstream(dateStart, dateEnd);
        if (rangeData != null) {
            return rangeData;
        }

        // 回退：按日期范围逐天调用旧接口，合并结果
        log.warn("日期段接口查询失败，回退逐天调用旧接口: {}~{}", dateStart, dateEnd);
        DateTimeFormatter inputFmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        DateTimeFormatter apiFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        LocalDate start = LocalDate.parse(dateStart, inputFmt);
        LocalDate end = LocalDate.parse(dateEnd, inputFmt);

        List<Map<String, Object>> allData = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            List<Map<String, Object>> dayData = queryByDate(d.format(apiFmt));
            allData.addAll(dayData);
        }
        log.debug("问题库日期范围查询完成（逐天回退）: {}~{}, 共{}条", dateStart, dateEnd, allData.size());
        return allData;
    }

    /**
     * 一次调用日期段接口查询问题库数据
     * 调用上游接口 POST /stationWtjc/list，参数 start_date + end_date（yyyyMMdd）
     *
     * @param dateStart 起始日期（yyyyMMdd 格式）
     * @param dateEnd   截止日期（yyyyMMdd 格式）
     * @return 该日期段问题列表；调用失败或响应异常时返回 null（由调用方回退逐天查询）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryByDateRangeUpstream(String dateStart, String dateEnd) {
        URI url = UriComponentsBuilder.fromUriString(problemBaseUrl + "/stationWtjc/list")
                .queryParam("start_date", dateStart)
                .queryParam("end_date", dateEnd)
                .build().encode().toUri();
        log.debug("查询问题库(日期段): url={}", url);

        try {
            ResponseEntity<AjaxResultVO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    HttpEntity.EMPTY,
                    AjaxResultVO.class
            );
            AjaxResultVO body = response.getBody();
            if (body == null || !body.isOk()) {
                log.warn("查询问题库(日期段)失败: dateStart={}, dateEnd={}, msg={}",
                        dateStart, dateEnd, body != null ? body.getMsg() : "无响应");
                return null;
            }
            Object dataObj = body.get("data");
            if (!(dataObj instanceof List<?> list)) {
                return List.of();
            }
            // 原样透传，字段名保持上游 /stationWtjc/list 返回的命名
            List<Map<String, Object>> data = (List<Map<String, Object>>) list;
            log.debug("查询问题库(日期段)完成: {}~{}, 共{}条", dateStart, dateEnd, data.size());
            return data;
        } catch (Exception e) {
            log.warn("查询问题库(日期段)异常: dateStart={}, dateEnd={}", dateStart, dateEnd, e);
            return null;
        }
    }

    /**
     * 将旧接口（getData2）返回的大写字段归一化为日期段接口（/stationWtjc/list）的字段名，
     * 使逐天回退路径与主路径返回的数据结构一致。旧接口无 stat_date/id，仅映射共有字段。
     *
     * @param raw 旧接口返回的原始数据（大写字段）
     * @return 归一化后的数据（与 /stationWtjc/list 字段名一致）
     */
    private Map<String, Object> mapGetData2Fields(Map<String, Object> raw) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("create_time", raw.get("CREATE_TIME"));
        mapped.put("grade", raw.get("GRADE"));
        mapped.put("property", raw.get("PROPERTY"));
        mapped.put("duty_ks_name", raw.get("DUTY_KS_NAME"));
        mapped.put("checks", raw.get("CHECKS"));
        mapped.put("check_by", raw.get("CHECK_BY"));
        mapped.put("duty_name", raw.get("DUTY_NAME"));
        mapped.put("correct_condition", raw.get("CORRECT_CONDITION"));
        mapped.put("finish_time", raw.get("FINISH_TIME"));
        return mapped;
    }

}
