package com.sysware.controller;

import com.sysware.service.XadService;
import com.sysware.swcloud.domian.AjaxResultVO;
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
