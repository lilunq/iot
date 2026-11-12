<template>
  <div v-if="visible" class="td-overlay" @click.self="$emit('close')">
    <div class="td-modal">
      <div class="td-header">
        <div class="td-header-bg"></div>
        <div class="td-header-content">
          <div class="td-title-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="28" height="28">
              <path d="M12 2L2 7l10 5 10-5-10-5z"/>
              <path d="M2 17l10 5 10-5"/>
              <path d="M2 12l10 5 10-5"/>
            </svg>
          </div>
          <h3 class="td-title">
            <span class="td-title-text">Agent分析协作详情</span>
          </h3>
        </div>
        <div class="td-header-actions">
          <div class="td-status-line"></div>
          <button class="td-btn td-btn-export" @click="exportPDF">导出PDF</button>
          <button class="td-close" @click="$emit('close')">&times;</button>
        </div>
      </div>
      <div class="td-body" v-if="task" ref="tdBodyRef">
        <!-- 三大块水平排列 -->
        <div class="td-columns">
          <!-- 1. 开始分析（包含实时数据采集、异常发现） -->
          <div class="td-section">
            <h4 class="td-section-title">
              <span class="td-section-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="16" height="16">
                  <circle cx="12" cy="12" r="10"/>
                  <path d="M12 8v4l3 3"/>
                </svg>
              </span>
              开始分析
              <span class="td-section-line"></span>
            </h4>
            <div class="td-section-content">
              <div class="td-info-grid">
                <!-- <div class="td-info-item"><label>ID</label><span>{{ task.id }}</span></div> -->
                <!-- <div class="td-info-item"><label>分析区域</label><span>{{ getZoneName(task.subareaCid) }}</span></div> -->
                <div class="td-info-item"><label>Agent</label><span>{{ task.creatorAgentName || '-' }}</span></div>
                <div class="td-info-item"><label>时间</label><span>{{ formatTime(task.createTime) }}</span></div>
              </div>
              <div class="td-info-item full">
                <label>问题：</label>
                <span>{{ task.problemDesc || '-' }}</span>
              </div>
              <div class="td-info-item full">
                <label>总结：</label>
                <span>{{ task.problemSummary || '-' }}</span>
              </div>
              <div class="td-info-item full" v-if="task.dataSnapshot">
                <label>数据快照：</label>
                <pre class="td-snapshot">{{ formatDataSnapshot(task.dataSnapshot) }}</pre>
              </div>
            </div>
          </div>

          <!-- 2. AI诊断 -->
          <div class="td-section">
            <h4 class="td-section-title">
              <span class="td-section-icon td-icon-ai">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="16" height="16">
                  <path d="M12 2a4 4 0 0 1 4 4v2a4 4 0 0 1-8 0V6a4 4 0 0 1 4-4z"/>
                  <path d="M16 14v2a4 4 0 0 1-8 0v-2"/>
                  <path d="M12 18v4"/>
                  <path d="M8 22h8"/>
                </svg>
              </span>
              AI诊断
              <span class="td-section-line"></span>
            </h4>
            <div class="td-section-content">
              <div class="td-info-item full">
                <label>思考过程：</label>
                <span>{{ task.thinkingOverview || '-' }}</span>
              </div>
              <div class="td-info-item full">
                <label>决策建议：</label>
                <span v-if="decisionContentHtml" class="td-md-content" v-html="decisionContentHtml"></span>
                <span v-else>-</span>
              </div>
              <div class="td-info-item full">
                <label>预期成效：</label>
                <span>{{ task.expectedEffect || '-' }}</span>
              </div>
            </div>
          </div>

          <!-- 3. Agent协作 -->
          <div class="td-section">
            <h4 class="td-section-title">
              <span class="td-section-icon td-icon-collab">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="16" height="16">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                  <circle cx="9" cy="7" r="4"/>
                  <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                  <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                </svg>
              </span>
              Agent协作
              <span class="td-section-line"></span>
            </h4>
            <div class="td-section-content">
              <div class="td-steps">
            <!-- 1. 创建 -->
            <div class="td-step td-step-active">
              <div class="td-step-num">1</div>
              <div class="td-step-content">
                <div class="td-step-label">创建</div>
                <div class="td-step-time">{{ formatTime(task.createTime) }}</div>
                <div class="td-step-details">
                  <span>创建Agent: {{ task.creatorAgentName || '-' }}</span>
                  <span>业务域: {{ task.businessDomain }}</span>
                  <span>任务类型: {{ task.taskType === 'hint' ? '提示' : task.taskType === 'collaboration' ? '协同方案' : '-' }}</span>
                  <span>等级: {{ task.level }}</span>
                </div>
              </div>
            </div>

            <!-- 2. 派发 -->
            <div class="td-step" :class="{ 'td-step-active': task.dispatched === 1 || task.dispatched === 2 }">
              <div class="td-step-num">2</div>
              <div class="td-step-content">
                <div class="td-step-label">派发</div>
                <div class="td-step-time">{{ formatTime(task.dispatchedTime) }}</div>
                <div class="td-step-details">
                  <span>目标Agent: {{ task.targetAgentName || task.targetAgentId || '-' }}</span>
                  <span v-if="task.dispatched === 1">已派发给 {{ task.targetAgentName || task.targetAgentId }}</span>
                  <span v-else>派发状态: {{ task.dispatched === 1 ? '已派发' : task.dispatched === 2 ? '已取消' : '待派发' }}</span>
                  <span v-if="task.dispatched !== 0">执行人: {{ task.dispatchedUser || '-' }}</span>
                </div>
              </div>
            </div>

            <!-- 3. 采纳 -->
            <div class="td-step" :class="{ 'td-step-active': task.adoptStatus === '1' || task.adoptStatus === '2' }">
              <div class="td-step-num">3</div>
              <div class="td-step-content">
                <div class="td-step-label">采纳</div>
                <div class="td-step-time">{{ formatTime(task.adoptTime) }}</div>
                <div class="td-step-details">
                  <span> {{ task.targetAgentName || task.targetAgentId || '-' }}</span>
                  <span>状态: {{ task.adoptStatus === '1' ? '已采纳' : task.adoptStatus === '2' ? '已驳回' : '未采纳' }}</span>
                  <span v-if="task.adoptUser">采纳人: {{ task.adoptUser }}</span>
                </div>
              </div>
            </div>

            <!-- 4. 执行结果 -->
            <div class="td-step" :class="{ 'td-step-active': task.completeTime }">
              <div class="td-step-num">4</div>
              <div class="td-step-content">
                <div class="td-step-label">执行结果</div>
                <div class="td-step-time">{{ formatTime(task.completeTime) }}</div>
                <div class="td-step-details td-step-details-col">
                  <span v-if="task.resultExecuteStatus">执行状态: <span :style="{color: (task.resultExecuteStatus==1||task.resultExecuteStatus=='1')?'#52c41a':(task.resultExecuteStatus==2||task.resultExecuteStatus=='2')?'#ff4d4f':''}">{{ (task.resultExecuteStatus==1||task.resultExecuteStatus=='1')?'成功':(task.resultExecuteStatus==2||task.resultExecuteStatus=='2')?'失败':'未执行' }}</span></span>
                  <div v-else class="td-feedback-form">
                    <input
                      v-model="feedbackText"
                      class="td-feedback-input"
                      type="text"
                      placeholder="回填结果"
                      maxlength="200"
                      @keyup.enter="submitFeedback"
                    />
                    <select v-model="feedbackStatus" class="td-feedback-select" title="处理结果">
                      <option :value="1">成功</option>
                      <option :value="2">失败</option>
                    </select>
                    <button class="td-feedback-btn" :disabled="feedbackSubmitting" @click="submitFeedback">
                      {{ feedbackSubmitting ? '提交中…' : '提交' }}
                    </button>
                    <span v-if="feedbackError" class="td-feedback-error">{{ feedbackError }}</span>
                  </div>
                  <span v-if="task.resultDesc">结果描述: {{ task.resultDesc }}</span>
                  <span v-if="task.userFeedback">回填: {{ task.userFeedback }}</span>
                  <span v-if="task.resultExecuteUser">结果执行人: {{ task.resultExecuteUser }}</span>
                  <span v-if="task.resultData" class="td-result-data">结果数据: {{ formatResultData(task.resultData) }}</span>
                </div>
              </div>
            </div>
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import html2canvas from 'html2canvas'
import jsPDF from 'jspdf'
import { marked } from 'marked'
import { aiRequest } from '@/utils/request'
import { useUserStore } from '@/stores/user'

