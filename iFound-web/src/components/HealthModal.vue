<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { marked } from 'marked'
import { aiRequest } from '@/utils/request'
import { useAreaStore } from '@/stores/area'
import { useUserStore } from '@/stores/user'
import html2canvas from 'html2canvas'
import jsPDF from 'jspdf'

// 自定义 marked 渲染器：将 echarts/table 代码块转换为可渲染的占位元素
marked.use({
  breaks: true,
  gfm: true,
  renderer: {
    code({ text, lang }) {
      if (lang === 'echarts') {
        return `<div class="hm-report-chart" data-chart="${encodeURIComponent(text)}"></div>`
      }
      if (lang === 'table') {
        try {
          const parsed = JSON.parse(text)
          if (parsed.columns && Array.isArray(parsed.data)) {
            let html = '<div class="hm-report-table-wrap"><table class="hm-report-md-table"><thead><tr>'
            parsed.columns.forEach(col => { html += `<th>${col}</th>` })
            html += '</tr></thead><tbody>'
            parsed.data.forEach(row => {
              html += '<tr>'
              if (Array.isArray(row)) {
                row.forEach(cell => { html += `<td>${cell}</td>` })
              }
              html += '</tr>'
            })
            html += '</tbody></table></div>'
            return html
          }
        } catch (e) { /* fall through */ }
      }
      return false
    }
  }
})

const emit = defineEmits(['close', 'openEnvAnalysis'])
const areaStore = useAreaStore()
const userStore = useUserStore()

const isHealthModalOpen = ref(false)
const isSubItemModalOpen = ref(false)
const currentSubItem = ref(null)
// 分项明细区内容切换：false=分项卡片，true=运行水平判定规则说明
const showSubRules = ref(false)

// 运行水平常量
const LEVEL_NORMAL = '正常'
const LEVEL_ATTENTION = '注意'
const LEVEL_URGENT = '紧急'

const healthSubItems = ref([])
const healthModalLevel = ref('')
const healthModalLevelDesc = ref('')
const healthModalTrendData = ref([])
const healthModalTrendTimes = ref([])
const healthModalChartRef = ref(null)
const healthReportRef = ref(null)
const scoreLoading = ref(false)
const scoreError = ref('')
const trendLoading = ref(false)
const trendError = ref('')
let healthModalChart = null

const hasHealthScore = computed(() => healthModalLevel.value && [LEVEL_NORMAL, LEVEL_ATTENTION, LEVEL_URGENT].includes(healthModalLevel.value))
// 将运行水平转换为数值用于图表（与 yAxis category 索引对齐）：
// yAxis data = [紧急(0), 注意(1), 正常(2)]，所以 紧急=0、注意=1、正常=2
const levelToValue = (level) => {
  if (level === LEVEL_NORMAL) return 2
  if (level === LEVEL_ATTENTION) return 1
  if (level === LEVEL_URGENT) return 0
  return 0
}
const hasTrendData = computed(() => healthModalTrendData.value.length >= 2)
const assessmentConclusion = computed(() => healthModalLevelDesc.value || '暂无可用的实时评估结论')

const agentCollabStats = ref({ totalTasks: 0, dispatchedTasks: 0, dispatchRate: 0, dailyStats: [] })
const agentCollabChartRef = ref(null)
let agentCollabChart = null

const closedTasksCount = ref(0)
const completionRate = computed(() => {
  const total = agentCollabStats.value.totalTasks || 0
  if (total <= 0) return 0
  return closedTasksCount.value / total
})

const isReportModalOpen = ref(false)
const reportContent = ref('')
const reportLoading = ref(false)
const reportError = ref('')
const reportRef = ref(null)
const reportHtml = computed(() => reportContent.value ? marked.parse(reportContent.value) : '')
let reportChartInstances = []

function openHealthModal() {
  isHealthModalOpen.value = true
  showSubRules.value = false
  fetchAgentCollabStats()
  fetchClosedTasksCount()
  fetchRealtimeScore()
  fetchHealthScoreHistory()
}

function closeHealthModal() {
  isHealthModalOpen.value = false
  if (healthModalChart) {
    healthModalChart.dispose()
    healthModalChart = null
  }
  agentCollabChart?.dispose()
  agentCollabChart = null
  isReportModalOpen.value = false
  emit('close')
}

onMounted(() => {
  openHealthModal()
})

onBeforeUnmount(() => {
  healthModalChart?.dispose()
  healthModalChart = null
  agentCollabChart?.dispose()
  agentCollabChart = null
  disposeReportCharts()
})

async function fetchRealtimeScore() {
  scoreLoading.value = true
  scoreError.value = ''
  try {
    const res = await aiRequest.get('/getTunnelRunScore', {
      params: { org_id: userStore.org_id || 3 }
    })
    if (res && (res.code === '200' || res.code === 200) && res.data) {
      const d = res.data
      healthModalLevel.value = d.totalLevel || ''
      healthModalLevelDesc.value = d.levelDesc || ''
      healthSubItems.value = buildHealthSubItems(d)
      if (!hasHealthScore.value) scoreError.value = '运行水平所需的实时数据暂不可用'
    } else {
      throw new Error(res?.message || '运行水平接口返回异常')
    }
  } catch (err) {
    console.error('获取实时运行水平失败:', err)
    healthModalLevel.value = ''
    healthModalLevelDesc.value = ''
    healthSubItems.value = []
    scoreError.value = '实时运行水平获取失败，请稍后重试'
  } finally {
    scoreLoading.value = false
  }
}

function buildHealthSubItems(scoreData) {
  const definitions = [
    { key: 'device', name: '设备健康', icon: 'gear', type: 'health' },
    { key: 'environment', name: '环境健康', icon: 'leaf', type: 'health' },
    { key: 'energy', name: '能源健康', icon: 'bolt', type: 'value' },
    { key: 'passenger', name: '客流压力', icon: 'user', type: 'passenger' }
  ]

  return definitions.flatMap((definition) => {
    const dimension = scoreData?.[definition.key]
    const healthRate = Number(dimension?.healthRate)
    if (!dimension || !Number.isFinite(healthRate)) return []

    let details
    if (definition.type === 'health') {
      details = [
        { label: '监测总数', value: dimension.totalCount ?? '—' },
        { label: '告警数量', value: dimension.alarmCount ?? '—' },
        { label: '故障数量', value: dimension.faultCount ?? '—' }
      ]
    } else if (definition.type === 'passenger') {
      const inOut = dimension.stationInOutTotal || {}
      details = [
        { label: '在站总人数', value: dimension.value ?? '—' },
        { label: '拥堵区域数', value: dimension.crowdedAreaCount ?? 0 },
        { label: '客流状态', value: dimension.status || '—' },
        { label: '累计进站', value: inOut.entryCount ?? '—' },
        { label: '累计出站', value: inOut.exitCount ?? '—' }
      ]
    } else {
      details = [
        { label: '实时值', value: dimension.value ?? '—' },
        { label: '接口状态', value: dimension.status || '—' }
      ]
    }

    const abnormalCount = definition.type === 'health'
      ? Number(dimension.alarmCount || 0) + Number(dimension.faultCount || 0)
      : (dimension.status && !dimension.status.includes('正常') && !dimension.status.includes('低') ? 1 : 0)

    // 将健康率转换为运行水平
    let level = LEVEL_NORMAL
    if (healthRate < 60) level = LEVEL_URGENT
    else if (healthRate < 85) level = LEVEL_ATTENTION

    // 客流维度附加完整明细数据
    const extra = {}
    if (definition.type === 'passenger') {
      if (dimension.crowdedAreas) extra.crowdedAreas = dimension.crowdedAreas
      if (dimension.stationRealtime) extra.stationRealtime = dimension.stationRealtime
      if (dimension.stationInOutTotal) extra.stationInOutTotal = dimension.stationInOutTotal
    }

    return [{ ...definition, level: dimension.level || level, healthRate: healthRate.toFixed(1), details, abnormalCount, ...extra }]
  })
}

