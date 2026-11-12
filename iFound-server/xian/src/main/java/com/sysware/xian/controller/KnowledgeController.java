package com.sysware.xian.controller;

import com.sysware.xian.service.AiAnalysisResultService;
import com.sysware.xian.service.DispatchService;
import com.sysware.xian.service.ProblemAnalysisService;
import com.sysware.xian.service.ProblemService;
import com.sysware.xian.service.RosterService;
import com.sysware.xian.swcloud.domain.*;
import com.sysware.xian.swcloud.fegin.MCPXClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 知识库问答及值班查询接口
 *
 * @author sysware
 */
@Slf4j
@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
@Tag(name = "知识库+车站", description = "知识库问答（应急预案/机电设备运维规范）、值班人员查询及问题库查询")
public class KnowledgeController {

    private final MCPXClient mcpxClient;
    private final RosterService rosterService;
    private final DispatchService dispatchService;
    private final ProblemService problemService;
    private final ProblemAnalysisService problemAnalysisService;
    private final AiAnalysisResultService aiAnalysisResultService;

    /**
     * 应急预案知识库问答
     */
    @PostMapping("/emergency-plan")
    @Operation(summary = "1.应急预案问答", description = "向应急预案知识库提问，返回结构化对象：success调用状态、data.answer答案文本、data.reference检索分块、error错误信息")
    public KnowledgeResponse emergencyPlan(@RequestBody KnowledgeRequest request) {
        log.debug("应急预案问答: question={}", request.getQuestion());
        try {
            return mcpxClient.opsKnowledge(request);
        } catch (Exception e) {
            log.error("调用应急预案知识库失败", e);
            return KnowledgeResponse.error("调用应急预案知识库失败: " + e.getMessage());
        }
    }

    /**
     * 机电设备运维规范与故障处理知识库问答
     */
    @PostMapping("/device-fault")
    @Operation(summary = "1.设备运维问答", description = "向机电设备运维规范与故障处理知识库提问，返回结构化对象：success调用状态、data.answer答案文本、data.reference检索分块、error错误信息")
    public KnowledgeResponse deviceFault(@RequestBody KnowledgeRequest request) {
        log.debug("机电设备运维规范问答: question={}", request.getQuestion());
        try {
            return mcpxClient.deviceFault(request);
        } catch (Exception e) {
            log.error("调用机电设备运维规范知识库失败", e);
            return KnowledgeResponse.error("调用机电设备运维规范知识库失败: " + e.getMessage());
        }
    }

    /**
     * 值班人员分页查询
     */
    @PostMapping("/roster/page")
    @Operation(summary = "2.值班人员分页查询", description = "分页查询值班人员列表（支持按姓名右模糊、按日期范围筛选）")
    public AjaxResultVO rosterPage(@RequestBody RosterPageRequest request) {
        log.debug("值班人员分页查询: page={}, limit={}, dutyDateStart={}, dutyDateEnd={}, personName={}",
                request.getPage(), request.getLimit(), request.getDutyDateStart(),
                request.getDutyDateEnd(), request.getPersonName());
        try {
            return rosterService.queryPage(request);
        } catch (Exception e) {
            log.error("值班人员分页查询失败", e);
            return AjaxResultVO.error("查询值班人员失败: " + e.getMessage());
        }
    }

