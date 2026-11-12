package com.sysware.xian.domain;

import lombok.Data;
import java.util.List;

/**
 * 通用数据分析响应
 */
@Data
public class CommonAnalysisResponse {
    /** AI分析结果 */
    private String analysis;
    
    /** 处理建议 */
    private String suggestion;
    
    /** 识别到的数据类型（如：区域分析、设备告警、通用问答等） */
    private String dataType;
    
    /** 派发任务列表 */
    private List<ZoneAnalysisResponse.DispatchTaskItem> dispatchTasks;
}