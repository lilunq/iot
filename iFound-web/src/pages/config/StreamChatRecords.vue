<template>
  <div class="ca-panel">
    <div class="ca-toolbar">
      <div class="ca-filters">
        <input type="datetime-local" v-model="startTime" class="ca-input-date" />
        <span class="ca-date-sep">至</span>
        <input type="datetime-local" v-model="endTime" class="ca-input-date" />
        <input type="text" v-model="filterUserName" placeholder="用户名" class="ca-filter-input" />
        <input type="text" v-model="filterAgentId" placeholder="Agent ID" class="ca-filter-input" />
        <button class="ca-btn-ghost" @click="fetchRecords">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
            <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
          </svg>
          查询
        </button>
      </div>
      <div class="ca-toolbar-right">
        <div class="batch-delete-wrap">
          <button class="ca-btn-danger" @click="showBatchDelete = !showBatchDelete">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
              <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
            </svg>
            批量清理
          </button>
          <div v-if="showBatchDelete" class="batch-delete-dropdown">
            <button class="batch-item" @click="batchDelete('7d')">7天前</button>
            <button class="batch-item" @click="batchDelete('30d')">30天前</button>
            <button class="batch-item" @click="batchDelete('90d')">90天前</button>
            <div class="batch-custom">
              <input type="datetime-local" v-model="customDeleteTime" class="batch-input" />
              <button class="batch-item" @click="batchDeleteCustom">自定义</button>
            </div>
          </div>
        </div>
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
                <th>请求时间</th>
                <th>用户名</th>
                <th>Agent ID</th>
                <th>语音内容</th>
                <th>AI响应内容</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in recordList" :key="record.id"
                  :class="{ 'row-active': selectedRecord && selectedRecord.id === record.id }"
                  @click="selectRecord(record)">
                <td class="td-id">{{ record.id }}</td>
                <td>{{ formatTime(record.requestTime) }}</td>
                <td>{{ record.userName || '-' }}</td>
                <td>{{ record.agentId || '-' }}</td>
                <td class="td-input" :title="record.message">{{ record.message }}</td>
                <td class="td-response" :title="formatPayload(record.messageCompletePayload)">{{ formatPayload(record.messageCompletePayload) }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="recordList.length === 0" class="ca-empty">
            <p>暂无对话记录</p>
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

      <!-- 右侧内容 -->
      <div class="records-content">
        <div class="content-header">
          <span class="content-title">流式AI语音日志记录</span>
          <button class="ca-btn-export" @click="exportPdf" title="导出PDF">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><polyline points="9 15 12 12 15 15"/>
            </svg>
            导出PDF
          </button>
        </div>
        <div class="chat-body" v-if="recordList.length > 0">
          <template v-for="(record, idx) in recordList" :key="record.id">
            <!-- 用户消息（右侧） -->
            <div class="chat-row chat-user">
              <div class="chat-bubble-wrap">
                <div class="chat-time">{{ formatTime(record.requestTime) }}</div>
                <div class="chat-bubble chat-user-bubble">
                  <div class="chat-text">{{ record.message || '-' }}</div>
                </div>
              </div>
              <div class="chat-side">
                <div class="chat-avatar chat-user-avatar">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
                  </svg>
                </div>
                <span class="avatar-name">{{ record.userName || '用户' }}</span>
              </div>
            </div>
            <!-- AI消息（左侧） -->
            <div class="chat-row chat-ai">
              <div class="chat-side">
                <div class="chat-avatar">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                    <rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/>
                  </svg>
                </div>
                <span class="avatar-name">{{ record.agentId || 'AI' }}</span>
              </div>
              <div class="chat-bubble-wrap">
                <div class="chat-time">{{ formatTime(record.responseTime) }}</div>
                <div class="chat-bubble chat-ai-bubble">
                  <div class="chat-text">{{ extractPayloadOutput(record.messageCompletePayload) }}</div>
                </div>
              </div>
            </div>
            <div v-if="idx < recordList.length - 1" class="chat-divider"></div>
          </template>
        </div>
        <div v-else class="content-empty">
          <p>暂无对话记录</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, inject } from 'vue'
import { aiRequest } from '@/utils/request'
import html2canvas from 'html2canvas'
import jsPDF from 'jspdf'

const showToast = inject('showToast') || (() => {})

const recordList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const startTime = ref('')
const endTime = ref('')
const filterUserName = ref('')
const filterAgentId = ref('')
const showBatchDelete = ref(false)
const customDeleteTime = ref('')
const selectedRecord = ref(null)

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

