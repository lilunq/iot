<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import 'echarts-gl'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls'

const router = useRouter()

const isAssistantOpen = ref(false)
const assistantInput = ref('')
const assistantMessagesEl = ref(null)
const assistantMessages = ref([
  { role: 'bot', text: "您好！我是智慧助手，请问有什么可以帮助您的？" }
])
const assistantReplyMessage = "收到您的问题，我正在为您分析..."

const isDarkTheme = ref(document.documentElement.getAttribute('data-theme') === 'dark')
const headerBgImage = computed(() => {
  return isDarkTheme.value ? '/dashboard/static/head.png' : '/dashboard/static/head2.png'
})
const sparklineChartRef = ref(null)
let sparklineChart = null
const energyChartRef = ref(null)
let energyChart = null
const isModalOpen = ref(false)
const selectedZone = ref(null)
const isDispatchModalOpen = ref(false)
const dispatchModalType = ref('repair') // 'repair' or 'check'
const dispatchOrder = ref({
  deviceName: '',
  deviceId: '',
  deviceType: '',
  deviceLocation: '',
  orderNumber: '',
  faultReason: '',
  repairSteps: '',
  recommendedPerson: null,
  notes: ''
})
const recommendedPerson = ref(null)
const dispatchFaultText = ref('')
const dispatchStepsText = ref('')
const dispatchFaultComplete = ref(false)
const dispatchStepsComplete = ref(false)
let dispatchFaultTimer = null
let dispatchStepsTimer = null

// 设备控制弹窗
const isDeviceControlModalOpen = ref(false)
const deviceControlInfo = ref({
  deviceName: '',
  deviceId: '',
  deviceType: '',
  action: '',
  status: '下发中...',
  statusClass: 'pending',
  timestamp: '',
  dataLinkStatus: '等待中',
  dataLinkClass: 'waiting',
  showWindEffect: false
})
let deviceControlTimer = null
let deviceCloseTimer = null

// 底座实时监控弹窗
const isBaseMonitorModalOpen = ref(false)

function openBaseMonitorModal() {
  isBaseMonitorModalOpen.value = true
}

function closeBaseMonitorModal() {
  isBaseMonitorModalOpen.value = false
}

// 运维Agent弹窗
const isOpsModalOpen = ref(false)

function openOpsAgent() {
  isOpsModalOpen.value = true
}

function closeOpsModal() {
  isOpsModalOpen.value = false
}

// 安全指数弹窗
const isSafetyModalOpen = ref(false)
const initialAnomalyCount = 17 // 3+5+2+1+2+4
const safetyScore = ref(Math.max(0, 100 - initialAnomalyCount))
const anomalyCount = ref(initialAnomalyCount)
const safetyAnomalies = ref({
  deviceFaults: 0,
  deviceAlarms: 0,
  environmentAbnormal: 0,
  energyAbnormal: 0,
  urgentItems: 0,
  importantItems: 0
})

function openSafetyModal() {
  // 计算异常数
  safetyAnomalies.value = {
    deviceFaults: 3,
    deviceAlarms: 5,
    environmentAbnormal: 2,
    energyAbnormal: 1,
    urgentItems: 2,
    importantItems: 4
  }
  
  anomalyCount.value = safetyAnomalies.value.deviceFaults + 
                       safetyAnomalies.value.deviceAlarms + 
                       safetyAnomalies.value.environmentAbnormal + 
                       safetyAnomalies.value.energyAbnormal + 
                       safetyAnomalies.value.urgentItems + 
                       safetyAnomalies.value.importantItems
  
  // 安全指数 = max(0, 100 - 异常数)
  safetyScore.value = Math.max(0, 100 - anomalyCount.value)
  
  isSafetyModalOpen.value = true
}

function closeSafetyModal() {
  isSafetyModalOpen.value = false
}

// 站房整体状态评估弹窗
const isHealthModalOpen = ref(false)
const healthTimeDimension = ref('10min') // '10min', '1h', '1d'
const healthScore = ref(92)
const healthSubItems = ref([
  {
    name: '设备健康',
    score: 88,
    weight: '30%',
    icon: 'gear',
    deductionDevices: [
      { name: '排风机2#', id: 'DEV-B-027', realTimeData: '转速: 0 RPM', deduction: 5 },
      { name: '3号水泵', id: 'DEV-M-003', realTimeData: '振动: 4.8mm/s', deduction: 3 }
    ]
  },
  {
    name: '环境舒适',
    score: 85,
    weight: '25%',
    icon: 'leaf',
    deductionDevices: [
      { name: '候车厅B区空调', id: 'AC-B-001', realTimeData: '设定温度: 26°C', deduction: 4 },
      { name: '机房区温控', id: 'TH-M-002', realTimeData: '温度: 32.6°C', deduction: 3 }
    ]
  },
  {
    name: '预警处置',
    score: 96,
    weight: '20%',
    icon: 'shield',
    deductionDevices: []
  },
  {
    name: '能源消耗',
    score: 90,
    weight: '15%',
    icon: 'bolt',
    deductionDevices: [
      { name: 'B区空调机组', id: 'HVAC-B-001', realTimeData: '功率: 45kW', deduction: 3 }
    ]
  },
  {
    name: '客流压力',
    score: 94,
    weight: '10%',
    icon: 'user',
    deductionDevices: []
  }
])
const healthAnalysisConclusion = ref('站房整体运行健康度92分，处于优良水平。设备健康方面，候车厅B区排风机2#故障停机、设备机房区3号水泵轴承振动偏高，是主要扣分项。环境舒适度受B区温度偏高和机房区散热不足影响。预警处置体系运转良好，无安全事故。能耗方面B区空调满负荷运行导致偏高。客流压力整体可控，B区高峰时段需关注。')

// 分项详情弹窗
const isSubItemModalOpen = ref(false)
const currentSubItem = ref(null)

const heatmapContainer = ref(null)
const heatmapLabels = ref(null)
const modalHeatmapContainer = ref(null)
const modalHeatmapLabels = ref(null)
const healthRadarChartRef = ref(null)
const healthScoreChartRef = ref(null)
const zoneViewMode = ref('model') // 'model' or 'cards'
let heatmapAnimFrame = null
let threeScene = null
let threeCamera = null
let threeRenderer = null
let threeControls = null
let pointsInstances = []
let modalHeatmapAnimFrame = null
let modalThreeScene = null
let modalThreeCamera = null
let modalThreeRenderer = null
let modalThreeControls = null
let modalPointsInstances = []
let healthRadarChart = null
let healthScoreChart = null

// 设备分析弹窗
const isDeviceAnalysisOpen = ref(false)
const deviceAnalysisData = ref({
  totalDevices: 156,
  healthScore: 87,
  normalDevices: 142,
  faultDevices: 5,
  alarmDevices: 6,
  maintenanceDevices: 3,
  anomalyList: [
    { type: 'fault', title: '排风机2# 故障停机', id: 'DEV-B-027', status: '转速0 RPM', action: '维修', location: '候车厅B区', severity: 'high' },
    { type: 'alarm', title: '3号水泵 轴承振动偏高', id: 'DEV-M-003', status: '振动4.8mm/s', action: '排查', location: '检票口B区', severity: 'medium' },
    ],
  maintenanceProgress: [
    { id: 'WO-20240115-001', device: '排风机2#', worker: '张工', progress: 75, status: '进行中' },
    { id: 'WO-20240115-002', device: '3号水泵', worker: '李工', progress: 100, status: '已完成' },
    { id: 'WO-20240115-003', device: '配电柜B', worker: '王工', progress: 45, status: '进行中' },
  ]
})
let deviceHeatmapChartRef = ref(null)
let deviceHeatmapChart = null
const deviceHeatmapData = ref({ dates: [], deviceTypes: [], data: [] })

// 事项处理弹窗
const isWarningModalOpen = ref(false)
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

// 平行坐标图数据
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

function openWarningModal() {
  isWarningModalOpen.value = true
  setTimeout(() => {
    initWarningTotalTrend()
    initWarningChart()
  }, 100)
}

function closeWarningModal() {
  isWarningModalOpen.value = false
  if (warningTotalTrendChart) { warningTotalTrendChart.dispose(); warningTotalTrendChart = null }
  if (warningChart) { warningChart.dispose(); warningChart = null }
}

function initWarningChart() {
  const container = warningChartRef.value
  if (!container) return
  if (warningChart) warningChart.dispose()
  warningChart = echarts.init(container)

  const isDark = isDarkTheme.value
  const bgColor = isDark ? '#0d2137' : '#fbfdff'
  const textColor = isDark ? '#e0f0ff' : '#07153b'
  const mutedColor = isDark ? '#88aacc' : '#526383'
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

const healthTrendData = [48, 38, 50, 42, 40, 48, 36, 42, 31, 39, 32, 36, 28, 34]

const zoneAreas = ref([
  {
    name: '检票口B区',
    heat: { color: '#ff6a00', bgColor: 'rgba(255,106,0,0.15)', pos: { x: 8, y: 0, z: -12 }, type: 'warn', desc: ['能耗异常', '温度偏高', '噪声偏高', '设备故障1台告警1台'] },
    status: '注意',
    statusClass: 'orange-text',
    boxClass: 'orange-box',
    wrapperColor: 'var(--orange)',
    pillClass: 'warn',
    expandedEnv: true,
    expandedDevice: true,
    env: [
      { name: '温度', value: '32.6', unit: '°C', isAbnormal: true },
      { name: '湿度', value: '45', unit: '%', isAbnormal: false },
      { name: '颗粒', value: '38', unit: 'μg/m³', isAbnormal: false },
      { name: '照度', value: '150', unit: 'lx', isAbnormal: false },
      { name: '噪声', value: '65', unit: 'dB', isAbnormal: true },
      { name: '异常气味', value: '无', unit: '', isAbnormal: false }
    ],
    envScore: '71分',
    envStatus: '注意',
    envStatusClass: 'orange-text',
    anomalyEnv: [
      { item: '温度', value: '32.6°C', standard: '≤30°C', status: '偏高', selected: false },
      { item: '噪声', value: '65dB', standard: '≤60dB', status: '偏大', selected: false }
    ],
    deviceStatus: '1台故障 1台告警',
    deviceStatusClass: 'red-text',
    anomalyDevices: [
      {
        id: 'DEV-M-003',
        name: '3号水泵',
        type: '水泵',
        currentData: [
          { label: '振动', value: '4.8 mm/s' },
          { label: '温度', value: '68°C' },
          { label: '电流', value: '12.5 A' }
        ],
        status: '故障',
        location: '设备机房-水泵房',
        selected: false,
        blink: true
      },
      {
        id: 'DEV-M-007',
        name: '配电柜B',
        type: '配电柜',
        currentData: [
          { label: '电压', value: '395 V' },
          { label: '电流', value: '45 A' },
          { label: '温度', value: '55°C' }
        ],
        status: '告警',
        location: '设备机房-配电室',
        selected: false,
        blink: true
      }
    ],
    passengerDensity: '0.08人/㎡',
    passengerStatus: '正常',
    passengerStatusClass: 'green-text',
    energyToday: '185',
    energyStatus: '注意',
    energyStatusClass: 'orange-text',
    suggestion: '温度偏高噪声较大，检修水泵轴承，排查告警设备降低能耗',
    suggestionClass: 'orange-text',
    zoneAnalysis: '设备机房区环境评分71分，温度32.6°C超出标准≤30°C上限，噪声65dB超过≤60dB标准。3号水泵（DEV-M-003）轴承振动达4.8mm/s，温度68°C，存在机械故障风险。配电柜B（DEV-M-007）电压395V偏低，三相负载不平衡，需检查线路。能耗185kWh偏高，与故障设备效率下降有关。',
    anomalyActions: [
      { title: '温度偏高 32.6°C', detail: '标准 ≤30°C，偏差 +2.6°C', type: 'env', buttons: ['巡检', '开启风机'] },
      { title: '噪声偏大 65dB', detail: '标准 ≤60dB，偏差 +5dB', type: 'env', buttons: ['巡检'] },
      { title: '3号水泵 故障', detail: 'DEV-M-003，振动 4.8mm/s，温度 68°C', type: 'device', buttons: ['维修'] },
      { title: '配电柜B 告警', detail: 'DEV-M-007，电压 395V 偏低', type: 'device', buttons: ['排查'] }
    ]
  },
  {
    name: '站台区',
    heat: { color: '#08ae66', bgColor: 'rgba(8,174,102,0.15)', pos: { x: 0, y: 0, z: 20 }, type: 'ok', desc: ['正常运行'] },
    status: '正常',
    statusClass: 'blue-text',
    boxClass: 'green-box',
    wrapperColor: 'var(--green)',
    pillClass: 'ok',
    expandedEnv: true,
    expandedDevice: true,
    env: [
      { name: '温度', value: '25.1', unit: '°C', isAbnormal: false },
      { name: '湿度', value: '50', unit: '%', isAbnormal: false },
      { name: '颗粒', value: '28', unit: 'μg/m³', isAbnormal: false },
      { name: '照度', value: '240', unit: 'lx', isAbnormal: false },
      { name: '噪声', value: '55', unit: 'dB', isAbnormal: false },
      { name: '异常气味', value: '无', unit: '', isAbnormal: false }
    ],
    envScore: '95分',
    envStatus: '正常',
    envStatusClass: 'green-text',
    anomalyEnv: [],
    deviceStatus: '8台全正常',
    deviceStatusClass: 'green-text',
    anomalyDevices: [],
    passengerDensity: '0.42人/㎡',
    passengerStatus: '正常',
    passengerStatusClass: 'green-text',
    energyToday: '142kWh',
    energyStatus: '正常',
    energyStatusClass: 'green-text',
    suggestion: '环境优良设备运转正常，能耗平稳，建议维持现有巡检频次',
    suggestionClass: 'green-text',
    zoneAnalysis: '站台区运行平稳，环境评分95分。温度25.1°C、湿度50%、噪声55dB均在标准范围内。8台设备全部正常运转，客流密度0.42人/㎡处于合理区间。今日能耗142kWh，与历史同期基本持平，建议维持现有巡检频次。',
    anomalyActions: []
  },
  {
    name: '检票口A区',
    heat: { color: '#ff6a00', bgColor: 'rgba(255,106,0,0.12)', pos: { x: 8, y: 0, z: 8 }, type: 'warn', desc: ['温度偏高'] },
    status: '注意',
    statusClass: 'orange-text',
    boxClass: 'orange-box',
    wrapperColor: 'var(--orange)',
    pillClass: 'warn',
    expandedEnv: true,
    expandedDevice: true,
    env: [
      { name: '温度', value: '27.8', unit: '°C', isAbnormal: true },
      { name: '湿度', value: '52', unit: '%', isAbnormal: false },
      { name: '颗粒', value: '36', unit: 'μg/m³', isAbnormal: false },
      { name: '照度', value: '220', unit: 'lx', isAbnormal: false },
      { name: '噪声', value: '60', unit: 'dB', isAbnormal: false },
      { name: '异常气味', value: '无', unit: '', isAbnormal: false }
    ],
    envScore: '86分',
    envStatus: '注意',
    envStatusClass: 'orange-text',
    anomalyEnv: [
      { item: '温度', value: '27.8°C', standard: '≤26°C', status: '偏高', selected: false }
    ],
    deviceStatus: '12台全正常',
    deviceStatusClass: 'green-text',
    anomalyDevices: [],
    passengerDensity: '0.48人/㎡',
    passengerStatus: '正常',
    passengerStatusClass: 'green-text',
    energyToday: '135kWh',
    energyStatus: '正常',
    energyStatusClass: 'green-text',
    suggestion: '温度略高于标准值，建议适当调低空调设定温度，其他指标均正常',
    suggestionClass: 'orange-text',
    zoneAnalysis: '检票口A区环境评分86分，温度27.8°C超出标准值26°C上限约1.8°C，主要因检票口区域人员流动频繁导致。湿度52%、颗粒36μg/m³、噪声60dB均在标准范围内。12台闸机设备全部正常运转，客流密度0.48人/㎡处于合理区间。今日能耗135kWh与历史同期基本持平，建议适当调低空调设定温度。',
    anomalyActions: [
      { title: '温度偏高 27.8°C', detail: '标准 ≤26°C，偏差 +1.8°C', type: 'env', buttons: ['巡检', '开启风机'] }
    ]
  },

  {
    name: '候车厅B区',
    heat: { color: '#ff6a00', bgColor: 'rgba(255,106,0,0.15)', pos: { x: 0, y: 2, z: -26 }, type: 'warn', desc: ['温度偏高', '设备故障1台', '客流偏高', '能耗偏高'] },
    status: '注意',
    statusClass: 'orange-text',
    boxClass: 'orange-box',
    wrapperColor: 'var(--orange)',
    pillClass: 'warn',
    expandedEnv: true,
    expandedDevice: true,
    env: [
      { name: '温度', value: '28.5', unit: '°C', isAbnormal: true },
      { name: '湿度', value: '55', unit: '%', isAbnormal: false },
      { name: '颗粒', value: '42', unit: 'μg/m³', isAbnormal: false },
      { name: '照度', value: '180', unit: 'lx', isAbnormal: false },
      { name: '噪声', value: '58', unit: 'dB', isAbnormal: false },
      { name: '异常气味', value: '无', unit: '', isAbnormal: false }
    ],
    envScore: '76分',
    envStatus: '注意',
    envStatusClass: 'orange-text',
    anomalyEnv: [
      { item: '温度', value: '28.5°C', standard: '≤26°C', status: '偏高', selected: false }
    ],
    deviceStatus: '1台故障',
    deviceStatusClass: 'orange-text',
    anomalyDevices: [
      {
        id: 'DEV-B-027',
        name: '排风机2#',
        type: '排风机',
        currentData: [
          { label: '转速', value: '0 rpm' },
          { label: '电流', value: '0 A' },
          { label: '状态码', value: 'ERR-03' }
        ],
        status: '故障',
        location: '候车厅B区-东侧吊顶',
        selected: false,
        blink: true
      }
    ],
    passengerDensity: '0.52人/㎡',
    passengerStatus: '注意',
    passengerStatusClass: 'orange-text',
    energyToday: '156kWh',
    energyStatus: '注意',
    energyStatusClass: 'orange-text',
    suggestion: '客流密度较高是引起温度偏高的主要原因，建议先调低空调设定再排查故障设备排风机2#',
    suggestionClass: 'orange-text',
    zoneAnalysis: '候车厅B区当前环境温度28.5°C，超出标准值26°C上限约2.5°C。主要原因为客流密度达0.52人/㎡，人体散热负荷较大。同时排风机2#（DEV-B-027）处于故障停机状态，转速为0，导致区域通风不足，进一步加剧温升。能耗156kWh较同期偏高约12%，与空调满负荷运行有关。',
    anomalyActions: [
      { title: '温度偏高 28.5°C', detail: '标准 ≤26°C，偏差 +2.5°C', type: 'env', buttons: ['巡检', '开启风机'] },
      { title: '排风机2# 故障', detail: 'DEV-B-027，转速 0 rpm，ERR-03', type: 'device', buttons: ['维修'] },
      { title: '客流密度偏高', detail: '0.52人/㎡，建议分流引导', type: 'env', buttons: ['巡检'] },
      { title: '能耗偏高', detail: '156kWh，同期 +12%', type: 'env', buttons: ['巡检'] }
    ]
  },

    {
    name: '候车厅A区',
    heat: { color: '#08ae66', bgColor: 'rgba(8,174,102,0.1)', pos: { x: 0, y: 2, z: 0 }, type: 'ok', desc: ['正常运行'] },
    status: '正常',
    statusClass: 'green-text',
    boxClass: 'green-box',
    wrapperColor: 'var(--green)',
    pillClass: 'ok',
    expandedEnv: true,
    expandedDevice: true,
    env: [
      { name: '温度', value: '24.2', unit: '°C', isAbnormal: false },
      { name: '湿度', value: '48', unit: '%', isAbnormal: false },
      { name: '颗粒', value: '35', unit: 'μg/m³', isAbnormal: false },
      { name: '照度', value: '200', unit: 'lx', isAbnormal: false },
      { name: '噪声', value: '52', unit: 'dB', isAbnormal: false },
      { name: '异常气味', value: '无', unit: '', isAbnormal: false }
    ],
    envScore: '92分',
    envStatus: '正常',
    envStatusClass: 'green-text',
    anomalyEnv: [],
    deviceStatus: '26台全正常',
    deviceStatusClass: 'green-text',
    anomalyDevices: [],
    passengerDensity: '0.35人/㎡',
    passengerStatus: '正常',
    passengerStatusClass: 'green-text',
    energyToday: '128kWh',
    energyStatus: '正常',
    energyStatusClass: 'green-text',
    suggestion: '环境舒适设备运行良好，能耗处于合理区间，建议保持当前策略',
    suggestionClass: 'green-text',
    zoneAnalysis: '候车厅A区各项指标优良，环境评分92分。温度24.2°C、湿度48%、噪声52dB均处于舒适区间。26台设备全量正常运行，无故障无告警。客流密度0.35人/㎡较为宽松，今日能耗128kWh处于合理水平，建议保持当前巡检策略和空调参数。',
    anomalyActions: []
  }
])

const energyTimeData = ['00:00', '02:00', '04:00', '06:00', '08:00', '10:00', '12:00', '14:00', '16:00', '18:00', '20:00', '22:00', '24:00']
const energyPredictData = [14, 17, 19, 21, 24, 26, 28, 27, 25, 23, 20, 17, 15]
const energyTotalData = [12, 15, 16, 17, 20, 22, 24, 23, 21, 19, 16, 14, 13]
const passengerFlowData = [13, 16, 18, 17, 21, 23, 24, 23, 21, 19, 15, 13, 12]
const energyBaseline = 38

let botReplyTimer

const isAssistantModalOpen = ref(false)

function toggleAssistant() {
  isAssistantModalOpen.value = true
}

function closeAssistantModal() {
  isAssistantModalOpen.value = false
}

function goKnowledgeBase() {
  isKnowledgeModalOpen.value = true
}

const isKnowledgeModalOpen = ref(false)

function closeKnowledgeModal() {
  isKnowledgeModalOpen.value = false
}

async function scrollAssistantToBottom() {
  await nextTick()
  const messages = assistantMessagesEl.value
  if (messages) {
    messages.scrollTop = messages.scrollHeight
  }
}

function sendAssistantMessage() {
  const text = assistantInput.value.trim()
  if (!text) return

  assistantMessages.value.push({ role: 'user', text })
  assistantInput.value = ''
  scrollAssistantToBottom()

  window.clearTimeout(botReplyTimer)
  botReplyTimer = window.setTimeout(() => {
    assistantMessages.value.push({ role: 'bot', text: assistantReplyMessage })
    scrollAssistantToBottom()
  }, 500)
}

function applyTheme(theme) {
  if (theme === 'dark') {
    document.documentElement.setAttribute('data-theme', 'dark')
    return
  }

  document.documentElement.removeAttribute('data-theme')
}

function toggleTheme() {
  const current = document.documentElement.getAttribute('data-theme')
  const next = current === 'dark' ? 'light' : 'dark'

  applyTheme(next)
  localStorage.setItem('theme', next)
  isDarkTheme.value = next === 'dark'
}

function initSparklineChart() {
  if (!sparklineChartRef.value) return
  sparklineChart = echarts.init(sparklineChartRef.value)
  const option = {
    grid: { top: 0, bottom: 0, left: 0, right: 0 },
    xAxis: { type: 'category', show: false, data: healthTrendData.map((_, i) => i) },
    yAxis: { type: 'value', show: false, min: 20, max: 60 },
    series: [{
      type: 'line',
      data: healthTrendData,
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 2, color: '#0aaa61' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(10, 170, 97, 0.26)' },
          { offset: 1, color: 'rgba(10, 170, 97, 0)' }
        ])
      }
    }]
  }
  sparklineChart.setOption(option)
}

function initEnergyChart() {
  if (!energyChartRef.value) return
  energyChart = echarts.init(energyChartRef.value)
  const option = {
    grid: { top: 40, bottom: 25, left: 40, right: 40 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: energyTimeData,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#1a3a5c', width: 0.5, type: 'solid' } },
      axisTick: { show: false },
      axisLabel: { color: '#a0c4e8', fontSize: 12 }
    },
    yAxis: [
      {
        type: 'value',
        name: '能耗总额（kWh/m²）',
        min: 0,
        max: 40,
        splitNumber: 4,
        axisLabel: { color: '#a0c4e8', fontSize: 10 },
        nameTextStyle: { color: '#a0c4e8', fontSize: 11, padding: [0, 0, 0, 70] },
        splitLine: { lineStyle: { color: '#1a3a5c', width: 0.5, type: 'solid' } },
        axisLine: { show: true, lineStyle: { color: '#ffffff' } }
      },
      {
        type: 'value',
        name: '客流（k人）',
        min: 0,
        max: 30,
        splitNumber: 4,
        axisLabel: { color: '#a0c4e8', fontSize: 10 },
        nameTextStyle: { color: '#a0c4e8', fontSize: 11 },
        splitLine: { show: false },
        axisLine: { show: true, lineStyle: { color: '#ffffff' } }
      }
    ],
    series: [
      {
        name: '能耗预测曲线',
        type: 'line',
        data: energyPredictData,
        smooth: false,
        showSymbol: false,
        lineStyle: { width: 2.4, color: '#1268ff' }
      },
      {
        name: '总能耗用曲线',
        type: 'line',
        data: energyTotalData,
        smooth: false,
        showSymbol: false,
        lineStyle: { width: 2.4, color: '#0caf64' }
      },
      {
        name: '客流曲线',
        type: 'line',
        yAxisIndex: 1,
        data: passengerFlowData,
        smooth: false,
        showSymbol: false,
        lineStyle: { width: 2.4, color: '#5b39ff', type: 'dashed' }
      },
      {
        name: '标准能耗基线',
        type: 'line',
        data: Array(energyTimeData.length).fill(energyBaseline),
        smooth: false,
        showSymbol: false,
        lineStyle: { width: 2.2, color: '#ff1d12', type: 'dashed' }
      }
    ]
  }
  energyChart.setOption(option)
}

const aiAnalysisText = ref('')
const aiAnalysisComplete = ref(false)
const aiFullText = '综合各分区运行数据，候车厅A区各项指标优良，环境评分92分，26台设备全量正常运行，建议保持当前巡检策略。候车厅B区环境评分76分，温度偏高至28.5°C（标准≤26°C），主要受客流密度0.52人/㎡影响，建议调低空调设定温度1-2°C，同时排风机2#（DEV-B-027）处于故障状态，转速为0，需尽快安排检修。站台区运行平稳，环境评分95分，8台设备运转正常，维持现有策略即可。设备机房区环境评分71分，温度32.6°C超标、噪声65dB偏大，3号水泵（DEV-M-003）轴承振动达4.8mm/s且温度68°C，建议停机更换轴承；配电柜B（DEV-M-007）电压395V偏低，需检查线路负载。综上，建议优先处理设备机房区故障设备，其次排查候车厅B区排风机，同时调整B区空调参数以改善环境舒适度。'

let aiTypewriterTimer = null

function startAiTypewriter() {
  aiAnalysisText.value = ''
  aiAnalysisComplete.value = false
  let charIndex = 0

  function typeChar() {
    if (charIndex < aiFullText.length) {
      aiAnalysisText.value += aiFullText[charIndex]
      charIndex++
      aiTypewriterTimer = setTimeout(typeChar, 30)
    } else {
      aiAnalysisComplete.value = true
    }
  }
  typeChar()
}

function stopAiTypewriter() {
  if (aiTypewriterTimer) {
    clearTimeout(aiTypewriterTimer)
    aiTypewriterTimer = null
  }
}

// 区域异常分析打字机
const zoneAnalysisText = ref('')
const zoneAnalysisComplete = ref(false)
const visibleActionCount = ref(0)
let zoneTypewriterTimer = null
let actionShowTimers = []

function startZoneTypewriter(zone) {
  stopZoneTypewriter()
  clearActionShowTimers()
  zoneAnalysisText.value = ''
  zoneAnalysisComplete.value = false
  visibleActionCount.value = 0
  const text = zone.zoneAnalysis || ''
  const actionCount = zone.anomalyActions?.length || 0
  if (!text) { zoneAnalysisComplete.value = true; return }
  let idx = 0
  function tick() {
    if (idx < text.length) {
      zoneAnalysisText.value += text[idx]
      idx++
      zoneTypewriterTimer = setTimeout(tick, 28)
    } else {
      zoneAnalysisComplete.value = true
      // 打字完成后逐条显示决策卡片
      if (actionCount > 0) {
        for (let i = 0; i < actionCount; i++) {
          const timer = setTimeout(() => {
            visibleActionCount.value = i + 1
          }, 300 + i * 400)
          actionShowTimers.push(timer)
        }
      }
    }
  }
  tick()
}

function stopZoneTypewriter() {
  if (zoneTypewriterTimer) {
    clearTimeout(zoneTypewriterTimer)
    zoneTypewriterTimer = null
  }
}

function clearActionShowTimers() {
  actionShowTimers.forEach(t => clearTimeout(t))
  actionShowTimers = []
}

function selectZoneFromModel(zone) {
  selectedZone.value = selectedZone.value === zone ? null : zone
  if (selectedZone.value) {
    startZoneTypewriter(zone)
    nextTick(() => {
      const section = document.querySelector('.modal-zone-section')
      if (section) section.scrollTop = 0
    })
  } else {
    stopZoneTypewriter()
    clearActionShowTimers()
    visibleActionCount.value = 0
  }
}

const selectedCount = computed(() => {
  let count = 0
  zoneAreas.value.forEach(zone => {
    zone.anomalyEnv.forEach(a => { if (a.selected) count++ })
    zone.anomalyDevices.forEach(d => { if (d.selected) count++ })
  })
  return count
})

function toggleSelect(item) {
  item.selected = !item.selected
}

function handleDispatch() {
  const selected = []
  zoneAreas.value.forEach(zone => {
    zone.anomalyDevices.forEach(d => {
      if (d.selected) selected.push(d)
    })
  })
  if (selected.length === 0) {
    alert('请先选择需要派单的设备')
    return
  }
  alert(`已选择 ${selected.length} 台设备，准备派单：\n${selected.map(d => `${d.name}(${d.id}) - ${d.status}`).join('\n')}`)
}

function handleRepair() {
  const selected = []
  zoneAreas.value.forEach(zone => {
    zone.anomalyDevices.forEach(d => {
      if (d.selected) selected.push(d)
    })
  })
  if (selected.length === 0) {
    alert('请先选择需要维修的设备')
    return
  }
  alert(`已选择 ${selected.length} 台设备，准备维修：\n${selected.map(d => `${d.name}(${d.id}) - ${d.location}`).join('\n')}`)
}

function openModal() {
  isModalOpen.value = true
  selectedZone.value = null
  nextTick(() => {
    setTimeout(() => {
      initModalHeatmap()
      startAiTypewriter()
    }, 200)
  })
}

function openModalWithZone(zone) {
  isModalOpen.value = true
  selectedZone.value = zone
  nextTick(() => {
    setTimeout(() => {
      initModalHeatmap()
      startAiTypewriter()
      // 延迟启动区域分析和扫描动画
      setTimeout(() => {
        startZoneTypewriter(zone)
        triggerPanelScan()
      }, 500)
    }, 200)
  })
}

function triggerPanelScan() {
  // 触发3D模型中对应区域面板的扫描动画
  if (!selectedZone.value) return
  const zoneIndex = selectedZone.value.zoneIndex
  // 弹窗打开时，使用弹窗的标签容器
  const labelDivs = isModalOpen.value
    ? modalHeatmapLabels.value?.querySelectorAll('.zone-panel')
    : heatmapLabels.value?.querySelectorAll('.zone-panel')
  if (!labelDivs || !labelDivs[zoneIndex]) return
  const labelDiv = labelDivs[zoneIndex]
  const scanLine = labelDiv.querySelector('.scan-line')
  if (scanLine) {
    scanLine.style.animation = 'none'
    scanLine.offsetHeight
    scanLine.style.animation = 'panelScan 2s ease-out'
    setTimeout(() => {
      scanLine.style.animation = 'none'
      scanLine.offsetHeight
      scanLine.style.animation = 'panelScan 2s ease-out'
      setTimeout(() => {
        scanLine.style.animation = 'none'
      }, 2000)
    }, 2000)
  }
}

