<script setup>
import { ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'

const emit = defineEmits(['close'])

const isWarningModalOpen = ref(true)
const warningKpi = ref({
  total: 156,
  today: 23,
  pending: 18,
  pendingTimeout: 3,
  pendingUrgent: 5,
  pendingImportant: 8,
  pendingNormal: 5,
  resolved: 138,
  aiResolved: 89,
  manualResolved: 49
})
const warningFilter = ref({ time: 'week', level: 'all', category: 'all', device: 'all' })
const warningDevices = ['3号水泵', '排风机2#', '配电柜B', '1号冷水机组', '电梯3#']
const warningList = ref([
  { device: '3号水泵', level: 'important', levelText: '重要', content: '轴承振动达3.5mm/s，接近阈值4.0mm/s', location: '检票口B区', time: '14:32', status: 'pending', statusText: '待处理' },
  { device: '排风机2#', level: 'urgent', levelText: '紧急', content: '电机温度78°C，接近上限85°C', location: '候车厅B区', time: '13:15', status: 'pending', statusText: '待处理' },
  { device: '配电柜B', level: 'normal', levelText: '一般', content: '电压波动395V±8V，超出正常范围', location: '配电室', time: '12:48', status: 'processing', statusText: '处理中' },
  { device: '1号冷水机组', level: 'important', levelText: '重要', content: '压缩机运行电流145A，接近上限150A', location: '制冷机房', time: '11:20', status: 'pending', statusText: '待处理' },
  { device: '电梯3#', level: 'normal', levelText: '一般', content: '钢丝绳磨损度82%，建议近期更换', location: '东侧电梯', time: '10:05', status: 'resolved', statusText: '已处理' },
  { device: '候车厅B区', level: 'important', levelText: '重要', content: '温度28.5°C，接近舒适上限30°C', location: '候车厅B区', time: '09:30', status: 'processing', statusText: '处理中' },
])
const warningAiStats = ref({ dedup: 12, recover: 8, predict: 3, upgrade: 2, effect: 5 })
const warningStats = ref({ processRate: 88.5, avgTime: 2.3 })
let warningTotalTrendRef = ref(null)
let warningTotalTrendChart = null
let warningChartRef = ref(null)
let warningChart = null

const schema = [
  { name: 'date', index: 0, text: '日期' },
  { name: 'quanzhan', index: 1, text: '全站' },
  { name: 'houchetinA', index: 2, text: '候车厅A区' },
  { name: 'houchetinB', index: 3, text: '候车厅B区' },
  { name: 'houchetinC', index: 4, text: '候车厅C区' },
  { name: 'houchetinD', index: 5, text: '候车厅D区' },
  { name: 'jifang', index: 6, text: '设备机房' },
  { name: 'jianpiao', index: 7, text: '检票大厅' },
  { name: 'tingchechang', index: 8, text: '停车场' }
]

const dataBJ = [
  [1, 55, 9, 56, 0.46, 18, 6, 42, 28],
  [2, 25, 11, 21, 0.65, 34, 9, 38, 22],
  [3, 56, 7, 63, 0.3, 14, 5, 45, 32],
  [4, 33, 7, 29, 0.33, 16, 6, 35, 18],
  [5, 42, 24, 44, 0.76, 40, 16, 52, 35],
  [6, 82, 58, 90, 1.77, 68, 33, 68, 55],
  [7, 74, 49, 77, 1.46, 48, 27, 61, 48],
  [8, 78, 55, 80, 1.29, 59, 29, 65, 52],
  [9, 267, 216, 280, 4.8, 108, 64, 185, 142],
  [10, 185, 127, 216, 2.52, 61, 27, 142, 108],
  [11, 39, 19, 38, 0.57, 31, 15, 32, 22],
  [12, 41, 11, 40, 0.43, 21, 7, 28, 18],
  [13, 64, 38, 74, 1.04, 46, 22, 55, 38],
  [14, 108, 79, 120, 1.7, 75, 41, 88, 72],
  [15, 108, 63, 116, 1.48, 44, 26, 82, 65],
  [16, 33, 6, 29, 0.34, 13, 5, 30, 20],
  [17, 94, 66, 110, 1.54, 62, 31, 75, 58],
  [18, 186, 142, 192, 3.88, 93, 79, 156, 125],
  [19, 57, 31, 54, 0.96, 32, 14, 48, 35],
  [20, 22, 8, 17, 0.48, 23, 10, 25, 15],
  [21, 39, 15, 36, 0.61, 29, 13, 33, 22],
  [22, 94, 69, 114, 2.08, 73, 39, 78, 62],
  [23, 99, 73, 110, 2.43, 76, 48, 82, 65],
  [24, 31, 12, 30, 0.5, 32, 16, 28, 18],
  [25, 42, 27, 43, 1, 53, 22, 38, 28],
  [26, 154, 117, 157, 3.05, 92, 58, 128, 98],
  [27, 234, 185, 230, 4.09, 123, 69, 195, 155],
  [28, 160, 120, 186, 2.77, 91, 50, 135, 105],
  [29, 134, 96, 165, 2.76, 83, 41, 112, 88],
  [30, 52, 24, 60, 1.03, 50, 21, 45, 32],
  [31, 46, 5, 49, 0.28, 10, 6, 38, 25]
]

const dataSH = [
  [1, 91, 45, 125, 0.82, 34, 23, 72, 55],
  [2, 65, 27, 78, 0.86, 45, 29, 58, 42],
  [3, 83, 60, 84, 1.09, 73, 27, 65, 48],
  [4, 109, 81, 121, 1.28, 68, 51, 88, 72],
  [5, 106, 77, 114, 1.07, 55, 51, 85, 68],
  [6, 109, 81, 121, 1.28, 68, 51, 88, 72],
  [7, 106, 77, 114, 1.07, 55, 51, 85, 68],
  [8, 89, 65, 78, 0.86, 51, 26, 68, 52],
  [9, 53, 33, 47, 0.64, 50, 17, 42, 32],
  [10, 80, 55, 80, 1.01, 75, 24, 62, 48],
  [11, 117, 81, 124, 1.03, 45, 24, 92, 75],
  [12, 99, 71, 142, 1.1, 62, 42, 78, 62],
  [13, 95, 69, 130, 1.28, 74, 50, 75, 58],
  [14, 116, 87, 131, 1.47, 84, 40, 95, 78],
  [15, 108, 80, 121, 1.3, 85, 37, 88, 72],
  [16, 134, 83, 167, 1.16, 57, 43, 108, 88],
  [17, 79, 43, 107, 1.05, 59, 37, 62, 48],
  [18, 71, 46, 89, 0.86, 64, 25, 55, 42],
  [19, 97, 71, 113, 1.17, 88, 31, 78, 62],
  [20, 84, 57, 91, 0.85, 55, 31, 65, 52],
  [21, 87, 63, 101, 0.9, 56, 41, 68, 55],
  [22, 104, 77, 119, 1.09, 73, 48, 85, 68],
  [23, 87, 62, 100, 1, 72, 28, 68, 52],
  [24, 168, 128, 172, 1.49, 97, 56, 138, 112],
  [25, 65, 45, 51, 0.74, 39, 17, 52, 38],
  [26, 39, 24, 38, 0.61, 47, 17, 32, 22],
  [27, 39, 24, 39, 0.59, 50, 19, 32, 25],
  [28, 93, 68, 96, 1.05, 79, 29, 72, 55],
  [29, 188, 143, 197, 1.66, 99, 51, 152, 125],
  [30, 174, 131, 174, 1.55, 108, 50, 142, 115],
  [31, 187, 143, 201, 1.39, 89, 53, 155, 128]
]

const dataGZ = [
  [1, 26, 37, 27, 1.163, 27, 13, 22, 15],
  [2, 85, 62, 71, 1.195, 60, 8, 68, 52],
  [3, 78, 38, 74, 1.363, 37, 7, 62, 48],
  [4, 21, 21, 36, 0.634, 40, 9, 18, 12],
  [5, 41, 42, 46, 0.915, 81, 13, 35, 25],
  [6, 56, 52, 69, 1.067, 92, 16, 48, 35],
  [7, 64, 30, 28, 0.924, 51, 2, 52, 38],
  [8, 55, 48, 74, 1.236, 75, 26, 45, 32],
  [9, 76, 85, 113, 1.237, 114, 27, 62, 48],
  [10, 91, 81, 104, 1.041, 56, 40, 75, 58],
  [11, 84, 39, 60, 0.964, 25, 11, 68, 52],
  [12, 64, 51, 101, 0.862, 58, 23, 52, 38],
  [13, 70, 69, 120, 1.198, 65, 36, 58, 45],
  [14, 77, 105, 178, 2.549, 64, 16, 65, 52],
  [15, 109, 68, 87, 0.996, 74, 29, 88, 72],
  [16, 73, 68, 97, 0.905, 51, 34, 58, 45],
  [17, 54, 27, 47, 0.592, 53, 12, 42, 32],
  [18, 51, 61, 97, 0.811, 65, 19, 42, 32],
  [19, 91, 71, 121, 1.374, 43, 18, 75, 58],
  [20, 73, 102, 182, 2.787, 44, 19, 62, 48],
  [21, 73, 50, 76, 0.717, 31, 20, 58, 45],
  [22, 84, 94, 140, 2.238, 68, 18, 68, 52],
  [23, 93, 77, 104, 1.165, 53, 7, 75, 58],
  [24, 99, 130, 227, 3.97, 55, 15, 82, 65],
  [25, 146, 84, 139, 1.094, 40, 17, 118, 95],
  [26, 113, 108, 137, 1.481, 48, 15, 92, 75],
  [27, 81, 48, 62, 1.619, 26, 3, 65, 48],
  [28, 56, 48, 68, 1.336, 37, 9, 45, 35],
  [29, 82, 92, 174, 3.29, 0, 13, 68, 52],
  [30, 106, 116, 188, 3.628, 101, 16, 88, 72],
  [31, 118, 50, 0, 1.383, 76, 11, 95, 78]
]

function initWarningChart() {
  const container = warningChartRef.value
  if (!container) return
  if (warningChart) warningChart.dispose()
  warningChart = echarts.init(container)

  const isDark = document.documentElement.getAttribute('data-theme') === 'dark'
  const bgColor = isDark ? '#0d2137' : '#fbfdff'
  const textColor = isDark ? '#e0f0ff' : '#07153b'
  const mutedColor = isDark ? 'white' : '#526383'
  const lineColor = isDark ? '#1a3a5c' : '#d8e5f7'

  const lineStyle = { width: 1, opacity: 0.5 }

  const option = {
    backgroundColor: bgColor,
    legend: {
      right: 10,
      top: 'center',
      orient: 'vertical',
      data: ['紧急', '重要', '一般'],
      itemGap: 20,
      textStyle: { color: textColor, fontSize: 14 }
    },
    tooltip: {
      padding: 10,
      backgroundColor: isDark ? '#1a3a5c' : '#ffffff',
      borderColor: lineColor,
      borderWidth: 1,
      textStyle: { color: textColor },
      formatter: function(params) {
        if (!params.data) return ''
        const data = params.data
        const dayNum = data[0]
        const now = new Date()
        const month = now.getMonth() + 1
        const day = dayNum
        const dateStr = `${month}月${day}日`
        const quanzhan = data[1]
        const houchetinA = data[2]
        const houchetinB = data[3]
        const houchetinC = data[4]
        const houchetinD = data[5]
        const jifang = data[6]
        const jianpiao = data[7]
        const tingchechang = data[8]
        return `
          <div style="font-weight:600;margin-bottom:6px;">${params.seriesName} - ${dateStr}</div>
          <div>全站: ${quanzhan}</div>
          <div>候车厅A区: ${houchetinA}</div>
          <div>候车厅B区: ${houchetinB}</div>
          <div>候车厅C区: ${houchetinC}</div>
          <div>候车厅D区: ${houchetinD}</div>
          <div>设备机房: ${jifang}</div>
          <div>检票大厅: ${jianpiao}</div>
          <div>停车场: ${tingchechang}</div>
        `
      }
    },
    parallelAxis: [
      { dim: 0, name: schema[0].text, inverse: true, max: 31, nameLocation: 'start' },
      { dim: 1, name: schema[1].text },
      { dim: 2, name: schema[2].text },
      { dim: 3, name: schema[3].text },
      { dim: 4, name: schema[4].text },
      { dim: 5, name: schema[5].text },
      { dim: 6, name: schema[6].text },
      { dim: 7, name: schema[7].text },
      { dim: 8, name: schema[8].text }
    ],
    visualMap: {
      show: true,
      min: 0,
      max: 150,
      dimension: 2,
      inRange: { color: ['#d94e5d', '#eac736', '#50a3ba'].reverse() }
    },
    parallel: {
      left: '5%',
      right: '10%',
      bottom: 20,
      parallelAxisDefault: {
        type: 'value',
        name: 'AQI指数',
        nameLocation: 'end',
        nameGap: 20,
        nameTextStyle: { color: textColor, fontSize: 12 },
        axisLine: { lineStyle: { color: lineColor } },
        axisTick: { lineStyle: { color: mutedColor } },
        splitLine: { show: false },
        axisLabel: { color: textColor }
      }
    },
    series: [
      { name: '紧急', type: 'parallel', lineStyle: lineStyle, data: dataBJ },
      { name: '重要', type: 'parallel', lineStyle: lineStyle, data: dataSH },
      { name: '一般', type: 'parallel', lineStyle: lineStyle, data: dataGZ }
    ]
  }

  warningChart.setOption(option)
}

function initWarningTotalTrend() {
  const container = warningTotalTrendRef.value
  if (!container) return
  if (warningTotalTrendChart) warningTotalTrendChart.dispose()
  warningTotalTrendChart = echarts.init(container)
  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      textStyle: { color: '#07153b', fontSize: 12 },
      formatter: function(params) {
        const p = params[0]
        const dates = []
        const now = new Date()
        for (let i = 6; i >= 0; i--) {
          const d = new Date(now - i * 86400000)
          dates.push(`${d.getMonth() + 1}/${d.getDate()}`)
        }
        return `${dates[p.dataIndex]}<br/><b>${p.value}条</b>`
      }
    },
    grid: { top: 4, bottom: 4, left: 0, right: 0 },
    xAxis: { type: 'category', show: false, data: ['D1', 'D2', 'D3', 'D4', 'D5', 'D6', 'D7'] },
    yAxis: { type: 'value', show: false },
    series: [{
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 0,
      emphasis: { symbolSize: 6 },
      lineStyle: { width: 1.5, color: '#1268ff' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(18,104,255,0.2)' },
          { offset: 1, color: 'rgba(18,104,255,0.02)' }
        ])
      },
      data: [18, 22, 15, 20, 25, 19, 23]
    }]
  }
  warningTotalTrendChart.setOption(option)
}

