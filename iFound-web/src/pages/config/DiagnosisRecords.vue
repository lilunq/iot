<template>
  <div class="ca-panel">
    <div class="ca-toolbar">
      <div class="ca-filters">
        <select v-model="sourceTypeFilter">
          <option value="">全部来源</option>
          <option value="ALARM">故障设备</option>
          <option value="ISSUE">待处理问题</option>
          <option value="COLLABORATION">协同任务</option>
        </select>
        <button class="ca-btn-ghost" @click="fetchRecords">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
            <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
          </svg>
          查询
        </button>
      </div>
      <div class="ca-toolbar-right">
        <button class="ca-btn-ghost" @click="fetchRecords" title="刷新">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <path d="M23 4v6h-6M1 20v-6h6"/>
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
          </svg>
        </button>
      </div>
    </div>

    <div class="records-layout">
      <!-- 左侧表格 -->
      <div class="records-table-wrap">
        <div class="records-table-inner">
          <table class="ca-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>来源类型</th>
                <th>诊断人</th>
                <th>派发状态</th>
                <th>派发人</th>
                <th>诊断时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in recordList" :key="record.id"
                  :class="{ 'row-active': selectedRecord && selectedRecord.id === record.id }"
                  @click="selectRecord(record)">
                <td class="td-id">{{ record.id }}</td>
                <td>
                  <span class="source-tag" :class="getSourceClass(record.sourceType)">
                    {{ getSourceLabel(record.sourceType) }}
                  </span>
                </td>
                <td>{{ record.diagnoseUser || '-' }}</td>
                <td>
                  <span class="dispatch-badge" :class="record.dispatchStatus === 1 ? 'dispatched' : 'pending'">
                    {{ record.dispatchStatus === 1 ? '已派发' : '待派发' }}
                  </span>
                </td>
                <td>{{ record.dispatchUser || '-' }}</td>
                <td>{{ formatTime(record.createTime) }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="recordList.length === 0" class="ca-empty">
            <p>暂无诊断记录</p>
          </div>
        </div>

        <!-- Pagination -->
        <div class="ca-pagination">
          <button class="ca-page-btn" :disabled="pageNum <= 1" @click="goPage(pageNum - 1)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
          <span class="ca-page-info">第 {{ pageNum }} / {{ totalPages }} 页，共 {{ total }} 条</span>
          <button class="ca-page-btn" :disabled="pageNum >= totalPages" @click="goPage(pageNum + 1)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </button>
          <select v-model="pageSize" @change="onPageSizeChange">
            <option :value="10">10条/页</option>
            <option :value="20">20条/页</option>
            <option :value="50">50条/页</option>
          </select>
        </div>
      </div>

      <!-- 右侧详情 -->
      <div class="records-content">
        <div class="content-header">
          <span class="content-title">诊断详情</span>
          <span v-if="selectedRecord" class="content-time">{{ formatTime(selectedRecord.createTime) }}</span>
          <button v-if="selectedRecord" class="ca-btn-export" @click="exportPdf" title="导出PDF">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><polyline points="9 15 12 12 15 15"/>
            </svg>
            导出PDF
          </button>
        </div>
        <div class="content-body" v-if="selectedRecord">
          <!-- 来源数据 -->
          <div class="detail-section">
            <h4 class="detail-title">来源数据</h4>
            <div class="detail-meta">
              <span class="meta-item">{{ getSourceLabel(selectedRecord.sourceType) }} ID: {{ selectedRecord.sourceDataId || '-' }}</span>
            </div>
            <pre class="detail-json">{{ formatJson(selectedRecord.sourceData) }}</pre>
          </div>

          <!-- 设备实时数据 -->
          <div class="detail-section" v-if="selectedRecord.deviceRealtimeData">
            <h4 class="detail-title">设备实时数据</h4>
            <pre class="detail-json">{{ formatJson(selectedRecord.deviceRealtimeData) }}</pre>
          </div>

          <!-- 诊断结果 -->
          <div class="detail-section" v-if="selectedRecord.diagnosisResult">
            <h4 class="detail-title">诊断结果</h4>
            <pre class="detail-json">{{ formatJson(selectedRecord.diagnosisResult) }}</pre>
          </div>

          <!-- 工单信息 -->
          <div class="detail-section" v-if="orderInfo">
            <h4 class="detail-title">工单信息</h4>
            <div class="order-grid">
              <div class="order-item">
                <span class="order-label">工单ID</span>
                <span class="order-value mono">{{ orderInfo.orderId }}</span>
              </div>
              <div class="order-item">
                <span class="order-label">优先级</span>
                <span class="order-value">
                  <span class="priority-tag" :class="getPriorityClass(orderInfo.priority)">{{ orderInfo.priority }}</span>
                </span>
              </div>
              <div class="order-item">
                <span class="order-label">执行人</span>
                <span class="order-value">{{ orderInfo.executeUserName || '-' }}</span>
              </div>
              <div class="order-item">
                <span class="order-label">到达时间</span>
                <span class="order-value">{{ orderInfo.arrivalTime || '-' }}</span>
              </div>
              <div class="order-item">
                <span class="order-label">完成时间</span>
                <span class="order-value">{{ orderInfo.completionTime || '-' }}</span>
              </div>
              <div class="order-item">
                <span class="order-label">所需工具</span>
                <span class="order-value">{{ orderInfo.tools || '-' }}</span>
              </div>
            </div>
            <div class="order-measures" v-if="orderInfo.measures">
              <span class="order-label">处理措施</span>
              <ul class="measure-list">
                <li v-for="(m, idx) in orderInfo.measures" :key="idx">{{ m }}</li>
              </ul>
            </div>
          </div>

          <!-- 备注 -->
          <div class="detail-section" v-if="selectedRecord.remark">
            <h4 class="detail-title">备注</h4>
            <p class="detail-remark">{{ selectedRecord.remark }}</p>
          </div>
        </div>
        <div class="content-body" v-else>
          <div class="content-empty">
            <p>选择左侧记录查看诊断详情</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, inject, nextTick } from 'vue'