function clampScore(value) {
  return Math.round(Math.min(100, Math.max(0, Number(value))) * 10) / 10
}

function initHealthModalChart() {
  if (!healthModalChartRef.value || !hasTrendData.value) {
    healthModalChart?.dispose()
    healthModalChart = null
    return
  }
  healthModalChart?.dispose()
  healthModalChart = echarts.init(healthModalChartRef.value)

  const data = healthModalTrendData.value
  const times = healthModalTrendTimes.value

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        const val = p.value
        let levelText = '未知'
        if (val === 2) levelText = LEVEL_NORMAL
        else if (val === 1) levelText = LEVEL_ATTENTION
        else if (val === 0) levelText = LEVEL_URGENT
        return `${times[p.dataIndex]}<br/>运行水平: <b>${levelText}</b>`
      },
      backgroundColor: 'rgba(20, 30, 50, 0.9)',
      borderColor: 'rgba(10, 170, 97, 0.3)',
      textStyle: { color: '#e8f0fe', fontSize: 12 }
    },
    grid: { top: 12, bottom: 24, left: 10, right: 12, containLabel: false },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: times,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,.12)' } },
      axisTick: { show: false },
      axisLabel: {
        color: 'rgba(255,255,255,.48)',
        fontSize: 10,
        formatter: (value) => value ? value.slice(11, 16) : ''
      }
    },
    yAxis: {
      type: 'category',
      data: [LEVEL_URGENT, LEVEL_ATTENTION, LEVEL_NORMAL],
      axisLine: { lineStyle: { color: 'rgba(255,255,255,.12)' } },
      axisTick: { show: false },
      axisLabel: {
        color: 'rgba(255,255,255,.48)',
        fontSize: 11
      }
    },
    series: [{
      type: 'line',
      data,
      smooth: true,
      showSymbol: data.length <= 8,
      symbolSize: 5,
      lineStyle: { width: 2.5, color: '#34d399' },
      itemStyle: { color: '#34d399', borderColor: '#064e3b', borderWidth: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(10, 170, 97, 0.26)' },
          { offset: 1, color: 'rgba(10, 170, 97, 0)' }
        ])
      }
    }]
  }
  healthModalChart.setOption(option)
  healthModalChart.resize()
}

async function fetchClosedTasksCount() {
  try {
    const res = await aiRequest.get('/dispatch/list', {
      params: { adoptStatus: 0, pageNum: 1, pageSize: 1 }
    })
    if (res && (res.code === '200' || res.code === 200) && res.data) {
      closedTasksCount.value = res.data.total || 0
    }
  } catch (err) {
    console.error('获取已关闭任务数失败:', err)
  }
}

async function fetchAgentCollabStats() {
  try {
    const res = await aiRequest.get('/dispatch/agent-collab-stats')
    if (res && (res.code === '200' || res.code === 200) && res.data) {
      agentCollabStats.value = res.data
    }
  } catch (err) {
    console.error('获取Agent协作统计失败:', err)
  }
  await nextTick()
  initAgentCollabChart()
}

function initAgentCollabChart() {
  if (!agentCollabChartRef.value) return
  const stats = agentCollabStats.value.dailyStats || []
  if (stats.length === 0) return

  agentCollabChart?.dispose()
  agentCollabChart = echarts.init(agentCollabChartRef.value)

  const dates = stats.map(s => s.date)
  const generatedData = stats.map(s => s.generatedCount || 0)
  const targetData = stats.map(s => s.targetCount || 0)

  const series = []
  if (generatedData.some(v => v > 0)) {
    series.push({
      name: '生成任务',
      type: 'bar',
      data: generatedData,
      itemStyle: { color: '#6366f1', borderRadius: [3, 3, 0, 0] }
    })
  }
  if (targetData.some(v => v > 0)) {
    series.push({
      name: '目标任务',
      type: 'bar',
      data: targetData,
      itemStyle: { color: '#06b6d4', borderRadius: [3, 3, 0, 0] }
    })
  }
  if (series.length === 0) {
    series.push({ name: '任务数', type: 'bar', data: stats.map(() => 0), itemStyle: { color: '#6366f1' } })
  }

  const option = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: {
      data: series.map(s => s.name),
      textStyle: { fontSize: 10, color: 'rgba(255,255,255,0.6)' },
      top: 0,
      type: 'scroll'
    },
    grid: { top: 28, bottom: 22, left: 30, right: 8 },
    xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 10, color: 'rgba(255,255,255,0.48)' } },
    yAxis: { type: 'value', axisLabel: { fontSize: 10, color: 'rgba(255,255,255,0.48)' }, splitLine: { lineStyle: { color: 'rgba(255,255,255,0.06)' } } },
    series
  }
  agentCollabChart.setOption(option)
  agentCollabChart.resize()
}

async function fetchHealthScoreHistory() {
  trendLoading.value = true
  trendError.value = ''
  try {
    const now = new Date()
    const sixHoursAgo = new Date(now.getTime() - 6 * 60 * 60 * 1000)
    const pad = (n) => String(n).padStart(2, '0')
    const endTime = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
    const startTime = `${sixHoursAgo.getFullYear()}-${pad(sixHoursAgo.getMonth() + 1)}-${pad(sixHoursAgo.getDate())} ${pad(sixHoursAgo.getHours())}:${pad(sixHoursAgo.getMinutes())}:${pad(sixHoursAgo.getSeconds())}`

    const res = await aiRequest.get('/getHistoryScore', {
      params: { org_id: userStore.org_id || 3, start_time: startTime, end_time: endTime }
    })
    if (res && (res.code === '200' || res.code === 200) && Array.isArray(res.data)) {
      const data = res.data
        .filter(item => item.totalLevel && [LEVEL_NORMAL, LEVEL_ATTENTION, LEVEL_URGENT].includes(item.totalLevel) && item.createTime)
        .slice(-12)
      // 将运行水平转换为数值：正常=3，注意=2，紧急=1
      healthModalTrendData.value = data.map(item => levelToValue(item.totalLevel))
      healthModalTrendTimes.value = data.map(item => item.createTime.replace('T', ' '))
      if (data.length > 0 && data[data.length - 1].levelDesc) {
        healthModalLevelDesc.value ||= data[data.length - 1].levelDesc
      }
      if (data.length < 2) trendError.value = '近6小时暂无足够的历史运行水平数据'
      await nextTick()
      initHealthModalChart()
    } else {
      throw new Error(res?.message || '历史运行水平接口返回异常')
    }
  } catch (err) {
    console.error('获取历史运行水平曲线失败:', err)
    healthModalTrendData.value = []
    healthModalTrendTimes.value = []
    trendError.value = '运行水平趋势获取失败，请稍后重试'
    initHealthModalChart()
  } finally {
    trendLoading.value = false
  }
}

function openSubItemModal(subItem) {
  currentSubItem.value = subItem
  isSubItemModalOpen.value = true
}

function closeSubItemModal() {
  isSubItemModalOpen.value = false
  currentSubItem.value = null
}

function getPressureLevelClass(level) {
  const map = {
    '低': 'pressure-low',
    '较低': 'pressure-low-mid',
    '中': 'pressure-mid',
    '较高': 'pressure-high-mid',
    '极高': 'pressure-high'
  }
  return map[level] || ''
}