function closeWarningModal() {
  if (warningTotalTrendChart) { warningTotalTrendChart.dispose(); warningTotalTrendChart = null }
  if (warningChart) { warningChart.dispose(); warningChart = null }
  emit('close')
}

function handleBatchConfirm() {
  alert('已确认全部待处理事项')
}

function handleAiDedup() {
  alert(`已合并 ${warningAiStats.value.dedup} 条重复告警`)
}

function handleAutoRecover() {
  alert(`已自动恢复 ${warningAiStats.value.recover} 条告警`)
}

function handlePredictiveMaint() {
  alert(`已生成 ${warningAiStats.value.predict} 条预测维护工单`)
}

function handleSmartUpgrade() {
  alert(`已升级 ${warningAiStats.value.upgrade} 条超时事项`)
}

function handleEffectCheck() {
  alert(`已检验 ${warningAiStats.value.effect} 条维修效果`)
}

function handleSingleAnalyze(item) {
  alert(`AI分析：${item.device} - ${item.content}`)
}

function handleWarningDispatch(item) {
  alert(`已为 ${item.device} 创建维修工单`)
}

onMounted(() => {
  nextTick(() => {
    setTimeout(() => {
      initWarningTotalTrend()
      initWarningChart()
    }, 100)
  })
})
</script>

