<template>
  <div class="ca-panel">
    <div class="ca-toolbar">
      <div class="ca-filters">
        <select v-model="filterCreatorAgent" @change="fetchList">
          <option value="">创建Agent</option>
          <option v-for="agent in agentList" :key="agent.agentId" :value="agent.agentName">
            {{ agent.agentName }}
          </option>
        </select>
        <select v-model="filterTargetAgent" @change="fetchList">
          <option value="">目标Agent</option>
          <option v-for="agent in agentList" :key="agent.agentId" :value="agent.agentId">
            {{ agent.agentName }}
          </option>
        </select>
        <select v-model="filterDispatched" @change="fetchList">
          <option value="all">派发</option>
          <option value="0">未派发</option>
          <option value="1">已派发</option>
          <option value="2">已取消</option>
        </select>
        <select v-model="filterDomain" @change="fetchList">
          <option value="all">业务域</option>
          <option value="设备">设备</option>
          <option value="能耗">能耗</option>
          <option value="环境">环境</option>
        </select>
        <select v-model="filterTaskType" @change="fetchList">
          <option value="all">任务类型</option>
          <option value="hint">提示</option>
          <option value="collaboration">协同方案</option>
        </select>
      </div>
      <div class="ca-toolbar-right">
        <button class="ca-btn-ghost" @click="fetchList" title="刷新">
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
                <th>分析区域</th>
                <th>等级</th>
                <th>问题描述</th>
                <th>目标Agent</th>
                <th>状态</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in taskList" :key="task.id"
                  :class="{ 'row-active': selectedTask && selectedTask.id === task.id, 'row-dispatched': task.dispatched === 1 }"
                  @click="selectTask(task)">
                <td class="td-id">{{ task.id?.substring(0, 8) }}</td>
                <td>{{ getZoneName(task.subareaCid) }}</td>
                <td>
                  <span class="ca-level-tag" :class="'level-' + task.level">{{ task.level }}</span>
                </td>
                <td class="td-desc" :title="task.problemDesc">{{ task.problemDesc }}</td>
                <td>{{ task.targetAgentName || task.targetAgentId || '-' }}</td>
                <td>
                  <span class="ca-status-tag" :class="task.dispatched === 1 ? 'dispatched' : 'pending'">
                    {{ task.dispatched === 1 ? '已派发' : task.dispatched === 2 ? '已取消' : '待派发' }}
                  </span>
                </td>
                <td class="td-time">{{ formatTime(task.createTime) }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="taskList.length === 0" class="ca-empty">
            <p>暂无任务数据</p>
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
          <span class="content-title">Agent分析协作详情</span>
          <div v-if="selectedTask" class="content-header-actions">
            <button class="ca-btn-export" @click.stop="exportPDF">导出PDF</button>
<!--            <button v-if="selectedTask.dispatched !== 1" class="ca-btn-dispatch" @click.stop="handleDispatch(selectedTask)">派发</button>-->
<!--            <button v-if="selectedTask.dispatched !== 1" class="ca-btn-cancel" @click.stop="cancelDispatch(selectedTask)">取消</button>-->
          </div>
        </div>
        <div class="content-body" v-if="selectedTask">
          <!-- 1. 开始分析 -->
          <div class="ca-flow-section">
            <h4 class="ca-flow-section-title">开始分析</h4>
            <div class="ca-detail-grid">
              <div class="ca-detail-item">
                <label>ID</label>
                <span>{{ selectedTask.id }}</span>
              </div>
              <div class="ca-detail-item">
                <label>分析区域</label>
                <span>{{ getZoneName(selectedTask.subareaCid) }}</span>
              </div>
              <div class="ca-detail-item">
                <label>分析Agent</label>
                <span>{{ selectedTask.creatorAgentName || '-' }}</span>
              </div>
              <div class="ca-detail-item">
                <label>时间</label>
                <span>{{ formatTime(selectedTask.createTime) }}</span>
              </div>
            </div>
          </div>

          <!-- 2. 实时数据采集 -->
          <div class="ca-flow-section" v-if="selectedTask.dataSnapshot">
            <h4 class="ca-flow-section-title">实时数据采集</h4>
            <div class="ca-detail-item full">
              <label>数据快照</label>
              <pre class="ca-snapshot">{{ formatDataSnapshot(selectedTask.dataSnapshot) }}</pre>
            </div>
          </div>

          <!-- 3. 异常发现 -->
          <div class="ca-flow-section">
            <h4 class="ca-flow-section-title">异常发现</h4>
            <div class="ca-detail-item full">
              <label>问题描述</label>
              <span>{{ selectedTask.problemDesc || '-' }}</span>
            </div>
            <div class="ca-detail-item full">
              <label>问题总结</label>
              <span>{{ selectedTask.problemSummary || '-' }}</span>
            </div>
          </div>

          <!-- 4. 诊断分析 -->
          <div class="ca-flow-section">
            <h4 class="ca-flow-section-title">诊断分析</h4>
            <div class="ca-detail-item full">
              <label>思考过程</label>
              <span>{{ selectedTask.thinkingOverview || '-' }}</span>
            </div>
            <div class="ca-detail-item full">
              <label>决策内容</label>
              <span v-if="decisionContentHtml" class="ca-md-content" v-html="decisionContentHtml"></span>
              <span v-else>-</span>
            </div>
            <div class="ca-detail-item full">
              <label>预期成效</label>
              <span>{{ selectedTask.expectedEffect || '-' }}</span>
            </div>
          </div>

          <!-- 5. Agent协作流程 -->
          <div class="ca-flow-section">
            <h4 class="ca-flow-section-title">Agent协作流程</h4>
            <div class="ca-steps-vertical">
              <!-- 创建 -->
              <div class="ca-step-v ca-step-active">
                <div class="ca-step-v-header">
                  <span class="ca-step-v-num">1</span>
                  <div class="ca-step-v-label">
                    <div>创建</div>
                    <div class="ca-step-v-time">{{ formatTime(selectedTask.createTime) }}</div>
                  </div>
                </div>
                <div class="ca-step-v-body">
                  <div class="ca-step-row"><label>创建Agent</label><span>{{ selectedTask.creatorAgentName || '-' }}</span></div>
                  <div class="ca-step-row"><label>业务域</label><span>{{ selectedTask.businessDomain }}</span></div>
                  <div class="ca-step-row"><label>任务类型</label><span>{{ selectedTask.taskType === 'hint' ? '提示' : selectedTask.taskType === 'collaboration' ? '协同方案' : '-' }}</span></div>
                  <div class="ca-step-row"><label>等级</label><span>{{ selectedTask.level }}</span></div>
                </div>
              </div>
              <!-- 派发 -->
              <div class="ca-step-v" :class="{ 'ca-step-active': selectedTask.dispatched === 1 || selectedTask.dispatched === 2 }">
                <div class="ca-step-v-header">
                  <span class="ca-step-v-num">2</span>
                  <div class="ca-step-v-label">
                    <div>派发</div>
                    <div class="ca-step-v-time">{{ formatTime(selectedTask.dispatchedTime) }}</div>
                  </div>
                </div>
                <div class="ca-step-v-body">
                  <div class="ca-step-row"><label>目标Agent</label><span>{{ selectedTask.targetAgentName || selectedTask.targetAgentId || '-' }}</span></div>
                  <div class="ca-step-row"  v-if=" selectedTask.dispatched != 1 "><label>派发状态</label><span>{{ selectedTask.dispatched === 1 ? '已派发' : selectedTask.dispatched === 2 ? '已取消' : '待派发' }}</span></div>
                  <div class="ca-step-row" v-if=" selectedTask.dispatched != 0 "><label>执行人</label><span>{{ selectedTask.dispatchedUser || '-' }}</span></div>
                  <div class="ca-step-row" v-if=" selectedTask.dispatched == 1 "><span>{{'已经派发给'+(selectedTask.targetAgentName || selectedTask.targetAgentId || '-')  }}</span></div>
                  
                  <!-- <div class="ca-step-row"><label>派发时间</label><span>{{ formatTime(selectedTask.dispatchedTime) }}</span></div> -->
                </div>
              </div>
              <!-- 采纳 -->
              <div class="ca-step-v" :class="{ 'ca-step-active': selectedTask.adoptStatus === '1' || selectedTask.adoptStatus === '2' }">
                <div class="ca-step-v-header">
                  <span class="ca-step-v-num">3</span>
                  <div class="ca-step-v-label">
                    <div>采纳</div>
                    <div class="ca-step-v-time">{{ formatTime(selectedTask.adoptTime) }}</div>
                  </div>
                </div>
                <div class="ca-step-v-body">
                  <div class="ca-step-row"><label>目标Agent</label><span>{{ selectedTask.targetAgentName || selectedTask.targetAgentId || '-' }}</span></div>
                  <div class="ca-step-row"><label>状态</label><span>{{ selectedTask.adoptStatus === '1' ? '已采纳' : selectedTask.adoptStatus === '2' ? '已驳回' : '未采纳' }}</span></div>
                  <div class="ca-step-row" v-if="selectedTask.adoptUser"><label>采纳人</label><span>{{ selectedTask.adoptUser }}</span></div>
                  <!-- <div class="ca-step-row" v-if="selectedTask.adoptTime"><label>采纳时间</label><span>{{ formatTime(selectedTask.adoptTime) }}</span></div> -->
                </div>
              </div>
              <!-- 执行结果 -->
              <div class="ca-step-v" :class="{ 'ca-step-active': selectedTask.completeTime }">
                <div class="ca-step-v-header">
                  <span class="ca-step-v-num">4</span>
                  <div class="ca-step-v-label">
                    <div>执行结果</div>
                    <div class="ca-step-v-time">{{ formatTime(selectedTask.completeTime) }}</div>
                  </div>
                </div>
                <div class="ca-step-v-body">
                  <div class="ca-step-row" v-if="selectedTask.resultExecuteStatus"><label>执行状态</label><span :style="{color: (selectedTask.resultExecuteStatus==1||selectedTask.resultExecuteStatus=='1')?'#52c41a':(selectedTask.resultExecuteStatus==2||selectedTask.resultExecuteStatus=='2')?'#ff4d4f':''}">{{ (selectedTask.resultExecuteStatus==1||selectedTask.resultExecuteStatus=='1')?'成功':(selectedTask.resultExecuteStatus==2||selectedTask.resultExecuteStatus=='2')?'失败':'未执行' }}</span></div>
                  <div class="ca-step-row" v-if="selectedTask.resultExecuteUser"><label>结果执行人</label><span>{{ selectedTask.resultExecuteUser }}</span></div>
                  <div class="ca-step-row" v-if="selectedTask.resultData"><label>结果数据</label><span class="ca-result-data">{{ formatResultData(selectedTask.resultData) }}</span></div>
                  <div class="ca-step-row" v-if="selectedTask.resultDesc"><label>结果描述</label><span>{{ selectedTask.resultDesc }}</span></div>
                  <div class="ca-step-row" v-if="selectedTask.completeTime"><label>完成时间</label><span>{{ formatTime(selectedTask.completeTime) }}</span></div>
                   <div class="ca-step-row" v-if="selectedTask.userFeedback"><label>用户反馈</label><span>{{ selectedTask.userFeedback }}</span></div>
                  <!-- AI诊断记录 -->
                  <template v-if="relatedDiagnosis">
                    <div class="ca-step-row"><label>诊断人</label><span>{{ relatedDiagnosis.diagnoseUser || '-' }}</span></div>
                    <div class="ca-step-row"><label>诊断时间</label><span>{{ formatTime(relatedDiagnosis.createTime) }}</span></div>
                    <div class="ca-step-row" v-if="relatedDiagnosis.diagnosisResult"><label>诊断结果</label><span class="ca-result-data">{{ formatResultData(relatedDiagnosis.diagnosisResult) }}</span></div>
                    <!-- 工单信息 -->
                    <div class="ca-order-card" v-if="orderInfo">
                      <div class="ca-order-title">工单信息</div>
                      <div class="ca-order-grid">
                        <div class="ca-order-item">
                          <span class="ca-order-label">工单ID</span>
                          <span class="ca-order-value mono">{{ orderInfo.orderId }}</span>
                        </div>
                        <div class="ca-order-item">
                          <span class="ca-order-label">优先级</span>
                          <span class="ca-order-value">
                            <span class="ca-priority-tag" :class="getPriorityClass(orderInfo.priority)">{{ orderInfo.priority }}</span>
                          </span>
                        </div>
                        <div class="ca-order-item">
                          <span class="ca-order-label">执行人</span>
                          <span class="ca-order-value">{{ orderInfo.executeUserName || '-' }}</span>
                        </div>
                        <div class="ca-order-item">
                          <span class="ca-order-label">到达时间</span>
                          <span class="ca-order-value">{{ orderInfo.arrivalTime || '-' }}</span>
                        </div>
                        <div class="ca-order-item">
                          <span class="ca-order-label">完成时间</span>
                          <span class="ca-order-value">{{ orderInfo.completionTime || '-' }}</span>
                        </div>
                        <div class="ca-order-item">
                          <span class="ca-order-label">所需工具</span>
                          <span class="ca-order-value">{{ orderInfo.tools || '-' }}</span>
                        </div>
                      </div>
                      <div class="ca-order-measures" v-if="orderInfo.measures && orderInfo.measures.length > 0">
                        <span class="ca-order-label">处理措施</span>
                        <ul class="ca-measure-list">
                          <li v-for="(m, idx) in orderInfo.measures" :key="idx">{{ m }}</li>
                        </ul>
                      </div>
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="content-empty">
          <p>选择左侧任务查看详情</p>
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
import { marked } from 'marked'

// 决策内容支持Markdown渲染（AI处置建议来自知识库，常带标题/列表/表格）
marked.use({ breaks: true, gfm: true })

function renderMarkdown(text) {
  if (!text) return ''
  try {
    return marked.parse(String(text))
  } catch {
    return String(text)
  }
}

// PDF导出容器内Markdown元素样式（白色背景）
const EXPORT_MD_STYLE = `<style>
.export-md{font-size:13px;color:#333;line-height:1.6;word-break:break-word;}
.export-md p{margin:4px 0;}
.export-md h1,.export-md h2,.export-md h3,.export-md h4,.export-md h5,.export-md h6{margin:10px 0 6px;color:#222;}
.export-md ul,.export-md ol{margin:6px 0;padding-left:22px;}
.export-md li{margin:2px 0;}
.export-md code{background:#f5f5f5;border:1px solid #e5e5e5;padding:1px 6px;border-radius:4px;}
.export-md pre{background:#f5f5f5;border:1px solid #ddd;border-radius:4px;padding:8px;white-space:pre-wrap;word-break:break-all;}
.export-md pre code{background:transparent;border:none;padding:0;}
.export-md table{border-collapse:collapse;margin:8px 0;}
.export-md th,.export-md td{border:1px solid #ddd;padding:4px 8px;}
.export-md blockquote{margin:6px 0;padding:4px 12px;border-left:3px solid #ccc;color:#555;}
.export-md a{color:#1a73e8;}
</style>`

const showToast = inject('showToast') || (() => {})

const taskList = ref([])
const agentList = ref([])
const filterCreatorAgent = ref('')
const filterTargetAgent = ref('')
const filterDispatched = ref('all')
const filterDomain = ref('all')
const filterTaskType = ref('all')
const selectedTask = ref(null)

// 决策内容Markdown渲染结果
const decisionContentHtml = computed(() => renderMarkdown(selectedTask.value?.decisionContent))
const relatedDiagnosis = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

// 从诊断结果中提取工单信息
const orderInfo = computed(() => {
  if (!relatedDiagnosis.value || !relatedDiagnosis.value.diagnosisResult) return null
  try {
    const parsed = JSON.parse(relatedDiagnosis.value.diagnosisResult)
    const measures = parsed.measures ? JSON.parse(parsed.measures) : []
    return {
      orderId: parsed.orderId || '-',
      priority: parsed.priority || '-',
      executeUserName: parsed.executeUserName || '-',
      arrivalTime: parsed.arrivalTime || '-',
      completionTime: parsed.completionTime || '-',
      tools: parsed.tools || '-',
      measures
    }
  } catch {
    return null
  }
})

function getPriorityClass(priority) {
  const map = { HIGH: 'high', MEDIUM: 'medium', LOW: 'low' }
  return map[priority] || ''
}

// 从缓存中获取区域名称
const zoneMap = computed(() => {
  try {
    const cached = localStorage.getItem('zoneListCache')
    if (cached) {
      const zones = JSON.parse(cached)
      return zones.reduce((map, z) => { map[z.cascadeId] = z.name; return map }, {})
    }
  } catch (e) {
    console.warn('读取区域缓存失败:', e)
  }
  return {}
})

function getZoneName(subareaCid) {
  return zoneMap.value[subareaCid] || subareaCid || '-'
}

function formatDataSnapshot(snapshot) {
  if (!snapshot) return '-'
  try {
    const parsed = JSON.parse(snapshot)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return snapshot
  }
}

function formatResultData(data) {
  if (!data) return '-'
  try {
    const parsed = JSON.parse(data)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return data
  }
}

async function fetchAgentList() {
  try {
    const res = await aiRequest.get('/agent/list')
    if (res && (res.code === '200' || res.code === 200)) {
      agentList.value = res.data || []
    }
  } catch (e) {
    console.error('获取Agent列表失败:', e)
  }
}

async function fetchList() {
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    if (filterCreatorAgent.value) {
      params.creatorAgentName = filterCreatorAgent.value
    }
    if (filterTargetAgent.value) {
      params.targetAgentId = filterTargetAgent.value
    }
    if (filterDispatched.value !== 'all') {
      params.dispatched = parseInt(filterDispatched.value)
    }
    if (filterDomain.value !== 'all') {
      params.businessDomain = filterDomain.value
    }
    if (filterTaskType.value !== 'all') {
      params.taskType = filterTaskType.value
    }
    const res = await aiRequest.get('/dispatch/list', { params })
    if (res && (res.code === '200' || res.code === 200)) {
      const data = res.data || {}
      taskList.value = data.records || []
      total.value = data.total || 0
      // 默认选中第一条
      if (taskList.value.length > 0 && !selectedTask.value) {
        selectedTask.value = taskList.value[0]
      }
    }
  } catch (e) {
    console.error('获取任务列表失败:', e)
  }
}

