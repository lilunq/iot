package com.sysware.tools;

import com.sysware.service.OpsKnowledgeService;
import com.sysware.swcloud.domian.DispatchRequest;
import com.sysware.swcloud.domian.EmergencyPlanScreenResult;
import com.sysware.swcloud.domian.R;
import com.sysware.swcloud.feign.IFoundClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * @Description: 提供给AI MCP的知识库工具（应急预案/机电设备运维等）
 * @Author: LiYanRu
 * @date: 2026-07-04
 **/
@Component
@RequiredArgsConstructor
public class RagFlowTool {

    private static final String DEVICE_FAULT_CHAT_ID = "bbf9f49c7f2811f191580bb256f6c5db";

    private final OpsKnowledgeService opsKnowledgeService;
    private final IFoundClient iFoundClient;

    @Tool(description = "应急预案知识库问答，查询应急预案相关内容", name = "opsKnowledgeQuery")
    public R<String> opsKnowledgeQuery(String question) {
        try {
            // MCP工具面向大模型，仅返回答案文本
            return R.success(opsKnowledgeService.query(question).answerText());
        } catch (Exception e) {
            return R.error("调用知识库失败: " + e.getMessage());
        }
    }

    @Tool(description = "机电设备运维规范与故障处理知识库问答，查询设备运维规范、故障处理方案等", name = "deviceFaultQuery")
    public R<String> deviceFaultQuery(String question) {
        try {
            return R.success(opsKnowledgeService.query(question, DEVICE_FAULT_CHAT_ID).answerText());
        } catch (Exception e) {
            return R.error("调用知识库失败: " + e.getMessage());
        }
    }

//    @Tool(description = "大屏应急预案查询：根据应急预案名称生成执行预案（含今日值班人员匹配，如：火灾Ⅲ级应急预案、旅客滞留Ⅰ级应急预案）。" +
//            "结果异步生成：若返回'正在生成中'，请稍后用相同的预案名称再次调用本工具获取结果", name = "emergencyPlanScreenQuery")
    public R<String> emergencyPlanScreenQuery(@ToolParam(description = "应急预案名称，如：火灾Ⅲ级应急预案") String question) {
        try {
            DispatchRequest request = new DispatchRequest();
            request.setQuestion(question);
            request.setOperatorName("mcp-ai");
            request.setOperatorId("0");
            request.setType(3);
            R<EmergencyPlanScreenResult> result = iFoundClient.emergencyPlanScreen(request);
            if (result == null) {
                return R.error("调用大屏应急预案接口失败");
            }
            EmergencyPlanScreenResult data = result.getData();
            if (data == null) {
                // 未命中缓存，AI异步分析中，提示稍后再次调用轮询
                return R.success("预案正在生成中，请稍后再次调用本工具查询「" + question + "」以获取结果");
            }
            StringBuilder sb = new StringBuilder();
            sb.append("【").append(data.getQuestion()).append("】执行预案\n\n");
            sb.append(data.getEmergencyAnswer());
            if (data.getDutyPersons() != null && !data.getDutyPersons().isEmpty()) {
                sb.append("\n\n今日值班人员：");
                for (EmergencyPlanScreenResult.DutyPersonInfo p : data.getDutyPersons()) {
                    sb.append("\n- ").append(p.getPersonName())
                            .append("（").append(p.getMemberType() != null ? p.getMemberType() : p.getPositionName()).append("）")
                            .append("，电话：").append(p.getPhone());
                }
            }
            return R.success(sb.toString());
        } catch (Exception e) {
            return R.error("调用大屏应急预案失败: " + e.getMessage());
        }
    }

    /**
     * 应急预案分类（14类，多个响应等级已合并）
     */
    private static final String[] EMERGENCY_PLAN_CATEGORIES = {
            "旅客滞留应急预案",
            "火灾应急预案",
            "防洪应急预案",
            "大规模停电应急预案",
            "从业人员伤亡事故应急预案",
            "高温中暑应急预案",
            "地震应急预案",
            "网络安全应急预案",
            "客票发售和预定应急预案",
            "除雪作业应急预案",
            "电梯设备事故应急预案",
            "生产生活用水中断应急预案",
            "群体性事件应急预案",
            "铁路交通事故应急预案"
    };

    @Tool(description = "应急预案统计：返回车站全部应急预案的分类清单（md格式，14个分类，多响应等级已合并）及按应急响应等级划分的总数", name = "emergencyPlanStatistics")
    public R<String> emergencyPlanStatistics() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("车站应急预案共 **14 个分类**：\n\n");
            for (int i = 0; i < EMERGENCY_PLAN_CATEGORIES.length; i++) {
                sb.append(i + 1).append(". ").append(EMERGENCY_PLAN_CATEGORIES[i]).append("\n");
            }
            sb.append("\n按应急响应等级划分共 42 个应急预案。");
            return R.success(sb.toString());
        } catch (Exception e) {
            return R.error("应急预案统计失败: " + e.getMessage());
        }
    }
}