onMounted(() => {
  fetchRecords()
})

async function fetchRecords() {
  pageNum.value = 1
  await loadRecords()
}

async function loadRecords() {
  try {
    const params = { page: pageNum.value, size: pageSize.value }
    if (startTime.value) params.start_time = startTime.value
    if (endTime.value) params.end_time = endTime.value
    if (filterUserName.value) params.user_name = filterUserName.value
    if (filterAgentId.value) params.agent_id = filterAgentId.value

    const res = await aiRequest.get('/stream-chat/records', { params })
    if (res && res.code === 200) {
      recordList.value = res.data.records || []
      total.value = res.data.total || 0
      if (recordList.value.length > 0) {
        selectedRecord.value = recordList.value[0]
      } else {
        selectedRecord.value = null
      }
    }
  } catch (e) {
    console.error('获取流式对话记录失败:', e)
  }
}

function goPage(page) {
  pageNum.value = page
  loadRecords()
}

function onPageSizeChange() {
  pageNum.value = 1
  loadRecords()
}

function selectRecord(record) {
  selectedRecord.value = record
}

async function batchDelete(beforeTime) {
  const labels = { '7d': '7天前', '30d': '30天前', '90d': '90天前' }
  if (!confirm(`确定删除${labels[beforeTime] || beforeTime}的所有记录吗？`)) return
  try {
    const res = await aiRequest.delete('/stream-chat/records/batch', { params: { before_time: beforeTime } })
    if (res && res.code === 200) {
      alert(`已删除 ${res.data} 条记录`)
      showBatchDelete.value = false
      fetchRecords()
    }
  } catch (e) {
    console.error('批量删除失败:', e)
  }
}

async function batchDeleteCustom() {
  if (!customDeleteTime.value) {
    alert('请选择时间')
    return
  }
  if (!confirm(`确定删除 ${customDeleteTime.value.replace('T', ' ')} 之前的所有记录吗？`)) return
  try {
    const res = await aiRequest.delete('/stream-chat/records/batch', { params: { before_time: customDeleteTime.value } })
    if (res && res.code === 200) {
      alert(`已删除 ${res.data} 条记录`)
      showBatchDelete.value = false
      customDeleteTime.value = ''
      fetchRecords()
    }
  } catch (e) {
    console.error('批量删除失败:', e)
  }
}

function formatTime(time) {
  if (!time) return '-'
  const str = typeof time === 'string' ? time : String(time)
  return str.replace('T', ' ').substring(0, 19)
}

function formatPayload(payload) {
  if (!payload) return '无响应内容'
  try {
    const parsed = JSON.parse(payload)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return payload
  }
}

function extractPayloadOutput(payload) {
  if (!payload) return '无响应内容'
  try {
    const parsed = JSON.parse(payload)
    return parsed.output || parsed.message || parsed.content || JSON.stringify(parsed)
  } catch {
    return payload
  }
}

async function exportPdf() {
  if (recordList.value.length === 0) return

  try {
    showToast('info', '正在生成PDF...')

    const container = document.createElement('div')
    container.style.cssText = 'font-family:system-ui,-apple-system,sans-serif;font-size:14px;color:#333;background:#fff;padding:24px;max-width:800px;line-height:1.6;'

    let html = `<h2 style="font-size:18px;margin:0 0 16px;padding-bottom:10px;border-bottom:2px solid #333;">流式AI语音日志记录</h2>`

    recordList.value.forEach((record, idx) => {
      html += `
        <div style="margin:12px 0;display:flex;justify-content:flex-end;">
          <div style="max-width:70%;">
            <div style="text-align:right;font-size:11px;color:#999;margin-bottom:4px;">${formatTime(record.requestTime)}</div>
            <div style="background:#3b82f6;color:#fff;padding:10px 14px;border-radius:12px;border-bottom-right-radius:4px;">
              <div style="font-size:13px;word-break:break-all;white-space:pre-wrap;">${record.message || '-'}</div>
            </div>
          </div>
          <div style="width:36px;height:36px;border-radius:50%;background:#6366f1;color:#fff;display:flex;align-items:center;justify-content:center;flex-shrink:0;margin-left:10px;font-size:10px;">${record.userName || '用户'}</div>
        </div>
        <div style="margin:12px 0;display:flex;justify-content:flex-start;">
          <div style="width:36px;height:36px;border-radius:50%;background:#3b82f6;color:#fff;display:flex;align-items:center;justify-content:center;flex-shrink:0;margin-right:10px;font-size:10px;">${record.agentId || 'AI'}</div>
          <div style="max-width:70%;">
            <div style="text-align:left;font-size:11px;color:#999;margin-bottom:4px;">${formatTime(record.responseTime)}</div>
            <div style="background:#f5f5f5;border:1px solid #ddd;padding:10px 14px;border-radius:12px;border-bottom-left-radius:4px;">
              <div style="font-size:13px;word-break:break-all;white-space:pre-wrap;">${extractPayloadOutput(record.messageCompletePayload)}</div>
            </div>
          </div>
        </div>
        ${idx < recordList.value.length - 1 ? '<div style="height:1px;background:#eee;margin:8px 0;"></div>' : ''}
      `
    })

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

    pdf.save('流式AI语音日志记录.pdf')
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

.ca-input-date {
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text);
  font-size: 13px;
  font-family: var(--mono);
}

.ca-filter-input {
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text);
  font-size: 13px;
  width: 120px;
}