function goPage(page) {
  if (page < 1 || page > totalPages.value) return
  pageNum.value = page
  fetchList()
}

function onPageSizeChange() {
  pageNum.value = 1
  fetchList()
}

async function selectTask(task) {
  selectedTask.value = task
  relatedDiagnosis.value = null
  if (task.id&&task.targetAgentName.includes('运维')) {
    try {
      const res = await aiRequest.get(`/ai-diagnosis/by-source/${task.id}`)
      if (res && (res.code === '200' || res.code === 200) && res.data) {
        relatedDiagnosis.value = res.data
      }
    } catch (e) {
      console.warn('获取关联诊断记录失败:', e)
    }
  }
}

function formatTime(timeStr) {
  if (!timeStr) return '-'
  const s = typeof timeStr === 'string' ? timeStr : String(timeStr)
  if (s.length >= 19) return s.substring(0, 19).replace('T', ' ')
  return s
}

async function handleDispatch(task) {
  try {
    const res = await aiRequest.post(`/dispatch/dispatch/${task.id}`)
    if (res && (res.code === '200' || res.code === 200)) {
      task.dispatched = 1
      showToast('success', '派发成功')
    } else {
      showToast('error', '派发失败', res?.message || '')
    }
  } catch (e) {
    console.error('派发请求异常:', e)
    showToast('error', '派发异常', e.message || '')
  }
}