const iconPaths = {
  gear: 'M12 15a3 3 0 100-6 3 3 0 000 6zm9-3a9 9 0 01-.18 1.8l2.1 1.6-2 3.4-2.5-1a9 9 0 01-3.1 1.8l-.4 2.6h-4l-.4-2.6a9 9 0 01-3.1-1.8l-2.5 1-2-3.4 2.1-1.6A9 9 0 013 12a9 9 0 01.18-1.8L1.08 8.6l2-3.4 2.5 1a9 9 0 013.1-1.8L9.08 2h4l.4 2.6a9 9 0 013.1 1.8l2.5-1 2 3.4-2.1 1.6A9 9 0 0121 12z',
  leaf: 'M11 20A7 7 0 014 13V4h9a7 7 0 010 14h-2zm0-2h2a5 5 0 000-10H6v6a5 5 0 005 5z',
  shield: 'M12 2L3 6v6c0 5 3.8 9.7 9 11 5.2-1.3 9-6 9-11V6l-9-4zm0 2.2L19 7.5V12c0 3.9-2.8 7.4-7 8.6-4.2-1.2-7-4.7-7-8.6V7.5L12 4.2zM10 14l-2-2 1.4-1.4L10 11.2l3.6-3.6L15 9l-5 5z',
  bolt: 'M13 2L4 14h6l-1 8 9-12h-6l1-8z',
  user: 'M12 12a5 5 0 100-10 5 5 0 000 10zm0 2c-5 0-9 3-9 7v1h18v-1c0-4-4-7-9-7z'
}
// 根据运行水平获取颜色
function getLevelColor(level) {
  if (level === LEVEL_NORMAL) return '#10b981'
  if (level === LEVEL_ATTENTION) return '#f59e0b'
  if (level === LEVEL_URGENT) return '#ef4444'
  return '#9ca3af'
}
function getLevelBg(level) {
  if (level === LEVEL_NORMAL) return 'rgba(16,185,129,0.1)'
  if (level === LEVEL_ATTENTION) return 'rgba(245,158,11,0.1)'
  if (level === LEVEL_URGENT) return 'rgba(239,68,68,0.1)'
  return 'rgba(156,163,175,0.1)'
}

async function openReportModal() {
  isReportModalOpen.value = true
  reportLoading.value = true
  reportError.value = ''
  reportContent.value = ''
  try {
    const res = await aiRequest.get('/report/latest')
    if (res && (res.code === '200' || res.code === 200)) {
      reportContent.value = res.data || ''
      if (!reportContent.value) {
        reportError.value = '暂无已生成的运行报告'
      }
    } else {
      throw new Error(res?.message || '获取运行报告失败')
    }
  } catch (err) {
    console.error('获取运行报告失败:', err)
    reportError.value = '获取运行报告失败，请稍后重试'
  } finally {
    reportLoading.value = false
    if (reportContent.value) {
      await nextTick()
      renderReportCharts()
    }
  }
}

function renderReportCharts() {
  disposeReportCharts()
  if (!reportRef.value) return
  const chartEls = reportRef.value.querySelectorAll('.hm-report-chart')
  chartEls.forEach(el => {
    const raw = decodeURIComponent(el.dataset.chart || '')
    if (!raw) return
    try {
      const option = JSON.parse(raw)
      const instance = echarts.init(el)
      instance.setOption(option)
      reportChartInstances.push(instance)
    } catch (e) {
      console.error('报告图表渲染失败:', e)
      el.innerHTML = '<div style="color:#f56c6c;font-size:12px;padding:8px;">图表配置解析失败</div>'
    }
  })
}

function disposeReportCharts() {
  reportChartInstances.forEach(c => c?.dispose())
  reportChartInstances = []
}

function closeReportModal() {
  isReportModalOpen.value = false
  disposeReportCharts()
}

async function exportReportPdf() {
  if (!reportRef.value) return
  const el = reportRef.value
  const originals = { overflow: el.style.overflow, height: el.style.height, maxHeight: el.style.maxHeight }
  el.style.overflow = 'visible'
  el.style.height = 'auto'
  el.style.maxHeight = 'none'
  await nextTick()
  try {
    const canvas = await html2canvas(el, {
      scale: 2,
      useCORS: true,
      backgroundColor: '#0a1628',
      logging: false,
      windowWidth: el.scrollWidth,
      windowHeight: el.scrollHeight,
    })
    const imgData = canvas.toDataURL('image/png')
    const pdf = new jsPDF('p', 'mm', 'a4')
    const pdfWidth = pdf.internal.pageSize.getWidth()
    const pdfHeight = pdf.internal.pageSize.getHeight()
    const imgWidth = pdfWidth
    const imgHeight = (canvas.height * imgWidth) / canvas.width
    let heightLeft = imgHeight
    let position = 0
    pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
    heightLeft -= pdfHeight
    while (heightLeft > 0) {
      position = heightLeft - imgHeight
      pdf.addPage()
      pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
      heightLeft -= pdfHeight
    }
    const now = new Date()
    const dateStr = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
    pdf.save(`站房运行报告_${dateStr}.pdf`)
  } catch (e) {
    console.error('导出报告PDF失败:', e)
    alert('导出报告PDF失败，请重试')
  } finally {
    el.style.overflow = originals.overflow
    el.style.height = originals.height
    el.style.maxHeight = originals.maxHeight
  }
}
</script>