.ca-date-sep {
  font-size: 12px;
  color: var(--muted);
}

.ca-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.batch-delete-wrap {
  position: relative;
}

.ca-btn-danger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--danger);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--danger);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.ca-btn-danger:hover {
  background: var(--danger);
  color: #fff;
}

.batch-delete-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  min-width: 180px;
  z-index: 50;
  padding: 4px;
}

.batch-item {
  display: block;
  width: 100%;
  padding: 8px 12px;
  border: none;
  background: transparent;
  text-align: left;
  font-size: 13px;
  color: var(--text);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.15s;
}
.batch-item:hover {
  background: var(--accent-soft);
  color: var(--accent);
}

.batch-custom {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-top: 1px solid var(--border);
  margin-top: 4px;
}

.batch-input {
  flex: 1;
  padding: 4px 6px;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: var(--surface);
  color: var(--text);
  font-size: 12px;
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

/* ---------- 左右布局 ---------- */
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
  scrollbar-width: none;
}
.records-table-inner::-webkit-scrollbar {
  display: none;
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
.ca-table tbody tr:last-child td {
  border-bottom: none;
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
  color: var(--muted);
}
.td-input {
  max-width: 200px;
  white-space: normal;
  word-break: break-all;
}

.td-response {
  max-width: 250px;
  white-space: normal;
  word-break: break-all;
  font-size: 12px;
  line-height: 1.5;
}

.ca-empty {
  padding: 40px;
  text-align: center;
  color: var(--muted);
  font-size: 14px;
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

/* ---------- 右侧内容区 ---------- */
.records-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--card-bg);
  border-radius: var(--radius);
  border: 1px solid var(--border);
  overflow: hidden;
}

.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  background: var(--sidebar-bg);
  gap: 12px;
}

.content-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
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

.chat-body {
  flex: 1;
  overflow-y: auto;
  scrollbar-width: none;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: var(--bg);
}
.chat-body::-webkit-scrollbar {
  display: none;
}

.chat-row {
  display: flex;
  width: 100%;
}

.chat-user {
  justify-content: flex-end;
}

.chat-ai {
  justify-content: flex-start;
}

.chat-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.chat-user-avatar {
  background: #6366f1;
}

.chat-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  margin-top: 4px;
}

.chat-ai .chat-side {
  margin-right: 10px;
}

.chat-user .chat-side {
  margin-left: 10px;
}

.avatar-name {
  font-size: 10px;
  font-weight: 600;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 60px;
  text-align: center;
}

.chat-bubble-wrap {
  max-width: 75%;
  display: flex;
  flex-direction: column;
}

.chat-user .chat-bubble-wrap {
  align-items: flex-end;
}

.chat-ai .chat-bubble-wrap {
  align-items: flex-start;
  margin-left: 10px;
}

.chat-time {
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 4px;
  font-family: var(--mono);
}

.chat-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  position: relative;
  line-height: 1.6;
}

.chat-user-bubble {
  background: var(--accent);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.chat-ai-bubble {
  background: var(--card-bg);
  color: var(--text);
  border: 1px solid var(--border);
  border-bottom-left-radius: 4px;
}

.chat-divider {
  height: 1px;
  background: var(--border);
  margin: 8px 0;
  opacity: 0.4;
}

.chat-text {
  font-size: 13px;
  word-break: break-all;
  white-space: pre-wrap;
}

.content-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--muted);
  font-size: 14px;
}
</style>