import { aiRequest } from '@/utils/request'
import html2canvas from 'html2canvas'
import jsPDF from 'jspdf'

const showToast = inject('showToast') || (() => {})

const recordList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const sourceTypeFilter = ref('')
const selectedRecord = ref(null)

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

const orderInfo = computed(() => {
  if (!selectedRecord.value?.diagnosisResult) return null
  try {
    const data = JSON.parse(selectedRecord.value.diagnosisResult)
    // measures 可能是字符串形式的 JSON 数组，需要二次解析
    if (data.measures && typeof data.measures === 'string') {
      try {
        data.measures = JSON.parse(data.measures)
      } catch {
        data.measures = [data.measures]
      }
    }
    return data
  } catch {
    return null
  }
})

onMounted(() => {
  fetchRecords()
})

async function fetchRecords() {
  try {
    const params = { page: pageNum.value, size: pageSize.value }
    if (sourceTypeFilter.value) params.sourceType = sourceTypeFilter.value

    const res = await aiRequest.get('/ai-diagnosis/list', { params })
    if (res && (res.code === 200 || res.code === '200')) {
      recordList.value = res.data.records || res.data || []
      total.value = res.data.total || recordList.value.length
      if (recordList.value.length > 0) {
        selectedRecord.value = recordList.value[0]
      } else {
        selectedRecord.value = null
      }
    }
  } catch (e) {
    console.error('获取诊断记录失败:', e)
    showToast('error', '获取诊断记录失败')
  }
}

function goPage(page) {
  pageNum.value = page
  fetchRecords()
}

function onPageSizeChange() {
  pageNum.value = 1
  fetchRecords()
}

function selectRecord(record) {
  selectedRecord.value = record
}

function formatTime(time) {
  if (!time) return '-'
  const str = typeof time === 'string' ? time : String(time)
  return str.replace('T', ' ').substring(0, 19)
}

function formatJson(str) {
  if (!str) return '无数据'
  try {
    const parsed = JSON.parse(str)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return str
  }
}

function getSourceLabel(type) {
  const map = { ALARM: '故障设备', ISSUE: '待处理问题', COLLABORATION: '协同任务' }
  return map[type] || type || '-'
}

function getSourceClass(type) {
  const map = { ALARM: 'alarm', ISSUE: 'issue', COLLABORATION: 'collab' }
  return map[type] || ''
}

function getPriorityClass(priority) {
  const map = { HIGH: 'high', MEDIUM: 'medium', LOW: 'low' }
  return map[priority] || 'medium'
}

