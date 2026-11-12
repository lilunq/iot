package com.sysware.xian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.domain.AiAnalysisResult;
import com.sysware.xian.mapper.AiAnalysisResultMapper;
import com.sysware.xian.swcloud.domain.AiAnalysisPageResult;
import com.sysware.xian.swcloud.domain.AiAnalysisRecordVO;
import com.sysware.xian.swcloud.domain.ProblemAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisResultService extends ServiceImpl<AiAnalysisResultMapper, AiAnalysisResult> {

    private final ObjectMapper objectMapper;

    /**
     * 分页查询分析记录（按创建时间倒序），resultData反序列化为ProblemAnalysisResult结构
     *
     * @param type     分析类型，为空则查全部
     * @param page     页码
     * @param size     每页条数
     * @param yearMonth 指定年月（如 2025-06），不为空时仅查询创建时间小于该月下一个月初的记录
     */
    public AiAnalysisPageResult pageQuery(String type, int page, int size, String yearMonth) {
        LambdaQueryWrapper<AiAnalysisResult> wrapper = new LambdaQueryWrapper<AiAnalysisResult>()
                .orderByDesc(AiAnalysisResult::getCreateTime);
        if (type != null && !type.isBlank()) {
            wrapper.eq(AiAnalysisResult::getType, type);
        }
        if (yearMonth != null && !yearMonth.isBlank()) {
            // 解析年月（如 2025-06），加1个月后取月初，筛选 createTime < 该时间点
            LocalDateTime cutoff = YearMonth.parse(yearMonth)
                    .plusMonths(1)
                    .atDay(1)
                    .atStartOfDay();
            wrapper.lt(AiAnalysisResult::getCreateTime, cutoff);
        }
        Page<AiAnalysisResult> pageResult = page(new Page<>(page, size), wrapper);

        List<AiAnalysisRecordVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .toList();
        return new AiAnalysisPageResult(records, pageResult.getTotal());
    }

    /**
     * 删除指定时间之前创建的分析记录
     * <p>
     * 保留 problem_analysis（问题库月分析结果）与 device_fault_repair（设备故障AI分析建议缓存，
     * 供同设备派单任务长期复用），其余类型超期清理。
     *
     * @param cutoffTime 截止时间，早于此时间的记录将被删除
     * @return 删除的记录数
     */
    public int deleteByCreateTimeBefore(LocalDateTime cutoffTime) {
        LambdaQueryWrapper<AiAnalysisResult> wrapper = new LambdaQueryWrapper<AiAnalysisResult>()
                .notIn(AiAnalysisResult::getType, ProblemAnalysisService.RESULT_TYPE, DeviceFaultAiAnalysisService.RESULT_TYPE)
                .lt(AiAnalysisResult::getCreateTime, cutoffTime);
        int deleted = getBaseMapper().delete(wrapper);
        log.info("清理 ai_analysis_result 旧数据完成，删除 {} 条记录（截止时间: {}）", deleted, cutoffTime);
        return deleted;
    }

    /**
     * 实体转视图对象：仅当 type=problem_analysis 时才将 resultData 反序列化为 ProblemAnalysisResult（失败时为 null）；
     * 其余类型（如 report）的 resultData 为纯文本（Markdown），不参与反序列化，原文见 resultDataRaw。
     */
    private AiAnalysisRecordVO toVO(AiAnalysisResult entity) {
        ProblemAnalysisResult resultData = null;
        String raw = entity.getResultData();
        // 仅 problem_analysis 类型的 resultData 才反序列化为结构化对象；report 等类型为纯字符串，见 resultDataRaw
        if (ProblemAnalysisService.RESULT_TYPE.equals(entity.getType())
                && raw != null && !raw.isBlank()) {
            try {
                resultData = objectMapper.readValue(raw, ProblemAnalysisResult.class);
            } catch (Exception e) {
                log.warn("分析记录resultData反序列化失败: id={}", entity.getId(), e);
            }
        }
        return AiAnalysisRecordVO.builder()
                .id(entity.getId())
                .type(entity.getType())
                .oData(entity.getOData())
                .resultData(resultData)
                .resultDataRaw(raw)
                .remake(entity.getRemake())
                .createTime(entity.getCreateTime())
                .build();
    }
}
