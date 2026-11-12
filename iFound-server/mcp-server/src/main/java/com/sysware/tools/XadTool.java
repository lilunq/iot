package com.sysware.tools;

import com.sysware.service.XadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class XadTool {

    private final XadService xadService;

    /**
     * 查询到达层/出发层/候车层的环境监测数据（温度、湿度、噪音、照度、PM2.5、CO2、氧气等）。
     * 返回已格式化好的中文 Markdown 结果，可直接展示给用户。
     *
     * @param subareaCid 区域 CID，非必填；仅支持 0.001.001.001-0.002（到达层）、
     *                   0.001.001.001-0.003（出发层）、0.001.001.001-0.004（候车层），
     *                   不传则查询全部支持区域
     * @return 格式化的中文 Markdown 文本
     */
    @Tool(name = "getEnvMonitor", description = "查询到达层/出发层/候车层的环境监测数据（温度、湿度、噪音、照度、PM2.5、CO2、氧气等），返回已格式化的中文Markdown结果，可直接原样展示给用户。subarea_cid 仅支持 0.001.001.001-0.002（到达层）、0.001.001.001-0.003（出发层）、0.001.001.001-0.004（候车层），不传则查询全部")
    public String getEnvMonitor(
            @ToolParam(description = "区域CID，非必填。仅支持：0.001.001.001-0.002（到达层）、0.001.001.001-0.003（出发层）、0.001.001.001-0.004（候车层），不传则查询全部", required = false) String subareaCid) {
        log.debug("mcp 调用查询环境监测: subareaCid={}", subareaCid);
        return xadService.queryEnvMonitor(subareaCid);
    }

    /**
     * 查询近7个月旅客发送量生产统计数据。
     *
     * @return 格式化的中文 Markdown 表格文本
     */
    @Tool(name = "getProductionMonthly", description = "查询近7个月的旅客发送量生产统计数据（当月累计与每日旅客发送量），返回已格式化的中文Markdown表格，可直接原样展示给用户")
    public String getProductionMonthly() {
        log.debug("mcp 调用查询生产统计月数据");
        return xadService.queryProductionMonthly();
    }
}