<template>
  <div v-if="isHealthModalOpen" class="hm-overlay" @click.self="closeHealthModal">
    <div class="hm-modal">
      <!-- Header -->
      <div class="hm-header">
        <div class="hm-header-title">
          <div class="hm-header-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="22" height="22">
              <path d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"/>
            </svg>
          </div>
          <h3>站房整体状态评估</h3>
        </div>
        <div class="hm-header-actions">
          <button class="hm-export-btn" type="button" @click="emit('openEnvAnalysis')">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a10 10 0 1 0 10 10H12V2z"/><path d="M12 2a10 10 0 0 1 10 10"/><circle cx="12" cy="12" r="3"/></svg>
            环境分析报告
          </button>
          <button class="hm-export-btn" type="button" @click="openReportModal">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><path d="M14 2v6h6M16 13H8M16 17H8M10 9H8"/></svg>
            全站分析报告
          </button>
          <button class="hm-close-btn" type="button" aria-label="关闭状态评估" @click="closeHealthModal">✕</button>
        </div>
      </div>

      <!-- Body -->
      <div class="hm-body" ref="healthReportRef">

        <!-- ====== 站房整体运行水平（含分项评估明细 + AI分析结论）====== -->
        <div class="hm-card hm-card-primary hm-fade-1">
          <div class="hm-card-head">
            <div class="hm-card-tg">
              <span class="hm-bar"></span>
              <h4>站房整体运行水平</h4>
            </div>
          </div>

          <!-- 水平 + 趋势图 -->
          <div class="hm-score-row">
            <div class="hm-score-block">
              <div class="hm-level-display" :class="healthModalLevel" role="img" :aria-label="hasHealthScore ? `站房运行水平 ${healthModalLevel}` : '站房运行水平暂无数据'">
                <div class="hm-level-icon">
                  <svg v-if="healthModalLevel === LEVEL_NORMAL" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 6L9 17l-5-5"/></svg>
                  <svg v-else-if="healthModalLevel === LEVEL_ATTENTION" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/></svg>
                  <svg v-else-if="healthModalLevel === LEVEL_URGENT" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/><path d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01"/></svg>
                </div>
                <div class="hm-level-text">
                  <span v-if="scoreLoading" class="hm-score-loading">获取中</span>
                  <span v-else class="hm-level-name">{{ healthModalLevel || (scoreLoading ? '评估中' : '暂无数据') }}</span>
                </div>
              </div>
              <div class="hm-score-meta">
                <span class="hm-level-desc">{{ scoreError || healthModalLevelDesc || '正在获取评估信息…' }}</span>
              </div>
            </div>
            <div class="hm-trend-block">
              <div class="hm-trend-label">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><path d="M3 17l6-6 4 4 8-8M21 7v6h-6"/></svg>
                运行水平趋势
              </div>
              <div class="hm-trend-stage">
                <div ref="healthModalChartRef" class="hm-trend-chart" :class="{ 'is-hidden': !hasTrendData }"></div>
                <div v-if="trendLoading || !hasTrendData" class="hm-trend-empty">
                  <span class="hm-trend-empty-icon"></span>
                  {{ trendLoading ? '正在加载历史水平数据…' : (trendError || '暂无运行水平趋势数据') }}
                </div>
              </div>
            </div>
          </div>

          <!-- 分项评估明细 -->
          <div class="hm-sub-section">
            <div class="hm-sub-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M9 11l3 3L22 4M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/></svg>
              分项评估明细
              <button class="hm-sub-rules-toggle" type="button"
                :class="{ active: showSubRules }"
                :title="showSubRules ? '返回分项明细' : '运行水平判定规则说明'"
                :aria-label="showSubRules ? '返回分项明细' : '查看运行水平判定规则说明'"
                @click="showSubRules = !showSubRules">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 015.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
              </button>
            </div>
            <!-- 运行水平判定规则说明（点击问题图标切换显示） -->
            <div v-if="showSubRules" class="hm-sub-rules-panel">
              <div class="hm-rule-group">
                <div class="hm-rule-group-title">整体水平判定：</div>
                <div class="hm-rule-item"><span class="hm-rule-dot hm-dot-green"></span>任一维度为紧急 → 整体<span class="hm-rule-level hm-l-urgent">紧急</span></div>
                <div class="hm-rule-item"><span class="hm-rule-dot hm-dot-orange"></span>无紧急但有注意 → 整体<span class="hm-rule-level hm-l-attention">注意</span></div>
                <div class="hm-rule-item"><span class="hm-rule-dot hm-dot-blue"></span>全部正常 → 整体<span class="hm-rule-level hm-l-normal">正常</span></div>
              </div>
              <div class="hm-rule-group">
                <div class="hm-rule-group-title">各维度健康率计算：</div>
                <div class="hm-rule-item"><span class="hm-rule-dot hm-dot-blue"></span><b>设备健康</b>：(总数 − 故障 − 告警) ÷ 总数 × 100%</div>
                <div class="hm-rule-item"><span class="hm-rule-dot hm-dot-blue"></span><b>环境健康</b>：(总数 − 异常) ÷ 总数 × 100%</div>
                <div class="hm-rule-item"><span class="hm-rule-dot hm-dot-blue"></span><b>能源健康</b>：状态正常 → 100%；状态异常 → 50%</div>
                <div class="hm-rule-item"><span class="hm-rule-dot hm-dot-blue"></span><b>客流压力</b>：0个拥堵 → 100%；1~2个 → 75%；3个以上 → 50%</div>
              </div>
              <div class="hm-rule-group">
                <div class="hm-rule-group-title">各维度水平判定：</div>
                  <span class="hm-rule-thr hm-thr-green">健康率 ≥ 85% → 正常</span>
                  <span class="hm-rule-thr hm-thr-orange">60% ≤ 健康率 &lt; 85% → 注意</span>
                  <span class="hm-rule-thr hm-thr-red">健康率 &lt; 60% → 紧急</span>
              </div>
            </div>
            <div v-else-if="healthSubItems.length" class="hm-sub-grid">
              <div v-for="(item, idx) in healthSubItems" :key="idx"
                class="hm-sub-card hm-card-anim"
                role="button" tabindex="0" :aria-label="`查看${item.name}接口明细`"
                :style="{ animationDelay: (idx * 0.08 + 0.2) + 's', '--cc': getLevelColor(item.level), '--cb': getLevelBg(item.level) }"
                @click="openSubItemModal(item)" @keydown.enter="openSubItemModal(item)" @keydown.space.prevent="openSubItemModal(item)">
                <div class="hm-sub-main">
                  <div class="hm-sub-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path :d="iconPaths[item.icon] || ''"/></svg>
                  </div>
                  <div class="hm-sub-info">
                    <div class="hm-sub-top">
                      <div class="hm-sub-name">{{ item.name }}</div>
                      <span class="hm-sub-score" :style="{ color: getLevelColor(item.level) }">{{ item.level }}</span>
                    </div>

                    <div class="hm-sub-tags">
                      <span v-if="item.abnormalCount > 0" class="hm-tag-alarm">{{ item.abnormalCount }}项异常</span>
                      <span v-else class="hm-tag-ok">正常</span>
                    </div>
                  </div>
                </div>

                <div class="hm-sub-bar"><div class="hm-sub-bar-fill" :style="{ width: item.healthRate + '%', background: getLevelColor(item.level) }"></div></div>
              </div>
            </div>
            <div v-else class="hm-inline-empty">{{ scoreLoading ? '正在加载分项数据…' : '接口未返回分项运行水平数据' }}</div>
          </div>

        </div>

        <!-- ====== 两列卡片行 ====== -->
        <div class="hm-cards-row hm-fade-2">
          <!-- AI分析统计 -->
          <div class="hm-card hm-card-side">
            <div class="hm-card-head">
              <div class="hm-card-tg">
                <span class="hm-bar hm-bar-blue"></span>
                <h4>AI分析统计</h4>
              </div>
            </div>
            <div class="hm-card-body">
              <div class="hm-stat-trio">
                <div class="hm-stat-item">
                  <div class="hm-stat-item-icon" style="color:#3b82f6;background:rgba(59,130,246,0.1)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/></svg>
                  </div>
                  <span class="hm-stat-item-label">总任务</span>
                  <span class="hm-stat-item-num">{{ agentCollabStats.totalTasks }}</span>
                </div>
                <div class="hm-stat-item">
                  <div class="hm-stat-item-icon" style="color:#10b981;background:rgba(16,185,129,0.1)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/></svg>
                  </div>
                  <span class="hm-stat-item-label">已派发</span>
                  <span class="hm-stat-item-num" style="color:#10b981">{{ agentCollabStats.dispatchedTasks }}</span>
                </div>
                <div class="hm-stat-item">
                  <div class="hm-stat-item-icon" style="color:#f59e0b;background:rgba(245,158,11,0.1)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M9 12l2 2 4-4M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
                  </div>
                  <span class="hm-stat-item-label">已完成</span>
                  <span class="hm-stat-item-num" style="color:#f59e0b">{{ closedTasksCount }}</span>
                </div>
              </div>
              <div class="hm-dispatch-rate">
                <span class="hm-rate-label">完成率</span>
                <div class="hm-rate-track"><div class="hm-rate-fill" :style="{ width: (completionRate * 100) + '%' }"></div></div>
                <span class="hm-rate-val">{{ (completionRate * 100).toFixed(1) }}%</span>
              </div>
            </div>
          </div>

          <!-- 近7日任务趋势 -->
          <div class="hm-card hm-card-side">
            <div class="hm-card-head">
              <div class="hm-card-tg">
                <span class="hm-bar hm-bar-purple"></span>
                <h4>近7日AI分析趋势</h4>
              </div>
            </div>
            <div class="hm-card-body">
              <div ref="agentCollabChartRef" class="hm-collab-chart"></div>
            </div>
          </div>
        </div>

      </div>
    </div>
  </div>

  <!-- 分项详情弹窗 -->
  <div v-if="isSubItemModalOpen && currentSubItem" class="hm-sub-overlay" @click.self="closeSubItemModal">
    <div class="hm-sub-modal">
      <div class="hm-sub-modal-head">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18" style="color:#60a5fa"><path :d="iconPaths[currentSubItem.icon] || ''"/></svg>
        {{ currentSubItem.name }}
        <button class="hm-sub-close-x" type="button" :aria-label="`关闭${currentSubItem.name}明细`" @click="closeSubItemModal"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M18 6L6 18M6 6l12 12"/></svg></button>
      </div>
      <div class="hm-sub-modal-body">
        <div class="hm-sub-overview">
          <div class="hm-ov-score"><span class="hm-ov-label">运行水平</span><span class="hm-ov-num" :style="{ color: getLevelColor(currentSubItem.level) }">{{ currentSubItem.level }}</span></div>
          <div class="hm-ov-meta"><span>健康率: {{ currentSubItem.healthRate }}%</span><span>异常: <span :class="currentSubItem.abnormalCount > 0 ? 'orange-text' : 'green-text'">{{ currentSubItem.abnormalCount > 0 ? currentSubItem.abnormalCount + '项' : '无' }}</span></span></div>
        </div>

        <!-- 累计进出站统计 -->
        <div v-if="currentSubItem.stationInOutTotal" class="hm-passenger-section">
          <h4 class="hm-ded-title">累计进出站统计</h4>
          <div class="hm-inout-row">
            <div class="hm-inout-card hm-inout-entry">
              <div class="hm-inout-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16M14 12H9"/><path d="M9 18l3-3-3-3"/></svg>
              </div>
              <div class="hm-inout-info">
                <span class="hm-inout-label">累计进站</span>
                <span class="hm-inout-value">{{ currentSubItem.stationInOutTotal.entryCount?.toLocaleString() ?? '—' }}</span>
              </div>
            </div>
            <div class="hm-inout-card hm-inout-exit">
              <div class="hm-inout-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16M9 12h5"/><path d="M15 18l-3-3 3-3"/></svg>
              </div>
              <div class="hm-inout-info">
                <span class="hm-inout-label">累计出站</span>
                <span class="hm-inout-value">{{ currentSubItem.stationInOutTotal.exitCount?.toLocaleString() ?? '—' }}</span>
              </div>
            </div>
          </div>
        </div>
        <div class="hm-deductions" style="margin-top:20px">
          <h4 class="hm-ded-title">实时监测</h4>
          <template v-for="detail in currentSubItem.details" :key="detail.label">
            <div v-if="!['拥堵区域数', '客流状态', '累计进站', '累计出站'].includes(detail.label)" class="hm-ded-card">
              <div class="hm-ded-info"><div class="hm-ded-name">{{ detail.label }}</div></div>
              <div class="hm-ded-rt"><span class="hm-ded-rt-val">{{ detail.value }}</span></div>
            </div>
          </template>
        </div>
        <!-- 客流拥堵区域列表 -->
        <div v-if="currentSubItem.crowdedAreas && currentSubItem.crowdedAreas.length" class="hm-passenger-section">
          <h4 class="hm-ded-title">拥堵区域 ({{ currentSubItem.crowdedAreas.length }})</h4>
          <div v-for="area in currentSubItem.crowdedAreas" :key="area.areaCode" class="hm-ded-card hm-inout-entry">
            <div class="hm-ded-info"><div class="hm-ded-name">{{ area.areaName }}</div></div>
            <div class="hm-ded-rt"><span class="hm-ded-rt-val">{{ area.currPeo }}人</span></div>
          </div>
        </div>
        <!-- 客流各分组区域人数 -->
        <div v-if="currentSubItem.stationRealtime && currentSubItem.stationRealtime.groupItems && currentSubItem.stationRealtime.groupItems.length" class="hm-passenger-section">
          <h4 class="hm-ded-title">各分组区域人数</h4>
          <div class="hm-group-grid">
            <div v-for="group in currentSubItem.stationRealtime.groupItems" :key="group.groupCode" class="hm-group-item">
              <span class="hm-group-name">{{ group.groupName }}</span>
              <span class="hm-group-peo">{{ group.currPeo }}人</span>
            </div>
          </div>
        </div>
      </div>
      <div class="hm-sub-modal-foot"><button class="hm-sub-close-btn" type="button" @click="closeSubItemModal">关闭</button></div>
    </div>
  </div>

  <!-- 运行报告弹窗 -->
  <div v-if="isReportModalOpen" class="hm-report-overlay" @click.self="closeReportModal">
    <div class="hm-report-modal">
      <div class="hm-report-header">
        <h3>站房运行分析报告</h3>
        <div class="hm-report-actions">
          <button class="hm-report-export-btn" type="button" :disabled="!reportContent" @click="exportReportPdf">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3"/></svg>
            导出PDF
          </button>
          <button class="hm-report-close-btn" type="button" @click="closeReportModal">✕</button>
        </div>
      </div>
      <div class="hm-report-body">
        <div v-if="reportLoading" class="hm-report-loading">正在获取站房运行分析报告…</div>
        <div v-else-if="reportError" class="hm-report-error">{{ reportError }}</div>
        <div v-else ref="reportRef" class="hm-report-content" v-html="reportHtml"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hm-overlay{position:fixed;inset:0;background:rgba(0,0,0,0.5);backdrop-filter:blur(8px);display:flex;align-items:center;justify-content:center;z-index:1000;animation:hm-fade-in .25s ease}
