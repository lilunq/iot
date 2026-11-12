package com.sysware.xian.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sysware.xian.domain.AiDiagnosisRecord;
import com.sysware.xian.service.AiDiagnosisRecordService;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai-diagnosis")
@RequiredArgsConstructor
@Tag(name = "AI诊断记录管理", description = "AI诊断记录的增删改查")
public class AiDiagnosisRecordController {

    private final AiDiagnosisRecordService aiDiagnosisRecordService;

    @GetMapping("/list")
    @Operation(summary = "获取诊断记录列表（分页）")
    public AjaxResultVO list(
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<AiDiagnosisRecord> pageResult = aiDiagnosisRecordService.listRecordsPage(sourceType, page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        return AjaxResultVO.ok().data(result);
    }

    @GetMapping("/by-source/{sourceDataId}")
    @Operation(summary = "根据来源数据ID获取诊断记录")
    public AjaxResultVO getBySourceDataId(@PathVariable String sourceDataId) {
        AiDiagnosisRecord record = aiDiagnosisRecordService.getBySourceDataId(sourceDataId);
        return record != null ? AjaxResultVO.ok().data(record) : AjaxResultVO.error("记录不存在");
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取诊断记录详情")
    public AjaxResultVO getById(@PathVariable String id) {
        AiDiagnosisRecord record = aiDiagnosisRecordService.getById(id);
        return record != null ? AjaxResultVO.ok().data(record) : AjaxResultVO.error("记录不存在");
    }

    @PostMapping
    @Operation(summary = "新增诊断记录")
    public AjaxResultVO add(@RequestBody AiDiagnosisRecord record) {
        aiDiagnosisRecordService.save(record);
        return AjaxResultVO.ok().data(record.getId());
    }

    @PutMapping
    @Operation(summary = "更新诊断记录")
    public AjaxResultVO update(@RequestBody AiDiagnosisRecord record) {
        aiDiagnosisRecordService.updateById(record);
        return AjaxResultVO.ok();
    }

    @PutMapping("/dispatch/{id}")
    @Operation(summary = "更新派发状态为已派发")
    public AjaxResultVO updateDispatchStatus(@PathVariable String id, @RequestParam String dispatchUser) {
        aiDiagnosisRecordService.updateDispatchStatus(id, dispatchUser);
        return AjaxResultVO.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除诊断记录")
    public AjaxResultVO delete(@PathVariable String id) {
        aiDiagnosisRecordService.removeById(id);
        return AjaxResultVO.ok();
    }
}
