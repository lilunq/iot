package com.sysware.controller;

import com.sysware.service.XadService;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.feign.EntityCenterClient;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "西安东接口", description = "西安东专属")
public class XadController {

    private final XadService xadService;
    private final EntityCenterClient entityCenterClient;


    /**
     * 调试实体中心自然语言检索接口，原样返回实体中心完整 JSON，便于核对候选实体结构。
     *
     * 请求体示例：{"prompt":"候车大厅的摄像头"}
     * 仅用于内部联调，请勿将该接口作为 Hermes 工具暴露。
     */
    @Operation(summary = "调试实体中心自然语言检索", description = "原样转发 /entity-center/assistant/search 的响应，用于查看完整 JSON 结构")
    @PostMapping("/debug/entity-center/assistant/search")
    public Map<String, Object> debugEntitySearch(@RequestBody Map<String, Object> request) {
        return entityCenterClient.searchAssistant(request);
    }

    /**
     * 查询到达层/出发层/候车层的环境监测数据，返回已格式化的中文 Markdown 结果。
     *
     * @param subarea_cid 区域 CID，非必填；仅支持 0.001.001.001-0.002（到达层）、
     *                    0.001.001.001-0.003（出发层）、0.001.001.001-0.004（候车层），
     *                    不传则查询全部支持区域
     */
    @GetMapping("/env/monitor/getEnvMonitor")
    public AjaxResultVO getEnvMonitor(@RequestParam(name = "subarea_cid", required = false) String subarea_cid) {
        AjaxResultVO result = AjaxResultVO.ok();
        result.put("data", xadService.queryEnvMonitor(subarea_cid));
        return result;
    }

    /**
     * 查询近7个月旅客发送量生产统计数据，返回已格式化的中文 Markdown 表格。
     */
    @GetMapping("/stationScxx/list")
    public AjaxResultVO list() {
        AjaxResultVO result = AjaxResultVO.ok();
        result.put("data", xadService.queryProductionMonthly());
        return result;
    }
}