// 决策建议支持Markdown渲染（AI处置建议来自知识库，常带标题/列表/表格）
marked.use({ breaks: true, gfm: true })

function renderMarkdown(text) {
  if (!text) return ''
  try {
    return marked.parse(String(text))
  } catch {
    return String(text)
  }
}

// PDF导出容器内Markdown元素样式（暗色背景）
const EXPORT_MD_STYLE = `<style>
.export-md{font-size:14px;color:#fff;line-height:1.6;word-break:break-word;}
.export-md p{margin:4px 0;}
.export-md h1,.export-md h2,.export-md h3,.export-md h4,.export-md h5,.export-md h6{margin:10px 0 6px;color:#fff;}
.export-md ul,.export-md ol{margin:6px 0;padding-left:22px;}
.export-md li{margin:2px 0;}
.export-md code{background:rgba(0,0,0,0.3);padding:1px 6px;border-radius:4px;}
.export-md pre{background:rgba(0,0,0,0.3);border:1px solid rgba(255,255,255,0.15);border-radius:4px;padding:8px;white-space:pre-wrap;word-break:break-all;}
.export-md pre code{background:transparent;padding:0;}
.export-md table{border-collapse:collapse;margin:8px 0;}
.export-md th,.export-md td{border:1px solid rgba(255,255,255,0.3);padding:4px 8px;}
.export-md blockquote{margin:6px 0;padding:4px 12px;border-left:3px solid rgba(255,255,255,0.4);color:rgba(255,255,255,0.8);}
.export-md a{color:#60a5fa;}
</style>`