    /**
     * 应急调度：查询应急预案 → 获取今日值班领导 → AI筛选最佳人选
     */
    @PostMapping("/dispatch")
    @Operation(summary = "2.1应急调度", description = "根据应急预案内容，自动筛选今日最合适的值班领导进行调度，不落库")
    public R<DispatchResult> dispatch(@RequestBody DispatchRequest request) {
        log.debug("应急调度: question={}, operatorName={}, operatorId={}",
                request.getQuestion(), request.getOperatorName(), request.getOperatorId());
        try {
            DispatchResult result = dispatchService.dispatch(request);
            return R.success(result);
        } catch (Exception e) {
            log.error("应急调度失败", e);
            return R.error("应急调度失败: " + e.getMessage());
        }
    }
    /**
     * 大屏应急预案：查询应急预案 → 匹配今日值班人员岗位 → 填充人员名称和电话 → 落库
     * <p>
     * 支持两种处理模式（通过请求体 type 字段指定）：
     * <ul>
     *   <li>type=1（默认）：AI完成人员匹配并格式化输出，将 answer + 值班人员列表交给 AI，由 AI 智能匹配岗位与人员</li>
     *   <li>type=2：关键词匹配 + AI格式化，先按岗位名称关键词匹配，再交给 AI 提炼格式化</li>
     * </ul>
     */
    @PostMapping("/emergency-plan-screen")
    @Operation(summary = "2.2大屏应急预案", description = "先查缓存，命中则直接返回；未命中则异步执行AI分析，立即返回data=null，结果落库后通过轮询查询。type=1AI匹配+格式化；type=2关键词匹配+AI格式化；type=3(默认)基于chunks原文总结+格式化。结果落库到ai_analysis_result（type=emergency-plan）")
    public R<EmergencyPlanScreenResult> emergencyPlanScreen(@RequestBody DispatchRequest request) {
        log.debug("大屏应急预案: question={}, operatorName={}, operatorId={}, type={}",
                request.getQuestion(), request.getOperatorName(), request.getOperatorId(), request.getType());
        // 预处理 question（与信号量 key 保持一致）
        String question = request.getQuestion()
                .replace("一级", "Ⅰ级").replace("1级", "Ⅰ级")
                .replace("二级", "Ⅱ级").replace("2级", "Ⅱ级")
                .replace("三级", "Ⅲ级").replace("3级", "Ⅲ级");
        request.setQuestion(question + "。生成执行预案");

        // 0. 先查缓存：如果今天已经分析过，直接返回结果
        EmergencyPlanScreenResult cached = dispatchService.getCachedScreenResult(request.getQuestion(), request.getType());
        if (cached != null) {
            log.info("大屏应急预案命中缓存，直接返回: question={}", request.getQuestion());
            return R.success(cached);
        }

        // 1. 未命中缓存，走信号量控制
        String lockKey = request.getQuestion();
        if (!dispatchService.tryAcquireScreenSemaphore(lockKey)) {
            log.info("大屏应急预案: 问题正在分析中: {}", lockKey);
            R<EmergencyPlanScreenResult> r = R.success();
            r.setMsg("AI正在分析中，请稍后查看");
            return r;
        }

        // 2. 拿到许可，启动异步分析
        try {
            dispatchService.emergencyPlanScreenAsync(request, lockKey);
        } catch (Exception e) {
            dispatchService.releaseScreenSemaphore(lockKey);
            log.error("大屏应急预案异步提交失败: {}", e.getMessage());
            return R.error("大屏应急预案提交失败: " + e.getMessage());
        }

        R<EmergencyPlanScreenResult> r = R.success();
        r.setMsg("AI正在分析中，请稍后查看");
        return r;
    }

    /**
     * 问题库分页查询
     */
    @PostMapping("/problem/page")
    @Operation(summary = "3.问题库分页查询", description = "分页查询问题库（责任部门固定为西安东站，日期默认今日）")
    public AjaxResultVO problemPage(@RequestBody ProblemPageRequest request) {
        log.debug("问题库分页查询: limit={}, dateStart={}, dateEnd={}",
                request.getLimit(), request.getDateStart(), request.getDateEnd());
        try {
            return problemService.queryPage(request);
        } catch (Exception e) {
            log.error("问题库分页查询失败", e);
            return AjaxResultVO.error("查询问题库失败: " + e.getMessage());
        }
    }

    /**
     * 问题库AI分析：统计三维度数量 + 应急预案知识库 → AI生成下月工作重点/检查重点
     */
    @PostMapping("/problem/analysis")
    @Operation(summary = "3.1问题库AI实时分析", description = "按日期范围（默认本月1号~今日）拉取问题库数据，统计问题级别/类别/区域数量，结合应急预案知识库由AI生成下月工作重点与检查重点（含结构化列表和完整通报文本）")
    public R<ProblemAnalysisResult> problemAnalysis(@RequestBody ProblemAnalysisRequest request) {
        log.debug("问题库AI分析: dateStart={}, dateEnd={}", request.getDateStart(), request.getDateEnd());
        try {
            return R.success(problemAnalysisService.analyze(request));
        } catch (Exception e) {
            log.error("问题库AI分析失败", e);
            return R.error("问题库AI分析失败: " + e.getMessage());
        }
    }

    /**
     * 问题库AI分析记录分页查询（定时任务持久化的结果，按创建时间倒序）
     */
    @GetMapping("/problem/analysis/records")
    @Operation(summary = "3.2问题库AI分析记录分页查询",
            description = "分页查询定时任务持久化的AI分析记录，按创建时间倒序，默认每页1条（即最新一条）。" +
                    "可选传yearMonth（如2025-06），传入后仅查询创建时间小于该月下一个月初的记录。" +
                    "响应data为AiAnalysisPageResult：records记录列表（每条含id主键、type分析类型、oData本次分析拉取的原始问题明细JSON、" +
                    "resultData分析结果（ProblemAnalysisResult结构，含统计/workFocus/inspectionFocus/reportText）、remake备注（统计周期）、createTime创建时间），total记录总数")
    public R<AiAnalysisPageResult> problemAnalysisRecords(
            @Parameter(description = "分析类型，默认problem_analysis（问题库AI分析）") @RequestParam(defaultValue = ProblemAnalysisService.RESULT_TYPE) String type,
            @Parameter(description = "页码，从1开始，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数，默认1（即取最新一条）") @RequestParam(defaultValue = "1") int size,
            @Parameter(description = "指定年月（如 2025-06），传入后仅查询创建时间小于该月下一个月初的记录，按时间倒序") @RequestParam(required = false) String yearMonth) {
        try {
            return R.success(aiAnalysisResultService.pageQuery(type, page, size, yearMonth));
        } catch (Exception e) {
            log.error("问题库AI分析记录查询失败", e);
            return R.error("查询分析记录失败: " + e.getMessage());
        }
    }

}