async function cancelDispatch(task) {
  try {
    const res = await aiRequest.post(`/dispatch/cancel/${task.id}`)
    if (res && (res.code === '200' || res.code === 200)) {
      task.dispatched = 2
      showToast('success', '取消成功')
    } else {
      showToast('error', '取消失败', res?.message || '')
    }
  } catch (e) {
    console.error('取消请求异常:', e)
    showToast('error', '取消异常', e.message || '')
  }
}

async function exportPDF() {
  if (!selectedTask.value) return
  try {
    showToast('info', '正在生成PDF...')

    // Build a dedicated export container with plain styles (no CSS variables)
    const task = selectedTask.value
    const diagnosis = relatedDiagnosis.value
    const order = orderInfo.value

    const container = document.createElement('div')
    container.style.cssText = 'font-family:system-ui,-apple-system,sans-serif;font-size:14px;color:#333;background:#fff;padding:24px;max-width:800px;line-height:1.6;'

    let html = `
      <h2 style="font-size:18px;margin:0 0 16px;padding-bottom:10px;border-bottom:2px solid #333;">Agent分析协作详情</h2>

      <h3 style="font-size:15px;margin:16px 0 8px;padding:6px 0;border-bottom:1px solid #ddd;">开始分析</h3>
      <table style="width:100%;border-collapse:collapse;font-size:13px;">
        <tr><td style="padding:4px 8px;color:#666;width:120px;">ID</td><td style="padding:4px 8px;">${task.id || '-'}</td><td style="padding:4px 8px;color:#666;width:120px;">分析区域</td><td style="padding:4px 8px;">${getZoneName(task.subareaCid)}</td></tr>
        <tr><td style="padding:4px 8px;color:#666;">分析Agent</td><td style="padding:4px 8px;">${task.creatorAgentName || '-'}</td><td style="padding:4px 8px;color:#666;">时间</td><td style="padding:4px 8px;">${formatTime(task.createTime)}</td></tr>
      </table>
    `

    if (task.dataSnapshot) {
      html += `
        <h3 style="font-size:15px;margin:16px 0 8px;padding:6px 0;border-bottom:1px solid #ddd;">实时数据采集</h3>
        <pre style="background:#f5f5f5;border:1px solid #ddd;border-radius:4px;padding:10px;font-size:12px;white-space:pre-wrap;word-break:break-all;">${formatDataSnapshot(task.dataSnapshot)}</pre>
      `
    }

    html += `
      <h3 style="font-size:15px;margin:16px 0 8px;padding:6px 0;border-bottom:1px solid #ddd;">异常发现</h3>
      <p style="margin:4px 0;"><strong>问题描述：</strong>${task.problemDesc || '-'}</p>
      <p style="margin:4px 0;"><strong>问题总结：</strong>${task.problemSummary || '-'}</p>

      <h3 style="font-size:15px;margin:16px 0 8px;padding:6px 0;border-bottom:1px solid #ddd;">诊断分析</h3>
      <p style="margin:4px 0;"><strong>思考过程：</strong>${task.thinkingOverview || '-'}</p>
      <p style="margin:4px 0;"><strong>决策内容：</strong></p><div class="export-md">${renderMarkdown(task.decisionContent) || '-'}</div>
      <p style="margin:4px 0;"><strong>预期成效：</strong>${task.expectedEffect || '-'}</p>

      <h3 style="font-size:15px;margin:16px 0 8px;padding:6px 0;border-bottom:1px solid #ddd;">Agent协作流程</h3>
    `

    // Step 1: 创建
    html += `
      <div style="margin:8px 0;border:1px solid #ddd;border-radius:4px;overflow:hidden;">
        <div style="background:#f0f0f0;padding:8px 12px;font-weight:600;font-size:13px;">1. 创建 - ${formatTime(task.createTime)}</div>
        <div style="padding:8px 12px;font-size:13px;">
          <p style="margin:2px 0;"><strong>创建Agent：</strong>${task.creatorAgentName || '-'}</p>
          <p style="margin:2px 0;"><strong>业务域：</strong>${task.businessDomain || '-'}</p>
          <p style="margin:2px 0;"><strong>任务类型：</strong>${task.taskType === 'hint' ? '提示' : task.taskType === 'collaboration' ? '协同方案' : '-'}</p>
          <p style="margin:2px 0;"><strong>等级：</strong>${task.level}</p>
        </div>
      </div>
    `

    // Step 2: 派发
    html += `
      <div style="margin:8px 0;border:1px solid #ddd;border-radius:4px;overflow:hidden;">
        <div style="background:#f0f0f0;padding:8px 12px;font-weight:600;font-size:13px;">2. 派发 - ${formatTime(task.dispatchedTime)}</div>
        <div style="padding:8px 12px;font-size:13px;">
          <p style="margin:2px 0;"><strong>目标Agent：</strong>${task.targetAgentName || task.targetAgentId || '-'}</p>
          <p style="margin:2px 0;"><strong>派发状态：</strong>${task.dispatched === 1 ? '已派发' : task.dispatched === 2 ? '已取消' : '待派发'}</p>
          ${task.dispatchedUser ? `<p style="margin:2px 0;"><strong>执行人：</strong>${task.dispatchedUser}</p>` : ''}
        </div>
      </div>
    `

    // Step 3: 采纳
    html += `
      <div style="margin:8px 0;border:1px solid #ddd;border-radius:4px;overflow:hidden;">
        <div style="background:#f0f0f0;padding:8px 12px;font-weight:600;font-size:13px;">3. 采纳 - ${formatTime(task.adoptTime)}</div>
        <div style="padding:8px 12px;font-size:13px;">
          <p style="margin:2px 0;"><strong>目标Agent：</strong>${task.targetAgentName || task.targetAgentId || '-'}</p>
          <p style="margin:2px 0;"><strong>状态：</strong>${task.adoptStatus === '1' ? '已采纳' : task.adoptStatus === '2' ? '已驳回' : '未采纳'}</p>
          ${task.adoptUser ? `<p style="margin:2px 0;"><strong>采纳人：</strong>${task.adoptUser}</p>` : ''}
        </div>
      </div>
    `

    // Step 4: 执行结果
    let execHtml = ''
    const execStatusText = (task.resultExecuteStatus==1||task.resultExecuteStatus=='1')?'成功':(task.resultExecuteStatus==2||task.resultExecuteStatus=='2')?'失败':'未执行'
    const execStatusColor = (task.resultExecuteStatus==1||task.resultExecuteStatus=='1')?'#52c41a':(task.resultExecuteStatus==2||task.resultExecuteStatus=='2')?'#ff4d4f':''
    execHtml += `<p style="margin:2px 0;"><strong>执行状态：</strong><span style="color:${execStatusColor}">${execStatusText}</span></p>`
    if (task.resultExecuteUser) execHtml += `<p style="margin:2px 0;"><strong>结果执行人：</strong>${task.resultExecuteUser}</p>`
    if (task.resultData) execHtml += `<p style="margin:2px 0;"><strong>结果数据：</strong><pre style="background:#f5f5f5;border:1px solid #ddd;padding:6px;font-size:12px;margin:4px 0;white-space:pre-wrap;word-break:break-all;">${formatResultData(task.resultData)}</pre></p>`
    if (task.resultDesc) execHtml += `<p style="margin:2px 0;"><strong>结果描述：</strong>${task.resultDesc}</p>`
    if (task.completeTime) execHtml += `<p style="margin:2px 0;"><strong>完成时间：</strong>${formatTime(task.completeTime)}</p>`

    html += `
      <div style="margin:8px 0;border:1px solid #ddd;border-radius:4px;overflow:hidden;">
        <div style="background:#f0f0f0;padding:8px 12px;font-weight:600;font-size:13px;">4. 执行结果 - ${formatTime(task.completeTime)}</div>
        <div style="padding:8px 12px;font-size:13px;">
          ${execHtml}
        </div>
      </div>
    `

    // 诊断记录
    if (diagnosis) {
      html += `
        <h3 style="font-size:15px;margin:16px 0 8px;padding:6px 0;border-bottom:1px solid #ddd;">关联诊断记录</h3>
        <p style="margin:2px 0;"><strong>诊断人：</strong>${diagnosis.diagnoseUser || '-'}</p>
        <p style="margin:2px 0;"><strong>诊断时间：</strong>${formatTime(diagnosis.createTime)}</p>
        ${diagnosis.diagnosisResult ? `<p style="margin:4px 0;"><strong>诊断结果：</strong><pre style="background:#f5f5f5;border:1px solid #ddd;padding:6px;font-size:12px;margin:4px 0;white-space:pre-wrap;word-break:break-all;">${formatResultData(diagnosis.diagnosisResult)}</pre></p>` : ''}
      `
    }

    // 工单信息
    if (order) {
      html += `
        <h3 style="font-size:15px;margin:16px 0 8px;padding:6px 0;border-bottom:1px solid #ddd;">工单信息</h3>
        <table style="width:100%;border-collapse:collapse;font-size:13px;">
          <tr><td style="padding:4px 8px;color:#666;width:120px;">工单ID</td><td style="padding:4px 8px;font-family:monospace;">${order.orderId}</td><td style="padding:4px 8px;color:#666;width:120px;">优先级</td><td style="padding:4px 8px;">${order.priority}</td></tr>
          <tr><td style="padding:4px 8px;color:#666;">执行人</td><td style="padding:4px 8px;">${order.executeUserName || '-'}</td><td style="padding:4px 8px;color:#666;">到达时间</td><td style="padding:4px 8px;">${order.arrivalTime || '-'}</td></tr>
          <tr><td style="padding:4px 8px;color:#666;">完成时间</td><td style="padding:4px 8px;">${order.completionTime || '-'}</td><td style="padding:4px 8px;color:#666;">所需工具</td><td style="padding:4px 8px;">${order.tools || '-'}</td></tr>
        </table>
        ${order.measures && order.measures.length > 0 ? `
          <p style="margin:8px 0 4px;"><strong>处理措施：</strong></p>
          <ol style="margin:0;padding-left:20px;">
            ${order.measures.map(m => `<li style="margin:2px 0;">${m}</li>`).join('')}
          </ol>
        ` : ''}
      `
    }

    container.innerHTML = EXPORT_MD_STYLE + html
    container.style.position = 'fixed'
    container.style.left = '-9999px'
    container.style.top = '0'
    document.body.appendChild(container)

    const canvas = await html2canvas(container, {
      scale: 2,
      useCORS: true,
      backgroundColor: '#ffffff',
      logging: false,
    })

    document.body.removeChild(container)

    const imgData = canvas.toDataURL('image/png')
    const imgWidth = 210
    const pageHeight = 295
    const imgHeight = (canvas.height * imgWidth) / canvas.width
    let heightLeft = imgHeight

    const pdf = new jsPDF('p', 'mm', 'a4')
    let position = 0

    pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
    heightLeft -= pageHeight

    while (heightLeft > 0) {
      position = heightLeft - imgHeight
      pdf.addPage()
      pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
      heightLeft -= pageHeight
    }

    const fileName = `Agent协作详情_${task.id || 'export'}.pdf`
    pdf.save(fileName)
    showToast('success', '导出成功')
  } catch (e) {
    console.error('导出PDF失败:', e)
    showToast('error', '导出失败', e.message || '')
  }
}