async function exportPdf() {
  if (!selectedRecord.value) return
  const r = selectedRecord.value

  try {
    showToast('info', '正在生成PDF...')

    const container = document.createElement('div')
    container.style.cssText = 'font-family:system-ui,-apple-system,sans-serif;font-size:14px;color:#333;background:#fff;padding:24px;max-width:800px;line-height:1.6;'

    let html = `
      <h2 style="font-size:18px;margin:0 0 16px;padding-bottom:10px;border-bottom:2px solid #333;">诊断详情</h2>
      <p style="font-size:12px;color:#999;margin:0 0 12px;">诊断时间：${formatTime(r.createTime)}</p>

      <h3 style="font-size:15px;margin:16px 0 8px;padding:6px 0;border-bottom:1px solid #ddd;">来源数据</h3>
      <p style="margin:4px 0;"><strong>来源类型：</strong>${getSourceLabel(r.sourceType)}</p>
      <p style="margin:4px 0;"><strong>来源ID：</strong>${r.sourceDataId || '-'}</p>
      <pre style="background:#f5f5f5;border:1px solid #ddd;border-radius:4px;padding:10px;font-size:12px;white-space:pre-wrap;word-break:break-all;">${formatJson(r.sourceData)}</pre>
    `

    if (r.deviceRealtimeData) {
      html += `
        <h3 style="font-size:15px;margin:16px 0 8px;padding:6px 0;border-bottom:1px solid #ddd;">设备实时数据</h3>
        <pre style="background:#f5f5f5;border:1px solid #ddd;border-radius:4px;padding:10px;font-size:12px;white-space:pre-wrap;word-break:break-all;">${formatJson(r.deviceRealtimeData)}</pre>
      `
    }

    if (r.diagnosisResult) {
      html += `
        <h3 style="font-size:15px;margin:16px 0 8px;padding:6px 0;border-bottom:1px solid #ddd;">诊断结果</h3>
        <pre style="background:#f5f5f5;border:1px solid #ddd;border-radius:4px;padding:10px;font-size:12px;white-space:pre-wrap;word-break:break-all;">${formatJson(r.diagnosisResult)}</pre>
      `
    }

    const oi = orderInfo.value
    if (oi) {
      html += `
        <h3 style="font-size:15px;margin:16px 0 8px;padding:6px 0;border-bottom:1px solid #ddd;">工单信息</h3>
        <table style="width:100%;border-collapse:collapse;font-size:13px;">
          <tr><td style="padding:4px 8px;color:#666;width:120px;">工单ID</td><td style="padding:4px 8px;">${oi.orderId || '-'}</td><td style="padding:4px 8px;color:#666;width:120px;">优先级</td><td style="padding:4px 8px;">${oi.priority || '-'}</td></tr>
          <tr><td style="padding:4px 8px;color:#666;">执行人</td><td style="padding:4px 8px;">${oi.executeUserName || '-'}</td><td style="padding:4px 8px;color:#666;">到达时间</td><td style="padding:4px 8px;">${oi.arrivalTime || '-'}</td></tr>
          <tr><td style="padding:4px 8px;color:#666;">完成时间</td><td style="padding:4px 8px;">${oi.completionTime || '-'}</td><td style="padding:4px 8px;color:#666;">所需工具</td><td style="padding:4px 8px;">${oi.tools || '-'}</td></tr>
        </table>
        ${oi.measures ? `<p style="margin:8px 0 4px;"><strong>处理措施：</strong></p><ul style="margin:0;padding-left:20px;">${oi.measures.map(m => `<li>${m}</li>`).join('')}</ul>` : ''}
      `
    }

    if (r.remark) {
      html += `
        <h3 style="font-size:15px;margin:16px 0 8px;padding:6px 0;border-bottom:1px solid #ddd;">备注</h3>
        <p style="margin:4px 0;">${r.remark}</p>
      `
    }

    container.innerHTML = html
    document.body.appendChild(container)

    const canvas = await html2canvas(container, {
      scale: 2,
      useCORS: true,
      backgroundColor: '#ffffff'
    })

    document.body.removeChild(container)

    const imgWidth = 210
    const imgHeight = (canvas.height * imgWidth) / canvas.width
    const pageHeight = 297
    let heightLeft = imgHeight
    let position = 0

    const pdf = new jsPDF('p', 'mm', 'a4')
    const imgData = canvas.toDataURL('image/png')

    pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
    heightLeft -= pageHeight

    while (heightLeft > 0) {
      position = heightLeft - imgHeight
      pdf.addPage()
      pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
      heightLeft -= pageHeight
    }

    pdf.save(`诊断记录_${r.id}.pdf`)
    showToast('success', '导出成功')
  } catch (e) {
    console.error('导出PDF失败:', e)
    showToast('error', '导出PDF失败')
  }
}
</script>