function openPassengerAgent() {
  window.open('http://192.168.128.203:3000/', '_blank')
}

function closeModal() {
  isModalOpen.value = false
  stopAiTypewriter()
  stopZoneTypewriter()
  clearActionShowTimers()
  visibleActionCount.value = 0
  selectedZone.value = null
  stopModalHeatmap()
}

function switchZoneView(mode) {
  zoneViewMode.value = mode
  if (mode === 'model') {
    nextTick(() => {
      setTimeout(() => {
        initThreeHeatmap()
      }, 100)
    })
  }
}

function openHealthModal() {
  isHealthModalOpen.value = true
  healthTimeDimension.value = '10min'
  nextTick(() => {
    setTimeout(() => {
      initHealthScoreChart()
      initHealthTrendChart()
      initHealthRadarChart()
    }, 200)
  })
}

function closeHealthModal() {
  isHealthModalOpen.value = false
  if (sparklineChart) {
    sparklineChart.dispose()
    sparklineChart = null
  }
  if (healthRadarChart) {
    healthRadarChart.dispose()
    healthRadarChart = null
  }
  if (healthScoreChart) {
    healthScoreChart.dispose()
    healthScoreChart = null
  }
}

function openDeviceAnalysis() {
  isDeviceAnalysisOpen.value = true
  generateHeatmapData()
  nextTick(() => {
    setTimeout(() => {
      initDeviceHeatmap()
    }, 200)
  })
}

function closeDeviceAnalysis() {
  isDeviceAnalysisOpen.value = false
  if (deviceHeatmapChart) {
    deviceHeatmapChart.dispose()
    deviceHeatmapChart = null
  }
}

function generateHeatmapData() {
  const dates = []
  const now = new Date()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now - i * 86400000)
    dates.push(`${d.getMonth() + 1}/${d.getDate()}`)
  }
  
  const categories = ['故障', '告警', '维修']
  const data = []
  
  dates.forEach((date, x) => {
    categories.forEach((category, y) => {
      const count = Math.floor(Math.random() * 8)
      data.push([x, y, count])
    })
  })
  
  deviceHeatmapData.value = { dates, categories, data }
}

function initDeviceHeatmap() {
  const container = deviceHeatmapChartRef.value
  if (!container || !deviceHeatmapData.value.dates) return

  if (deviceHeatmapChart) {
    deviceHeatmapChart.dispose()
  }
  deviceHeatmapChart = echarts.init(container)

  const { dates, categories, data } = deviceHeatmapData.value

  const option = {
    tooltip: {
      position: 'top',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      textStyle: { color: '#07153b', fontSize: 12 },
      formatter: function(params) {
        return `${categories[params.value[1]]}<br/>${dates[params.value[0]]}<br/>数量：<b>${params.value[2]}</b>`
      }
    },
    grid: {
      height: '60%',
      top: '8%',
      left: '12%',
      right: '8%'
    },
    xAxis: {
      type: 'category',
      data: dates,
      splitArea: { show: true, areaStyle: { color: ['rgba(248,251,255,0.8)', 'rgba(240,245,255,0.8)'] } },
      axisLine: { lineStyle: { color: '#d8e5f7' } },
      axisLabel: { color: '#526383', fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: categories,
      splitArea: { show: true, areaStyle: { color: ['rgba(248,251,255,0.8)', 'rgba(240,245,255,0.8)'] } },
      axisLine: { lineStyle: { color: '#d8e5f7' } },
      axisLabel: { color: '#526383', fontSize: 12, fontWeight: 600 }
    },
    visualMap: {
      min: 0,
      max: 7,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: '2%',
      inRange: {
        color: ['#e6f4ff', '#bae0ff', '#69b1ff', '#1677ff', '#0958d9', '#003eb3', '#002b8a']
      },
      textStyle: { color: '#526383' }
    },
    series: [{
      type: 'heatmap',
      data: data,
      label: { show: true, fontSize: 10, color: '#07153b' },
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowColor: 'rgba(0, 0, 0, 0.3)'
        }
      }
    }]
  }
  deviceHeatmapChart.setOption(option)
}

function switchHealthTimeDimension(dim) {
  healthTimeDimension.value = dim
  nextTick(() => {
    initHealthTrendChart()
  })
}

function openSubItemModal(subItem) {
  currentSubItem.value = subItem
  isSubItemModalOpen.value = true
}

function closeSubItemModal() {
  isSubItemModalOpen.value = false
  currentSubItem.value = null
}

function initHealthTrendChart() {
  const container = document.querySelector('.health-trend-chart')
  if (!container) return

  if (sparklineChart) {
    sparklineChart.dispose()
  }
  sparklineChart = echarts.init(container)

  const timeLabels = []
  const data = []
  const now = new Date()

  if (healthTimeDimension.value === '10min') {
    for (let i = 9; i >= 0; i--) {
      const t = new Date(now - i * 60000)
      timeLabels.push(`${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`)
      data.push(88 + Math.random() * 8)
    }
  } else if (healthTimeDimension.value === '1h') {
    for (let i = 9; i >= 0; i--) {
      const t = new Date(now - i * 3600000)
      timeLabels.push(`${String(t.getHours()).padStart(2, '0')}:00`)
      data.push(85 + Math.random() * 10)
    }
  } else {
    for (let i = 9; i >= 0; i--) {
      const t = new Date(now - i * 86400000)
      timeLabels.push(`${t.getMonth() + 1}/${t.getDate()}`)
      data.push(82 + Math.random() * 12)
    }
  }

  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      textStyle: { color: '#07153b', fontSize: 12 },
      formatter: function(params) {
        return params[0].name + '<br/>健康度：<b>' + params[0].value.toFixed(1) + '分</b>'
      }
    },
    grid: { top: 20, right: 20, bottom: 30, left: 45 },
    xAxis: {
      type: 'category',
      data: timeLabels,
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      min: 70,
      max: 100,
      splitLine: { lineStyle: { color: '#f3f4f6', type: 'dashed' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 }
    },
    series: [{
      data: data,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      itemStyle: { color: '#1268ff' },
      lineStyle: { width: 2.5 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(18,104,255,0.3)' },
          { offset: 1, color: 'rgba(18,104,255,0.05)' }
        ])
      }
    }]
  }
  sparklineChart.setOption(option)
}

function initHealthScoreChart() {
  const container = healthScoreChartRef.value
  if (!container) return

  if (healthScoreChart) {
    healthScoreChart.dispose()
  }
  healthScoreChart = echarts.init(container)

  const option = {
    series: [{
      type: 'gauge',
      startAngle: 90,
      endAngle: -270,
      pointer: { show: false },
      progress: {
        show: true,
        overlap: false,
        roundCap: true,
        clip: false,
        itemStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 1, y2: 0,
            colorStops: [
              { offset: 0, color: '#1268ff' },
              { offset: 1, color: '#6fe9ff' }
            ]
          }
        }
      },
      axisLine: {
        lineStyle: {
          width: 12,
          color: [[1, '#e5e7eb']]
        }
      },
      splitLine: { show: false },
      axisTick: { show: false },
      axisLabel: { show: false },
      data: [{
        value: healthScore.value,
        name: '',
        title: { offsetCenter: ['0%', '0%'] },
        detail: {
          valueAnimation: true,
          offsetCenter: ['0%', '0%'],
          formatter: function(value) {
            return '{value|' + value.toFixed(0) + '}\n{name|分}'
          },
          rich: {
            value: {
              fontSize: 32,
              fontWeight: 800,
              color: '#1268ff',
              lineHeight: 36
            },
            name: {
              fontSize: 12,
              color: '#9ca3af',
              lineHeight: 16
            }
          }
        }
      }],
      animationDuration: 1500,
      animationEasing: 'cubicOut'
    }]
  }
  healthScoreChart.setOption(option)
}

function initHealthRadarChart() {
  const container = healthRadarChartRef.value
  if (!container) return

  if (healthRadarChart) {
    healthRadarChart.dispose()
  }
  healthRadarChart = echarts.init(container)

  const indicators = healthSubItems.value.map(item => ({
    name: item.name,
    max: 100
  }))

  const scores = healthSubItems.value.map(item => item.score)

  const option = {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      textStyle: { color: '#07153b' },
      formatter: function(params) {
        const idx = params.dataIndex || 0
        const item = healthSubItems.value[idx]
        if (!item) return ''
        let html = `<div style="font-weight:700;font-size:14px;margin-bottom:8px;">${item.name}</div>`
        html += `<div style="font-size:13px;line-height:1.8;">`
        html += `分数：<b style="color:#1268ff">${item.score}分</b><br/>`
        html += `权重：${item.weight}<br/>`
        if (item.deductionDevices.length > 0) {
          html += `异常：${item.deductionDevices.length}项<br/>`
          html += `扣分设备：${item.deductionDevices.map(d => d.name).join('、')}`
        } else {
          html += `异常：无`
        }
        html += `</div>`
        return html
      }
    },
    radar: {
      indicator: indicators,
      shape: 'polygon',
      splitNumber: 5,
      radius: '65%',
      center: ['50%', '55%'],
      axisName: {
        color: '#ffffff',
        fontSize: 12,
        fontWeight: 600,
        padding: [3, 5]
      },
      splitLine: {
        lineStyle: { color: '#e5e7eb' }
      },
      splitArea: {
        areaStyle: {
          color: ['rgba(18,104,255,0.05)', 'rgba(18,104,255,0.02)']
        }
      },
      axisLine: {
        lineStyle: { color: '#e5e7eb' }
      }
    },
    series: [{
      type: 'radar',
      data: [{
        value: scores,
        name: '站房健康度',
        symbol: 'circle',
        symbolSize: 8,
        itemStyle: { color: '#1268ff' },
        lineStyle: { width: 2.5, color: '#1268ff' },
        areaStyle: {
          color: new echarts.graphic.RadialGradient(0.5, 0.5, 1, [
            { offset: 0, color: 'rgba(18,104,255,0.4)' },
            { offset: 1, color: 'rgba(18,104,255,0.1)' }
          ])
        }
      }]
    }]
  }
  healthRadarChart.setOption(option)
}

function handleAnomalyAction(btnText, action) {
  if (btnText === '维修') {
    openDispatchModal(action, 'repair')
  } else if (btnText === '排查') {
    openDispatchModal(action, 'check')
  } else if (btnText === '巡检') {
    openDispatchModal(action, 'inspect')
  } else if (btnText === '开启风机') {
    openDeviceControlModal('fan', btnText, action)
  }
}