onMounted(() => {
  fetchAgentList()
  fetchList()
})
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
  scrollbar-width: none; /* Firefox */
}
.records-table-inner::-webkit-scrollbar {
  display: none; /* Chrome/Safari/Edge */
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
.ca-table tbody tr.row-dispatched {
  opacity: 0.6;
}

.td-id {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--muted);
}
.td-desc {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.td-time {
  font-family: var(--mono);
  font-size: 11px;
  white-space: nowrap;
}

.ca-level-tag {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.ca-level-tag.level-高 { background: rgba(239,68,68,0.15); color: #ef4444; }
.ca-level-tag.level-中 { background: rgba(245,158,11,0.15); color: #f59e0b; }
.ca-level-tag.level-低 { background: rgba(34,197,94,0.15); color: #08ae66; }
.ca-status-tag {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.ca-status-tag.pending { background: rgba(245,158,11,0.15); color: #f59e0b; }
.ca-status-tag.dispatched { background: rgba(34,197,94,0.15); color: #08ae66; }

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

/* ---------- 右侧详情区 ---------- */
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
}

.content-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.content-header-actions {
  display: flex;
  gap: 8px;
}

.ca-btn-dispatch {
  padding: 4px 12px;
  border: none;
  border-radius: 6px;
  background: var(--accent);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.ca-btn-dispatch:hover { opacity: 0.9; }

.ca-btn-cancel {
  padding: 4px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: transparent;
  color: var(--text-2);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}
.ca-btn-cancel:hover {
  border-color: #ef4444;
  color: #ef4444;
}

.ca-btn-export {
  padding: 4px 12px;
  border: 1px solid var(--accent);
  border-radius: 6px;
  background: transparent;
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.ca-btn-export:hover {
  background: var(--accent);
  color: #fff;
}

.content-body {
  flex: 1;
  overflow-y: auto;
  scrollbar-width: none; /* Firefox */
  padding: 16px;
}
.content-body::-webkit-scrollbar {
  display: none; /* Chrome/Safari/Edge */
}

.content-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--muted);
  font-size: 14px;
}

/* ---------- 流程分区样式 ---------- */
.ca-flow-section {
  margin-bottom: 20px;
}

.ca-flow-section:first-child {
  margin-top: 0;
}

.ca-flow-section-title {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
  border-bottom: 1px solid var(--border);
  padding-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.ca-flow-section-title::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
}

.ca-steps-vertical {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.ca-step-v {
  display: flex;
  gap: 12px;
  position: relative;
  padding-bottom: 16px;
}
.ca-step-v:last-child {
  padding-bottom: 0;
}
.ca-step-v::after {
  content: '';
  position: absolute;
  left: 11px;
  top: 0px;
  bottom: 0;
  width: 1px;
  background: var(--border);
}

.ca-step-v-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  min-width: 70px;
  padding-top: 2px;
  position: relative;
  z-index: 1;
}

.ca-step-v-num {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 1px solid var(--accent);
  background: transparent;
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ca-step-v-label {
  width: 100px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ca-step-v-time {
  font-size: 11px;
  font-weight: 400;
}

.ca-step-v-body {
  flex: 1;
  background: rgba(255,255,255,0.02);
  border-left: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 12px;
}

.ca-step-active .ca-step-v-body {
  background: rgba(64, 158, 255, 0.1);
  border-left-color: var(--accent);
}

.ca-step-active .ca-step-v-num {
  background: var(--accent);
  color:white;
}

.ca-step-row {
  display: flex;
  align-items: center;
  padding: 4px 0;
  font-size: 12px;
  border-bottom: 1px dashed rgba(255,255,255,0.04);
}
.ca-step-row:last-child {
  border-bottom: none;
}
.ca-step-row label {
  font-size: 11px;
  flex-shrink: 0;
  min-width: 60px;
}
.ca-step-row span {
  color: var(--text);
  text-align: right;
  word-break: break-all;
}

.ca-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.ca-detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ca-detail-item.full {
  grid-column: 1 / -1;
}
.ca-detail-item label {
  font-size: 11px;
  font-weight: 600;
  color: var(--muted);
  letter-spacing: 0.5px;
}
.ca-detail-item span {
  font-size: 13px;
  color: var(--text);
  line-height: 1.5;
}
.ca-detail-item.full span {
  background: var(--surface);
  padding: 8px 12px;
  border-radius: 6px;
  border: 1px solid var(--border);
}

/* 决策内容Markdown样式 */
.ca-md-content {
  display: block;
  line-height: 1.6;
  word-break: break-word;
}

.ca-md-content :deep(p) {
  margin: 4px 0;
}

.ca-md-content :deep(h1),
.ca-md-content :deep(h2),
.ca-md-content :deep(h3),
.ca-md-content :deep(h4),
.ca-md-content :deep(h5),
.ca-md-content :deep(h6) {
  margin: 10px 0 6px;
  color: var(--text);
}

.ca-md-content :deep(ul),
.ca-md-content :deep(ol) {
  margin: 6px 0;
  padding-left: 22px;
}

.ca-md-content :deep(li) {
  margin: 2px 0;
}

.ca-md-content :deep(code) {
  background: var(--sidebar-bg);
  border: 1px solid var(--border);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 0.9em;
}

.ca-md-content :deep(pre) {
  background: var(--sidebar-bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 10px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.ca-md-content :deep(pre code) {
  background: transparent;
  border: none;
  padding: 0;
}

.ca-md-content :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
}

.ca-md-content :deep(th),
.ca-md-content :deep(td) {
  border: 1px solid var(--border);
  padding: 4px 10px;
}

.ca-md-content :deep(blockquote) {
  margin: 6px 0;
  padding: 4px 12px;
  border-left: 3px solid var(--accent);
  color: var(--text-2);
}

.ca-md-content :deep(a) {
  color: var(--accent);
}

.ca-snapshot {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 12px;
  font-size: 11px;
  color: var(--text-2);
  font-family: var(--mono);
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.ca-result-data {
  background: var(--surface);
  padding: 8px 12px;
  border-radius: 6px;
  border: 1px solid var(--border);
  font-size: 12px;
  color: var(--text-2);
  font-family: var(--mono);
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  display: block;
  width: 100%;
  box-sizing: border-box;
}

/* 工单信息卡片 */
.ca-order-card {
  margin-top: 8px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 12px;
}
.ca-order-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--border);
}
.ca-order-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 16px;
  margin-bottom: 10px;
}
.ca-order-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ca-order-label {
  font-size: 11px;
  color: var(--muted);
  font-weight: 600;
}
.ca-order-value {
  font-size: 12px;
  color: var(--text);
}
.ca-order-value.mono {
  font-family: var(--mono);
  font-size: 11px;
}
.ca-priority-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.ca-priority-tag.high {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}
.ca-priority-tag.medium {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}
.ca-priority-tag.low {
  background: rgba(8, 174, 102, 0.1);
  color: #08ae66;
}
.ca-order-measures {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border);
}
.ca-measure-list {
  margin: 6px 0 0;
  padding-left: 18px;
  list-style: decimal;
}
.ca-measure-list li {
  font-size: 12px;
  color: var(--text-2);
  line-height: 1.6;
  margin-bottom: 4px;
}
</style>