<style scoped>
.ca-panel {
  padding: 0;
}

.ca-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}

.ca-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.ca-filters select {
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text);
  font-size: 13px;
  outline: none;
}

.ca-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ca-btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-2);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.ca-btn-ghost:hover {
  border-color: var(--border-hover);
}

/* 左右布局 */
.records-layout {
  display: flex;
  gap: 16px;
  height: calc(100vh - 260px);
  min-height: 500px;
}

.records-table-wrap {
  flex: 0 0 55%;
  display: flex;
  flex-direction: column;
  background: var(--card-bg);
  border-radius: var(--radius);
  border: 1px solid var(--border);
  overflow: hidden;
}

.records-table-inner {
  flex: 1;
  overflow-y: auto;
}

.ca-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.ca-table thead {
  background: var(--sidebar-bg);
  position: sticky;
  top: 0;
  z-index: 10;
}
.ca-table th {
  padding: 10px 12px;
  text-align: left;
  font-weight: 600;
  color: var(--text-2);
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}
.ca-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
  color: var(--text);
}
.ca-table tbody tr {
  cursor: pointer;
  transition: background 0.15s;
}
.ca-table tbody tr:hover {
  background: var(--surface-hover);
}
.ca-table tbody tr.row-active {
  background: var(--accent-soft);
}

.td-id {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--text-2);
}

/* 来源标签 */
.source-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.source-tag.alarm {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}
.source-tag.issue {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}
.source-tag.collab {
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
}

/* 派发状态 */
.dispatch-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.dispatch-badge.pending {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}
.dispatch-badge.dispatched {
  background: rgba(8, 174, 102, 0.1);
  color: #08ae66;
}

.ca-empty {
  padding: 60px 20px;
  text-align: center;
  color: var(--muted);
}

.ca-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 12px;
  border-top: 1px solid var(--border);
  background: var(--sidebar-bg);
}
.ca-page-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  color: var(--text-2);
  cursor: pointer;
  transition: all 0.2s;
}
.ca-page-btn:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
}
.ca-page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.ca-page-info {
  font-size: 13px;
  color: var(--muted);
}
.ca-pagination select {
  padding: 4px 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text-2);
  font-size: 12px;
}

/* 右侧内容 */
.records-content {
  flex: 1;
  background: var(--card-bg);
  border-radius: var(--radius);
  border: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border);
  background: var(--sidebar-bg);
  gap: 12px;
}
.content-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
  flex-shrink: 0;
}
.content-time {
  font-size: 12px;
  color: var(--muted);
  font-family: var(--mono);
  flex-shrink: 0;
}

.ca-btn-export {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
  background: transparent;
  border: 1px solid var(--accent);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}
.ca-btn-export:hover {
  background: var(--accent);
  color: #fff;
}

.content-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.detail-section {
  margin-bottom: 16px;
}
.detail-section:last-child {
  margin-bottom: 0;
}
.detail-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--border);
}
.detail-meta {
  margin-bottom: 8px;
}
.meta-item {
  font-size: 12px;
  color: var(--text-2);
  font-family: var(--mono);
}
.detail-json {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 12px;
  font-size: 12px;
  font-family: var(--mono);
  color: var(--text);
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
.detail-remark {
  font-size: 13px;
  color: var(--text-2);
  margin: 0;
  line-height: 1.6;
}

.content-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--muted);
  font-size: 14px;
}

/* 工单信息 */
.order-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 16px;
  margin-bottom: 12px;
}
.order-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.order-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.order-value {
  font-size: 13px;
  color: var(--text);
}
.order-value.mono {
  font-family: var(--mono);
  font-size: 12px;
}

/* 优先级标签 */
.priority-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
}
.priority-tag.high {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}
.priority-tag.medium {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}
.priority-tag.low {
  background: rgba(8, 174, 102, 0.1);
  color: #08ae66;
}

/* 处理措施 */
.order-measures {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}
.order-measures .order-label {
  display: block;
  margin-bottom: 8px;
}
.measure-list {
  margin: 0;
  padding-left: 18px;
  list-style: decimal;
}
.measure-list li {
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.6;
  margin-bottom: 4px;
}
</style>
