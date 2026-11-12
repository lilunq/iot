package com.sysware.xian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sysware.xian.domain.AiDiagnosisRecord;
import com.sysware.xian.mapper.AiDiagnosisRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDiagnosisRecordService extends ServiceImpl<AiDiagnosisRecordMapper, AiDiagnosisRecord> {

    /**
     * 分页查询诊断记录列表
     */
    public Page<AiDiagnosisRecord> listRecordsPage(String sourceType, int page, int size) {
        LambdaQueryWrapper<AiDiagnosisRecord> wrapper = new LambdaQueryWrapper<AiDiagnosisRecord>()
                .orderByDesc(AiDiagnosisRecord::getCreateTime);
        if (sourceType != null) {
            wrapper.eq(AiDiagnosisRecord::getSourceType, sourceType);
        }
        return page(new Page<>(page, size), wrapper);
    }

    /**
     * 查询诊断记录列表
     */
    public List<AiDiagnosisRecord> listRecords(String sourceType) {
        LambdaQueryWrapper<AiDiagnosisRecord> wrapper = new LambdaQueryWrapper<AiDiagnosisRecord>()
                .orderByDesc(AiDiagnosisRecord::getCreateTime);
        if (sourceType != null) {
            wrapper.eq(AiDiagnosisRecord::getSourceType, sourceType);
        }
        return list(wrapper);
    }

    /**
     * 根据来源数据ID查询诊断记录
     */
    public AiDiagnosisRecord getBySourceDataId(String sourceDataId) {
        LambdaQueryWrapper<AiDiagnosisRecord> wrapper = new LambdaQueryWrapper<AiDiagnosisRecord>()
                .eq(AiDiagnosisRecord::getSourceDataId, sourceDataId)
                .orderByDesc(AiDiagnosisRecord::getCreateTime)
                .last("LIMIT 1");
        return getOne(wrapper);
    }

    /**
     * 更新派发状态
     */
    public boolean updateDispatchStatus(String id, String dispatchUser) {
        AiDiagnosisRecord record = new AiDiagnosisRecord();
        record.setId(id);
        record.setDispatchStatus(1);
        record.setDispatchUser(dispatchUser);
        record.setDispatchTime(LocalDateTime.now());
        return updateById(record);
    }
}