function openDeviceControlModal(deviceType, actionText, action) {
  const now = new Date()
  const timestamp = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`

  deviceControlInfo.value = {
    deviceName: action.title.split(' ')[0] || '风机',
    deviceId: 'DEV-FAN-001',
    deviceType: deviceType,
    action: actionText,
    status: '下发中...',
    statusClass: 'pending',
    timestamp: timestamp,
    dataLinkStatus: '等待中',
    dataLinkClass: 'waiting'
  }

  isDeviceControlModalOpen.value = true

  // 1秒后显示下发成功，风机开始旋转
  deviceControlTimer = setTimeout(() => {
    deviceControlInfo.value.status = '下发成功'
    deviceControlInfo.value.statusClass = 'success'
  }, 1000)

  // 2秒后显示数据链路开启成功，并显示吹风效果
  setTimeout(() => {
    deviceControlInfo.value.dataLinkStatus = '开启成功'
    deviceControlInfo.value.dataLinkClass = 'success'
    deviceControlInfo.value.showWindEffect = true
  }, 2000)

  // 5秒后关闭弹窗
  deviceCloseTimer = setTimeout(() => {
    closeDeviceControlModal()
  }, 5000)
}

function closeDeviceControlModal() {
  isDeviceControlModalOpen.value = false
  if (deviceControlTimer) {
    clearTimeout(deviceControlTimer)
    deviceControlTimer = null
  }
  if (deviceCloseTimer) {
    clearTimeout(deviceCloseTimer)
    deviceCloseTimer = null
  }
}

function openDispatchModal(action, modalType = 'repair') {
  dispatchModalType.value = modalType
  // 查找对应的设备信息
  let deviceInfo = null
  zoneAreas.value.forEach(zone => {
    zone.anomalyDevices.forEach(d => {
      if (action.title.includes(d.name) || action.title.includes(d.id)) {
        deviceInfo = { ...d, zoneName: zone.name }
      }
    })
  })

  if (!deviceInfo) {
    deviceInfo = {
      name: action.title.split(' ')[0],
      id: 'DEV-UNKNOWN',
      type: '未知设备',
      location: '待确认',
      zoneName: selectedZone.value?.name || '未知区域'
    }
  }

  const orderNum = 'WO-' + Date.now().toString().slice(-8)
  dispatchOrder.value = {
    deviceName: deviceInfo.name,
    deviceId: deviceInfo.id,
    deviceType: deviceInfo.type || '未知',
    deviceLocation: deviceInfo.location || '待确认',
    orderNumber: orderNum,
    faultReason: '',
    repairSteps: '',
    recommendedPerson: null,
    notes: ''
  }

  // 推荐维修人员信息
  recommendedPerson.value = {
    name: '张工',
    title: '高级维修工程师',
    employeeId: '工号169',
    phone: '138-0013-8000',
    avatar: null,
    matchRate: 95,
    status: '空闲',
    currentTasks: 1,
    reason: '该维修人员擅长处理' + (deviceInfo.type || '相关') + '设备故障，当前状态空闲，距离' + (deviceInfo.zoneName || '该区域') + '最近，历史维修成功率98.5%'
  }

  dispatchFaultText.value = ''
  dispatchStepsText.value = ''
  dispatchFaultComplete.value = false
  dispatchStepsComplete.value = false

  isDispatchModalOpen.value = true

  // 逐字显示故障原因/告警分析/巡检说明
  let faultText, stepsText
  if (modalType === 'repair') {
    faultText = `根据设备${deviceInfo.name}（${deviceInfo.id}）的运行数据分析，当前故障原因为：${action.detail}。该设备位于${deviceInfo.zoneName}，建议尽快安排维修人员前往现场处理。`
    stepsText = `维修步骤：1. 到达现场后先确认设备状态指示灯；2. 使用万用表检测电路电压是否正常；3. 根据故障代码查阅维修手册；4. 更换故障部件或进行参数校准；5. 重启设备并观察运行状态30分钟；6. 填写维修记录并上报系统。`
  } else if (modalType === 'check') {
    faultText = `根据设备${deviceInfo.name}（${deviceInfo.id}）的运行数据分析，当前告警信息为：${action.detail}。该设备位于${deviceInfo.zoneName}，建议尽快安排人员前往现场排查确认。`
    stepsText = `排查步骤：1. 到达现场后确认设备运行状态；2. 检查设备参数是否在正常范围内；3. 查看设备日志和告警记录；4. 使用检测工具测量关键指标；5. 确认是否存在真实故障或误报；6. 填写排查记录并上报结果。`
  } else {
    faultText = `根据设备${deviceInfo.name}（${deviceInfo.id}）的运行数据分析，该设备位于${deviceInfo.zoneName}，当前运行状态正常。建议安排巡检人员前往现场进行例行检查，确保设备持续稳定运行。`
    stepsText = `巡检步骤：1. 到达现场后记录设备外观状态；2. 检查设备运行参数是否在正常范围；3. 检测设备温度和振动情况；4. 清理设备表面和周围环境；5. 记录巡检数据并拍照存档；6. 填写巡检报告并上报系统。`
  }
  startDispatchFaultTypewriter(faultText)

  // 故障原因/告警分析/巡检说明显示完成后，逐字显示维修/排查/巡检步骤
  setTimeout(() => {
    startDispatchStepsTypewriter(stepsText)
  }, faultText.length * 25 + 500)
}

function startDispatchFaultTypewriter(text) {
  clearDispatchTypewriters()
  dispatchFaultText.value = ''
  dispatchFaultComplete.value = false
  let idx = 0
  function tick() {
    if (idx < text.length) {
      dispatchFaultText.value += text[idx]
      idx++
      dispatchFaultTimer = setTimeout(tick, 25)
    } else {
      dispatchFaultComplete.value = true
    }
  }
  tick()
}

function startDispatchStepsTypewriter(text) {
  dispatchStepsText.value = ''
  dispatchStepsComplete.value = false
  let idx = 0
  function tick() {
    if (idx < text.length) {
      dispatchStepsText.value += text[idx]
      idx++
      dispatchStepsTimer = setTimeout(tick, 25)
    } else {
      dispatchStepsComplete.value = true
    }
  }
  tick()
}

function clearDispatchTypewriters() {
  if (dispatchFaultTimer) {
    clearTimeout(dispatchFaultTimer)
    dispatchFaultTimer = null
  }
  if (dispatchStepsTimer) {
    clearTimeout(dispatchStepsTimer)
    dispatchStepsTimer = null
  }
}

function closeDispatchModal() {
  isDispatchModalOpen.value = false
  clearDispatchTypewriters()
}

function confirmDispatchOrder() {
  dispatchOrder.value.faultReason = dispatchFaultText.value
  dispatchOrder.value.repairSteps = dispatchStepsText.value
  const actionText = dispatchModalType.value === 'repair' ? '维修' : '排查'
  alert(`已确认${actionText}！\n工单号：${dispatchOrder.value.orderNumber}\n设备：${dispatchOrder.value.deviceName}\n人员：${dispatchOrder.value.recommendedPerson}`)
  closeDispatchModal()
}

function cancelDispatchOrder() {
  closeDispatchModal()
}

function toggleAnomalySection(index, type) {
  const zone = zoneAreas.value[index]
  if (type === 'env') {
    if (zone.anomalyEnv.length > 0) {
      zone.expandedEnv = !zone.expandedEnv
    }
  } else if (type === 'device') {
    if (zone.anomalyDevices.length > 0) {
      zone.expandedDevice = !zone.expandedDevice
    }
  }
}

let heatmapTime = 0

function initThreeHeatmap() {
  const container = heatmapContainer.value
  const labelsContainer = heatmapLabels.value
  if (!container || !labelsContainer) return

  // Clear previous
  while (container.firstChild) container.removeChild(container.firstChild)
  while (labelsContainer.firstChild) labelsContainer.removeChild(labelsContainer.firstChild)
  pointsInstances = []

  const rect = container.getBoundingClientRect()

  const isDark = document.documentElement.getAttribute('data-theme') === 'dark'
  const bgColor = isDark ? 0x0a1929 : 0xf8fbff
  const gridColor1 = isDark ? 0x1a3a5c : 0xc3daf7
  const gridColor2 = isDark ? 0x2a5a8c : 0xd0e1f9
  const fogColor = isDark ? 0x0d2137 : 0xe3eafd

  const scene = new THREE.Scene()
  scene.background = new THREE.Color(bgColor)
  scene.fog = new THREE.FogExp2(fogColor, 0.008)
  threeScene = scene

  const camera = new THREE.PerspectiveCamera(45, rect.width / rect.height, 1, 1000)
  camera.position.set(38, 16, 0)
  threeCamera = camera

  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setPixelRatio(window.devicePixelRatio)
  renderer.setSize(rect.width, rect.height)
  renderer.setClearColor(bgColor, 1)
  container.appendChild(renderer.domElement)
  threeRenderer = renderer

  const controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.05
  controls.maxPolarAngle = Math.PI / 2.1
  threeControls = controls

  const ambientLight = new THREE.AmbientLight(0xffffff, 0.8)
  scene.add(ambientLight)
  const dirLight = new THREE.DirectionalLight(0xffffff, 0.4)
  dirLight.position.set(20, 40, 20)
  scene.add(dirLight)

  const gridHelper = new THREE.GridHelper(100, 40, gridColor1, gridColor2)
  gridHelper.position.y = -0.1
  scene.add(gridHelper)

  const buildingGroup = new THREE.Group()

  const roofColor = isDark ? 0x00d4ff : 0x3b82f6
  const roofWireColor = isDark ? 0x00e5ff : 0x60a5fa

  const roofWidth = 26, roofLength = 50, segments = 30
  const roofGeo = new THREE.PlaneGeometry(roofWidth, roofLength, segments, segments)
  roofGeo.rotateX(-Math.PI / 2)
  const posAttr = roofGeo.attributes.position
  for (let i = 0; i < posAttr.count; i++) {
    let x = posAttr.getX(i)
    let z = posAttr.getZ(i)
    const zNorm = z / (roofLength / 2)
    const xScale = 1 + zNorm * zNorm * 0.5
    posAttr.setX(i, x * xScale)
    let y = (z * z) * 0.006 - (x * xScale) * (x * xScale) * 0.004 + 6
    posAttr.setY(i, y)
  }
  roofGeo.computeVertexNormals()

  const roofMat = new THREE.MeshStandardMaterial({ color: roofColor, transparent: true, opacity: isDark ? 0.1 : 0.15, side: THREE.DoubleSide })
  const centerRoof = new THREE.Mesh(roofGeo, roofMat)
  buildingGroup.add(centerRoof)

  const roofWireMat = new THREE.MeshBasicMaterial({ color: roofWireColor, wireframe: true, transparent: true, opacity: isDark ? 0.3 : 0.4 })
  const centerRoofWire = new THREE.Mesh(roofGeo, roofWireMat)
  buildingGroup.add(centerRoofWire)

  scene.add(buildingGroup)

  // 3层楼板（1层y=0, 2层y=2, 3层y=4），与屋顶同宽同长同弧度
  const floorGap = 2
  const floorColor = isDark ? 0x00d4ff : 0x3b82f6
  const floorWireColor = isDark ? 0x00e5ff : 0x60a5fa
  const floorWidth = roofWidth, floorLength = roofLength
  const pillarColor = isDark ? 0x1a5a8c : 0x93c5fd
  const floorNames = ['1层', '2层', '3层']

  // 生成与屋顶同弧度的楼板几何体
  function createCurvedFloorGeo(w, l, segW, segL) {
    const geo = new THREE.PlaneGeometry(w, l, segW, segL)
    geo.rotateX(-Math.PI / 2)
    const attr = geo.attributes.position
    for (let i = 0; i < attr.count; i++) {
      const x = attr.getX(i)
      const z = attr.getZ(i)
      const zNorm = z / (l / 2)
      const xScale = 1 + zNorm * zNorm * 0.5
      attr.setX(i, x * xScale)
    }
    return geo
  }

  for (let level = 0; level < 3; level++) {
    const floorY = level * floorGap

    // 楼板填充
    const floorGeo = createCurvedFloorGeo(floorWidth, floorLength, 2, 30)
    const floorMat = new THREE.MeshBasicMaterial({
      color: floorColor,
      transparent: true,
      opacity: isDark ? 0.06 : 0.09,
      side: THREE.DoubleSide
    })
    const floorMesh = new THREE.Mesh(floorGeo, floorMat)
    floorMesh.position.y = floorY
    scene.add(floorMesh)

    // 楼板网格线
    const gridGeo = createCurvedFloorGeo(floorWidth, floorLength, 6, 30)
    const gridWireMat = new THREE.MeshBasicMaterial({ color: floorWireColor, wireframe: true, transparent: true, opacity: isDark ? 0.08 : 0.12 })
    const gridWire = new THREE.Mesh(gridGeo, gridWireMat)
    gridWire.position.y = floorY + 0.01
    scene.add(gridWire)

    // 楼板边框
    const floorEdgeGeo = new THREE.EdgesGeometry(createCurvedFloorGeo(floorWidth, floorLength, 2, 30))
    const floorLineMat = new THREE.LineBasicMaterial({ color: floorWireColor, transparent: true, opacity: isDark ? 0.35 : 0.45 })
    const floorLine = new THREE.LineSegments(floorEdgeGeo, floorLineMat)
    floorLine.position.y = floorY
    scene.add(floorLine)

    // 层级名称标签
    const labelCanvas = document.createElement('canvas')
    labelCanvas.width = 128
    labelCanvas.height = 64
    const ctx = labelCanvas.getContext('2d')
    ctx.fillStyle = isDark ? 'rgba(0,212,255,0.85)' : 'rgba(59,130,246,0.85)'
    ctx.font = 'bold 36px sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(floorNames[level], 64, 32)
    const labelTexture = new THREE.CanvasTexture(labelCanvas)
    const labelMat = new THREE.SpriteMaterial({ map: labelTexture, transparent: true, depthTest: false })
    const labelSprite = new THREE.Sprite(labelMat)
    labelSprite.scale.set(4, 2, 1)
    labelSprite.position.set(floorWidth / 2 + 3, floorY + 0.8, 0)
    scene.add(labelSprite)
  }

  // 四角立柱连接各层
  const pillarHeight = floorGap * 2
  const pillarGeo = new THREE.CylinderGeometry(0.15, 0.15, pillarHeight, 8)
  const pillarMat = new THREE.MeshBasicMaterial({ color: pillarColor, transparent: true, opacity: isDark ? 0.3 : 0.4 })
  const phw = floorWidth / 2
  const phl = floorLength / 2
  ;[[-phw, -phl], [-phw, phl], [phw, -phl], [phw, phl]].forEach(([px, pz]) => {
    const pillar = new THREE.Mesh(pillarGeo, pillarMat)
    pillar.position.set(px, floorGap, pz)
    scene.add(pillar)
  })

  zoneAreas.value.forEach((zone, zoneIdx) => {
    const h = zone.heat
    if (!h) return
    const pointGroup = new THREE.Group()
    pointGroup.position.set(h.pos.x, h.pos.y, h.pos.z)
    pointGroup.userData = { zone, heat: h }

    const ballGeo = new THREE.SphereGeometry(0.6, 16, 16)
    const ballMat = new THREE.MeshBasicMaterial({ color: new THREE.Color(h.color) })
    const ball = new THREE.Mesh(ballGeo, ballMat)
    pointGroup.add(ball)

    const zoneGeo = new THREE.CylinderGeometry(4, 4, 0.2, 32)
    const zoneMat = new THREE.MeshBasicMaterial({
      color: new THREE.Color(h.color),
      transparent: true,
      opacity: h.type === 'ok' ? 0.05 : 0.2,
      wireframe: true
    })
    const zoneMesh = new THREE.Mesh(zoneGeo, zoneMat)
    zoneMesh.position.y = 0.02
    pointGroup.add(zoneMesh)

    const rippleCount = 3
    const rippleMeshes = []
    for (let i = 0; i < rippleCount; i++) {
      const rippleGeo = new THREE.RingGeometry(0.5, 0.8, 32)
      const rippleMat = new THREE.MeshBasicMaterial({
        color: new THREE.Color(h.color),
        transparent: true,
        opacity: 0.6,
        side: THREE.DoubleSide,
        depthWrite: false,
        depthTest: false
      })
      const rippleMesh = new THREE.Mesh(rippleGeo, rippleMat)
      rippleMesh.rotation.x = -Math.PI / 2
      rippleMesh.position.y = 0.5
      rippleMesh.renderOrder = 999
      pointGroup.add(rippleMesh)
      rippleMeshes.push({ mesh: rippleMesh, phase: i * (Math.PI * 2 / rippleCount) })
    }

    pointGroup.userData.ripples = rippleMeshes

    scene.add(pointGroup)

    const labelDiv = document.createElement('div')
    labelDiv.className = 'absolute pointer-events-auto flex flex-col items-center select-none cursor-pointer'
    const isWarn = h.type === 'warn'
    const statusColor = isWarn ? '#ff6a00' : '#08ae66'
    const statusColorAlpha = isWarn ? 'rgba(255,106,0,0.12)' : 'rgba(8,174,102,0.12)'
    const statusLabel = isWarn ? '注意' : '正常'
    const glowColor = isWarn ? 'rgba(255,106,0,0.22)' : 'rgba(8,174,102,0.22)'
    const bgPanel = isDark ? 'rgba(10,25,50,0.72)' : 'rgba(255,255,255,0.78)'
    const borderColor = isDark ? 'rgba(100,180,255,0.18)' : 'rgba(140,180,230,0.35)'
    const descHtml = h.desc.map(d =>
      `<span style="font-size:10px;font-weight:600;padding:1px 6px;border-radius:6px;background:${statusColorAlpha};color:${statusColor};white-space:nowrap;text-align:center;">${d}</span>`
    ).join('')
    const descGridStyle = h.desc.length > 3
      ? 'display:grid;grid-template-columns:1fr 1fr;gap:3px;justify-items:center;'
      : 'display:flex;flex-wrap:wrap;justify-content:center;gap:3px;'

    labelDiv.innerHTML = `
      <div style="
        backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);
        background:${bgPanel};
        border:1px solid ${borderColor};
        border-top:2.5px solid ${statusColor};
        border-radius:14px;
        box-shadow:0 4px 24px ${glowColor},inset 0 1px 0 rgba(255,255,255,0.08);
        padding:20px 14px;
        min-width:140px;
        text-align:center;
        transition:transform .2s,box-shadow .2s;
        position:relative;
        overflow:hidden;
      " class="mb-1 hover:scale-105 zone-panel-inner">
        <div style="display:flex;align-items:center;justify-content:center;gap:5px;margin-bottom:12px;">
          <span style="width:7px;height:7px;border-radius:50%;background:${statusColor};box-shadow:0 0 6px ${statusColor};display:inline-block;flex-shrink:0;"></span>
          <span style="font-size:15px;font-weight:800;color:var(--text);letter-spacing:0.5px;">${zone.name}</span>${zone.envStatus === '注意' ? `<div class="panel-ai-btn btn-flash" data-zone-idx="${zoneIdx}" style="cursor:pointer;margin-left:4px;flex-shrink:0;">
            <div class="ai-orb small" style="width:24px;height:24px;min-width:24px;min-height:24px;font-size:9px;">AI</div>
          </div>` : ''}
        </div>
        <div style="${descGridStyle}">
          ${descHtml}
        </div>
        <div class="scan-line" style="position:absolute;top:0;left:0;right:0;height:3px;background:linear-gradient(90deg,transparent,${statusColor},transparent);opacity:0;pointer-events:none;"></div>
      </div>
      <svg width="20" height="80" viewBox="0 0 20 80" style="display:block;flex-shrink:0;overflow:visible;">
        <defs>
          <marker id="arrow-${zoneIdx}" markerWidth="10" markerHeight="8" refX="5" refY="4" orient="auto" markerUnits="userSpaceOnUse">
            <polygon points="0 0, 10 4, 0 8" fill="${h.color}" opacity="0.75"/>
          </marker>
        </defs>
        <line x1="10" y1="0" x2="10" y2="74" stroke="${h.color}" stroke-width="1.5" stroke-dasharray="4,3" opacity="0.6" marker-end="url(#arrow-${zoneIdx})"/>
      </svg>
    `

    const aiBtn = labelDiv.querySelector('.panel-ai-btn')
    if (aiBtn) {
      aiBtn.addEventListener('click', (e) => {
        e.stopPropagation()
        openModalWithZone(zone)
      })
    }

    labelDiv.addEventListener('click', () => {
      openModalWithZone(zone)
    })

    labelsContainer.appendChild(labelDiv)
    pointsInstances.push({ threeObject: pointGroup, domElement: labelDiv })
  })

  const clock = new THREE.Clock()

  function animate() {
    heatmapAnimFrame = requestAnimationFrame(animate)
    const time = clock.getElapsedTime()
    buildingGroup.children.forEach(mesh => {
      if (mesh.isLineSegments || mesh.geometry.type === 'PlaneGeometry') {
        mesh.position.y = Math.sin(time * 0.5) * 0.1
      }
    })
    pointsInstances.forEach(item => {
      const ripples = item.threeObject.userData.ripples
      if (ripples) {
        ripples.forEach(r => {
          const t = (time * 0.8 + r.phase) % (Math.PI * 2)
          const progress = t / (Math.PI * 2)
          const scale = 1 + progress * 8
          r.mesh.scale.set(scale, scale, 1)
          r.mesh.material.opacity = 0.6 * (1 - progress)
        })
      }
    })
    controls.update()
    updateLabelsPosition()
    renderer.render(scene, camera)
  }
  animate()
}

function updateLabelsPosition() {
  if (!threeCamera || !heatmapContainer.value) return
  const container = heatmapContainer.value
  const rect = container.getBoundingClientRect()
  const wp = new THREE.Vector3()
  pointsInstances.forEach(item => {
    item.threeObject.getWorldPosition(wp)
    wp.project(threeCamera)
    if (wp.z > 1) {
      item.domElement.style.display = 'none'
      return
    }
    item.domElement.style.display = 'flex'
    const x = (wp.x * 0.5 + 0.5) * rect.width
    const y = (-(wp.y * 0.5) + 0.5) * rect.height
    item.domElement.style.left = x + 'px'
    item.domElement.style.top = y + 'px'
    const scale = Math.max(0.7, Math.min(1.2, 1.5 - wp.z))
    item.domElement.style.transform = `translate(-50%, calc(-100% - 6px)) scale(${scale})`
  })
}

function stopHeatmap() {
  if (heatmapAnimFrame) {
    cancelAnimationFrame(heatmapAnimFrame)
    heatmapAnimFrame = null
  }
  if (threeRenderer) {
    threeRenderer.dispose()
    threeRenderer = null
  }
  threeScene = null
  threeCamera = null
  threeControls = null
  pointsInstances = []
}

function initModalThreeHeatmap() {
  const container = modalHeatmapContainer.value
  const labelsContainer = modalHeatmapLabels.value
  if (!container || !labelsContainer) return

  // Clear previous
  while (container.firstChild) container.removeChild(container.firstChild)
  while (labelsContainer.firstChild) labelsContainer.removeChild(labelsContainer.firstChild)
  modalPointsInstances = []

  const rect = container.getBoundingClientRect()

  const isDark = document.documentElement.getAttribute('data-theme') === 'dark'
  const bgColor = isDark ? 0x0a1929 : 0xf8fbff
  const gridColor1 = isDark ? 0x1a3a5c : 0xc3daf7
  const gridColor2 = isDark ? 0x2a5a8c : 0xd0e1f9
  const fogColor = isDark ? 0x0d2137 : 0xe3eafd

  const scene = new THREE.Scene()
  scene.background = new THREE.Color(bgColor)
  scene.fog = new THREE.FogExp2(fogColor, 0.008)
  modalThreeScene = scene

  const camera = new THREE.PerspectiveCamera(45, rect.width / rect.height, 1, 1000)
  camera.position.set(38, 16, 0)
  modalThreeCamera = camera

  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setPixelRatio(window.devicePixelRatio)
  renderer.setSize(rect.width, rect.height)
  renderer.setClearColor(bgColor, 1)
  container.appendChild(renderer.domElement)
  modalThreeRenderer = renderer

  const controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.05
  controls.maxPolarAngle = Math.PI / 2.1
  modalThreeControls = controls

  const ambientLight = new THREE.AmbientLight(0xffffff, 0.8)
  scene.add(ambientLight)
  const dirLight = new THREE.DirectionalLight(0xffffff, 0.4)
  dirLight.position.set(20, 40, 20)
  scene.add(dirLight)

  const gridHelper = new THREE.GridHelper(100, 40, gridColor1, gridColor2)
  gridHelper.position.y = -0.1
  scene.add(gridHelper)

  const buildingGroup = new THREE.Group()

  const roofColor = isDark ? 0x00d4ff : 0x3b82f6
  const roofWireColor = isDark ? 0x00e5ff : 0x60a5fa

  const roofWidth = 26, roofLength = 50, segments = 30
  const roofGeo = new THREE.PlaneGeometry(roofWidth, roofLength, segments, segments)
  roofGeo.rotateX(-Math.PI / 2)
  const posAttr = roofGeo.attributes.position
  for (let i = 0; i < posAttr.count; i++) {
    let x = posAttr.getX(i)
    let z = posAttr.getZ(i)
    const zNorm = z / (roofLength / 2)
    const xScale = 1 + zNorm * zNorm * 0.5
    posAttr.setX(i, x * xScale)
    let y = (z * z) * 0.006 - (x * xScale) * (x * xScale) * 0.004 + 6
    posAttr.setY(i, y)
  }
  roofGeo.computeVertexNormals()

  const roofMat = new THREE.MeshStandardMaterial({ color: roofColor, transparent: true, opacity: isDark ? 0.1 : 0.15, side: THREE.DoubleSide })
  const centerRoof = new THREE.Mesh(roofGeo, roofMat)
  buildingGroup.add(centerRoof)

  const roofWireMat = new THREE.MeshBasicMaterial({ color: roofWireColor, wireframe: true, transparent: true, opacity: isDark ? 0.3 : 0.4 })
  const centerRoofWire = new THREE.Mesh(roofGeo, roofWireMat)
  buildingGroup.add(centerRoofWire)

  scene.add(buildingGroup)

  // 3层楼板（1层y=0, 2层y=2, 3层y=4），与屋顶同宽同长同弧度
  const floorGap = 2
  const floorColor = isDark ? 0x00d4ff : 0x3b82f6
  const floorWireColor = isDark ? 0x00e5ff : 0x60a5fa
  const floorWidth = roofWidth, floorLength = roofLength
  const pillarColor = isDark ? 0x1a5a8c : 0x93c5fd
  const floorNames = ['1层', '2层', '3层']

  // 生成与屋顶同弧度的楼板几何体
  function createCurvedFloorGeo(w, l, segW, segL) {
    const geo = new THREE.PlaneGeometry(w, l, segW, segL)
    geo.rotateX(-Math.PI / 2)
    const attr = geo.attributes.position
    for (let i = 0; i < attr.count; i++) {
      const x = attr.getX(i)
      const z = attr.getZ(i)
      const zNorm = z / (l / 2)
      const xScale = 1 + zNorm * zNorm * 0.5
      attr.setX(i, x * xScale)
    }
    return geo
  }

  for (let level = 0; level < 3; level++) {
    const floorY = level * floorGap

    // 楼板填充
    const floorGeo = createCurvedFloorGeo(floorWidth, floorLength, 2, 30)
    const floorMat = new THREE.MeshBasicMaterial({
      color: floorColor,
      transparent: true,
      opacity: isDark ? 0.06 : 0.09,
      side: THREE.DoubleSide
    })
    const floorMesh = new THREE.Mesh(floorGeo, floorMat)
    floorMesh.position.y = floorY
    scene.add(floorMesh)

    // 楼板网格线
    const gridGeo = createCurvedFloorGeo(floorWidth, floorLength, 6, 30)
    const gridWireMat = new THREE.MeshBasicMaterial({ color: floorWireColor, wireframe: true, transparent: true, opacity: isDark ? 0.08 : 0.12 })
    const gridWire = new THREE.Mesh(gridGeo, gridWireMat)
    gridWire.position.y = floorY + 0.01
    scene.add(gridWire)

    // 楼板边框
    const floorEdgeGeo = new THREE.EdgesGeometry(createCurvedFloorGeo(floorWidth, floorLength, 2, 30))
    const floorLineMat = new THREE.LineBasicMaterial({ color: floorWireColor, transparent: true, opacity: isDark ? 0.35 : 0.45 })
    const floorLine = new THREE.LineSegments(floorEdgeGeo, floorLineMat)
    floorLine.position.y = floorY
    scene.add(floorLine)

    // 层级名称标签
    const labelCanvas = document.createElement('canvas')
    labelCanvas.width = 128
    labelCanvas.height = 64
    const ctx = labelCanvas.getContext('2d')
    ctx.fillStyle = isDark ? 'rgba(0,212,255,0.85)' : 'rgba(59,130,246,0.85)'
    ctx.font = 'bold 36px sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(floorNames[level], 64, 32)
    const labelTexture = new THREE.CanvasTexture(labelCanvas)
    const labelMat = new THREE.SpriteMaterial({ map: labelTexture, transparent: true, depthTest: false })
    const labelSprite = new THREE.Sprite(labelMat)
    labelSprite.scale.set(4, 2, 1)
    labelSprite.position.set(floorWidth / 2 + 3, floorY + 0.8, 0)
    scene.add(labelSprite)
  }

  // 四角立柱连接各层
  const pillarHeight = floorGap * 2
  const pillarGeo = new THREE.CylinderGeometry(0.15, 0.15, pillarHeight, 8)
  const pillarMat = new THREE.MeshBasicMaterial({ color: pillarColor, transparent: true, opacity: isDark ? 0.3 : 0.4 })
  const phw = floorWidth / 2
  const phl = floorLength / 2
  ;[[-phw, -phl], [-phw, phl], [phw, -phl], [phw, phl]].forEach(([px, pz]) => {
    const pillar = new THREE.Mesh(pillarGeo, pillarMat)
    pillar.position.set(px, floorGap, pz)
    scene.add(pillar)
  })

  zoneAreas.value.forEach((zone, zoneIdx) => {
    const h = zone.heat
    if (!h) return
    const pointGroup = new THREE.Group()
    pointGroup.position.set(h.pos.x, h.pos.y, h.pos.z)
    pointGroup.userData = { zone, heat: h }

    const ballGeo = new THREE.SphereGeometry(0.6, 16, 16)
    const ballMat = new THREE.MeshBasicMaterial({ color: new THREE.Color(h.color) })
    const ball = new THREE.Mesh(ballGeo, ballMat)
    pointGroup.add(ball)

    const zoneGeo = new THREE.CylinderGeometry(4, 4, 0.2, 32)
    const zoneMat = new THREE.MeshBasicMaterial({
      color: new THREE.Color(h.color),
      transparent: true,
      opacity: h.type === 'ok' ? 0.05 : 0.2,
      wireframe: true
    })
    const zoneMesh = new THREE.Mesh(zoneGeo, zoneMat)
    zoneMesh.position.y = 0.02
    pointGroup.add(zoneMesh)

    const rippleCount = 3
    const rippleMeshes = []
    for (let i = 0; i < rippleCount; i++) {
      const rippleGeo = new THREE.RingGeometry(0.5, 0.8, 32)
      const rippleMat = new THREE.MeshBasicMaterial({
        color: new THREE.Color(h.color),
        transparent: true,
        opacity: 0.6,
        side: THREE.DoubleSide,
        depthWrite: false,
        depthTest: false
      })
      const rippleMesh = new THREE.Mesh(rippleGeo, rippleMat)
      rippleMesh.rotation.x = -Math.PI / 2
      rippleMesh.position.y = 0.5
      rippleMesh.renderOrder = 999
      pointGroup.add(rippleMesh)
      rippleMeshes.push({ mesh: rippleMesh, phase: i * (Math.PI * 2 / rippleCount) })
    }

    pointGroup.userData.ripples = rippleMeshes

    scene.add(pointGroup)

    const labelDiv = document.createElement('div')
    labelDiv.className = 'absolute pointer-events-auto flex flex-col items-center select-none cursor-pointer'
    const isWarn = h.type === 'warn'
    const statusColor = isWarn ? '#ff6a00' : '#08ae66'
    const statusColorAlpha = isWarn ? 'rgba(255,106,0,0.12)' : 'rgba(8,174,102,0.12)'
    const statusLabel = isWarn ? '注意' : '正常'
    const glowColor = isWarn ? 'rgba(255,106,0,0.22)' : 'rgba(8,174,102,0.22)'
    const bgPanel = isDark ? 'rgba(10,25,50,0.72)' : 'rgba(255,255,255,0.78)'
    const borderColor = isDark ? 'rgba(100,180,255,0.18)' : 'rgba(140,180,230,0.35)'
    const descHtml = h.desc.map(d =>
      `<span style="font-size:10px;font-weight:600;padding:1px 6px;border-radius:6px;background:${statusColorAlpha};color:${statusColor};white-space:nowrap;text-align:center;">${d}</span>`
    ).join('')
    const descGridStyle = h.desc.length > 3
      ? 'display:grid;grid-template-columns:1fr 1fr;gap:3px;justify-items:center;'
      : 'display:flex;flex-wrap:wrap;justify-content:center;gap:3px;'

    labelDiv.innerHTML = `
      <div style="
        backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);
        background:${bgPanel};
        border:1px solid ${borderColor};
        border-top:2.5px solid ${statusColor};
        border-radius:14px;
        box-shadow:0 4px 24px ${glowColor},inset 0 1px 0 rgba(255,255,255,0.08);
        padding:20px 14px;
        min-width:140px;
        text-align:center;
        transition:transform .2s,box-shadow .2s;
        position:relative;
        overflow:hidden;
      " class="mb-1 hover:scale-105 zone-panel-inner">
        <div style="display:flex;align-items:center;justify-content:center;gap:5px;margin-bottom:12px;">
          <span style="width:7px;height:7px;border-radius:50%;background:${statusColor};box-shadow:0 0 6px ${statusColor};display:inline-block;flex-shrink:0;"></span>
          <span style="font-size:15px;font-weight:800;color:var(--text);letter-spacing:0.5px;">${zone.name}</span>${zone.envStatus === '注意' ? `<div class="panel-ai-btn btn-flash" data-zone-idx="${zoneIdx}" style="cursor:pointer;margin-left:4px;flex-shrink:0;">
            <div class="ai-orb small" style="width:24px;height:24px;min-width:24px;min-height:24px;font-size:9px;">AI</div>
          </div>` : ''}
        </div>
        <div style="${descGridStyle}">
          ${descHtml}
        </div>
        <div class="scan-line" style="position:absolute;top:0;left:0;right:0;height:3px;background:linear-gradient(90deg,transparent,${statusColor},transparent);opacity:0;pointer-events:none;"></div>
      </div>
      <svg width="20" height="80" viewBox="0 0 20 80" style="display:block;flex-shrink:0;overflow:visible;">
        <defs>
          <marker id="modal-arrow-${zoneIdx}" markerWidth="10" markerHeight="8" refX="5" refY="4" orient="auto" markerUnits="userSpaceOnUse">
            <polygon points="0 0, 10 4, 0 8" fill="${h.color}" opacity="0.75"/>
          </marker>
        </defs>
        <line x1="10" y1="0" x2="10" y2="74" stroke="${h.color}" stroke-width="1.5" stroke-dasharray="4,3" opacity="0.6" marker-end="url(#modal-arrow-${zoneIdx})"/>
      </svg>
    `

    const aiBtn = labelDiv.querySelector('.panel-ai-btn')
    if (aiBtn) {
      aiBtn.addEventListener('click', (e) => {
        e.stopPropagation()
        openModalWithZone(zone)
      })
    }

    labelDiv.addEventListener('click', () => {
      openModalWithZone(zone)
    })

    labelsContainer.appendChild(labelDiv)
    modalPointsInstances.push({ threeObject: pointGroup, domElement: labelDiv })
  })

  const clock = new THREE.Clock()

  function animate() {
    modalHeatmapAnimFrame = requestAnimationFrame(animate)
    const time = clock.getElapsedTime()
    buildingGroup.children.forEach(mesh => {
      if (mesh.isLineSegments || mesh.geometry.type === 'PlaneGeometry') {
        mesh.position.y = Math.sin(time * 0.5) * 0.1
      }
    })
    modalPointsInstances.forEach(item => {
      const ripples = item.threeObject.userData.ripples
      if (ripples) {
        ripples.forEach(r => {
          const t = (time * 0.8 + r.phase) % (Math.PI * 2)
          const progress = t / (Math.PI * 2)
          const scale = 1 + progress * 8
          r.mesh.scale.set(scale, scale, 1)
          r.mesh.material.opacity = 0.6 * (1 - progress)
        })
      }
    })
    controls.update()
    updateModalLabelsPosition()
    renderer.render(scene, camera)
  }
  animate()
}

function updateModalLabelsPosition() {
  if (!modalThreeCamera || !modalHeatmapContainer.value) return
  const container = modalHeatmapContainer.value
  const rect = container.getBoundingClientRect()
  const wp = new THREE.Vector3()
  modalPointsInstances.forEach(item => {
    item.threeObject.getWorldPosition(wp)
    wp.project(modalThreeCamera)
    if (wp.z > 1) {
      item.domElement.style.display = 'none'
      return
    }
    item.domElement.style.display = 'flex'
    const x = (wp.x * 0.5 + 0.5) * rect.width
    const y = (-(wp.y * 0.5) + 0.5) * rect.height
    item.domElement.style.left = x + 'px'
    item.domElement.style.top = y + 'px'
    const scale = Math.max(0.7, Math.min(1.2, 1.5 - wp.z))
    item.domElement.style.transform = `translate(-50%, calc(-100% - 6px)) scale(${scale})`
  })
}

function stopModalHeatmap() {
  if (modalHeatmapAnimFrame) {
    cancelAnimationFrame(modalHeatmapAnimFrame)
    modalHeatmapAnimFrame = null
  }
  if (modalThreeRenderer) {
    modalThreeRenderer.dispose()
    modalThreeRenderer = null
  }
  modalThreeScene = null
  modalThreeCamera = null
  modalThreeControls = null
  modalPointsInstances = []
}

function initModalHeatmap() {
  stopModalHeatmap()
  nextTick(() => {
    setTimeout(() => {
      initModalThreeHeatmap()
    }, 200)
  })
}

function getEnvValue(zone, name) {
  if (!zone || !zone.env) return ''
  const item = zone.env.find(e => e.name === name)
  return item ? item.value : ''
}

onMounted(() => {
  const savedTheme = localStorage.getItem('theme')
  if (savedTheme === 'light') {
    isDarkTheme.value = false
  } else {
    applyTheme('dark')
    isDarkTheme.value = true
  }
  
  // 检测屏幕高度并动态调整样式
  const checkScreenHeight = () => {
    const screenHeight = window.innerHeight
    if (screenHeight > 964) {
      document.documentElement.style.setProperty('--top-grid-margin-top', '40px')
      document.documentElement.style.setProperty('--divider-title-padding', '20px')
      document.documentElement.style.setProperty('--ai-banner-margin', '30px 0')
      document.documentElement.style.setProperty('--page-header-height', '80px')
      document.documentElement.style.setProperty('--header-station-left-top', '40px')
      document.documentElement.style.setProperty('--header-station-left-font-size', '30px')
      document.documentElement.style.setProperty('--header-station-name-top', '40px')
      document.documentElement.style.setProperty('--header-station-name-font-size', '40px')
      document.documentElement.style.setProperty('--header-right-margin-top', '30px')
    } else {
      document.documentElement.style.setProperty('--top-grid-margin-top', '0')
      document.documentElement.style.setProperty('--divider-title-padding', '')
      document.documentElement.style.setProperty('--ai-banner-margin', '10px 0')
      document.documentElement.style.setProperty('--page-header-height', '')
      document.documentElement.style.setProperty('--header-station-left-top', '20px')
      document.documentElement.style.setProperty('--header-station-left-font-size', '20px')
      document.documentElement.style.setProperty('--header-station-name-top', '20px')
      document.documentElement.style.setProperty('--header-station-name-font-size', '28px')
      document.documentElement.style.setProperty('--header-right-margin-top', '0')
    }
  }
  
  checkScreenHeight()
  window.addEventListener('resize', checkScreenHeight)
  
  initSparklineChart()
  initEnergyChart()
  nextTick(() => {
    setTimeout(() => {
      initThreeHeatmap()
    }, 300)
  })
})

onUnmounted(() => {
  window.clearTimeout(botReplyTimer)
  sparklineChart?.dispose()
  energyChart?.dispose()
  stopHeatmap()
  stopModalHeatmap()
  if (deviceControlTimer) {
    clearTimeout(deviceControlTimer)
  }
  if (deviceCloseTimer) {
    clearTimeout(deviceCloseTimer)
  }
})
// ------- 语音服务订阅 start -------
onMounted(() => {
  window.addEventListener("message", handleServiceMessage);
});
onUnmounted(() => {
  window.removeEventListener("message", handleServiceMessage);
});
const handleServiceMessage = (event) => {
  if (event.source !== window) return;
  if (event.data?.type !== "VISION_VOICE_EVENT") return;
  const { subscribeId, subscribeEvent } = event.data.data;
  if (subscribeId !== "OpenIframe") return;
  if (subscribeEvent === "打开智慧助手") {
    toggleAssistant()
  } else if(subscribeEvent === "关闭智慧助手") {
    closeAssistantModal()
  } else if(subscribeEvent === "打开知识库") {
    goKnowledgeBase()
  } else if(subscribeEvent === "关闭知识库") {
    closeKnowledgeModal()
  } else if(subscribeEvent === "打开运维") {
    openOpsAgent()
  } else if(subscribeEvent === "关闭运维") {
    closeOpsModal()
  }
};
// ------- 语音服务订阅 end -------
</script>

<template>
<svg width="0" height="0" aria-hidden="true" focusable="false">
    <symbol id="i-leaf" viewBox="0 0 24 24">
      <path d="M20.8 3.2C12.9 3.5 6.3 6.5 4 13.5c2.8-1.6 5.8-2.1 9.3-1.6-4.2 1.4-6.7 3.8-8 7.2" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"/>
      <path d="M5.3 16.5c7.8.5 12.6-4.1 15.5-13.3" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round"/>
    </symbol>
    <symbol id="i-gear" viewBox="0 0 24 24">
      <path d="M10.1 2.8h3.8l.8 2.7 2.5 1.1 2.5-1.3 1.9 3.3-2.1 1.8v3.2l2.1 1.8-1.9 3.3-2.5-1.3-2.5 1.1-.8 2.7h-3.8l-.8-2.7-2.5-1.1-2.5 1.3-1.9-3.3 2.1-1.8v-3.2L2.4 8.6l1.9-3.3 2.5 1.3 2.5-1.1.8-2.7Z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/>
      <circle cx="12" cy="12" r="3.2" fill="none" stroke="currentColor" stroke-width="2"/>
    </symbol>
    <symbol id="i-bell" viewBox="0 0 24 24">
      <path d="M5 18h14l-1.5-2.5V10a5.5 5.5 0 0 0-11 0v5.5L5 18Z" fill="currentColor"/>
      <path d="M9.4 19.2a2.8 2.8 0 0 0 5.2 0" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
    </symbol>
    <symbol id="i-home" viewBox="0 0 24 24">
      <path d="M3.5 11.5 12 4l8.5 7.5" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"/>
      <path d="M6 10.7V20h12v-9.3" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linejoin="round"/>
      <path d="M10 20v-5h4v5" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linejoin="round"/>
    </symbol>
    <symbol id="i-shield" viewBox="0 0 24 24">
      <path d="M12 2.6 20 6v5.8c0 5.1-3.4 8.3-8 10-4.6-1.7-8-4.9-8-10V6l8-3.4Z" fill="currentColor"/>
      <path d="m7.8 12.3 2.7 2.8 5.9-6.4" fill="none" stroke="#fff" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
    </symbol>
    <symbol id="i-bolt" viewBox="0 0 24 24">
      <path d="M13 2 4.8 13.2h5.7L9.8 22 19.2 9.8h-6L13 2Z" fill="currentColor"/>
    </symbol>
    <symbol id="i-thermo" viewBox="0 0 24 24">
      <path d="M10 14.6V5a3 3 0 1 1 6 0v9.6a5 5 0 1 1-6 0Z" fill="none" stroke="currentColor" stroke-width="2.1"/>
      <path d="M13 8v8" stroke="currentColor" stroke-width="2.1" stroke-linecap="round"/>
    </symbol>
    <symbol id="i-user" viewBox="0 0 24 24">
      <circle cx="12" cy="8" r="4" fill="currentColor"/>
      <path d="M4 21a8 8 0 0 1 16 0" fill="currentColor"/>
    </symbol>
    <symbol id="i-chip" viewBox="0 0 24 24">
      <rect x="6" y="6" width="12" height="12" rx="2.5" fill="none" stroke="currentColor" stroke-width="2"/>
      <path d="M9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M2 15h4M18 9h4M18 15h4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
    </symbol>
    <symbol id="i-flower" viewBox="0 0 24 24">
      <path d="M12 8V3M12 21v-5M8 12H3M21 12h-5M9.2 9.2 5.7 5.7M18.3 18.3l-3.5-3.5M14.8 9.2l3.5-3.5M5.7 18.3l3.5-3.5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>
      <circle cx="12" cy="12" r="3.6" fill="none" stroke="currentColor" stroke-width="2.2"/>
    </symbol>
    <symbol id="i-snow" viewBox="0 0 24 24">
      <path d="M12 2v20M4 6l16 12M20 6 4 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
    </symbol>
    <symbol id="i-cube" viewBox="0 0 24 24">
      <path d="M12 2.8 20 7.2v9.6l-8 4.4-8-4.4V7.2l8-4.4Z" fill="none" stroke="currentColor" stroke-width="2"/>
      <path d="M4.4 7.5 12 12l7.6-4.5M12 12v8.8" stroke="currentColor" stroke-width="2"/>
    </symbol>
    <symbol id="i-arrow" viewBox="0 0 24 24">
      <path d="m9 5 7 7-7 7" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
    </symbol>
    <symbol id="i-water" viewBox="0 0 24 24">
      <path d="M12 3s6 7 6 11.2a6 6 0 0 1-12 0C6 10 12 3 12 3Z" fill="none" stroke="currentColor" stroke-width="2.2"/>
    </symbol>
  </svg>

  <main class="dashboard">
    <header class="page-header">
      <img :src="headerBgImage" style="position: absolute; top: 0; left: 0px; width: 100%; height: 100%; object-fit: contain; z-index: 0;" alt="logo" class="header-logo" />
      <div class="header-station-left">Dashboard</div>
      <div class="header-station-name">站房智慧底座</div>
      <div class="header-station-right">
          <div class="header-right">
          <div class="header-badge">
            <span class="pulse"></span>
            系统运行正常
          </div>
          <button class="theme-toggle" @click="toggleTheme" aria-label="切换主题">🌓 切换主题</button>
        </div>
      </div>  
    </header>

    <section class="top-grid" aria-label="站房运行概览顶部指标">
      <div class="status-card-wrapper" @click="openHealthModal">
        <article class="card status-card">
          <div class="card-title">
            <span class="title-left"><svg class="ico"><use href="#i-gear"></use></svg>站房整体状态评估</span>
          </div>
          <div class="health-main">
            <svg class="shield green" viewBox="0 0 24 24"><use href="#i-shield"></use></svg>
            <div class="health-state">
              <strong>运行健康</strong>
            </div>
          </div>
          <p class="health-desc">全域运行平稳，用能态势优良，系统运行正常</p>
          <div ref="sparklineChartRef" class="sparkline-chart" aria-label="健康趋势"></div>
        </article>
      </div>

      <div class="status-card-wrapper" style="--hover-color: var(--blue);">
        <article class="card energy-card">
          <div class="card-title headline-tight">
            <span class="title-left"><svg class="ico"><use href="#i-leaf"></use></svg>车站能耗</span>
          </div>
          <div class="legend">
            <span class="legend-item" style="color:#1268ff"><i class="line-key"></i>能耗预测曲线</span>
            <span class="legend-item" style="color:#0caf64"><i class="line-key"></i>总能耗用曲线</span>
            <span class="legend-item" style="color:#5b39ff"><i class="line-key dash"></i>客流曲线</span>
            <span class="legend-item" style="color:#ff2116"><i class="line-key dash"></i>标准能耗基线</span>
          </div>
          <div ref="energyChartRef" class="energy-chart-container" aria-label="车站能耗实时曲线"></div>
        </article>
      </div>

<!--      <article class="card">-->
<!--        <div class="card-title">-->
<!--          <span class="title-left"><svg class="ico"><use href="#i-cube"></use></svg>环境监测</span>-->
<!--          <span class="more">更多 <svg width="14" height="14"><use href="#i-arrow"></use></svg></span>-->
<!--        </div>-->
<!--        <div class="env-table">-->
<!--          <div class="head">区域</div><div class="head">温度(°C)</div><div class="head">湿度(%)</div><div class="head">照度(lx)</div><div class="head">异常</div><div class="head">建议</div>-->
<!--          <div>站厅</div><div>24.2</div><div class="blue-text">48</div><div class="green-text">200</div><div>&#45;&#45;</div><div>&#45;&#45;</div>-->
<!--          <div>一站台</div><div>24.5</div><div class="blue-text">52</div><div class="green-text">240</div><div>&#45;&#45;</div><div>&#45;&#45;</div>-->
<!--          <div>二站台</div><div>24.8</div><div class="blue-text">48</div><div class="orange-text">100</div><div class="blue-text">照度低</div><div class="red-text">开灯</div>-->
<!--          <div>设备间</div><div>25.1</div><div class="blue-text">55</div><div class="green-text">100</div><div>&#45;&#45;</div><div>&#45;&#45;</div>-->
<!--        </div>-->
<!--      </article>-->

      <article class="card zone-model-card">
        <div class="card-title" style="margin-bottom: 6px;">
          <span class="title-left"><svg class="ico purple"><use href="#i-home"></use></svg><span class="blue-text">分区态势</span></span>
          <button class="view-toggle-icon" @click="switchZoneView(zoneViewMode === 'model' ? 'cards' : 'model')" :title="zoneViewMode === 'model' ? '切换卡片视图' : '切换模型视图'">
            <svg v-if="zoneViewMode === 'model'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
          </button>
          <span class="more" @click="openModal">更多 <svg width="14" height="14"><use href="#i-arrow"></use></svg></span>
        </div>

        <!-- 3D模型视图 -->
        <div v-show="zoneViewMode === 'model'" class="zone-model-section">
          <div ref="heatmapContainer" class="heatmap-canvas"></div>
          <div class="heatmap-labels" ref="heatmapLabels"></div>
        </div>

        <!-- 卡片视图 -->
        <div v-show="zoneViewMode === 'cards'" class="zone-cards-section">
          <div class="risk-grid">
            <div v-for="(zone, index) in zoneAreas" :key="index" class="status-card-wrapper" :style="{ '--hover-color': zone.wrapperColor}">
              <section class="risk-box" :class="zone.boxClass" @click="openModalWithZone(zone)" style="">
                <div class="risk-head" :class="zone.statusClass">{{ zone.name }} <span class="pill" :class="zone.pillClass">{{ zone.status }}</span></div>
                <div class="zone-data">
                  <div class="zone-env">
                    <span v-for="(e, i) in zone.env" :key="i" class="env-item" :class="{ 'env-abnormal': e.isAbnormal }">
                      {{ e.name }} <b>{{ e.value }}</b>{{ e.unit }}
                    </span>
                  </div>
                  <div class="zone-status">
                    <span class="status-item">环境指标 <b>{{ zone.envScore }}</b><b :class="zone.envStatusClass"> {{ zone.envStatus }}</b></span>
                    <span class="status-item">设备状况 <b :class="zone.deviceStatusClass">{{ zone.deviceStatus }}</b></span>
                    <span class="status-item">客流密度 <b>{{ zone.passengerDensity }}</b><b :class="zone.passengerStatusClass"> {{ zone.passengerStatus }}</b></span>
                    <span class="status-item">今日能耗 <b>{{ zone.energyToday }}</b> <b :class="zone.energyStatusClass">{{ zone.energyStatus }}</b></span>
                    <span class="wide">建议： <b :class="zone.suggestionClass">{{ zone.suggestion }}</b></span>
                  </div>
                </div>
              </section>
            </div>
          </div>
        </div>
      </article>
    </section>

    <section class="ai-banner" style="border:none;" aria-label="AI分析与建议">
      <div class="ai-brand">
        <div class="ai-orb">AI</div>
        <strong style="font-size:22px">实时分析与建议</strong>
      </div>
      <div class="ai-copy">夹层1号电梯 温度38°C，启停次数126次，载荷波动12%，整体运行状态平稳无异常。建议维持常规巡检频次，早高峰客流回落时段重点复核曳引系统温度，密切监测温升变化，适时调整运行负荷，提前规避高温运行隐患，保障乘梯通行稳定安全。</div>
      <div class="circle-btn"><svg width="16" height="16"><use href="#i-arrow"></use></svg></div>
    </section>

    <section class="mid-grid" aria-label="分析处理区域">
      <div class="status-card-wrapper" style="--hover-color: var(--blue);">
        <article class="card">
          <div class="card-title">
            <span class="title-left"><svg class="ico green"><use href="#i-leaf"></use></svg>服务分析</span>
          </div>
          <div class="service-metrics">
            <div class="service-box客流">
              <div class="service-label">客流(到/发)</div>
              <div class="service-value blue">12,458</div>
              <div class="service-value green">11,832</div>
            </div>
            <div class="service-box安全" @click="openSafetyModal" style="cursor: pointer;">
              <div class="service-label">安全指数</div>
              <div class="service-value green large">{{ safetyScore }}分</div>
              <span class="pill ok">安全</span>
            </div>
            <div class="service-box雷达">
              <svg viewBox="-20 -8 200 176" class="radar-chart" aria-label="环境雷达图">
                <polygon points="80,18 134,50 114,118 46,118 26,50" fill="rgba(18,104,255,0.12)" stroke="#b9cef2" stroke-width="1.5"/>
                <polygon points="80,38 118,60 102,106 58,106 42,60" fill="rgba(18,104,255,0.08)" stroke="#d8e5f7" stroke-width="1"/>
                <polygon points="80,58 102,70 92,94 68,94 58,70" fill="rgba(18,104,255,0.06)" stroke="#e8eef7" stroke-width="1"/>
                <line x1="80" y1="80" x2="80" y2="18" stroke="#c9d8f0" stroke-width="1"/>
                <line x1="80" y1="80" x2="134" y2="50" stroke="#c9d8f0" stroke-width="1"/>
                <line x1="80" y1="80" x2="114" y2="118" stroke="#c9d8f0" stroke-width="1"/>
                <line x1="80" y1="80" x2="46" y2="118" stroke="#c9d8f0" stroke-width="1"/>
                <line x1="80" y1="80" x2="26" y2="50" stroke="#c9d8f0" stroke-width="1"/>
                <polygon points="80,28 124,54 108,110 52,110 34,58" fill="rgba(18,104,255,0.22)" stroke="#1268ff" stroke-width="2"/>
                <circle cx="80" cy="28" r="3.5" fill="#1268ff"/>
                <circle cx="124" cy="54" r="3.5" fill="#1268ff"/>
                <circle cx="108" cy="110" r="3.5" fill="#1268ff"/>
                <circle cx="52" cy="110" r="3.5" fill="#1268ff"/>
                <circle cx="34" cy="58" r="3.5" fill="#1268ff"/>
                <text x="80" y="10" text-anchor="middle" class="tick" font-size="11">温度</text>
                <text x="138" y="50" text-anchor="start" class="tick" font-size="11">湿度</text>
                <text x="124" y="130" text-anchor="middle" class="tick" font-size="11">照度</text>
                <text x="36" y="130" text-anchor="middle" class="tick" font-size="11">气味</text>
                <text x="22" y="50" text-anchor="end" class="tick" font-size="11">噪声</text>
                <text x="80" y="84" text-anchor="middle" font-size="20" font-weight="900" fill="#08ae66">90</text>
              </svg>
            </div>
          </div>
          <div class="analysis">
            <p><strong>分析结论</strong>当前站房客流平稳，安全指数 94 分处于安全区间，环境整体达标，服务保障态势良好。</p>
            <p><strong class="suggest">建议措施</strong>可结合客流变化动态优化环境调控，持续维持旅客服务体验。</p>
          </div>
        </article>
      </div>

      <div class="status-card-wrapper" style="--hover-color: var(--blue);" @click="openDeviceAnalysis">
        <article class="card">
          <div class="card-title">
            <span class="title-left"><svg class="ico"><use href="#i-gear"></use></svg>设备分析</span>
          </div>
          <div class="device-stats">
          <div class="device-box故障">
            <div class="device-label">故障</div>
            <div class="device-value red">1</div>
          </div>
            <div class="device-box告警">
              <div class="device-label">告警</div>
              <div class="device-value orange">1</div>
            </div>
            <div class="device-box维修">
              <div class="device-label">维修</div>
              <div class="device-value green">1</div>
            </div>
          </div>
          <div class="analysis">
            <p><strong>分析结论</strong>3号水泵轴承振动超标（4.8mm/s），B区空调回风温度偏高（28.5°C），A区电梯存在轻微异响待检修。</p>
<!--          ，1台电梯存在轻微异响待检修        3. 对异响电梯加强巡检频次，周末安排维保。 -->
            <p><strong class="suggest">建议措施</strong>1. 优先安排3号水泵停机检修，更换轴承；2. 检查B区空调压缩机运行状态；3. 对异响电梯加强巡检频次，周末安排维保。</p>
          </div>
        </article>
      </div>

      <div class="status-card-wrapper" style="--hover-color: var(--blue);" @click="openWarningModal">
        <article class="card">
          <div class="card-title">
            <span class="title-left"><svg class="ico red"><use href="#i-bell"></use></svg>事项处理</span>
          </div>
          <div class="metric-row warning-stats">
            <div class="metric"><span class="label">告警总数</span><span class="value red-text">12</span></div>
            <div class="metric"><span class="label">已处理</span><span class="value green-text">8</span></div>
            <div class="metric"><span class="label">处理中</span><span class="value orange-text">2</span></div>
            <div class="metric"><span class="label">待处理</span><span class="value red-text">2</span></div>
          </div>
          <div style="height:14px"></div>
          <div class="analysis">
            <p><strong>分析结论</strong>当前 2 条待处理告警需尽快处理，设备影响运行。</p>
            <p><strong class="suggest">建议措施</strong>优先处理高温报警与设备离线告警，完成后检查相关工单。</p>
          </div>
        </article>
      </div>

      <div class="status-card-wrapper" style="--hover-color: var(--blue);">
        <article class="card">
          <div class="card-title">
            <span class="title-left"><svg class="ico purple"><use href="#i-flower"></use></svg><span class="blue-text">AI主动建议</span></span>
          <span class="more" >更多 <svg width="14" height="14"><use href="#i-arrow"></use></svg></span>
          </div>
          <div class="suggestions">
            <div class="suggestion">
<!--            <svg class="ico green"><use href="#i-leaf"></use></svg>-->
              <svg class="ico orange"><use href="#i-bolt"></use></svg>
              <span>建议对二站台温湿度阈值，期望湿度至 60%以下</span><b class="action">一键执行</b></div>
            <div class="suggestion"><svg class="ico orange"><use href="#i-bolt"></use></svg><span>建议对二站台新风提升至 3500m³/h</span><b class="action">一键执行</b></div>
            <div class="suggestion"><svg class="ico"><use href="#i-snow"></use></svg>

              <span>建议对冷源 2# 进行换热保养</span><b class="action">自动派单</b></div>
            <div class="suggestion">
              <svg class="ico"><use href="#i-gear"></use></svg>
<!--            <svg class="ico orange"><use href="#i-bell"></use></svg>-->
              <span>排风机1#离线告警建议优先处理</span><b class="action">生成工单</b></div>
          </div>
        </article>
      </div>
    </section>

    <div class="divider-title title-brand" style="cursor:pointer" @click="openBaseMonitorModal"><span>💻</span><span>底座实时监控</span></div>
    <section class="bottom-grid " aria-label="底座实时监控">
      <div class="status-card-wrapper" style="--hover-color: var(--blue);">
        <article class="card">
          <div class="card-title">
            <span class="title-left"><svg class="ico"><use href="#i-cube"></use></svg>算力实时指标</span>
          </div>
          <div class="compute-grid">
            <div class="metric"><span class="label">GPU使用率</span><span class="value">68%</span></div>
            <div class="metric"><span class="label">正在处理请求数</span><span class="value">128</span></div>
            <div class="metric"><span class="label">排队请求数</span><span class="value">36</span></div>
            <div class="metric"><span class="label">吞吐量(token/秒)</span><span class="value">1,248</span></div>
            <div class="metric"><span class="label">推理耗时(ms)</span><span class="value">86</span></div>
            <div class="metric"><span class="label">失败请求数</span><span class="value">2</span></div>
          </div>
        </article>
      </div>

      <div class="status-card-wrapper" style="--hover-color: var(--blue);">
        <article class="card">
          <div class="agent-title"><span class="title-left">
<!--            <svg class="ico green"><use href="#i-leaf"></use></svg>-->
            <svg class="ico orange"><use href="#i-bolt"></use></svg>
            能管Agent</span><span class="run-tag"><i class="dot"></i>运行中</span></div>
          <div class="agent-layout">
            <div>
            <div class="ring" style="--p:94"><span><b>120/128</b><small>已确认/今日决策</small></span></div>
          <div class="agent-footer" style="margin: 10px 20px; font-size: 13px; font-weight: 900; display: flex;flex-flow: row;">
            较昨日 <span class="green-text" style="margin-left:4px"> ↓ 4%</span>
          </div>
            </div>
            <div class="agent-kv">
              <span>在线状态</span><span class="right green-text">在线</span>
              <span>运行时长</span><span class="right">12天 4小时</span>
              <span>决策成功率</span><span class="right">96.2%</span>
              <span>API调用次数</span><span class="right">12,456</span>
              <span>平均响应时间</span><span class="right">320 ms</span>
              <span>词元消耗</span><span class="right">15.87M</span>
            </div>
          </div>
        </article>
      </div>

      <div class="status-card-wrapper" style="--hover-color: var(--blue);" @click="openPassengerAgent">
        <article class="card">
          <div class="agent-title"><span class="title-left">
            <svg class="ico"><use href="#i-gear"></use></svg>
            客流压力Agent</span><span class="run-tag"><i class="dot"></i>运行中</span></div>
          <div class="agent-layout">
            <div>
            <div class="ring" style="--p:92"><span><b>88/96</b><small>已确认/今日决策</small></span></div>
            
          <div class="agent-footer" style="margin: 10px 20px; font-size: 13px; font-weight: 900; display: flex;flex-flow: row;">
            较昨日 <span class="green-text" style="margin-left:4px"> ↓ 3%</span>
          </div></div>
            <div class="agent-kv">
              <span>在线状态</span><span class="right green-text">在线</span>
              <span>运行时长</span><span class="right">15天 8小时</span>
              <span>决策成功率</span><span class="right">95.6%</span>
              <span>API调用次数</span><span class="right">75,789</span>
              <span>平均响应时间</span><span class="right">280 ms</span>
              <span>词元消耗</span><span class="right">10.67M</span>
            </div>
          </div>
        </article>
      </div>

      <!-- ==================== 智慧助手模块（已注释） ==================== -->
      <!-- 如需启用智慧助手，取消下方注释，并注释掉运维Agent模块 -->
      <!--
      <div class="status-card-wrapper"  @click="toggleAssistant"  style="--hover-color: var(--blue);">
        <article class="card">
          <div class="agent-title"><span class="title-left"><svg class="ico"><use href="#i-chip"></use></svg>智慧助手</span><span class="run-tag"><i class="dot"></i>在线</span></div>
          <div class="cooling-layout">
            <div>
              <div class="compute-grid" style="grid-template-columns: repeat(2, 1fr); gap: 8px; margin-top: 10px;">
                <div class="metric" style="min-height: 72px;"><span class="label">今日对话</span><span class="value">156</span></div>
                <div class="metric" style="min-height: 72px;"><span class="label">平均响应</span><span class="value">1.2s</span></div>
                <div class="metric" style="min-height: 72px;"><span class="label">满意度</span><span class="value green-text">98%</span></div>
                <div class="metric" style="min-height: 72px;"><span class="label">解决问题</span><span class="value">142</span></div>
              </div>
            </div>
            <div class="agent-kv">
              <span>在线状态</span><span class="right green-text">在线</span>
              <span>运行时长</span><span class="right">30天 12小时</span>
              <span>累计对话</span><span class="right">4,567</span>
              <span>知识条目</span><span class="right">231</span>
              <span>模型版本</span><span class="right">v2.4.1</span>
              <span>支持能力</span><span class="right">6项</span>
            </div>
          </div>
        </article>
      </div>
      -->

      <!-- ==================== 运维Agent模块（当前启用） ==================== -->
      <!-- 如需切换回智慧助手，注释此模块，取消上方智慧助手的注释 -->
      <div class="status-card-wrapper" @click="openOpsAgent" style="--hover-color: var(--blue);">
        <article class="card">
          <div class="agent-title"><span class="title-left"><svg class="ico"><use href="#i-tools"></use></svg>运维Agent</span><span class="run-tag"><i class="dot"></i>运行中</span></div>
          <div class="cooling-layout">
            <div>
              <div class="ring" style="--p:96"><span><b>34/37</b><small>已诊断/今日故障</small></span></div>
              <div class="agent-footer" style="margin: 10px 20px; font-size: 13px; font-weight: 900; display: flex;flex-flow: row;">
                较昨日 <span class="green-text" style="margin-left:4px"> ↓ 2.5%</span>
              </div>
            </div>
            <div class="agent-kv">
              <span>运行状态</span><span class="right green-text">正常</span>
              <span>运行时长</span><span class="right">45天 8小时</span>
              <span>累计工单</span><span class="right">12,345</span>
              <span>巡检任务</span><span class="right">156</span>
              <span>模型版本</span><span class="right">v3.1.0</span>
              <span>运维能力</span><span class="right">12项</span>
            </div>
          </div>
        </article>
      </div>

      <div class="status-card-wrapper"   @click="goKnowledgeBase"  style="--hover-color: var(--blue);">
        <article class="card">
          <div class="agent-title"><span class="title-left"><svg class="ico"><use href="#i-cube"></use></svg>知识库统计</span></div>
          <div class="knowledge-layout">
            <div class="knowledge-ring"><span>231</span></div>
            <div class="k-legend">
              <span><i class="legend-dot" style="background:#156dff"></i>标准</span>
              <span><i class="legend-dot" style="background:#1fc3d7"></i>规范</span>
              <span><i class="legend-dot" style="background:#10b86b"></i>工程</span>
              <span><i class="legend-dot" style="background:#ff9933"></i>经验</span>
              <span><i class="legend-dot" style="background:#7b63f0"></i>预案</span>
              <span><i class="legend-dot" style="background:#ff4444"></i>指标</span>
              <span><i class="legend-dot" style="background:#20c4d6"></i>算法</span>
            </div>
          </div>
          <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; margin-top: 12px;">
            <div class="metric" style=" padding: 4px 6px;">
              <span class="label">检索次数</span>
              <span class="label">3456</span>
            </div>
            <div class="metric" style=" padding: 4px 6px;">
              <span class="label">知识命中率</span>
              <span class="label">87.5%</span>
            </div>
          </div>
        </article>
      </div>
    </section>
  </main>

  <!-- 运维Agent弹窗 -->
  <div v-if="isOpsModalOpen" class="ops-modal-overlay" @click.self="closeOpsModal">
    <div class="ops-modal-content">
      <button class="ops-modal-close" @click="closeOpsModal">×</button>
      <iframe 
        src="/dashboard/operations.html" 
        class="ops-modal-iframe"
        frameborder="0"
      ></iframe>
    </div>
  </div>

  <!-- 智慧助手弹窗 -->
  <div v-if="isAssistantModalOpen" class="assistant-modal-overlay" @click.self="closeAssistantModal">
    <div class="assistant-modal-content">
      <button class="assistant-modal-close" @click="closeAssistantModal">×</button>
      <iframe 
        src="http://192.168.128.82:3000/assistant/#/chat" 
        class="assistant-modal-iframe"
        frameborder="0"
      ></iframe>
    </div>
  </div>

  <!-- 知识库统计弹窗 -->
  <div v-if="isKnowledgeModalOpen" class="knowledge-modal-overlay" @click.self="closeKnowledgeModal">
    <div class="knowledge-modal-content">
      <button class="knowledge-modal-close" @click="closeKnowledgeModal">×</button>
      <iframe 
        src="http://192.168.128.82:3000/know/datasets" 
        class="knowledge-modal-iframe"
        frameborder="0"
      ></iframe>
    </div>
  </div>

  <div v-if="isModalOpen" class="modal-overlay" @click.self="closeModal">
    <div class="modal-content">
      <div class="modal-header">
        <h3>分区态势详情</h3>
        <button class="modal-close" @click="closeModal">×</button>
      </div>
      <div class="modal-upper">
        <div class="modal-heatmap-section">
          <div ref="modalHeatmapContainer" class="heatmap-canvas"></div>
          <div class="heatmap-labels" ref="modalHeatmapLabels"></div>
        </div>
        <div class="modal-zone-section">
          <!-- 未选中时显示区域列表 -->
          <template v-if="!selectedZone">
            <div v-for="(zone, index) in zoneAreas" :key="index" class="modal-zone-card" :class="[zone.boxClass, { 'zone-card-selected': selectedZone === zone }]" @click="selectZoneFromModel(zone)">
              <div class="modal-zone-header" :class="zone.statusClass">
                <span>{{ zone.name }}</span>
                <span class="header-right-group">
                  <span class="pill" :class="zone.pillClass">{{ zone.status }}</span>
                </span>
              </div>
              <div class="modal-zone-env">
                <span v-for="(e, i) in zone.env" :key="i" class="env-item" :class="{ 'env-abnormal': e.isAbnormal }">
                  {{ e.name }} <b>{{ e.value }}</b>{{ e.unit }}
                </span>
              </div>
              <div class="modal-zone-status">
                <div class="status-row">
                  <span class="status-item">环境指标 <b>{{ zone.envScore }}</b><b :class="zone.envStatusClass"> {{ zone.envStatus }}</b></span>
                </div>
                <div v-if="zone.anomalyEnv.length > 0" class="anomaly-list">
                  <div v-for="(a, i) in zone.anomalyEnv" :key="i" class="anomaly-item">
                    <label class="anomaly-check" @click.stop="toggleSelect(a)">
                      <input type="checkbox" v-model="a.selected" class="hidden-check"/>
                    </label>
                    <span class="anomaly-label">{{ a.item }}:</span>
                    <span class="anomaly-value">{{ a.value }}</span>
                    <span class="anomaly-standard">标准 {{ a.standard }}</span>
                    <span class="anomaly-status-tag">{{ a.status }}</span>
                  </div>
                </div>

                <div class="status-row">
                  <span class="status-item">设备状况 <b :class="zone.deviceStatusClass">{{ zone.deviceStatus }}</b></span>
                </div>
                <div v-if="zone.anomalyDevices.length > 0" class="anomaly-device-list">
                  <div v-for="(d, i) in zone.anomalyDevices" :key="i" class="anomaly-device-item" :class="{ blinking: d.blink }">
                    <div class="device-header">
                      <label class="device-check" @click.stop="toggleSelect(d)">
                        <input type="checkbox" v-model="d.selected" class="hidden-check"/>
                      </label>
                      <span class="device-name">{{ d.name }}</span>
                      <span class="device-type">{{ d.type }}</span>
                      <span class="device-status-tag" :class="d.status === '故障' ? 'tag-danger' : 'tag-warning'">{{ d.status }}</span>
                    </div>
                    <div class="device-info-row">
                      <span class="device-info-label">设备ID:</span><span>{{ d.id }}</span>
                      <span class="device-info-label">位置:</span><span>{{ d.location }}</span>
                    </div>
                    <div class="device-data-grid">
                      <span v-for="(cd, j) in d.currentData" :key="j" class="device-data-item">
                        <span class="data-label">{{ cd.label }}:</span><b>{{ cd.value }}</b>
                      </span>
                    </div>
                  </div>
                </div>

                <span class="status-item">客流密度 <b>{{ zone.passengerDensity }}</b><b :class="zone.passengerStatusClass"> {{ zone.passengerStatus }}</b></span>
                <span class="status-item">今日能耗 <b>{{ zone.energyToday }}</b> <b :class="zone.energyStatusClass">{{ zone.energyStatus }}</b></span>
                <span class="suggestion-text">建议：<b :class="zone.suggestionClass">{{ zone.suggestion }}</b></span>
              </div>
            </div>
          </template>

          <!-- 选中时显示区域详情卡片 -->
          <div v-else class="zone-detail-card" :class="selectedZone.boxClass">
            <div class="zone-detail-card-header">
              <div class="zone-detail-title-row">
                <span class="zone-detail-name">{{ selectedZone.name }}</span>
                <span class="pill" :class="selectedZone.pillClass">{{ selectedZone.status }}</span>
                <button class="zone-detail-close" @click="selectedZone = null; stopZoneTypewriter()">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M18 6L6 18M6 6l12 12"/></svg>
                </button>
              </div>
              <div class="zone-detail-summary">
                <span>环境 <b :class="selectedZone.envStatusClass">{{ selectedZone.envScore }}</b></span>
                <span>设备 <b :class="selectedZone.deviceStatusClass">{{ selectedZone.deviceStatus }}</b></span>
                <span>客流 <b :class="selectedZone.passengerStatusClass">{{ selectedZone.passengerDensity }}</b></span>
                <span>能耗 <b :class="selectedZone.energyStatusClass">{{ selectedZone.energyToday }}</b></span>
              </div>
            </div>

            <!-- 异常分析打字区 -->
            <div class="zone-analysis-box">
              <div class="zone-analysis-header">
                <div class="ai-orb small" style="width:20px;height:20px;min-width:20px;min-height:20px;font-size:8px;">AI</div>
                <span class="zone-analysis-label">异常分析</span>
              </div>
              <div class="zone-analysis-content">
                <span class="zone-analysis-text">{{ zoneAnalysisText }}</span>
                <span v-if="!zoneAnalysisComplete" class="zone-typing-cursor">|</span>
              </div>
            </div>

            <!-- 异常项操作卡片 -->
            <div v-if="selectedZone.anomalyActions && selectedZone.anomalyActions.length > 0" class="anomaly-action-list">
              <div v-for="(action, ai) in selectedZone.anomalyActions" :key="ai" class="anomaly-action-card" :class="['action-type-' + action.type, { 'action-card-visible': ai < visibleActionCount }]">
                <div class="anomaly-action-body">
                  <div class="anomaly-action-title">
                    <svg v-if="action.type === 'device'" class="pin-icon" :class="{ 'pin-active': action.pinned }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14" @click.stop="action.pinned = !action.pinned"><path d="M12 17v5M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z"/></svg>
                    {{ action.title }}
                  </div>
                  <div class="anomaly-action-detail">{{ action.detail }}</div>
                </div>
                <div class="anomaly-action-btns">
                  <button v-for="(btn, bi) in action.buttons" :key="bi" class="anomaly-action-btn" :class="btn === '维修' ? 'btn-repair' : btn === '排查' ? 'btn-check' : btn === '巡检' ? 'btn-inspect' : 'btn-fan'" @click.stop="handleAnomalyAction(btn, action)">
                    <svg v-if="btn === '维修'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
                    </svg>
                    <svg v-else-if="btn === '排查'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="11" cy="11" r="8"/>
                      <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                    </svg>
                    <svg v-else-if="btn === '巡检'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M9 11l3 3L22 4"/>
                      <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
                    </svg>
                    <svg v-else-if="btn === '开启风机'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M9.59 4.59A2 2 0 1 1 11 8H2m10.59 11.41A2 2 0 1 0 14 16H2m15.73-8.27A2.5 2.5 0 1 1 19.5 12H2"/>
                    </svg>
                    <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
                    </svg>
                    <span>{{ btn }}</span>
                  </button>
                </div>
              </div>
            </div>
            <div v-else class="zone-normal-badge">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><path d="M22 4L12 14.01l-3-3"/></svg>
              <span>该区域运行平稳，无异常项</span>
            </div>
          </div>
        </div>
      </div>
      <div class="modal-lower">
        <div class="ai-analysis-section">
          <div class="ai-analysis-header">
            <div class="ai-orb small">AI</div>
            <h4>AI 全分区态势分析</h4>
          </div>
          <div class="ai-analysis-content">
            <span class="typewriter-text">{{ aiAnalysisText }}</span>
            <span v-if="!aiAnalysisComplete" class="typing-cursor">|</span>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 派单弹窗 -->
  <div v-if="isDispatchModalOpen" class="dispatch-modal-overlay" @click.self="closeDispatchModal">
    <div class="dispatch-modal-content">
      <div class="dispatch-modal-header">
        <div class="dispatch-header-left">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
            <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/>
          </svg>
          <h3>{{ dispatchModalType === 'repair' ? '故障诊断' : dispatchModalType === 'check' ? '告警排查' : '设备巡检' }}</h3>
        </div>
        <button class="dispatch-modal-close" @click="closeDispatchModal">×</button>
      </div>

      <div class="dispatch-modal-body">
        <!-- 设备信息 -->
        <div class="dispatch-section">
          <h4 class="section-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/>
            </svg>
            设备信息
          </h4>
          <div class="dispatch-info-grid">
            <div class="info-item">
              <span class="info-label">设备名称</span>
              <span class="info-value">{{ dispatchOrder.deviceName }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">设备ID</span>
              <span class="info-value">{{ dispatchOrder.deviceId }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">设备类型</span>
              <span class="info-value">{{ dispatchOrder.deviceType }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">设备位置</span>
              <span class="info-value">{{ dispatchOrder.deviceLocation }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">工单编号</span>
              <span class="info-value order-num">{{ dispatchOrder.orderNumber }}</span>
            </div>
          </div>
        </div>

        <!-- AI分析 -->
        <div class="dispatch-section">
          <h4 class="section-title">
            <div class="ai-orb small" style="width:20px;height:20px;min-width:20px;min-height:20px;font-size:8px;">AI</div>
            AI分析
          </h4>

          <!-- 故障原因和维修步骤（左右排列） -->
          <div class="dispatch-cards-row">
            <!-- 故障原因/告警分析（逐字显示） -->
            <div class="dispatch-card-item">
              <label class="textarea-label">{{ dispatchModalType === 'repair' ? '故障原因分析' : dispatchModalType === 'check' ? '告警原因分析' : '巡检说明' }}</label>
              <div v-if="!dispatchFaultComplete" class="typewriter-box">
                <span class="typewriter-text">{{ dispatchFaultText }}</span>
                <span class="typing-cursor">|</span>
              </div>
              <div v-else class="editable-text" contenteditable="true" @blur="dispatchOrder.faultReason = $event.target.textContent">{{ dispatchFaultText }}</div>
            </div>

            <!-- 维修/排查步骤（逐字显示） -->
            <div class="dispatch-card-item">
              <label class="textarea-label">{{ dispatchModalType === 'repair' ? '维修步骤' : dispatchModalType === 'check' ? '排查步骤' : '巡检步骤' }}</label>
              <div v-if="!dispatchStepsComplete" class="typewriter-box">
                <span class="typewriter-text">{{ dispatchStepsText }}</span>
                <span class="typing-cursor">|</span>
              </div>
              <div v-else class="editable-text" contenteditable="true" @blur="dispatchOrder.repairSteps = $event.target.textContent">{{ dispatchStepsText }}</div>
            </div>
          </div>

          <!-- 推荐维修/排查人员 -->
          <div class="dispatch-person-box" v-if="recommendedPerson">
            <label class="textarea-label">{{ dispatchModalType === 'repair' ? '推荐维修人员' : dispatchModalType === 'check' ? '推荐排查人员' : '推荐巡检人员' }}</label>
            <div class="person-card">
              <div class="person-left">
                <div class="person-avatar">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="36" height="36">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                    <circle cx="12" cy="7" r="4"/>
                  </svg>
                </div>
                <div class="person-info">
                  <div class="person-name">{{ recommendedPerson.name }}</div>
                  <div class="person-title">{{ recommendedPerson.title }}</div>
                  <div class="person-contact">
                    <span class="contact-item">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
                        <rect x="2" y="4" width="20" height="16" rx="2"/><path d="M22 7l-10 7L2 7"/>
                      </svg>
                      {{ recommendedPerson.employeeId }}
                    </span>
                    <span class="contact-item">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
                        <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z"/>
                      </svg>
                      {{ recommendedPerson.phone }}
                    </span>
                  </div>
                </div>
              </div>
              <div class="person-right">
                <div class="person-badge match">
                  <span class="badge-label">匹配度</span>
                  <span class="badge-value">{{ recommendedPerson.matchRate }}%</span>
                </div>
                <div class="person-badge status">
                  <span class="status-dot" :class="'status-' + recommendedPerson.status"></span>
                  <span class="badge-value">{{ recommendedPerson.status }}</span>
                  <span class="badge-tasks">任务: {{ recommendedPerson.currentTasks }}</span>
                </div>
              </div>
            </div>
            <div class="person-reason">
              <span class="reason-label">推荐理由：</span>
              <span class="reason-text">{{ recommendedPerson.reason }}</span>
            </div>
          </div>

          <!-- 备注 -->
          <div class="dispatch-textarea-box">
            <label class="textarea-label">备注</label>
            <div class="editable-text" contenteditable="true" @blur="dispatchOrder.notes = $event.target.textContent">{{ dispatchOrder.notes }}</div>
          </div>
        </div>
      </div>

      <div class="dispatch-modal-footer">
        <button class="dispatch-btn cancel" @click="cancelDispatchOrder">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
          <span>取消</span>
        </button>
        <button class="dispatch-btn confirm" @click="confirmDispatchOrder">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/>
          </svg>
          <span>{{ dispatchModalType === 'repair' ? '确认派单' : dispatchModalType === 'check' ? '确认派单' : '确认派单' }}</span>
        </button>
      </div>
    </div>
  </div>

  <!-- 设备控制弹窗 -->
  <div v-if="isDeviceControlModalOpen" class="device-control-overlay" @click.self="closeDeviceControlModal">
    <div class="device-control-modal">
      <div class="device-control-header">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
          <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
        </svg>
        设备控制
      </div>

      <div class="device-control-body">
        <!-- 左侧SVG设备图片 -->
        <div class="device-svg-section">
          <div class="device-svg-wrapper">
            <svg viewBox="0 0 300 300" fill="none" xmlns="http://www.w3.org/2000/svg" class="device-fan-svg">
              <!-- 风机底座 -->
              <rect x="120" y="260" width="60" height="20" rx="4" fill="currentColor" opacity="0.2"/>
              <rect x="130" y="240" width="40" height="25" rx="3" fill="currentColor" opacity="0.15"/>
              
              <!-- 风机外壳外圈 -->
              <circle cx="150" cy="120" r="110" stroke="currentColor" stroke-width="4" fill="url(#fan-gradient-outer)" opacity="0.4"/>
              <circle cx="150" cy="120" r="100" stroke="currentColor" stroke-width="2" fill="url(#fan-gradient-inner)" opacity="0.3"/>
              
              <!-- 风机防护网 -->
              <g opacity="0.2">
                <line x1="150" y1="20" x2="150" y2="220" stroke="currentColor" stroke-width="1"/>
                <line x1="50" y1="120" x2="250" y2="120" stroke="currentColor" stroke-width="1"/>
                <line x1="80" y1="50" x2="220" y2="190" stroke="currentColor" stroke-width="1"/>
                <line x1="220" y1="50" x2="80" y2="190" stroke="currentColor" stroke-width="1"/>
                <circle cx="150" cy="120" r="60" stroke="currentColor" stroke-width="1" fill="none"/>
                <circle cx="150" cy="120" r="80" stroke="currentColor" stroke-width="1" fill="none"/>
              </g>
              
              <!-- 风机叶片 -->
              <g class="fan-blades">
                <path d="M150 120 C150 60, 170 40, 150 25 C130 40, 150 60, 150 120Z" fill="url(#blade-gradient)" opacity="0.8"/>
                <path d="M150 120 C210 120, 230 100, 245 120 C230 140, 210 120, 150 120Z" fill="url(#blade-gradient)" opacity="0.8"/>
                <path d="M150 120 C150 180, 130 200, 150 215 C170 200, 150 180, 150 120Z" fill="url(#blade-gradient)" opacity="0.8"/>
                <path d="M150 120 C90 120, 70 140, 55 120 C70 100, 90 120, 150 120Z" fill="url(#blade-gradient)" opacity="0.8"/>
              </g>
              
              <!-- 中心电机 -->
              <circle cx="150" cy="120" r="22" fill="url(#center-gradient)" stroke="currentColor" stroke-width="2"/>
              <circle cx="150" cy="120" r="12" fill="currentColor" opacity="0.6"/>
              <circle cx="150" cy="120" r="5" fill="currentColor" opacity="0.9"/>
              
              <!-- 吹风效果 -->
              <g class="wind-effect" :opacity="deviceControlInfo.showWindEffect ? 1 : 0">
                <!-- 主风流 -->
                <path d="M260 80 Q290 75, 320 85" stroke="currentColor" stroke-width="3" stroke-linecap="round" fill="none" opacity="0.6"/>
                <path d="M265 100 Q300 95, 335 105" stroke="currentColor" stroke-width="3" stroke-linecap="round" fill="none" opacity="0.7"/>
                <path d="M265 120 Q305 118, 345 120" stroke="currentColor" stroke-width="3" stroke-linecap="round" fill="none" opacity="0.8"/>
                <path d="M265 140 Q300 145, 335 135" stroke="currentColor" stroke-width="3" stroke-linecap="round" fill="none" opacity="0.7"/>
                <path d="M260 160 Q290 165, 320 155" stroke="currentColor" stroke-width="3" stroke-linecap="round" fill="none" opacity="0.6"/>
                
                <!-- 风流粒子 -->
                <g class="wind-particles">
                  <circle cx="280" cy="70" r="3" fill="currentColor" opacity="0.4"/>
                  <circle cx="300" cy="90" r="2" fill="currentColor" opacity="0.3"/>
                  <circle cx="310" cy="110" r="4" fill="currentColor" opacity="0.5"/>
                  <circle cx="305" cy="130" r="2" fill="currentColor" opacity="0.3"/>
                  <circle cx="290" cy="150" r="3" fill="currentColor" opacity="0.4"/>
                  <circle cx="275" cy="170" r="2" fill="currentColor" opacity="0.3"/>
                </g>
                
                <!-- 风流波纹 -->
                <g class="wind-ripples">
                  <ellipse cx="290" cy="120" rx="15" ry="40" stroke="currentColor" stroke-width="1" fill="none" opacity="0.2"/>
                  <ellipse cx="310" cy="120" rx="20" ry="50" stroke="currentColor" stroke-width="1" fill="none" opacity="0.15"/>
                  <ellipse cx="330" cy="120" rx="25" ry="60" stroke="currentColor" stroke-width="1" fill="none" opacity="0.1"/>
                </g>
              </g>
              
              <!-- 渐变定义 -->
              <defs>
                <linearGradient id="fan-gradient-outer" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="currentColor" stop-opacity="0.3"/>
                  <stop offset="100%" stop-color="currentColor" stop-opacity="0.1"/>
                </linearGradient>
                <linearGradient id="fan-gradient-inner" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="currentColor" stop-opacity="0.2"/>
                  <stop offset="100%" stop-color="currentColor" stop-opacity="0.05"/>
                </linearGradient>
                <linearGradient id="blade-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="currentColor" stop-opacity="0.9"/>
                  <stop offset="100%" stop-color="currentColor" stop-opacity="0.4"/>
                </linearGradient>
                <radialGradient id="center-gradient" cx="50%" cy="50%" r="50%">
                  <stop offset="0%" stop-color="currentColor" stop-opacity="0.8"/>
                  <stop offset="100%" stop-color="currentColor" stop-opacity="0.3"/>
                </radialGradient>
              </defs>
            </svg>
          </div>
          <div class="device-name">{{ deviceControlInfo.deviceName }}</div>
        </div>

        <!-- 右侧操作信息 -->
        <div class="device-info-section">
          <div class="info-row">
            <span class="info-label">操作</span>
            <span class="info-value action-text">{{ deviceControlInfo.action }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">状态</span>
            <span class="info-value" :class="'status-' + deviceControlInfo.statusClass">
              <span v-if="deviceControlInfo.statusClass === 'pending'" class="status-spinner"></span>
              <svg v-else-if="deviceControlInfo.statusClass === 'success'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="16" height="16" class="status-icon">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              {{ deviceControlInfo.status }}
            </span>
          </div>
          <div class="info-row">
            <span class="info-label">时间</span>
            <span class="info-value time-text">{{ deviceControlInfo.timestamp }}</span>
          </div>
          <div class="info-row data-link-row">
            <span class="info-label">数据链路</span>
            <span class="info-value" :class="'link-' + deviceControlInfo.dataLinkClass">
              <span v-if="deviceControlInfo.dataLinkClass === 'waiting'" class="link-spinner"></span>
              <svg v-else-if="deviceControlInfo.dataLinkClass === 'success'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="16" height="16" class="link-icon">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              {{ deviceControlInfo.dataLinkStatus }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 站房整体状态评估弹窗 -->
  <div v-if="isHealthModalOpen" class="health-modal-overlay" @click.self="closeHealthModal">
    <div class="health-modal">
      <div class="health-modal-header">
        <div class="header-title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          </svg>
          站房整体状态评估
        </div>
        <button class="modal-close-btn" @click="closeHealthModal">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
            <path d="M18 6L6 18M6 6l12 12"/>
          </svg>
        </button>
      </div>

      <div class="health-modal-body">
        <!-- 当前分数 + 分析描述 -->
        <div class="health-score-section">
          <div class="health-score-left">
            <div ref="healthScoreChartRef" class="health-score-chart"></div>
            <p class="analysis-desc">全域运行平稳，用能态势优良，系统运行正常</p>
          </div>
          <div class="health-analysis-conclusion-right">
            <h4 class="conclusion-title-small">
              <div class="ai-orb small" style="width:18px;height:18px;min-width:18px;min-height:18px;font-size:7px;">AI</div>
              整体分析
            </h4>
            <p class="conclusion-text-small">{{ healthAnalysisConclusion }}</p>
          </div>
        </div>
        <!-- 雷达图 -->
        <div class="health-radar-section">
          <div ref="healthRadarChartRef" class="health-radar-chart"></div>
          <!-- 分项数据卡片 -->
          <div class="radar-data-cards">
            <div v-for="(item, index) in healthSubItems" :key="index" class="radar-data-card" @click="openSubItemModal(item)">
              <div class="radar-card-header">
                <span class="radar-card-name">{{ item.name }}</span>
                <span class="radar-card-score" :class="item.deductionDevices.length > 0 ? 'orange-text' : 'green-text'">{{ item.score }}分</span>
              </div>
              <div class="radar-card-meta">
                <span>权重 {{ item.weight }}</span>
                <span class="radar-card-anomalies" v-if="item.deductionDevices.length > 0">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
                    <circle cx="12" cy="12" r="10"/>
                    <line x1="12" y1="8" x2="12" y2="12"/>
                    <line x1="12" y1="16" x2="12.01" y2="16"/>
                  </svg>
                  {{ item.deductionDevices.length }}项异常
                </span>
                <span v-else class="radar-card-normal">无异常</span>
              </div>
            </div>
          </div>
        </div>
        <!-- 历史曲线 -->
        <div class="health-trend-section">
          <div class="health-time-select">
            <select v-model="healthTimeDimension" @change="switchHealthTimeDimension(healthTimeDimension)" class="time-dropdown">
              <option value="10min">近10分钟</option>
              <option value="1h">近10小时</option>
              <option value="1d">近10天</option>
            </select>
          </div>
          <div class="health-trend-chart"></div>
        </div>



      </div>
    </div>
  </div>

  <!-- 分项详情弹窗 -->
  <div v-if="isSubItemModalOpen && currentSubItem" class="sub-item-overlay" @click.self="closeSubItemModal">
    <div class="sub-item-modal">
      <div class="sub-item-header">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
          <use :href="'#i-' + currentSubItem.icon"></use>
        </svg>
        {{ currentSubItem.name }} - 扣分详情
        <button class="sub-item-close" @click="closeSubItemModal">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
            <path d="M18 6L6 18M6 6l12 12"/>
          </svg>
        </button>
      </div>

      <div class="sub-item-body">
        <!-- 分项概览 -->
        <div class="sub-item-overview">
          <div class="overview-score">
            <span class="score-label">当前分数</span>
            <span class="score-num" :class="currentSubItem.deductionDevices.length > 0 ? 'orange-text' : 'green-text'">{{ currentSubItem.score }}分</span>
          </div>
          <div class="overview-meta">
            <span>权重: {{ currentSubItem.weight }}</span>
            <span>异常: <span :class="currentSubItem.deductionDevices.length > 0 ? 'orange-text' : 'green-text'">{{ currentSubItem.deductionDevices.length > 0 ? currentSubItem.deductionDevices.length + '项' : '无' }}</span></span>
          </div>
        </div>

        <!-- 扣分设备列表 -->
        <div v-if="currentSubItem.deductionDevices.length > 0" class="deduction-devices">
          <h4 class="deduction-title">扣分设备</h4>
          <div v-for="(device, idx) in currentSubItem.deductionDevices" :key="idx" class="deduction-device-card">
            <div class="device-info">
              <div class="device-name">{{ device.name }}</div>
              <div class="device-id">{{ device.id }}</div>
            </div>
            <div class="device-realtime">
              <span class="realtime-label">实时数据</span>
              <span class="realtime-value">{{ device.realTimeData }}</span>
            </div>
            <div class="device-deduction">
              <span class="deduction-badge">-{{ device.deduction }}分</span>
            </div>
          </div>
        </div>

        <div v-else class="no-deduction">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="48" height="48">
            <circle cx="12" cy="12" r="10"/>
            <path d="M8 12l2 2 4-4"/>
          </svg>
          <p>该分项无扣分设备，运行状态良好</p>
        </div>
      </div>

      <div class="sub-item-footer">
        <button class="sub-item-close-btn" @click="closeSubItemModal">关闭</button>
      </div>
    </div>
  </div>

  <!-- 设备分析弹窗 -->
  <div v-if="isDeviceAnalysisOpen" class="device-analysis-overlay" @click.self="closeDeviceAnalysis">
    <div class="device-analysis-modal">
      <div class="device-analysis-header">
        <div class="header-title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
          </svg>
          设备分析
        </div>
        <button class="modal-close-btn" @click="closeDeviceAnalysis">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
            <path d="M18 6L6 18M6 6l12 12"/>
          </svg>
        </button>
      </div>

      <div class="device-analysis-body">
        <!-- KPI指标 -->
        <div class="device-kpi-row">
          <div class="device-kpi-card">
            <div class="kpi-label">总设备数</div>
            <div class="kpi-value">{{ deviceAnalysisData.totalDevices }}</div>
          </div>
          <div class="device-kpi-card">
            <div class="kpi-label">设备健康度</div>
            <div class="kpi-value blue-text">{{ deviceAnalysisData.healthScore }}分</div>
          </div>
          <div class="device-kpi-card">
            <div class="kpi-label">正常设备</div>
            <div class="kpi-value green-text">{{ deviceAnalysisData.normalDevices }}</div>
          </div>
          <div class="device-kpi-card">
            <div class="kpi-label">故障设备</div>
            <div class="kpi-value red-text">{{ deviceAnalysisData.faultDevices }}</div>
          </div>
          <div class="device-kpi-card">
            <div class="kpi-label">告警设备</div>
            <div class="kpi-value orange-text">{{ deviceAnalysisData.alarmDevices }}</div>
          </div>
          <div class="device-kpi-card">
            <div class="kpi-label">维修设备</div>
            <div class="kpi-value purple-text">{{ deviceAnalysisData.maintenanceDevices }}</div>
          </div>
        </div>
        <!-- 异常分析与处理 -->
        <div class="device-anomaly-section">
          <div class="anomaly-layout">
            <!-- 左侧：AI异常分析 -->
            <div class="anomaly-analysis-panel">
              <div class="device-ai-section">
                <div class="ai-section-header">
                  <div class="ai-orb small">AI</div>
                  <span class="ai-section-title">设备分析</span>
                </div>
                <div class="ai-analysis-content">
                  <p class="ai-analysis-text">设备机房区3号水泵轴承振动达4.8mm/s，超标2.3倍，建议立即停机检修。候车厅B区排风机2#故障停机导致区域温度偏高2.5°C，需尽快修复恢复通风。配电柜B电压395V偏低，建议检查线路负载平衡。</p>
                </div>
              </div>
            </div>
            <!-- 中间：异常设备处理列表 -->
            <div class="anomaly-device-panel">
              <h4 class="section-title">异常设备</h4>
              <div class="anomaly-action-list">
                <div v-for="(device, idx) in deviceAnalysisData.anomalyList" :key="idx" :class="['anomaly-action-item', device.type === 'fault' ? 'fault-item' : 'alarm-item']">
                  <div class="anomaly-info">
                    <div class="anomaly-title">{{ device.title }}</div>
                    <div class="anomaly-meta">
                      <span class="anomaly-id">{{ device.id }}</span>
                      <span class="anomaly-location">{{ device.location }}</span>
                    </div>
                    <div class="anomaly-status">{{ device.status }}</div>
                  </div>
                  <div class="anomaly-actions">
                    <button :class="['anomaly-btn', device.action === '维修' ? 'btn-repair' : 'btn-check']" @click.stop="handleAnomalyAction(device.action, device)">
                      <svg v-if="device.action === '维修'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                        <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
                      </svg>
                      <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                        <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
                      </svg>
                      {{ device.action }}
                    </button>
                  </div>
                </div>
              </div>
            </div>
            <!-- 右侧：维修进度 -->
            <div class="maintenance-panel">
              <h4 class="section-title">维修进度</h4>
              <div class="maintenance-progress-list">
                <div v-for="(task, idx) in deviceAnalysisData.maintenanceProgress" :key="idx" class="maintenance-task">
                  <div class="task-info">
                    <span class="task-id">{{ task.id }}</span>
                    <span class="task-device">{{ task.device }}</span>
                    <span class="task-worker">{{ task.worker }}</span>
                  </div>
                  <div class="task-progress">
                    <div class="progress-bar">
                      <div class="progress-fill" :style="{ width: task.progress + '%' }" :class="task.progress === 100 ? 'completed' : ''"></div>
                    </div>
                    <span class="progress-text">{{ task.progress }}%</span>
                  </div>
                  <div class="task-status" :class="task.status === '已完成' ? 'completed-text' : 'progressing-text'">{{ task.status }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 设备异常热力图 -->
        <div class="device-heatmap-section">
          <h4 class="section-title">近7日设备异常热力图</h4>
          <div ref="deviceHeatmapChartRef" class="device-heatmap-chart"></div>
        </div>

      </div>
    </div>
  </div>

  <!-- 安全指数弹窗 -->
  <div v-if="isSafetyModalOpen" class="safety-modal-overlay" @click.self="closeSafetyModal">
    <div class="safety-modal">
      <div class="safety-modal-header">
        <div class="header-title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            <polyline points="9 12 11 14 15 10"/>
          </svg>
          安全指数
        </div>
        <button class="modal-close-btn" @click="closeSafetyModal">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>

      <div class="safety-modal-body">
        <!-- 安全指数主展示区 -->
        <div class="safety-score-section">
          <div class="safety-score-circle">
            <svg viewBox="0 0 200 200" class="score-ring">
              <defs>
                <linearGradient id="safetyGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" style="stop-color:#00ff88;stop-opacity:1" />
                  <stop offset="100%" style="stop-color:#00d4ff;stop-opacity:1" />
                </linearGradient>
              </defs>
              <circle cx="100" cy="100" r="85" fill="none" stroke="#1a3a5c" stroke-width="12"/>
              <circle cx="100" cy="100" r="85" fill="none" stroke="url(#safetyGradient)" stroke-width="12"
                      :stroke-dasharray="2 * Math.PI * 85"
                      :stroke-dashoffset="2 * Math.PI * 85 * (1 - safetyScore / 100)"
                      stroke-linecap="round"
                      transform="rotate(-90 100 100)"
                      class="score-progress"/>
            </svg>
            <div class="score-center">
              <div class="score-value" :class="safetyScore >= 80 ? 'score-good' : safetyScore >= 60 ? 'score-warning' : 'score-danger'">
                {{ safetyScore }}
              </div>
              <div class="score-label">安全指数</div>
            </div>
          </div>
          <div class="safety-formula">
            <div class="formula-text">安全指数 = max(0, 100 - 异常数)</div>
            <div class="formula-calc">= max(0, 100 - {{ anomalyCount }}) = {{ safetyScore }}</div>
          </div>
        </div>

        <!-- 异常统计 -->
        <div class="anomaly-stats">
          <div class="anomaly-total-inline">
            <div class="total-label">异常总数</div>
            <div class="total-value">{{ anomalyCount }}</div>
          </div>
          <div class="breakdown-item">
            <div class="breakdown-icon device-fault">🔧</div>
            <div class="breakdown-content">
              <div class="breakdown-label">设备故障/告警</div>
              <div class="breakdown-value">{{ safetyAnomalies.deviceFaults + safetyAnomalies.deviceAlarms }}</div>
              <div class="breakdown-detail">故障 {{ safetyAnomalies.deviceFaults }} | 告警 {{ safetyAnomalies.deviceAlarms }}</div>
            </div>
          </div>
          <div class="breakdown-item">
            <div class="breakdown-icon env-abnormal">🌡️</div>
            <div class="breakdown-content">
              <div class="breakdown-label">环境异常</div>
              <div class="breakdown-value">{{ safetyAnomalies.environmentAbnormal }}</div>
              <div class="breakdown-detail">温度、湿度、空气质量等超标</div>
            </div>
          </div>
          <div class="breakdown-item">
            <div class="breakdown-icon energy-abnormal">⚡</div>
            <div class="breakdown-content">
              <div class="breakdown-label">能耗异常</div>
              <div class="breakdown-value">{{ safetyAnomalies.energyAbnormal }}</div>
              <div class="breakdown-detail">用电量、用水量异常波动</div>
            </div>
          </div>
          <div class="breakdown-item">
            <div class="breakdown-icon pending-items">📋</div>
            <div class="breakdown-content">
              <div class="breakdown-label">未处理事项</div>
              <div class="breakdown-value">{{ safetyAnomalies.urgentItems + safetyAnomalies.importantItems }}</div>
              <div class="breakdown-detail">紧急 {{ safetyAnomalies.urgentItems }} | 重要 {{ safetyAnomalies.importantItems }}</div>
            </div>
          </div>
        </div>

        <!-- 安全建议 -->
        <div class="safety-suggestions">
          <div class="suggestion-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="16" x2="12" y2="12"/>
              <line x1="12" y1="8" x2="12.01" y2="8"/>
            </svg>
            安全建议
          </div>
          <div class="suggestion-list">
            <div v-if="safetyAnomalies.deviceFaults > 0" class="suggestion-item warning">
              <span class="suggestion-icon">⚠️</span>
              <span>请及时处理 {{ safetyAnomalies.deviceFaults }} 个设备故障，避免影响正常运行</span>
            </div>
            <div v-if="safetyAnomalies.environmentAbnormal > 0" class="suggestion-item info">
              <span class="suggestion-icon">🌡️</span>
              <span>关注环境指标变化，及时调整空调通风系统</span>
            </div>
            <div v-if="safetyAnomalies.urgentItems > 0" class="suggestion-item danger">
              <span class="suggestion-icon">🚨</span>
              <span>有 {{ safetyAnomalies.urgentItems }} 个紧急事项待处理，请优先处理</span>
            </div>
            <div v-if="safetyScore >= 80" class="suggestion-item success">
              <span class="suggestion-icon">✅</span>
              <span>当前安全指数良好，继续保持日常巡检和维护工作</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 事项处理弹窗 -->
  <div v-if="isWarningModalOpen" class="warning-modal-overlay" @click.self="closeWarningModal">
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
        <!-- KPI指标行 -->
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
            <!-- <button class="ai-action-btn" @click="handlePredictiveMaint">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>
              预测维护
              <span class="ai-badge" v-if="warningAiStats.predict > 0">{{ warningAiStats.predict }}条</span>
            </button> -->
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

        <!-- AI功能区域 + 事项列表 -->
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

        <!-- 事项列表 -->
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

        <!-- ECharts 平行坐标图 -->
        <div class="warning-chart-section">
          <div class="chart-header">
            <h4 class="panel-title">事项趋势</h4>
          </div>
          <div ref="warningChartRef" class="warning-chart"></div>
        </div>
      </div>
    </div>
  </div>

  <button class="assistant-fab" @click="toggleAssistant" aria-label="智慧助手">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
    </svg>
  </button>

  <div :class="['assistant-panel', { active: isAssistantOpen }]">
    <div class="assistant-header">
      <div class="assistant-header-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="3"/>
          <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
        </svg>
        智慧助手
      </div>
      <button class="assistant-close" @click="toggleAssistant" aria-label="关闭">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/>
          <line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
    </div>
    <div class="assistant-messages" ref="assistantMessagesEl">
      <div
        v-for="(message, index) in assistantMessages"
        :key="message.role + '-' + index"
        :class="['assistant-message', message.role]"
      >{{ message.text }}</div>
    </div>
    <div class="assistant-input-area">
      <input v-model="assistantInput" class="assistant-input" placeholder="输入您的问题..." @keypress.enter="sendAssistantMessage">
      <button class="assistant-send" @click="sendAssistantMessage" aria-label="发送">
        <svg viewBox="0 0 24 24" fill="currentColor">
          <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
        </svg>
      </button>
    </div>
  </div>

  <!-- 底座实时监控弹窗 -->
  <div v-if="isBaseMonitorModalOpen" class="base-monitor-overlay" @click.self="closeBaseMonitorModal">
    <div class="base-monitor-modal">
      <button class="base-monitor-close" @click="closeBaseMonitorModal" aria-label="关闭">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"></line>
          <line x1="6" y1="6" x2="18" y2="18"></line>
        </svg>
      </button>
      <iframe src="/dashboard/openclaw_svg/index.html" class="base-monitor-iframe" frameborder="0"></iframe>
    </div>
  </div>
</template>

<style>

    :root {
      --bg: #f8fbff;
      --panel: #fbfdff;
      --line: #d8e5f7;
      --line-strong: #b9cef2;
      --blue: #1268ff;
      --blue-deep: #073ebc;
      --green: #08ae66;
      --shield-green: oklch(73% 0.18 160);
      --green-soft: #eafbf3;
      --orange: #ff6a00;
      --orange-soft: #fff3e8;
      --red: #ff1515;
      --red-soft: #fff0ee;
      --purple: #5b39ff;
      --cyan: #18bfd0;
      --text: #07153b;
      --muted: #526383;
      --soft: #eef5ff;
      --shadow: 0 10px 28px rgba(30, 82, 162, 0.08);
    }

    [data-theme="dark"] {
      --bg: #000d21;
      --panel: #0d2137;
      --line: #1a3a5c;
      --line-strong: #2a5a8c;
      --blue: #00d4ff;
      --blue-deep: #0099cc;
      --green: #00ff88;
      --shield-green: #00ff88;
      --green-soft: rgba(0, 255, 136, 0.1);
      --orange: #ff9933;
      --orange-soft: rgba(255, 153, 51, 0.1);
      --red: #ff4444;
      --red-soft: rgba(255, 68, 68, 0.1);
      --purple: #aa66ff;
      --cyan: #00e5ff;
      --text: #e0f0ff;
      --muted: #88aacc;
      --soft: rgba(0, 150, 255, 0.08);
      --shadow: 0 10px 28px rgba(0, 100, 200, 0.2);
    }

    * {
      box-sizing: border-box;
      letter-spacing: 0;
    }

    body {
      margin: 0;
      min-height: 100vh;
      color: var(--text);
/*          background: radial-gradient(circle at 12% 2%, oklch(91% 0.06 255 / 0.7), transparent 22%), radial-gradient(circle at 70% 0%, oklch(96% 0.04 220 / 0.8), transparent 28%), oklch(98% 0.012 235);*/
      background:
        radial-gradient(circle at 16% 0%, rgba(20, 105, 255, 0.05), transparent 30rem),
        linear-gradient(180deg, #fbfdff 0%, var(--bg) 64%, #f7fbff 100%);
      font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", sans-serif;
      transition: background 0.3s, color 0.3s;
    }

    [data-theme="dark"] body {
      background:#000d21;

        /* radial-gradient(circle at 20% 0%, rgba(0, 180, 255, 0.08), transparent 25rem),
        radial-gradient(circle at 80% 100%, rgba(0, 255, 136, 0.05), transparent 20rem),
     linear-gradient(180deg, #0d1f33 0%, var(--bg) 60%, #081420 100%); */

    }

    svg {
      display: block;
    }

    .dashboard {
      /* width: min(100%, 1672px); */
      width: 100%;
      margin: 0 auto;
      padding: 0 10px;
      display: flex;
      flex-direction: column;
      min-height: calc(100vh - 120px);
    }

    .page-header {
      position: relative;
      padding: 24px 32px;
      height: var(--page-header-height, auto);
      /* background: linear-gradient(135deg, rgba(18, 104, 255, 0.08) 0%, rgba(8, 174, 102, 0.06) 50%, rgba(91, 57, 255, 0.05) 100%); */
      /* border: 1px solid var(--line); */
      /* border-radius: 12px; */
      overflow: hidden;
    }

    [data-theme="dark"] .page-header {
      /* background: linear-gradient(135deg, rgba(0, 150, 255, 0.12) 0%, rgba(0, 255, 136, 0.08) 50%, rgba(170, 102, 255, 0.06) 100%); */
      /* border-color: var(--line-strong); */
    }

    .page-header::before {
      content: "";
      position: absolute;
      top: -50%;
      right: -10%;
      width: 400px;
      height: 400px;
      /* background: radial-gradient(circle, rgba(18, 104, 255, 0.12) 0%, transparent 70%); */
      pointer-events: none;
    }

    [data-theme="dark"] .page-header::before {
      background: radial-gradient(circle, rgba(0, 212, 255, 0.15) 0%, transparent 70%);
    }

    .page-header::after {
      content: "";
      position: absolute;
      bottom: -30%;
      left: 20%;
      width: 300px;
      height: 300px;
      /* background: radial-gradient(circle, rgba(8, 174, 102, 0.08) 0%, transparent 70%); */
      pointer-events: none;
    }

    .header-content {
      position: relative;
      z-index: 1;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 20px;
    }

    .header-left {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .main-title {
      display: flex;
      align-items: center;
      gap: 14px;
      margin: 0;
      color: var(--text);
      font-size: 32px;
      font-weight: 900;
      line-height: 1;
    }

    [data-theme="dark"] .main-title {
      color: #00d4ff;
      text-shadow: 0 0 15px rgba(0, 212, 255, 0.3);
    }

    .main-title::before {
      content: "";
      width: 6px;
      height: 40px;
      border-radius: 999px;
      background: linear-gradient(180deg, var(--blue), var(--green));
      box-shadow: 0 0 12px rgba(18, 104, 255, 0.4);
    }

    .subtitle {
      color: var(--muted);
      font-size: 15px;
      font-weight: 700;
      padding-left: 20px;
    }

    [data-theme="dark"] .subtitle {
      color: #88aacc;
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-top: var(--header-right-margin-top, 0);
    }

    .header-station-left {
      position: absolute;
      top: var(--header-station-left-top, 20px);
      left: 20px;
      font-size: var(--header-station-left-font-size, 20px);
      font-weight: 700;
      color: var(--text);
      letter-spacing: 1px;
      text-shadow: 0 2px 20px rgba(0,0,0,0.3);
      pointer-events: none;
      z-index: 1;
    }

    .header-station-right {
      position: absolute;
      top: 10px;
      right: 20px;
      font-size: 20px;
      font-weight: 700;
      color: var(--text);
      letter-spacing: 1px;
      text-shadow: 0 2px 20px rgba(0,0,0,0.3);
      z-index: 1;
    }

    .header-station-name {
      position: absolute;
      top: var(--header-station-name-top, 20px);
      left: 50%;
      transform: translate(-50%, -50%);
      font-size: var(--header-station-name-font-size, 28px);
      font-weight: 900;
      color: var(--text);
      letter-spacing: 8px;
      text-shadow: 0 2px 20px rgba(0,0,0,0.3);
      pointer-events: none;
      z-index: 1;
    }

    .header-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      background: rgba(8, 174, 102, 0.1);
      border: 1px solid rgba(8, 174, 102, 0.3);
      border-radius: 999px;
      color: var(--green);
      font-size: 13px;
      font-weight: 800;
    }

    [data-theme="dark"] .header-badge {
      background: rgba(0, 255, 136, 0.12);
      border-color: rgba(0, 255, 136, 0.3);
    }

    .header-badge .pulse {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--green);
      animation: pulse 2s ease-in-out infinite;
    }

    @keyframes pulse {
      0%, 100% { opacity: 1; transform: scale(1); }
      50% { opacity: 0.6; transform: scale(1.2); }
    }

    .title {
      display: flex;
      align-items: flex-start;
      color: #101114;
      font-size: 22px;
      font-weight: 900;
    }

    [data-theme="dark"] .title {
      color: #00d4ff;
      text-shadow: 0 0 10px rgba(0, 212, 255, 0.3);
    }

    .theme-toggle {
      margin-left: auto;
      padding: 6px 14px;
      border: 1px solid var(--line);
      border-radius: 6px;
      background: rgba(255, 255, 255, 0.6);
      color: var(--text);
      font-size: 13px;
      font-weight: 700;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all 0.3s;
      position: relative;
      z-index: 10;
      pointer-events: auto;
    }

    .theme-toggle:hover {
      background: rgba(255, 255, 255, 0.9);
      border-color: var(--blue);
    }

    [data-theme="dark"] .theme-toggle {
      background: rgba(0, 100, 200, 0.2);
      border-color: var(--blue);
      color: var(--blue);
    }

    [data-theme="dark"] .theme-toggle:hover {
      background: rgba(0, 150, 255, 0.3);
      box-shadow: 0 0 12px rgba(0, 212, 255, 0.3);
    }

    .top-grid,
    .mid-grid,
    .bottom-grid {
      display: grid;
      gap: 10px;
    }

/*      grid-template-columns: minmax(260px, 0.88fr) minmax(380px, 1.25fr) minmax(340px, 1.14fr) minmax(390px, 1.34fr); */
    .top-grid {
      grid-template-columns: minmax(260px, 0.88fr) minmax(380px, 1.25fr) minmax(340px, 2fr) ;
      margin-top: var(--top-grid-margin-top, 0);
    }

    .mid-grid {
      grid-template-columns: minmax(320px, 1.03fr) minmax(330px, 1.04fr) minmax(300px, 0.96fr) minmax(390px, 1.25fr);
    }

    .bottom-grid {
      margin-top: 10px;
      grid-template-columns: minmax(300px, 1.04fr) minmax(310px, 1fr) minmax(280px, 1fr) minmax(330px, 1.1fr) minmax(300px, 1fr);
      flex: 1;
      /* height: 100%; */
      min-height: 0;
    }

    .card {
      /* background: rgba(255, 255, 255, 0.84); */
      border: 1px solid var(--line);
      border-radius: 9px;
      box-shadow: var(--shadow);
      transition: background 0.3s, border-color 0.3s, box-shadow 0.3s;
    }

    [data-theme="dark"] .card,
    [data-theme="dark"] .ai-banner {
      /* background: #000d21;
      border-color: var(--line);
      box-shadow: var(--shadow), 0 0 15px rgba(0, 150, 255, 0.08); */
    }

    .card {
      padding: 12px 14px;
      overflow: hidden;
    }

    .top-grid .card {
      min-height: 259px;
    }

    .mid-grid .card {
      min-height: 240px;
    }

    .bottom-grid .card {
      height: 100%;
      min-height: 0;
    }

    .card-title {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      margin-bottom: 10px;
      color: #0b1738;
      font-size: 16px;
      font-weight: 800;
      line-height: 1;
      white-space: nowrap;
    }

    [data-theme="dark"] .card-title {
      color: #00d4ff;
    }

    .title-left,
    .mini-label,
    .tag,
    .more,
    .action {
      display: inline-flex;
      align-items: center;
      gap: 7px;
    }

    .ico {
      width: 18px;
      height: 18px;
      flex: 0 0 auto;
      color: var(--blue);
    }

    .ico.green {
      color: var(--green);
    }

    .ico.red {
      color: var(--red);
    }

    .ico.orange {
      color: var(--orange);
    }

    .ico.purple {
      color: var(--purple);
    }

    .more {
      color: #111f55;
      font-size: 12px;
      font-weight: 700;
      opacity: 0.82;
      cursor: pointer;
    }

    [data-theme="dark"] .more {
      color: var(--blue);
    }

    .status-card {
      display: grid;
      grid-template-rows: auto 1fr auto;
    }

    .status-card-wrapper {
      position: relative;
      border-radius: 12px;
      transition: all 0.3s ease;
      cursor: pointer;
      --hover-color: var(--green);
      height: 100%;
    }

    .status-card-wrapper:hover {
      background: color-mix(in srgb, var(--hover-color) 5%, transparent);
      box-shadow: 0 4px 20px color-mix(in srgb, var(--hover-color) 15%, transparent);
      transform: translateY(-2px);
    }

    .status-card-wrapper::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      border-radius: 12px;
      border: 2px solid transparent;
      transition: border-color 0.3s ease;
      pointer-events: none;
    }

    .status-card-wrapper:hover::before {
      border-color: color-mix(in srgb, var(--hover-color) 40%, transparent);
    }

    .health-main {
      display: grid;
      grid-template-columns: 112px 1fr;
      align-items: center;
      gap: 10px;
      min-height: 118px;
      padding-top: 4px;
    }

    .shield {
      width: 96px;
      height: 96px;
      justify-self: center;
      filter: drop-shadow(0 12px 15px rgba(7, 171, 102, 0.25));
    }

    .shield.green {
      color: var(--shield-green);
    }

    .health-state {
      display: grid;
      gap: 6px;
    }

    .health-state strong {
      color: #00a85e;
      font-size: 28px;
      line-height: 1;
      font-weight: 900;
    }

    [data-theme="dark"] .health-state strong {
      color: var(--green);
      text-shadow: 0 0 8px rgba(0, 255, 136, 0.4);
    }

    .health-state span {
      color: #07133d;
      font-size: 18px;
      font-weight: 800;
    }

    [data-theme="dark"] .health-state span {
      color: #a0c4e8;
    }

    .health-desc {
      margin: 2px 6px 0;
      color: #172966;
      font-size: 15px;
      font-weight: 700;
    }

    [data-theme="dark"] .health-desc {
      color: #88aacc;
    }

    .sparkline-chart {
      height: 54px;
      margin-top: 8px;
      width: 100%;
    }

    .shield {
      width: 96px;
      height: 96px;
      justify-self: center;
      filter: drop-shadow(0 12px 15px rgba(7, 171, 102, 0.25));
    }

    [data-theme="dark"] .shield {
      filter: drop-shadow(0 12px 15px rgba(0, 255, 136, 0.3));
    }

    .energy-card {
      display: flex;
      flex-direction: column;
    }

    .legend {
      display: flex;
      flex-wrap: wrap;
      gap: 18px;
      align-items: center;
      margin: 0 0 2px 2px;
      color: #243662;
      font-size: 11px;
      font-weight: 700;
    }

    [data-theme="dark"] .legend {
      color: #a0c4e8;
    }

    .legend-item {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      white-space: nowrap;
    }

    .line-key {
      width: 26px;
      height: 3px;
      border-radius: 999px;
      background: currentColor;
    }

    .line-key.dash {
      background: repeating-linear-gradient(90deg, currentColor 0 6px, transparent 6px 10px);
    }

    .energy-chart-container {
      width: 100%;
      height: 220px;
    }

    .axis-label {
      font-size: 11px;
      fill: #1c2b5b;
      font-weight: 700;
    }

    [data-theme="dark"] .axis-label {
      fill: #a0c4e8;
    }

    .tick {
      font-size: 11px;
      fill: #243663;
    }

    [data-theme="dark"] .tick {
      fill: #88aacc;
    }

    .grid-line {
      stroke: #dce6f5;
      stroke-width: 1;
    }

    [data-theme="dark"] .grid-line {
      stroke: var(--line);
    }

    .env-table {
      display: grid;
      grid-template-columns: 0.82fr 0.8fr 0.7fr 0.7fr 0.62fr 0.75fr;
      border: 1px solid #edf3fb;
      border-radius: 8px;
      overflow: hidden;
      font-size: 14px;
      font-weight: 800;
    }

    [data-theme="dark"] .env-table {
      border-color: var(--line);
    }

    .env-table div {
      min-width: 0;
      padding: 9px 8px;
      border-bottom: 1px solid #edf3fb;
      background: rgba(255,255,255,0.58);
      white-space: nowrap;
    }

    [data-theme="dark"] .env-table div {
      border-bottom-color: var(--line);
      background: rgba(10, 25, 41, 0.6);
    }

    .env-table .head {
      color: #111a3d;
      background: #f7fbff;
      font-size: 13px;
      font-weight: 900;
    }

    [data-theme="dark"] .env-table .head {
      color: #00d4ff;
      background: rgba(0, 100, 200, 0.15);
    }

    .env-table div:nth-last-child(-n+6) {
      border-bottom: 0;
    }

    .green-text {
      color: var(--green);
    }

    .blue-text {
      color: var(--blue);
    }

    .orange-text {
      color: var(--orange);
    }

    .red-text {
      color: var(--red);
    }

    .muted {
      color: var(--muted);
    }

    .zone-model-card {
      padding: 6px 14px;
    }

    .view-toggle-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border: 1px solid var(--line);
      border-radius: 6px;
      background: transparent;
      color: var(--muted);
      cursor: pointer;
      transition: all 0.2s;
    }

    .view-toggle-icon:hover {
      color: var(--blue);
      border-color: var(--blue);
      background: var(--soft);
    }

    [data-theme="dark"] .view-toggle-icon:hover {
      color: var(--blue);
      border-color: var(--blue);
      background: rgba(0, 150, 255, 0.1);
    }

    .zone-cards-section {
      min-height: 360px;
      margin-top: 20px;
    }

    .zone-cards-section .risk-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 14px;
    }

    .zone-cards-section .risk-box {
      min-height: 150px;
      padding: 10px 12px;
    }

    .zone-cards-section .zone-env {
      gap: 4px 8px;
    }

    .zone-cards-section .env-item {
      font-size: 11px;
    }

    .zone-cards-section .env-item b {
      font-size: 13px;
    }

    .zone-cards-section .zone-status {
      gap: 6px;
      font-size: 11px;
    }

    .zone-cards-section .status-item {
      padding: 3px 5px;
    }

    .zone-cards-section .risk-head {
      font-size: 14px;
      margin-bottom: 8px;
    }

    .zone-model-section {
      position: relative;
      min-height: 360px;
      border-radius: 8px;
      overflow: hidden;
      background: var(--soft);
      border: 1px solid var(--line);
    }

    .zone-model-section .heatmap-canvas {
      width: 100%;
      height: 100%;
      min-height: 360px;
      cursor: pointer;
      position: relative;
    }

    .zone-model-section .heatmap-labels {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      pointer-events: none;
      z-index: 50;
    }

    .risk-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 14px;
    }

    .risk-box {
      min-height: 88px;
      padding: 12px 14px;
      border: 1px solid;
      border-radius: 9px;
      background: #fff;
    }

    [data-theme="dark"] .risk-box {
      background: rgba(10, 25, 41, 0.7);
    }

    .risk-box.green-box {
      border-color: #54d08c;
      background: linear-gradient(180deg, #f4fff9 0%, #ffffff 100%);
    }

    [data-theme="dark"] .risk-box.green-box {
      border-color: var(--green);
      background: linear-gradient(180deg, rgba(0, 255, 136, 0.08) 0%, rgba(10, 25, 41, 0.7) 100%);
    }

    .risk-box.orange-box {
      height: 100%;
      border-color: #ffa03d;
      background: linear-gradient(180deg, #fff7ef 0%, #ffffff 100%);
    }

    [data-theme="dark"] .risk-box.orange-box {
      border-color: var(--orange);
      background: linear-gradient(180deg, rgba(255, 153, 51, 0.08) 0%, rgba(10, 25, 41, 0.7) 100%);
    }

    .risk-box.blue-box {
      border-color: #72a5ff;
      background: linear-gradient(180deg, #f5f9ff 0%, #ffffff 100%);
    }

    [data-theme="dark"] .risk-box.blue-box {
      border-color: var(--blue);
      background: linear-gradient(180deg, rgba(0, 212, 255, 0.08) 0%, rgba(10, 25, 41, 0.7) 100%);
    }

    .risk-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 11px;
      font-size: 15px;
      font-weight: 900;
    }

    .pill {
      padding: 3px 6px;
      border-radius: 999px;
      font-size: 10px;
      font-weight: 900;
      line-height: 1;
    }

    .pill.ok {
      color: #008c45;
      background: #dff7e9;
    }

    [data-theme="dark"] .pill.ok {
      color: var(--green);
      background: rgba(0, 255, 136, 0.15);
    }

    .pill.warn {
      color: var(--orange);
      background: #fff0dd;
    }

    [data-theme="dark"] .pill.warn {
      color: var(--orange);
      background: rgba(255, 153, 51, 0.15);
    }

    .risk-body {
      display: grid;
      grid-template-columns: 1fr 1fr;
      row-gap: 6px;
      column-gap: 10px;
      font-size: 12px;
      font-weight: 800;
      color: #142452;
    }

    [data-theme="dark"] .risk-body {
      color: #a0c4e8;
    }

    .risk-body .wide {
      grid-column: 1 / -1;
    }

    .zone-data {
      display: grid;
      gap: 8px;
    }

    .zone-env {
      display: flex;
      flex-wrap: wrap;
      gap: 6px 12px;
      font-size: 12px;
      font-weight: 800;
      color: #142452;
    }

    [data-theme="dark"] .zone-env {
      color: #a0c4e8;
    }

    .env-item {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      white-space: nowrap;
    }

    .env-item b {
      font-size: 14px;
      font-weight: 900;
    }

    .zone-status {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 8px;
      font-size: 12px;
      font-weight: 800;
      color: #142452;
    }

    [data-theme="dark"] .zone-status {
      color: #a0c4e8;
    }

    .status-item {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      white-space: nowrap;
      border:1px solid var(--blue);
      border-radius: 10px;
      padding:4px 6px;
      box-shadow: 0 0 10px rgba(18, 104, 255, 0.28);
    }

    .status-item b {
      font-weight: 900;
    }

    .zone-status .wide {
      grid-column: 1 / -1;
    }

    .risk-line {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      white-space: nowrap;
    }

    .risk-mini-icon {
      width: 15px;
      height: 15px;
      color: var(--blue);
      flex: 0 0 auto;
    }

    .ai-banner {
      /* min-height: 64px; */
      margin: var(--ai-banner-margin, 10px 0);
      padding: 0 18px 0 14px;
      display: grid;
      grid-template-columns: 340px 1fr 38px;
      align-items: center;
    }

    .ai-brand {
      position: relative;
      display: flex;
      align-items: center;
      gap: 16px;
      min-width: 0;
    }

    .ai-brand::before {
      content: "";
      width: 5px;
      height: 34px;
      border-radius: 999px;
      background: var(--blue);
      box-shadow: 0 0 10px rgba(18, 104, 255, 0.28);
      flex: 0 0 auto;
    }
  .title-brand{
    gap: 16px;
    padding:0 10px;
    margin-top:6px;
    }
    .title-brand::before {
      content: "";
      width: 5px;
      height: 34px;
      border-radius: 999px;
      background: var(--blue);
      box-shadow: 0 0 10px rgba(18, 104, 255, 0.28);
      flex: 0 0 auto;
    }

    .ai-brand strong {
      color: #073dff;
      font-size: 23px;
      font-weight: 900;
      white-space: nowrap;
    }

    [data-theme="dark"] .ai-brand strong {
      color: var(--blue);
      text-shadow: 0 0 8px rgba(0, 212, 255, 0.4);
    }

    .ai-orb {
      width: 34px;
      height: 34px;
      border-radius: 50%;
      display: grid;
      place-items: center;
      color: #fff;
      font-size: 18px;
      font-weight: 900;
      background:
        radial-gradient(circle at 35% 30%, #e9fbff 0 10%, transparent 22%),
        radial-gradient(circle, #2e9fff 0 38%, #1276e7 55%, #6fe9ff 74%, transparent 77%),
        #1b91f4;
      box-shadow: 0 0 0 4px rgba(32, 151, 247, 0.08), 0 0 20px rgba(18, 104, 255, 0.4);
    }

    [data-theme="dark"] .ai-orb {
      background:
        radial-gradient(circle at 35% 30%, rgba(0, 229, 255, 0.3) 0 10%, transparent 22%),
        radial-gradient(circle, #00aaff 0 38%, #0088cc 55%, #00e5ff 74%, transparent 77%),
        #0099dd;
      box-shadow: 0 0 0 4px rgba(0, 180, 255, 0.15), 0 0 25px rgba(0, 212, 255, 0.5);
    }

    .ai-copy {
      color: #11111194;
      font-size: 15px;
      font-weight: 800;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    [data-theme="dark"] .ai-copy {
      color: #a0c4e8;
    }

    .circle-btn {
      margin-left: 10px;
      width: 22px;
      height: 22px;
      border: 1px solid #347bff;
      border-radius: 50%;
      display: grid;
      place-items: center;
      color: var(--blue);
      background: #fff;
    }

    [data-theme="dark"] .circle-btn {
      border-color: var(--blue);
      color: var(--blue);
      background: rgba(0, 100, 200, 0.2);
    }

    .run-tag {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      color: #02a85f;
      background: #e8f9f0;
      padding: 4px 9px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 900;
    }

    [data-theme="dark"] .run-tag {
      color: var(--green);
      background: rgba(0, 255, 136, 0.12);
    }

    .agent-footer {
      color: #142452;
    }

    [data-theme="dark"] .agent-footer {
      color: #a0c4e8;
    }

    .metric-row {
      display: grid;
      gap: 8px;
    }

    .env-metrics {
      grid-template-columns: repeat(4, minmax(0, 1fr));
      margin-top: 6px;
    }

    .metric {
      /* min-height: 88px; */
      padding: 11px 7px 9px;
      border: 1px solid #e2eaf7;
      border-radius: 8px;
      background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
      text-align: center;
      display: grid;
      align-content: center;
      gap: 8px;
    }

    [data-theme="dark"] .metric {
      border-color: var(--line);
      background: linear-gradient(180deg, rgba(15, 35, 60, 0.8) 0%, rgba(10, 25, 41, 0.6) 100%);
    }

    .metric .label {
      color: #17295e;
      font-size: 12px;
      font-weight: 700;
    }

    [data-theme="dark"] .metric .label {
      color: #88aacc;
    }

    .metric .value {
      color: #071744;
      font-size: 17px;
      font-weight: 900;
      line-height: 1;
    }

    [data-theme="dark"] .metric .value {
      color: #e0f0ff;
    }

    .metric .state {
      font-size: 13px;
      font-weight: 900;
    }

    .service-metrics {
      height: 100px;
      display: grid;
      grid-template-columns: 0.85fr 1fr 1.1fr;
      gap: 10px;
      align-items: start;
    }

    .service-box客流,
    .service-box安全 {
      padding: 8px 10px;
      border: 1px solid #e2eaf7;
      border-radius: 8px;
      text-align: center;
    }

    .service-box客流 {
      background: linear-gradient(180deg, #f0f7ff 0%, #ffffff 100%);
    }

    .service-box安全 {
      background: linear-gradient(180deg, #f4fff9 0%, #ffffff 100%);
    }

    [data-theme="dark"] .service-box客流 {
      background: linear-gradient(180deg, rgba(0, 100, 200, 0.15) 0%, rgba(10, 25, 41, 0.7) 100%);
      border-color: var(--line);
    }

    [data-theme="dark"] .service-box安全 {
      background: linear-gradient(180deg, rgba(0, 255, 136, 0.1) 0%, rgba(10, 25, 41, 0.7) 100%);
      border-color: var(--line);
    }

    .service-label {
      font-size: 11px;
      font-weight: 700;
      color: #17295e;
      margin-bottom: 3px;
    }

    [data-theme="dark"] .service-label {
      color: #88aacc;
    }

    .service-value {
      font-size: 18px;
      font-weight: 900;
    }

    .service-value.blue {
      color: #1268ff;
    }

    .service-value.green {
      color: #08ae66;
    }

    .service-value.large {
      font-size: 20px;
    }

    [data-theme="dark"] .service-value.blue {
      color: var(--blue);
    }

    [data-theme="dark"] .service-value.green {
      color: var(--green);
    }

    .service-box雷达 {
    transform: translateY(-10px);
      display: flex;
      justify-content: center;
      align-items: flex-start;
    }

    .radar-chart {
    transform: scale(1.2);
      width: 130px;
      height: 130px;
    }

    .device-stats {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 10px;
      margin-bottom: 12px;
    }

    .device-box故障,
    .device-box告警,
    .device-box维修 {
      padding: 10px 12px;
      border: 1px solid #e2eaf7;
      border-radius: 8px;
      text-align: center;
    }

    .device-box故障 {
      background: linear-gradient(180deg, #fff0ee 0%, #ffffff 100%);
    }

    .device-box告警 {
      background: linear-gradient(180deg, #fff7ef 0%, #ffffff 100%);
    }

    .device-box维修 {
      background: linear-gradient(180deg, #f0f7ff 0%, #ffffff 100%);
    }

    [data-theme="dark"] .device-box故障 {
      background: linear-gradient(180deg, rgba(255, 68, 68, 0.12) 0%, rgba(10, 25, 41, 0.7) 100%);
      border-color: var(--line);
    }

    [data-theme="dark"] .device-box告警 {
      background: linear-gradient(180deg, rgba(255, 153, 51, 0.12) 0%, rgba(10, 25, 41, 0.7) 100%);
      border-color: var(--line);
    }

    [data-theme="dark"] .device-box维修 {
      background: linear-gradient(180deg, rgba(0, 255, 136, 0.1) 0%, rgba(10, 25, 41, 0.7) 100%);
      border-color: var(--line);
    }

    .device-label {
      font-size: 12px;
      font-weight: 700;
      color: #17295e;
      margin-bottom: 4px;
    }

    [data-theme="dark"] .device-label {
      color: #88aacc;
    }

    .device-value {
      font-size: 26px;
      font-weight: 900;
    }

    .device-value.red {
      color: #ff1515;
    }

    .device-value.orange {
      color: #ff6a00;
    }

    .device-value.green {
      color: #08ae66;
    }

    [data-theme="dark"] .device-value.red {
      color: var(--red);
    }

    [data-theme="dark"] .device-value.orange {
      color: var(--orange);
    }

    [data-theme="dark"] .device-value.green {
      color: var(--green);
    }

    .analysis {
      display: grid;
      gap: 8px;
      color: #12255b;
      font-size: 13px;
      font-weight: 700;
      line-height: 1.5;
    }

    [data-theme="dark"] .analysis {
      color: #a0c4e8;
    }

    .analysis p {
      margin: 0;
    }

    .analysis strong {
      display: block;
      color: #0b51ff;
      font-size: 15px;
      font-weight: 900;
      line-height: 1.1;
    }

    [data-theme="dark"] .analysis strong {
      color: var(--blue);
    }

    .analysis .suggest {
      color: #009d58;
    }

    [data-theme="dark"] .analysis .suggest {
      color: var(--green);
    }

    .equip-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
      margin-top: 6px;
    }

    .equip-card {
      padding: 10px 12px;
      border: 1px solid #e2eaf7;
      border-radius: 8px;
      background: #fff;
      min-height: 84px;
    }

    .equip-card-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 9px;
      font-size: 13px;
      font-weight: 900;
    }

    [data-theme="dark"] .equip-card-head {
      color: #e0f0ff;
    }

    .agent-title {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 4px;
      font-size: 16px;
      font-weight: 900;
    }

    [data-theme="dark"] .agent-title {
      color: #e0f0ff;
    }

    .equip-icon {
      width: 22px;
      height: 22px;
      color: #0c1738;
    }

    .equip-kv {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 7px 12px;
      color: #13245a;
      font-size: 12px;
      font-weight: 800;
    }

    [data-theme="dark"] .equip-kv {
      color: #a0c4e8;
    }

    .equip-card {
      padding: 10px 12px;
      border: 1px solid #e2eaf7;
      border-radius: 8px;
      background: #fff;
      min-height: 84px;
    }

    [data-theme="dark"] .equip-card {
      border-color: var(--line);
      background: rgba(10, 25, 41, 0.6);
    }

    .warning-stats {
      grid-template-columns: repeat(4, minmax(0, 1fr));
      margin-top: 10px;
    }

    .warning-stats .metric {
      min-height: 78px;
    }

    .warning-stats .value {
      font-size: 24px;
    }

    .suggestions {
      display: grid;
      gap: 0;
      border: 1px solid #e1e9f7;
      border-radius: 8px;
      overflow: hidden;
      margin-top: 4px;
    }

    [data-theme="dark"] .suggestions {
      border-color: var(--line);
    }

    .suggestion {
      display: grid;
      grid-template-columns: 35px 1fr 88px;
      align-items: center;
      gap: 9px;
      min-height: 47px;
      padding: 6px 9px;
      border-bottom: 1px solid #e6eef9;
      background: #fff;
    }

    [data-theme="dark"] .suggestion {
      border-bottom-color: var(--line);
      background: rgba(10, 25, 41, 0.5);
    }

    .suggestion:last-child {
      border-bottom: 0;
    }

    .suggestion span {
      color: #17265a;
      font-size: 12px;
      font-weight: 800;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    [data-theme="dark"] .suggestion span {
      color: #a0c4e8;
    }

    .risk-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 11px;
      font-size: 15px;
      font-weight: 900;
    }

    [data-theme="dark"] .risk-head {
      color: #e0f0ff;
    }

    .action {
      justify-content: center;
      min-height: 32px;
      padding: 0 10px;
      border: 1px solid #a9b5ff;
      border-radius: 5px;
      color: #123dff;
      background: #f6f7ff;
      font-size: 12px;
      font-weight: 900;
      white-space: nowrap;
    }

    [data-theme="dark"] .action {
      border-color: var(--blue);
      color: var(--blue);
      background: rgba(0, 150, 255, 0.15);
    }

    .compute-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 10px;
      margin-top: 8px;
    }

    .compute-grid .metric {
      min-height: 72px;
      gap: 6px;
    }

    .compute-grid .value {
      font-size: 19px;
    }

    .agent-layout {
      display: grid;
      grid-template-columns: 142px 1fr;
      align-items: center;
      margin-top: 4px;
    }

    .agent-title {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 4px;
      font-size: 16px;
      font-weight: 900;
    }

    .run-tag {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      color: #02a85f;
      background: #e8f9f0;
      padding: 4px 9px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 900;
    }

    .dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: currentColor;
    }

    .ring {
      --p: 90;
      position: relative;
      width: 106px;
      aspect-ratio: 1;
      border-radius: 50%;
      background:
        conic-gradient(var(--green) calc(var(--p) * 1%), #e6edf6 0);
      display: grid;
      place-items: center;
      box-shadow: inset 0 0 0 1px #e4edf7;
    }

    .ring::after {
      content: "";
      position: absolute;
      inset: 15px;
      border-radius: 50%;
      background: #fff;
      box-shadow: inset 0 0 0 1px #e9f0f8;
    }

    [data-theme="dark"] .ring::after {
      background: var(--bg);
      box-shadow: inset 0 0 0 1px var(--line);
    }

    .ring span {
      position: relative;
      z-index: 1;
      display: grid;
      justify-items: center;
      color: #101b43;
      font-weight: 900;
      line-height: 1;
    }

    [data-theme="dark"] .ring span {
      color: #e0f0ff;
    }

    .ring b {
      font-size: 22px;
    }

    .ring small {
      margin-top: 5px;
      font-size: 10px;
      color: #24365e;
      font-weight: 900;
    }

    [data-theme="dark"] .ring small {
      color: #88aacc;
    }

    .agent-kv {
      display: grid;
      grid-template-columns: minmax(54px, 0.78fr) max-content;
      gap: 7px 8px;
      padding: 9px 10px;
      border: 1px solid #e1eaf7;
      border-radius: 8px;
      background: #fff;
      font-size: 12px;
      font-weight: 900;
      color: #15265a;
      white-space: nowrap;
    }

    [data-theme="dark"] .agent-kv {
      border-color: var(--line);
      background: rgba(10, 25, 41, 0.6);
      color: #a0c4e8;
          margin-top: 10px;
    }

    .agent-kv .right {
      text-align: right;
      color: #071744;
    }

    [data-theme="dark"] .agent-kv .right {
      color: #00d4ff;
    }

    .cooling-layout {
      display: grid;
      grid-template-columns: 160px 1fr;
      gap: 12px;
      align-items: center;
    }

    .mini-chart {
      width: 100%;
      height: 138px;
      margin-top:10px;
      margin-bottom:10px;
    }

    .cooling-foot {
      display: flex;
      gap: 18px;
      align-items: center;
      color: #12245a;
      font-size: 13px;
      font-weight: 900;
      margin-top: 4px;
      text-align: center;
    }

    [data-theme="dark"] .cooling-foot {
      color: #a0c4e8;
    }

    .knowledge-layout {
      display: grid;
      grid-template-columns: 132px 1fr;
      gap: 12px;
      align-items: center;
      margin-top: 2px;
    }

    .k-legend {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 8px 12px;
      font-size: 12px;
      font-weight: 900;
      color: #14265a;
    }

    [data-theme="dark"] .k-legend {
      color: #a0c4e8;
    }

    .knowledge-foot {
      margin-top: 12px;
      color: #12245a;
      font-size: 14px;
      line-height: 1.55;
      font-weight: 900;
    }

    [data-theme="dark"] .knowledge-foot {
      color: #88aacc;
    }

    /* 派单弹窗样式 */
    .dispatch-modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.65);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 2000;
      animation: overlay-fade-in 0.2s ease;
    }
    @keyframes overlay-fade-in {
      from { opacity: 0; }
      to { opacity: 1; }
    }
    .dispatch-modal-content {
      background: var(--panel);
      border-radius: 16px;
      width: 95%;
      max-width: 900px;
      display: flex;
      flex-direction: column;
      box-shadow: 0 12px 48px rgba(0, 0, 0, 0.3);
      animation: modal-slide-up 0.3s ease;
    }
    @keyframes modal-slide-up {
      from { opacity: 0; transform: translateY(20px); }
      to { opacity: 1; transform: translateY(0); }
    }
    [data-theme="dark"] .dispatch-modal-content {
      background: var(--bg);
      border: 1px solid var(--line);
    }
    .dispatch-modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 18px 24px;
      border-bottom: 1px solid var(--line);
    }
    .dispatch-header-left {
      display: flex;
      align-items: center;
      gap: 10px;
      color: var(--blue);
    }
    .dispatch-header-left h3 {
      margin: 0;
      font-size: 17px;
      font-weight: 800;
      color: var(--text);
    }
    .dispatch-modal-close {
      width: 32px;
      height: 32px;
      border: none;
      background: transparent;
      color: var(--muted);
      font-size: 22px;
      cursor: pointer;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.15s;
    }
    .dispatch-modal-close:hover {
      background: var(--soft);
      color: var(--text);
    }
    .dispatch-modal-body {
      padding: 16px 24px;
    }
    .dispatch-section {
      margin-bottom: 14px;
    }
    .dispatch-section:last-child {
      margin-bottom: 0;
    }
    .section-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      font-weight: 700;
      color: var(--text);
      margin: 0 0 14px 0;
      padding-bottom: 8px;
      border-bottom: 2px solid #1a3a5c;
    }
    .dispatch-info-grid {
      display: grid;
      grid-template-columns: repeat(5, 1fr);
      gap: 12px;
    }
    .info-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 10px 12px;
      background: var(--soft);
      border-radius: 8px;
    }
    .info-label {
      font-size: 11px;
      font-weight: 600;
      color: var(--muted);
    }
    .info-value {
      font-size: 13px;
      font-weight: 700;
      color: var(--text);
    }
    .info-value.order-num {
      color: var(--blue);
      font-family: monospace;
    }
    .dispatch-textarea-box {
      margin-bottom: 14px;
    }
    .dispatch-textarea-box:last-child {
      margin-bottom: 0;
    }
    .dispatch-cards-row {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 14px;
      margin-bottom: 14px;
    }
    .dispatch-card-item {
      padding: 12px;
      background: var(--bg);
      border: 1px solid var(--line);
      border-radius: 10px;
    }
    .dispatch-card-item .typewriter-box,
    .dispatch-card-item .editable-text {
      margin-bottom: 0;
      border: none;
      background: transparent;
      padding: 8px 0;
    }
    .textarea-label {
      display: block;
      font-size: 12px;
      font-weight: 700;
      color: var(--muted);
      margin-bottom: 6px;
    }
    .typewriter-box {
      padding: 10px 12px;
      background: var(--bg);
      border: 1px solid var(--line);
      border-radius: 8px;
      margin-bottom: 8px;
      min-height: 48px;
      font-size: 13px;
      line-height: 1.6;
      color: var(--text);
      font-weight: 600;
    }
    .editable-text {
      padding: 10px 12px;
      background: var(--bg);
      border: 1px solid var(--line);
      border-radius: 8px;
      min-height: 48px;
      font-size: 13px;
      line-height: 1.6;
      color: var(--text);
      font-weight: 600;
      outline: none;
      transition: border-color 0.2s;
      cursor: text;
    }
    .editable-text:hover {
      border-color: var(--blue);
    }
    .editable-text:focus {
      border-color: var(--blue);
      background: var(--panel);
    }
    .typewriter-text {
      white-space: pre-wrap;
    }
    .typing-cursor {
      animation: cursor-blink 0.8s infinite;
      color: var(--blue);
      font-weight: 900;
    }
    @keyframes cursor-blink {
      0%, 50% { opacity: 1; }
      51%, 100% { opacity: 0; }
    }
    .dispatch-modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: 12px;
      padding: 16px 24px;
      border-top: 1px solid var(--line);
    }
    .dispatch-person-box {
      margin-bottom: 14px;
    }
    .person-card {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 14px 16px;
      background: var(--bg);
      border: 1px solid var(--line);
      border-radius: 10px;
      margin-bottom: 10px;
    }
    .person-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .person-avatar {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      background: linear-gradient(135deg, var(--blue), #0099ff);
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      flex-shrink: 0;
    }
    .person-info {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .person-name {
      font-size: 15px;
      font-weight: 700;
      color: var(--text);
    }
    .person-title {
      font-size: 12px;
      font-weight: 600;
      color: var(--muted);
    }
    .person-contact {
      display: flex;
      gap: 12px;
      margin-top: 4px;
    }
    .contact-item {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 11px;
      font-weight: 500;
      color: var(--muted);
    }
    .contact-item svg {
      flex-shrink: 0;
    }
    .person-right {
      display: flex;
      gap: 10px;
    }
    .person-badge {
      display: flex;
      flex-direction: row;
      align-items: center;
      gap: 6px;
      padding: 8px 14px;
      border-radius: 8px;
      background: var(--soft);
    }
    .person-badge.match {
      background: rgba(18, 104, 255, 0.1);
    }
    .person-badge.status {
      background: rgba(8, 174, 102, 0.1);
    }
    .badge-label {
      font-size: 11px;
      font-weight: 600;
      color: var(--muted);
    }
    .badge-value {
      font-size: 14px;
      font-weight: 800;
      color: var(--blue);
    }
    .person-badge.status .badge-value {
      color: #08ae66;
    }
    .badge-tasks {
      font-size: 12px;
      font-weight: 700;
      color: var(--muted);
      padding-left: 6px;
      border-left: 1px solid rgba(8, 174, 102, 0.3);
    }
    .status-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #08ae66;
    }
    .person-reason {
      display: flex;
      gap: 6px;
      padding: 10px 14px;
      background: var(--soft);
      border-radius: 8px;
      font-size: 13px;
      line-height: 1.5;
    }
    .reason-label {
      font-weight: 700;
      color: var(--muted);
      flex-shrink: 0;
    }
    .reason-text {
      font-weight: 500;
      color: var(--text);
    }
    /* 设备控制弹窗样式 */
    .device-control-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.6);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 10001;
      animation: overlay-fade-in 0.2s ease;
    }
    .device-control-modal {
      background: var(--panel);
      border-radius: 16px;
      width: 90%;
      max-width: 700px;
      box-shadow: 0 12px 48px rgba(0, 0, 0, 0.3);
      animation: modal-slide-up 0.3s ease;
      overflow: hidden;
    }
    .device-control-header {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 18px 24px;
      border-bottom: 1px solid var(--line);
      font-size: 16px;
      font-weight: 700;
      color: var(--text);
    }
    .device-control-body {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;
      padding: 24px;
    }
    .device-svg-section {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
    }
    .device-svg-wrapper {
      width: 280px;
      height: 280px;
      position: relative;
    }
    .device-fan-svg {
      width: 100%;
      height: 100%;
      color: var(--text);
    }
    .fan-blades {
      animation: fan-spin 1.5s linear infinite;
      transform-origin: 150px 120px;
    }
    .wind-effect {
      transition: opacity 0.5s ease;
    }
    .wind-particles circle {
      animation: particle-float 2s ease-in-out infinite;
    }
    .wind-particles circle:nth-child(1) { animation-delay: 0s; }
    .wind-particles circle:nth-child(2) { animation-delay: 0.3s; }
    .wind-particles circle:nth-child(3) { animation-delay: 0.6s; }
    .wind-particles circle:nth-child(4) { animation-delay: 0.9s; }
    .wind-particles circle:nth-child(5) { animation-delay: 1.2s; }
    .wind-particles circle:nth-child(6) { animation-delay: 1.5s; }
    .wind-ripples ellipse {
      animation: ripple-expand 2s ease-out infinite;
    }
    .wind-ripples ellipse:nth-child(1) { animation-delay: 0s; }
    .wind-ripples ellipse:nth-child(2) { animation-delay: 0.5s; }
    .wind-ripples ellipse:nth-child(3) { animation-delay: 1s; }
    .device-name {
      margin-top: 12px;
      font-size: 15px;
      font-weight: 700;
      color: var(--text);
    }
    .device-info-section {
      display: flex;
      flex-direction: column;
      gap: 16px;
      justify-content: center;
    }
    .info-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 16px;
      background: var(--bg);
      border-radius: 8px;
    }
    .info-label {
      font-size: 13px;
      font-weight: 600;
      color: var(--muted);
    }
    .info-value {
      font-size: 14px;
      font-weight: 700;
      color: var(--text);
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .action-text {
      color: var(--blue);
    }
    .time-text {
      font-family: monospace;
      font-size: 13px;
    }
    .status-pending {
      color: var(--orange);
    }
    .status-success {
      color: #08ae66;
    }
    .link-waiting {
      color: var(--muted);
    }
    .link-success {
      color: #08ae66;
    }
    .status-spinner,
    .link-spinner {
      width: 14px;
      height: 14px;
      border: 2px solid currentColor;
      border-top-color: transparent;
      border-radius: 50%;
      animation: spinner 0.8s linear infinite;
    }
    .status-icon,
    .link-icon {
      animation: icon-pop 0.3s ease;
    }
    @keyframes fan-spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
    @keyframes wind-blow {
      0%, 100% { opacity: 0; transform: translateX(0); }
      50% { opacity: 0.6; transform: translateX(10px); }
    }
    @keyframes particle-float {
      0%, 100% { transform: translate(0, 0); opacity: 0.3; }
      25% { transform: translate(15px, -10px); opacity: 0.6; }
      50% { transform: translate(30px, 0); opacity: 0.4; }
      75% { transform: translate(15px, 10px); opacity: 0.6; }
    }
    @keyframes ripple-expand {
      0% { transform: scale(0.8); opacity: 0.3; }
      100% { transform: scale(1.5); opacity: 0; }
    }
    @keyframes spinner {
      to { transform: rotate(360deg); }
    }
    @keyframes icon-pop {
      0% { transform: scale(0); }
      70% { transform: scale(1.2); }
      100% { transform: scale(1); }
    }
    .dispatch-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 12px 24px;
      border: none;
      border-radius: 10px;
      font-size: 14px;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.2s;
    }
    .dispatch-btn svg {
      width: 18px;
      height: 18px;
    }
    .dispatch-btn.cancel {
      background: var(--soft);
      color: var(--muted);
    }
    .dispatch-btn.cancel:hover {
      background: var(--line);
      color: var(--text);
    }
    .dispatch-btn.confirm {
      background: linear-gradient(135deg, #1268ff, #0099ff);
      color: #fff;
    }
    .dispatch-btn.confirm:hover {
      background: linear-gradient(135deg, #0055dd, #0077cc);
      transform: translateY(-2px);
      box-shadow: 0 4px 16px rgba(18, 104, 255, 0.3);
    }

    /* ===== 安全指数弹窗 ===== */
    .safety-modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(7, 21, 59, 0.75);
      backdrop-filter: blur(8px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      animation: fadeIn 0.3s ease;
    }

    .safety-modal {
      background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
      border-radius: 20px;
      width: 90%;
      max-width: 1100px;
      height: 90vh;
      display: flex;
      flex-direction: column;
      box-shadow: 0 20px 60px rgba(0,0,0,0.3), 0 0 40px rgba(0,212,255,0.1);
      animation: slideUp 0.4s ease;
      overflow: hidden;
    }

    [data-theme="dark"] .safety-modal {
      background: linear-gradient(135deg, #0d1f3c 0%, #0a1628 100%);
    }

    .safety-modal-body {
      flex: 1;
      overflow-y: auto;
      overflow-x: hidden;
      padding: 32px;
    }

    .safety-modal-body::-webkit-scrollbar {
      width: 0;
      height: 0;
    }

    .safety-modal-body {
      scrollbar-width: none;
      -ms-overflow-style: none;
    }

    .safety-modal-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 24px 32px;
      background: linear-gradient(135deg, rgba(0,212,255,0.08), rgba(0,102,255,0.05));
      border-bottom: 1px solid rgba(0,212,255,0.2);
    }

    .safety-modal-header .header-title {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 22px;
      font-weight: 900;
      color: #0b51ff;
    }

    [data-theme="dark"] .safety-modal-header .header-title {
      color: var(--blue);
    }

    .safety-modal-header svg {
      color: #1268ff;
    }

    .modal-close-btn {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: rgba(0,0,0,0.05);
      border: none;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.3s;
      color: #666;
    }

    .modal-close-btn:hover {
      background: rgba(255,68,68,0.1);
      color: #ff4444;
      transform: rotate(90deg);
    }

    [data-theme="dark"] .modal-close-btn {
      background: rgba(255,255,255,0.05);
      color: #8899aa;
    }

    .safety-score-section {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 40px;
      margin-bottom: 40px;
      padding: 32px;
      background: linear-gradient(135deg, rgba(0,212,255,0.05), rgba(0,102,255,0.03));
      border-radius: 16px;
      border: 1px solid rgba(0,212,255,0.15);
    }

    [data-theme="dark"] .safety-score-section {
      background: linear-gradient(135deg, rgba(0,212,255,0.08), rgba(0,102,255,0.05));
    }

    .safety-score-circle {
      position: relative;
      width: 200px;
      height: 200px;
    }

    .score-ring {
      width: 100%;
      height: 100%;
      transform: rotate(-90deg);
    }

    .score-progress {
      transition: stroke-dashoffset 1s ease;
    }

    .score-center {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      text-align: center;
    }

    .score-value {
      font-size: 56px;
      font-weight: 900;
      line-height: 1;
      margin-bottom: 8px;
    }

    .score-good {
      color: #00ff88;
      text-shadow: 0 0 20px rgba(0,255,136,0.3);
    }

    .score-warning {
      color: #ffaa00;
      text-shadow: 0 0 20px rgba(255,170,0,0.3);
    }

    .score-danger {
      color: #ff4444;
      text-shadow: 0 0 20px rgba(255,68,68,0.3);
    }

    .score-label {
      font-size: 14px;
      color: #8899aa;
      font-weight: 700;
    }

    .safety-formula {
      text-align: center;
    }

    .formula-text {
      font-size: 16px;
      color: #666;
      margin-bottom: 8px;
      font-weight: 700;
    }

    [data-theme="dark"] .formula-text {
      color: #8899aa;
    }

    .formula-calc {
      font-size: 20px;
      color: #0b51ff;
      font-weight: 900;
      font-family: 'Courier New', monospace;
    }

    [data-theme="dark"] .formula-calc {
      color: var(--blue);
    }

    .anomaly-stats {
      margin-bottom: 32px;
      display: flex;
      gap: 16px;
      align-items: stretch;
    }

    .anomaly-total-inline {
      text-align: center;
      padding: 20px 24px;
      background: linear-gradient(135deg, rgba(255,170,0,0.1), rgba(255,68,68,0.05));
      border-radius: 12px;
      border: 1px solid rgba(255,170,0,0.2);
      display: flex;
      flex-direction: column;
      justify-content: center;
      min-width: 140px;
      flex-shrink: 0;
    }

    .total-label {
      font-size: 14px;
      color: #8899aa;
      margin-bottom: 8px;
      font-weight: 700;
    }

    .total-value {
      font-size: 48px;
      font-weight: 900;
      color: #ffaa00;
      text-shadow: 0 0 20px rgba(255,170,0,0.2);
      line-height: 1;
    }

    .breakdown-item {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 20px;
      background: white;
      border-radius: 12px;
      border: 1px solid rgba(0,0,0,0.08);
      transition: all 0.3s;
      flex: 1;
    }

    .breakdown-item:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 20px rgba(0,0,0,0.1);
      border-color: rgba(0,212,255,0.3);
    }

    [data-theme="dark"] .breakdown-item {
      background: rgba(13,31,60,0.6);
      border-color: rgba(255,255,255,0.08);
    }

    .breakdown-icon {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      flex-shrink: 0;
    }

    .breakdown-icon.device-fault {
      background: linear-gradient(135deg, rgba(255,68,68,0.15), rgba(255,102,85,0.1));
    }

    .breakdown-icon.env-abnormal {
      background: linear-gradient(135deg, rgba(255,170,0,0.15), rgba(255,136,0,0.1));
    }

    .breakdown-icon.energy-abnormal {
      background: linear-gradient(135deg, rgba(0,212,255,0.15), rgba(0,102,255,0.1));
    }

    .breakdown-icon.pending-items {
      background: linear-gradient(135deg, rgba(123,99,240,0.15), rgba(102,51,204,0.1));
    }

    .breakdown-content {
      flex: 1;
      min-width: 0;
    }

    .breakdown-label {
      font-size: 13px;
      color: #8899aa;
      margin-bottom: 4px;
      font-weight: 700;
    }

    .breakdown-value {
      font-size: 28px;
      font-weight: 900;
      color: #0b1a48;
      margin-bottom: 4px;
    }

    [data-theme="dark"] .breakdown-value {
      color: #e0f0ff;
    }

    .breakdown-detail {
      font-size: 12px;
      color: #666;
    }

    [data-theme="dark"] .breakdown-detail {
      color: #8899aa;
    }

    .safety-suggestions {
      padding: 24px;
      background: linear-gradient(135deg, rgba(0,212,255,0.05), rgba(0,102,255,0.03));
      border-radius: 12px;
      border: 1px solid rgba(0,212,255,0.15);
    }

    .suggestion-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 16px;
      font-weight: 900;
      color: #0b51ff;
      margin-bottom: 16px;
    }

    [data-theme="dark"] .suggestion-title {
      color: var(--blue);
    }

    .suggestion-list {
      display: grid;
      gap: 12px;
    }

    .suggestion-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px 16px;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 700;
    }

    .suggestion-item.danger {
      background: rgba(255,68,68,0.1);
      border-left: 3px solid #ff4444;
      color: #ff4444;
    }

    .suggestion-item.warning {
      background: rgba(255,170,0,0.1);
      border-left: 3px solid #ffaa00;
      color: #ffaa00;
    }

    .suggestion-item.info {
      background: rgba(0,212,255,0.1);
      border-left: 3px solid #00d4ff;
      color: #00d4ff;
    }

    .suggestion-item.success {
      background: rgba(0,255,136,0.1);
      border-left: 3px solid #00ff88;
      color: #00a85f;
    }

    [data-theme="dark"] .suggestion-item.success {
      color: #00ff88;
    }

    .suggestion-icon {
      font-size: 18px;
      flex-shrink: 0;
    }

    /* ===== 事项处理弹窗 ===== */
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
      border-bottom: 1px solid var(--line);
      display: flex;
      align-items: center;
      justify-content: space-between;
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

    /* KPI行 */
    .warning-kpi-row {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 10px;
      margin-bottom: 16px;
    }

    .warning-kpi-card {
      background: var(--panel);
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

    /* 筛选栏 */
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

    /* AI功能区域 */
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

    /* 智能处理 + 事项列表 并排 */
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
      border-left: 3px solid #1268ff;
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

    .assistant-fab {
      position: fixed;
      right: 24px;
      bottom: 24px;
      width: 60px;
      height: 60px;
      border-radius: 50%;
      background: linear-gradient(135deg, var(--blue), var(--blue-deep));
      border: none;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 6px 20px rgba(18, 104, 255, 0.35);
      transition: all 0.3s;
      z-index: 1000;
    }

    .assistant-fab:hover {
      transform: scale(1.1);
      box-shadow: 0 8px 28px rgba(18, 104, 255, 0.5);
    }

    .assistant-fab svg {
      width: 30px;
      height: 30px;
      color: #fff;
    }

    [data-theme="dark"] .assistant-fab {
      background: linear-gradient(135deg, #00aaff, #0066cc);
      box-shadow: 0 6px 20px rgba(0, 180, 255, 0.4);
    }

    .assistant-panel {
      position: fixed;
      right: 24px;
      bottom: 100px;
      width: 380px;
      height: 520px;
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 16px;
      box-shadow: 0 12px 40px rgba(30, 82, 162, 0.15);
      display: none;
      flex-direction: column;
      z-index: 1000;
      overflow: hidden;
    }

    .assistant-panel.active {
      display: flex;
    }

    [data-theme="dark"] .assistant-panel {
      box-shadow: 0 12px 40px rgba(0, 100, 200, 0.3);
    }

    .assistant-header {
      padding: 16px 20px;
      background: linear-gradient(135deg, rgba(18, 104, 255, 0.1), rgba(8, 174, 102, 0.08));
      border-bottom: 1px solid var(--line);
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    [data-theme="dark"] .assistant-header {
      background: linear-gradient(135deg, rgba(0, 150, 255, 0.15), rgba(0, 255, 136, 0.1));
    }

    .assistant-header-title {
      display: flex;
      align-items: center;
      gap: 10px;
      color: var(--text);
      font-size: 16px;
      font-weight: 900;
    }

    .assistant-header-title svg {
      width: 22px;
      height: 22px;
      color: var(--blue);
    }

    .assistant-close {
      width: 28px;
      height: 28px;
      border: none;
      background: transparent;
      cursor: pointer;
      color: var(--muted);
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 6px;
      transition: all 0.2s;
    }

    .assistant-close:hover {
      background: var(--soft);
      color: var(--text);
    }

    .assistant-messages {
      flex: 1;
      overflow-y: auto;
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .assistant-message {
      max-width: 85%;
      padding: 10px 14px;
      border-radius: 12px;
      font-size: 14px;
      font-weight: 700;
      line-height: 1.5;
    }

    .assistant-message.bot {
      align-self: flex-start;
      background: var(--soft);
      color: var(--text);
      border-bottom-left-radius: 4px;
    }

    .assistant-message.user {
      align-self: flex-end;
      background: var(--blue);
      color: #fff;
      border-bottom-right-radius: 4px;
    }

    [data-theme="dark"] .assistant-message.bot {
      background: rgba(0, 100, 200, 0.15);
    }

    [data-theme="dark"] .assistant-message.user {
      background: var(--blue-deep);
    }

    .assistant-input-area {
      padding: 12px 16px;
      border-top: 1px solid var(--line);
      display: flex;
      gap: 8px;
    }

    .assistant-input {
      flex: 1;
      padding: 10px 14px;
      border: 1px solid var(--line);
      border-radius: 20px;
      background: var(--bg);
      color: var(--text);
      font-size: 14px;
      font-weight: 700;
      outline: none;
      transition: border-color 0.2s;
    }

    .assistant-input:focus {
      border-color: var(--blue);
    }

    .assistant-send {
      width: 40px;
      height: 40px;
      border: none;
      border-radius: 50%;
      background: var(--blue);
      color: #fff;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
    }

    .assistant-send:hover {
      background: var(--blue-deep);
      transform: scale(1.05);
    }

    .assistant-send svg {
      width: 18px;
      height: 18px;
    }

    @media (max-width: 820px) {
      .assistant-panel {
        right: 8px;
        left: 8px;
        width: auto;
        bottom: 92px;
      }

      .assistant-fab {
        right: 16px;
        bottom: 16px;
      }
    }

    .knowledge-layout {
      display: grid;
      grid-template-columns: 145px 1fr;
      gap: 18px;
      align-items: center;
      margin-top: 2px;
    }

    .knowledge-ring {
      width: 102px;
      aspect-ratio: 1;
      border-radius: 20%;
      background: conic-gradient(
        #156dff 0 14%,
        #1fc3d7 14% 28%,
        #10b86b 28% 42%,
        #ff9933 42% 56%,
        #7b63f0 56% 70%,
        #ff4444 70% 84%,
        #20c4d6 84% 100%
      );
      display: grid;
      place-items: center;
      position: relative;
    }

    .knowledge-ring::after {
      content: "";
      position: absolute;
      inset: 34px;
      border-radius: 50%;
      background: #fff;
    }

    [data-theme="dark"] .knowledge-ring::after {
      background: var(--bg);
    }

    .knowledge-ring span {
      position: relative;
      z-index: 1;
      display: grid;
      justify-items: center;
      color: #0b1a48;
      font-weight: 900;
    }

    [data-theme="dark"] .knowledge-ring span {
      color: #e0f0ff;
    }

    .knowledge-ring b {
      font-size: 27px;
      line-height: 1;
    }

    .knowledge-ring small {
      margin-top: 5px;
      font-size: 12px;
    }

    [data-theme="dark"] .knowledge-ring small {
      color: #88aacc;
    }

    .k-legend {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 10px 14px;
      font-size: 12px;
      font-weight: 900;
      color: #14265a;
    }

    .legend-dot {
      width: 14px;
      height: 14px;
      border-radius: 50%;
      display: inline-block;
      margin-right: 10px;
      vertical-align: -2px;
    }

    .knowledge-foot {
      margin-top: 12px;
      color: #12245a;
      font-size: 14px;
      line-height: 1.55;
      font-weight: 900;
    }

    .divider-title {
      display: flex;
      align-items: center;
      gap: 11px;
      color: #0b51ff;
      font-size: 22px;
      font-weight: 900;
      padding: var(--divider-title-padding, 0);
    }

    [data-theme="dark"] .divider-title {
      color: var(--blue);
      text-shadow: 0 0 8px rgba(0, 212, 255, 0.3);
    }

    .divider-title::after {
      content: "";
      height: 1px;
      flex: 1;
      background: linear-gradient(90deg, #7da8ff, transparent);
    }

    [data-theme="dark"] .divider-title::after {
      background: linear-gradient(90deg, var(--blue), transparent);
    }

    .headline-tight {
      margin-bottom: 6px;
    }

    @media (min-width: 1451px) {
      .top-grid .card {
        height: 280px;
        min-height: 0;
      }

      .mid-grid .card {
        height: 260px;
        min-height: 0;
      }

      .bottom-grid .card {
        height: 100%;
        min-height: 0;
      }

      .energy-chart {
        min-height: 176px;
      }
    }

    @media (max-width: 1450px) {
      .top-grid,
      .mid-grid,
      .bottom-grid {
        grid-template-columns: repeat(2, minmax(320px, 1fr));
      }

      .ai-banner {
        grid-template-columns: 300px 1fr 38px;
      }
    }

    @media (max-width: 820px) {
      .dashboard {
        padding: 0 8px;
      }

      .title {
        height: auto;
        font-size: 34px;
      }

      .top-grid,
      .mid-grid,
      .bottom-grid {
        grid-template-columns: 1fr;
      }

      .card {
        padding: 12px;
      }

      .ai-banner {
        grid-template-columns: 1fr 34px;
        gap: 10px;
        padding: 10px 12px;
      }

      .ai-brand {
        grid-column: 1 / -1;
      }

      .ai-copy {
        font-size: 18px;
        white-space: normal;
      }

      .risk-grid,
      .env-metrics,
      .warning-stats,
      .equip-grid,
      .compute-grid,
      .agent-layout,
      .cooling-layout,
      .knowledge-layout {
        grid-template-columns: 1fr;
      }

      .suggestion {
        grid-template-columns: 30px 1fr;
      }

      .suggestion .action {
        grid-column: 2;
        width: max-content;
      }
    }
  
    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.6);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1001;
    }

    .modal-content {
      background: #fff;
      border-radius: 12px;
      padding: 24px;
      width: 95%;
      max-width: 1600px;
      height: 92vh;
      position: relative;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
      display: flex;
      flex-direction: column;
    }

    [data-theme="dark"] .modal-content {
      background: var(--bg);
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
      padding-bottom: 12px;
      border-bottom: 1px solid #e8eef7;
      flex-shrink: 0;
    }

    [data-theme="dark"] .modal-header {
      border-bottom-color: var(--line);
    }

    .modal-header h3 {
      margin: 0;
      font-size: 20px;
      color: #1a2b4a;
    }

    [data-theme="dark"] .modal-header h3 {
      color: #e0f0ff;
    }

    .modal-close {
      background: none;
      border: none;
      font-size: 28px;
      color: #666;
      cursor: pointer;
      padding: 0;
      line-height: 1;
    }

    [data-theme="dark"] .modal-close {
      color: #a0c4e8;
    }

    .modal-upper {
      display: flex;
      gap: 20px;
      flex: 1;
      min-height: 0;
    }

    .modal-heatmap-section {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      position: relative;
      background: var(--bg);
      border-radius: 8px;
      overflow: hidden;
    }

    .heatmap-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px 16px;
      background: var(--panel);
      border-bottom: 1px solid var(--line);
      z-index: 100;
    }

    .heatmap-header h4 {
      margin: 0;
      font-size: 16px;
      color: var(--blue);
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .heatmap-header h4::before {
      content: '';
      display: inline-block;
      width: 4px;
      height: 16px;
      background: var(--blue);
      border-radius: 2px;
    }

    .heatmap-legend {
      display: flex;
      gap: 12px;
      font-size: 12px;
      color: var(--muted);
    }

    .heatmap-legend .legend-dot {
      display: inline-block;
      width: 10px;
      height: 10px;
      border-radius: 50%;
      margin-right: 4px;
      vertical-align: middle;
    }

    .heatmap-legend .legend-dot.red {
      background: #ef4444;
    }

    .heatmap-legend .legend-dot.blue {
      background: #3b82f6;
    }

    .heatmap-legend .legend-dot.orange {
      background: #f97316;
    }

    @keyframes ripple {
      0% { transform: scale(0.4); opacity: 1; }
      100% { transform: scale(1.5); opacity: 0; }
    }

    .ripple-effect {
      animation: ripple 2s infinite linear;
    }

    .heatmap-labels .absolute { position: absolute; }
    .heatmap-labels .flex { display: flex; }
    .heatmap-labels .flex-col { flex-direction: column; }
    .heatmap-labels .items-center { align-items: center; }
    .heatmap-labels .justify-center { justify-content: center; }
    .heatmap-labels .select-none { user-select: none; }
    .heatmap-labels .pointer-events-auto { pointer-events: auto; }
    .heatmap-labels .cursor-pointer { cursor: pointer; }
    .heatmap-labels .transition-all { transition: all 0.2s ease; }
    .heatmap-labels .backdrop-blur-sm { backdrop-filter: blur(4px); }
    .heatmap-labels .px-4 { padding-left: 1rem; padding-right: 1rem; }
    .heatmap-labels .py-2 { padding-top: 0.5rem; padding-bottom: 0.5rem; }
    .heatmap-labels .rounded-xl { border-radius: 0.75rem; }
    .heatmap-labels .min-w-\[110px\] { min-width: 110px; }
    .heatmap-labels .mb-1 { margin-bottom: 0.25rem; }
    .heatmap-labels .hover\\:scale-105:hover { transform: scale(1.05); }
    .heatmap-labels .text-xs { font-size: 0.75rem; line-height: 1rem; }
    .heatmap-labels .text-\[13px\] { font-size: 13px; }
    .heatmap-labels .text-\[10px\] { font-size: 10px; }
    .heatmap-labels .font-black { font-weight: 900; }
    .heatmap-labels .font-bold { font-weight: 700; }
    .heatmap-labels .font-medium { font-weight: 500; }
    .heatmap-labels .tracking-wider { letter-spacing: 0.05em; }
    .heatmap-labels .leading-tight { line-height: 1.25; }
    .heatmap-labels .text-red-500 { color: #ef4444; }
    .heatmap-labels .text-orange-500 { color: #ff6a00; }
    .heatmap-labels .text-green-500 { color: #08ae66; }
    .heatmap-labels .mt-0\.5 { margin-top: 0.125rem; }

    .heatmap-canvas {
      width: 100%;
      flex: 1;
      cursor: pointer;
      min-height: 300px;
      position: relative;
    }

    .heatmap-labels {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      pointer-events: none;
      z-index: 50;
    }

    @keyframes panelScan {
      0% {
        top: 0;
        opacity: 0;
      }
      10% {
        opacity: 1;
      }
      90% {
        opacity: 1;
      }
      100% {
        top: 100%;
        opacity: 0;
      }
    }

    .panel-ai-btn .ai-orb.small {
      transition: transform 0.2s;
    }
    .panel-ai-btn:hover .ai-orb.small {
      transform: scale(1.1);
    }
    .panel-ai-btn.btn-flash .ai-orb.small {
      animation: panel-orb-pulse 2s ease-in-out infinite;
    }
    @keyframes panel-orb-pulse {
      0%, 100% { transform: scale(1); box-shadow: 0 0 0 4px rgba(32, 151, 247, 0.08), 0 0 20px rgba(18, 104, 255, 0.4); }
      50% { transform: scale(1.15); box-shadow: 0 0 0 6px rgba(32, 151, 247, 0.15), 0 0 28px rgba(18, 104, 255, 0.6); }
    }
    [data-theme="dark"] .panel-ai-btn.btn-flash .ai-orb.small {
      animation: panel-orb-pulse-dark 2s ease-in-out infinite;
    }
    @keyframes panel-orb-pulse-dark {
      0%, 100% { transform: scale(1); box-shadow: 0 0 0 4px rgba(0, 180, 255, 0.15), 0 0 25px rgba(0, 212, 255, 0.5); }
      50% { transform: scale(1.15); box-shadow: 0 0 0 6px rgba(0, 180, 255, 0.25), 0 0 35px rgba(0, 212, 255, 0.7); }
    }

    .modal-zone-section {
      width: 420px;
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
      gap: 12px;
      overflow-y: auto;
      padding-right: 4px;
    }

    .modal-zone-section::-webkit-scrollbar {
      width: 4px;
    }

    .modal-zone-section::-webkit-scrollbar-thumb {
      background: var(--line);
      border-radius: 2px;
    }

    /* 选中区域详情卡片 */
    .zone-detail-card {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 14px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      animation: zone-card-in 0.25s ease;
      box-shadow: 0 4px 20px rgba(0,0,0,0.06);
    }
    @keyframes zone-card-in {
      from { opacity: 0; transform: translateY(-8px); }
      to { opacity: 1; transform: translateY(0); }
    }
    .zone-detail-card-header {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .zone-detail-title-row {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .zone-detail-name {
      font-size: 15px;
      font-weight: 800;
      color: var(--text);
      letter-spacing: 0.5px;
    }
    .zone-detail-close {
      margin-left: auto;
      background: none;
      border: none;
      color: var(--muted);
      cursor: pointer;
      padding: 4px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      transition: background 0.15s;
    }
    .zone-detail-close:hover {
      background: rgba(0,0,0,0.06);
      color: var(--text);
    }
    .zone-detail-summary {
      display: flex;
      gap: 12px;
      font-size: 11px;
      color: var(--muted);
      flex-wrap: wrap;
    }
    .zone-detail-summary b {
      font-weight: 700;
    }

    /* AI 异常分析区 */
    .zone-analysis-box {
      background: var(--bg);
      border: 1px solid var(--line);
      border-radius: 10px;
      padding: 10px 12px;
    }
    .zone-analysis-header {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 8px;
    }
    .zone-analysis-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 22px;
      height: 22px;
      border-radius: 6px;
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      color: #fff;
      font-size: 10px;
      font-weight: 800;
      flex-shrink: 0;
    }
    .zone-analysis-label {
      font-size: 12px;
      font-weight: 700;
      color: var(--text);
    }
    .zone-analysis-content {
      font-size: 12px;
      line-height: 1.7;
      color: var(--muted);
    }
    .zone-analysis-text {
      white-space: pre-wrap;
    }
    .zone-typing-cursor {
      animation: blink-cursor 0.7s step-end infinite;
      color: var(--text);
      font-weight: 300;
    }
    @keyframes blink-cursor {
      50% { opacity: 0; }
    }

    /* 异常项操作卡片 */
    .anomaly-action-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .anomaly-action-card {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 12px;
      background: var(--bg);
      border-radius: 10px;
      border-left: 3px solid #ef4444;
      box-shadow: 0 1px 4px rgba(0,0,0,0.04);
      opacity: 0;
      transform: translateY(12px);
      transition: opacity 0.4s ease, transform 0.4s ease;
    }
    .anomaly-action-card.action-card-visible {
      opacity: 1;
      transform: translateY(0);
    }
    .anomaly-action-card.action-type-env {
      border-left-color: #f59e0b;
    }
    .anomaly-action-card.action-type-device {
      border-left-color: #ef4444;
    }
    .anomaly-action-body {
      flex: 1;
      min-width: 0;
    }
    .anomaly-action-title {
      font-size: 12px;
      font-weight: 700;
      color: var(--text);
      margin-bottom: 2px;
    }
    .anomaly-action-detail {
      font-size: 11px;
      color: var(--muted);
    }
    .anomaly-action-btns {
      display: flex;
      gap: 8px;
      flex-shrink: 0;
    }
    .anomaly-action-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      padding: 8px 14px;
      border: none;
      border-radius: 8px;
      font-size: 12px;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.2s;
      white-space: nowrap;
    }
    .anomaly-action-btn svg {
      width: 14px;
      height: 14px;
    }
    .anomaly-action-btn.btn-repair {
      background: linear-gradient(135deg, #1268ff, #0099ff);
      color: #fff;
    }
    .anomaly-action-btn.btn-repair:hover {
      background: linear-gradient(135deg, #0055dd, #0077cc);
      transform: translateY(-1px);
      box-shadow: 0 3px 10px rgba(18, 104, 255, 0.25);
    }
    .anomaly-action-btn.btn-check {
      background: linear-gradient(135deg, #8b5cf6, #a78bfa);
      color: #fff;
    }
    .anomaly-action-btn.btn-check:hover {
      background: linear-gradient(135deg, #7c3aed, #8b5cf6);
      transform: translateY(-1px);
      box-shadow: 0 3px 10px rgba(139, 92, 246, 0.25);
    }
    .anomaly-action-btn.btn-fan {
      background: linear-gradient(135deg, #08ae66, #00d4aa);
      color: #fff;
    }
    .anomaly-action-btn.btn-fan:hover {
      background: linear-gradient(135deg, #069955, #00bb99);
      transform: translateY(-1px);
      box-shadow: 0 3px 10px rgba(8, 174, 102, 0.25);
    }
    .anomaly-action-btn.btn-inspect {
      background: linear-gradient(135deg, #ff6a00, #ff9933);
      color: #fff;
    }
    .anomaly-action-btn.btn-inspect:hover {
      background: linear-gradient(135deg, #dd5500, #cc7700);
      transform: translateY(-1px);
      box-shadow: 0 3px 10px rgba(255, 106, 0, 0.25);
    }

    /* 正常区域提示 */
    .zone-normal-badge {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      padding: 14px;
      border-radius: 10px;
      background: rgba(8,174,102,0.06);
      border: 1px solid rgba(8,174,102,0.15);
      color: #08ae66;
      font-size: 13px;
      font-weight: 600;
    }

    /* 选中高亮 */
    .zone-card-selected {
      outline: 2px solid var(--blue);
      outline-offset: -1px;
    }

    .modal-lower {
      display: flex;
      gap: 20px;
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid var(--line);
      flex-shrink: 0;
    }

    .ai-analysis-section {
      flex: 1;
      min-width: 0;
    }

    .ai-analysis-header {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 10px;
    }

    .ai-analysis-header h4 {
      margin: 0;
      font-size: 16px;
      color: var(--text);
    }

    .ai-orb.small {
      width: 32px;
      height: 32px;
      min-width: 32px;
      min-height: 32px;
      font-size: 12px;
    }

    .analyze-btn.btn-flash .ai-orb.small {
      animation: orb-pulse 2s ease-in-out infinite;
    }

    @keyframes orb-pulse {
      0%, 100% { transform: scale(1); box-shadow: 0 0 0 4px rgba(32, 151, 247, 0.08), 0 0 20px rgba(18, 104, 255, 0.4); }
      50% { transform: scale(1.15); box-shadow: 0 0 0 6px rgba(32, 151, 247, 0.15), 0 0 28px rgba(18, 104, 255, 0.6); }
    }

    [data-theme="dark"] .analyze-btn.btn-flash .ai-orb.small {
      animation: orb-pulse-dark 2s ease-in-out infinite;
    }

    @keyframes orb-pulse-dark {
      0%, 100% { transform: scale(1); box-shadow: 0 0 0 4px rgba(0, 180, 255, 0.15), 0 0 25px rgba(0, 212, 255, 0.5); }
      50% { transform: scale(1.15); box-shadow: 0 0 0 6px rgba(0, 180, 255, 0.25), 0 0 35px rgba(0, 212, 255, 0.7); }
    }

    .ai-analysis-content {
      font-size: 13px;
      line-height: 1.8;
      color: var(--muted);
      max-height: 200px;
      overflow-y: auto;
      padding: 12px;
      background: var(--soft);
      border-radius: 8px;
      min-height: 80px;
    }

    .typewriter-text {
      white-space: pre-wrap;
    }

    .typing-cursor {
      animation: blink 0.8s infinite;
      color: var(--blue);
      font-weight: 700;
    }

    @keyframes blink {
      0%, 50% { opacity: 1; }
      51%, 100% { opacity: 0; }
    }

    .action-buttons {
      display: flex;
      flex-direction: column;
      gap: 12px;
      width: 140px;
      flex-shrink: 0;
      justify-content: center;
    }

    .action-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 14px 16px;
      border: none;
      border-radius: 10px;
      font-size: 15px;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.2s;
      position: relative;
    }

    .action-btn svg {
      width: 20px;
      height: 20px;
    }

    .dispatch-btn {
      background: linear-gradient(135deg, #1268ff, #0099ff);
      color: #fff;
    }

    .dispatch-btn:hover {
      background: linear-gradient(135deg, #0055dd, #0077cc);
      transform: translateY(-2px);
      box-shadow: 0 4px 16px rgba(18, 104, 255, 0.3);
    }

    .repair-btn {
      background: linear-gradient(135deg, #ff6a00, #ff9933);
      color: #fff;
    }

    .repair-btn:hover {
      background: linear-gradient(135deg, #dd5500, #cc7700);
      transform: translateY(-2px);
      box-shadow: 0 4px 16px rgba(255, 106, 0, 0.3);
    }

    .action-btn .badge {
      position: absolute;
      top: -6px;
      right: -6px;
      min-width: 20px;
      height: 20px;
      padding: 0 6px;
      border-radius: 10px;
      background: #ff1515;
      color: #fff;
      font-size: 11px;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
      animation: badge-pulse 0.3s ease;
    }

    @keyframes badge-pulse {
      0% { transform: scale(0.8); }
      50% { transform: scale(1.2); }
      100% { transform: scale(1); }
    }

    .modal-zone-card {
      border-radius: 10px;
      padding: 14px 16px;
      border: 1px solid var(--line);
      background: #f6faff;
      transition: border-color 0.2s, box-shadow 0.2s;
    }

    [data-theme="dark"] .modal-zone-card {
      background: rgba(13, 33, 55, 0.6);
    }

    .modal-zone-card:hover {
      border-color: var(--blue);
      box-shadow: 0 2px 12px rgba(18, 104, 255, 0.1);
    }

    .modal-zone-card.green-box {
      border-left: 3px solid var(--green);
    }

    .modal-zone-card.orange-box {
      border-left: 3px solid var(--orange);
    }

    .modal-zone-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 15px;
      font-weight: 700;
      margin-bottom: 10px;
      padding-bottom: 8px;
      border-bottom: 1px dashed var(--line);
    }

    .header-right-group {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-left: auto;
    }
    .analyze-btn {
      cursor: pointer;
      transition: all 0.2s;
      margin-left: 12px;
    }
    .analyze-btn.btn-flash {
      animation: analyze-pulse 2s ease-in-out infinite;
    }
    .analyze-btn:hover {
      animation: none;
    }
    @keyframes analyze-pulse {
      0%, 100% { transform: scale(1); opacity: 1; box-shadow: 0 0 0 0 rgba(18, 104, 255, 0.4); }
      50% { transform: scale(1.15); opacity: 0.8; box-shadow: 0 0 14px 5px rgba(18, 104, 255, 0.35); }
    }

    .modal-zone-header.green-text {
      color: var(--green);
    }

    .modal-zone-header.orange-text {
      color: var(--orange);
    }

    .modal-zone-header.blue-text {
      color: var(--blue);
    }

    .modal-zone-env {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 6px 10px;
      margin-bottom: 10px;
    }

    .modal-zone-env .env-item {
      font-size: 12px;
      color: var(--muted);
    }

    .modal-zone-env .env-item b {
      color: var(--text);
      font-weight: 600;
    }

    .modal-zone-env .env-item.env-abnormal {
      color: var(--orange);
    }

    .modal-zone-env .env-item.env-abnormal b {
      color: var(--orange);
    }

    .modal-zone-status {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .modal-zone-status .status-item {
      font-size: 12px;
      color: var(--muted);
    }

    .modal-zone-status .status-item b {
      color: var(--text);
      font-weight: 600;
    }

    .modal-zone-status .suggestion-text {
      font-size: 12px;
      color: var(--muted);
      line-height: 1.5;
      margin-top: 4px;
    }

    .modal-zone-status .suggestion-text b {
      font-weight: 500;
    }

    .status-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      cursor: pointer;
      padding: 2px 0;
      transition: background 0.15s;
      border-radius: 4px;
    }

    .status-row:hover {
      background: rgba(18, 104, 255, 0.04);
    }

    .expand-btn {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      background: none;
      border: 1px solid var(--line);
      border-radius: 4px;
      padding: 2px 8px;
      font-size: 11px;
      color: var(--muted);
      cursor: pointer;
      transition: all 0.2s;
      flex-shrink: 0;
    }

    .expand-btn:hover {
      border-color: var(--blue);
      color: var(--blue);
      background: rgba(18, 104, 255, 0.06);
    }

    .expand-btn.expanded {
      border-color: var(--blue);
      color: var(--blue);
    }

    .expand-icon {
      width: 12px;
      height: 12px;
      transition: transform 0.2s;
    }

    .expand-btn.expanded .expand-icon {
      transform: rotate(180deg);
    }

    .anomaly-list {
      margin: 6px 0 8px 8px;
      padding: 8px 10px;
      background: rgba(255, 106, 0, 0.06);
      border-radius: 6px;
      border-left: 2px solid var(--orange);
    }

    .anomaly-item {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 4px 0;
      font-size: 12px;
    }

    .anomaly-item + .anomaly-item {
      border-top: 1px dashed var(--line);
    }

    .hidden-check {
      display: none;
    }

    .anomaly-check,
    .device-check {
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      flex-shrink: 0;
    }

    .anomaly-label {
      color: var(--text);
      font-weight: 600;
    }

    .anomaly-value {
      color: var(--orange);
      font-weight: 600;
    }

    .anomaly-standard {
      color: var(--muted);
      font-size: 11px;
    }

    .anomaly-status-tag {
      margin-left: auto;
      padding: 1px 8px;
      border-radius: 10px;
      font-size: 11px;
      font-weight: 600;
      color: var(--orange);
      background: rgba(255, 106, 0, 0.12);
    }

    .anomaly-device-list {
      margin: 6px 0 8px 8px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .anomaly-device-item {
      padding: 10px 12px;
      background: rgba(255, 255, 255, 0.6);
      border-radius: 8px;
      border: 1px solid var(--line);
      transition: border-color 0.2s;
    }

    [data-theme="dark"] .anomaly-device-item {
      background: rgba(13, 33, 55, 0.5);
    }

    .anomaly-device-item:hover {
      border-color: var(--blue);
    }

    .anomaly-device-item.blinking {
      animation: device-blink 1.5s ease-in-out infinite;
      border-left: 3px solid #ff4444;
    }

    @keyframes device-blink {
      0%, 100% {
        background: rgba(255, 68, 68, 0.04);
        box-shadow: 0 0 0 0 rgba(255, 68, 68, 0);
      }
      50% {
        background: rgba(255, 68, 68, 0.1);
        box-shadow: 0 0 12px 2px rgba(255, 68, 68, 0.15);
      }
    }

    [data-theme="dark"] .anomaly-device-item.blinking {
      animation: device-blink-dark 1.5s ease-in-out infinite;
    }

    @keyframes device-blink-dark {
      0%, 100% {
        background: rgba(255, 68, 68, 0.06);
        box-shadow: 0 0 0 0 rgba(255, 68, 68, 0);
      }
      50% {
        background: rgba(255, 68, 68, 0.15);
        box-shadow: 0 0 16px 3px rgba(255, 68, 68, 0.2);
      }
    }

    .device-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 6px;
    }

    .device-name {
      font-size: 13px;
      font-weight: 700;
      color: var(--text);
    }

    .device-type {
      font-size: 11px;
      color: var(--muted);
      padding: 1px 6px;
      background: var(--soft);
      border-radius: 3px;
    }

    .device-status-tag {
      margin-left: auto;
      padding: 2px 10px;
      border-radius: 10px;
      font-size: 11px;
      font-weight: 600;
    }

    .device-status-tag.tag-danger {
      color: #ef4444;
      background: rgba(239, 68, 68, 0.1);
    }

    .device-status-tag.tag-warning {
      color: #f97316;
      background: rgba(249, 115, 22, 0.1);
    }

    .device-info-row {
      display: flex;
      gap: 12px;
      font-size: 11px;
      color: var(--muted);
      margin-bottom: 6px;
    }

    .device-info-label {
      color: var(--text);
      font-weight: 500;
    }

    .device-data-grid {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
    }

    .device-data-item {
      font-size: 11px;
      color: var(--muted);
      padding: 3px 8px;
      background: var(--soft);
      border-radius: 4px;
    }

    .device-data-item b {
      color: var(--text);
      font-weight: 600;
      margin-left: 4px;
    }

    /* ===== 运维Agent弹窗 ===== */
    .ops-modal-overlay {
      position: fixed;
      inset: 0;
      background: #0a1628;
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 2000;
      animation: fadeIn 0.3s ease;
    }

    .ops-modal-content {
      background: #0a1628;
      border: none;
      border-radius: 0;
      width: 100%;
      height: 100vh;
      display: flex;
      flex-direction: column;
      box-shadow: none;
      animation: fadeIn 0.3s ease;
      overflow: hidden;
      position: relative;
    }

    .ops-modal-close {
      position: fixed;
      top: 20px;
      right: 20px;
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background: rgba(255,68,68,0.2);
      border: 1px solid rgba(255,68,68,0.4);
      color: #ff4444;
      font-size: 24px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
      z-index: 2001;
      backdrop-filter: blur(10px);
    }

    .ops-modal-close:hover {
      background: rgba(255,68,68,0.35);
      border-color: rgba(255,68,68,0.6);
      transform: rotate(90deg) scale(1.1);
    }

    .ops-modal-iframe {
      width: 100%;
      height: 100%;
      border: none;
    }

    @keyframes scaleIn {
      from {
        opacity: 0;
        transform: scale(0.95);
      }
      to {
        opacity: 1;
        transform: scale(1);
      }
    }

    /* ===== 知识库统计弹窗 ===== */
    .knowledge-modal-overlay {
      position: fixed;
      inset: 0;
      background: #0a1628;
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 2000;
      animation: fadeIn 0.3s ease;
    }

    .knowledge-modal-content {
      background: #0a1628;
      border: none;
      border-radius: 0;
      width: 100%;
      height: 100vh;
      display: flex;
      flex-direction: column;
      box-shadow: none;
      animation: fadeIn 0.3s ease;
      overflow: hidden;
      position: relative;
    }

    .knowledge-modal-close {
      position: fixed;
      top: 20px;
      right: 20px;
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background: rgba(255,68,68,0.2);
      border: 1px solid rgba(255,68,68,0.4);
      color: #ff4444;
      font-size: 24px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
      z-index: 2001;
      backdrop-filter: blur(10px);
    }

    .knowledge-modal-close:hover {
      background: rgba(255,68,68,0.35);
      border-color: rgba(255,68,68,0.6);
      transform: rotate(90deg) scale(1.1);
    }

    .knowledge-modal-iframe {
      width: 100%;
      height: 100%;
      border: none;
    }

    /* ===== 智慧助手弹窗 ===== */
    .assistant-modal-overlay {
      position: fixed;
      inset: 0;
      background: #0a1628;
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 2000;
      animation: fadeIn 0.3s ease;
    }

    .assistant-modal-content {
      background: #0a1628;
      border: none;
      border-radius: 0;
      width: 100%;
      height: 100vh;
      display: flex;
      flex-direction: column;
      box-shadow: none;
      animation: fadeIn 0.3s ease;
      overflow: hidden;
      position: relative;
    }

    .assistant-modal-close {
      position: fixed;
      top: 20px;
      right: 20px;
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background: rgba(255,68,68,0.2);
      border: 1px solid rgba(255,68,68,0.4);
      color: #ff4444;
      font-size: 24px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
      z-index: 2001;
      backdrop-filter: blur(10px);
    }

    .assistant-modal-close:hover {
      background: rgba(255,68,68,0.35);
      border-color: rgba(255,68,68,0.6);
      transform: rotate(90deg) scale(1.1);
    }

    .assistant-modal-iframe {
      width: 100%;
      height: 100%;
      border: none;
    }

    /* ===== 站房整体状态评估弹窗 ===== */
    .health-modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(7, 21, 59, 0.35);
      backdrop-filter: blur(6px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      animation: fadeIn 0.2s ease;
    }

    .health-modal {
      background: white;
      border-radius: 16px;
      width: 96%;
      max-width: 1100px;
      max-height: 95vh;
      display: flex;
      flex-direction: column;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
      animation: slideUp 0.3s ease;
    }

    .health-modal-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 18px 24px;
      border-bottom: 1px solid var(--line);
      font-size: 17px;
      font-weight: 700;
      color: var(--text);
    }

    .header-title {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .health-modal-header svg {
      color: #1268ff;
    }

    .modal-close-btn {
      background: none;
      border: none;
      color: var(--muted);
      cursor: pointer;
      padding: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 6px;
      transition: all 0.2s;
    }

    .modal-close-btn:hover {
      background: var(--soft);
      color: var(--text);
    }

    .health-trend-section {
    }

    .health-time-select {
      display: flex;
      justify-content: flex-end;
      margin-bottom: 10px;
    }

    .time-dropdown {
      background: transparent;
      border: 1px solid var(--line);
      border-radius: 6px;
      color: var(--text);
      font-size: 13px;
      font-weight: 600;
      padding: 4px 24px 4px 10px;
      cursor: pointer;
      appearance: none;
      background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%23526383' stroke-width='2'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
      background-repeat: no-repeat;
      background-position: right 6px center;
      outline: none;
      transition: all 0.2s;
    }

    .time-dropdown:hover {
      color: #1268ff;
      border-color: #1268ff;
    }

    .time-dropdown option {
      background: var(--panel, #fbfdff);
      color: var(--text);
      padding: 8px;
    }

    [data-theme="dark"] .time-dropdown {
      background-color: var(--bg);
      border-color: #45475a;
      background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%23cdd6f4' stroke-width='2'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
    }

    [data-theme="dark"] .time-dropdown:hover {
      border-color: #89b4fa;
      color: #89b4fa;
    }

    [data-theme="dark"] .time-dropdown option {
      background: var(--bg);
      color: #cdd6f4;
    }

    .health-modal-body {
      flex: 1;
      overflow-y: auto;
      padding: 0 24px;
    }

    .health-score-section {
      display: flex;
      align-items: flex-start;
      gap: 82px;
      margin-bottom: 20px;
    }

    .health-score-left {
      flex: 0 0 auto;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      margin-left: 150px;
    }

    .health-analysis-conclusion-right {
      flex: 1;
      min-width: 0;
      margin-left: 100px;
      margin-top: 60px;
      gap: 12px;
    }

    .conclusion-title-small {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      font-weight: 700;
      color: var(--text);
      margin: 0 0 10px 0;
    }

    .conclusion-text-small {
      font-size: 13px;
      line-height: 1.7;
      color: var(--text);
      margin: 0;
      padding: 12px;
      background: var(--panel);
      border-radius: 8px;
      border: 1px solid var(--line);
    }

    .health-score-chart {
      width: 180px;
      height: 180px;
    }

    .health-analysis-text {
      flex: 1;
    }

    .analysis-desc {
      font-size: 15px;
      color: var(--text);
      line-height: 1.6;
      margin: 0;
    }

    .health-trend-chart {
      width: 100%;
      height: 200px;
      border: 1px solid var(--line);
      border-radius: 10px;
      padding: 8px;
      background: var(--panel);
    }

    .health-radar-section {
      display: flex;
    }

    .health-radar-chart {
      width: 50%;
      height: 300px;
      border: none;
      border-radius: 10px;
      /* padding: 8px; */
      background: transparent;
    }

    .radar-data-cards {
      flex: 1;
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 8px;
      align-content: start;
      margin-top: 50px;
    }

    .radar-data-card {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 8px;
      padding: 10px 12px;
      cursor: pointer;
      transition: all 0.2s;
    }

    .radar-data-card:hover {
      border-color: #1268ff;
      box-shadow: 0 2px 8px rgba(18, 104, 255, 0.1);
      transform: translateX(2px);
    }

    .radar-card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 6px;
    }

    .radar-card-name {
      font-size: 13px;
      font-weight: 600;
      color: var(--text);
    }

    .radar-card-score {
      font-size: 12px;
      font-weight: 700;
      padding: 2px 8px;
      border-radius: 4px;
    }

    .radar-card-score.green-text {
      background: rgba(12, 175, 100, 0.1);
      color: #0caf64;
    }

    .radar-card-score.orange-text {
      background: rgba(255, 106, 0, 0.1);
      color: #ff6a00;
    }

    .radar-card-meta {
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 11px;
      color: var(--muted);
    }

    .radar-card-status {
      font-weight: 600;
    }

    .radar-card-status.green-text {
      color: #0caf64;
    }

    .radar-card-status.orange-text {
      color: #ff6a00;
    }

    .radar-card-anomalies {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 2px 6px;
      background: rgba(255, 106, 0, 0.08);
      border-radius: 4px;
      font-size: 10px;
      color: #ff6a00;
      font-weight: 600;
    }

    .radar-card-anomalies svg {
      color: #ff6a00;
    }

    .radar-card-normal {
      font-size: 10px;
      color: #0caf64;
      font-weight: 600;
    }

    .health-conclusion-section {
      margin-bottom: 16px;
    }

    .conclusion-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      font-weight: 700;
      color: var(--text);
      margin: 0 0 10px 0;
    }

    .conclusion-text {
      font-size: 13px;
      line-height: 1.7;
      color: var(--text);
      margin: 0;
      padding: 12px;
      background: var(--bg);
      border-radius: 8px;
      border: 1px solid var(--line);
    }

    .health-suggestion-section {
      margin-bottom: 0;
    }

    .suggestion-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      font-weight: 700;
      color: var(--text);
      margin: 0 0 10px 0;
    }

    .suggestion-title svg {
      color: #1268ff;
    }

    .suggestion-text {
      font-size: 13px;
      line-height: 1.7;
      color: var(--text);
      margin: 0;
      padding: 12px;
      background: var(--bg);
      border-radius: 8px;
      border: 1px solid var(--line);
      white-space: pre-wrap;
      font-family: inherit;
    }

    /* ===== 分项详情弹窗 ===== */
    .sub-item-overlay {
      position: fixed;
      inset: 0;
      background: rgba(7, 21, 59, 0.35);
      backdrop-filter: blur(6px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1001;
      animation: fadeIn 0.2s ease;
    }

    .sub-item-modal {
      background: var(--card);
      border-radius: 16px;
      width: 90%;
      max-width: 600px;
      max-height: 80vh;
      display: flex;
      flex-direction: column;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
      animation: slideUp 0.3s ease;
    }

    .sub-item-header {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 18px 24px;
      border-bottom: 1px solid var(--line);
      font-size: 16px;
      font-weight: 700;
      color: var(--text);
    }

    .sub-item-header svg:first-child {
      color: #1268ff;
    }

    .sub-item-close {
      margin-left: auto;
      background: none;
      border: none;
      color: var(--muted);
      cursor: pointer;
      padding: 4px;
      display: flex;
      transition: color 0.2s;
    }

    .sub-item-close:hover {
      color: var(--text);
    }

    .sub-item-body {
      flex: 1;
      overflow-y: auto;
      padding: 20px 24px;
    }

    .sub-item-overview {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 14px 16px;
      background: var(--bg);
      border-radius: 10px;
      margin-bottom: 20px;
    }

    .overview-score {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .score-label {
      font-size: 13px;
      color: var(--muted);
    }

    .score-num {
      font-size: 22px;
      font-weight: 800;
    }

    .overview-meta {
      display: flex;
      gap: 16px;
      font-size: 13px;
      color: var(--muted);
    }

    .deduction-devices {
      margin-bottom: 0;
    }

    .deduction-title {
      font-size: 14px;
      font-weight: 700;
      color: var(--text);
      margin: 0 0 12px 0;
    }

    .deduction-device-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 14px 16px;
      background: var(--bg);
      border: 1px solid var(--line);
      border-radius: 10px;
      margin-bottom: 10px;
    }

    .device-info {
      flex: 1;
    }

    .device-name {
      font-size: 14px;
      font-weight: 600;
      color: var(--text);
      margin-bottom: 4px;
    }

    .device-id {
      font-size: 12px;
      color: var(--muted);
    }

    .device-realtime {
      flex: 1.5;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .realtime-label {
      font-size: 11px;
      color: var(--muted);
    }

    .realtime-value {
      font-size: 13px;
      font-weight: 600;
      color: #ff6a00;
    }

    .device-deduction {
      flex-shrink: 0;
    }

    .deduction-badge {
      padding: 6px 12px;
      background: rgba(255, 106, 0, 0.1);
      color: #ff6a00;
      border-radius: 6px;
      font-size: 14px;
      font-weight: 700;
    }

    .no-deduction {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 40px 0;
      color: var(--muted);
    }

    .no-deduction svg {
      color: #0caf64;
      margin-bottom: 12px;
    }

    .no-deduction p {
      margin: 0;
      font-size: 14px;
    }

    .sub-item-footer {
      padding: 14px 24px;
      border-top: 1px solid var(--line);
      display: flex;
      justify-content: flex-end;
    }

    .sub-item-close-btn {
      padding: 8px 24px;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: transparent;
      color: var(--text);
      font-size: 14px;
      cursor: pointer;
      transition: all 0.2s;
    }

    .sub-item-close-btn:hover {
      background: #1268ff;
      color: #fff;
      border-color: #1268ff;
    }

    /* 深色主题适配 */
    [data-theme="dark"] .health-modal,
    [data-theme="dark"] .sub-item-modal {
      background: var(--bg);
    }

    [data-theme="dark"] .health-trend-chart,
    [data-theme="dark"] .conclusion-text,
    [data-theme="dark"] .suggestion-text,
    [data-theme="dark"] .health-sub-item,
    [data-theme="dark"] .sub-item-overview,
    [data-theme="dark"] .deduction-device-card {
      /* background: var(--panel); */
    }

    /* ===== 设备分析弹窗 ===== */
    .device-analysis-overlay {
      position: fixed;
      inset: 0;
      background: rgba(7, 21, 59, 0.35);
      backdrop-filter: blur(6px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      animation: fadeIn 0.2s ease;
    }

    .device-analysis-modal {
      background: white;
      border-radius: 16px;
      width: 96%;
      max-width: 1200px;
      max-height: 95vh;
      display: flex;
      flex-direction: column;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
      animation: slideUp 0.3s ease;
    }

    .device-analysis-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 18px 24px;
      border-bottom: 1px solid var(--line);
      font-size: 17px;
      font-weight: 700;
      color: var(--text);
    }

    .device-analysis-body {
      flex: 1;
      overflow-y: auto;
      padding: 20px 24px;
    }

    .device-kpi-row {
      display: grid;
      grid-template-columns: repeat(6, 1fr);
      gap: 12px;
      margin-bottom: 20px;
    }

    .device-kpi-card {
      background: var(--bg);
      border: 1px solid var(--line);
      border-radius: 10px;
      padding: 14px;
      text-align: center;
    }

    .kpi-label {
      font-size: 12px;
      color: var(--muted);
      margin-bottom: 6px;
    }

    .kpi-value {
      font-size: 24px;
      font-weight: 800;
      color: var(--text);
    }

    .blue-text { color: #1268ff !important; }
    .green-text { color: #0caf64 !important; }
    .red-text { color: #ff1515 !important; }
    .orange-text { color: #ff6a00 !important; }
    .purple-text { color: #5b39ff !important; }

    .section-title {
      font-size: 15px;
      font-weight: 700;
      color: var(--text);
      margin: 0 0 12px 0;
    }

    .device-anomaly-section {
      margin-bottom: 20px;
    }

    .anomaly-layout {
      display: flex;
      gap: 20px;
    }

    .anomaly-analysis-panel {
      flex: 0 0 320px;
    }

    .device-ai-section {
      background: linear-gradient(135deg, rgba(18, 104, 255, 0.05), rgba(139, 92, 246, 0.05));
      border: 1px solid rgba(18, 104, 255, 0.15);
      border-radius: 12px;
      padding: 14px 16px;
    }

    .anomaly-device-panel {
      flex: 1;
      min-width: 0;
    }

    .anomaly-device-panel .section-title {
      margin-bottom: 12px;
    }

    .maintenance-panel {
      flex: 0 0 480px;
      min-width: 0;
    }

    .maintenance-panel .section-title {
      margin-bottom: 12px;
    }

    .maintenance-progress-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .anomaly-action-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .anomaly-action-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 14px;
      background: var(--bg);
      border: 1px solid var(--line);
      border-radius: 10px;
      transition: all 0.2s;
    }

    .fault-item { border-left: 3px solid #ff1515; }
    .alarm-item { border-left: 3px solid #ff6a00; }

    .anomaly-info { flex: 1; }

    .anomaly-title {
      font-size: 14px;
      font-weight: 600;
      color: var(--text);
      margin-bottom: 4px;
    }

    .anomaly-meta {
      display: flex;
      gap: 12px;
      font-size: 12px;
      color: var(--muted);
      margin-bottom: 4px;
    }

    .anomaly-status {
      font-size: 12px;
      color: #ff6a00;
      font-weight: 600;
    }

    .anomaly-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      border: none;
      border-radius: 6px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }

    .btn-repair {
      background: linear-gradient(135deg, #1268ff, #69b1ff);
      color: #fff;
    }

    .btn-repair:hover {
      box-shadow: 0 2px 8px rgba(18, 104, 255, 0.4);
      transform: translateY(-1px);
    }

    .btn-check {
      background: linear-gradient(135deg, #8b5cf6, #a78bfa);
      color: #fff;
    }

    .btn-check:hover {
      box-shadow: 0 2px 8px rgba(139, 92, 246, 0.4);
      transform: translateY(-1px);
    }

    .device-maintenance-section {
      margin-bottom: 20px;
    }

    .maintenance-progress-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .maintenance-task {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 12px 14px;
      background: var(--bg);
      border: 1px solid var(--line);
      border-radius: 10px;
    }

    .task-info {
      display: flex;
      gap: 8px;
      font-size: 13px;
      flex: 1.5;
    }

    .task-id { color: var(--muted); }
    .task-device { color: var(--text); font-weight: 600; }
    .task-worker { color: var(--muted); }

    .task-progress {
      display: flex;
      align-items: center;
      gap: 8px;
      flex: 1;
    }

    .progress-bar {
      flex: 1;
      height: 8px;
      background: #e5e7eb;
      border-radius: 4px;
      overflow: hidden;
    }

    .progress-fill {
      height: 100%;
      background: linear-gradient(90deg, #1268ff, #69b1ff);
      border-radius: 4px;
      transition: width 0.5s ease;
    }

    .progress-fill.completed {
      background: linear-gradient(90deg, #0caf64, #34d399);
    }

    .progress-text {
      font-size: 12px;
      font-weight: 700;
      color: var(--text);
      min-width: 40px;
      text-align: right;
    }

    .task-status {
      font-size: 12px;
      font-weight: 600;
      min-width: 60px;
      text-align: right;
    }

    .completed-text { color: #0caf64; }
    .progressing-text { color: #1268ff; }

    .device-heatmap-section {
      margin-bottom: 0;
    }

    .device-heatmap-chart {
      width: 100%;
      height: 320px;
      border: 1px solid var(--line);
      border-radius: 10px;
      padding: 8px;
      background: var(--bg);
    }

    [data-theme="dark"] .device-analysis-modal {
      background: var(--bg);
    }

    [data-theme="dark"] .device-kpi-card,
    [data-theme="dark"] .anomaly-action-item,
    [data-theme="dark"] .maintenance-task,
    [data-theme="dark"] .device-heatmap-chart {
      background: var(--panel);
    }

    /* ===== 底座实时监控弹窗 ===== */
    .base-monitor-overlay {
      position: fixed;
      inset: 0;
      background: rgba(7, 21, 59, 0.85);
      backdrop-filter: blur(8px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      animation: fadeIn 0.2s ease;
    }

    .base-monitor-modal {
      background: #0f172a;
      border-radius: 16px;
      width: 95%;
      max-width: 1400px;
      height: 90vh;
      display: flex;
      flex-direction: column;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
      animation: slideUp 0.3s ease;
      position: relative;
      overflow: hidden;
    }

    .base-monitor-close {
      position: absolute;
      top: 12px;
      right: 12px;
      background: rgba(255, 255, 255, 0.1);
      border: none;
      color: white;
      cursor: pointer;
      padding: 8px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 10;
      transition: background 0.2s;
    }

    .base-monitor-close:hover {
      background: rgba(255, 255, 255, 0.2);
    }

    .base-monitor-close svg {
      width: 20px;
      height: 20px;
    }

    .base-monitor-iframe {
      width: 100%;
      height: 100%;
      border: none;
      border-radius: 16px;
    }

</style>