const props = defineProps({
  visible: { type: Boolean, default: false },
  task: { type: Object, default: null }
})

const emit = defineEmits(['close'])

const userStore = useUserStore()

// 执行结果回填表单（仅未完结任务显示，回填后任务关闭）
const feedbackText = ref('')
const feedbackStatus = ref(1) // 1-成功 2-失败
const feedbackSubmitting = ref(false)
const feedbackError = ref('')

// 切换任务时重置回填表单
watch(() => props.task?.id, () => {
  feedbackText.value = ''
  feedbackStatus.value = 1
  feedbackSubmitting.value = false
  feedbackError.value = ''
})

async function submitFeedback() {
  if (!props.task?.id || feedbackSubmitting.value) return
  const text = feedbackText.value.trim()
  if (!text) {
    feedbackError.value = '请填写回填结果'
    return
  }
  feedbackError.value = ''
  feedbackSubmitting.value = true
  try {
    const res = await aiRequest.post('/dispatch/user-feedback', {
      id: props.task.id,
      username: userStore.user_name,
      userFeedback: text,
      resultExecuteStatus: feedbackStatus.value
    })
    if (res && (res.code === '200' || res.Status === 'Ok')) {
      // 回写本地任务对象，界面立即切换为已完结展示（下次列表刷新会以服务端数据为准）
      props.task.resultExecuteStatus = feedbackStatus.value
      props.task.resultExecuteUser = userStore.user_name
      props.task.userFeedback = text
      props.task.completeTime = new Date().toISOString()
      feedbackText.value = ''
    } else {
      feedbackError.value = (res && res.msg) || '回填失败'
    }
  } catch (e) {
    console.error('执行结果回填失败:', e)
    feedbackError.value = '回填失败，请稍后重试'
  } finally {
    feedbackSubmitting.value = false
  }
}

// 决策建议Markdown渲染结果
const decisionContentHtml = computed(() => renderMarkdown(props.task?.decisionContent))