<template>
  <div class="warning-modal-overlay" @click.self="closeWarningModal">
    <div class="warning-modal">
      <div class="warning-modal-header">
        <h3 class="warning-modal-title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
            <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
          </svg>
          事项处理
        </h3>
        <button class="warning-modal-close" @click="closeWarningModal">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
            <path d="M18 6L6 18M6 6l12 12"/>
          </svg>
        </button>
      </div>

      <div class="warning-modal-content">
        <div class="warning-kpi-row">
          <div class="warning-kpi-card ai-stat-card">
            <div class="kpi-label">智能处理</div>
          <div class="ai-actions-row">
            <button class="ai-action-btn" @click="handleAiDedup">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
              告警去重
              <span class="ai-badge" v-if="warningAiStats.dedup > 0">{{ warningAiStats.dedup }}条</span>
            </button>
            <button class="ai-action-btn" @click="handleAutoRecover">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
              自动恢复
              <span class="ai-badge" v-if="warningAiStats.recover > 0">{{ warningAiStats.recover }}条</span>
            </button>
            <button class="ai-action-btn" @click="handleSmartUpgrade">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                <polyline points="17 1 21 5 17 9"/>
                <path d="M3 11V9a4 4 0 0 1 4-4h14"/>
                <polyline points="7 23 3 19 7 15"/>
                <path d="M21 13v2a4 4 0 0 1-4 4H3"/>
              </svg>
              智能升级
              <span class="ai-badge" v-if="warningAiStats.upgrade > 0">{{ warningAiStats.upgrade }}条</span>
            </button>
            <button class="ai-action-btn" @click="handleEffectCheck">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                <path d="M9 11l3 3L22 4"/>
                <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
              </svg>
              效果检验
              <span class="ai-badge" v-if="warningAiStats.effect > 0">{{ warningAiStats.effect }}条</span>
            </button>
          </div>
          </div>
          <div class="warning-kpi-card total-card">
            <div class="kpi-label">事项总数</div>
            <div class="kpi-value total-value">{{ warningKpi.total }}<span class="kpi-today-inline">/{{ warningKpi.today }}条</span></div>
            <div ref="warningTotalTrendRef" class="kpi-sparkline"></div>
          </div>
          <div class="warning-kpi-card pending-card">
            <div class="kpi-label">待处理</div>
            <div class="kpi-value pending-value">{{ warningKpi.pending }}<span class="kpi-sub">/{{ warningKpi.pendingTimeout }}超时</span></div>
            <div class="kpi-level-breakdown">
              <span class="level-urgent-tag">紧急 {{ warningKpi.pendingUrgent }}</span>
              <span class="level-important-tag">重要 {{ warningKpi.pendingImportant }}</span>
              <span class="level-normal-tag">一般 {{ warningKpi.pendingNormal }}</span>
            </div>
          </div>
          <div class="warning-kpi-card resolved-card">
            <div class="kpi-label">已处理</div>
            <div class="kpi-value resolved-value">{{ warningKpi.resolved }}</div>
            <div class="kpi-breakdown">
              <span class="ai-count">AI: {{ warningKpi.aiResolved }}</span>
              <span class="manual-count">人工: {{ warningKpi.manualResolved }}</span>
            </div>
            <div class="kpi-efficiency">
              <span>处理率 <b class="rate-value">{{ warningStats.processRate }}%</b></span>
              <span>平均 <b class="time-value">{{ warningStats.avgTime }}h</b></span>
            </div>
          </div>
        </div>

        <div class="warning-body-row">
        <div class="warning-ai-section">
          <div class="ai-section-header">
            <div class="ai-orb small">AI</div>
            <span class="ai-section-title">事项分析</span>
          </div>

          <div class="ai-analysis-content">
            <p class="ai-analysis-text">今日事项总数较昨日下降12%，AI自动处理占比64.5%。当前18条超时待处理事项建议立即派单处理。预测性维护已生成3条工单，可有效避免设备故障升级。</p>
          </div>
        </div>

        <div class="warning-list-panel">
          <div class="warning-list-header">
            <h4 class="panel-title">事项列表
            <span class="list-count">{{ warningList.length }}条</span></h4>
            <span class="list-count">更多</span>
          </div>
          <div class="warning-list">
            <div v-for="(item, idx) in warningList" :key="idx" :class="['warning-item', `level-${item.level}`]">
              <div class="warning-item-left">
                <div class="warning-item-header">
                  <span class="warning-device">{{ item.device }}</span>
                  <span :class="['warning-level-badge', `badge-${item.level}`]">{{ item.levelText }}</span>
                </div>
                <div class="warning-item-content">{{ item.content }}</div>
                <div class="warning-item-meta">
                  <span class="warning-location">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
                      <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                      <circle cx="12" cy="10" r="3"/>
                    </svg>
                    {{ item.location }}
                  </span>
                  <span class="warning-time">{{ item.time }}</span>
                  <span :class="['warning-status', `status-${item.status}`]">{{ item.statusText }}</span>
                </div>
              </div>
              <div class="warning-item-right">
                <button class="warning-action-btn dispatch-btn" @click="handleWarningDispatch(item)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                  </svg>
                  派单
                </button>
              </div>
            </div>
          </div>
        </div>
        </div>

        <div class="warning-chart-section">
          <div class="chart-header">
            <h4 class="panel-title">事项趋势</h4>
          </div>
          <div ref="warningChartRef" class="warning-chart"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.warning-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.warning-modal {
  width: 96%;
  max-width: 1200px;
  max-height: 95vh;
  background: var(--bg);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.warning-modal-header {
  padding: 20px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: url('/static/titile-bg-center-1.png') center center / contain no-repeat;
}

.warning-modal-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
  margin: 0;
}

