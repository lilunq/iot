package com.sysware.xian.service;

import com.sysware.xian.controller.ComputingPowerController;
import com.sysware.xian.controller.SpeakerPermissionController;
import com.sysware.xian.controller.TTSController;
import com.sysware.xian.swcloud.domain.DispatchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 定时任务统一调度服务
 * <p>
 * 所有 @Scheduled 注解集中在此，具体的业务执行方法仍由各自的 Service / Controller 保留。
 * 新增定时任务时，只需在此添加 @Scheduled 方法并委托调用对应 Service 的业务方法即可。
 *
 * @author sysware
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledService {

    private final ReportService reportService;
    private final ProblemAnalysisService problemAnalysisService;
    private final EnvAnalysisService envAnalysisService;
    private final DispatchTaskService dispatchTaskService;
    private final DashboardService dashboardService;
    private final AiAnalysisResultService aiAnalysisResultService;
    private final ComputingPowerController computingPowerController;
    private final DispatchService dispatchService;
    private final SpeakerPermissionController speakerPermissionController;
    private final FireInspectionService fireInspectionService;
    private final PresentationChatService presentationChatService;
    private final TTSController ttsController;

    // ==================== TTS缓存清理 ====================

    /**
     * 每天凌晨2点清理所有StreamChatV2 session缓存和responseId缓存
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void clearAllTtsCaches() {
        log.info("[定时任务] 开始清理所有TTS缓存");
        ttsController.scheduledClearAllCaches();
    }

    // ==================== 用户同步 ====================

    /**
     * 每分钟同步Iot用户信息
     */
//    @Scheduled(cron = "0 */2 * * * ?")
    public void iotUserSync() {
        log.info("[定时任务] 每分钟同步iot用户");
        speakerPermissionController.sync();
    }

    // ==================== 消防巡查 ====================

    /**
     * 每5分钟查询当日消防巡查数据，异常设备生成/更新任务，恢复设备完结任务
     */
//    @Scheduled(cron = "10 */10 * * * ?")
    public void checkFireInspection() {
        log.info("[定时任务] 开始检查消防巡查异常");
        fireInspectionService.scheduledCheckFireInspection();
    }

    // ==================== 演示模式全站概述 ====================

    /**
     * 每1分钟刷新全站概述缓存：重新查询各路接口生成文字概述写入 Redis，
     * 供演示模式"全站情况"分支读缓存直接返回，消除多路接口查询耗时
     */
//    @Scheduled(cron = "20 * * * * ?")
    public void refreshStationOverviewCache() {
        log.info("[定时任务] 开始刷新全站概述缓存");
        presentationChatService.refreshStationOverviewCache();
    }

    // ==================== 报告相关 ====================

    /**
     * 每日23:50生成运行报告并落库
     */
//    @Scheduled(cron = "30 50 23 * * ?")
    public void generateDailyReport() {
        log.info("[定时任务] 开始生成运行报告");
        reportService.scheduledGenerateReport();
    }

    // ==================== 问题分析 ====================

    /**
     * 每日凌晨2点执行问题库AI分析并持久化结果
     */
//    @Scheduled(cron = "10 10 2 * * ?")
    public void analyzeProblems() {
        log.info("[定时任务] 开始问题库AI分析");
        problemAnalysisService.scheduledAnalyze();
    }

    // ==================== 环境分析 ====================

    /**
     * 每小时执行一次环境分析并缓存
     */
//    @Scheduled(cron = "30 10 * * * ?")
    public void envAnalysis() {
        log.info("[定时任务] 开始环境分析");
        envAnalysisService.scheduledEnvAnalysis();
    }

    // ==================== 设备故障恢复检查 ====================

    /**
     * 每小时检查运维Agent未完成任务的设备是否已恢复
     */
//    @Scheduled(cron = "20 0 * * * ?")
    public void checkDeviceFaultRecovery() {
        log.info("[定时任务] 开始检查设备故障恢复状态");
        dispatchTaskService.scheduledCheckDeviceFaultRecovery();
    }

    // ==================== 站房评估 ====================

    /**
     * 每小时评估并存储站房运行
     */
//    @Scheduled(cron = "10 0 * * * ?")
    public void calculateDashboardScore() {
        log.info("[定时任务] 开始评估站房运行");
        dashboardService.scheduledCalculateScore();
    }

    /**
     * 每月1号凌晨2点清除10天前的历史AI分析
     */
//    @Scheduled(cron = "0 0 2 1 * ?")
    public void cleanOldDashboardScores() {
        log.info("[定时任务] 开始清理历史数据");
        // 清理站房运行评分历史数据
        dashboardService.scheduledCleanOldScores();
        // 清理 ai_analysis_result 表10天前的记录
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(10)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        try {
            int deleted = aiAnalysisResultService.deleteByCreateTimeBefore(cutoffTime);
            log.info("[定时任务] ai_analysis_result 清理完成，删除 {} 条记录", deleted);
        } catch (Exception e) {
            log.error("[定时任务] 清理 ai_analysis_result 旧数据失败", e);
        }
    }

    // ==================== 站房运行评分日期刷新 ====================

    /**
     * 每天凌晨1点将 tunnel_run_score 所有记录的 create_time 日期改为当日，时分秒保持不变
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void refreshTunnelRunScoreCreateDate() {
        log.info("[定时任务] 开始刷新站房运行评分日期");
        dashboardService.refreshAllCreateDateToToday();
    }

    // ==================== 计算能力监控 ====================

    /**
     * 后台定时轮询metrics接口（每2秒），缓存快照供接口返回
     * 注意：pollMetrics 依赖 ComputingPowerController 内部的缓存状态，故业务方法保留在 Controller 中
     */
//    @Scheduled(fixedDelay = ComputingPowerController.POLLING_INTERVAL_MS)
    public void pollComputingPowerMetrics() {
        computingPowerController.pollMetrics();
    }

    /**
     * 每半小时检查一次算力服务器最高温度，达到90℃且不存在未完结的
     * 算力服务器监控任务时，新建提示任务入库dispatch_task
     */
//    @Scheduled(cron = "50 0/30 * * * ?")
    public void checkServerHighTemperature() {
        log.info("[定时任务] 开始检查算力服务器温度");
        int maxTemperature = computingPowerController.getCurrentMaxTemperature();
        dispatchTaskService.checkServerHighTemperature(maxTemperature);
    }

    // ==================== 大屏应急预案 ====================

    /**
     * 每日1:40顺序执行大屏应急预案生成并落库
     * <p>
     * 按顺序执行两个预案：
     * 1. 旅客滞留三级应急预案
     * 2. 火灾三级（较大火灾）应急预案
     * 前一个执行完才执行后一个，结果落库到 ai_analysis_result（type=emergency-plan）。
     */
//    @Scheduled(cron = "40 5 8 * * ?")
    public void generateEmergencyPlans() {
        log.info("[定时任务] 开始顺序生成大屏应急预案");
        String[] questions = {
                "旅客滞留Ⅰ级应急预案。生成执行预案",
                "旅客滞留Ⅱ级应急预案。生成执行预案",
                "旅客滞留Ⅲ级应急预案。生成执行预案",
                "火灾Ⅰ级应急预案。生成执行预案",
                "火灾Ⅱ级应急预案。生成执行预案",
                "火灾Ⅲ级应急预案。生成执行预案",
                "火灾Ⅳ级应急预案。生成执行预案",
                "防洪Ⅰ级应急预案。生成执行预案",
                "防洪Ⅱ级应急预案。生成执行预案",
                "防洪Ⅲ级应急预案。生成执行预案",
                "防洪Ⅳ级应急预案。生成执行预案",
                "大规模停电应急预案。生成执行预案",
                "从业人员伤亡事故Ⅰ级应急预案。生成执行预案",
                "从业人员伤亡事故Ⅱ级应急预案。生成执行预案",
                "从业人员伤亡事故Ⅲ级应急预案。生成执行预案",
                "高温中暑Ⅰ级应急预案。生成执行预案",
                "高温中暑Ⅱ级应急预案。生成执行预案",
                "高温中暑Ⅲ级应急预案。生成执行预案",
                "地震Ⅰ级应急预案。生成执行预案",
                "地震Ⅱ级应急预案。生成执行预案",
                "地震Ⅲ级应急预案。生成执行预案",
                "地震Ⅳ级应急预案。生成执行预案",
                "网络安全Ⅳ级（一般）应急预案。生成执行预案",
                "网络安全Ⅲ级（较大）应急预案。生成执行预案",
                "网络安全Ⅱ级（重大）应急预案。生成执行预案",
                "网络安全Ⅰ级（特别重大）应急预案。生成执行预案",
                "客票发售和预定急预案一页纸IV级（一般）应急预案。生成执行预案",
                "客票发售和预定急预案一页纸Ⅲ级（较大）应急预案。生成执行预案",
                "客票发售和预定急预案一页纸Ⅱ级（重大）应急预案。生成执行预案",
                "客票发售和预定急预案一页纸Ⅰ级（特别重大）应急预案。生成执行预案",
                "除雪作业蓝色预警（Ⅳ 级）应急预案。生成执行预案",
                "除雪作业黄色预警（Ⅲ 级）应急预案。生成执行预案",
                "除雪作业橙色预警（Ⅱ 级）应急预案。生成执行预案",
                "除雪作业红色预警（Ⅰ 级）应急预案。生成执行预案",
                "电梯设备事故及组织（西安站站长指挥）应急预案。生成执行预案",
                "生产生活用水中断应急预案。生成执行预案",
                "群体性事件Ⅲ级应急预案。生成执行预案",
                "群体性事件Ⅱ级应急预案。生成执行预案",
                "群体性事件Ⅰ级应急预案。生成执行预案",
                "铁路交通事故Ⅲ 级应急预案。生成执行预案",
                "铁路交通事故Ⅱ 级应急预案。生成执行预案",
                "铁路交通事故Ⅰ 级应急预案。生成执行预案"
        };
        for (String question : questions) {
            try {
                // 尝试获取信号量：如果前端正在分析相同问题则跳过（结果会由前端的异步任务落库）
                if (!dispatchService.tryAcquireScreenSemaphore(question)) {
                    log.info("[定时任务] 大屏应急预案跳过（正在分析中）: {}", question);
                    continue;
                }
                try {
                    log.info("[定时任务] 大屏应急预案开始: {}", question);
                    DispatchRequest request = new DispatchRequest();
                    request.setQuestion(question);
                    request.setOperatorName("system");
                    request.setOperatorId("0");
                    request.setType(3);
                    dispatchService.emergencyPlanScreen(request);
                    log.info("[定时任务] 大屏应急预案完成: {}", question);
                } finally {
                    dispatchService.releaseScreenSemaphore(question);
                }
            } catch (Exception e) {
                log.error("[定时任务] 大屏应急预案失败: {}", question, e);
            }
        }
        log.info("[定时任务] 大屏应急预案全部执行完毕");
    }
}