function formatTime(time) {
  if (!time) return '-'
  try {
    const date = new Date(time)
    if (isNaN(date.getTime())) return time
    return date.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } catch {
    return time
  }
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

function getZoneName(cid) {
  if (!cid) return '-'
  return zoneMap.value[cid] || cid
}

function formatDataSnapshot(data) {
  if (typeof data === 'string') {
    try {
      return JSON.stringify(JSON.parse(data), null, 2)
    } catch {
      return data
    }
  }
  return JSON.stringify(data, null, 2)
}

function formatResultData(data) {
  if (!data) return '-'
  if (typeof data === 'string') {
    try {
      return JSON.stringify(JSON.parse(data), null, 2)
    } catch {
      return data
    }
  }
  return JSON.stringify(data, null, 2)
}

async function exportPDF() {
  if (!props.task) return
  try {
    const task = props.task

    // 创建导出容器
    const container = document.createElement('div')
    container.style.cssText = 'font-family:system-ui,-apple-system,sans-serif;font-size:14px;color:#fff;background:rgb(0, 66, 126);padding:24px;max-width:1200px;line-height:1.6;'

    let html = `<h2 style="font-size:20px;margin:0 0 20px;padding-bottom:10px;border-bottom:2px solid rgba(255,255,255,0.3);">Agent分析协作详情</h2>`

    // 1. 开始分析
    html += `<h3 style="font-size:16px;margin:16px 0 10px;padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.2);">开始分析</h3>`
    html += `<table style="width:100%;border-collapse:collapse;font-size:14px;">
      <tr><td style="padding:6px 10px;color:rgba(255,255,255,0.7);width:100px;">ID</td><td style="padding:6px 10px;">${task.id || '-'}</td><td style="padding:6px 10px;color:rgba(255,255,255,0.7);width:100px;">分析区域</td><td style="padding:6px 10px;">${getZoneName(task.subareaCid)}</td></tr>
      <tr><td style="padding:6px 10px;color:rgba(255,255,255,0.7);">分析Agent</td><td style="padding:6px 10px;">${task.creatorAgentName || '-'}</td><td style="padding:6px 10px;color:rgba(255,255,255,0.7);">时间</td><td style="padding:6px 10px;">${formatTime(task.createTime)}</td></tr>
    </table>`

    if (task.dataSnapshot) {
      html += `<h4 style="font-size:14px;margin:12px 0 6px;color:rgba(255,255,255,0.7);">数据快照</h4>
        <pre style="background:rgba(0,0,0,0.3);border:1px solid rgba(255,255,255,0.15);border-radius:4px;padding:10px;font-size:14px;white-space:pre-wrap;word-break:break-all;">${formatDataSnapshot(task.dataSnapshot)}</pre>`
    }

    html += `<h4 style="font-size:14px;margin:12px 0 6px;color:rgba(255,255,255,0.7);">问题描述</h4><p style="margin:4px 0;">${task.problemDesc || '-'}</p>
      <h4 style="font-size:14px;margin:12px 0 6px;color:rgba(255,255,255,0.7);">问题总结</h4><p style="margin:4px 0;">${task.problemSummary || '-'}</p>`

    // 2. AI诊断
    html += `<h3 style="font-size:16px;margin:20px 0 10px;padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.2);">AI诊断</h3>`
    html += `<h4 style="font-size:14px;margin:10px 0 6px;color:rgba(255,255,255,0.7);">思考过程</h4><p style="margin:4px 0;">${task.thinkingOverview || '-'}</p>
      <h4 style="font-size:14px;margin:10px 0 6px;color:rgba(255,255,255,0.7);">决策建议</h4><div class="export-md">${renderMarkdown(task.decisionContent) || '-'}</div>
      <h4 style="font-size:14px;margin:10px 0 6px;color:rgba(255,255,255,0.7);">预期成效</h4><p style="margin:4px 0;">${task.expectedEffect || '-'}</p>`

    // 3. Agent协作
    html += `<h3 style="font-size:16px;margin:20px 0 10px;padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.2);">Agent协作</h3>`

    // 创建
    html += `<div style="margin:10px 0;border:1px solid rgba(255,255,255,0.2);border-radius:4px;overflow:hidden;">
      <div style="background:rgba(255,255,255,0.1);padding:8px 12px;font-weight:600;font-size:14px;">1. 创建 - ${formatTime(task.createTime)}</div>
      <div style="padding:8px 12px;font-size:14px;">
        <p style="margin:4px 0;"><strong>创建Agent：</strong>${task.creatorAgentName || '-'}</p>
        <p style="margin:4px 0;"><strong>业务域：</strong>${task.businessDomain || '-'}</p>
        <p style="margin:4px 0;"><strong>任务类型：</strong>${task.taskType === 'hint' ? '提示' : task.taskType === 'collaboration' ? '协同方案' : '-'}</p>
        <p style="margin:4px 0;"><strong>等级：</strong>${task.level}</p>
      </div>
    </div>`

    // 派发
    html += `<div style="margin:10px 0;border:1px solid rgba(255,255,255,0.2);border-radius:4px;overflow:hidden;">
      <div style="background:rgba(255,255,255,0.1);padding:8px 12px;font-weight:600;font-size:14px;">2. 派发 - ${formatTime(task.dispatchedTime)}</div>
      <div style="padding:8px 12px;font-size:14px;">
        <p style="margin:4px 0;"><strong>目标Agent：</strong>${task.targetAgentName || task.targetAgentId || '-'}</p>
        <p style="margin:4px 0;"><strong>派发状态：</strong>${task.dispatched === 1 ? '已派发' : task.dispatched === 2 ? '已取消' : '待派发'}</p>
        ${task.dispatchedUser ? `<p style="margin:4px 0;"><strong>执行人：</strong>${task.dispatchedUser}</p>` : ''}
      </div>
    </div>`

    // 采纳
    html += `<div style="margin:10px 0;border:1px solid rgba(255,255,255,0.2);border-radius:4px;overflow:hidden;">
      <div style="background:rgba(255,255,255,0.1);padding:8px 12px;font-weight:600;font-size:14px;">3. 采纳 - ${formatTime(task.adoptTime)}</div>
      <div style="padding:8px 12px;font-size:14px;">
        <p style="margin:4px 0;"><strong>目标Agent：</strong>${task.targetAgentName || task.targetAgentId || '-'}</p>
        <p style="margin:4px 0;"><strong>状态：</strong>${task.adoptStatus === '1' ? '已采纳' : task.adoptStatus === '2' ? '已驳回' : '未采纳'}</p>
        ${task.adoptUser ? `<p style="margin:4px 0;"><strong>采纳人：</strong>${task.adoptUser}</p>` : ''}
      </div>
    </div>`

    // 执行结果
    let execHtml = ''
    if (task.resultExecuteStatus) execHtml += `<p style="margin:4px 0;"><strong>执行状态：</strong>${task.resultExecuteStatus == 1 ? '已完成' : task.resultExecuteStatus}</p>`
    if (task.resultDesc) execHtml += `<p style="margin:4px 0;"><strong>结果描述：</strong>${task.resultDesc}</p>`
    if (task.resultExecuteUser) execHtml += `<p style="margin:4px 0;"><strong>结果执行人：</strong>${task.resultExecuteUser}</p>`
    if (task.resultData) execHtml += `<p style="margin:4px 0;"><strong>结果数据：</strong><pre style="background:rgba(0,0,0,0.3);border:1px solid rgba(255,255,255,0.15);padding:8px;font-size:14px;margin:4px 0;white-space:pre-wrap;word-break:break-all;">${formatResultData(task.resultData)}</pre></p>`

    html += `<div style="margin:10px 0;border:1px solid rgba(255,255,255,0.2);border-radius:4px;overflow:hidden;">
      <div style="background:rgba(255,255,255,0.1);padding:8px 12px;font-weight:600;font-size:14px;">4. 执行结果 - ${formatTime(task.completeTime)}</div>
      <div style="padding:8px 12px;font-size:14px;">${execHtml}</div>
    </div>`

    container.innerHTML = EXPORT_MD_STYLE + html
    container.style.position = 'fixed'
    container.style.left = '-9999px'
    container.style.top = '0'
    document.body.appendChild(container)

    await new Promise(resolve => setTimeout(resolve, 100))

    const canvas = await html2canvas(container, {
      scale: 2,
      useCORS: true,
      backgroundColor: 'rgb(0, 66, 126)',
      logging: false,
    })

    document.body.removeChild(container)

    const imgData = canvas.toDataURL('image/png')
    const pdf = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' })
    const pdfWidth = pdf.internal.pageSize.getWidth()
    const pdfHeight = pdf.internal.pageSize.getHeight()
    const imgWidth = pdfWidth
    const imgHeight = (canvas.height * imgWidth) / canvas.width
    let heightLeft = imgHeight
    let position = 0

    pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
    heightLeft -= pdfHeight
    while (heightLeft > 0) {
      position -= pdfHeight
      pdf.addPage()
      pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
      heightLeft -= pdfHeight
    }
    pdf.save(`协作详情_${task.id || 'task'}.pdf`)
  } catch (err) {
    console.error('导出PDF失败:', err)
  }
}
</script>

<style scoped>
.td-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(6px);
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.td-modal {
  width: 90%;
  max-width: 90vw;
  max-height: 85vh;
  background: rgb(0, 66, 126);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}

.td-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 32px;
  border-bottom: 1px solid rgba(59, 130, 246, 0.3);
  background: linear-gradient(135deg, rgba(0, 0, 0, 0.3), rgba(59, 130, 246, 0.1));
  position: relative;
  overflow: hidden;
}