.hm-modal{background:rgb(0,66,126);border:1px solid rgba(0,180,255,.15);border-radius:16px;width:60vw;max-width:90vw;max-height:90vh;display:flex;flex-direction:column;box-shadow:0 24px 80px rgba(0,0,0,.6),0 0 1px rgba(0,180,255,.3),inset 0 1px 0 rgba(255,255,255,.05);animation:hm-scale-in .35s cubic-bezier(.16,1,.3,1);overflow:hidden;position:relative}
.hm-modal::before{content:'';position:absolute;top:0;left:0;right:0;height:2px;background:linear-gradient(90deg,transparent,rgba(0,180,255,.6),rgba(96,165,250,.4),rgba(0,180,255,.6),transparent);z-index:10}
.hm-header{display:flex;justify-content:space-between;align-items:center;padding:16px 24px;border-bottom:1px solid rgba(255,255,255,.06);background:linear-gradient(180deg,rgba(0,0,0,.15),transparent);flex-shrink:0}
.hm-header-title{display:flex;align-items:center;gap:12px}
.hm-header-icon{width:36px;height:36px;border-radius:10px;background:linear-gradient(135deg,rgba(59,130,246,.2),rgba(96,165,250,.08));border:1px solid rgba(59,130,246,.3);display:flex;align-items:center;justify-content:center;color:#60a5fa;animation:hm-icon-pulse 3s ease-in-out infinite}
@keyframes hm-icon-pulse{0%,100%{box-shadow:0 0 8px rgba(59,130,246,.3)}50%{box-shadow:0 0 16px rgba(59,130,246,.5),0 0 24px rgba(96,165,250,.2)}}
.hm-header h3{margin:0;font-size:19px;font-weight:700;color:#e8f0fe;letter-spacing:.5px}
.hm-header-actions{display:flex;align-items:center;gap:8px}
.hm-export-btn{display:flex;align-items:center;gap:6px;padding:7px 14px;background:rgba(59,130,246,.12);border:1px solid rgba(59,130,246,.3);border-radius:8px;color:#60a5fa;font-size:13px;cursor:pointer;transition:all .25s}
.hm-export-btn:hover{background:rgba(59,130,246,.22);border-color:rgba(59,130,246,.5);box-shadow:0 0 12px rgba(59,130,246,.3)}
.hm-close-btn{background:none;border:none;color:rgba(255,255,255,.35);font-size:20px;cursor:pointer;width:34px;height:34px;border-radius:8px;transition:all .2s;line-height:1;display:flex;align-items:center;justify-content:center}
.hm-close-btn:hover{color:#fff;background:rgba(255,255,255,.08)}
.hm-body{flex:1;overflow-y:auto;padding:16px 20px 20px;display:flex;flex-direction:column;gap:14px}
.hm-body::-webkit-scrollbar{width:6px}
.hm-body::-webkit-scrollbar-track{background:transparent}
.hm-body::-webkit-scrollbar-thumb{background:rgba(255,255,255,.12);border-radius:3px}
.hm-body::-webkit-scrollbar-thumb:hover{background:rgba(255,255,255,.22)}
.hm-card{background:rgba(255,255,255,.025);border:1px solid rgba(255,255,255,.07);border-radius:12px;overflow:hidden;transition:border-color .3s}
.hm-card:hover{border-color:rgba(59,130,246,.2)}
.hm-card-primary{background:linear-gradient(135deg,rgba(59,130,246,.04),rgba(0,180,255,.02));border:1px solid rgba(59,130,246,.12)}
.hm-card-head{display:flex;align-items:center;justify-content:space-between;padding:12px 18px;border-bottom:1px solid rgba(255,255,255,.05)}
.hm-card-tg{display:flex;align-items:center;gap:10px}
.hm-bar{width:3px;height:15px;border-radius:2px;background:#3b82f6;flex-shrink:0}
.hm-bar-blue{background:#3b82f6}
.hm-bar-orange{background:#f59e0b}
.hm-bar-green{background:#10b981}
.hm-bar-purple{background:#8b5cf6}
.hm-card-head h4{margin:0;font-size:15px;font-weight:700;color:rgba(255,255,255,.9);letter-spacing:.3px}
.hm-count-badge{font-size:12px;color:rgba(255,255,255,.4);padding:2px 8px;background:rgba(255,255,255,.05);border-radius:6px}
.hm-score-row{display:flex;gap:24px;padding:18px;align-items:stretch}
.hm-score-block{display:flex;flex-direction:column;gap:12px;flex:0 0 310px;min-width:0}
/* 运行水平显示样式 */
.hm-level-display{width:116px;height:116px;border-radius:50%;display:flex;flex-direction:column;align-items:center;justify-content:center;transition:all .3s ease;margin:0 auto}
.hm-level-display.正常{background:rgba(16,185,129,.15);border:2px solid rgba(16,185,129,.4);box-shadow:0 0 20px rgba(16,185,129,.2)}
.hm-level-display.注意{background:rgba(245,158,11,.15);border:2px solid rgba(245,158,11,.4);box-shadow:0 0 20px rgba(245,158,11,.2)}
.hm-level-display.紧急{background:rgba(239,68,68,.15);border:2px solid rgba(239,68,68,.4);box-shadow:0 0 20px rgba(239,68,68,.2);animation:urgent-pulse 2s ease-in-out infinite}
@keyframes urgent-pulse{0%,100%{box-shadow:0 0 20px rgba(239,68,68,.2)}50%{box-shadow:0 0 30px rgba(239,68,68,.5)}}
.hm-level-icon{width:40px;height:40px;margin-bottom:4px}
.hm-level-display.正常 .hm-level-icon{color:#10b981}
.hm-level-display.注意 .hm-level-icon{color:#f59e0b}
.hm-level-display.紧急 .hm-level-icon{color:#ef4444}
.hm-level-text{display:flex;align-items:center;justify-content:center;min-height:36px}
.hm-level-name{font-size:20px;font-weight:800}
.hm-level-display.正常 .hm-level-name{color:#10b981}
.hm-level-display.注意 .hm-level-name{color:#f59e0b}
.hm-level-display.紧急 .hm-level-name{color:#ef4444}
.hm-score-loading{font-size:12px;color:rgba(255,255,255,.6)}
.hm-score-meta{display:flex;flex-direction:column;gap:6px;align-self:center}
.hm-level-desc{font-size:12px;color:rgba(255,255,255,.55);max-width:220px;line-height:1.5;text-align:center}
.hm-trend-block{flex:1;display:flex;flex-direction:column;gap:6px;min-width:240px}
.hm-trend-label{display:flex;align-items:center;gap:6px;font-size:12px;color:rgba(255,255,255,.5);font-weight:600}
.hm-trend-stage{position:relative;min-height:104px;border-radius:8px;background:rgba(0,0,0,.08);overflow:hidden}
.hm-trend-chart{width:100%;height:104px}
.hm-trend-chart.is-hidden{visibility:hidden}
.hm-trend-empty{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:8px;color:rgba(255,255,255,.48);font-size:12px;text-align:center;padding:16px}
.hm-trend-empty-icon{width:28px;height:14px;border-bottom:2px solid rgba(96,165,250,.42);transform:skewY(-18deg);opacity:.8}
.hm-sub-section{padding:0 18px 16px}
.hm-sub-title{display:flex;align-items:center;gap:8px;font-size:13px;font-weight:600;color:rgba(255,255,255,.65);margin-bottom:10px;padding-top:14px;border-top:1px solid rgba(255,255,255,.05)}
.hm-sub-rules-toggle{display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;padding:0;border-radius:50%;background:rgba(96,165,250,.12);border:1px solid rgba(96,165,250,.3);color:#60a5fa;cursor:pointer;transition:all .2s;flex-shrink:0;line-height:1}
.hm-sub-rules-toggle:hover{background:rgba(96,165,250,.25);border-color:rgba(96,165,250,.55);box-shadow:0 0 8px rgba(96,165,250,.3)}
.hm-sub-rules-toggle.active{background:rgba(59,130,246,.25);border-color:rgba(59,130,246,.55);box-shadow:0 0 8px rgba(59,130,246,.35)}
.hm-sub-rules-panel{display:flex;flex-direction:row;gap:46px;padding:14px 56px;border:1px dashed rgba(96,165,250,.28);border-radius:10px;background:rgba(59,130,246,.04);animation:hm-fade-in .3s ease}
.hm-sub-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px}
.hm-inline-empty{display:flex;align-items:center;justify-content:center;min-height:72px;border:1px dashed rgba(255,255,255,.12);border-radius:8px;color:rgba(255,255,255,.42);font-size:12px}
.hm-sub-card{background:var(--cb,rgba(255,255,255,.03));border:1px solid rgba(255,255,255,.08);border-radius:10px;padding:12px;cursor:pointer;transition:all .3s cubic-bezier(.16,1,.3,1);position:relative;overflow:hidden;opacity:0;animation:hm-card-in .5s ease forwards}
.hm-sub-card::before{content:'';position:absolute;top:0;left:0;right:0;height:3px;background:var(--cc,#3b82f6);opacity:.7}
.hm-sub-card:hover{transform:translateY(-4px);border-color:var(--cc,rgba(59,130,246,.4));box-shadow:0 8px 24px rgba(0,0,0,.2),0 0 16px var(--cb,rgba(59,130,246,.1))}
.hm-sub-top{display:flex;flex-flow:row;align-items:center;justify-content:space-between;gap:8px;margin-bottom:5px;min-width:0}
.hm-sub-main{display:flex;flex-flow:row;align-items:flex-start;gap:10px;min-width:0}
.hm-sub-icon{width:34px;height:34px;border-radius:9px;background:rgba(255,255,255,.07);display:flex;align-items:center;justify-content:center;color:rgba(255,255,255,.6);flex-shrink:0}
.hm-sub-info{flex:1;min-width:0}
.hm-sub-score{font-size:18px;font-weight:800;line-height:1;flex-shrink:0}
.hm-sub-name{font-size:13px;color:rgba(255,255,255,.82);font-weight:600;line-height:1.3;margin-bottom:0;flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.hm-sub-tags{display:flex;align-items:center;gap:5px}
.hm-tag-alarm{font-size:10px;color:#f59e0b;padding:2px 6px;background:rgba(245,158,11,.1);border-radius:4px}
.hm-tag-ok{font-size:10px;color:#10b981;padding:2px 6px;background:rgba(16,185,129,.1);border-radius:4px}
.hm-sub-bar{height:4px;background:rgba(255,255,255,.06);border-radius:2px;overflow:hidden;margin-top:10px}
.hm-sub-bar-fill{height:100%;border-radius:2px;transition:width .8s ease}
.hm-conclusion{display:flex;gap:12px;padding:14px;background:rgba(59,130,246,.04);border:1px solid rgba(59,130,246,.12);border-radius:10px}
.hm-conclusion-icon{flex-shrink:0;width:32px;height:32px;border-radius:8px;background:rgba(59,130,246,.12);display:flex;align-items:center;justify-content:center;color:#60a5fa}
.hm-conclusion-text{font-size:13px;line-height:1.7;color:rgba(255,255,255,.7)}

/* ===== Cards Row ===== */
.hm-cards-row{display:grid;grid-template-columns:repeat(2,1fr);gap:14px}
.hm-card-body{padding:14px 16px}
.hm-stat-trio{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-bottom:14px}
.hm-stat-item{display:flex;flex-direction:column;align-items:center;gap:4px;padding:12px 8px;background:rgba(255,255,255,.03);border-radius:10px}
.hm-stat-item-icon{width:36px;height:36px;border-radius:8px;display:flex;align-items:center;justify-content:center;flex-shrink:0;margin-bottom:2px}
.hm-stat-item-label{font-size:11px;color:rgba(255,255,255,.45)}
.hm-stat-item-num{font-size:24px;font-weight:800;color:rgba(255,255,255,.9);line-height:1}
.hm-dispatch-rate{display:flex;align-items:center;gap:10px;margin-top:14px}
.hm-rate-label{font-size:12px;color:rgba(255,255,255,.45);flex-shrink:0}
.hm-rate-track{flex:1;height:6px;background:rgba(255,255,255,.06);border-radius:3px;overflow:hidden}
.hm-rate-fill{height:100%;border-radius:3px;background:linear-gradient(90deg,#3b82f6,#10b981);transition:width .8s ease}
.hm-rate-val{font-size:14px;font-weight:700;color:#10b981;flex-shrink:0}
.hm-collab-chart{width:100%;height:140px}
/* ===== Report Modal ===== */
.hm-report-overlay{position:fixed;inset:0;background:rgba(0,0,0,.5);backdrop-filter:blur(6px);display:flex;align-items:center;justify-content:center;z-index:1002;animation:hm-fade-in .2s ease}
.hm-report-modal{background:rgb(0,66,126);border:1px solid rgba(0,180,255,.15);border-radius:14px;width:90%;max-width:800px;max-height:85vh;display:flex;flex-direction:column;box-shadow:0 20px 60px rgba(0,0,0,.4);animation:hm-scale-in .3s ease}
.hm-report-header{display:flex;justify-content:space-between;align-items:center;padding:16px 20px;border-bottom:1px solid rgba(255,255,255,.06);flex-shrink:0}
.hm-report-header h3{margin:0;font-size:17px;font-weight:700;color:#e8f0fe}
.hm-report-actions{display:flex;align-items:center;gap:8px}
.hm-report-export-btn{display:flex;align-items:center;gap:6px;padding:7px 14px;background:rgba(59,130,246,.12);border:1px solid rgba(59,130,246,.3);border-radius:8px;color:#60a5fa;font-size:13px;cursor:pointer;transition:all .25s}
.hm-report-export-btn:hover:not(:disabled){background:rgba(59,130,246,.22);border-color:rgba(59,130,246,.5);box-shadow:0 0 12px rgba(59,130,246,.3)}
.hm-report-export-btn:disabled{opacity:.4;cursor:not-allowed}
.hm-report-close-btn{background:none;border:none;color:rgba(255,255,255,.35);font-size:20px;cursor:pointer;width:34px;height:34px;border-radius:8px;transition:all .2s;line-height:1;display:flex;align-items:center;justify-content:center}
.hm-report-close-btn:hover{color:#fff;background:rgba(255,255,255,.08)}
.hm-report-body{flex:1;overflow-y:auto;padding:20px 24px}
.hm-report-body::-webkit-scrollbar{width:6px}
.hm-report-body::-webkit-scrollbar-track{background:transparent}
.hm-report-body::-webkit-scrollbar-thumb{background:rgba(255,255,255,.12);border-radius:3px}
.hm-report-loading{display:flex;align-items:center;justify-content:center;min-height:200px;color:rgba(255,255,255,.48);font-size:14px}
.hm-report-error{display:flex;align-items:center;justify-content:center;min-height:200px;color:rgba(255,255,255,.42);font-size:14px}
.hm-report-content{color:rgba(255,255,255,.82);font-size:14px;line-height:1.8}
.hm-report-content :deep(h1){font-size:22px;font-weight:700;color:#e8f0fe;margin:0 0 16px;padding-bottom:8px;border-bottom:1px solid rgba(255,255,255,.08)}
.hm-report-content :deep(h2){font-size:18px;font-weight:600;color:#e8f0fe;margin:20px 0 12px}
.hm-report-content :deep(h3){font-size:16px;font-weight:600;color:rgba(255,255,255,.9);margin:16px 0 10px}
.hm-report-content :deep(p){margin:0 0 12px}
.hm-report-content :deep(ul),.hm-report-content :deep(ol){margin:0 0 12px;padding-left:20px}
.hm-report-content :deep(li){margin-bottom:6px}
.hm-report-content :deep(table){width:100%;border-collapse:collapse;margin:12px 0;font-size:13px}
.hm-report-content :deep(th){background:rgba(255,255,255,.04);padding:8px 10px;text-align:left;color:rgba(255,255,255,.6);font-weight:600;border:1px solid rgba(255,255,255,.08)}
.hm-report-content :deep(td){padding:8px 10px;border:1px solid rgba(255,255,255,.06);color:rgba(255,255,255,.7)}
.hm-report-content :deep(blockquote){border-left:3px solid rgba(59,130,246,.4);padding:8px 14px;margin:0 0 12px;background:rgba(59,130,246,.05);color:rgba(255,255,255,.6);font-style:italic}
.hm-report-content :deep(code){background:rgba(255,255,255,.06);padding:2px 6px;border-radius:4px;font-size:13px;color:#60a5fa}
.hm-report-content :deep(pre){background:rgba(0,0,0,.2);padding:12px 14px;border-radius:8px;overflow-x:auto;margin:0 0 12px}
.hm-report-content :deep(pre code){background:none;padding:0;color:rgba(255,255,255,.8)}
.hm-report-content :deep(strong){color:#e8f0fe;font-weight:600}
.hm-report-content :deep(hr){border:none;border-top:1px solid rgba(255,255,255,.08);margin:16px 0}
/* 报告内 echarts 图表容器 */
.hm-report-content :deep(.hm-report-chart){width:100%;height:320px;margin:12px 0;border-radius:8px;background:rgba(255,255,255,.02);border:1px solid rgba(255,255,255,.06)}
/* 报告内自定义表格 */
.hm-report-content :deep(.hm-report-table-wrap){margin:12px 0;overflow-x:auto}
.hm-report-content :deep(.hm-report-md-table){width:100%;border-collapse:collapse;font-size:13px}
.hm-report-content :deep(.hm-report-md-table th){background:rgba(59,130,246,.1);padding:8px 10px;text-align:left;color:#e8f0fe;font-weight:600;border:1px solid rgba(59,130,246,.2);white-space:nowrap}
.hm-report-content :deep(.hm-report-md-table td){padding:8px 10px;border:1px solid rgba(255,255,255,.06);color:rgba(255,255,255,.7)}
.hm-report-content :deep(.hm-report-md-table tbody tr:hover){background:rgba(255,255,255,.03)}
/* ===== Sub-item Detail Modal ===== */
.hm-sub-overlay{position:fixed;inset:0;background:rgba(0,0,0,.5);backdrop-filter:blur(6px);display:flex;align-items:center;justify-content:center;z-index:1001;animation:hm-fade-in .2s ease}
.hm-sub-modal{background:rgb(0,66,126);border:1px solid rgba(0,180,255,.15);border-radius:14px;width:90%;max-width:560px;max-height:80vh;display:flex;flex-direction:column;box-shadow:0 20px 60px rgba(0,0,0,.4);animation:hm-scale-in .3s ease}
.hm-sub-modal-head{display:flex;align-items:center;gap:10px;padding:16px 20px;border-bottom:1px solid rgba(255,255,255,.06);font-size:15px;font-weight:700;color:#e8f0fe}
.hm-sub-close-x{margin-left:auto;background:none;border:none;color:rgba(255,255,255,.4);cursor:pointer;padding:4px;display:flex;transition:color .2s}
.hm-sub-close-x:hover{color:#fff}
.hm-sub-modal-body{flex:1;overflow-y:auto;padding:18px 20px}
.hm-sub-overview{display:flex;align-items:center;justify-content:space-between;padding:12px 14px;background:rgba(255,255,255,.03);border-radius:10px;margin-bottom:16px}
.hm-ov-score{display:flex;align-items:center;gap:10px}
.hm-ov-label{font-size:13px;color:rgba(255,255,255,.5)}
.hm-ov-num{font-size:22px;font-weight:800}
.hm-ov-meta{display:flex;gap:14px;font-size:13px;color:rgba(255,255,255,.5)}
.orange-text{color:#f59e0b}
.green-text{color:#10b981}
.hm-ded-title{font-size:14px;font-weight:700;color:#e8f0fe;margin:0 0 10px 0}
.hm-ded-card{display:flex;align-items:center;gap:14px;padding:12px 14px;background:rgba(255,255,255,.03);border:1px solid rgba(255,255,255,.06);border-radius:10px;margin-bottom:8px}
.hm-ded-info{flex:1}
.hm-ded-name{font-size:14px;font-weight:600;color:#e8f0fe;margin-bottom:3px}
.hm-ded-id{font-size:12px;color:rgba(255,255,255,.4)}
.hm-ded-rt{flex:1.5;display:flex;flex-direction:column;gap:2px}
.hm-ded-rt-label{font-size:11px;color:rgba(255,255,255,.4)}
.hm-ded-rt-val{font-size:13px;font-weight:600;color:#ff6a00}
.hm-ded-badge{padding:5px 10px;background:rgba(255,106,0,.1);color:#ff6a00;border-radius:6px;font-size:13px;font-weight:700;flex-shrink:0}
.hm-no-ded{display:flex;flex-direction:column;align-items:center;justify-content:center;padding:36px 0;color:rgba(255,255,255,.4)}
.hm-no-ded svg{color:#0caf64;margin-bottom:12px}
.hm-no-ded p{margin:0;font-size:14px}
.hm-sub-modal-foot{padding:14px 20px;border-top:1px solid rgba(255,255,255,.06);display:flex;justify-content:flex-end}
.hm-sub-close-btn{padding:8px 24px;border:1px solid rgba(255,255,255,.15);border-radius:8px;background:transparent;color:#e8f0fe;font-size:14px;cursor:pointer;transition:all .2s}
.hm-sub-close-btn:hover{background:#3b82f6;color:#fff;border-color:#3b82f6}
/* 客流拥堵区域 */
.hm-passenger-section{margin-top:16px}
/* 分组区域人数 */
.hm-group-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(140px,1fr));gap:8px}
.hm-group-item{display:flex;align-items:center;justify-content:space-between;padding:8px 12px;background:rgba(255,255,255,.03);border:1px solid rgba(255,255,255,.06);border-radius:8px}
.hm-group-name{font-size:12px;color:rgba(255,255,255,.6)}
.hm-group-peo{font-size:14px;font-weight:700;color:#60a5fa}
/* 累计进出站统计 */
.hm-inout-row{display:flex;gap:12px}
.hm-inout-card{flex:1;display:flex;align-items:center;gap:12px;padding:14px 16px;border-radius:10px;border:1px solid}
.hm-inout-entry{background:rgba(16,185,129,.06);border-color:rgba(16,185,129,.15)}
.hm-inout-exit{background:rgba(59,130,246,.06);border-color:rgba(59,130,246,.15)}
.hm-inout-icon{width:36px;height:36px;border-radius:8px;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.hm-inout-entry .hm-inout-icon{background:rgba(16,185,129,.12);color:#10b981}
.hm-inout-exit .hm-inout-icon{background:rgba(59,130,246,.12);color:#60a5fa}
.hm-inout-info{display:flex;flex-direction:column;gap:2px}
.hm-inout-label{font-size:12px;color:rgba(255,255,255,.5);font-weight:600}
.hm-inout-value{font-size:22px;font-weight:800;color:#e8f0fe}
/* 运行水平判定规则说明 */
.hm-rule-group{display:flex;flex-direction:column;gap:6px}
.hm-rule-group-title{font-size:12px;font-weight:700;color:rgba(255,255,255,.5);margin-bottom:2px}
.hm-rule-item{display:flex;align-items:center;gap:8px;font-size:12px;color:rgba(255,255,255,.65);line-height:1.5;padding:4px 0}
.hm-rule-item b{color:#93c5fd;font-weight:700}
.hm-rule-dot{width:6px;height:6px;border-radius:50%;flex-shrink:0}
.hm-dot-green{background:#10b981}
.hm-dot-orange{background:#f59e0b}
.hm-dot-blue{background:#60a5fa}
.hm-rule-level{font-weight:700;padding:1px 6px;border-radius:4px;font-size:11px;margin-left:2px}
.hm-l-normal{background:rgba(16,185,129,.12);color:#10b981}
.hm-l-attention{background:rgba(245,158,11,.12);color:#f59e0b}
.hm-l-urgent{background:rgba(239,68,68,.12);color:#ef4444}
.hm-rule-thresholds{display:flex;flex-wrap:wrap;gap:8px;margin-top:6px;padding-top:8px;border-top:1px dashed rgba(255,255,255,.06)}
.hm-rule-thr{font-size:11px;font-weight:600;padding:3px 10px;border-radius:6px}
.hm-thr-green{background:rgba(16,185,129,.08);color:#10b981}
.hm-thr-orange{background:rgba(245,158,11,.08);color:#f59e0b}
.hm-thr-red{background:rgba(239,68,68,.08);color:#ef4444}
.hm-export-btn:focus-visible,.hm-close-btn:focus-visible,.hm-sub-close-x:focus-visible,.hm-sub-close-btn:focus-visible,.hm-sub-card:focus-visible,.hm-sub-rules-toggle:focus-visible{outline:2px solid #93c5fd;outline-offset:2px}
.hm-fade-1{opacity:0;animation:hm-fade-up .5s ease .1s forwards}
.hm-fade-2{opacity:0;animation:hm-fade-up .5s ease .25s forwards}
@keyframes hm-fade-in{from{opacity:0}to{opacity:1}}
@keyframes hm-scale-in{from{opacity:0;transform:scale(.95) translateY(10px)}to{opacity:1;transform:scale(1) translateY(0)}}
@keyframes hm-card-in{from{opacity:0;transform:translateY(12px)}to{opacity:1;transform:translateY(0)}}
@keyframes hm-fade-up{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:translateY(0)}}
@media (max-width:900px){.hm-modal{width:94vw;max-width:94vw}.hm-score-row{flex-direction:column}.hm-score-block{flex-basis:auto}.hm-cards-row{grid-template-columns:1fr}.hm-trend-block{min-width:0}.hm-sub-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media (max-width:560px){.hm-header{padding:12px 14px}.hm-body{padding:12px}.hm-header h3{font-size:16px}.hm-export-btn{padding:7px 9px}.hm-score-row{padding:14px}.hm-score-block{align-items:flex-start}.hm-ring-wrap{width:104px;height:104px;flex-basis:104px}.hm-score-num{font-size:25px}.hm-sub-grid{grid-template-columns:1fr}.hm-ov-meta{flex-direction:column;gap:4px}.hm-ded-card{align-items:flex-start}}
@media (prefers-reduced-motion:reduce){.hm-overlay,.hm-modal,.hm-header-icon,.hm-sub-card,.hm-fade-1,.hm-fade-2{animation:none!important;opacity:1}.hm-ring-progress,.hm-sub-bar-fill,.hm-rate-fill{transition:none!important}}
</style>