.warning-modal-close {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: var(--text);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.warning-modal-close:hover {
  background: #f3f4f6;
  color: #1268ff;
}

.warning-modal-content {
  padding: 20px 24px;
  overflow-y: auto;
  flex: 1;
}

.warning-kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}

.warning-kpi-card {
  background: #021631;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 12px 10px;
  text-align: center;
}

.ai-stat-card {
  background: linear-gradient(135deg, rgba(18, 104, 255, 0.06), rgba(139, 92, 246, 0.06));
  border-color: rgba(18, 104, 255, 0.15);
}

.ai-value {
  color: #1268ff;
}

.kpi-label {
  font-size: 12px;
  color: #526383;
  margin-bottom: 6px;
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text);
}

.total-value {
  font-size: 26px;
}

.kpi-today-inline {
  font-size: 14px;
  font-weight: 400;
  color: #1268ff;
}

.kpi-sparkline {
  width: 100%;
  height: 30px;
  margin-top: 4px;
}

.kpi-today {
  font-size: 12px;
  color: #526383;
  margin-top: 4px;
}

.kpi-today b {
  color: #1268ff;
}

.pending-card { border-color: #ff6a00; }
.pending-value { color: #ff6a00; }

.kpi-sub {
  font-size: 13px;
  font-weight: 400;
  color: #ff4d4f;
}

.resolved-card { border-color: #0caf64; }
.resolved-value { color: #0caf64; }

.kpi-breakdown {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 4px;
  font-size: 11px;
}

.ai-count { color: #8b5cf6; }
.manual-count { color: #526383; }

.kpi-level-breakdown {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 8px;
}

.level-urgent-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(255, 77, 79, 0.1);
  color: #ff4d4f;
}

.level-important-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(255, 147, 0, 0.1);
  color: #ff9300;
}

.level-normal-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(18, 104, 255, 0.1);
  color: #1268ff;
}

.kpi-efficiency {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 8px;
  font-size: 11px;
  color: #526383;
}

.kpi-efficiency b {
  font-weight: 700;
}

.kpi-efficiency .rate-value { color: #0caf64; }
.kpi-efficiency .time-value { color: #1268ff; }

.warning-filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
}

.filter-group {
  display: flex;
  gap: 10px;
}

.warning-filter-select {
  padding: 8px 32px 8px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--bg);
  color: var(--text);
  font-size: 13px;
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%23526383' stroke-width='2'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
  transition: all 0.2s;
}

.warning-filter-select:hover {
  border-color: #1268ff;
}

.warning-confirm-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  background: linear-gradient(135deg, #1268ff, #69b1ff);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.warning-confirm-btn:hover {
  box-shadow: 0 4px 12px rgba(18, 104, 255, 0.4);
  transform: translateY(-1px);
}

.warning-ai-section {
  background: linear-gradient(135deg, rgba(18, 104, 255, 0.05), rgba(139, 92, 246, 0.05));
  border: 1px solid rgba(18, 104, 255, 0.15);
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 16px;
}

.ai-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.ai-section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
}

.ai-actions-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: center;
}

.ai-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  background: rgba(18, 104, 255, 0.08);
  border: 1px solid rgba(18, 104, 255, 0.2);
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #1268ff;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.ai-action-btn:hover {
  background: rgba(18, 104, 255, 0.15);
  border-color: #1268ff;
  box-shadow: 0 2px 6px rgba(18, 104, 255, 0.15);
  transform: translateY(-1px);
}