.td-header::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, #3b82f6, #60a5fa, #3b82f6, transparent);
}

.td-header-bg {
  position: absolute;
  inset: 0;
  background: 
    repeating-linear-gradient(90deg, transparent, transparent 40px, rgba(59, 130, 246, 0.03) 40px, rgba(59, 130, 246, 0.03) 41px),
    repeating-linear-gradient(0deg, transparent, transparent 40px, rgba(59, 130, 246, 0.03) 40px, rgba(59, 130, 246, 0.03) 41px);
  pointer-events: none;
}

.td-header-content {
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.td-title-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.2), rgba(96, 165, 250, 0.1));
  border: 1px solid rgba(59, 130, 246, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #60a5fa;
  animation: td-pulse-glow 3s ease-in-out infinite;
}

@keyframes td-pulse-glow {
  0%, 100% { box-shadow: 0 0 8px rgba(59, 130, 246, 0.3); }
  50% { box-shadow: 0 0 16px rgba(59, 130, 246, 0.6), 0 0 24px rgba(96, 165, 250, 0.3); }
}

.td-title {
  font-size: 24px;
  font-weight: 700;
  color: #fff;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.td-title-text {
  background: linear-gradient(135deg, #fff, #60a5fa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 1px;
}


@keyframes td-badge-pulse {
  0%, 100% { box-shadow: 0 0 12px rgba(59, 130, 246, 0.4); }
  50% { box-shadow: 0 0 20px rgba(59, 130, 246, 0.6), 0 0 30px rgba(96, 165, 250, 0.3); }
}

.td-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
  z-index: 1;
}

