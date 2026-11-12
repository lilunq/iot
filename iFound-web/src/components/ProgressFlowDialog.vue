<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  progress: { type: Object, default: null },
  resultDesc: { type: String, default: '' }
})

const emit = defineEmits(['close'])

const order = computed(() => props.progress?.order || {})
const asset = computed(() => props.progress?.asset || {})
const logs = computed(() => props.progress?.logs || [])
const aiDiagnosis = computed(() => props.progress?.aiDiagnosis || null)
const hasProgressData = computed(() => props.progress && (props.progress.order || props.progress.logs))

const progressStatusMeta = {
  '10': { label: '待派单', color: '#f97316', bg: 'rgba(249,115,22,.12)' },
  '20': { label: '待确认', color: '#3b82f6', bg: 'rgba(59,130,246,.12)' },
  '30': { label: '处理中', color: '#8b5cf6', bg: 'rgba(139,92,246,.12)' },
  '40': { label: '待验收', color: '#0f766e', bg: 'rgba(15,118,110,.12)' },
  '50': { label: '已完成', color: '#16a34a', bg: 'rgba(22,163,74,.12)' },
  '60': { label: '已关闭', color: '#6b7280', bg: 'rgba(107,114,128,.12)' }
}

const logTypeMeta = {
  CREATE: { label: '创建', color: '#3b82f6' },
  DISPATCH: { label: '派单', color: '#8b5cf6' },
  ACCEPT: { label: '接单', color: '#0ea5e9' },
  PROCESS: { label: '处理中', color: '#f59e0b' },
  FINISH: { label: '维修结束', color: '#14b8a6' },
  ACCEPTANCE: { label: '验收', color: '#22c55e' },
  CLOSE: { label: '关闭', color: '#6b7280' }
}

const repairResultMeta = {
  '10': { label: '故障排除', color: '#16a34a' },
  '20': { label: '无法维修', color: '#f97316' },
  '30': { label: '报废', color: '#ef4444' },
  '40': { label: '其他', color: '#6b7280' }
}

const processTypeMeta = {
  REPAIR: { label: '维修', color: '#3b82f6' },
  RENOVATION: { label: '更新改造', color: '#16a34a' },
  OVERHAUL: { label: '大修', color: '#f97316' },
  RECTIFICATION: { label: '整治', color: '#ef4444' }
}

const aiCollapseState = ref({ reason: false, conclusion: false, measures: false })

function formatTime(time) {
  if (!time) return '-'
  return String(time).replace('T', ' ').slice(0, 19)
}

function formatStatus(value) {
  if (value === null || value === undefined || value === '') return '-'
  return progressStatusMeta[String(value)]?.label || String(value)
}

function getStatusStyle(status) {
  const meta = status !== undefined && status !== null ? progressStatusMeta[String(status)] : null
  return {
    color: meta?.color || '#475569',
    background: meta?.bg || 'rgba(148,163,184,.12)',
    borderColor: meta?.color || 'rgba(148,163,184,.2)'
  }
}

function getLogSummary(log) {
  switch (log.logType) {
    case 'CREATE':
      try {
        if (order.value.orderContent) {
          const parsed = JSON.parse(order.value.orderContent)
          return parsed.issueContent || log.logContent || '人工创建工单'
        }
      } catch (e) { /* ignore */ }
      return log.logContent || '人工创建工单'
    case 'DISPATCH': {
      const dt = String(order.value.dispatchType || '')
      const name = order.value.executeUserName || '-'
      if (dt === 'MANUAL') return `人工派单给${name}`
      if (dt === 'AUTO') return `自动派单给${name}`
      return log.logContent || `派单给${name}`
    }
    case 'ACCEPT':
      return `${order.value.executeUserName || ''}已接单`
    case 'FINISH':
      return `维修结果：${repairResultMeta[String(order.value.repairResult || '')]?.label || order.value.repairResult || '-'}。${order.value.repairContent || '暂无维修说明'}`
    case 'ACCEPTANCE':
      return `验收备注：${order.value.acceptanceRemark || log.logContent || '验收完成'}`
    default:
      return log.logContent || '-'
  }
}