.ai-badge {
  background: #1268ff;
  color: #fff;
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 8px;
  min-width: 18px;
  text-align: center;
}

.ai-stats-summary {
  font-size: 12px;
  color: #526383;
  padding-top: 8px;
  border-top: 1px dashed rgba(18, 104, 255, 0.2);
}

.ai-stats-summary b {
  color: #1268ff;
}

.ai-analysis-content {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed rgba(18, 104, 255, 0.2);
}

.ai-analysis-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 6px 0;
}

.ai-analysis-text {
  font-size: 12px;
  color: #526383;
  line-height: 1.7;
  margin: 0;
}

.warning-ai-section {
  flex: 0 0 380px;
}

.warning-list-panel {
  flex: 1;
  min-width: 0;
  margin-left: 20px;
}

.warning-body-row {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  max-height: 150px;
}

.warning-chart-section {
  margin-top: 20px;
  padding: 16px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 10px;
}

.chart-header {
  display: none;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.chart-legend {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--muted);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.warning-chart {
  width: 100%;
  height: 400px;
}

.warning-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
  margin: 0;
}

.list-count {
  margin-left: 10px;
  font-size: 14px;
}

.warning-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 130px;
  overflow-y: hidden;
}

.warning-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 12px 14px;
  border-left: 0.5px solid #1268ff;
  transition: all 0.2s;
}