.td-status-line {
  width: 1px;
  height: 24px;
  background: linear-gradient(180deg, transparent, rgba(59, 130, 246, 0.5), transparent);
}

.td-close {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.6);
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.td-close:hover {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
}

.td-body {
  padding: 32px;
  overflow-y: auto;
  flex: 1;
}

/* 三大块水平排列 */
.td-columns {
  display: flex;
  gap: 28px;
  align-items: flex-start;
}

.td-section {
  flex: 1;
  min-width: 0;
}

.td-section-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.td-section:last-child {
  margin-bottom: 0;
}

.td-section-title {
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 20px 0;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(59, 130, 246, 0.3);
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
}

.td-section-title::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 60px;
  height: 2px;
  background: linear-gradient(90deg, #3b82f6, transparent);
}

.td-section-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.15), rgba(96, 165, 250, 0.08));
  border: 1px solid rgba(59, 130, 246, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #60a5fa;
  flex-shrink: 0;
  transition: all 0.3s;
}

.td-section:hover .td-section-icon {
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.4);
  border-color: rgba(59, 130, 246, 0.5);
}

.td-icon-ai {
  animation: td-icon-pulse 3s ease-in-out infinite;
}

@keyframes td-icon-pulse {
  0%, 100% { box-shadow: 0 0 6px rgba(59, 130, 246, 0.2); }
  50% { box-shadow: 0 0 14px rgba(59, 130, 246, 0.5), 0 0 20px rgba(96, 165, 250, 0.2); }
}

.td-icon-collab {
  animation: td-icon-collab-pulse 3.5s ease-in-out infinite;
}

@keyframes td-icon-collab-pulse {
  0%, 100% { box-shadow: 0 0 6px rgba(59, 130, 246, 0.2); }
  50% { box-shadow: 0 0 14px rgba(96, 165, 250, 0.5), 0 0 20px rgba(147, 197, 253, 0.2); }
}

.td-section-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.3), transparent);
}

/* 信息网格（开始分析区块内） */
.td-info-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.td-info-grid .td-info-item {
  flex: 0 0 auto;
  min-width: 120px;
}