function buildTimelineColor(log, index) {
  if (index === 0) return '#8b5cf6'
  if (log.logType && logTypeMeta[log.logType]) return logTypeMeta[log.logType].color
  if (log.afterStatus && progressStatusMeta[String(log.afterStatus)]) return progressStatusMeta[String(log.afterStatus)].color
  return '#60a5fa'
}

const parsedMeasures = computed(() => {
  if (!aiDiagnosis.value?.measures) return []
  try {
    return typeof aiDiagnosis.value.measures === 'string' ? JSON.parse(aiDiagnosis.value.measures) : aiDiagnosis.value.measures
  } catch { return [] }
})

const parsedTools = computed(() => {
  const tools = aiDiagnosis.value?.tools
  if (!tools) return ''
  try {
    const parsed = typeof tools === 'string' ? JSON.parse(tools) : tools
    if (Array.isArray(parsed)) return parsed.join('、')
  } catch { /* not JSON array */ }
  return tools
})

const aiPriorityMap = { LOW: { label: '低', color: '#6b7280' }, MEDIUM: { label: '中', color: '#f59e0b' }, HIGH: { label: '高', color: '#ef4444' }, CRITICAL: { label: '紧急', color: '#ef4444' } }
</script>

<template>
  <div class="progress-overlay" v-if="visible" @click.self="emit('close')">
    <div class="progress-modal">
      <div class="progress-modal-header">
        <span>工单进度流转日志</span>
        <button class="progress-close-btn" @click="emit('close')">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
      <div class="progress-modal-body">
        <!-- 有完整工单数据 -->
        <template v-if="hasProgressData">
          <!-- 顶部信息 -->
          <div class="pf-hero">
            <div class="pf-hero-left">
              <div class="pf-kicker">工单进度流转日志</div>
              <div class="pf-no">{{ order.orderTitle || '工单详情' }}</div>
              <div class="pf-sub">工单编号：{{ order.orderNo || '-' }} ｜ 问题编号：{{ order.issueNo || '-' }}</div>
            </div>
            <div class="pf-hero-right">
              <div class="pf-ring">
                <svg viewBox="0 0 80 80" width="80" height="80">
                  <circle cx="40" cy="40" r="34" fill="none" stroke="rgba(255,255,255,0.08)" stroke-width="6"/>
                  <circle cx="40" cy="40" r="34" fill="none" stroke="#00d4ff" stroke-width="6"
                    :stroke-dasharray="2 * Math.PI * 34"
                    :stroke-dashoffset="2 * Math.PI * 34 * (1 - (order.orderProgress || 0) / 100)"
                    stroke-linecap="round" transform="rotate(-90 40 40)" />
                </svg>
                <div class="pf-ring-text">
                  <span class="pf-ring-percent">{{ order.orderProgress || 0 }}%</span>
                  <span class="pf-ring-label">进度</span>
                </div>
              </div>
              <div class="pf-status-block">
                <div class="pf-status-label">当前状态</div>
                <div class="pf-status-tag" :style="getStatusStyle(order.status)">{{ formatStatus(order.status) }}</div>
                <div class="pf-status-owner">负责人：{{ order.executeUserName || '-' }}</div>
              </div>
            </div>
          </div>

          <div class="pf-grid">
            <!-- 左侧：设备+工单概览 -->
            <div class="pf-panel pf-panel-overview">
              <div class="pf-section-title">关联设备</div>
              <div class="pf-asset-name">{{ asset.assetName || '-' }}</div>
              <div class="pf-meta-row"><span class="pf-meta-label">设备编码</span><span class="pf-meta-value">{{ asset.assetCode || '-' }}</span></div>
              <div class="pf-meta-row"><span class="pf-meta-label">组织</span><span class="pf-meta-value">{{ asset.orgName || '-' }}</span></div>
              <div class="pf-meta-row"><span class="pf-meta-label">系统</span><span class="pf-meta-value">{{ asset.specSystemName || '-' }}</span></div>
              <div class="pf-meta-row"><span class="pf-meta-label">分类</span><span class="pf-meta-value">{{ asset.assetCategoryName || '-' }}</span></div>
              <div class="pf-meta-row"><span class="pf-meta-label">区域</span><span class="pf-meta-value">{{ asset.areaName || '-' }}</span></div>
              <div class="pf-meta-row"><span class="pf-meta-label">位置</span><span class="pf-meta-value">{{ asset.location || '-' }}</span></div>

              <div class="pf-section-title" style="margin-top:16px;">工单概览</div>
              <div class="pf-meta-row"><span class="pf-meta-label">过程类型</span><span class="pf-meta-value" :style="{ color: processTypeMeta[String(order.processType||'')]?.color || 'inherit' }">{{ processTypeMeta[String(order.processType||'')]?.label || order.processType || '-' }}</span></div>
              <div class="pf-meta-row"><span class="pf-meta-label">维修结果</span><span class="pf-meta-value" :style="{ color: repairResultMeta[String(order.repairResult||'')]?.color || 'inherit' }">{{ repairResultMeta[String(order.repairResult||'')]?.label || order.repairResult || '-' }}</span></div>
              <div class="pf-meta-row"><span class="pf-meta-label">派单时间</span><span class="pf-meta-value">{{ formatTime(order.dispatchTime) }}</span></div>
              <div class="pf-meta-row"><span class="pf-meta-label">接单时间</span><span class="pf-meta-value">{{ formatTime(order.acceptTime) }}</span></div>
              <div class="pf-meta-row"><span class="pf-meta-label">维修结束</span><span class="pf-meta-value">{{ formatTime(order.finishTime) }}</span></div>

              <div class="pf-result-box">
                <div class="pf-box-label">维修过程记录</div>
                <div class="pf-box-content">{{ order.repairContent || '暂无维修过程记录' }}</div>
              </div>
              <div class="pf-result-box">
                <div class="pf-box-label">备品备件使用记录</div>
                <div class="pf-box-content">{{ order.sparePartsUsed || '暂无备品备件使用记录' }}</div>
              </div>
            </div>

            <!-- 右侧：流转日志 -->
            <div class="pf-panel pf-panel-timeline">
              <div class="pf-panel-head">
                <div class="pf-section-title">流转日志</div>
                <span class="pf-count-badge">共 {{ logs.length }} 条记录</span>
              </div>
              <div class="pf-timeline-scroll">
                <div class="pf-empty" v-if="logs.length === 0">暂无流转日志</div>
                <div class="pf-timeline" v-else>
                  <div class="pf-tl-item" v-for="(log, index) in logs" :key="log.id || index">
                    <div class="pf-tl-dot" :style="{ background: buildTimelineColor(log, index) }"></div>
                    <div class="pf-tl-line" v-if="index < logs.length - 1"></div>
                    <div class="pf-tl-card">
                      <div class="pf-tl-head">
                        <div>
                          <span class="pf-tl-type">{{ logTypeMeta[log.logType||'']?.label || log.logType || '操作记录' }}</span>
                          <span class="pf-tl-operator">{{ log.operatorUserName || '系统' }}</span>
                        </div>
                        <span class="pf-tl-result" v-if="log.operateResult">{{ log.operateResult }}</span>
                      </div>
                      <div class="pf-tl-time">{{ formatTime(log.createTime) }}</div>
                      <div class="pf-tl-content">{{ log.logContent }}</div>

                      <!-- AI 分析结果（仅在 CREATE 类型且有 AI 诊断数据时显示） -->
                      <div v-if="log.logType === 'CREATE' && aiDiagnosis" class="pf-ai-section">
                        <div class="pf-ai-confidence">置信度：{{ aiDiagnosis.confidence || 0 }}%</div>

                        <div class="pf-ai-collapse-item">
                          <div class="pf-ai-collapse-header" @click="aiCollapseState.reason = !aiCollapseState.reason">
                            <span class="pf-ai-collapse-title">故障原因分析</span>
                            <span class="pf-ai-collapse-icon" :class="{ 'is-open': aiCollapseState.reason }">
                              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/></svg>
                            </span>
                          </div>
                          <div v-show="aiCollapseState.reason" class="pf-ai-collapse-content">
                            <div class="pf-ai-main">{{ aiDiagnosis.reason || '-' }}</div>
                            <div v-if="aiDiagnosis.reasonDetail" class="pf-ai-detail">{{ aiDiagnosis.reasonDetail }}</div>
                          </div>
                        </div>

                        <div class="pf-ai-collapse-item">
                          <div class="pf-ai-collapse-header" @click="aiCollapseState.conclusion = !aiCollapseState.conclusion">
                            <span class="pf-ai-collapse-title">诊断结论</span>
                            <span class="pf-ai-collapse-icon" :class="{ 'is-open': aiCollapseState.conclusion }">
                              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/></svg>
                            </span>
                          </div>
                          <div v-show="aiCollapseState.conclusion" class="pf-ai-collapse-content">
                            <div class="pf-ai-main">{{ aiDiagnosis.conclusion || '-' }}</div>
                            <div v-if="aiDiagnosis.conclusionDetail" class="pf-ai-detail">{{ aiDiagnosis.conclusionDetail }}</div>
                          </div>
                        </div>

                        <div v-if="parsedMeasures.length > 0" class="pf-ai-collapse-item">
                          <div class="pf-ai-collapse-header" @click="aiCollapseState.measures = !aiCollapseState.measures">
                            <span class="pf-ai-collapse-title">措施建议</span>
                            <span class="pf-ai-collapse-icon" :class="{ 'is-open': aiCollapseState.measures }">
                              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/></svg>
                            </span>
                          </div>
                          <div v-show="aiCollapseState.measures" class="pf-ai-collapse-content">
                            <div class="pf-ai-measures">
                              <div class="pf-ai-measure" v-for="(measure, idx) in parsedMeasures" :key="idx">
                                <span class="pf-measure-index">{{ idx + 1 }}</span>
                                <span class="pf-measure-text">{{ measure }}</span>
                              </div>
                            </div>
                          </div>
                        </div>

                        <div v-if="parsedTools" class="pf-ai-tools">
                          <span class="pf-tools-label">所需工具：</span>
                          <span class="pf-tools-value">{{ parsedTools }}</span>
                        </div>
                      </div>

                      <div class="pf-tl-summary">{{ getLogSummary(log) }}</div>
                      <div class="pf-tl-meta">
                        <span>变更前：{{ formatStatus(log.beforeStatus) }}</span>
                        <span>变更后：{{ formatStatus(log.afterStatus) }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 无工单数据，仅显示 resultDesc -->
        <template v-else>
          <div class="pf-result-desc-section">
            <div class="pf-result-desc-title">处理结果</div>
            <div class="pf-result-desc-content">{{ resultDesc || '暂无处理结果描述' }}</div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.progress-overlay ::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
.progress-overlay ::-webkit-scrollbar-track {
  background: rgba(0, 66, 126, 0.05);
  border-radius: 3px;
}
.progress-overlay ::-webkit-scrollbar-thumb {
  background: rgba(0, 66, 126, 0.4);
  border-radius: 3px;
}
.progress-overlay ::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 66, 126, 0.6);
}
.progress-overlay {
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 66, 126, 0.4) transparent;
  position: fixed;
  top: 0; left: 0;
  width: 100vw; height: 100vh;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3000;
  overflow-y: auto;
  padding: 30px 20px;
}