.warning-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.level-urgent { border-left-color: #ff4d4f; }
.level-important { border-left-color: #ff9300; }
.level-normal { border-left-color: #1268ff; }

.warning-item-left {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.warning-item-left .warning-item-header {
  margin-bottom: 0;
  flex-shrink: 0;
}

.warning-item-left .warning-item-content {
  margin-bottom: 0;
  flex-shrink: 0;
}

.warning-item-left .warning-item-meta {
  margin-bottom: 0;
  margin-left: auto;
}

.warning-item-right {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.warning-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.warning-device {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.warning-level-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 600;
}

.badge-urgent { background: rgba(255, 77, 79, 0.1); color: #ff4d4f; }
.badge-important { background: rgba(255, 147, 0, 0.1); color: #ff9300; }
.badge-normal { background: rgba(18, 104, 255, 0.1); color: #1268ff; }

.warning-item-content {
  font-size: 13px;
  color: var(--text);
  margin-bottom: 8px;
  line-height: 1.5;
}

.warning-item-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 11px;
  color: #526383;
  margin-bottom: 8px;
}

.warning-location {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.warning-status {
  margin-left: auto;
  font-weight: 600;
}

.status-pending { color: #ff6a00; }
.status-processing { color: #1268ff; }
.status-resolved { color: #0caf64; }

.warning-item-actions {
  display: flex;
  gap: 8px;
}

.warning-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: transparent;
  font-size: 12px;
  color: var(--text);
  cursor: pointer;
  transition: all 0.2s;
}

.warning-action-btn:hover {
  border-color: #1268ff;
  color: #1268ff;
}
</style>