.td-info-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.td-info-item label {
  font-size: 20px;
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 0.5px;
}

.td-info-item span {
  font-size: 20px;
  color: #fff;
}

.td-info-item.full {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.td-snapshot {
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  padding: 16px;
  font-size: 20px;
  color: #fff;
  line-height: 1.6;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

/* 决策建议Markdown样式 */
.td-md-content {
  display: block;
  line-height: 1.6;
  word-break: break-word;
}

.td-md-content :deep(p) {
  margin: 4px 0;
}

.td-md-content :deep(h1),
.td-md-content :deep(h2),
.td-md-content :deep(h3),
.td-md-content :deep(h4),
.td-md-content :deep(h5),
.td-md-content :deep(h6) {
  margin: 10px 0 6px;
  color: #fff;
}

.td-md-content :deep(ul),
.td-md-content :deep(ol) {
  margin: 6px 0;
  padding-left: 22px;
}

.td-md-content :deep(li) {
  margin: 2px 0;
}

.td-md-content :deep(code) {
  background: rgba(0, 0, 0, 0.3);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 0.9em;
}

.td-md-content :deep(pre) {
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  padding: 12px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.td-md-content :deep(pre code) {
  background: transparent;
  padding: 0;
}

.td-md-content :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
}

.td-md-content :deep(th),
.td-md-content :deep(td) {
  border: 1px solid rgba(255, 255, 255, 0.25);
  padding: 4px 10px;
}

.td-md-content :deep(blockquote) {
  margin: 6px 0;
  padding: 4px 12px;
  border-left: 3px solid rgba(59, 130, 246, 0.6);
  color: rgba(255, 255, 255, 0.8);
}

.td-md-content :deep(a) {
  color: #60a5fa;
}

/* 协作流程步骤 */
.td-steps {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.td-step {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  position: relative;
}

.td-step::before {
  content: '';
  position: absolute;
  left: 23px;
  top: 52px;
  bottom: -20px;
  width: 2px;
  background: rgba(255, 255, 255, 0.2);
}

.td-step:last-child::before {
  display: none;
}

.td-step-num {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  border: 2px solid rgba(255, 255, 255, 0.3);
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.td-step-active .td-step-num {
  background: #fff;
  border-color: #fff;
  color: rgb(0, 66, 126);
}

.td-step-content {
  flex: 1;
}

.td-step-label {
  font-size: 22px;
  font-weight: 600;
  color: #fff;
}

.td-step-time {
  font-size: 20px;
  color: rgba(255, 255, 255, 0.6);
  margin-top: 4px;
}

.td-step-details {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 12px;
}

.td-step-details-col {
  flex-direction: column;
  align-items: flex-start;
}

.td-step-details span {
  font-size: 20px;
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.15);
}

.td-result-data {
  white-space: pre-wrap;
  word-break: break-all;
  max-width: 600px;
}

/* 执行结果回填表单 */
.td-feedback-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
}

.td-feedback-input,
.td-feedback-select {
  height: 40px;
  padding: 0 14px;
  font-size: 18px;
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 6px;
  outline: none;
  transition: border-color 0.2s;
}

.td-feedback-input {
  flex: 1;
  min-width: 220px;
}

.td-feedback-input::placeholder {
  color: rgba(255, 255, 255, 0.45);
}

.td-feedback-input:focus,
.td-feedback-select:focus {
  border-color: rgba(59, 130, 246, 0.8);
}

.td-feedback-select option {
  color: #333;
  background: #fff;
}

.td-feedback-btn {
  height: 40px;
  padding: 0 22px;
  font-size: 18px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #3b82f6, #60a5fa);
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.td-feedback-btn:hover {
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.6);
}

.td-feedback-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.td-feedback-error {
  font-size: 16px;
  color: #ff4d4f;
}

/* 滚动条样式 */
.td-body::-webkit-scrollbar {
  width: 8px;
}

.td-body::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 4px;
}

.td-body::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.25);
  border-radius: 4px;
}

.td-body::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.4);
}

/* 数据快照滚动条 */
.td-snapshot::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.td-snapshot::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 3px;
}

.td-snapshot::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 3px;
}

.td-snapshot::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.35);
}
</style>