.progress-modal {
  position: relative;
  width: 90vw;
  max-width: 1200px;
  max-height: 90vh;
  background: rgb(0,66,126);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}

.progress-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid rgba(59, 130, 246, 0.15);
  font-size: 16px;
  font-weight: 700;
  color: #c0d0e0;
}

.progress-close-btn {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  cursor: pointer;
  color: #a0b0c0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  transition: all 0.2s;
}
.progress-close-btn:hover {
  background: rgba(239, 68, 68, 0.2);
  border-color: rgba(239, 68, 68, 0.4);
  color: #ef4444;
}

.progress-modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

/* Hero */
.pf-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
  padding: 20px;
  border-radius: 14px;
  background: rgba(255,255,255,.025);
  border: 1px solid rgba(59, 130, 246, 0.1);
}
.pf-hero-left { flex: 1; min-width: 0; }
.pf-kicker { font-size: 12px; color: rgba(255,255,255,0.5); margin-bottom: 6px; }
.pf-no { font-size: 18px; font-weight: 700; color: #fff; word-break: break-word; }
.pf-sub { margin-top: 8px; font-size: 13px; color: rgba(255,255,255,0.65); }
.pf-hero-right { display: flex; align-items: center; flex-shrink: 0; gap: 20px; }

.pf-ring { position: relative; width: 80px; height: 80px; }
.pf-ring-text {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.pf-ring-percent { font-size: 16px; font-weight: 700; color: #00d4ff; }
.pf-ring-label { font-size: 11px; color: rgba(255,255,255,0.5); }

.pf-status-label { font-size: 12px; color: rgba(255,255,255,0.5); margin-bottom: 6px; }
.pf-status-tag {
  display: inline-flex; padding: 4px 12px; border-radius: 999px;
  font-size: 12px; font-weight: 600; border: 1px solid;
}
.pf-status-owner { margin-top: 8px; font-size: 12px; color: rgba(255,255,255,0.6); }

/* Grid */
.pf-grid { display: grid; grid-template-columns: 0.9fr 1.3fr; gap: 16px; }
.pf-panel {
  border-radius: 14px;
  border: 1px solid rgba(59, 130, 246, 0.12);
  background: rgba(255,255,255,.025);
  padding: 18px;
}
.pf-panel-overview { max-height: 550px; overflow-y: auto; }
.pf-panel-timeline { max-height: 550px; display: flex; flex-direction: column; min-height: 0; }
.pf-panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }

.pf-section-title { font-size: 14px; font-weight: 700; color: #c0d0e0; margin-bottom: 10px; }
.pf-count-badge {
  font-size: 11px; color: rgba(160,180,200,0.6);
  padding: 2px 8px; border-radius: 999px; background: rgba(59,130,246,0.1);
}
.pf-asset-name { font-size: 15px; font-weight: 700; color: #e0e8f0; margin-bottom: 8px; }

.pf-meta-row {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 6px 0; border-bottom: 1px dashed rgba(59,130,246,0.08);
}
.pf-meta-label { flex: 0 0 auto; width: 70px; font-size: 12px; color: rgba(160,180,200,0.6); }
.pf-meta-value { flex: 1; font-size: 12px; color: #c0d0e0; text-align: right; word-break: break-word; }

.pf-result-box {
  margin-top: 12px; padding: 12px; border-radius: 10px;
  background: rgba(59,130,246,0.06); border: 1px solid rgba(59,130,246,0.1);
}
.pf-box-label { font-size: 12px; color: rgba(160,180,200,0.6); margin-bottom: 4px; }
.pf-box-content { font-size: 13px; color: #c0d0e0; line-height: 1.6; }

/* Timeline */
.pf-timeline-scroll { flex: 1; overflow-y: auto; min-height: 0; }
.pf-empty {
  display: flex; align-items: center; justify-content: center;
  height: 100%; color: rgba(160,180,200,0.4); font-size: 13px;
}
.pf-tl-item { position: relative; padding-left: 24px; padding-bottom: 16px; }
.pf-tl-dot { position: absolute; left: 0; top: 4px; width: 10px; height: 10px; border-radius: 50%; z-index: 1; }
.pf-tl-line { position: absolute; left: 4px; top: 16px; bottom: 0; width: 2px; background: rgba(59,130,246,0.15); }
.pf-tl-card {
  padding: 12px; border-radius: 10px;
  background: rgba(10,22,40,0.5); border: 1px solid rgba(59,130,246,0.1);
}
.pf-tl-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
.pf-tl-type { font-size: 13px; font-weight: 600; color: #c0d0e0; }
.pf-tl-operator { font-size: 12px; color: rgba(160,180,200,0.5); margin-left: 6px; }
.pf-tl-result {
  font-size: 11px; padding: 2px 8px; border-radius: 999px;
  background: rgba(59,130,246,0.1); color: #8bb4e0;
}
.pf-tl-time { margin-top: 6px; font-size: 11px; color: rgba(160,180,200,0.5); }
.pf-tl-content { margin-top: 6px; font-size: 13px; color: #b0c4d8; line-height: 1.6; }
.pf-tl-summary {
  margin-top: 8px; padding: 8px 10px; border-radius: 8px;
  background: rgba(59,130,246,0.05); font-size: 12px; color: #a0b8d0;
}
.pf-tl-meta { margin-top: 8px; display: flex; gap: 16px; font-size: 11px; color: rgba(160,180,200,0.4); }

/* AI section inside timeline */
.pf-ai-section {
  margin-top: 12px; padding: 12px;
  background: linear-gradient(135deg, rgba(99,102,241,0.04), rgba(139,92,246,0.04));
  border: 1px solid rgba(139,92,246,0.15); border-radius: 10px;
}
.pf-ai-confidence { font-size: 12px; color: #8b5cf6; margin-bottom: 8px; }
.pf-ai-collapse-item { border-bottom: 1px solid rgba(139,92,246,0.1); }
.pf-ai-collapse-item:last-child { border-bottom: none; }
.pf-ai-collapse-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 4px; cursor: pointer; user-select: none;
}
.pf-ai-collapse-title { font-size: 13px; font-weight: 600; color: #c0d0e0; }
.pf-ai-collapse-icon { width: 16px; height: 16px; transition: transform 0.3s ease; color: rgba(160,180,200,0.5); }
.pf-ai-collapse-icon.is-open { transform: rotate(180deg); }
.pf-ai-collapse-content { padding: 0 4px 10px; }
.pf-ai-main { font-size: 13px; font-weight: 500; color: #c0d0e0; line-height: 1.6; }
.pf-ai-detail {
  margin-top: 6px; font-size: 12px; color: rgba(160,180,200,0.6); line-height: 1.7;
  padding-top: 6px; border-top: 1px dashed rgba(139,92,246,0.15);
}
.pf-ai-measures { display: flex; flex-direction: column; gap: 6px; }
.pf-ai-measure {
  display: flex; align-items: flex-start; gap: 8px;
  padding: 6px 8px; border-radius: 6px;
  background: rgba(10,22,40,0.4); border: 1px solid rgba(139,92,246,0.08);
}
.pf-measure-index {
  width: 18px; height: 18px; flex-shrink: 0; border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff;
  font-size: 10px; font-weight: 600; display: flex; align-items: center; justify-content: center;
}
.pf-measure-text { font-size: 12px; color: #b0c4d8; line-height: 1.5; }
.pf-ai-tools {
  margin-top: 10px; padding: 8px 10px; border-radius: 6px;
  background: rgba(59,130,246,0.05); font-size: 12px;
}
.pf-tools-label { font-weight: 600; color: #b0c4d8; }
.pf-tools-value { color: rgba(160,180,200,0.7); }

/* resultDesc fallback */
.pf-result-desc-section { padding: 20px; }
.pf-result-desc-title { font-size: 15px; font-weight: 700; color: #c0d0e0; margin-bottom: 12px; }
.pf-result-desc-content {
  font-size: 14px; color: #b0c4d8; line-height: 1.8;
  padding: 16px; border-radius: 10px;
  background: rgba(59,130,246,0.06); border: 1px solid rgba(59,130,246,0.1);
}

@media (max-width: 900px) {
  .pf-grid { grid-template-columns: 1fr; }
}
</style>
