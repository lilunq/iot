<script setup>
// ============================================================
// Dashboard1：无后端环境演示副本（访问路径 /dashboard1）
// 与 Dashboard.vue 页面结构完全一致，所有接口数据由
// src/utils/mockDashboardAdapter.js 提供本地假数据。
// 其中 Agent 协作任务为 dispatch_task 表两条真实记录的模拟数据，
// 中部分区态势固定为：候车层 / 出发层 / 到达层 / 出发层。
// ============================================================
import { computed, nextTick, onBeforeUnmount, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { iotRequest, aiRequest, posRequest, keliuRequest, ragflowRequest, emsRequest } from '@/utils/request'
import { listDeviceEventPage, listDeviceStatePage } from '@/service/deviceApi'
import { useAreaStore } from '@/stores/area'
import { useUserStore } from '@/stores/user'
import { installDashboardMock } from '@/utils/mockDashboardAdapter'
import gsap from 'gsap'
import Toast from '@/components/Toast.vue'
import TaskDetailModal from '@/components/TaskDetailModal.vue'
import { buildZoneCardSnapshot } from '@/utils/zoneSituation'
import { useZoneCyberIslands } from '@/composables/useZoneCyberIslands'
import html2canvas from 'html2canvas'
import jsPDF from 'jspdf'
import HealthModal from '@/components/HealthModal.vue'
import DispatchModal from '@/components/DispatchModal.vue'
import DeviceAnalysisModal from '@/components/DeviceAnalysisModal.vue'
import WarningModal from '@/components/WarningModal.vue'
import AgentCollabModal from '@/components/AgentCollabModal.vue'
import ServiceAnalysisModal from '@/components/ServiceAnalysisModal.vue'
import MapModal from '@/components/MapModal.vue'
import LifeExpectancyModal from '@/components/LifeExpectancyModal.vue'
import EnvAnalysisModal from '@/components/EnvAnalysisModal.vue'
import ComputingPowerModal from '@/components/ComputingPowerModal.vue'

const toastConfig = ref({ show: false, type: 'info', title: '', description: '' })
function showToast(type, title, description = '') {
  toastConfig.value = { show: true, type, title, description }
}

const router = useRouter()
const areaStore = useAreaStore()

// 无后端演示模式：拦截全部请求返回假数据，卸载页面时恢复
const uninstallDashboardMock = installDashboardMock()
const userStore = useUserStore()

const isAssistantOpen = ref(false)
const assistantInput = ref('')
const assistantMessagesEl = ref(null)
const assistantMessages = ref([
  { role: 'bot', text: "您好！我是智慧助手，请问有什么可以帮助您的？" }
])
const assistantReplyMessage = "收到您的问题，我正在为您分析..."

const agentCollabList = ref([
  {
    from: 'supervisor',
    fromLabel: '系統',
    to: 'energy',
    toLabel: '能管',
    text: '系統Agent指派：候车厅A区温度调至24℃',
    desc: '能管Agent将执行：调节AHU-03机组送风温度，目标24℃，预计20分钟达标',
    action: '执行'
  },
  {
    from: 'supervisor',
    fromLabel: '系統',
    to: 'energy',
    toLabel: '能管',
    text: '系統Agent指派：候车厅A区温度调至24℃',
    desc: '能管Agent将执行：调节AHU-03机组送风温度，目标24℃，预计20分钟达标',
    action: '执行'
  },
  {
    from: 'supervisor',
    fromLabel: '系統',
    to: 'energy',
    toLabel: '能管',
    text: '系統Agent指派：候车厅A区温度调至24℃',
    desc: '能管Agent将执行：调节AHU-03机组送风温度，目标24℃，预计20分钟达标',
    action: '执行'
  },
  {
    from: 'supervisor',
    fromLabel: '系統',
    to: 'energy',
    toLabel: '能管',
    text: '系統Agent指派：候车厅A区温度调至24℃',
    desc: '能管Agent将执行：调节AHU-03机组送风温度，目标24℃，预计20分钟达标',
    action: '执行'
  },
  {
    from: 'supervisor',
    fromLabel: '系統',
    to: 'energy',
    toLabel: '能管',
    text: '系統Agent指派：候车厅A区温度调至24℃',
    desc: '能管Agent将执行：调节AHU-03机组送风温度，目标24℃，预计20分钟达标',
    action: '执行'
  },
  {
    from: 'energy',
    fromLabel: '能管',
    to: 'supervisor',
    toLabel: '系統',
    text: '能管Agent上报：夜间节能策略已自动切换',
    desc: '系統Agent确认：已切换至夜间模式，冷源负荷降至60%，预计节电18%',
    action: '确认'
  },
  {
    from: 'passenger',
    fromLabel: '客流',
    to: 'energy',
    toLabel: '能管',
    text: '客流Agent预警：10:00将迎来客流高峰',
    desc: '能管Agent将执行：提前启动预冷模式，冷源负荷提升至85%，保障舒适度',
    action: '预冷'
  },
  {
    from: 'ops',
    fromLabel: '运维',
    to: 'supervisor',
    toLabel: '系統',
    text: '运维Agent上报：冷却塔风机振动异常需关注',
    desc: '系統Agent建议：降低该机组运行频率至40Hz，安排下周停机检修',
    action: '降频'
  },
  {
    from: 'supervisor',
    fromLabel: '系統',
    to: 'ops',
    toLabel: '运维',
    text: '系統Agent指派：执行月度巡检计划',
    desc: '运维Agent将执行：生成巡检工单，覆盖12台冷机、36台末端设备，预计4小时',
    action: '派单'
  },
  {
    from: 'energy',
    fromLabel: '能管',
    to: 'ops',
    toLabel: '运维',
    text: '能管Agent发现：2#冷机COP值下降15%',
    desc: '运维Agent将执行：检查冷媒压力及压缩机状态，排查能效下降原因',
    action: '排查'
  }
])

const isDarkTheme = ref(document.documentElement.getAttribute('data-theme') === 'dark')
const alertCount = ref(0)
const isAlertModalOpen = ref(false)
const isAutoDiagnosisModalOpen = ref(false)
const autoDiagnosisAlert = ref(null)
const autoDiagnosisHasVideo = ref(false)
const aiAnalysisResult = ref(null)
const aiAnalysisLoading = ref(false)
const dispatchTaskResult = ref(null)
const dispatchTaskLoading = ref(false)
const dispatchLoadingVisible = ref(false)
const dispatchRunningInBackground = ref(false)
const isEnvAnalysisModalOpen = ref(false)
const envAnalysisReady = ref(false)
let dispatchLoadingTimer = null
const alertListData = ref([])
const alertCurrentPage = ref(1)
const alertPageSize = ref(5)
const alertTotalCount = ref(0)
const alertFilterType = ref('fault') // 'fault' 或 'alarm'，默认故障
let alertRefreshTimer = null
let aiAnalysisRefreshTimer = null
let dispatchTaskRefreshTimer = null
let knowledgeRefreshTimer = null
const dispatchTaskList = ref([])
const agentCollabTaskList = ref([])
const knowledgeStats = ref({
  queryCount: 0,
  hitRate: 0,
  knowledgeFiles: [],
  totalFiles: 0
})
const knowledgeChartRef = ref(null)
let knowledgeChartInstance = null
const opsKpi = ref({
  totalDiagnosis: 0,
  totalDispatch: 0,
  totalInspections: 0,
  totalWorkOrders: 0
})
let opsKpiRefreshTimer = null
const healthMetrics = ref({
  healthRate: 0,
  faultDevices: 0,
  alarmDevices: 0,
  todayOrders: 0
})
const kpiStats = ref({
  todayDiagnosis: 0,
  todayDispatch: 0
})
const energyMetrics = ref({
  todayEnergy: 0,
  yesterdayCompare: { value: 0, isNormal: false },
  monthEnergy: 0,
  yearEnergy: 0
})
const headerBgImage = computed(() => {
  return isDarkTheme.value ? '/aidashboard/static/head.png' : '/aidashboard/static/head2.png'
})
const sparklineChartRef = ref(null)
let sparklineChart = null
const energyChartRef = ref(null)
let energyChart = null
const isModalOpen = ref(false)
const selectedZone = ref(null)
const isVideoFullscreen = ref(false)
const zoneVideoPlayer = ref(null)
const fullscreenVideoPlayer = ref(null)
const isDispatchModalOpen = ref(false)
const dispatchModalFromDeviceAnalysis = ref(false)
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
const dispatchModalRef = ref(null)

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

function openOpsAgent() {
  router.push('/operations1')
}

const safetyScore = ref(83)

// 服务分析详情弹窗
const isServiceAnalysisModalOpen = ref(false)

function openServiceAnalysisModal() {
  isServiceAnalysisModalOpen.value = true
  nextTick(() => {
    initSaTrendChart()
  })
}

function closeServiceAnalysisModal() {
  isServiceAnalysisModalOpen.value = false
}

const isMapModalOpen = ref(false)

function openMapModal() {
  isMapModalOpen.value = true
}
function closeMapModal() {
  isMapModalOpen.value = false
}

const isLifeModalOpen = ref(false)

function openLifeModal() {
  isLifeModalOpen.value = true
}
function closeLifeModal() {
  isLifeModalOpen.value = false
}

// Agent协作详情弹窗
const isAgentCollabModalOpen = ref(false)
const agentCollabStats = ref({ totalTasks: 0, dispatchedTasks: 0, dispatchRate: 0, dailyStats: [] })
const agentCollabPage = ref(1)
const agentCollabPageSize = 10
const agentCollabTotal = ref(0)

function openAgentCollabModal() {
  isAgentCollabModalOpen.value = true
}

function closeAgentCollabModal() {
  isAgentCollabModalOpen.value = false
  if (agentCollabChartInstance) {
    agentCollabChartInstance.dispose()
    agentCollabChartInstance = null
  }
}

// 派发任务详情弹窗
const isTaskDetailModalOpen = ref(false)
const selectedTaskDetail = ref(null)

function openTaskDetail(task) {
  selectedTaskDetail.value = task
  isTaskDetailModalOpen.value = true
  nextTick(() => {
    animateTaskDetailModal()
  })
}

function animateTaskDetailModal() {
  const modal = document.querySelector('.task-detail-modal')
  if (!modal) return

  // 设置初始状态
  gsap.set(modal, { scale: 0.8, opacity: 0, y: 60 })

  const tl = gsap.timeline({ defaults: { ease: 'back.out(1.4)' } })
      .to(modal, { scale: 1, opacity: 1, y: 0, duration: 0.5 })

  // 逐行内容动画
  const rows = modal.querySelectorAll('.detail-row')
  if (rows.length > 0) {
    gsap.fromTo(rows,
        { opacity: 0, x: -20 },
        { opacity: 1, x: 0, duration: 0.35, stagger: 0.05, ease: 'power2.out', delay: 0.2 }
    )
  }

  // 头部标题动画
  const header = modal.querySelector('.task-detail-header h3')
  if (header) {
    gsap.fromTo(header,
        { opacity: 0, y: -15 },
        { opacity: 1, y: 0, duration: 0.4, ease: 'power2.out', delay: 0.1 }
    )
  }

  // 关闭按钮动画
  const closeBtn = modal.querySelector('.close-btn')
  if (closeBtn) {
    gsap.fromTo(closeBtn,
        { opacity: 0, rotation: -90, scale: 0.5 },
        { opacity: 1, rotation: 0, scale: 1, duration: 0.3, ease: 'back.out(1.7)', delay: 0.15 }
    )
  }
}

function closeTaskDetailModal() {
  isTaskDetailModalOpen.value = false
  selectedTaskDetail.value = null
}

// 站房整体状态评估弹窗
const isHealthModalOpen = ref(false)
const healthTimeDimension = ref('10min') // '10min', '1h', '1d'
const healthScore = ref(92)
const healthLevelDesc = ref('全域运行平稳，用能态势优良，系统运行正常')
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
    name: '站房运营',
    score: 94,
    weight: '10%',
    icon: 'user',
    deductionDevices: []
  }
])

// 站房评估弹窗数据
const healthModalScore = ref(92)
const healthModalLevel = ref('')
const healthModalLevelDesc = ref('')
const healthModalTrendData = ref([])
const healthModalTrendTimes = ref([])
const healthModalTrendLevels = ref([])
const healthModalChartRef = ref(null)
const healthReportRef = ref(null)
let healthModalChart = null
const assetStats = ref({
  total: 0,
  assetTotal: 0,
  deviceTotal: 0,
  deviceNormal: 0,
  deviceFault: 0,
  deviceAlarm: 0
})
const aiStats = ref({
  totalDiagnosis: 0,
  totalDispatch: 0,
  todayDiagnosis: 0,
  todayDispatch: 0
})
const computingPower = ref({
  gpuUtilizationRate: 0,
  processingRequest: 0,
  queueRequest: 0,
  throughput: 0,
  reasoningTime: 0,
  successRequest: 0
})
const keliuAgentData = ref({
  trainCount: '',
  passengerFlow: '',
  alertCount: '',
  severeCount: '',
  normalCount: '',
  notifyCount: '',
  operationStatus: '',
  crowdedCount: '',
  pressureScore: '',
  pressureLevel: '',
  passengerCount: '-',
  congestionText: '-',
  congestionClass: ''
})
const emsAgentData = ref({
  todayTotal: 0,
  yesterdayCompareRate: 0,
  todayDecisionPassRate: 0,
  todayDecisionSuccessRate: 0,
  total: 0,
  decisionPassRate: 0,
  decisionSuccessRate: 0,
  timeoutCount: 0,
  timeoutRate: 0
})
const predictionData = ref({
  predictionDiagnosis: { count: 0, name: '今日预警AI分析', label: '0条' },
  predictionTomorrow: { count: 0, name: '明日运营压力来源AI分析', label: '0条' },
  predictionSource: { count: 0, name: '今日运营压力来源AI分析', label: '0条' },
  predictionFlow: { count: 0, name: '未来客流AI分析预测', label: '0条' },
  totalCount: 0
})
const eventStats = ref({
  remote: 0,
  notification: 0,
  alarm: 0,
  fault: 0
})

const assetStatusRows = computed(() => {
  const statusList = [
    { status: 10, name: '备用', colorClass: 'blue' },
    { status: 20, name: '在用', colorClass: 'green' },
    { status: 30, name: '闲置', colorClass: 'gray' },
    { status: 40, name: '停用', colorClass: 'red' },
    { status: 50, name: '维修', colorClass: 'orange' },
    { status: 60, name: '报废', colorClass: 'dark' }
  ]
  return statusList.map(s => ({
    ...s,
    count: assetStats.value[`asset_${s.status}`] || 0
  })).filter(s => s.count > 0)
})

const eventTotal = computed(() => {
  return eventStats.value.remote + eventStats.value.notification + eventStats.value.alarm + eventStats.value.fault
})

const deviceNormalRate = computed(() => {
  const d = assetStats.value
  if (d.deviceTotal === 0) return 100
  return ((d.deviceNormal / d.deviceTotal) * 100).toFixed(1)
})

// 分项详情弹窗
const isSubItemModalOpen = ref(false)
const currentSubItem = ref(null)

// Canvas 2D 赛博小岛视图（替换原 Three.js 3D 模型）
const heatmapCanvas = ref(null)
const modalHeatmapCanvas = ref(null)
const healthRadarChartRef = ref(null)
const healthScoreChartRef = ref(null)
const zoneViewMode = ref('cards') // 'model' or 'cards'
const zoneCardsSectionRef = ref(null)
const focusedZone = ref(null)
const focusedZoneLoading = ref(false)
const cardZoneAnalysisText = ref('')
const cardZoneAnalysisComplete = ref(true)
// 分区聚焦-异常逐条分析队列
const anomalyQueue = ref([])
const anomalyIndex = ref(0)
const anomalyTaskList = ref([])
const anomalyAnalyzing = ref(false)
const zoneCardSnapshots = computed(() => zoneAreas.value.map(zone => ({
  zone,
  snapshot: buildZoneCardSnapshot(zone)
})))
const zoneStatusSummary = computed(() => {
  const total = zoneAreas.value.length
  const alarm = zoneAreas.value.filter(z => {
    const s = buildZoneCardSnapshot(z)
    return s.anomalyTotal > 0
  }).length
  const normal = total - alarm
  return { total, normal, alarm }
})
const focusedZoneSnapshot = computed(() => focusedZone.value ? buildZoneCardSnapshot(focusedZone.value) : null)
const focusedZoneCardItem = computed(() => {
  if (!focusedZone.value) return null
  return zoneCardSnapshots.value.find(item => item.zone === focusedZone.value) || null
})

// ========== 新版分区态势：AI洞察模拟数据与辅助函数 ==========
const mockInsightData = {
  0: { // 西安东站
    normal: ['未发现设备故障'],
    abnormal: []
  },
  1: { // 地下层
    normal: ['未发现设备故障'],
    abnormal: []
  },
  2: { // 到达层
    normal: ['未发现设备故障'],
    abnormal: []
  },
  3: { // 出发层
    normal: ['未发现设备故障'],
    abnormal: []
  }
}

function getMockInsights(item, idx) {
  const data = mockInsightData[idx] || mockInsightData[0]
  if (item.snapshot.anomalyTotal > 0) {
    return data.abnormal || data.normal
  }
  return data.normal || ['运行正常，各项指标在合理范围内']
}

// ========== 分区未完成任务洞察（AI洞察真实数据） ==========
const subareaPendingInsights = ref({ total: 0, groups: [] })

async function fetchSubareaPendingInsights() {
  try {
    const res = await aiRequest.get('/dispatch/pending-insights')
    if (res && (res.code === '200' || res.Status === 'Ok')) {
      subareaPendingInsights.value = res.data || { total: 0, groups: [] }
    }
  } catch (e) {
    console.error('获取分区未完成任务洞察失败', e)
  }
}

// 获取某分区（zone卡片）对应的未完成任务分组
function getZoneInsightGroup(item) {
  const cid = item.zone?._rawSubareaCid
  if (!cid) return null
  return subareaPendingInsights.value.groups.find(g => g.subareaCid === cid) || null
}

function agentPageName(targetAgentId) {
  if (targetAgentId === 'energy-agent') return '能源管理Agent'
  if (targetAgentId === 'foot-agent') return '车站运营Agent'
  if (targetAgentId === 'ops-agent') return '设备运维Agent'
  return targetAgentId || 'Agent'
}

// 点击小手图标：按任务的targetAgentId进入对应Agent页面
function gotoAgentByTask(task) {
  if (task.targetAgentId === 'energy-agent') {
    openEnergyAgent()
  } else if (task.targetAgentId === 'foot-agent') {
    openPassengerAgent()
  } else if (task.targetAgentId === 'ops-agent') {
    openOpsAgent()
  }
}

function getZoneDeviceTotal(zone) {
  const rawDev = zone?._rawDev || {}
  const total = Number(rawDev.device_total || rawDev.total || 113)
  return total
}

function formatInspectTime(idx) {
  const baseMinutes = 32 + idx * 7
  const baseSeconds = 45 + idx * 13
  return `10:${String(baseMinutes).padStart(2, '0')}:${String(baseSeconds % 60).padStart(2, '0')}`
}

// AI巡检流程步骤
const inspectionSteps = ref([
  { label: '数据采集', desc: '数据实时采集', dotColor: '#00a0ff' },
  { label: '智能分析', desc: 'AI分析诊断', dotColor: '#7c3aed' },
  { label: '风险识别', desc: '识别异常', dotColor: '#ff8800' },
  { label: '生成建议', desc: '输出优化建议', dotColor: '#00c878' }
])
const currentInspectionStep = ref(4)

// ========== 今日巡检统计：汇总区域（第一个区域）数据 ==========
function getSummaryDeviceTotal() {
  const first = zoneAreas.value[0]
  return Number(first?._rawDev?.total || 0)
}

function getSummaryDeviceIssues() {
  const first = zoneAreas.value[0]
  const raw = first?._rawDev || {}
  return Number(raw.fault_total || 0) + Number(raw.alarm_total || 0)
}

function getSummaryEnvTotal() {
  const first = zoneAreas.value[0]
  const env = first?._rawEnvironment
  if (env && typeof env === 'object' && !Array.isArray(env)) {
    return Number(env.totalDevices || 0)
  }
  return 0
}

function getSummaryEnvAbnormal() {
  const first = zoneAreas.value[0]
  const env = first?._rawEnvironment
  if (env && typeof env === 'object' && !Array.isArray(env)) {
    return Number(env.abnormalDevices || 0)
  }
  return 0
}
// ========== 新版分区态势结束 ==========

let healthRadarChart = null
let healthScoreChart = null
let cardZoneTypewriterTimer = null
let zoneCardsAnimation = null

// 设备分析弹窗
const isDeviceAnalysisOpen = ref(false)
const deviceAnalysisData = ref({
  totalDevices: 156,
  healthScore: 87,
  normalDevices: 142,
  faultDevices: 5,
  alarmDevices: 6,
  maintenanceDevices: 3,
  riskItems: [
    { level: 'danger', text: '排风机2# 故障停机，影响通风散热' },
    { level: 'warning', text: '3号水泵振动偏高，存在潜在损坏风险' },
    { level: 'info', text: '配电柜温度异常，建议持续监测' },
  ],
  keyDevices: [
    { icon: 'fan', title: '排风机2# 故障停机', area: '候车厅B区', severity: '紧急', severityClass: 'sev-danger', detail: '无法启动，电机过载保护动作', action: 'AI诊断' },
    { icon: 'pump', title: '3号水泵 振动偏高', area: '检票口B区', severity: '较高', severityClass: 'sev-warning', detail: '振动超标 4.8mm/s', action: 'AI诊断' },
    { icon: 'cabinet', title: '配电柜温度异常', area: '站厅层配电间', severity: '一般', severityClass: 'sev-normal', detail: '柜内温度 62°C', action: 'AI诊断' },
  ],
  maintenanceProgress: [
    { id: 'WO-20240115-001', device: '排风机2#', worker: '张工', progress: 100, status: '已完成', statusClass: 'done' },
    { id: 'WO-20240115-002', device: '3号水泵', worker: '李工', progress: 65, status: '处理中', statusClass: 'processing' },
    { id: 'WO-20240115-003', device: '配电柜B', worker: '王工', progress: 30, status: '待确认', statusClass: 'pending' },
  ],
  trendData: [
    { date: '5/22', anomaly: 5, alarm: 2, fault: 1 },
    { date: '5/23', anomaly: 6, alarm: 5, fault: 1 },
    { date: '5/24', anomaly: 3, alarm: 7, fault: 3 },
    { date: '5/25', anomaly: 2, alarm: 6, fault: 7 },
    { date: '5/26', anomaly: 2, alarm: 3, fault: 1 },
    { date: '5/27', anomaly: 7, alarm: 5, fault: 3 },
    { date: '5/28', anomaly: 0, alarm: 2, fault: 4 },
  ]
})
let deviceHealthScoreChartRef = ref(null)
let deviceHealthScoreChart = null
let deviceTrendChartRef = ref(null)
let deviceTrendChart = null

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

const GRID_SIZE = 100
const GRID_HALF = GRID_SIZE / 2
function toThreePos(logicalX, logicalY, logicalZ) {
  return { x: logicalX - GRID_HALF, y: logicalY, z: logicalZ - GRID_HALF }
}

const healthTrendData = ref([])

const zoneAreas = ref([])
const zoneAreasLoading = ref(false)

// Canvas 2D 赛博小岛实例（主视图 + 模态视图）
// 必须在 zoneAreas 声明之后调用 composable，否则 TDZ 报错
// 第二参数传 getter 函数而非 ref，避免任何潜在的初始化顺序问题
const mainIslands = useZoneCyberIslands(heatmapCanvas, () => zoneAreas.value, (canvasNode) => {
  const liveZone = canvasNode.liveRef
  if (liveZone) selectZoneInModel(liveZone)
})
const modalIslands = useZoneCyberIslands(modalHeatmapCanvas, () => zoneAreas.value, (canvasNode) => {
  const liveZone = canvasNode.liveRef
  if (liveZone) {
    selectedZone.value = liveZone
    mainIslands.selectedNodeId.value = canvasNode.id
  }
})

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
const isPassengerModalOpen = ref(false)
const isEnergyModalOpen = ref(false)
// 能源管理Agent：无后端演示模式下展示静态示意图（public/能源管理Agent.png）
const energyAgentImageUrl = import.meta.env.BASE_URL + '能源管理Agent.png'

function closeKnowledgeModal() {
  isKnowledgeModalOpen.value = false
}

function openPassengerAgent() {
  isPassengerModalOpen.value = true
}

function closePassengerModal() {
  isPassengerModalOpen.value = false
}

function openEnergyAgent() {
  isEnergyModalOpen.value = true
}

function closeEnergyModal() {
  isEnergyModalOpen.value = false
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

function goToAdmin() {
  router.push('/config')
}

function goToExternal() {
  if (typeof window !== 'undefined') {
    const modal = window.parent.document.getElementById('aidashboardModal');
    if (modal) modal.remove();
  }
}

function initSparklineChart() {
  if (!sparklineChartRef.value) return
  sparklineChart = echarts.init(sparklineChartRef.value)
  const data = healthTrendData.value.length > 0 ? healthTrendData.value : [0]
  const times = healthModalTrendTimes.value.length > 0 ? healthModalTrendTimes.value : data.map(() => '')
  const levels = healthModalTrendLevels.value.length > 0 ? healthModalTrendLevels.value : data.map(() => '')
  const minVal = Math.min(...data)
  const maxVal = Math.max(...data)
  const padding = Math.max((maxVal - minVal) * 0.1, 5)
  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        const t = times[p.dataIndex] || ''
        const level = levels[p.dataIndex] || ''
        return `${t}<br/>整体评估: <b>${level}</b>`
      },
      backgroundColor: 'rgba(20, 30, 50, 0.9)',
      borderColor: 'rgba(10, 170, 97, 0.3)',
      textStyle: { color: '#e8f0fe', fontSize: 12 }
    },
    grid: { top: 0, bottom: 0, left: 0, right: 0 },
    xAxis: { type: 'category', show: false, data: data.map((_, i) => i) },
    yAxis: { type: 'value', show: false, min: Math.max(0, minVal - padding), max: maxVal + padding },
    series: [{
      type: 'line',
      data,
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

// 获取历史评分曲线（最近6条）
async function fetchHealthScoreHistory() {
  try {
    const now = new Date()
    const sixHoursAgo = new Date(now.getTime() - 6 * 60 * 60 * 1000)
    const pad = (n) => String(n).padStart(2, '0')
    const endTime = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
    const startTime = `${sixHoursAgo.getFullYear()}-${pad(sixHoursAgo.getMonth() + 1)}-${pad(sixHoursAgo.getDate())} ${pad(sixHoursAgo.getHours())}:${pad(sixHoursAgo.getMinutes())}:${pad(sixHoursAgo.getSeconds())}`

    const res = await aiRequest.get('/getHistoryScore', {
      params: { org_id: userStore.org_id || 3, start_time: startTime, end_time: endTime }
    })
    console.log('历史评分响应:', res)
    if (res && (res.code === '200' || res.code === 200) && Array.isArray(res.data)) {
      // 只取最近6条
      const data = res.data.slice(-6)
      console.log('历史评分数据条数:', data.length)
      healthModalTrendData.value = data.map(item => Number(item.totalScore) || 0)
      healthModalTrendTimes.value = data.map(item => item.createTime ? item.createTime.replace('T', ' ') : '')
      healthModalTrendLevels.value = data.map(item => item.totalLevel || '')
      healthTrendData.value = healthModalTrendData.value
      // 最新一条数据的levelDesc
      if (data.length > 0 && data[data.length - 1].levelDesc) {
        healthLevelDesc.value = data[data.length - 1].levelDesc
      }
      nextTick(() => {
        initSparklineChart()
      })
    }
  } catch (err) {
    console.error('获取历史评分曲线失败:', err)
  }
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

const saTrendChartRef = ref(null)
let saTrendChart = null

function initSaTrendChart() {
  if (!saTrendChartRef.value) return
  saTrendChart = echarts.init(saTrendChartRef.value)
  const hours = ['00:00', '02:00', '04:00', '06:00', '08:00', '10:00', '12:00', '14:00', '16:00', '18:00', '20:00', '22:00', '24:00']
  const flowData = [2000, 1500, 1200, 2500, 8000, 12000, 9500, 8000, 7500, 10000, 6000, 3500, 2000]
  const option = {
    grid: { top: 20, bottom: 25, left: 45, right: 15 },
    tooltip: { trigger: 'axis', backgroundColor: 'rgba(0,20,50,0.9)', borderColor: 'rgba(0,150,255,0.3)', textStyle: { color: '#e0f0ff', fontSize: 12 } },
    xAxis: {
      type: 'category',
      data: hours,
      boundaryGap: false,
      axisLine: { lineStyle: { color: 'rgba(0,150,255,0.2)', width: 1 } },
      axisTick: { show: false },
      axisLabel: { color: 'rgba(180,200,230,0.5)', fontSize: 10, interval: 1 }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 16000,
      splitNumber: 4,
      axisLabel: { color: 'rgba(180,200,230,0.5)', fontSize: 10, formatter: '{value}' },
      splitLine: { lineStyle: { color: 'rgba(0,150,255,0.08)', type: 'solid' } },
      axisLine: { show: false }
    },
    series: [
      {
        name: '客流量',
        type: 'line',
        data: flowData,
        smooth: true,
        showSymbol: false,
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(0,150,255,0.35)' },
              { offset: 1, color: 'rgba(0,150,255,0.02)' }
            ]
          }
        },
        lineStyle: { width: 2, color: '#0088ff' },
        itemStyle: { color: '#00aaff' }
      }
    ],
    markArea: {
      silent: true,
      data: [[{
        xAxis: '08:00',
        itemStyle: { color: 'rgba(0,150,255,0.08)' }
      }, {
        xAxis: '10:00'
      }]],
      label: { show: true, formatter: '08:00-10:00', color: 'rgba(0,170,255,0.6)', fontSize: 10, position: 'insideTop' }
    }
  }
  saTrendChart.setOption(option)
}
const aiFullText = ref('')

let aiTypewriterTimer = null

function startAiTypewriter() {
  aiAnalysisText.value = ''
  aiAnalysisComplete.value = false
  let charIndex = 0

  function typeChar() {
    const text = aiFullText.value
    if (charIndex < text.length) {
      aiAnalysisText.value += text[charIndex]
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
const showZoneVideo = ref(false)
let zoneTypewriterTimer = null
let actionShowTimers = []

function startZoneTypewriter(zone) {
  stopZoneTypewriter()
  clearActionShowTimers()
  zoneAnalysisText.value = ''
  zoneAnalysisComplete.value = false
  visibleActionCount.value = 0
  showZoneVideo.value = false
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
            // 最后一个卡片显示完后显示视频
            if (i === actionCount - 1) {
              const videoTimer = setTimeout(() => {
                showZoneVideo.value = true
                if (zoneVideoPlayer.value) {
                  zoneVideoPlayer.value.play().catch(() => {})
                }
              }, 400)
              actionShowTimers.push(videoTimer)
            }
          }, 300 + i * 400)
          actionShowTimers.push(timer)
        }
      } else {
        // 没有决策卡片，打字完成后直接显示视频
        const videoTimer = setTimeout(() => {
          showZoneVideo.value = true
          if (zoneVideoPlayer.value) {
            zoneVideoPlayer.value.play().catch(() => {})
          }
        }, 500)
        actionShowTimers.push(videoTimer)
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

function stopCardZoneTypewriter() {
  if (cardZoneTypewriterTimer) {
    clearTimeout(cardZoneTypewriterTimer)
    cardZoneTypewriterTimer = null
  }
}

function startCardZoneTypewriter(zone) {
  stopCardZoneTypewriter()
  cardZoneAnalysisText.value = ''
  cardZoneAnalysisComplete.value = false
  const text = zone?.zoneAnalysis || ''
  if (!text) {
    cardZoneAnalysisComplete.value = true
    return
  }

  let idx = 0
  const tick = () => {
    if (idx < text.length) {
      cardZoneAnalysisText.value += text[idx]
      idx++
      cardZoneTypewriterTimer = setTimeout(tick, 22)
    } else {
      cardZoneAnalysisComplete.value = true
      cardZoneTypewriterTimer = null
    }
  }
  tick()
}

function killZoneCardsAnimation() {
  if (zoneCardsAnimation) {
    zoneCardsAnimation.kill()
    zoneCardsAnimation = null
  }
}

function animateZoneCardGrid() {
  const root = zoneCardsSectionRef.value
  if (!root) return
  const cards = root.querySelectorAll('.zone-card-tile')
  if (!cards.length) return
  killZoneCardsAnimation()
  zoneCardsAnimation = gsap.fromTo(cards,
      { autoAlpha: 0, y: 18, scale: 0.98 },
      { autoAlpha: 1, y: 0, scale: 1, duration: 0.42, stagger: 0.06, ease: 'power3.out', clearProps: 'transform,opacity,visibility' }
  )
}

function animateFocusedZoneCard() {
  const root = zoneCardsSectionRef.value
  if (!root) return
  killZoneCardsAnimation()
  const focusCard = root.querySelector('.zone-focus-card')
  const aiPanel = root.querySelector('.zone-ai-insight-panel')
  const detailItems = root.querySelectorAll('.zone-focus-card .zone-focus-reveal')

  zoneCardsAnimation = gsap.timeline({ defaults: { ease: 'power3.out' } })
  if (focusCard) {
    zoneCardsAnimation.fromTo(focusCard,
        { autoAlpha: 0, x: 42, scale: 0.96 },
        { autoAlpha: 1, x: 0, scale: 1, duration: 0.46, clearProps: 'transform,opacity,visibility' },
        0
    )
  }
  if (aiPanel) {
    zoneCardsAnimation.fromTo(aiPanel,
        { autoAlpha: 0, x: -26 },
        { autoAlpha: 1, x: 0, duration: 0.5, clearProps: 'transform,opacity,visibility' },
        0.08
    )
  }
  if (detailItems.length) {
    zoneCardsAnimation.fromTo(detailItems,
        { autoAlpha: 0, y: 12 },
        { autoAlpha: 1, y: 0, duration: 0.34, stagger: 0.045, clearProps: 'transform,opacity,visibility' },
        0.18
    )
  }
}

// ===== 分区异常逐条分析（聚焦视图） =====
// 构建异常队列：环境 → 设备 → 能耗 → 客流
function buildAnomalyQueue(zone) {
  const queue = []
  // 环境异常：按设备粒度，一个环境设备的所有异常数据为一条
  if (Array.isArray(zone._rawEnvironment)) {
    zone._rawEnvironment
        .filter(env => env.alarm_data && env.alarm_data.length > 0)
        .forEach(env => {
          const alarmItems = env.alarm_data.map(a => `${a.name}:${a.value}`).join('，')
          queue.push({
            type: 'env',
            desc: `${env.device_name || '环境设备'}异常（${alarmItems}）`,
            envDevice: {
              id: env.id,
              name: env.device_name,
              location: env.location,
              alarmData: env.alarm_data
            }
          })
        })
  }
  ;(zone.anomalyDevices || []).forEach(d => {
    queue.push({
      type: 'device',
      desc: `${d.name}(${d.id})${d.status}`,
      deviceRef: d
    })
  })
  if (zone.energyStatus && String(zone.energyStatus).includes('注意')) {
    queue.push({
      type: 'energy',
      desc: '能耗异常',
      energy: zone._rawEnergy || { message: '能耗异常', value: zone.energyToday, status: zone.energyStatus }
    })
  }
  return queue
}

// 构建单条异常分析 payload（对应后端 ZoneAnalysisRequest 单条结构）
function buildSingleAnomalyPayload(zone, anomaly) {
  const subareaCid = zone._rawSubareaCid || zone.subareaCid
  const zoneName = zone.name
  const desc = zone._desc || []
  const payload = { anomalyType: anomaly.type, subareaCid, zoneName, desc }
  if (anomaly.type === 'env') {
    payload.envDevice = anomaly.envDevice
  } else if (anomaly.type === 'device') {
    const d = anomaly.deviceRef
    payload.device = { id: d.id, name: d.name, type: d.type, status: d.status, currentData: d.currentData, location: d.location }
  } else if (anomaly.type === 'energy') {
    payload.energy = anomaly.energy
  } else if (anomaly.type === 'keliu') {
    payload.keliu = anomaly.keliu
  }
  return payload
}

// 构造数据快照JSON，供后侧运维Agent从任务中解析设备/区域信息（详见 Operations.vue handlePendingTaskDiagnosis）
// 输出字符串格式与 Operations.vue 消费端兼容：zoneName + deviceList[{id,name,status,type}] 或顶层 deviceId/deviceName/processState
function buildDataSnapshot(zone, anomaly) {
  const zoneName = zone.name || ''
  const subareaCid = zone._rawSubareaCid || zone.subareaCid || ''
  const snapshot = { zoneName, subareaCid, anomalyType: anomaly.type, anomalyDesc: anomaly.desc || '' }
  if (anomaly.type === 'device' && anomaly.deviceRef) {
    const d = anomaly.deviceRef
    const deviceInfo = {
      id: d.id || '',
      name: d.name || '',
      type: d.type || '',
      status: d.status || '',
      location: d.location || ''
    }
    // deviceList 为主路径，同时补顶层字段作为兜底（Operations.vue 第二分支用 processState 判故障）
    snapshot.deviceList = [deviceInfo]
    snapshot.deviceId = deviceInfo.id
    snapshot.deviceName = deviceInfo.name
    snapshot.processState = d.status === '故障' ? 1 : 0
  } else if (anomaly.type === 'env' && anomaly.envDevice) {
    const d = anomaly.envDevice
    snapshot.envDevice = {
      id: d.id,
      name: d.name,
      location: d.location,
      alarmData: d.alarmData
    }
  }
  return JSON.stringify(snapshot)
}

// 统计：已处理(保存+下发) / 已取消 / 待分析(还没轮到)
const anomalyStats = computed(() => {
  const total = anomalyQueue.value.length
  const generated = anomalyIndex.value
  const processed = anomalyTaskList.value.filter(t => t.status === 'saved' || t.status === 'dispatched').length
  const cancelled = anomalyTaskList.value.filter(t => t.status === 'cancelled').length
  const pending = Math.max(0, total - generated)
  return { total, generated, processed, cancelled, pending }
})

const anomalyHasNext = computed(() => anomalyIndex.value < anomalyQueue.value.length)
const anomalyAllDone = computed(() => anomalyQueue.value.length > 0 && anomalyIndex.value >= anomalyQueue.value.length && !anomalyAnalyzing.value)

// 降级任务（AI失败时本地生成）
function buildFallbackAnomalyTask(zone, anomaly) {
  let businessDomain = '告警处理'
  let level = '中'
  let decisionContent = ''
  let targetAgentId = 'commander'
  let targetAgentName = '系統Agent'
  if (anomaly.type === 'env') {
    businessDomain = '告警处理'
    const d = anomaly.envDevice
    const alarmItems = d?.alarmData?.map(a => a.name).join('、') || '环境参数'
    decisionContent = `检查${d?.name || alarmItems}相关设备运行状态`
  } else if (anomaly.type === 'device') {
    const d = anomaly.deviceRef
    businessDomain = '维修工单'
    level = d.status === '故障' ? '高' : '中'
    decisionContent = d.status === '故障' ? `安排维修人员对${d.name}(${d.id})到场检修` : `排查${d.name}(${d.id})告警原因`
    targetAgentId = 'ops-agent'
    targetAgentName = '设备运维Agent'
  } else if (anomaly.type === 'energy') {
    businessDomain = '能耗'
    decisionContent = '排查能耗异常原因，优化用能策略'
  } else if (anomaly.type === 'keliu') {
    businessDomain = '客流压力'
    decisionContent = '关注客流密度，必要时启动疏导措施'
  }
  return {
    uid: `${anomaly.type}-${anomalyIndex.value}-${Date.now()}`,
    anomalyType: anomaly.type,
    anomalyDesc: anomaly.desc,
    taskId: null,
    businessDomain,
    level,
    problemDesc: anomaly.desc,
    thinkingOverview: `检测到${anomaly.desc}`,
    problemSummary: anomaly.desc,
    decisionContent,
    decisionData: '[]',
    expectedEffect: '恢复正常运行',
    targetAgentId,
    targetAgentName,
    taskType: 'collaboration',
    zoneAnalysis: '',
    suggestion: '',
    subareaCid: zone._rawSubareaCid || zone.subareaCid,
    zoneName: zone.name,
    dataSnapshot: buildDataSnapshot(zone, anomaly),
    status: 'pending',
    statusText: '',
    statusClass: '',
    busy: false
  }
}

// 处理下一条异常：获取实时数据 + 调单条AI分析，生成一个任务卡片（不落库）
async function processNextAnomaly() {
  if (anomalyAnalyzing.value) return
  if (anomalyIndex.value >= anomalyQueue.value.length) return
  const zone = focusedZone.value
  if (!zone) return
  const anomaly = anomalyQueue.value[anomalyIndex.value]
  anomalyAnalyzing.value = true

  try {
    // 环境/设备：先拉取设备实时数据
    if (anomaly.type === 'device' && anomaly.deviceRef && !anomaly.deviceRef._loaded) {
      try {
        const deviceData = await fetchDeviceData(anomaly.deviceRef.id)
        const currentData = deviceData
            .filter(d => isDataAbnormal(d))
            .map(d => ({ label: d.data_short_name, value: d.dataValue }))
        if (currentData.length > 0) {
          anomaly.deviceRef.currentData = currentData
        }
        anomaly.deviceRef._loaded = true
      } catch (e) {
        console.error(`获取设备${anomaly.deviceRef.id}实时数据失败:`, e)
      }
    }

    // 调单条AI分析（不落库）
    const aiResult = await fetchZoneAnalysis(buildSingleAnomalyPayload(zone, anomaly))

    let task = null
    if (aiResult && aiResult.dispatchTasks && aiResult.dispatchTasks.length > 0) {
      const t = aiResult.dispatchTasks[0]
      task = {
        uid: `${anomaly.type}-${anomalyIndex.value}-${Date.now()}`,
        anomalyType: anomaly.type,
        anomalyDesc: anomaly.desc,
        taskId: t.id || t.taskId || null,
        businessDomain: t.businessDomain,
        level: t.level,
        problemDesc: t.problemDesc,
        thinkingOverview: t.thinkingOverview,
        problemSummary: t.problemSummary,
        decisionContent: t.decisionContent,
        decisionData: t.decisionData,
        expectedEffect: t.expectedEffect,
        targetAgentId: t.targetAgentId,
        targetAgentName: t.targetAgentName,
        taskType: t.taskType,
        zoneAnalysis: aiResult.zoneAnalysis || '',
        suggestion: aiResult.suggestion || '',
        subareaCid: zone._rawSubareaCid || zone.subareaCid,
        zoneName: zone.name,
        dataSnapshot: buildDataSnapshot(zone, anomaly),
        status: 'pending',
        statusText: '',
        statusClass: '',
        busy: false
      }
    } else {
      task = buildFallbackAnomalyTask(zone, anomaly)
    }

    if (task) {
      anomalyTaskList.value.unshift(task)
    }
  } catch (e) {
    console.error('单条异常分析失败:', e)
    const task = buildFallbackAnomalyTask(zone, anomaly)
    if (task) anomalyTaskList.value.unshift(task)
  } finally {
    anomalyAnalyzing.value = false
    anomalyIndex.value += 1
  }
}

// 保存任务到数据库（dispatched=0）
async function saveAnomalyTask(task) {
  if (task.busy) return
  if (task.status !== 'pending') return
  task.busy = true
  try {
    const body = {
      subareaCid: task.subareaCid,
      creatorAgentId: 'commander',
      creatorAgentName: '系統Agent',
      businessDomain: task.businessDomain,
      level: task.level,
      problemDesc: task.problemDesc,
      thinkingOverview: task.thinkingOverview,
      problemSummary: task.problemSummary,
      decisionContent: task.decisionContent,
      decisionData: task.decisionData,
      expectedEffect: task.expectedEffect,
      targetAgentId: task.targetAgentId,
      targetAgentName: task.targetAgentName,
      taskType: task.taskType,
      dataSnapshot: task.dataSnapshot || null,
      dispatched: 0
    }
    const res = await aiRequest.post('/dispatch', body)
    if (res && (res.code === '200' || res.Status === 'Ok')) {
      task.taskId = res.data?.id
      task.status = 'saved'
      task.statusText = '已保存'
      task.statusClass = 'saved'
      showToast('success', '保存成功', '任务已保存，可在任务列表中派发')
    } else {
      showToast('error', '保存失败', res?.msg || '未知错误')
    }
  } catch (e) {
    showToast('error', '保存失败', '网络异常')
    console.error('保存任务异常:', e)
  } finally {
    task.busy = false
  }
}

// 下发任务（dispatched=1 + 派发时间 + 派发人），未保存则先创建再派发
async function dispatchAnomalyTask(task) {
  if (task.busy) return
  if (task.status !== 'pending') return
  task.busy = true
  try {
    let id = task.taskId
    if (!id) {
      const createBody = {
        subareaCid: task.subareaCid,
        creatorAgentId: 'commander',
        creatorAgentName: '系統Agent',
        businessDomain: task.businessDomain,
        level: task.level,
        problemDesc: task.problemDesc,
        thinkingOverview: task.thinkingOverview,
        problemSummary: task.problemSummary,
        decisionContent: task.decisionContent,
        decisionData: task.decisionData,
        expectedEffect: task.expectedEffect,
        targetAgentId: task.targetAgentId,
        targetAgentName: task.targetAgentName,
        taskType: task.taskType,
        dataSnapshot: task.dataSnapshot || null,
        dispatched: 0
      }
      const createRes = await aiRequest.post('/dispatch', createBody)
      if (createRes && (createRes.code === '200' || createRes.Status === 'Ok')) {
        id = createRes.data?.id
        task.taskId = id
      } else {
        showToast('error', '下发失败', createRes?.msg || '创建任务失败')
        return
      }
    }
    const dispatchBody = {
      id,
      businessDomain: task.businessDomain,
      level: task.level,
      problemDesc: task.problemDesc,
      thinkingOverview: task.thinkingOverview,
      problemSummary: task.problemSummary,
      decisionContent: task.decisionContent,
      decisionData: task.decisionData,
      expectedEffect: task.expectedEffect,
      targetAgentId: task.targetAgentId,
      targetAgentName: task.targetAgentName,
      taskType: task.taskType,
      dispatchedUser: userStore.user_name,
      dispatched: 0
    }
    const res = await aiRequest.post('/dispatch/dispatch', dispatchBody)
    if (res && (res.code === '200' || res.Status === 'Ok')) {
      task.status = 'dispatched'
      task.statusText = '已下发'
      task.statusClass = 'dispatched'
      showToast('success', '下发成功', `任务已派发至${task.targetAgentName}`)
    } else {
      showToast('error', '下发失败', res?.msg || '未知错误')
    }
  } catch (e) {
    showToast('error', '下发失败', '网络异常')
    console.error('下发任务异常:', e)
  } finally {
    task.busy = false
  }
}

// 取消任务（dispatched=2），未保存则先创建再取消
async function cancelAnomalyTask(task) {
  if (task.busy) return
  if (task.status !== 'pending') return
  task.busy = true
  try {
    let id = task.taskId
    if (!id) {
      const createBody = {
        subareaCid: task.subareaCid,
        creatorAgentId: 'commander',
        creatorAgentName: '系統Agent',
        businessDomain: task.businessDomain,
        level: task.level,
        problemDesc: task.problemDesc,
        thinkingOverview: task.thinkingOverview,
        problemSummary: task.problemSummary,
        decisionContent: task.decisionContent,
        decisionData: task.decisionData,
        expectedEffect: task.expectedEffect,
        targetAgentId: task.targetAgentId,
        targetAgentName: task.targetAgentName,
        taskType: task.taskType,
        dataSnapshot: task.dataSnapshot || null,
        dispatched: 0
      }
      const createRes = await aiRequest.post('/dispatch', createBody)
      if (createRes && (createRes.code === '200' || createRes.Status === 'Ok')) {
        id = createRes.data?.id
        task.taskId = id
      }
    }
    if (id) {
      await aiRequest.post(`/dispatch/cancel/${id}`, { dispatchedUser: userStore.user_name })
    }
    task.status = 'cancelled'
    task.statusText = '已取消'
    task.statusClass = 'cancelled'
    showToast('info', '已取消', '任务已取消')
  } catch (e) {
    showToast('error', '取消失败', '网络异常')
    console.error('取消任务异常:', e)
  } finally {
    task.busy = false
  }
}

function taskStatusClass(task) {
  return {
    'task-dispatched': task.status === 'dispatched',
    'task-saved': task.status === 'saved',
    'task-cancelled': task.status === 'cancelled'
  }
}

function anomalyTypeLabel(type) {
  const map = { env: '环境', device: '设备', energy: '能耗', keliu: '客流' }
  return map[type] || type
}

async function focusZoneCard(zone) {
  if (!zone) return
  focusedZone.value = zone
  focusedZoneLoading.value = true
  cardZoneAnalysisText.value = ''
  cardZoneAnalysisComplete.value = true

  // 构建异常队列（环境 → 设备 → 能耗 → 客流），逐条分析
  anomalyQueue.value = buildAnomalyQueue(zone)
  anomalyIndex.value = 0
  anomalyTaskList.value = []

  await nextTick()
  animateFocusedZoneCard()

  // 自动处理第一条异常
  try {
    if (anomalyQueue.value.length > 0) {
      await processNextAnomaly()
    }
  } finally {
    focusedZoneLoading.value = false
  }
}

// 模型视图模式下点击区域：只展示该区域卡片，不触发AI分析
function selectZoneInModel(zone) {
  if (!zone) return
  focusedZone.value = zone
  focusedZoneLoading.value = false
  cardZoneAnalysisText.value = ''
  cardZoneAnalysisComplete.value = true
  anomalyQueue.value = []
  anomalyIndex.value = 0
  anomalyTaskList.value = []
  anomalyAnalyzing.value = false
  nextTick(() => {
    animateFocusedZoneCard()
  })
}

function clearFocusedZone() {
  focusedZone.value = null
  focusedZoneLoading.value = false
  stopCardZoneTypewriter()
  cardZoneAnalysisText.value = ''
  cardZoneAnalysisComplete.value = true
  anomalyQueue.value = []
  anomalyIndex.value = 0
  anomalyTaskList.value = []
  anomalyAnalyzing.value = false
  nextTick(() => {
    animateZoneCardGrid()
  })
}

async function selectZoneFromModel(zone) {
  selectedZone.value = selectedZone.value === zone ? null : zone
  if (selectedZone.value) {
    await analyzeZoneOnDemand(zone)
    startZoneTypewriter(zone)
    nextTick(() => {
      const section = document.querySelector('.modal-zone-section')
      if (section) section.scrollTop = 0
    })
  } else {
    stopZoneTypewriter()
    clearActionShowTimers()
    visibleActionCount.value = 0
    showZoneVideo.value = false
    if (zoneVideoPlayer.value) {
      zoneVideoPlayer.value.pause()
    }
  }
}

function openVideoFullscreen() {
  isVideoFullscreen.value = true
  nextTick(() => {
    if (fullscreenVideoPlayer.value) {
      fullscreenVideoPlayer.value.play().catch(() => {})
    }
  })
}

function closeVideoFullscreen() {
  isVideoFullscreen.value = false
  if (fullscreenVideoPlayer.value) {
    fullscreenVideoPlayer.value.pause()
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
      modalIslands.start()
      startAiTypewriter()
    }, 200)
  })
}

async function openModalWithZone(zone) {
  isModalOpen.value = true
  selectedZone.value = zone

  await analyzeZoneOnDemand(zone)

  nextTick(() => {
    setTimeout(() => {
      modalIslands.start()
      startAiTypewriter()
      // 延迟启动区域分析和扫描动画
      setTimeout(() => {
        startZoneTypewriter(zone)
        // 高亮模态画布上对应的节点（替代原 .scan-line DOM 动画）
        const matched = modalIslands.canvasNodes.value.find(n => n.cascadeId === zone._rawSubareaCid || n.name === zone.name)
        if (matched) {
          modalIslands.pulseNode(matched.id)
          modalIslands.selectedNodeId.value = matched.id
        }
      }, 500)
    }, 200)
  })
}

// 按需调用 AI 分析分区态势
async function analyzeZoneOnDemand(zone) {
  const isWarn = zone._rawIsWarn
  const desc = zone._desc || []

  // 默认值
  const snapshotDefaults = buildZoneCardSnapshot(zone)
  let zoneAnalysis = `${zone.name}${isWarn ? '存在异常：' + desc.join('，') : '运行正常'}`
  let suggestion = '运行正常，建议维持现有巡检频次'
  let suggestionClass = 'green-text'
  let envScore = snapshotDefaults.envScore
  let envStatus = snapshotDefaults.envStatus
  let envStatusClass = snapshotDefaults.anomalyTotal > 0 ? 'orange-text' : 'green-text'
  const dispatchTasks = []

  // 构建异常队列，取第一条调单条AI分析（模态视图复用单条接口）
  const queue = buildAnomalyQueue(zone)
  if (queue.length > 0) {
    const anomaly = queue[0]
    // 设备异常先拉实时数据
    if (anomaly.type === 'device' && anomaly.deviceRef && !anomaly.deviceRef._loaded) {
      try {
        const deviceData = await fetchDeviceData(anomaly.deviceRef.id)
        const currentData = deviceData
            .filter(d => isDataAbnormal(d))
            .map(d => ({ label: d.data_short_name, value: d.dataValue }))
        if (currentData.length > 0) anomaly.deviceRef.currentData = currentData
        anomaly.deviceRef._loaded = true
      } catch (e) {
        console.error(`获取设备${anomaly.deviceRef.id}实时数据失败:`, e)
      }
    }
    const aiResult = await fetchZoneAnalysis(buildSingleAnomalyPayload(zone, anomaly))
    if (aiResult) {
      if (aiResult.zoneAnalysis) zoneAnalysis = aiResult.zoneAnalysis
      if (aiResult.dispatchTasks && aiResult.dispatchTasks.length > 0) {
        const t = aiResult.dispatchTasks[0]
        dispatchTasks.push({
          taskId: t.id || t.taskId || null,
          businessDomain: t.businessDomain,
          level: t.level,
          problemDesc: t.problemDesc,
          thinkingOverview: t.thinkingOverview,
          problemSummary: t.problemSummary,
          decisionContent: t.decisionContent,
          decisionData: t.decisionData,
          expectedEffect: t.expectedEffect,
          targetAgentId: t.targetAgentId,
          targetAgentName: t.targetAgentName,
          taskType: t.taskType,
          dispatched: false
        })
      }
      if ('suggestion' in aiResult) {
        suggestion = aiResult.suggestion || ''
        suggestionClass = aiResult.envStatus
            ? (aiResult.envStatus === '正常' ? 'green-text' : 'orange-text')
            : (snapshotDefaults.anomalyTotal > 0 ? 'orange-text' : 'green-text')
      }
      if (aiResult.envScore) envScore = aiResult.envScore
      if (aiResult.envStatus) {
        envStatus = aiResult.envStatus
        envStatusClass = aiResult.envStatus === '正常' ? 'green-text' : 'orange-text'
      }
    } else {
      // AI失败，本地降级（针对第一条异常）
      const fb = buildFallbackAnomalyTask(zone, anomaly)
      dispatchTasks.push({
        taskId: null,
        businessDomain: fb.businessDomain,
        level: fb.level,
        problemDesc: fb.problemDesc,
        thinkingOverview: fb.thinkingOverview,
        problemSummary: fb.problemSummary,
        decisionContent: fb.decisionContent,
        expectedEffect: fb.expectedEffect,
        targetAgentId: fb.targetAgentId,
        targetAgentName: fb.targetAgentName,
        dispatched: false
      })
      suggestion = `${anomaly.desc}，建议及时排查处理`
      suggestionClass = 'orange-text'
    }
  } else {
    zoneAnalysis = `${zone.name}运行正常。环境、设备、客流与能耗数据均未发现异常，建议维持当前运行策略。`
    suggestion = '运行正常，建议维持现有巡检频次'
    suggestionClass = 'green-text'
  }

  // 更新分区数据
  zone.zoneAnalysis = zoneAnalysis
  zone.suggestion = suggestion
  zone.suggestionClass = suggestionClass
  zone.envScore = envScore
  zone.totalScore = snapshotDefaults.totalScore
  zone.envStatus = envStatus
  zone.envStatusClass = envStatusClass
  zone.dispatchTasks = dispatchTasks
}

function triggerPanelScan() {
  // Canvas 2D 视图下用节点脉冲高亮替代原 DOM .scan-line 动画
  if (!selectedZone.value) return
  const matched = modalIslands.canvasNodes.value.find(n => n.cascadeId === selectedZone.value._rawSubareaCid || n.name === selectedZone.value.name)
  if (matched) modalIslands.pulseNode(matched.id)
}

function closeModal() {
  isModalOpen.value = false
  stopAiTypewriter()
  stopZoneTypewriter()
  clearActionShowTimers()
  visibleActionCount.value = 0
  selectedZone.value = null
  modalIslands.stop()
  modalIslands.selectedNodeId.value = null
  // 关闭弹窗时清空主视图节点的选中态，恢复原色
  mainIslands.selectedNodeId.value = null
}

function switchZoneView(mode) {
  zoneViewMode.value = mode
  if (mode === 'model') {
    clearFocusedZone()
    nextTick(() => {
      setTimeout(() => {
        mainIslands.stop()
        mainIslands.start()
      }, 100)
    })
  } else {
    mainIslands.stop()
    clearFocusedZone()
  }
}

function openHealthModal() {
  isHealthModalOpen.value = true
  fetchHealthModalData()
  fetchEventStats()
}

function closeHealthModal() {
  isHealthModalOpen.value = false
  if (healthModalChart) {
    healthModalChart.dispose()
    healthModalChart = null
  }
}

function openEnvAnalysis() {
  isEnvAnalysisModalOpen.value = true
}

function closeEnvAnalysis() {
  isEnvAnalysisModalOpen.value = false
}

// 环境分析后台完成轮询
let envAnalysisPollTimer = null

function startEnvAnalysisPolling() {
  envAnalysisPollTimer = setInterval(async () => {
    if (envAnalysisReady.value) return
    try {
      const res = await aiRequest.get('/env/analyze/result', {
        params: { org_id: userStore.org_id }
      })
      if (res?.data?.status === 'ready') {
        envAnalysisReady.value = true
        showToast('info', '环境分析完成', '环境设备分析已完成，点击按钮查看报告')
        clearInterval(envAnalysisPollTimer)
      }
    } catch (e) { /* ignore */ }
  }, 15000)
}

// 页面加载后开始轮询
if (typeof window !== 'undefined') {
  // 在onMounted或nextTick中启动
}

// 清理轮询
onBeforeUnmount(() => {
  if (envAnalysisPollTimer) {
    clearInterval(envAnalysisPollTimer)
    envAnalysisPollTimer = null
  }
})

async function exportHealthReport() {
  if (!healthReportRef.value) return

  const el = healthReportRef.value
  const modal = el.closest('.health-modal')

  // 保存原始样式
  const originals = {
    el: { overflow: el.style.overflow, height: el.style.height, maxHeight: el.style.maxHeight },
    modal: modal ? { overflow: modal.style.overflow, maxHeight: modal.style.maxHeight } : null
  }

  // 临时展开元素以捕获完整内容
  el.style.overflow = 'visible'
  el.style.height = 'auto'
  el.style.maxHeight = 'none'
  if (modal) {
    modal.style.overflow = 'visible'
    modal.style.maxHeight = 'none'
  }

  // 等待DOM重排
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

    // 第一页
    pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
    heightLeft -= pdfHeight

    // 多页
    while (heightLeft > 0) {
      position = heightLeft - imgHeight
      pdf.addPage()
      pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
      heightLeft -= pdfHeight
    }

    const now = new Date()
    const dateStr = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
    pdf.save(`站房状态评估报告_${dateStr}.pdf`)
  } catch (e) {
    console.error('导出PDF失败:', e)
    alert('导出PDF失败，请重试')
  } finally {
    // 恢复原始样式
    el.style.overflow = originals.el.overflow
    el.style.height = originals.el.height
    el.style.maxHeight = originals.el.maxHeight
    if (modal && originals.modal) {
      modal.style.overflow = originals.modal.overflow
      modal.style.maxHeight = originals.modal.maxHeight
    }
  }
}

// 获取实时评分并更新弹窗
async function fetchRealtimeScore() {
  try {
    const res = await aiRequest.get('/getTunnelRunScore', {
      params: { org_id: userStore.org_id || 3 }
    })
    if (res && (res.code === '200' || res.code === 200) && res.data) {
      const d = res.data
      healthModalScore.value = Number(d.totalScore) || 0
      healthModalLevel.value = d.scoreLevel || ''
      healthModalLevelDesc.value = d.levelDesc || ''
      healthLevelDesc.value = d.levelDesc || ''
      nextTick(() => {
        initHealthModalChart()
      })
    }
  } catch (err) {
    console.error('获取实时评分失败:', err)
  }
}

// 初始化弹窗评分曲线
function initHealthModalChart() {
  if (!healthModalChartRef.value) return
  if (healthModalChart) healthModalChart.dispose()
  healthModalChart = echarts.init(healthModalChartRef.value)

  const data = healthModalTrendData.value.length > 0 ? healthModalTrendData.value : [0]
  const times = healthModalTrendTimes.value.length > 0 ? healthModalTrendTimes.value : data.map(() => '')
  const minVal = Math.min(...data)
  const maxVal = Math.max(...data)
  const padding = Math.max((maxVal - minVal) * 0.1, 5)

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        return `${times[p.dataIndex]}<br/>评分: <b>${p.value}</b>分`
      },
      backgroundColor: 'rgba(20, 30, 50, 0.9)',
      borderColor: 'rgba(10, 170, 97, 0.3)',
      textStyle: { color: '#e8f0fe', fontSize: 12 }
    },
    grid: { top: 10, bottom: 10, left: 0, right: 0 },
    xAxis: { type: 'category', show: false, data: data.map((_, i) => i) },
    yAxis: { type: 'value', show: false, min: Math.max(0, minVal - padding), max: maxVal + padding },
    series: [{
      type: 'line',
      data,
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
  healthModalChart.setOption(option)
}

async function fetchHealthModalData() {
  // 获取设备状态统计
  try {
    const totalRes = await listDeviceStatePage({ page: 1, limit: 1, org_id: userStore.org_id })
    if (totalRes.Status === 'Ok') assetStats.value.deviceTotal = totalRes.count || 0
  } catch (e) { console.error('获取设备总数失败:', e) }

  try {
    const faultRes = await listDeviceStatePage({ page: 1, limit: 1, org_id: userStore.org_id, fault_state: 1 })
    if (faultRes.Status === 'Ok') assetStats.value.deviceFault = faultRes.count || 0
  } catch (e) {}

  try {
    const alarmRes = await listDeviceStatePage({ page: 1, limit: 1, org_id: userStore.org_id, alarm_state: 1 })
    if (alarmRes.Status === 'Ok') assetStats.value.deviceAlarm = alarmRes.count || 0
  } catch (e) {}

  assetStats.value.deviceNormal = Math.max(0, assetStats.value.deviceTotal - assetStats.value.deviceFault - assetStats.value.deviceAlarm)

  // 获取资产使用状态统计
  const statusList = [10, 20, 30, 40, 50, 60]
  try {
    const totalRes = await posRequest.get('/asset-info', { params: { current: 1, page: 1, limit: 1 } })
    if (totalRes.Status === 'Ok') assetStats.value.assetTotal = totalRes.count || 0
  } catch (e) {}

  for (const s of statusList) {
    try {
      const res = await posRequest.get('/asset-info', { params: { current: 1, page: 1, limit: 1, usageStatuses: s } })
      assetStats.value[`asset_${s}`] = res.count || 0
    } catch { assetStats.value[`asset_${s}`] = 0 }
  }

  // 获取AI处理统计
  try {
    const diagTotalRes = await posRequest.get('/diagnosis', {
      params: { size: 1, current: 1, source: 'ONE_CLICK' }
    })
    aiStats.value.totalDiagnosis = diagTotalRes.count || 0
  } catch (e) {}

  try {
    const dispatchTotalRes = await posRequest.get('/repair/orders', {
      params: { size: 1, current: 1, dispatchType: 'AUTO', status: '20,30,40,50' }
    })
    aiStats.value.totalDispatch = dispatchTotalRes.count || 0
  } catch (e) {}

  const todayStart = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const todayStr = `${todayStart.getFullYear()}-${pad(todayStart.getMonth() + 1)}-${pad(todayStart.getDate())}T00:00:00`

  try {
    const diagTodayRes = await posRequest.get('/diagnosis', {
      params: { size: 1, current: 1, source: 'ONE_CLICK', createTimeBegin: todayStr }
    })
    aiStats.value.todayDiagnosis = diagTodayRes.count || 0
  } catch (e) {}

  try {
    const dispatchTodayRes = await posRequest.get('/repair/orders', {
      params: { size: 1, current: 1, dispatchType: 'AUTO', status: '20,30,40,50', dispatchTimeBegin: todayStr }
    })
    aiStats.value.todayDispatch = dispatchTodayRes.count || 0
  } catch (e) {}
}

async function fetchRecentAlerts(page = 1, limit = 5) {
  const now = new Date()
  const sevenDaysAgo = new Date(now.getTime() - 1 * 24 * 60 * 60 * 1000)
  const startTime = sevenDaysAgo.getFullYear().toString() +
      String(sevenDaysAgo.getMonth() + 1).padStart(2, '0') +
      String(sevenDaysAgo.getDate()).padStart(2, '0') + '000000'

  const orgId = 3
  const eventLevel = alertFilterType.value === 'fault' ? 3 : 2
  const levelText = alertFilterType.value === 'fault' ? '故障' : '告警'

  try {
    const res = await listDeviceEventPage({ page, limit, org_id: orgId, process_state: 0, event_state: 1, event_level: eventLevel, start_time: startTime })

    let data = []
    let count = 0

    if (res && (res.code === '200' || res.Status === 'Ok')) {
      data = res.data || []
      count = res.count || data.length
    }

    const resultData = data.map(item => ({ ...item, levelText }))

    alertListData.value = resultData
    alertCount.value = count
    alertTotalCount.value = count

    return { count, data: resultData }
  } catch (err) {
    console.error('获取未确认告警事项失败:', err)
    return { count: 0, data: [] }
  }
}

async function changeAlertFilterType(type) {
  alertFilterType.value = type
  alertCurrentPage.value = 1
  await fetchRecentAlerts()
}

async function openAlertModal() {
  isAlertModalOpen.value = true
  alertCurrentPage.value = 1
  await fetchRecentAlerts()
}

async function changeAlertPage(page) {
  alertCurrentPage.value = page
  await fetchRecentAlerts(page, alertPageSize.value)
}

function closeAutoDiagnosisModal() {
  isAutoDiagnosisModalOpen.value = false
  autoDiagnosisAlert.value = null
  aiAnalysisResult.value = null
  aiAnalysisLoading.value = false
  dispatchTaskResult.value = null
  dispatchTaskLoading.value = false
}

// 确认派发任务
async function confirmDispatch(task) {

  //直接返回成功结果
  dispatchTaskLoading.value = true
  dispatchTaskResult.value = null

  // 模拟派发任务
  // setTimeout(() => {
  //   showToast('success', '派发成功')
  //   dispatchTaskResult.value = {
  //     resultExecuteStatus: '1',
  //     resultExecuteUser: '系統Agent',
  //     resultData: '多联机内机1、2、3、4已成功开启，模式设置为制冷，风速和温度设为自动。预计10分钟内温度将下降至舒适范围。',
  //     resultDesc: 'AI执行完成'
  //   }
  //   dispatchTaskLoading.value = false
  //   isAlertModalOpen.value = false
  // }, 1000)
  // return

  try {
    dispatchTaskLoading.value = true
    dispatchTaskResult.value = null
    // 显示加载动画并设置5分钟超时
    dispatchLoadingVisible.value = true
    clearTimeout(dispatchLoadingTimer)
    dispatchLoadingTimer = setTimeout(() => {
      // 超时后切换为后台执行
      dispatchLoadingVisible.value = false
      dispatchRunningInBackground.value = true
      showToast('info', '提示', '任务执行时间较长，已转为后台执行')
    }, 5 * 60 * 1000)

    const res = await aiRequest.post('/dispatch/dispatch', { ...task, dispatchedUser: userStore.user_name })
    // 关闭加载动画和后台执行状态
    dispatchLoadingVisible.value = false
    dispatchRunningInBackground.value = false
    clearTimeout(dispatchLoadingTimer)

    console.log("派发执行", res)
    console.log("task", task)
    if (res.Status === 'Ok') {
      showToast('success', '派发成功')
      if (task.targetAgentId === 'commander' && task.id) {
        console.log(" commander")
        const taskData = await fetchCommanderTaskResult(task.id)
        console.log("taskData", taskData)
        if (taskData) {
          // 关闭自动诊断弹窗，打开任务详情弹窗
          closeAutoDiagnosisModal()
          // 直接使用返回的任务数据显示详情
          selectedTaskDetail.value = taskData
          isTaskDetailModalOpen.value = true
        } else {
          closeAutoDiagnosisModal()
        }
      } else {
        closeAutoDiagnosisModal()
      }
    } else {
      showToast('error', '派发失败', res.message || '')
    }
  } catch (err) {
    console.error('派发任务失败:', err)
    dispatchLoadingVisible.value = false
    dispatchRunningInBackground.value = false
    clearTimeout(dispatchLoadingTimer)
    showToast('success', '后台执行中', err.message || '')
  } finally {
    dispatchTaskLoading.value = false
  }
}

// 关闭dispatch加载动画
function closeDispatchLoading() {
  dispatchLoadingVisible.value = false
  dispatchRunningInBackground.value = false
  clearTimeout(dispatchLoadingTimer)
}

// 获取commander任务执行结果
async function fetchCommanderTaskResult(taskId) {
  try {
    const res = await aiRequest.get(`/dispatch/${taskId}`)
    if (res.Status === 'Ok' && res.data) {
      return res.data
    }
  } catch (err) {
    console.error('获取任务结果失败:', err)
  }
  return null
}

// 取消任务
async function cancelTask(taskId) {
  try {
    const res = await aiRequest.post(`/dispatch/cancel/${taskId}`, {})
    if (res.Status === 'Ok') {
      showToast('success', '已取消')
      closeAutoDiagnosisModal()
    } else {
      showToast('error', '取消失败', res.message || '')
    }
  } catch (err) {
    console.error('取消任务失败:', err)
    showToast('error', '取消异常', err.message || '')
  }
}

async function fetchHealthMetrics() {
  const now = new Date()
  const dateStr = now.getFullYear().toString() + '-' +
      String(now.getMonth() + 1).padStart(2, '0') + '-' +
      String(now.getDate()).padStart(2, '0')
  const todayStart = `${dateStr}T00:00:00`

  try {
    const [totalRes, faultRes, alarmRes, orderRes] = await Promise.all([
      listDeviceStatePage({ page: 1, limit: 1, org_id: 3 }),
      listDeviceStatePage({ page: 1, limit: 1, org_id: 3, fault_state: 1 }),
      listDeviceStatePage({ page: 1, limit: 1, org_id: 3, alarm_state: 1 }),
      posRequest.get('/repair/orders', {
        params: { size: 1, current: 1, createTimeBegin: todayStart }
      })
    ])

    let totalCount = 0
    let faultCount = 0
    let alarmCount = 0

    if (totalRes && (totalRes.code === '200' || totalRes.Status === 'Ok')) {
      totalCount = totalRes.count || 0
    }
    if (faultRes && (faultRes.code === '200' || faultRes.Status === 'Ok')) {
      faultCount = faultRes.count || 0
    }
    if (alarmRes && (alarmRes.code === '200' || alarmRes.Status === 'Ok')) {
      alarmCount = alarmRes.count || 0
    }

    const healthRate = totalCount > 0 ? ((totalCount - faultCount - alarmCount) / totalCount * 100).toFixed(1) : 0

    let orderCount = 0
    if (orderRes && (orderRes.Status === 'Ok' || orderRes.code === '200')) {
      orderCount = orderRes.count || 0
    }

    healthMetrics.value = {
      healthRate: healthRate,
      faultDevices: faultCount,
      alarmDevices: alarmCount,
      todayOrders: orderCount
    }
  } catch (err) {
    console.error('获取健康指标失败:', err)
  }
}

async function fetchEnergyMetrics() {
  const now = new Date()
  const year = now.getFullYear().toString()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = now.getDate()
  const orgId = userStore.org_id
  const subCid = userStore.subarea_cid

  try {
    // 获取月度能耗数据
    const monthRes = await iotRequest.get('/report/listData', {
      params: {
        orgId: orgId,
        typeCids: '0.001',
        subCids: subCid,
        timeType: 'day',
        dateTimes: `${year}${month}`
      }
    })

    let todayEnergy = 0
    let yesterdayEnergy = 0
    let monthEnergy = 0

    if (monthRes && (monthRes.code === '200' || monthRes.Status === 'Ok') && monthRes.sub) {
      const monthKey = `${year}${month}`
      const subData = monthRes.sub[monthKey]
      if (subData && subData[subCid]) {
        const dataArray = subData[subCid]
        // 本月能耗：累加所有数据
        monthEnergy = dataArray.reduce((sum, val) => sum + (val || 0), 0)
        // 今日能耗：数组索引为 day-1
        todayEnergy = dataArray[day - 1] || 0
        // 昨日能耗：数组索引为 day-2
        if (day > 1) {
          yesterdayEnergy = dataArray[day - 2] || 0
        }
      }
    }

    // 计算较昨日比较
    let yesterdayCompare = { value: 0, isNormal: false }
    if (day === 1 || yesterdayEnergy === 0) {
      // 如果是1号或者昨日能耗为0，显示能耗水平正常
      yesterdayCompare = { value: 0, isNormal: true }
    } else {
      // 计算百分比变化
      yesterdayCompare = {
        value: ((todayEnergy - yesterdayEnergy) / yesterdayEnergy * 100).toFixed(1),
        isNormal: false
      }
    }

    // 获取年度能耗数据
    const yearRes = await iotRequest.get('/report/listData', {
      params: {
        orgId: orgId,
        subCids: subCid,
        typeCids: '0.001',
        timeType: 'year',
        dateTimes: year
      }
    })

    let yearEnergy = 0
    if (yearRes && (yearRes.code === '200' || yearRes.Status === 'Ok') && yearRes.sub) {
      const yearKey = year
      const subData = yearRes.sub[yearKey]
      if (subData && subData[subCid]) {
        const dataArray = subData[subCid]
        yearEnergy = dataArray.reduce((sum, val) => sum + (val || 0), 0)
      }
    }

    energyMetrics.value = {
      todayEnergy: todayEnergy.toFixed(2),
      yesterdayCompare: yesterdayCompare,
      monthEnergy: monthEnergy.toFixed(2),
      yearEnergy: yearEnergy.toFixed(2)
    }
  } catch (err) {
    console.error('获取能耗指标失败:', err)
  }
}

function closeAlertModal() {
  isAlertModalOpen.value = false
}

// 状态颜色映射
const STATUS_COLORS = {
  ok: { color: '#08ae66', bgColor: 'rgba(8,174,102,0.15)' },
  warn: { color: '#ff6a00', bgColor: 'rgba(255,106,0,0.15)' }
}

// 获取故障/告警设备列表
async function fetchDeviceList(orgId, subareaCid, stateType, limit = 5) {
  try {
    const res = await listDeviceStatePage({
      page: 1,
      limit,
      org_id: orgId,
      subarea_cid: subareaCid,
      [stateType]: 1,
      addChangeStateTime: 1
    })
    if (res && (res.code === '200' || res.Status === 'Ok')) {
      return res.data || []
    }
  } catch (err) {
    console.error(`获取${stateType}设备列表失败:`, err)
  }
  return []
}

// 获取设备实时数据
async function fetchDeviceData(deviceId) {
  try {
    const res = await iotRequest.get('/base/device/data/listOneDeviceDataOrState', {
      params: {
        page: 1,
        limit: 20,
        device_id: deviceId
      }
    })
    if (res && (res.code === '200' || res.Status === 'Ok')) {
      return res.data || []
    }
  } catch (err) {
    console.error(`获取设备${deviceId}数据失败:`, err)
  }
  return []
}

// 调用后端AI分析分区态势（单条异常，不缓存）
async function fetchZoneAnalysis(payload) {
  try {
    const res = await aiRequest.post('/analysis/zoneAnalysis', payload)
    if (res && (res.code === '200' || res.Status === 'Ok') && res.data) {
      return res.data
    }
  } catch (err) {
    console.error('[分区态势] AI分析失败:', err)
  }

  return null
}

// 调用后端AI全分区综合分析
async function fetchAllZoneAnalysis() {
  if (!zoneAreas.value || zoneAreas.value.length === 0) return

  const zones = zoneAreas.value.map(zone => {
    const snapshot = buildZoneCardSnapshot(zone)
    return {
      name: zone.name,
      subareaCid: zone._rawSubareaCid,
      status: snapshot.status,
      envScore: snapshot.envScore,
      totalScore: snapshot.totalScore,
      envStatus: snapshot.envStatus,
      deviceStatus: zone.deviceStatus,
      passengerDensity: zone.passengerDensity,
      passengerStatus: zone.passengerStatus,
      energyToday: zone.energyToday,
      energyStatus: zone.energyStatus,
      suggestion: zone.suggestion || snapshot.primaryRiskText,
      desc: zone._desc || [],
      envAlarms: (zone._anomalyEnvSnapshot || []).map(a => ({
        item: a.item,
        value: a.value,
        status: a.status
      })),
      anomalyDevices: (zone.anomalyDevices || []).map(d => ({
        name: d.name,
        type: d.type,
        status: d.status,
        location: d.location
      }))
    }
  })

  try {
    const res = await aiRequest.post('/analysis/allZoneAnalysis', { zones })
    if (res && (res.code === '200' || res.Status === 'Ok') && res.data) {
      aiFullText.value = res.data
      startAiTypewriter()
    }
  } catch (err) {
    console.error('AI全分区综合分析失败:', err)
  }
}

// 获取派发任务列表
async function fetchDispatchTasks() {
  try {
    const res = await aiRequest.get('/dispatch/list', {
      params: { pageNum: 1, pageSize: 20 }
    })
    if (res && (res.code === '200' || res.code === 200) && res.data) {
      const data = res.data
      dispatchTaskList.value = data.records || []
    }
  } catch (err) {
    console.error('获取派发任务列表失败:', err)
  }
}

// 获取知识库统计
async function fetchKnowledgeStats() {
  try {
    const res = await ragflowRequest.get('/ragflowStatistics')
    if (res && res.success && res.data) {
      const data = res.data
      knowledgeStats.value = {
        queryCount: data.queryCount || 0,
        hitRate: data.hitRate || 0,
        knowledgeFiles: data.knowledgeFileCount || [],
        totalFiles: data.knowledgeFileTotal || 0
      }
      nextTick(() => {
        renderKnowledgeChart()
      })
    }
  } catch (err) {
    console.error('获取知识库统计失败:', err)
  }
}

// 获取Agent协作统计
async function fetchAgentCollabStats() {
  try {
    const res = await aiRequest.get('/dispatch/agent-collab-stats')
    if (res && (res.code === '200' || res.code === 200) && res.data) {
      agentCollabStats.value = res.data
    }
  } catch (err) {
    console.error('获取Agent协作统计失败:', err)
  }
}

// 获取Agent协作任务列表（分页）
async function fetchAgentCollabTasks() {
  try {
    const res = await aiRequest.get('/dispatch/list', {
      params: { pageNum: agentCollabPage.value, pageSize: agentCollabPageSize }
    })
    if (res && (res.code === '200' || res.code === 200) && res.data) {
      agentCollabTaskList.value = res.data.records || []
      agentCollabTotal.value = res.data.total || 0
      nextTick(() => animateTaskCards())
    }
  } catch (err) {
    console.error('获取Agent协作任务列表失败:', err)
  }
}

// 获取算力实时指标
async function fetchComputingPower() {
  try {
    const res = await aiRequest.get('/computing-power')
    if (res && (res.code === '200' || res.Status === 'Ok') && res.data) {
      computingPower.value = res.data
    }
  } catch (err) {
    console.error('获取算力实时指标失败:', err)
  }
}

// 获取客流Agent数据
async function fetchKeliuAgentData() {
  try {
    const [overviewRes, stationRes, crowdedRes] = await Promise.all([
      keliuRequest.get('/v1/monitor/overviewCards'),
      iotRequest.get('/passenger-flow/station/realtime'),
      iotRequest.get('/passenger-flow/station/crowded')
    ])

    // 新接口数据
    if (overviewRes && (overviewRes.code === 200) && overviewRes.data) {
      const items = {}
      overviewRes.data.forEach(item => { items[item.name] = item.value })
      keliuAgentData.value = {
        ...keliuAgentData.value,
        alertCount: items['今日告警数量'] ?? '-',
        severeCount: items['今日严重事项数量'] ?? '-',
        normalCount: items['今日一般事项数量'] ?? '-',
        notifyCount: items['今日通知事项数量'] ?? '-',
        operationStatus: items['当前运营状态'] ?? '-',
        passengerFlow: items['当前客流量'] ?? '-',
        trainCount: items['当前车流量'] ?? '-',
        crowdedCount: items['当前拥挤区域个数'] ?? '-'
      }
    }

    // 实时客流概览数据
    const stationData = stationRes?.data
    const crowdedData = crowdedRes?.data || []
    if (stationData) {
      const isCrowded = crowdedData.length > 0
      keliuAgentData.value.passengerCount = stationData.stationCurrPeo != null ? `${stationData.stationCurrPeo}人` : '-'
      keliuAgentData.value.congestionText = isCrowded ? `${crowdedData.length}处拥堵` : '正常'
      keliuAgentData.value.congestionClass = isCrowded ? 'orange-text' : 'green-text'
    }
  } catch (err) {
    console.error('获取客流Agent数据失败:', err)
  }
}

// 获取能源管理Agent统计数据
async function fetchEmsAgentData() {
  try {
    const res = await emsRequest.get('/api/collab/statistics/summary')
    if (res && res.code === 200 && res.data) {
      const d = res.data
      emsAgentData.value = {
        todayTotal: d.todayTotal ?? 0,
        yesterdayCompareRate: d.yesterdayCompareRate ?? 0,
        todayDecisionPassRate: d.todayDecisionPassRate ?? 0,
        todayDecisionSuccessRate: d.todayDecisionSuccessRate ?? 0,
        total: d.total ?? 0,
        decisionPassRate: d.decisionPassRate ?? 0,
        decisionSuccessRate: d.decisionSuccessRate ?? 0,
        timeoutCount: d.timeoutCount ?? 0,
        timeoutRate: d.timeoutRate ?? 0
      }
    }
  } catch (err) {
    console.error('获取能源管理Agent数据失败:', err)
  }
}

// 获取设备事件统计
async function fetchEventStats() {
  const orgId = userStore.org_id || 3
  const startTime = '20260510000000'

  try {
    const remoteRes = await listDeviceEventPage({ limit: 1, org_id: orgId, start_time: startTime, event_level: 0 })
    if (remoteRes.Status === 'Ok') eventStats.value.remote = remoteRes.count || 0
  } catch (e) { console.error('获取遥控事项失败:', e) }

  try {
    const notifRes = await listDeviceEventPage({ limit: 1, org_id: orgId, start_time: startTime, event_level: 1 })
    if (notifRes.Status === 'Ok') eventStats.value.notification = notifRes.count || 0
  } catch (e) { console.error('获取通知事项失败:', e) }

  try {
    const alarmRes = await listDeviceEventPage({ limit: 1, org_id: orgId, start_time: startTime, event_level: 2 })
    if (alarmRes.Status === 'Ok') eventStats.value.alarm = alarmRes.count || 0
  } catch (e) { console.error('获取告警事项失败:', e) }

  try {
    const faultRes = await listDeviceEventPage({ limit: 1, org_id: orgId, start_time: startTime, event_level: 3 })
    if (faultRes.Status === 'Ok') eventStats.value.fault = faultRes.count || 0
  } catch (e) { console.error('获取故障事项失败:', e) }
}

// 压力等级样式类
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

// 任务卡片入场动画
function animateTaskCards() {
  const cards = document.querySelectorAll('.task-card')
  if (cards.length === 0) return
  gsap.fromTo(cards,
      { opacity: 0, y: 12, scale: 0.97 },
      { opacity: 1, y: 0, scale: 1, duration: 0.35, stagger: 0.06, ease: 'power2.out' }
  )
}

// 派发任务
async function handleDispatch(task) {
  // 如果目标Agent是设备运维Agent，跳转到运维Agent页面并自动触发协作记录的派发
  if (task.targetAgentId === 'ops-agent') {
    router.push({
      path: '/operations1',
      query: {
        autoDispatchTask: 'true',
        taskId: task.id || ''
      }
    })
    return
  }

  try {
    const taskBody = { ...task, dispatchedUser: userStore.user_name }
    const res = await aiRequest.post('/dispatch/dispatch', taskBody)
    if (res && (res.code === '200' || res.code === 200)) {
      task.dispatched = 1
      // 按钮动画反馈
      gsap.to(`[data-task-id="${task.id}"]`, {
        scale: 1.05,
        duration: 0.15,
        yoyo: true,
        repeat: 1,
        ease: 'power2.out'
      })
      showToast('success', '派发成功', `任务已派发至${task.targetAgentName}`)
      // 如果目标Agent是能源管理Agent，打开能源管理弹窗
      if (task.targetAgentId === 'energy-agent') {
        openEnergyAgent()
        return
      }
      // await fetchAgentCollabStats()
      // nextTick(() => initAgentCollabChart())
    } else {
      showToast('error', '派发失败', res?.msg || '未知错误')
    }
  } catch (err) {
    showToast('error', '后台执行中', err.message || '网络异常')
    console.error('派发任务失败:', err)
  }
}

// 取消任务
async function handleCancel(task) {
  try {
    const res = await aiRequest.post(`/dispatch/cancel/${task.id}`, { dispatchedUser: userStore.user_name })
    if (res && (res.code === '200' || res.code === 200)) {
      task.dispatched = 2
      // 取消动画：行淡出效果
      gsap.to(`[data-task-id="${task.id}"]`, {
        opacity: 0.4,
        duration: 0.3,
        ease: 'power2.out'
      })
      showToast('success', '取消成功', '任务已取消')
      await fetchAgentCollabStats()
      nextTick(() => initAgentCollabChart())
    } else {
      showToast('error', '取消失败', res?.msg || '未知错误')
    }
  } catch (err) {
    showToast('error', '取消失败', err.message || '网络异常')
    console.error('取消任务失败:', err)
  }
}

let agentCollabChartInstance = null
const agentCollabChartRef = ref(null)

function initAgentCollabChart() {
  if (!agentCollabChartRef.value) return
  if (!agentCollabChartInstance) {
    agentCollabChartInstance = echarts.init(agentCollabChartRef.value)
  }
  const stats = agentCollabStats.value.dailyStats || []
  const agentNames = agentCollabStats.value.agentNames || []
  const dates = stats.map(s => s.date)

  if (dates.length === 0) return

  // 构建每个Agent的生成任务序列和目标任务序列
  const series = []
  const colors = ['#6366f1', '#06b6d4', '#22c55e', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6']

  agentNames.forEach((name, idx) => {
    series.push({
      name: name + '(生成)',
      type: 'bar',
      stack: 'generated',
      data: stats.map(s => (s.agentGenerated && s.agentGenerated[name]) || 0),
      itemStyle: { color: colors[idx % colors.length] }
    })
  })

  agentNames.forEach((name, idx) => {
    series.push({
      name: name + '(目标)',
      type: 'bar',
      stack: 'target',
      data: stats.map(s => (s.agentTarget && s.agentTarget[name]) || 0),
      itemStyle: { color: colors[(idx + 4) % colors.length] }
    })
  })

  // 如果没有Agent数据，使用简单的生成/目标模式
  if (series.length === 0) {
    series.push(
        { name: '生成任务', type: 'bar', data: stats.map(s => s.generatedCount || 0), itemStyle: { color: '#6366f1' } },
        { name: '目标任务', type: 'bar', data: stats.map(s => s.targetCount || 0), itemStyle: { color: '#06b6d4' } }
    )
  }

  const option = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: {
      data: series.map(s => s.name),
      textStyle: { fontSize: 10, color: 'rgba(255,255,255,0.6)' },
      top: 0,
      type: 'scroll'
    },
    grid: { top: 35, bottom: 30, left: 40, right: 10 },
    xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 10, color: 'white' } },
    yAxis: { type: 'value', axisLabel: { fontSize: 10, color: 'white' }, splitLine: { lineStyle: { color: 'rgba(255,255,255,0.06)' } } },
    series
  }
  agentCollabChartInstance.setOption(option, true)
  // 确保图表尺寸正确
  agentCollabChartInstance.resize()
}

// 获取运维Agent KPI数据
async function fetchOpsKpi() {
  try {
    // 累计AI诊断
    const diagRes = await posRequest.get('/diagnosis', {
      params: { size: 1, current: 1, source: 'ONE_CLICK' }
    })
    opsKpi.value.totalDiagnosis = diagRes.count || 0

    // 累计AI派单
    const dispatchRes = await posRequest.get('/repair/orders', {
      params: { size: 1, current: 1, dispatchType: 'AUTO', status: '20,30,40,50' }
    })
    opsKpi.value.totalDispatch = dispatchRes.count || 0

    // 累计巡检
    const insRes = await posRequest.get('/ins/orders', {
      params: { size: 1, current: 1, orderNo: 1 }
    })
    opsKpi.value.totalInspections = insRes.count || 0

    // 累计工单
    const repairRes = await posRequest.get('/repair/orders', {
      params: { size: 1, current: 1 }
    })
    opsKpi.value.totalWorkOrders = repairRes.count || 0
  } catch (err) {
    console.error('获取运维KPI数据失败:', err)
  }
}

function getTodayStart() {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T00:00:00`
}

async function fetchKpiStats() {
  try {
    const todayStart = getTodayStart()
    // 今日诊断次数
    const diagTodayRes = await posRequest.get('/diagnosis', {
      params: { size: 1, current: 1, source: 'ONE_CLICK', createTimeBegin: todayStart }
    })
    kpiStats.value.todayDiagnosis = diagTodayRes.count || 0

    // 今日派单次数
    const dispatchTodayRes = await posRequest.get('/repair/orders', {
      params: { size: 1, current: 1, dispatchType: 'AUTO', status: '20,30,40,50', dispatchTimeBegin: todayStart }
    })
    kpiStats.value.todayDispatch = dispatchTodayRes.count || 0
  } catch (err) {
    console.error('获取KPI统计数据失败:', err)
  }
}

const opsRingGradient = computed(() => {
  const dispatch = kpiStats.value.todayDispatch
  const diagnosis = kpiStats.value.todayDiagnosis
  const total = dispatch + diagnosis
  if (total === 0) return 'conic-gradient(#e6edf6 100%)'

  const dispatchPct = (dispatch / total) * 100
  const diagnosisPct = (diagnosis / total) * 100

  return `conic-gradient(#156dff 0% ${dispatchPct}%, #10b86b ${dispatchPct}% ${dispatchPct + diagnosisPct}%)`
})

const todayAdoptionRate = computed(() => {
  if (kpiStats.value.todayDiagnosis === 0) return 0
  return Math.round((kpiStats.value.todayDispatch / kpiStats.value.todayDiagnosis) * 100)
})

const predictionRingGradient = computed(() => {
  const data = keliuAgentData.value
  const severe = Number(data.severeCount) || 0
  const normal = Number(data.normalCount) || 0
  const notify = Number(data.notifyCount) || 0
  const total = severe + normal + notify
  if (total === 0) return 'conic-gradient(#e6edf6 100%)'

  const colors = ['#ef4444', '#f59e0b', '#60a5fa']
  const counts = [severe, normal, notify]
  let segments = []
  let cumulative = 0

  counts.forEach((count, i) => {
    const pct = (count / total) * 100
    segments.push(`${colors[i]} ${cumulative}% ${cumulative + pct}%`)
    cumulative += pct
  })

  return `conic-gradient(${segments.join(', ')})`
})


const knowledgeColorPalette = [
  '#156dff', '#1fc3d7', '#10b86b', '#ff9933',
  '#7b63f0', '#ff4444', '#20c4d6', '#e91e63',
  '#9c27b0', '#3f51b5', '#009688', '#cddc39'
]

function getKnowledgeColor(name, index = 0) {
  return  knowledgeColorPalette[index % knowledgeColorPalette.length]
}

function renderKnowledgeChart() {
  if (!knowledgeChartRef.value) return

  if (!knowledgeChartInstance) {
    knowledgeChartInstance = echarts.init(knowledgeChartRef.value)
  }

  const files = knowledgeStats.value.knowledgeFiles
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    series: [
      {
        type: 'pie',
        radius: ['50%', '75%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 4,
          borderColor: 'rgba(0,0,0,0.2)',
          borderWidth: 1
        },
        label: {
          show: false
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 12,
            fontWeight: 'bold'
          }
        },
        data: files.map((f, i) => ({
          name: f.name,
          value: f.count,
          itemStyle: { color: getKnowledgeColor(f.name, i) }
        }))
      }
    ]
  }

  knowledgeChartInstance.setOption(option)
}

// 判断数据是否异常
function isDataAbnormal(dataItem) {
  if (dataItem.state === 0) return false
  if (dataItem.event_level > 1) {
    return true
  }
  return dataItem.state !== 0
}

// 获取异常状态描述
function getAbnormalDesc(dataItem) {
  if (dataItem.data_type === '1' || dataItem.data_type === 1) {
    if (dataItem.state === 1) return '越上限'
    if (dataItem.state === 10) return '越下限'
  }
  return '异常'
}

// 将 API 返回的分区态势数据转换为前端需要的格式（异步版本）
async function transformZoneData(apiData) {
  const orgId = userStore.org_id
  const results = []

  for (const item of apiData) {
    // 解析坐标
    const coordParts = (item.subarea_coord || '0,0,0').split(',').map(Number)
    const pos = { x: coordParts[0] || 0, y: coordParts[1] || 0, z: coordParts[2] || 0 }

    // 判断状态类型
    const isWarn = item.subarea_alter === '1'
    const statusType = isWarn ? 'warn' : 'ok'
    const statusColors = STATUS_COLORS[statusType]

    // 构建 desc 数组
    const desc = []
    const dev = item.deviceStateCount || {}
    if (dev.fault_total > 0) desc.push(`设备故障${dev.fault_total}台`)
    if (dev.alarm_total > 0) desc.push(`告警${dev.alarm_total}台`)

    // 环境异常（environment 为 { totalDevices, abnormalDevices }）
    const envAbnormal = Number(item.environment?.abnormalDevices || 0)
    if (envAbnormal > 0) {
      desc.push(`环境异常${envAbnormal}处`)
    }

    if (item.energy?.status === '1') desc.push('能耗异常')
    if (item.keliu?.status === '1') desc.push('客流异常')
    if (desc.length === 0) desc.push('正常运行')

    // 设备状态描述
    let deviceStatus = `${dev.total || 0}台全正常`
    let deviceStatusClass = 'green-text'
    if (dev.fault_total > 0 || dev.alarm_total > 0) {
      const parts = []
      if (dev.fault_total > 0) parts.push(`${dev.fault_total}台故障`)
      if (dev.alarm_total > 0) parts.push(`${dev.alarm_total}台告警`)
      deviceStatus = parts.join(' ')
      deviceStatusClass = dev.fault_total > 0 ? 'red-text' : 'orange-text'
    }

    // 客流（后端 subareaState 已移除 keliu 聚合，首个区域在 fetchZoneAreas 中单独注入）
    const passengerDensity = item.keliu?.value ? `${item.keliu.value}人/㎡` : '-'
    const passengerStatus = item.keliu?.status === '0' ? '正常' : (item.keliu ? '注意' : '-')
    const passengerStatusClass = item.keliu?.status === '0' ? 'green-text' : (item.keliu ? 'orange-text' : '')

    // 能耗
    const energy = item.energy || {}
    const energyToday = energy.value ? `${energy.value}kWh` : '-'
    const energyStatus = energy.status === '0' ? '正常' : '注意'
    const energyStatusClass = energy.status === '0' ? 'blue-text' : 'orange-text'

    // 状态描述
    const status = isWarn ? '注意' : '正常'
    const statusClass = isWarn ? 'orange-text' : 'blue-text'
    const boxClass = isWarn ? 'orange-box' : 'green-box'
    const wrapperColor = isWarn ? 'var(--orange)' : 'var(--green)'
    const pillClass = isWarn ? 'warn' : 'ok'

    // 故障/告警设备基本信息（不请求实时数据，点击分区卡片时才获取）
    const anomalyDevices = []
    const subareaCid = item.subarea_cid

    if (dev.fault_total > 0 || dev.alarm_total > 0) {
      // 仅保存设备基本信息，不请求实时数据
      if (dev.fault_total > 0) {
        const faultDevices = await fetchDeviceList(orgId, subareaCid, 'fault_state', dev.fault_total)
        for (const devItem of faultDevices) {
          anomalyDevices.push({
            id: devItem.id,
            name: devItem.device_name,
            type: devItem.device_type || '未知设备',
            currentData: [{ label: '状态', value: devItem.event_info || '故障' }],
            status: '故障',
            location: devItem.location || '待确认',
            selected: false,
            blink: true,
            _loaded: false  // 标记未加载实时数据
          })
        }
      }

      if (dev.alarm_total > 0) {
        const alarmDevices = await fetchDeviceList(orgId, subareaCid, 'alarm_state', dev.alarm_total)
        for (const devItem of alarmDevices) {
          anomalyDevices.push({
            id: devItem.id,
            name: devItem.device_name,
            type: devItem.device_type || '未知设备',
            currentData: [{ label: '状态', value: devItem.event_info || '告警' }],
            status: '告警',
            location: devItem.location || '待确认',
            selected: false,
            blink: true,
            _loaded: false
          })
        }
      }
    }

    // AI 分析相关字段初始化为空，点击分区弹窗时才调用
    let zoneAnalysis = ''
    let suggestion = ''
    let suggestionClass = ''
    let envScore = ''
    let envStatus = ''
    let envStatusClass = ''
    const dispatchTasks = []
    // 缓存原始数据，用于弹窗时调用 AI 分析
    const rawSubareaCid = item.subarea_cid
    const rawIsWarn = isWarn
    const rawDev = { ...dev }
    const rawEnvironment = item.environment ? JSON.parse(JSON.stringify(item.environment)) : null
    const rawKeliu = item.keliu ? { ...item.keliu } : null
    const rawEnergy = item.energy ? { ...item.energy } : null

    results.push({
      name: item.subarea_name,
      heat: {
        color: statusColors.color,
        bgColor: statusColors.bgColor,
        pos,
        type: statusType,
        desc
      },
      status,
      statusClass,
      boxClass,
      wrapperColor,
      pillClass,
      expandedEnv: true,
      expandedDevice: true,
      env: [],
      envScore,
      envStatus,
      envStatusClass,
      anomalyEnv: [],
      deviceStatus,
      deviceStatusClass,
      anomalyDevices,
      passengerDensity,
      passengerStatus,
      passengerStatusClass,
      energyToday,
      energyStatus,
      energyStatusClass,
      suggestion,
      suggestionClass,
      zoneAnalysis,
      dispatchTasks,
      // 原始数据缓存，用于弹窗时 AI 分析
      _rawSubareaCid: rawSubareaCid,
      _rawIsWarn: rawIsWarn,
      _rawDev: rawDev,
      _rawEnvironment: rawEnvironment,
      _rawKeliu: rawKeliu,
      _rawEnergy: rawEnergy,
      _desc: [...desc],
      _anomalyEnvSnapshot: []
    })
  }

  return results
}

async function fetchZoneAreas() {
  const orgId = userStore.org_id

  // 先从缓存加载旧数据，避免空白
  try {
    const cached = localStorage.getItem('zoneAreas_cache')
    if (cached) {
      const parsed = JSON.parse(cached)
      zoneAreas.value = parsed
    }
  } catch (e) {
    // 忽略缓存解析错误
  }

  zoneAreasLoading.value = true
  try {
    const res = await aiRequest.get('/subareaState', {
      params: { org_id: orgId }
    })

    if (res && (res.code === '200' || res.Status === 'Ok') && res.data) {
      const previousByCid = new Map(zoneAreas.value.map(zone => [zone._rawSubareaCid || zone.name, zone]))
      const transformed = await transformZoneData(res.data)

      transformed.forEach(zone => {
        const previous = previousByCid.get(zone._rawSubareaCid || zone.name)
        if (previous?.zoneAnalysis || (previous?.dispatchTasks && previous.dispatchTasks.length > 0)) {
          zone.zoneAnalysis = previous.zoneAnalysis || zone.zoneAnalysis
          zone.suggestion = previous.suggestion || zone.suggestion
          zone.suggestionClass = previous.suggestionClass || zone.suggestionClass
          zone.envScore = previous.envScore || zone.envScore
          zone.envStatus = previous.envStatus || zone.envStatus
          zone.envStatusClass = previous.envStatusClass || zone.envStatusClass
          zone.dispatchTasks = previous.dispatchTasks || []
        }
      })
      zoneAreas.value = transformed
      // 缓存新数据
      try {
        localStorage.setItem('zoneAreas_cache', JSON.stringify(transformed))
      } catch (e) {
        // 忽略缓存写入错误
      }
      if (focusedZone.value) {
        const focusedKey = focusedZone.value._rawSubareaCid || focusedZone.value.name
        const refreshedFocus = transformed.find(zone => (zone._rawSubareaCid || zone.name) === focusedKey)
        if (refreshedFocus) {
          focusedZone.value = refreshedFocus
        } else {
          clearFocusedZone()
        }
      }
      // 数据加载完成后重新渲染 Canvas 2D 视图
      if (zoneViewMode.value === 'model') {
        nextTick(() => {
          setTimeout(() => {
            mainIslands.stop()
            mainIslands.start()
          }, 100)
        })
      }
    }
  } catch (err) {
    console.error('获取分区态势数据失败:', err)
  } finally {
    zoneAreasLoading.value = false
  }
}

function formatAlertTime(timeStr) {
  if (!timeStr) return timeStr
  const str = String(timeStr)
  if (str.length < 14) return str
  const year = str.substring(0, 4)
  const month = str.substring(4, 6)
  const day = str.substring(6, 8)
  const hour = str.substring(8, 10)
  const minute = str.substring(10, 12)
  return `${year}-${month}-${day} ${hour}:${minute}`
}

function getZoneNameByCid(subareaCid) {
  if (!subareaCid) return ''
  const zone = zoneAreas.value.find(z => z._rawSubareaCid === subareaCid)
  return zone ? zone.name : ''
}

function getAlertLocation(item) {
  const zoneName = getZoneNameByCid(item.subarea_cid)
  const location = item.location
  if (zoneName && location) return `${zoneName} - ${location}`
  if (zoneName) return zoneName
  if (location) return location
  return ''
}

// 已知：标记事项为已处理
async function handleAlertKnown(item) {
  if (!item.id) return
  try {
    const formData = new FormData()
    formData.append('ids', `["${item.id}"]`)
    const res = await iotRequest.post('/base/device/event/processEvent', formData)
    if (res && (res.code === '200' || res.Status === 'Ok')) {
      // 刷新告警列表
      await fetchRecentAlerts()
    } else {
      console.error('已知操作失败:', res)
    }
  } catch (err) {
    console.error('已知操作异常:', err)
  }
}

// 转问题：上报为问题工单
async function handleAlertToIssue(item) {
  if (!item.device_name) return

  const issueLevel = item.event_level === 3 ? 'CRITICAL' : 'HIGH'
  let foundTime = ''
  if (item.happen_time && item.happen_time.length >= 19) {
    foundTime = item.happen_time.replace('T', ' ')
  } else {
    const now = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    foundTime = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  }

  try {
    const command = {
      issueTitle: item.device_name,
      issueContent: item.event_info || item.type || '设备告警',
      issueLevel: issueLevel,
      assetId: item.device_id || '',
      foundTime,
      userId: userStore.user_id,
      userName: userStore.user_name,
      orgCid: userStore.org_cid
    }
    const commandBlob = new Blob([JSON.stringify(command)], { type: 'application/json' })
    const formData = new FormData()
    formData.append('command', commandBlob, 'command.json')

    const res = await posRequest.post('/repair/issue', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      transformRequest: [(data, headers) => {
        delete headers['Content-Type']
        return data
      }]
    })
    if (res.Status !== 'Ok' || !res.data) {
      console.error('转问题失败:', res)
      return
    }

    // 转问题成功后，跳转到运维Agent页面并自动触发AI诊断
    router.push({ path: '/operations1', query: { autoDiagnosis: 'true' } })
  } catch (err) {
    console.error('转问题异常:', err)
  }
}

function goToOperations() {
  router.push('/operations1')
}

async function openDeviceAnalysis() {
  isDeviceAnalysisOpen.value = true

  try {
    const res = await listDeviceStatePage({ page: 1, limit: 3, org_id: 3, fault_state: 1 })
    if (res && (res.code === '200' || res.Status === 'Ok') && res.data && res.data.length > 0) {
      const faultCount = res.data.filter(d => d.fault === 1).length
      const alarmCount = res.data.filter(d => d.alarm === 1).length
      deviceAnalysisData.value.faultDevices = faultCount
      deviceAnalysisData.value.alarmDevices = alarmCount
      deviceAnalysisData.value.totalDevices = res.data.length
      deviceAnalysisData.value.keyDevices = res.data.map(d => ({
        icon: d.fault === 1 ? 'fan' : 'cabinet',
        title: `${d.device_name}${d.fault === 1 ? ' 故障' : d.alarm === 1 ? ' 告警' : ''}`,
        area: d.subarea_cid || d.subarea_id || '未知区域',
        severity: d.fault === 1 ? '紧急' : d.alarm === 1 ? '较高' : '一般',
        severityClass: d.fault === 1 ? 'sev-danger' : d.alarm === 1 ? 'sev-warning' : 'sev-normal',
        detail: `设备ID: ${d.id}${d.fault === 1 ? ' | 故障状态' : ''}${d.alarm === 1 ? ' | 告警中' : ''}`,
        action: 'AI诊断'
      }))
    }
  } catch (err) {
    console.error('获取异常设备列表失败:', err)
  }

  nextTick(() => {
    setTimeout(() => {
      initDeviceHealthScoreChart()
      initDeviceTrendChart()
    }, 200)
  })
}

function closeDeviceAnalysis() {
  isDeviceAnalysisOpen.value = false
  if (deviceHealthScoreChart) {
    deviceHealthScoreChart.dispose()
    deviceHealthScoreChart = null
  }
  if (deviceTrendChart) {
    deviceTrendChart.dispose()
    deviceTrendChart = null
  }
}

function initDeviceHealthScoreChart() {
  const container = deviceHealthScoreChartRef.value
  if (!container) return
  if (deviceHealthScoreChart) {
    deviceHealthScoreChart.dispose()
  }
  deviceHealthScoreChart = echarts.init(container)
  const score = deviceAnalysisData.value.healthScore
  const option = {
    series: [{
      type: 'gauge',
      startAngle: 200,
      endAngle: -20,
      min: 0,
      max: 100,
      radius: '90%',
      center: ['50%', '55%'],
      splitNumber: 10,
      itemStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 1, y2: 0,
          colorStops: [
            { offset: 0, color: '#ff4646' },
            { offset: 0.5, color: '#ff6a00' },
            { offset: 1, color: '#08ae66' }
          ]
        }
      },
      progress: {
        show: true,
        roundCap: true,
        width: 12
      },
      pointer: { show: false },
      axisLine: {
        roundCap: true,
        lineStyle: { width: 12, color: [[1, 'rgba(255,255,255,0.06)']] }
      },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false },
      title: { show: false },
      detail: {
        valueAnimation: true,
        fontSize: 32,
        fontWeight: 800,
        color: '#00b4ff',
        offsetCenter: [0, '0%'],
        formatter: '{value}'
      },
      data: [{ value: score }]
    }]
  }
  deviceHealthScoreChart.setOption(option)
}

function initDeviceTrendChart() {
  const container = deviceTrendChartRef.value
  if (!container) return
  if (deviceTrendChart) {
    deviceTrendChart.dispose()
  }
  deviceTrendChart = echarts.init(container)
  const trendData = deviceAnalysisData.value.trendData
  const dates = trendData.map(d => d.date)
  const anomalyData = trendData.map(d => d.anomaly)
  const alarmData = trendData.map(d => d.alarm)
  const faultData = trendData.map(d => d.fault)

  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(10,22,40,0.95)',
      borderColor: 'rgba(0,180,255,0.3)',
      borderWidth: 1,
      textStyle: { color: 'rgba(255,255,255,0.85)', fontSize: 12 },
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(0,180,255,0.05)' } }
    },
    legend: {
      data: ['异常总数', '告警数量', '故障数量'],
      textStyle: { color: 'white', fontSize: 11 },
      top: 0,
      right: 0,
      itemWidth: 12,
      itemHeight: 3,
      itemGap: 16
    },
    grid: {
      left: 40,
      right: 16,
      top: 36,
      bottom: 24
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
      axisTick: { show: false },
      axisLabel: { color: 'rgba(255,255,255,0.4)', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.04)' } },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: 'rgba(255,255,255,0.3)', fontSize: 11 }
    },
    series: [
      {
        name: '异常总数',
        type: 'bar',
        barWidth: 16,
        itemStyle: {
          borderRadius: [3, 3, 0, 0],
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#ff6b6b' },
              { offset: 1, color: 'rgba(255,70,70,0.3)' }
            ]
          }
        },
        data: anomalyData
      },
      {
        name: '告警数量',
        type: 'bar',
        barWidth: 16,
        itemStyle: {
          borderRadius: [3, 3, 0, 0],
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#ff9f43' },
              { offset: 1, color: 'rgba(255,106,0,0.3)' }
            ]
          }
        },
        data: alarmData
      },
      {
        name: '故障数量',
        type: 'bar',
        barWidth: 16,
        itemStyle: {
          borderRadius: [3, 3, 0, 0],
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#00b4ff' },
              { offset: 1, color: 'rgba(0,180,255,0.3)' }
            ]
          }
        },
        data: faultData
      }
    ]
  }
  deviceTrendChart.setOption(option)
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

function getDomainClass(domain) {
  if (!domain) return 'env'
  if (domain.includes('设备')) return 'device'
  if (domain.includes('能耗')) return 'energy'
  return 'env'
}

async function handleDispatchTask(task, zone) {
  console.log('handleDispatchTask', JSON.stringify(task))
  if (!task.taskId) {
    // 如果没有taskId（降级生成的任务），先创建任务
    try {
      const createRes = await aiRequest.post('/dispatch', {
        subareaCid: zone.subareaCid || zone._rawSubareaCid || '',
        creatorAgentId: 'commander',
        creatorAgentName: '系統Agent',
        businessDomain: task.businessDomain,
        level: task.level,
        problemDesc: task.problemDesc,
        thinkingOverview: task.thinkingOverview,
        problemSummary: task.problemSummary,
        decisionContent: task.decisionContent,
        expectedEffect: task.expectedEffect,
        targetAgentId: task.targetAgentId,
        dispatched: 0
      })
      if (createRes && (createRes.code === '200' || createRes.Status === 'Ok')) {
        task.taskId = createRes.data?.id
      }
    } catch (e) {
      console.error('创建任务失败:', e)
      return
    }
  }

  try {
    const { taskId, ...restTask } = task
    const taskBody = { ...restTask, id: task.taskId, dispatchedUser: userStore.user_name, dispatched: 0 }
    const res = await aiRequest.post('/dispatch/dispatch', taskBody)
    if (res && (res.code === '200' || res.Status === 'Ok')) {
      task.dispatched = true
      showToast('success', '派发成功', `任务已派发至${task.targetAgentName}`)
    } else {
      showToast('error', '派发失败', res?.msg || '未知错误')
    }
  } catch (e) {
    showToast('error', '后台执行中', '网络异常')
    console.error('派发请求异常:', e)
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
  if (isDispatchModalOpen.value) return
  let deviceInfo = null
  zoneAreas.value.forEach(zone => {
    zone.anomalyDevices.forEach(d => {
      if (action.title.includes(d.name) || action.title.includes(d.id)) {
        deviceInfo = { ...d, zoneName: zone.name }
      }
    })
  })
  isDispatchModalOpen.value = true
  nextTick(() => {
    dispatchModalRef.value?.open(action, modalType, deviceInfo)
  })
}

function closeDispatchModal() {
  isDispatchModalOpen.value = false
  if (dispatchModalFromDeviceAnalysis.value) {
    dispatchModalFromDeviceAnalysis.value = false
    isDeviceAnalysisOpen.value = true
    nextTick(() => {
      setTimeout(() => {
        initDeviceHealthScoreChart()
        initDeviceTrendChart()
      }, 200)
    })
  }
}

function handleDispatchConfirm(orderData) {
  const actionText = dispatchModalType.value === 'repair' ? '维修' : '排查'
  alert(`已确认${actionText}！\n工单号：${orderData.orderNumber}\n设备：${orderData.deviceName}\n人员：${orderData.recommendedPerson}`)
  closeDispatchModal()
}

function handleDeviceAnalysisDispatch(action, modalType) {
  dispatchModalFromDeviceAnalysis.value = true
  isDeviceAnalysisOpen.value = false
  openDispatchModal(action, modalType)
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


function getEnvValue(zone, name) {
  if (!zone || !zone.env) return ''
  const item = zone.env.find(e => e.name === name)
  return item ? item.value : ''
}

onMounted(() => {
  areaStore.fetchAreaList()
  const savedTheme = localStorage.getItem('theme')
  if (savedTheme === 'light') {
    isDarkTheme.value = false
  } else {
    applyTheme('dark')
    isDarkTheme.value = true
  }

  // 启动环境分析后台完成轮询
  startEnvAnalysisPolling()

  // 检测屏幕高度并动态调整样式
  const checkScreenHeight = () => {
    const screenHeight = window.innerHeight
    if (screenHeight > 964) {
      document.documentElement.style.setProperty('--top-grid-margin-top', '40px')
      document.documentElement.style.setProperty('--divider-title-padding', '20px')
      document.documentElement.style.setProperty('--ai-banner-margin', '30px 0')
      document.documentElement.style.setProperty('--page-header-height', '100px')
      document.documentElement.style.setProperty('--header-station-left-top', '20px')
      document.documentElement.style.setProperty('--header-station-left-font-size', '30px')
      document.documentElement.style.setProperty('--header-station-name-top', '36px')
      document.documentElement.style.setProperty('--header-station-name-font-size', '40px')
      document.documentElement.style.setProperty('--header-right-margin-top', '2    0px')
      document.documentElement.style.setProperty('--header-station-left-border', '300px')
    } else {
      document.documentElement.style.setProperty('--top-grid-margin-top', '10px')
      document.documentElement.style.setProperty('--divider-title-padding', '10px ')
      document.documentElement.style.setProperty('--ai-banner-margin', '10px 0')
      document.documentElement.style.setProperty('--page-header-height', '')
      document.documentElement.style.setProperty('--header-station-left-top', '8px')
      document.documentElement.style.setProperty('--header-station-left-font-size', '20px')
      document.documentElement.style.setProperty('--header-station-name-top', '30px')
      document.documentElement.style.setProperty('--header-station-name-font-size', '28px')
      document.documentElement.style.setProperty('--header-right-margin-top', '0')
      document.documentElement.style.setProperty('--header-station-left-border', '180px')
    }
  }

  checkScreenHeight()
  window.addEventListener('resize', checkScreenHeight)

  initSparklineChart()
  fetchHealthScoreHistory()
  initEnergyChart()
  nextTick(() => {
    setTimeout(() => {
      mainIslands.start()
    }, 300)
  })

  fetchRecentAlerts()
  fetchHealthMetrics()
  fetchEnergyMetrics()
  fetchZoneAreas()
  fetchSubareaPendingInsights()
  fetchComputingPower()
  fetchKeliuAgentData()
  fetchEmsAgentData()

  // 页面加载完成后调用一次全分区分析
  setTimeout(() => {
    fetchAllZoneAnalysis()
  }, 2000)

  // 每2分钟刷新一次全分区分析
  aiAnalysisRefreshTimer = setInterval(() => {
    fetchAllZoneAnalysis()
    fetchSubareaPendingInsights()
  }, 120000)

  // 页面加载后立即获取派发任务列表（Agent协作），无需延迟
  fetchDispatchTasks()

  // 每3秒刷新一次派发任务列表
  dispatchTaskRefreshTimer = setInterval(() => {
    fetchDispatchTasks()
  }, 3000)

  // 页面加载完成后调用一次知识库统计
  setTimeout(() => {
    fetchKnowledgeStats()
  }, 4000)

  // 每2分钟刷新一次知识库统计
  knowledgeRefreshTimer = setInterval(() => {
    fetchKnowledgeStats()
  }, 120000)

  // 页面加载完成后调用一次运维KPI
  setTimeout(() => {
    fetchOpsKpi()
  }, 5000)

  // 每5分钟刷新一次运维KPI
  opsKpiRefreshTimer = setInterval(() => {
    fetchOpsKpi()
  }, 300000)

  // 页面加载完成后调用一次KPI统计
  setTimeout(() => {
    fetchKpiStats()
  }, 5500)

  // 每5分钟刷新一次KPI统计
  setInterval(() => {
    fetchKpiStats()
  }, 300000)

  // 每10秒刷新一次告警列表和健康指标
  alertRefreshTimer = setInterval(() => {
    fetchRecentAlerts()
    fetchHealthMetrics()
    fetchEnergyMetrics()
    fetchZoneAreas()
  }, 60000)
})

onUnmounted(() => {
  uninstallDashboardMock()
  window.clearTimeout(botReplyTimer)
  sparklineChart?.dispose()
  energyChart?.dispose()
  mainIslands.stop()
  modalIslands.stop()
  stopCardZoneTypewriter()
  killZoneCardsAnimation()
  if (deviceControlTimer) {
    clearTimeout(deviceControlTimer)
  }
  if (deviceCloseTimer) {
    clearTimeout(deviceCloseTimer)
  }
  if (alertRefreshTimer) {
    clearInterval(alertRefreshTimer)
  }
  if (aiAnalysisRefreshTimer) {
    clearInterval(aiAnalysisRefreshTimer)
  }
  if (dispatchTaskRefreshTimer) {
    clearInterval(dispatchTaskRefreshTimer)
  }
  if (knowledgeRefreshTimer) {
    clearInterval(knowledgeRefreshTimer)
  }
  if (opsKpiRefreshTimer) {
    clearInterval(opsKpiRefreshTimer)
  }
})
// ------- 语音服务订阅 start -------
const autoDiagnosisChannel = new BroadcastChannel('autoDiagnosis')

const agentCollabChannel = new BroadcastChannel("SW-IOT-AGENTCOLLAB-LIST");

// 处理自动诊断逻辑
async function handleAutoDiagnosis(alertItem) {
  autoDiagnosisAlert.value = alertItem
  isAutoDiagnosisModalOpen.value = true
  // 校验设备是否有视频
  await checkDeviceVideo(alertItem.device_id)
  // 请求AI分析接口
  aiAnalysisLoading.value = true
  aiAnalysisResult.value = null
  try {
    const res = await aiRequest.post('/analysis/deviceAlarmAnalysis', alertItem)
    if (res.Status === 'Ok' && res.data) {
      aiAnalysisResult.value = res.data
    }
  } catch (err) {
    console.error('设备告警AI分析失败:', err)
  } finally {
    aiAnalysisLoading.value = false
  }
}

// 模拟自动诊断测试
function testAutoDiagnosis() {
  // 生成当前时间格式 YYYYMMDDHHmmss
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const currentTime = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`

  // 模拟告警数据
  const mockAlertItem = {
    id: "1252981094207197184",
    org_id: "3",
    subarea_cid: "0.001.001.001-0.002",
    system_type: "0.004",
    device_id: "1226586214233018368",
    device_name: "车站安全生产指挥中心环境传感器",
    event_level: 3,
    event_state: 0,
    location: null,
    event_info: "车站安全生产指挥中心当前环境温度过高，温度值为24.5°C",
    happen_time: currentTime,
    receive_time: currentTime,
    process_state: 0,
    event_type: "0-002.004",
    data_type: 2,
    data_key: "1275",
    confirm_state: null,
    warning_level: null
  }

  // 设置告警数据
  autoDiagnosisAlert.value = mockAlertItem
  isAutoDiagnosisModalOpen.value = true
  aiAnalysisLoading.value = true
  aiAnalysisResult.value = null

  // 模拟AI分析结果
  setTimeout(() => {
    aiAnalysisResult.value = {
      alarmAnalysis: "经监测，车站安全生产指挥中心当前环境温度达30℃，已超出舒适范围（22-26℃），存在高温风险。",
      suggestion: "建议立即启动车站安全生产指挥中心多联机内机1~4号，运行模式设置为制冷，风速与目标温度调至自动档位，以快速降低室内温度。",
      dispatchTasks: [
        {
          id: "mock-task-001",
          level: "高",
          problemDesc: "车站安全生产指挥中心当前环境温度过高，温度值为30°C",
          decision_content: "开启车站安全生产指挥中心多联机内机1~4号，模式设为制冷，风速和温度设为自动，进行降温处理",
          targetAgentName: "系統Agent",
          target_agent_id: "commander",
          businessDomain: "空调控制",
          expectedEffect: "预计10-15分钟内将环境温度降至24-26℃舒适区间，保障安全生产指挥中心工作环境及设备正常运行"
        }
      ]
    }
    aiAnalysisLoading.value = false
  }, 1000)
}

// 模拟告警数据并调用真实AI分析接口
async function testAutoDiagnosisRealAI() {
  // 生成当前时间格式 YYYYMMDDHHmmss
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const currentTime = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`

  // 模拟告警数据
  const mockAlertItem = {
    id: "1252981094207197184",
    org_id: "3",
    subarea_cid: "0.001.001.001-0.002",
    system_type: "0.004",
    device_id: "1231342073462919168",
    device_name: "TX43_6号岛式空调环境传感器",
    event_level: 2,
    event_state: 0,
    location: null,
    event_info: "设备故障",
    happen_time: currentTime,
    receive_time: currentTime,
    process_state: 0,
    event_type: "0-002.004",
    data_type: 1,
    data_key: "1275",
    confirm_state: null,
    warning_level: null
  }

  // 设置告警数据并调用真实AI分析
  autoDiagnosisAlert.value = mockAlertItem
  isAutoDiagnosisModalOpen.value = true
  // 校验设备是否有视频
  await checkDeviceVideo(mockAlertItem.device_id)
  // 请求AI分析接口
  aiAnalysisLoading.value = true
  aiAnalysisResult.value = null
  try {
    const res = await aiRequest.post('/analysis/deviceAlarmAnalysis', mockAlertItem)
    if (res.Status === 'Ok' && res.data) {
      aiAnalysisResult.value = res.data
    }
  } catch (err) {
    console.error('设备告警AI分析失败:', err)
  } finally {
    aiAnalysisLoading.value = false
  }
}

// 校验设备是否有视频数据
async function checkDeviceVideo(deviceId) {
  if (!deviceId) {
    autoDiagnosisHasVideo.value = false;
    return;
  }
  try {
    const res = await iotRequest.post('/base/device/StateListPage', {
      page: "1",
      limit: "1",
      orgId: "3",
      deviceId: deviceId,
      systemType: "0.004",
      addChangeStateTime: 0
    }, {
      headers: { 'Content-Type': 'application/json' }
    });
    autoDiagnosisHasVideo.value = res.data && res.data.count > 0;
  } catch (error) {
    console.error("校验设备视频失败:", error);
    autoDiagnosisHasVideo.value = false;
  }
}

// 打开视频播放
const videoChannel = new BroadcastChannel("SW-IOT-HT-CHANNEL");
function openVideoPlayer(deviceId, deviceName) {
  if (!deviceId) {
    showToast('error', '提示', '设备ID为空，无法打开视频');
    return;
  }
  videoChannel.postMessage({
    id: "xxxx1x",
    type: "SW-SLAVE",
    cmd: "SW.IOT.VIDEO.SHOW-VIDEO-PLAYER",
    data: { deviceId: deviceId, deviceName: deviceName }
  });
}

onMounted(() => {
  window.addEventListener("message", handleVisionVoiceMessage);
  // 监听iframe父页面发送的自动诊断事件
  autoDiagnosisChannel.onmessage = async (event) => {
    console.log('收到autoDiagnosis消息:', event.data)
    if (event.data?.type === 'autoDiagnosis') {
      const alertItem = event.data.alertItem
      console.log('alertItem:', alertItem)
      if (alertItem) {
        //真实
        handleAutoDiagnosis(alertItem)
        //模拟
        // testAutoDiagnosis()
      }
    }
  }
  // 监听iframe父页面发送打开协作列表弹窗事件
  agentCollabChannel.onmessage = async (e)=>{
    console.log('收到agentCollabChannel消息:', e.data)
    if(e.data?.type === "OPEN_AGENT_COLLAB_MODAL"){
      openAgentCollabModal()
    }
  }
});
onUnmounted(() => {
  window.removeEventListener("message", handleVisionVoiceMessage);
  autoDiagnosisChannel.close()
  agentCollabChannel.close()
  videoChannel.close()
});

const handleVisionVoiceMessage = (event) => {
  if (event.source !== window) return;
  if (event.data?.type !== "VISION_VOICE_EVENT") return;
  const { subscribeId, subscribeEvent, msgId } = event.data.data;
  if (subscribeId === "OpenIframe") {
    if (subscribeEvent === "打开智慧助手") {
      toggleAssistant()
      responseVisionVoice(msgId, "已打开")
    } else if(subscribeEvent === "关闭智慧助手") {
      closeAssistantModal()
      responseVisionVoice(msgId, "已关闭")
    } else if(subscribeEvent === "打开知识库") {
      goKnowledgeBase()
      responseVisionVoice(msgId, "已打开")
    } else if(subscribeEvent === "关闭知识库") {
      closeKnowledgeModal()
      responseVisionVoice(msgId, "已关闭")
    } else if(subscribeEvent === "打开运维") {
      openOpsAgent()
      responseVisionVoice(msgId, "已打开")
    }
  }
};
const responseVisionVoice = (msgId, msg, msgData) => {
  console.log("responseVisionVoice",msgId, msg, msgData)
  window.postMessage({
    type: "VISION_VOICE_EVENT_ACK",
    msgId,
    msg,
    msgData,
  }, "*");
}
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
    <symbol id="i-alert" viewBox="0 0 24 24">
      <path d="M12 2L2 22h20L12 2Z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/>
      <line x1="12" y1="9" x2="12" y2="14" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"/>
      <circle cx="12" cy="18" r="1.5" fill="currentColor"/>
    </symbol>
    <symbol id="i-tools" viewBox="0 0 24 24">
      <path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
    </symbol>
    <symbol id="i-agents" viewBox="0 0 24 24">
      <circle cx="12" cy="5" r="2.8" fill="currentColor"/>
      <circle cx="5" cy="15" r="2.8" fill="currentColor"/>
      <circle cx="19" cy="15" r="2.8" fill="currentColor"/>
      <circle cx="12" cy="19" r="2.2" fill="none" stroke="currentColor" stroke-width="2"/>
      <path d="M12 7.8v9.2" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
      <path d="M7.5 13.5 9.8 16" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
      <path d="M16.5 13.5 14.2 16" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
      <path d="M9.5 5.5 7 13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity="0.6"/>
      <path d="M14.5 5.5 17 13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity="0.6"/>
    </symbol>
  </svg>

  <main class="dashboard">
    <!-- SVG definitions for AI ring gradient -->
    <svg width="0" height="0" style="position:absolute">
      <defs>
        <linearGradient id="aiRingGradient" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="#00d4ff"/>
          <stop offset="50%" stop-color="#0080ff"/>
          <stop offset="100%" stop-color="#0040dd"/>
        </linearGradient>
      </defs>
    </svg>

    <header class="page-header" :style="{ backgroundImage: `url(${headerBgImage})` }">
      <div class="header-station-left">
        <button class="admin-btn" @click="goToExternal" aria-label="外部系统">
          3D
        </button>
        <!-- <svg class="station-icon" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="iconGradient" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" style="stop-color:#00f5ff;stop-opacity:1" />
              <stop offset="100%" style="stop-color:#0088ff;stop-opacity:1" />
            </linearGradient>
            <filter id="glow">
              <feGaussianBlur stdDeviation="2" result="coloredBlur"/>
              <feMerge>
                <feMergeNode in="coloredBlur"/>
                <feMergeNode in="SourceGraphic"/>
              </feMerge>
            </filter>
          </defs>
          <polygon points="20,4 36,14 36,26 20,36 4,26 4,14" fill="none" stroke="url(#iconGradient)" stroke-width="2" filter="url(#glow)"/>
          <polygon points="20,10 30,16 30,24 20,30 10,24 10,16" fill="url(#iconGradient)" opacity="0.6"/>
          <circle cx="20" cy="20" r="4" fill="#00f5ff" filter="url(#glow)"/>
          <line x1="20" y1="4" x2="20" y2="10" stroke="#00f5ff" stroke-width="1.5" opacity="0.8"/>
          <line x1="36" y1="14" x2="30" y2="16" stroke="#00f5ff" stroke-width="1.5" opacity="0.8"/>
          <line x1="36" y1="26" x2="30" y2="24" stroke="#00f5ff" stroke-width="1.5" opacity="0.8"/>
          <line x1="20" y1="36" x2="20" y2="30" stroke="#00f5ff" stroke-width="1.5" opacity="0.8"/>
          <line x1="4" y1="26" x2="10" y2="24" stroke="#00f5ff" stroke-width="1.5" opacity="0.8"/>
          <line x1="4" y1="14" x2="10" y2="16" stroke="#00f5ff" stroke-width="1.5" opacity="0.8"/>
        </svg>
        <span class="station-text">Dashboard</span> -->
      </div>
      <div class="header-station-name">智慧底座</div>
      <div class="header-station-right">
        <div class="header-right">
          <button v-show="false" class="test-btn" @click="testAutoDiagnosisRealAI" title="模拟自动诊断">告警分析</button>
          <!-- <button class="test-btn" @click="testAutoDiagnosis" title="模拟自动诊断">模拟分析</button>  -->

          <div class="alert-badge" :class="{ 'has-alert': alertCount > 0 }" @click="openAlertModal" style="cursor: pointer;">
            <svg class="alert-icon" viewBox="0 0 24 24"><use href="#i-alert"></use></svg>
            <span class="alert-count" v-if="alertCount > 0">{{ alertCount }}</span>
          </div>
          <div class="header-badge">
            <span class="pulse"></span>
            系统运行正常
          </div>
          <!-- 后台任务执行指示器 -->
          <div v-if="dispatchRunningInBackground" class="header-badge bg-task-running" @click="dispatchLoadingVisible = true" style="cursor: pointer;">
            <span class="pulse pulse-orange"></span>
            后台任务执行中
          </div>
          <!-- <button class="theme-toggle" @click="toggleTheme" aria-label="切换主题">🌓 切换主题</button> -->
          <button class="admin-btn" @click="goToAdmin" aria-label="后台管理">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <circle cx="12" cy="12" r="3"/>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
            </svg>
            <!-- 后台 -->
          </button>
        </div>
      </div>
    </header>
<div class="wudatupo">实现了全域智能感知、数据融合、智能预警、闭环运维、节能降碳五大突破</div>
    <section class="top-grid" aria-label="站房运行概览">
      <!-- 左侧栏：状态评估 -->
      <div class="left-column">
        <div class="status-card-wrapper" >
          <article class="card status-card health-full-card">
            <div class="card-title">
              <span class="title-left"><svg class="ico"><use href="#i-shield"></use></svg>站房整体状态评估</span>
            </div>
            <div class="hover" @click="openHealthModal">
              <div class="health-main " >
                <svg class="shield green" viewBox="0 0 24 24"><use href="#i-shield"></use></svg>
                <div class="health-state">
                  <strong>AI评估</strong>
                </div>
              </div>
              <p class="health-desc " >{{ healthLevelDesc }}</p>
              <div ref="sparklineChartRef" class="sparkline-chart" aria-label="健康趋势"></div>
            </div>
            <div class="health-details " style="cursor: default;">
              <div class="health-section clickable" @click="openEnergyAgent">
                <h5>能耗概况</h5>
                <div class="health-metrics">
                  <div class="health-metric"><span class="label">今日</span><span class="value">{{ Number(energyMetrics.todayEnergy).toLocaleString() }} kWh</span></div>
                  <div class="health-metric">
                    <span class="label">较昨日</span>
                    <span v-if="energyMetrics.yesterdayCompare.isNormal" class="value green-text">正常</span>
                    <span v-else :class="energyMetrics.yesterdayCompare.value > 0 ? 'value red-text' : 'value green-text'">
                      {{ energyMetrics.yesterdayCompare.value > 0 ? '↑' : '↓' }} {{ Math.abs(energyMetrics.yesterdayCompare.value) }}%
                    </span>
                  </div>
                  <div class="health-metric"><span class="label">本月</span><span class="value">{{ Number(energyMetrics.monthEnergy).toLocaleString() }} kWh</span></div>
                  <div class="health-metric"><span class="label">本年</span><span class="value">{{ Number(energyMetrics.yearEnergy).toLocaleString() }} kWh</span></div>
                </div>
              </div>
              <div class="health-section clickable" @click="openPassengerAgent">
                <h5>车站运营</h5>
                <div class="health-metrics">
                  <div class="health-metric"><span class="label">当前客流量</span><span class="value">{{ keliuAgentData.passengerFlow }}</span></div>
                  <div class="health-metric"><span class="label">当前车流量</span><span class="value">{{ keliuAgentData.trainCount }}</span></div>
                  <div class="health-metric"><span class="label">运营状态</span><span class="value" :class="keliuAgentData.operationStatus === '注意' ? 'orange-text' : 'green-text'">{{ keliuAgentData.operationStatus }}</span></div>
                  <div class="health-metric"><span class="label">拥堵区域</span><span class="value" :class="Number(keliuAgentData.crowdedCount) > 0 ? 'orange-text' : 'green-text'">{{ keliuAgentData.crowdedCount }}处</span></div>
                </div>
              </div>
              <div class="health-section clickable" @click="openOpsAgent">
                <h5>设备运维</h5>
                <div class="health-metrics">
                  <div class="health-metric"><span class="label">健康率</span><span class="value green-text">{{ healthMetrics.healthRate }}%</span></div>
                  <div class="health-metric"><span class="label">故障设备</span><span class="value red-text">{{ healthMetrics.faultDevices }}台</span></div>
                  <div class="health-metric"><span class="label">告警设备</span><span class="value orange-text">{{ healthMetrics.alarmDevices }}台</span></div>
                  <div class="health-metric"><span class="label">今日工单</span><span class="value">{{ healthMetrics.todayOrders }}个</span></div>
                </div>
              </div>
            </div>
          </article>
        </div>
      </div>

      <!-- 中间：分区态势 -->
      <article class="card zone-model-card">
        <div class="card-title zone-title-bar" style="margin-bottom: 16px;">
          <span class="title-left"><svg class="ico blue"><use href="#i-home"></use></svg><span class="blue-text">分区态势</span></span>
          <div class="zone-status-summary-inline">
            <div class="summary-item summary-normal">
              <span class="summary-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
              </span>
              <span class="summary-label">正常</span>
              <span class="summary-value">{{ zoneStatusSummary.normal }}</span>
            </div>
            <div class="summary-item summary-alarm">
              <span class="summary-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
              </span>
              <span class="summary-label">告警</span>
              <span class="summary-value">{{ zoneStatusSummary.alarm }}</span>
            </div>
          </div>
          <div class="zone-title-right">
            <div class="ai-inspect-badge">
              <span class="ai-inspect-dot"></span>
              <span class="ai-inspect-text">AI巡检中</span>
            </div>
            <button class="view-toggle-icon" @click="switchZoneView(zoneViewMode === 'model' ? 'cards' : 'model')" :title="zoneViewMode === 'model' ? '切换卡片视图' : '切换模型视图'">
              <svg v-if="zoneViewMode === 'model'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
            </button>
          </div>
        </div>

        <!-- 视图容器：固定高度，切换 canvas / 卡片时不弹动 -->
        <div class="zone-viewport">
          <div v-show="zoneViewMode === 'model'" class="zone-model-section">
            <canvas ref="heatmapCanvas" class="heatmap-canvas"></canvas>
          </div>

          <!-- 新版卡片视图：2x2网格 + AI洞察 + 底部指标栏 + 巡检流程 -->
          <div v-show="zoneViewMode === 'cards'" ref="zoneCardsSectionRef" class="zone-cards-section" :class="{ 'is-focused': focusedZone }">
            <!-- AI巡检流程条 + 今日巡检统计 -->
            <div class="zone-inspection-bar">
              <div class="inspection-flow">
                <template v-for="(step, sIdx) in inspectionSteps" :key="sIdx">
                  <div class="flow-step" :class="{ active: sIdx <= currentInspectionStep, done: sIdx < currentInspectionStep }">
                    <span class="flow-step-dot" :style="{ '--dot-color': step.dotColor, 'animation-delay': (sIdx * 0.3) + 's' }"></span>
                    <span class="flow-step-label">{{ step.label }}</span>
                  </div>
                  <div class="flow-arrow" v-if="sIdx < inspectionSteps.length - 1">
                    <div class="flow-arrow-track"><div class="flow-arrow-pulse"></div></div>
                  </div>
                </template>
              </div>
              <div class="inspection-stats">
                <div class="inspect-stat-item">
                  <div class="inspect-stat-icon ai-mini-ring">
                    <svg viewBox="0 0 40 40" width="36" height="36">
                      <circle cx="20" cy="20" r="17" class="mini-ring-bg"/>
                      <circle cx="20" cy="20" r="17" class="mini-ring-fill" style="--p: 100"/>
                      <text x="20" y="23" text-anchor="middle" class="mini-ring-text">AI</text>
                    </svg>
                  </div>
                  <div class="inspect-stat-info">
                    <span class="inspect-stat-main green-text">AI巡检已完成</span>
                  </div>
                </div>
                <div class="inspect-stat-divider"></div>
                <div class="inspect-stat-group">
                  <span class="inspect-stat-label">巡检统计</span>
                  <div class="inspect-stat-numbers">
                    <div class="stat-num">
                      <span class="stat-num-value">{{ zoneStatusSummary.normal + zoneStatusSummary.alarm  }}</span>
                      <span class="stat-num-label">巡检区域</span>
                    </div>
                    <div class="stat-num">
                      <span class="stat-num-value">{{ getSummaryDeviceTotal() }}</span>
                      <span class="stat-num-label">设备总数</span>
                    </div>
                    <div class="stat-num">
                      <span class="stat-num-value orange-text">{{ getSummaryDeviceIssues() }}</span>
                      <span class="stat-num-label">设备异常</span>
                    </div>
                    <div class="stat-num">
                      <span class="stat-num-value">{{ getSummaryEnvTotal() }}</span>
                      <span class="stat-num-label">环境监测</span>
                    </div>
                    <div class="stat-num">
                      <span class="stat-num-value orange-text">{{ getSummaryEnvAbnormal() }}</span>
                      <span class="stat-num-label">环境异常</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="!focusedZone" class="zone-new-grid">
              <div v-for="(item, idx) in zoneCardSnapshots" :key="item.zone._rawSubareaCid || item.zone.name" class="zone-new-card" :class="[item.zone.boxClass, { 'zone-card-abnormal': item.snapshot.anomalyTotal > 0 }]">
                <!-- 卡片头部：区域名 + 设备统计 + 注意标签 -->
                <div class="znc-header">
                  <div class="znc-title-row">
                    <span class="znc-name" :class="item.zone.statusClass">{{ item.snapshot.name }}</span>
                    <span class="znc-attention-tag" v-if="item.snapshot.anomalyTotal > 0">注意</span>
                  </div>
                  <div class="znc-device-stats">
                    <span>设备总数 <b>{{ getZoneDeviceTotal(item.zone) }}台</b></span>
                    <span>告警 <b>{{ item.snapshot.deviceAlarmCount }}台</b></span>
                  </div>
                </div>

                <!-- 卡片主体：左侧AI圆环 + 右侧AI洞察 -->
                <div class="znc-body">
                  <!-- 左侧：AI圆环 + 巡检状态 -->
                  <div class="znc-left">
                    <div class="ai-ring-container" :class="'ai-ring-' + idx">
                      <!-- 时钟底环 -->
                      <div class="ai-clock-ring"></div>
                      <!-- 扫描旋转环（三层，不同速度和方向） -->
                      <div class="ai-scan-ring-1"></div>
                      <div class="ai-scan-ring-2"></div>
                      <div class="ai-scan-ring-3"></div>
                      <!-- SVG刻度层 -->
                      <svg class="ai-ring-svg" viewBox="0 0 120 120">
                        <circle class="ai-ring-bg" cx="60" cy="60" r="52" />
                        <circle class="ai-ring-track" cx="60" cy="60" r="52" :style="{ '--ring-progress': item.snapshot.totalScore / 100 }" />
                        <!-- 装饰刻度线 -->
                        <g class="ai-ring-ticks">
                          <template v-for="t in 36" :key="t">
                            <line :x1="60 + 46 * Math.cos((t * 10 - 90) * Math.PI / 180)" :y1="60 + 46 * Math.sin((t * 10 - 90) * Math.PI / 180)"
                                  :x2="60 + (t % 3 === 0 ? 50 : 48) * Math.cos((t * 10 - 90) * Math.PI / 180)" :y2="60 + (t % 3 === 0 ? 50 : 48) * Math.sin((t * 10 - 90) * Math.PI / 180)"
                                  :class="t % 3 === 0 ? 'tick-major' : 'tick-minor'" />
                          </template>
                        </g>
                      </svg>
                      <div class="ai-ring-center">
                        <span class="ai-ring-label">AI</span>
                      </div>
                    </div>
                    <!--                    <div class="znc-inspect-status" :class="item.snapshot.anomalyTotal > 0 ? 'inspect-warn' : 'inspect-ok'">-->
                    <div class="znc-inspect-status  inspect-ok">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="14" height="14"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
                      <span>AI巡检完成</span>
                    </div>
                  </div>

                  <!-- 右侧：AI洞察面板 -->
                  <div class="znc-right">
                    <div class="znc-insight-header">
                      <svg class="znc-brain-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="16" height="16">
                        <path d="M12 4.5a2.5 2.5 0 0 0-4.96-.46 2.5 2.5 0 0 0-1.98 3 2.5 2.5 0 0 0-1.02 4.06A2.5 2.5 0 0 0 4 14.5a2.5 2.5 0 0 0 2.04 2.46A2.5 2.5 0 0 0 9 19a2.5 2.5 0 0 0 4 .02A2.5 2.5 0 0 0 18 16.54a2.5 2.5 0 0 0 .96-4.08A2.5 2.5 0 0 0 17 8.5a2.5 2.5 0 0 0-2.02-2.46A2.5 2.5 0 0 0 12 4.5"/>
                        <path d="M12 4.5v15"/>
                      </svg>
                      <span class="znc-insight-title">AI洞察</span>
                    </div>
                    <div class="znc-insight-content" :class="(getZoneInsightGroup(item) || item.snapshot.anomalyTotal > 0) ? 'insight-warn' : 'insight-ok'">
                      <div class="insight-status-line" v-if="getZoneInsightGroup(item)">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="14" height="14" class="insight-warning-icon"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                        <span class="insight-alert-count">当前有{{ getZoneInsightGroup(item).count }} 条优化建议</span>
                      </div>
                      <div class="insight-status-line" v-else-if="item.snapshot.anomalyTotal > 0">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="14" height="14" class="insight-warning-icon"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                        <span class="insight-alert-count">发现 {{ item.snapshot.anomalyTotal }} 处异常 </span>
                      </div>
                      <div class="insight-status-line" v-else>
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="14" height="14" class="insight-ok-icon"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
                        <span class="insight-ok-text">状态良好</span>
                      </div>
                      <ul class="insight-list" v-if="getZoneInsightGroup(item)">
                        <li v-for="t in getZoneInsightGroup(item).tasks" :key="t.id" class="insight-item insight-task-item">
                          <span class="insight-task-desc" :title="t.problemDesc">{{ t.problemDesc }}</span>
                          <button class="insight-hand-btn" type="button" :title="'前往' + agentPageName(t.targetAgentId) + '处理'" @click.stop="gotoAgentByTask(t)">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><path d="M18 11V6a2 2 0 0 0-4 0v5"/><path d="M14 10V4a2 2 0 0 0-4 0v6"/><path d="M10 10.5V6a2 2 0 0 0-4 0v8"/><path d="M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15"/></svg>
                          </button>
                        </li>
                      </ul>
                      <ul class="insight-list" v-else>
                        <li v-for="(insight, i) in getMockInsights(item, idx)" :key="i" class="insight-item">{{ insight }}</li>
                      </ul>
                    </div>
                  </div>
                </div>

                <!-- 底部指标栏：环境状态 / 设备状态 / 能耗情况 -->
                <div class="znc-footer">
                  <div class="znc-metric clickable" @click="openEnvAnalysis()">
                    <div class="znc-metric-icon env-icon">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
                    </div>
                    <div class="znc-metric-body">
                      <span class="znc-metric-label">环境状态</span>
                      <span class="znc-metric-value" :class="item.snapshot.envIssueCount > 0 ? 'orange-text' : ''">{{ item.snapshot.envIssueCount > 0 ? item.snapshot.envIssueCount + '处' : '正常' }}</span>
                      <span class="znc-metric-sub">{{ item.snapshot.envIssueCount > 0 ? '需关注' : '无异常' }}</span>
                    </div>
                  </div>
                  <div class="znc-metric clickable" @click="openOpsAgent()">
                    <div class="znc-metric-icon device-icon">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><rect x="4" y="4" width="16" height="16" rx="2"/><circle cx="9" cy="10" r="1"/><circle cx="15" cy="10" r="1"/><path d="M9 15h6"/><path d="M8 6h8"/></svg>
                    </div>
                    <div class="znc-metric-body">
                      <span class="znc-metric-label">设备状态</span>
                      <span class="znc-metric-value" :class="item.snapshot.deviceIssueCount > 0 ? 'red-text' : ''">{{ item.snapshot.deviceIssueCount > 0 ? item.snapshot.deviceIssueCount + '处故障' : '无故障' }}</span>
                      <span :class="item.snapshot.deviceAlarmCount > 0 ? ' znc-metric-sub orange-text' : 'znc-metric-sub'">{{ item.snapshot.deviceAlarmCount > 0 ? item.snapshot.deviceAlarmCount + '台告警' : '无告警' }}</span>
                    </div>
                  </div>
                  <div class="znc-metric clickable" @click="openEnergyAgent()">
                    <div class="znc-metric-icon energy-icon">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
                    </div>
                    <div class="znc-metric-body">
                      <span class="znc-metric-label">能耗情况</span>
                      <span class="znc-metric-value" :class="item.snapshot.energyStatus === '注意' ? 'orange-text' : ''">{{ item.snapshot.energyToday || '-' }}</span>
                      <span class="znc-metric-sub">{{ item.snapshot.energyStatus === '注意' ? '需优化' : '无异常波动' }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>


          </div>

          <!-- 模型视图模式：选中区域后展示该区域卡片 -->
          <div v-if="focusedZone && zoneViewMode === 'model'" class="zone-focus-layout zone-focus-single-card">
            <button class="zone-focus-back" @click="clearFocusedZone" title="返回分区列表">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5"/><path d="M12 19l-7-7 7-7"/></svg>
            </button>
            <div v-if="focusedZoneCardItem" class="zone-new-card" :class="[focusedZoneCardItem.zone.boxClass, { 'zone-card-abnormal': focusedZoneCardItem.snapshot.anomalyTotal > 0 }]">
              <!-- 卡片头部：区域名 + 设备统计 + 注意标签 -->
              <div class="znc-header">
                <div class="znc-title-row">
                  <span class="znc-name" :class="focusedZoneCardItem.zone.statusClass">{{ focusedZoneCardItem.snapshot.name }}</span>
                  <span class="znc-attention-tag" v-if="focusedZoneCardItem.snapshot.anomalyTotal > 0">注意</span>
                </div>
                <div class="znc-device-stats">
                  <span>设备总数 <b>{{ getZoneDeviceTotal(focusedZoneCardItem.zone) }}台</b></span>
                  <span>告警 <b>{{ focusedZoneCardItem.snapshot.deviceAlarmCount }}台</b></span>
                </div>
              </div>

              <!-- 卡片主体：左侧AI圆环 + 右侧AI洞察 -->
              <div class="znc-body">
                <!-- 左侧：AI圆环 + 巡检状态 -->
                <div class="znc-left">
                  <div class="ai-ring-container ai-ring-0">
                    <!-- 时钟底环 -->
                    <div class="ai-clock-ring"></div>
                    <!-- 扫描旋转环（三层，不同速度和方向） -->
                    <div class="ai-scan-ring-1"></div>
                    <div class="ai-scan-ring-2"></div>
                    <div class="ai-scan-ring-3"></div>
                    <!-- SVG刻度层 -->
                    <svg class="ai-ring-svg" viewBox="0 0 120 120">
                      <circle class="ai-ring-bg" cx="60" cy="60" r="52" />
                      <circle class="ai-ring-track" cx="60" cy="60" r="52" :style="{ '--ring-progress': focusedZoneCardItem.snapshot.totalScore / 100 }" />
                      <!-- 装饰刻度线 -->
                      <g class="ai-ring-ticks">
                        <template v-for="t in 36" :key="t">
                          <line :x1="60 + 46 * Math.cos((t * 10 - 90) * Math.PI / 180)" :y1="60 + 46 * Math.sin((t * 10 - 90) * Math.PI / 180)"
                                :x2="60 + (t % 3 === 0 ? 50 : 48) * Math.cos((t * 10 - 90) * Math.PI / 180)" :y2="60 + (t % 3 === 0 ? 50 : 48) * Math.sin((t * 10 - 90) * Math.PI / 180)"
                                :class="t % 3 === 0 ? 'tick-major' : 'tick-minor'" />
                        </template>
                      </g>
                    </svg>
                    <div class="ai-ring-center">
                      <span class="ai-ring-label">AI</span>
                    </div>
                  </div>
                  <div class="znc-inspect-status" :class="focusedZoneCardItem.snapshot.anomalyTotal > 0 ? 'inspect-warn' : 'inspect-ok'">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="14" height="14"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
                    <span>AI巡检完成</span>
                  </div>
                  <div class="znc-inspect-time">{{ formatInspectTime(0) }} 完成巡检</div>
                </div>

                <!-- 右侧：AI洞察面板 -->
                <div class="znc-right">
                  <div class="znc-insight-header">
                    <svg class="znc-brain-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="16" height="16">
                      <path d="M12 4.5a2.5 2.5 0 0 0-4.96-.46 2.5 2.5 0 0 0-1.98 3 2.5 2.5 0 0 0-1.02 4.06A2.5 2.5 0 0 0 4 14.5a2.5 2.5 0 0 0 2.04 2.46A2.5 2.5 0 0 0 9 19a2.5 2.5 0 0 0 4 .02A2.5 2.5 0 0 0 18 16.54a2.5 2.5 0 0 0 .96-4.08A2.5 2.5 0 0 0 17 8.5a2.5 2.5 0 0 0-2.02-2.46A2.5 2.5 0 0 0 12 4.5"/>
                      <path d="M12 4.5v15"/>
                    </svg>
                    <span class="znc-insight-title">AI洞察</span>
                  </div>
                  <div class="znc-insight-content" :class="(getZoneInsightGroup(focusedZoneCardItem) || focusedZoneCardItem.snapshot.anomalyTotal > 0) ? 'insight-warn' : 'insight-ok'">
                    <div class="insight-status-line" v-if="getZoneInsightGroup(focusedZoneCardItem)">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="14" height="14" class="insight-warning-icon"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                      <span class="insight-alert-count">当前有{{ getZoneInsightGroup(focusedZoneCardItem).count }}条优化建议</span>
                    </div>
                    <div class="insight-status-line" v-else-if="focusedZoneCardItem.snapshot.anomalyTotal > 0">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="14" height="14" class="insight-warning-icon"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                      <span class="insight-alert-count">发现 {{ focusedZoneCardItem.snapshot.anomalyTotal }} 处异常 </span>
                    </div>
                    <div class="insight-status-line" v-else>
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="14" height="14" class="insight-ok-icon"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
                      <span class="insight-ok-text">状态良好</span>
                    </div>
                    <ul class="insight-list" v-if="getZoneInsightGroup(focusedZoneCardItem)">
                      <li v-for="t in getZoneInsightGroup(focusedZoneCardItem).tasks" :key="t.id" class="insight-item insight-task-item">
                        <span class="insight-task-desc" :title="t.problemDesc">{{ t.problemDesc }}</span>
                        <button class="insight-hand-btn" type="button" :title="'前往' + agentPageName(t.targetAgentId) + '处理'" @click.stop="gotoAgentByTask(t)">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><path d="M18 11V6a2 2 0 0 0-4 0v5"/><path d="M14 10V4a2 2 0 0 0-4 0v6"/><path d="M10 10.5V6a2 2 0 0 0-4 0v8"/><path d="M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15"/></svg>
                        </button>
                      </li>
                    </ul>
                    <ul class="insight-list" v-else>
                      <li v-for="(insight, i) in getMockInsights(focusedZoneCardItem, 0)" :key="i" class="insight-item">{{ insight }}</li>
                    </ul>
                  </div>
                </div>
              </div>

              <!-- 底部指标栏：环境状态 / 设备状态 / 能耗情况 -->
              <div class="znc-footer">
                <div class="znc-metric clickable" @click="openEnvAnalysis()">
                  <div class="znc-metric-icon env-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
                  </div>
                  <div class="znc-metric-body">
                    <span class="znc-metric-label">环境状态</span>
                    <span class="znc-metric-value" :class="focusedZoneCardItem.snapshot.envIssueCount > 0 ? 'orange-text' : ''">{{ focusedZoneCardItem.snapshot.envIssueCount > 0 ? focusedZoneCardItem.snapshot.envIssueCount + '处异常' : '正常' }}</span>
                    <span class="znc-metric-sub">{{ focusedZoneCardItem.snapshot.envIssueCount > 0 ? '需关注' : '无异常' }}</span>
                  </div>
                </div>
                <div class="znc-metric clickable" @click="openOpsAgent()">
                  <div class="znc-metric-icon device-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><rect x="4" y="4" width="16" height="16" rx="2"/><circle cx="9" cy="10" r="1"/><circle cx="15" cy="10" r="1"/><path d="M9 15h6"/><path d="M8 6h8"/></svg>
                  </div>
                  <div class="znc-metric-body">
                    <span class="znc-metric-label">设备状态</span>
                    <span class="znc-metric-value" :class="focusedZoneCardItem.snapshot.deviceIssueCount > 0 ? 'red-text' : ''">{{ focusedZoneCardItem.snapshot.deviceIssueCount > 0 ? focusedZoneCardItem.snapshot.deviceIssueCount + '处故障' : '无故障' }}</span>
                    <span :class="focusedZoneCardItem.snapshot.deviceAlarmCount > 0 ? ' znc-metric-sub orange-text' : 'znc-metric-sub'">{{ focusedZoneCardItem.snapshot.deviceAlarmCount > 0 ? focusedZoneCardItem.snapshot.deviceAlarmCount + '台告警' : '无告警' }}</span>
                  </div>
                </div>
                <div class="znc-metric clickable" @click="openEnergyAgent()">
                  <div class="znc-metric-icon energy-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
                  </div>
                  <div class="znc-metric-body">
                    <span class="znc-metric-label">能耗情况</span>
                    <span class="znc-metric-value" :class="focusedZoneCardItem.snapshot.energyStatus === '注意' ? 'orange-text' : ''">{{ focusedZoneCardItem.snapshot.energyToday || '-' }}</span>
                    <span class="znc-metric-sub">{{ focusedZoneCardItem.snapshot.energyStatus === '注意' ? '需优化' : '无异常波动' }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 聚焦详情视图：卡片视图模式下面板+AI分析 -->
          <div v-if="focusedZone && zoneViewMode === 'cards'" class="zone-focus-layout">
            <section class="risk-box zone-focus-card" :class="focusedZone.boxClass">
              <div class="zone-focus-header">
                <button class="zone-focus-back" @click="clearFocusedZone" title="返回分区列表">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5"/><path d="M12 19l-7-7 7-7"/></svg>
                </button>
                <div>
                  <div class="zone-focus-name" :class="focusedZone.statusClass">{{ focusedZoneSnapshot.name }}</div>
                  <div class="zone-focus-sub">{{ focusedZoneSnapshot.primaryRiskText }}</div>
                </div>
                <span class="pill" :class="focusedZone.pillClass">{{ focusedZoneSnapshot.status }}</span>
              </div>

              <div class="zone-focus-score zone-focus-reveal">
                <div>
                  <span class="score-label">综合评分</span>
                  <strong :class="focusedZoneSnapshot.totalScore >= 90 ? 'green-text' : (focusedZoneSnapshot.totalScore >= 75 ? 'orange-text' : 'red-text')">{{ focusedZoneSnapshot.totalScore }}分</strong>
                </div>
                <!-- <div>
                  <span class="score-label">环境评分</span>
                  <strong :class="focusedZoneSnapshot.anomalyTotal > 0 ? 'orange-text' : 'green-text'">{{ focusedZoneSnapshot.envScore }}</strong>
                </div> -->
                <div>
                  <span class="score-label">关注项</span>
                  <strong>{{ focusedZoneSnapshot.anomalyTotal }}</strong>
                </div>
              </div>

              <div class="zone-focus-metrics zone-focus-reveal">
                <div v-for="metric in focusedZoneSnapshot.metrics" :key="metric.key" class="zone-focus-metric">
                  <span>{{ metric.label }}</span>
                  <b :class="metric.className">{{ metric.value }}</b>
                  <small :class="metric.detailClassName">{{ metric.detail }}</small>
                </div>
              </div>

              <div class="zone-focus-section zone-focus-reveal" v-if="focusedZoneSnapshot.envItems.length > 0">
                <div class="zone-focus-section-title">环境实时数据</div>
                <div class="zone-env zone-focus-env">
                  <span v-for="(e, i) in focusedZoneSnapshot.envItems.slice(0, 1)" :key="i" class="env-item" :class="{ 'env-abnormal': e.isAbnormal }">
                    {{ e.name }} <b>{{ e.value }}</b>{{ e.unit }}
                  </span>
                </div>
              </div>

              <div v-if="focusedZone.anomalyDevices && focusedZone.anomalyDevices.length > 0" class="zone-focus-section zone-focus-reveal">
                <div class="zone-focus-section-title">异常设备</div>
                <div class="zone-focus-device-list">
                  <div v-for="device in focusedZone.anomalyDevices" :key="device.id" class="zone-focus-device">
                    <b>{{ device.name }}</b>
                    <span> {{ device.status }} · {{ device.location }}</span>
                  </div>
                </div>
              </div>
            </section>

            <aside class="zone-ai-insight-panel">
              <!-- 统计栏 -->
              <div class="anomaly-stats-bar">
                <div class="anomaly-stat">
                  <span class="anomaly-stat-value">{{ anomalyStats.processed }}</span>
                  <span class="anomaly-stat-label">已处理</span>
                </div>
                <div class="anomaly-stat">
                  <span class="anomaly-stat-value">{{ anomalyStats.cancelled }}</span>
                  <span class="anomaly-stat-label">已取消</span>
                </div>
                <div class="anomaly-stat">
                  <span class="anomaly-stat-value">{{ anomalyStats.pending }}</span>
                  <span class="anomaly-stat-label">待分析</span>
                </div>
                <div class="anomaly-stat">
                  <span class="anomaly-stat-value">{{ anomalyStats.total }}</span>
                  <span class="anomaly-stat-label">总数</span>
                </div>
              </div>

              <!-- 任务列表 -->
              <div class="zone-anomaly-task-list">
                <!-- 正在分析 loading（置顶） -->
                <div v-if="anomalyAnalyzing" class="zone-ai-loading anomaly-analyzing">
                  <span></span><span></span><span></span>
                  <span class="anomaly-analyzing-text">正在分析第 {{ anomalyStats.generated + 1 }} / {{ anomalyStats.total }} 条异常...</span>
                </div>

                <div
                    v-for="task in anomalyTaskList"
                    :key="task.uid"
                    class="dispatch-task-card zone-ai-task"
                    :class="taskStatusClass(task)"
                >
                  <div class="zone-anomaly-task-head">
                    <span class="zone-anomaly-type" :class="`anomaly-type-${task.anomalyType}`">{{ anomalyTypeLabel(task.anomalyType) }}</span>
                    <span class="zone-anomaly-desc">{{ task.anomalyDesc }}</span>
                  </div>
                  <div class="zone-anomaly-analysis" v-if="task.zoneAnalysis">
                    <div class="zone-anomaly-analysis-title">态势分析</div>
                    <div class="zone-anomaly-analysis-text">{{ task.zoneAnalysis }}</div>
                  </div>
                  <div class="zone-anomaly-suggestion" v-if="task.suggestion">
                    <div class="zone-anomaly-suggestion-title">建议</div>
                    <div class="zone-anomaly-suggestion-text">{{ task.suggestion }}</div>
                  </div>
                  <div class="dispatch-task-body">
                    <div class="dispatch-task-field">
                      <span class="field-label">决策</span>
                      <span class="field-value">{{ task.decisionContent }}</span>
                    </div>
                  </div>
                  <div class="dispatch-task-footer">
                    <div class="dispatch-target-agent">
                      <span>派发至: {{ task.targetAgentName || task.targetAgentId }}</span>
                    </div>
                    <div class="anomaly-task-actions" v-if="task.status === 'pending'">
                      <button class="dispatch-btn anomaly-save-btn" :disabled="task.busy" @click.stop="saveAnomalyTask(task)">
                        <span>保存</span>
                      </button>
                      <button class="dispatch-btn" :disabled="task.busy" @click.stop="dispatchAnomalyTask(task)">
                        <span>派发</span>
                      </button>
                      <button class="dispatch-btn anomaly-cancel-btn" :disabled="task.busy" @click.stop="cancelAnomalyTask(task)">
                        <span>取消</span>
                      </button>
                    </div>
                    <span v-else class="anomaly-task-status" :class="task.statusClass">{{ task.statusText }}</span>
                  </div>
                </div>

                <!-- 空状态 -->
                <div v-if="anomalyTaskList.length === 0 && !anomalyAnalyzing && anomalyStats.total === 0" class="zone-empty">
                  该区域暂无异常，运行正常
                </div>

                <!-- 全部完成 -->
                <div v-if="anomalyAllDone" class="zone-anomaly-done">全部异常已分析完毕</div>
              </div>

              <!-- 下一条按钮 -->
              <div class="anomaly-next-bar" v-if="anomalyHasNext && !anomalyAnalyzing">
                <button class="anomaly-next-btn" @click.stop="processNextAnomaly">
                  <span>下一条 (剩余 {{ anomalyStats.pending }})</span>
                </button>
              </div>
            </aside>
          </div>
        </div>
      </article>

      <!-- 右侧栏：Agent协作 -->
      <div class="right-column">
        <div class="status-card-wrapper" style="--hover-color: var(--blue);">
          <article class="card ai-suggestions-card">
            <div class="card-title">
              <span class="title-left">
                <svg class="ico green"><use href="#i-agents"></use></svg><span class="blue-text">Agent协作</span>
                <!--               <button class="view-toggle-icon" type="button" aria-label="查看站房状态与Agent协作关系演变" @click="openLifeModal" title="站房状态与Agent协作关系演变">-->
                <!--                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>-->
                <!--              </button>-->
              </span>

              <button class="more" type="button" @click.stop="openAgentCollabModal">更多 <svg width="14" height="14"><use href="#i-arrow"></use></svg></button>
            </div>
            <div class="suggestions">
              <div v-if="dispatchTaskList.length === 0" class="suggestion-empty">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="28" height="28"><path d="M9 12l2 2 4-4"/><circle cx="12" cy="12" r="10"/></svg>
                <span>该区域运行平稳，暂无派发任务</span>
              </div>
              <div v-for="task in dispatchTaskList" :key="task.id" class="task-card" @click="openTaskDetail(task)" style="cursor:pointer;">
                <div class="task-card-header" style="justify-content: space-between;">
                  <div class="task-card-flow">
                    <span class="flow-agent-tag from-tag">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11"><circle cx="12" cy="8" r="4"/><path d="M6 20v-2a4 4 0 014-4h4a4 4 0 014 4v2"/></svg>
                      {{ task.creatorAgentName || task.creatorAgentId }}
                    </span>
                    <svg  v-if="task.creatorAgentId!=task.targetAgentId" class="flow-arrow-icon" viewBox="0 0 24 24" width="14" height="14"><path d="M5 12h14M13 6l6 6-6 6" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
                    <span  v-if="task.creatorAgentId!=task.targetAgentId" class="flow-agent-tag to-tag">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11"><circle cx="12" cy="8" r="4"/><path d="M6 20v-2a4 4 0 014-4h4a4 4 0 014 4v2"/></svg>
                      {{ task.targetAgentName || task.targetAgentId }}
                    </span>
                  </div>
                  <span>
                    <span class="meta-tag tag-hint" >
                      <!-- {{ task.taskType === 'hint' ? '提示' : '协同' }} -->
                    </span>
                  <span class="task-card-status" :class="task.dispatched === 1 ? 'status-done' : 'status-wait'">
                    <span class="status-dot"></span>
                    {{ task.resultExecuteStatus==1?'已完成':task.resultExecuteStatus==2?'失败': task.dispatched === 1 ? '已采纳' : task.dispatched === 0 ?'待决策':'已取消' }}
                  </span></span>
                </div>
                <div class="task-card-body">
                  <div class="task-problem-row">
                    <span class="decision-label">问题</span>
                    <span class="decision-content">{{ task.problemDesc }}</span>
                  </div>
                  <div class="task-decision-row" v-if="task.decisionContent">
                    <span class="decision-label">决策</span>
                    <span class="decision-content">{{ task.decisionContent }}</span>
                  </div>
                  <!-- <div class="task-card-meta">
                    <span class="meta-domain">{{ task.businessDomain }}</span>
                    <span class="meta-level" :class="'level-' + task.level">P{{ task.level }}</span>
                  </div> -->
                </div>
                <div class="task-card-actions" v-if="task.resultExecuteStatus==0&&task.dispatched === 0">
                  <button class="mini-dispatch-btn" @click.stop="handleDispatch(task)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
                    {{ task.taskType === 'hint' ? '确认' : '派发' }}
                  </button>
                  <button class="mini-cancel-btn" @click.stop="handleCancel(task)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                    取消
                  </button>
                </div>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <div class="divider-title title-brand"><span>💻</span><span>底座实时监控</span></div>
    <section class="bottom-grid " aria-label="底座实时监控">
      <div class="status-card-wrapper" style="--hover-color: var(--blue);cursor:pointer;" @click="openBaseMonitorModal">
        <article class="card">
          <div class="card-title">
            <span class="title-left"><svg class="ico"><use href="#i-cube"></use></svg>算力实时指标</span>
          </div>
          <div class="compute-grid">
            <div class="metric"><span class="label">GPU使用率</span><span class="value">{{ computingPower.gpuUtilizationRate }}%</span></div>
            <div class="metric"><span class="label">正在处理请求数</span><span class="value">{{ computingPower.processingRequest }}</span></div>
            <div class="metric"><span class="label">排队请求数</span><span class="value">{{ computingPower.queueRequest }}</span></div>
            <div class="metric"><span class="label">吞吐量(token/s)</span><span class="value">{{ computingPower.throughput.toLocaleString() }}</span></div>
            <div class="metric"><span class="label">推理耗时(ms)</span><span class="value">{{ computingPower.reasoningTime }}</span></div>
            <div class="metric"><span class="label">成功请求数</span><span class="value">{{ computingPower.successRequest }}</span></div>
          </div>
        </article>
      </div>

      <div class="status-card-wrapper" style="--hover-color: var(--blue);" @click="openEnergyAgent">
        <article class="card">
          <div class="agent-title"><span class="title-left">
<!--            <svg class="ico green"><use href="#i-leaf"></use></svg>-->
            <svg class="ico orange"><use href="#i-bolt"></use></svg>
            能源管理Agent</span><span class="run-tag"><i class="dot"></i>运行中</span></div>
          <div class="agent-layout">
            <div>
              <div class="ring" :style="{ '--p': emsAgentData.todayDecisionPassRate }"><span><b>{{ emsAgentData.todayTotal }}</b><small>今日AI建议</small></span></div>
              <div class="agent-footer" style="margin: 10px 20px; font-size: 13px; font-weight: 900; display: flex;flex-flow: row;">
                较昨日 <span :class="emsAgentData.yesterdayCompareRate >= 0 ? 'green-text' : 'orange-text'" style="margin-left:4px"> {{ emsAgentData.yesterdayCompareRate >= 0 ? '↑' : '↓' }} {{ Math.abs(emsAgentData.yesterdayCompareRate) }}%</span>
              </div>
            </div>
            <div class="agent-kv">
              <span>运行状态</span><span class="right green-text">正常</span>
              <span>累计AI建议</span><span class="right">{{ emsAgentData.total }}次</span>
              <span>累计采纳率</span><span class="right">{{ emsAgentData.decisionPassRate }}%</span>
              <span>执行成功率</span><span class="right">{{ emsAgentData.decisionSuccessRate }}%</span>
              <span>累计超时</span><span class="right">{{ emsAgentData.timeoutCount }}次</span>
              <span>累计超时率</span><span class="right">{{ emsAgentData.timeoutRate }}%</span>
            </div>
          </div>
        </article>
      </div>

      <div class="status-card-wrapper" style="--hover-color: var(--blue);" @click="openPassengerAgent">
        <article class="card">
          <div class="agent-title"><span class="title-left">
            <svg class="ico"><use href="#i-gear"></use></svg>
            车站运营Agent</span><span class="run-tag"><i class="dot"></i>运行中</span></div>
          <div class="agent-layout">
            <div class="prediction-ring-wrap">
              <div class="ring prediction-ring" :style="{ background: predictionRingGradient }"><span><b>{{ keliuAgentData.alertCount }}</b><small>今日告警</small></span></div>
              <div class="prediction-tooltip">
                <div class="tooltip-item"><span class="dot" style="background:#ef4444"></span>严重事项 <b>{{ keliuAgentData.severeCount }}</b></div>
                <div class="tooltip-item"><span class="dot" style="background:#f59e0b"></span>一般事项 <b>{{ keliuAgentData.normalCount }}</b></div>
                <div class="tooltip-item"><span class="dot" style="background:#60a5fa"></span>通知事项 <b>{{ keliuAgentData.notifyCount }}</b></div>
              </div>
              <div class="agent-footer" style="margin: 10px 10px; font-size: 13px; font-weight: 900; display: flex;flex-flow: row;">
                严重 <span class="orange-text" style="margin-left:4px">{{ keliuAgentData.severeCount }}</span>
                <span style="margin-left: 12px;">一般 <span style="margin-left:4px">{{ keliuAgentData.normalCount }}</span></span>
                <!--      <span style="margin-left: 12px;">通知 <span style="margin-left:4px">{{ keliuAgentData.notifyCount }}</span></span> -->
              </div>
            </div>
            <div class="agent-kv">
              <span>运行状态</span><span class="right green-text">正常</span>
              <span>今日告警</span><span class="right">{{ keliuAgentData.alertCount }} 次</span>
              <span>当前车流量</span><span class="right">{{ keliuAgentData.trainCount }}</span>
              <span>当前客流量</span><span class="right">{{ keliuAgentData.passengerFlow }}</span>
              <span>当前运营状态</span><span class="right">{{ keliuAgentData.operationStatus }}</span>
              <span>拥堵区域</span><span class="right">{{ keliuAgentData.crowdedCount }}处</span>
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
      <!-- 如需切换回智慧助手，注释此模块，取消上方智慧助手的注释  style="--hover-color: var(--blue);" -->
      <div class="status-card-wrapper" @click="openOpsAgent">
        <article class="card">
          <div class="agent-title"><span class="title-left"><svg class="ico"><use href="#i-tools"></use></svg>设备运维Agent</span><span class="run-tag"><i class="dot"></i>运行中</span></div>
          <div class="cooling-layout">
            <div>
              <div class="ring ops-ring" :style="{ background: opsRingGradient }"><span><b>{{ kpiStats.todayDispatch + kpiStats.todayDiagnosis }}</b><small>今日AI处理</small></span></div>
              <div class="agent-footer" style="margin: 10px 20px; font-size: 13px; font-weight: 900; display: flex;flex-flow: row;">
                派单 <span class="green-text" style="margin-left:4px">{{ kpiStats.todayDispatch }}</span>
                <span style="margin-left: 12px;">诊断 <span class="green-text" style="margin-left:4px">{{ kpiStats.todayDiagnosis }}</span></span>
              </div>
            </div>
            <div class="agent-kv">
              <span>运行状态</span><span class="right green-text">正常</span>
              <span>累计AI处理</span><span class="right">{{ opsKpi.totalDiagnosis+opsKpi.totalDispatch }} 次</span>
              <span>累计AI诊断</span><span class="right">{{ opsKpi.totalDiagnosis }} 次</span>
              <span>累计AI派单</span><span class="right">{{ opsKpi.totalDispatch }} 次</span>
              <span>累计巡检</span><span class="right">{{ opsKpi.totalInspections }} 次</span>
              <span>累计工单</span><span class="right">{{ opsKpi.totalWorkOrders }} 次</span>
            </div>
          </div>
        </article>
      </div>
      <!-- style="--hover-color: var(--blue);" -->
      <div class="status-card-wrapper"   @click="goKnowledgeBase"  >
        <article class="card">
          <div class="agent-title"><span class="title-left"><svg class="ico"><use href="#i-cube"></use></svg>知识库统计</span></div>
          <div class="knowledge-layout">
            <div ref="knowledgeChartRef" class="knowledge-chart"></div>
            <div class="k-legend">
              <span v-for="(file, index) in knowledgeStats.knowledgeFiles" :key="file.name">
                <i class="legend-dot" :style="{ background: getKnowledgeColor(file.name, index) }"></i>{{ file.name }} ({{ file.count }})
              </span>
            </div>
          </div>
          <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; margin-top: 12px;">
            <div class="metric" style=" padding: 4px 6px;">
              <span class="label">检索次数</span>
              <span class="label">{{ knowledgeStats.queryCount }}</span>
            </div>
            <div class="metric" style=" padding: 4px 6px;">
              <span class="label">知识命中率</span>
              <span class="label">{{ (knowledgeStats.hitRate * 100).toFixed(1) }}%</span>
            </div>
          </div>
        </article>
      </div>
    </section>
  </main>

  <!-- 视频全屏播放弹窗 -->
  <div v-if="isVideoFullscreen" class="video-fullscreen-overlay" @click.self="closeVideoFullscreen">
    <div class="video-fullscreen-modal">
      <button class="video-fullscreen-close" @click="closeVideoFullscreen">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24">
          <path d="M18 6L6 18M6 6l12 12"/>
        </svg>
      </button>
      <div class="video-fullscreen-info">
        <span class="video-live-badge">
          <span class="live-dot"></span>
          实时监控
        </span>
        <span class="video-zone-name">{{ selectedZone?.name || '' }}</span>
      </div>
    </div>
  </div>

  <!-- 智慧助手弹窗 -->
  <div v-if="isAssistantModalOpen" class="assistant-modal-overlay" @click.self="closeAssistantModal">
    <div class="assistant-modal-content">
      <button class="assistant-modal-close" @click="closeAssistantModal" aria-label="返回首页">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          <polyline points="9 22 9 12 15 12 15 22"/>
        </svg>
      </button>
      <!--http://192.168.0.102:8080/hermesweb/#/chat http://192.168.1.109:8080/hermesweb/#/chat"  -->
      <iframe
          src="/assistant/#/chat"
          class="assistant-modal-iframe"
          frameborder="0"
      ></iframe>
    </div>
  </div>

  <!-- 知识库统计弹窗 -->
  <div v-if="isKnowledgeModalOpen" class="knowledge-modal-overlay" @click.self="closeKnowledgeModal">
    <div class="knowledge-modal-content">
      <button class="knowledge-modal-close" @click="closeKnowledgeModal" aria-label="返回首页">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          <polyline points="9 22 9 12 15 12 15 22"/>
        </svg>
      </button>
      <iframe
          src="/know/datasets"
          class="knowledge-modal-iframe"
          frameborder="0"
      ></iframe>
    </div>
  </div>

  <!-- 站房运营Agent弹窗 -->
  <div v-if="isPassengerModalOpen" class="knowledge-modal-overlay" @click.self="closePassengerModal">
    <div class="knowledge-modal-content">
      <button class="knowledge-modal-close" @click="closePassengerModal" aria-label="返回首页">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          <polyline points="9 22 9 12 15 12 15 22"/>
        </svg>
      </button>
      <iframe
          src="http://192.168.128.157:3000/#/"
          class="knowledge-modal-iframe"
          frameborder="0"
      ></iframe>
    </div>
  </div>

  <!-- 能管Agent弹窗 -->
  <div v-if="isEnergyModalOpen" class="knowledge-modal-overlay" @click.self="closeEnergyModal">
    <div class="knowledge-modal-content">
      <button class="knowledge-modal-close" @click="closeEnergyModal" aria-label="返回首页">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          <polyline points="9 22 9 12 15 12 15 22"/>
        </svg>
      </button>
      <img
          :src="energyAgentImageUrl"
          class="knowledge-modal-iframe"
          style="width: 100%; height: 100%; object-fit: cover; pointer-events: none;"
      />
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
          <canvas ref="modalHeatmapCanvas" class="heatmap-canvas"></canvas>
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
                  <span class="status-item">综合评分 <b :class="zone.totalScore >= 90 ? 'green-text' : (zone.totalScore >= 75 ? 'orange-text' : 'red-text')">{{ zone.totalScore }}分</b></span>
                </div>
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
                      <!-- <span class="device-type">{{ d.type }}</span> -->
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

            <!-- 无异常项提示（放在分析上方） -->
            <div v-if="!selectedZone.dispatchTasks || selectedZone.dispatchTasks.length === 0" class="zone-normal-badge">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><path d="M22 4L12 14.01l-3-3"/></svg>
              <span>该区域运行平稳，无异常项</span>
            </div>

            <!-- 异常/区域分析打字区 -->
            <div class="zone-analysis-box">
              <div class="zone-analysis-header">
                <div class="ai-orb small" style="width:20px;height:20px;min-width:20px;min-height:20px;font-size:8px;">AI</div>
                <span class="zone-analysis-label">{{ (selectedZone.dispatchTasks && selectedZone.dispatchTasks.length > 0) ? '异常分析' : '区域分析' }}</span>
              </div>
              <div class="zone-analysis-content">
                <span class="zone-analysis-text">{{ zoneAnalysisText }}</span>
                <span v-if="!zoneAnalysisComplete" class="zone-typing-cursor">|</span>
              </div>
            </div>

            <!-- 派发任务卡片 -->
            <div v-if="selectedZone.dispatchTasks && selectedZone.dispatchTasks.length > 0" class="dispatch-task-list">
              <div v-for="(task, ti) in selectedZone.dispatchTasks" :key="ti" class="dispatch-task-card" :class="['task-domain-' + getDomainClass(task.businessDomain), 'task-level-' + task.level, { 'task-dispatched': task.dispatched }]">
                <div class="dispatch-task-header">
                  <span class="dispatch-task-domain">{{ task.businessDomain }}</span>
                  <span class="dispatch-task-level" :class="'level-' + task.level">{{ task.level }}优先级</span>
                  <span v-if="task.dispatched" class="dispatch-task-status dispatched">已采纳</span>
                  <span v-else-if="task.dispatched==0" class="dispatch-task-status pending">待决策</span>
                  <span v-else class="dispatch-task-status cancelled">已取消</span>
                </div>
                <div class="dispatch-task-body">
                  <div class="dispatch-task-field">
                    <span class="field-label">问题描述</span>
                    <span class="field-value">{{ task.problemDesc }}</span>
                  </div>
                  <div class="dispatch-task-field">
                    <span class="field-label">决策建议</span>
                    <span class="field-value">{{ task.decisionContent }}</span>
                  </div>
                  <div class="dispatch-task-field">
                    <span class="field-label">预期成效</span>
                    <span class="field-value">{{ task.expectedEffect }}</span>
                  </div>
                </div>
                <div class="dispatch-task-footer">
                  <div class="dispatch-target-agent">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/></svg>
                    <span>派发至: {{ task.targetAgentName }}</span>
                  </div>
                  <button v-if="!task.dispatched" class="dispatch-btn" @click.stop="handleDispatchTask(task, selectedZone)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
                    <span>派发</span>
                  </button>
                  <span v-else class="dispatched-label">✓ 已决策</span>
                </div>
              </div>
            </div>
            <!-- 固定实时监控视频 -->
            <div v-if="showZoneVideo" class="fixed-zone-video">
              <div class="fixed-video-header">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
                  <polygon points="23 7 16 12 23 17 23 7"/>
                  <rect x="1" y="5" width="15" height="14" rx="2" ry="2"/>
                </svg>
                实时监控
              </div>
              <div class="fixed-video-wrapper" @click="openVideoFullscreen">
                <div class="fixed-video-play-overlay">
                  <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
                    <polygon points="5 3 19 12 5 21 5 3"/>
                  </svg>
                </div>
                <div class="fixed-video-expand-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                    <polyline points="15 3 21 3 21 9"/>
                    <polyline points="9 21 3 21 3 15"/>
                    <line x1="21" y1="3" x2="14" y2="10"/>
                    <line x1="3" y1="21" x2="10" y2="14"/>
                  </svg>
                </div>
              </div>
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
  <DispatchModal ref="dispatchModalRef" v-if="isDispatchModalOpen" @close="closeDispatchModal" @confirm="handleDispatchConfirm" />

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
  <HealthModal v-if="isHealthModalOpen" @close="closeHealthModal" @openEnvAnalysis="openEnvAnalysis" />

  <!-- 设备分析弹窗 -->
  <DeviceAnalysisModal v-if="isDeviceAnalysisOpen" @close="closeDeviceAnalysis" @dispatch="handleDeviceAnalysisDispatch" />

  <!-- 告警事项列表弹窗 -->
  <div v-if="isAlertModalOpen" class="alert-modal-overlay" @click.self="closeAlertModal">
    <div class="alert-modal">
      <div class="alert-modal-header">
        <div class="alert-header-left">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
            <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
          </svg>
          <div>
            <h3>今日未确认告警事项</h3>
            <p class="alert-subtitle">共 {{ alertCount }} 条待处理事项</p>
          </div>
        </div>
        <div class="alert-header-right">
          <!-- 类型筛选 -->
          <div class="filter-selector">
            <button class="filter-btn" :class="{ active: alertFilterType === 'fault' }" @click="changeAlertFilterType('fault')">故障</button>
            <button class="filter-btn" :class="{ active: alertFilterType === 'alarm' }" @click="changeAlertFilterType('alarm')">告警</button>
          </div>
          <button class="alert-modal-close" @click="closeAlertModal">×</button>
        </div>
      </div>

      <div class="alert-modal-body">
        <div v-if="alertListData.length === 0" class="alert-empty">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="64" height="64">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22 4 12 14.01 9 11.01"/>
          </svg>
          <p>暂无未确认告警事项</p>
        </div>
        <div v-else class="alert-table-wrapper">
          <table class="alert-table">
            <thead>
            <tr>
              <th>等级</th>
              <th>设备名称</th>
              <th>位置</th>
              <th>告警信息</th>
              <!-- <th>状态</th> -->
              <th>发生时间</th>
              <th>操作</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="(item, idx) in alertListData" :key="idx" class="alert-item">
              <td>
                  <span class="alert-level" :class="item.event_level === 3 ? 'level-fault' : 'level-alarm'">
                    {{ item.levelText }}
                  </span>
              </td>
              <td class="alert-device">{{ item.device_name }}</td>
              <td class="alert-location-cell">{{ getAlertLocation(item) || '-' }}</td>
              <td class="alert-info-cell">{{ item.event_info }}</td>
              <!-- <td>
                  <span class="alert-status" :class="item.process_state === 0 ? 'status-pending' : 'status-processing'">
                    {{ item.process_state === 0 ? '待处理' : '已处理' }}
                  </span>
                </td> -->
              <td class="alert-time">{{ formatAlertTime(item.receive_time) }}</td>
              <td>
                <div class="alert-actions" v-if="item.process_state === 0">
                  <button v-if="item.id" class="alert-action-btn btn-known" @click="handleAlertKnown(item)">已知</button>
                  <button class="alert-action-btn btn-issue" @click="handleAlertToIssue(item)">AI处理</button>
                </div>
              </td>
            </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页 -->
        <div class="alert-pagination" v-if="alertTotalCount > alertPageSize">
          <button class="pagination-btn" :disabled="alertCurrentPage === 1" @click="changeAlertPage(alertCurrentPage - 1)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
          <span class="pagination-info">
            第 {{ alertCurrentPage }} / {{ Math.ceil(alertTotalCount / alertPageSize) }} 页，共 {{ alertTotalCount }} 条
          </span>
          <button class="pagination-btn" :disabled="alertCurrentPage >= Math.ceil(alertTotalCount / alertPageSize)" @click="changeAlertPage(alertCurrentPage + 1)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
  <div v-if="isAutoDiagnosisModalOpen" class="alert-modal-overlay" @click.self="closeAutoDiagnosisModal">
    <div class="alert-modal auto-diagnosis-modal">
      <div class="alert-modal-header">
        <div class="alert-header-left">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
            <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
          </svg>
          <div>
            <h3>AI分析处置</h3>
            <!-- <p class="alert-subtitle">{{ autoDiagnosisAlert?.device_name || '' }}</p> -->
          </div>
        </div>
        <button class="alert-modal-close" @click="closeAutoDiagnosisModal">×</button>
      </div>

      <div class="alert-modal-body">
        <div class="auto-diagnosis-layout">
          <!-- 左右布局 -->
          <div class="alert-modal-columns">
            <!-- 左侧：告警信息卡片 -->
            <div class="alert-modal-col alert-modal-col-left">
              <div class="alert-info-card">
                <div class="alert-info-header">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                    <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                    <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
                  </svg>
                  <span>告警信息</span>
                </div>
                <div class="auto-diagnosis-detail">
                  <div class="detail-row">
                    <span class="detail-label">告警等级</span>
                    <span class="alert-level" :class="autoDiagnosisAlert?.event_level === 3 ? 'level-fault' : 'level-alarm'">
                      {{ autoDiagnosisAlert?.event_level === 3 ? '紧急' : '重要' }}
                    </span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">设备名称</span>
                    <span class="detail-value">{{ autoDiagnosisAlert?.device_name || '-' }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">告警信息</span>
                    <span class="detail-value">{{ autoDiagnosisAlert?.event_info || '-' }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">发生时间</span>
                    <span class="detail-value">{{ formatAlertTime(autoDiagnosisAlert?.happen_time) }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">处理状态</span>
                    <span class="alert-status" :class="autoDiagnosisAlert?.process_state === 0 ? 'status-pending' : 'status-processing'">
                      {{ autoDiagnosisAlert?.process_state === 0 ? '待处理' : '已处理' }}
                    </span>
                  </div>
                  <div class="detail-row">
                    <button style="color: white; font-size: 20px;" class="test-btn" @click="() => openVideoPlayer(autoDiagnosisAlert.device_id, autoDiagnosisAlert.device_name)" title="打开视频">打开摄像头</button>
                  </div>
                </div>
              </div>
            </div>

            <!-- 右侧：AI分析 + 任务列表 -->
            <div class="alert-modal-col alert-modal-col-right">
              <!-- AI分析加载中 -->
              <div class="ai-loading" v-if="aiAnalysisLoading">
                <div class="loading-spinner"></div>
                <span>AI正在分析中...</span>
              </div>

              <!-- AI分析结果区域 -->
              <div class="ai-analysis-section" v-else-if="aiAnalysisResult">
                <div class="ai-analysis-header">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                    <path d="M12 2a10 10 0 1 0 10 10H12V2z"/>
                    <path d="M12 2a10 10 0 0 1 10 10"/>
                    <circle cx="12" cy="12" r="3"/>
                  </svg>
                  <span>AI分析</span>
                </div>
                <div class="ai-analysis-content">
                  <div class="analysis-item" v-if="aiAnalysisResult.alarmAnalysis">
                    <span class="analysis-label">分析总结:</span>
                    <span class="analysis-value">{{ aiAnalysisResult.alarmAnalysis }}</span>
                  </div>
                  <div class="analysis-item" v-if="aiAnalysisResult.suggestion">
                    <span class="analysis-label">处理建议:</span>
                    <span class="analysis-value">{{ aiAnalysisResult.suggestion }}</span>
                  </div>
                </div>
              </div>

              <!-- 派发任务列表 -->
              <div class="dispatch-tasks-card" v-if="aiAnalysisResult && aiAnalysisResult.dispatchTasks && aiAnalysisResult.dispatchTasks.length > 0">
                <div class="dispatch-tasks-header">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                    <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/>
                    <rect x="9" y="3" width="6" height="4" rx="1"/>
                    <path d="M9 14l2 2 4-4"/>
                  </svg>
                  <span>协作任务</span>
                </div>
                <div class="dispatch-tasks-list">
                  <div class="task-card" v-for="(task, idx) in aiAnalysisResult.dispatchTasks" :key="idx">
                    <div class="task-card-header">
                      <span class="task-level" style="margin-left: 8px;" :class="task.level === '高' ? 'level-high' : task.level === '中' ? 'level-medium' : 'level-low'">{{ task.level }}</span>
                      <span class="task-card-title">派发任务 {{ idx + 1 }}</span>
                    </div>
                    <div class="task-detail-rows">
                      <div class="detail-row" v-if="task.problemDesc">
                        <span class="detail-label">问题描述</span>
                        <span class="detail-value">{{ task.problemDesc }}</span>
                      </div>
                      <div class="detail-row" v-if="task.decision_content">
                        <span class="detail-label">决策内容</span>
                        <span class="detail-value">{{ task.decision_content }}</span>
                      </div>
                      <div class="detail-row" v-if="task.targetAgentName">
                        <span class="detail-label">目标Agent</span>
                        <span class="detail-value">{{ task.targetAgentName }}</span>
                      </div>
                      <div class="detail-row" v-if="task.businessDomain">
                        <span class="detail-label">业务域</span>
                        <span class="detail-value">{{ task.businessDomain }}</span>
                      </div>
                      <div class="detail-row" v-if="task.expectedEffect">
                        <span class="detail-label">预期成效</span>
                        <span class="detail-value">{{ task.expectedEffect }}</span>
                      </div>
                    </div>
                    <div class="task-actions">
                      <button class="task-btn btn-confirm" @click="confirmDispatch(task)" :disabled="dispatchTaskLoading">
                        <span v-if="dispatchTaskLoading">处理中...</span>
                        <span v-else>确认执行</span>
                      </button>
                      <button class="task-btn btn-cancel" @click="cancelTask(task.id)">取消</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 任务执行结果 -->
          <div class="task-result-card" v-if="dispatchTaskResult">
            <div class="task-result-header">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
              <span>任务执行结果</span>
            </div>
            <div class="task-result-content">
              <div class="result-row">
                <span class="result-label">执行状态</span>
                <span class="result-value status-done" v-if="dispatchTaskResult.resultExecuteStatus === '1' || dispatchTaskResult.resultExecuteStatus === 1">成功</span>
                <span class="result-value status-fail" v-else-if="dispatchTaskResult.resultExecuteStatus === '2' || dispatchTaskResult.resultExecuteStatus === 2">失败</span>
                <span class="result-value" v-else>未执行</span>
              </div>
              <div class="result-row">
                <span class="result-label">执行人</span>
                <span class="result-value">{{ dispatchTaskResult.resultExecuteUser }}</span>
              </div>
              <div class="result-row">
                <span class="result-label">执行描述</span>
                <span class="result-value">{{ dispatchTaskResult.resultDesc }}</span>
              </div>
              <div class="result-row" v-if="dispatchTaskResult.resultData">
                <span class="result-label">执行数据</span>
                <span class="result-value result-data">{{ dispatchTaskResult.resultData }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 事项处理弹窗 -->
  <WarningModal v-if="isWarningModalOpen" @close="closeWarningModal" />

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

  <!-- Agent协作详情弹窗 -->
  <AgentCollabModal v-if="isAgentCollabModalOpen" :visible="isAgentCollabModalOpen" @close="closeAgentCollabModal" />

  <!-- 任务详情弹窗 -->
  <TaskDetailModal
      :visible="isTaskDetailModalOpen"
      :task="selectedTaskDetail"
      @close="closeTaskDetailModal"
  />

  <!-- 服务分析详情弹窗 -->
  <ServiceAnalysisModal v-if="isServiceAnalysisModalOpen" @close="closeServiceAnalysisModal" />

  <MapModal v-if="isMapModalOpen" @close="closeMapModal" />
  <LifeExpectancyModal v-if="isLifeModalOpen" :org-id="userStore.org_id || 3" @close="closeLifeModal" />

  <!-- Dispatch任务加载动画 -->
  <Transition name="fade">
    <div v-if="dispatchLoadingVisible" class="dispatch-loading-overlay">
      <div class="dispatch-loading-modal">
        <button class="dispatch-loading-close" @click="closeDispatchLoading" title="关闭">&times;</button>
        <div class="dispatch-loading-spinner">
          <div class="spinner-ring"></div>
          <div class="spinner-ring"></div>
          <div class="spinner-ring"></div>
          <div class="spinner-core">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"/>
            </svg>
          </div>
        </div>
        <div class="dispatch-loading-text">
          <h3>任务执行中</h3>
          <p>AI正在分析并执行任务，请耐心等待...</p>
        </div>
        <div class="dispatch-loading-dots">
          <span></span><span></span><span></span>
        </div>
      </div>
    </div>
  </Transition>

  <!-- 环境分析弹窗 -->
  <EnvAnalysisModal v-if="isEnvAnalysisModalOpen" @close="closeEnvAnalysis" />

  <!-- 算力实时指标弹窗 -->
  <ComputingPowerModal
      v-if="isBaseMonitorModalOpen"
      :data="computingPower"
      @close="closeBaseMonitorModal"
  />

  <!-- Toast 提示 -->
  <Toast
      v-if="toastConfig.show"
      :type="toastConfig.type"
      :title="toastConfig.title"
      :description="toastConfig.description"
      @close="toastConfig.show = false"
  />
</template>

<style>

:root {
  /* 深蓝色科技主题 - 默认主题 */
  --bg: rgb(0, 66, 126);
  --bg-gradient-end: #051839;
  --panel: rgba(10, 31, 68, 0.65);
  --panel-solid: #0a1f44;
  --modal-bg: #0d3967;
  --line: rgba(0, 162, 255, 0.3);
  --line-strong: rgba(0, 162, 255, 0.5);
  --blue: #00d8ff;
  --blue-bright: #00a2ff;
  --blue-deep: #0072ff;
  --blue-glow: #00f6ff;
  --green: #00ffcc;
  --shield-green: #00ffcc;
  --green-soft: rgba(0, 255, 204, 0.1);
  --orange: #ff9933;
  --orange-soft: rgba(255, 153, 51, 0.15);
  --red: #ff3333;
  --red-soft: rgba(255, 51, 51, 0.15);
  --purple: #aa66ff;
  --cyan: #00e5ff;
  --text: #ffffff;
  --text-secondary: #e1f5fe;
  --text-tertiary: #90caf9;
  --text-muted: white;
  --text-label: #a2b9d6;
  --card-bg: rgba(10, 31, 68, 0.65);
  --muted: white;
  --soft: rgba(0, 150, 255, 0.08);
  --shadow: 0 10px 28px rgba(0, 100, 200, 0.3);
  --glow: 0 0 12px rgba(0, 114, 255, 0.5);
  --glow-strong: 0 0 20px rgba(0, 216, 255, 0.6);
  --overlay: rgba(0, 0, 0, 0.6);
  --border-radius: 4px;
  --font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Microsoft YaHei", monospace;
}

[data-theme="dark"] {
  /* 深蓝色科技主题 - 深色模式 */
  --bg: rgb(0, 66, 126);
  --bg-gradient-end: #051839;
  --panel: rgba(10, 31, 68, 0.65);
  --panel-solid: #0a1f44;
  --modal-bg: #0d3967;
  --line: rgba(0, 162, 255, 0.3);
  --line-strong: rgba(0, 162, 255, 0.5);
  --blue: #00d8ff;
  --blue-bright: #00a2ff;
  --blue-deep: #0072ff;
  --blue-glow: #00f6ff;
  --green: #00ffcc;
  --shield-green: #00ffcc;
  --green-soft: rgba(0, 255, 204, 0.1);
  --orange: #ff9933;
  --orange-soft: rgba(255, 153, 51, 0.15);
  --red: #ff3333;
  --red-soft: rgba(255, 51, 51, 0.15);
  --purple: #aa66ff;
  --cyan: #00e5ff;
  --text: #ffffff;
  --text-secondary: #e1f5fe;
  --text-tertiary: #90caf9;
  --text-muted: white;
  --text-label: #a2b9d6;
  --card-bg: rgba(10, 31, 68, 0.65);
  --muted: white;
  --soft: rgba(0, 150, 255, 0.08);
  --shadow: 0 10px 28px rgba(0, 100, 200, 0.3);
  --glow: 0 0 12px rgba(0, 114, 255, 0.5);
  --glow-strong: 0 0 20px rgba(0, 216, 255, 0.6);
  --overlay: rgba(0, 0, 0, 0.6);
  --border-radius: 4px;
  --font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Microsoft YaHei", monospace;
}

* {
  box-sizing: border-box;
  letter-spacing: 0;
}

body {
  margin: 0;
  min-height: 100vh;
  color: var(--text);
  background: rgb(0, 66, 126);
  font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Microsoft YaHei", monospace;
  transition: background 0.3s, color 0.3s;
  position: relative;
}

body::before {
  content: '';
  position: fixed;
  inset: 0;
  opacity: 0;
  z-index: -1;
  pointer-events: none;
}

[data-theme="dark"] body {
  background: rgb(0, 66, 126);
}

/* 科技主题通用样式 */
.sub-title-line {
  height: 2px;
  background: linear-gradient(to right, rgba(0, 216, 255, 0.8) 0%, rgba(0, 162, 255, 0.4) 30%, rgba(0, 0, 0, 0) 100%);
  margin-top: 6px;
  margin-bottom: 12px;
}

.tech-card {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--border-radius);
  box-shadow: var(--glow);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.tech-modal-overlay {
  background: var(--overlay);
}

.tech-modal {
  background: var(--modal-bg);
  border: 1px solid var(--line);
  border-radius: var(--border-radius);
  box-shadow: var(--glow-strong);
}

.tech-glow {
  box-shadow: var(--glow);
}

.tech-glow-strong {
  box-shadow: var(--glow-strong);
}

.tech-section-title {
  font-size: 14px;
  font-weight: bold;
  color: #ffffff;
  border-left: 4px solid #00bcff;
  padding-left: 8px;
}

.text-primary-value {
  color: #ffffff;
}

.text-secondary-value {
  color: #e1f5fe;
}

.text-tertiary-value {
  color: #90caf9;
}

.text-muted-value {
  color: white;
}

.text-label-value {
  color: #a2b9d6;
}

.text-highlight-cyan {
  color: #00fbfb;
}

.text-status-safe {
  color: #00ffcc;
}

.text-status-danger {
  color: #ff3333;
}

svg {
  display: block;
}

.dashboard {
  height: 100%;
  width: 100%;
  margin: 0 auto;
  padding: 0 10px;
  display: flex;
  flex-direction: column;
}

.page-header {
  background-size: cover;
  background-position: center center;
  background-repeat: no-repeat;
  position: relative;
  padding: 24px 32px ;
  height: 70px;
  border-bottom: 1px solid rgba(80, 120, 160, .2);
}

[data-theme="dark"] .page-header {
  /* background: linear-gradient(135deg, rgba(0, 150, 255, 0.12) 0%, rgba(0, 255, 136, 0.08) 50%, rgba(170, 102, 255, 0.06) 100%); */
  /* border-color: var(--line-strong); */
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
  color: white;
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
  color: white;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: var(--header-right-margin-top, 0);
}

.header-station-left {
  position: absolute;
  top: var(--header-station-left-top, 10px);
  left: 4px;
  display: flex;
  align-items: center;
  gap: 12px;
  z-index: 1;
}

.station-icon {
  width: var(--header-station-left-font-size, 20px);
  height: var(--header-station-left-font-size, 20px);
  animation: iconRotate 8s linear infinite;
  margin-left: 40px;
}

.station-text {
  font-size: var(--header-station-left-font-size, 20px);
  font-weight: 700;
  color: var(--text);
  letter-spacing: 1px;
  text-shadow: 0 2px 20px rgba(0,0,0,0.3);
  pointer-events: none;
}

.header-station-left::after {
  content: '';
  position: absolute;
  bottom: -12px;
  left: 0;
  width: var(--header-station-left-border, 180px);
  height: 2px;
  background: linear-gradient(90deg,
  rgba(0, 180, 255, 0.9) 0%,
  rgba(0, 180, 255, 0.5) 20%,
  rgba(0, 180, 255, 0.2) 40%,
  rgba(0, 180, 255, 0.05) 60%,
  rgba(0, 180, 255, 0) 100%
  );
  border-radius: 1px;
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

.alert-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  background: rgba(100, 100, 100, 0.1);
  border: 1px solid rgba(100, 100, 100, 0.2);
  border-radius: 999px;
  color: #666;
  font-size: 13px;
  font-weight: 800;
  position: relative;
}

.alert-badge.has-alert {
  background: rgba(255, 68, 68, 0.1);
  border-color: rgba(255, 68, 68, 0.3);
  color: #ff4444;
  animation: alert-flash 1s ease-in-out infinite;
}

[data-theme="dark"] .alert-badge {
  background: rgba(100, 100, 100, 0.15);
  border-color: rgba(100, 100, 100, 0.25);
  color: #888;
}

[data-theme="dark"] .alert-badge.has-alert {
  background: rgba(255, 68, 68, 0.15);
  border-color: rgba(255, 68, 68, 0.35);
  color: #ff4444;
}

.test-btn {
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(59, 130, 246, 0.15);
  border: 1px solid rgba(59, 130, 246, 0.3);
  color: #3b82f6;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.test-btn:hover {
  background: rgba(59, 130, 246, 0.25);
}

.alert-icon {
  width: 18px;
  height: 18px;
}

.alert-count {
  min-width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #ff4444;
  color: #fff;
  font-size: 11px;
  font-weight: 900;
  border-radius: 999px;
  padding: 0 5px;
}

@keyframes alert-flash {
  0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(255, 68, 68, 0); }
  50% { opacity: 0.85; box-shadow: 0 0 8px 2px rgba(255, 68, 68, 0.4); }
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

.header-badge.bg-task-running {
  background: rgba(255, 152, 0, 0.12);
  border-color: rgba(255, 152, 0, 0.35);
  color: #ff9800;
}

.pulse-orange {
  background: #ff9800 !important;
  box-shadow: 0 0 8px rgba(255, 152, 0, 0.6);
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
  color: white;
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

.admin-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: rgba(0, 100, 200, 0.2);
  border: 1px solid var(--blue);
  border-radius: 6px;
  color: var(--blue);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.admin-btn:hover {
  background: rgba(0, 150, 255, 0.3);
  box-shadow: 0 0 12px rgba(0, 212, 255, 0.3);
}

.admin-btn svg {
  flex-shrink: 0;
}

.top-grid,
.mid-grid,
.bottom-grid {
  display: grid;
  gap: 10px;
}

/*      grid-template-columns: minmax(260px, 0.88fr) minmax(380px, 1.25fr) minmax(340px, 1.14fr) minmax(390px, 1.34fr); */
/* padding-top: var(--top-grid-margin-top, 0); */
.top-grid {
  grid-template-columns: 300px 1fr 520px;
  gap: 14px;
  align-items: stretch;
  height: 62vh;
  overflow:  hidden;
}
.wudatupo{
  text-align: center;
  font-weight: 600;
  height: var(--top-grid-margin-top, 0);
  line-height: var(--top-grid-margin-top, 0);
}

.top-grid .left-column {
  display: flex;
  flex-direction: column;
}

.top-grid .left-column .status-card-wrapper {
  flex: 1;
}

.top-grid .left-column .status-card-wrapper .card {
  height: 100%;
}

.top-grid .right-column {
  display: flex;
  flex-direction: column;
}

.top-grid .zone-model-card {
  min-height: 480px;
}

.left-column .status-card-wrapper:first-child .card {
  flex: 1;
}

.left-column .status-card-wrapper:last-child .card {
  flex: 1;
}

.ai-suggestions-card .suggestions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ai-suggestions-card{
  height: 100%;
}
.bottom-grid {
  padding-top: 10px;
  grid-template-columns: minmax(300px, 1.04fr) minmax(310px, 1fr) minmax(280px, 1fr) minmax(330px, 1.1fr) minmax(300px, 1fr);
  flex: 1;
  /* height: 100%; */
  min-height: 0;
}

.card {
  /* background: rgba(255, 255, 255, 0.84); */
  border: 1px solid var(--line);
  border-radius: 9px;
  /* box-shadow: var(--shadow); */
  transition: background 0.3s, border-color 0.3s, box-shadow 0.3s;
}

[data-theme="dark"] .card {
  /* background: #000d21;
      border-color: var(--line);
      box-shadow: var(--shadow), 0 0 15px rgba(0, 150, 255, 0.08); */
}

.card {
  padding: 12px 14px;
  overflow: hidden;
}

.top-grid .card {
  /* height: 56vh; */
  min-height: 259px;
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
  color: white;
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

.ico.blue {
  color: var(--blue);
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
  padding: 0;
  border: 0;
  background: transparent;
  font-family: inherit;
}

.more:focus-visible {
  outline: 2px solid var(--blue);
  outline-offset: 3px;
  border-radius: 3px;
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
  /* --hover-color: var(--green); */
  height: 100%;
}

.status-card-wrapper:hover {
  /* background: color-mix(in srgb, var(--hover-color) 5%, transparent); */
  /* box-shadow: 0 0px 4px color-mix(in srgb, var(--hover-color) 15%, transparent); */
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
  /* border: 2px solid transparent; */
  /* transition: border-color 0.3s ease; */
  pointer-events: none;
}

.status-card-wrapper:hover::before {
  /* border-color: color-mix(in srgb, var(--hover-color) 40%, transparent); */
}

.status-card-wrapper.status-card-no-hover {
  cursor: default !important;
}
.status-card-wrapper.status-card-no-hover:hover {
  transform: none !important;
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
  color: white;
  font-size: 16px;
  font-weight: 700;
}

[data-theme="dark"] .health-desc {
  color: white;
}

.sparkline-chart {
  height: 54px;
  margin-top: 8px;
  width: 100%;
}

.health-full-card {
  display: flex;
  flex-direction: column;
}

.health-details {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
  flex: 1;
}

.health-section h5 {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 900;
  color: #0b51ff;
  border-bottom: 1px solid #e1e9f7;
  padding-bottom: 4px;
}

.health-section.clickable {
  cursor: pointer;
}
.health-section.clickable:hover {
  opacity: 0.85;
}

[data-theme="dark"] .health-section h5 {
  color: var(--blue);
  border-bottom-color: var(--line);
}

.health-metrics {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
}

.health-metric {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 8px;
  background: var(--soft);
  border-radius: 6px;
  font-size: 16px;
}

.health-metric .label {
  color: #556;
  font-weight: 700;
}

[data-theme="dark"] .health-metric .label {
  color: white;
}

.health-metric .value {
  color: #172966;
  font-weight: 900;
  font-size: 12px;
}

[data-theme="dark"] .health-metric .value {
  color: #a0c4e8;
}

/* ===== 派发任务列表 ===== */
.dispatch-task-section {
  margin-top: 10px;
}

.dispatch-task-section h5 {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 900;
  color: #0b51ff;
  border-bottom: 1px solid #e1e9f7;
  padding-bottom: 4px;
}

[data-theme="dark"] .dispatch-task-section h5 {
  color: var(--blue);
  border-bottom-color: var(--line);
}

.dispatch-task-list {
  max-height: 180px;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  gap: 6px;
  scrollbar-width: thin;
  scrollbar-color: transparent transparent;
}

.dispatch-task-list:hover {
  scrollbar-color: rgba(0, 150, 255, 0.3) rgba(0, 0, 0, 0.05);
}

[data-theme="dark"] .dispatch-task-list {
  scrollbar-color: rgba(0, 150, 255, 0.2) rgba(0, 0, 0, 0.2);
}

[data-theme="dark"] .dispatch-task-list:hover {
  scrollbar-color: rgba(0, 150, 255, 0.4) rgba(0, 0, 0, 0.3);
}

.dispatch-task-list::-webkit-scrollbar {
  width: 4px;
}

.dispatch-task-list::-webkit-scrollbar-track {
  background: transparent;
}

.dispatch-task-list::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 2px;
}

.dispatch-task-list:hover::-webkit-scrollbar-thumb {
  background: rgba(0, 150, 255, 0.3);
}

[data-theme="dark"] .dispatch-task-list:hover::-webkit-scrollbar-thumb {
  background: rgba(0, 150, 255, 0.4);
}

.task-empty {
  text-align: center;
  color: #999;
  font-size: 12px;
  padding: 20px 0;
}

[data-theme="dark"] .task-empty {
  color: #667;
}

.task-item {
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--soft);
  border-left: 0.5px solid var(--blue);
  transition: background 0.2s;
}

.task-item:hover {
  background: rgba(0, 150, 255, 0.08);
}

[data-theme="dark"] .task-item {
  background: rgba(255, 255, 255, 0.03);
  border-left-color: var(--blue);
}

[data-theme="dark"] .task-item:hover {
  background: rgba(0, 150, 255, 0.1);
}

.task-item.task-hint {
  border-left-color: #06b6d4;
}

.task-item.task-collaboration {
  border-left-color: #6366f1;
}

.task-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.task-domain {
  font-size: 11px;
  font-weight: 700;
  color: #334;
  padding: 1px 6px;
  background: rgba(0, 150, 255, 0.1);
  border-radius: 3px;
}

[data-theme="dark"] .task-domain {
  color: #9cf;
  background: rgba(0, 150, 255, 0.15);
}

.task-type-tag {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 5px;
  border-radius: 3px;
}

.task-type-tag.tag-hint {
  background: rgba(6, 182, 212, 0.15);
  color: #06b6d4;
}
.task-type-tag.tag-collaboration {
  background: rgba(6, 182, 212, 0.15);
  color: #06b6d4;
}

.task-type-tag.tag-collaboration {
  background: rgba(99, 102, 241, 0.15);
  color: #6366f1;
}

.task-level {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 3px;
  margin-left: auto;
}

.task-level.level-高 {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.task-level.level-中 {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.task-level.level-低 {
  background: rgba(34, 197, 94, 0.15);
  color: #22c55e;
}

.task-desc {
  font-size: 11px;
  color: white;
  line-height: 1.4;
  margin-bottom: 4px;
}

[data-theme="dark"] .task-desc {
  color: white;
}

.task-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 10px;
}

.task-agent {
  color: #667;
  font-weight: 600;
}

[data-theme="dark"] .task-agent {
  color: #889;
}

.task-status {
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 3px;
}

.task-status.pending {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.task-status.dispatched {
  background: rgba(34, 197, 94, 0.15);
  color: #22c55e;
}

.collab-desc {
  color: #556;
  font-size: 10px;
  font-weight: 600;
  line-height: 1.3;
  display: block;
  margin-top: 2px;
  padding-left: 4px;
  border-left: 0.5px solid #d0d8e8;
}

[data-theme="dark"] .collab-desc {
  color: #7799bb;
  border-left-color: #334455;
}

.suggestion.agent-collab {
  grid-template-columns: 1fr 68px;
  gap: 6px;
  padding: 10px 12px;
  min-height: 68px;
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
  fill: white;
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
  color: white;
  background: rgba(0, 100, 200, 0.15);
}

.env-table div:nth-last-child(-n+6) {
  border-bottom: 0;
}

.green-text {
  color: var(--green);
}

.blue-text {
  color: white;
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
  padding: 16px 14px;
  display: flex;
  flex-direction: column;
}

/* ========== 新版分区态势：标题栏增强 ========== */
.zone-title-bar {
  display: flex !important;
  align-items: center;
  gap: 12px;
  flex-wrap: nowrap;
}
.zone-title-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ai-inspect-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(0, 150, 255, 0.1);
  border: 1px solid rgba(0, 150, 255, 0.3);
  font-size: 11px;
  font-weight: 800;
  color: var(--blue);
}
.ai-inspect-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--blue);
  animation: ai-pulse-dot 2s ease-in-out infinite;
}
@keyframes ai-pulse-dot {
  0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(0, 150, 255, 0.5); }
  50% { opacity: 0.7; box-shadow: 0 0 0 4px rgba(0, 150, 255, 0); }
}

.zone-menu-btn {
  background: transparent !important;
}

/* ========== 新版分区态势：2x2网格卡片 ========== */
.zone-new-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}
.zone-new-card {
  position: relative;
  border-radius: 10px;
  border: 1px solid;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: all 0.28s ease;
  overflow: hidden;
  background: linear-gradient( 137deg, rgb(10 86 154), rgb(2 43 81));
}

.zone-new-card.green-box {
  border-color: rgba(0, 200, 120, 0.35);
  box-shadow: 0 2px 12px rgba(0, 200, 120, 0.08), inset 0 1px 0 rgba(0, 255, 136, 0.08);
}

.zone-new-card.orange-box {
  border-color: rgba(100, 150, 210, 0.08);
  box-shadow: 0 2px 12px rgba(255, 140, 0, 0.1), inset 0 1px 0 rgba(255, 180, 0, 0.08);
}

.zone-new-card.blue-box {
  border-color: rgba(60, 140, 255, 0.3);
  box-shadow: 0 2px 12px rgba(60, 140, 255, 0.08), inset 0 1px 0 rgba(100, 180, 255, 0.08);
}

.zone-new-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.12);
}
[data-theme="dark"] .zone-new-card:hover {
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

/* 卡片头部 */
.znc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.znc-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.znc-name {
  font-size: 15px;
  font-weight: 900;
  color: #e8f4ff;
  letter-spacing: 0.3px;
}

.znc-attention-tag {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 900;
  background: rgba(255, 160, 0, 0.15);
  color: #ff9500;
  border: 1px solid rgba(255, 160, 0, 0.3);
}
.znc-device-stats {
  display: flex;
  gap: 12px;
  font-size: 11px;
  font-weight: 700;
  color: #8899aa;
}
.znc-device-stats b {
  color: #c0d0e0;
  font-weight: 900;
}

/* 卡片主体：左右布局 */
.znc-body {
  display: grid;
  grid-template-columns: 130px 1fr;
  gap: 14px;
  align-items: start;
}

/* 左侧：AI圆环 - 时钟扫描风格（参考EnvAnalysisModal spinner-ring）*/
.znc-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.ai-ring-container {
  position: relative;
  width: 108px;
  height: 108px;
}
/* 时钟外圈 - 静态底环 */
.ai-clock-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2px solid rgba(100, 160, 220, 0.12);
}
/* 扫描旋转环1 - 主色 */
.ai-scan-ring-1 {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2.5px solid transparent;
  border-top-color: var(--blue, #0090ee);
  animation: aiClockSpin 3s cubic-bezier(0.5, 0, 0.5, 1) infinite;
  filter: drop-shadow(0 0 4px rgba(0, 144, 238, 0.35));
}
/* 扫描旋转环2 - 绿色（反向） */
.ai-scan-ring-2 {
  position: absolute;
  inset: 6px;
  border-radius: 50%;
  border: 2px solid transparent;
  border-top-color: var(--green, #00c878);
  animation: aiClockSpin 2.2s cubic-bezier(0.5, 0, 0.5, 1) infinite reverse;
  filter: drop-shadow(0 0 3px rgba(0, 200, 120, 0.25));
}
/* 扫描旋转环3 - 橙色（内圈） */
.ai-scan-ring-3 {
  position: absolute;
  inset: 14px;
  border-radius: 50%;
  border: 1.5px solid transparent;
  border-top-color: var(--orange, #ffa000);
  animation: aiClockSpin 1.6s cubic-bezier(0.5, 0, 0.5, 1) infinite;
  opacity: 0.7;
}
@keyframes aiClockSpin {
  to { transform: rotate(360deg); }
}
/* SVG刻度层叠在上面 */
.ai-ring-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.ai-ring-bg {
  fill: none;
  stroke: rgba(100, 160, 220, 0.08);
  stroke-width: 1;
}
.ai-ring-track {
  fill: none;
  stroke: url(#aiRingGradient);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-dasharray: 327;
  stroke-dashoffset: calc(327 * (1 - var(--ring-progress, 0.85)));
  transform: rotate(-90deg);
  transform-origin: center;
  transition: stroke-dashoffset 1.5s ease;
  opacity: 0.6;
}
.ai-ring-glow {
  display: none; /* 时钟模式下不需要发光层 */
}
.ai-ring-ticks .tick-major {
  stroke: rgba(100, 170, 240, 0.18);
  stroke-width: 1.5;
  stroke-linecap: round;
}
.ai-ring-ticks .tick-minor {
  stroke: rgba(100, 170, 240, 0.07);
  stroke-width: 0.8;
  stroke-linecap: round;
}
/* 扫描光晕效果（conic-gradient旋转） */
.ai-ring-container::before {
  content: '';
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  background: conic-gradient(from 0deg, transparent 0%, rgba(0, 144, 238, 0.08) 15%, transparent 30%);
  animation: aiRingGlow 4s linear infinite;
  z-index: 1;
  pointer-events: none;
}
@keyframes aiRingGlow {
  to { transform: rotate(360deg); }
}
.ai-ring-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle, rgba(0, 144, 238, 0.12) 0%, transparent 70%);
  z-index: 2;
}
.ai-ring-label {
  font-size: 15px;
  font-weight: 900;
  color: var(--blue, #0090ee);
  text-shadow: 0 0 10px rgba(0, 144, 238, 0.45);
  letter-spacing: 1px;
}
.znc-inspect-status {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 800;
  padding: 3px 8px;
  border-radius: 999px;
}
.znc-inspect-status.inspect-ok {
  color: #00c070;
  background: rgba(0, 200, 112, 0.1);
}
.znc-inspect-status.inspect-warn {
  color: #ff8c00;
  background: rgba(255, 140, 0, 0.1);
}
.znc-inspect-time {
  font-size: 10px;
  color: #668099;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

/* 右侧：AI洞察面板 */
.znc-right {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 110px;
}
.znc-insight-header {
  display: flex;
  align-items: center;
  gap: 6px;
}
.znc-brain-icon {
  color: #00a0ff;
  opacity: 0.8;
}
.znc-insight-title {
  font-size: 13px;
  font-weight: 800;
  color: #00a0ff;
}
.znc-insight-content {
  flex: 1;width: 310px;
  border-radius: 8px;
  padding: 10px 12px;
  border: 1px solid;
}
.znc-insight-content.insight-ok {
  background: rgba(0, 200, 112, 0.04);
  border-color: rgba(0, 200, 112, 0.12);
}
.znc-insight-content.insight-warn {
  background: rgba(255, 120, 0, 0.04);
  border-color: rgba(255, 120, 0, 0.15);
}

.insight-status-line {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 800;
}
.insight-warning-icon { color: #ff8c00; flex-shrink: 0; }
.insight-ok-icon { color: #00c070; flex-shrink: 0; }
.insight-alert-count { color: #ff5522; }
.insight-ok-text { color: #00c070; }
.insight-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  line-height: 2;
}
.insight-item {
  padding-left: 12px;
  position: relative;
}
.insight-item::before {
  content: '•';
  position: absolute;
  left: 2px;
  color: #99aabb;
}
/* 真实任务列表：problem_desc + 小手跳转按钮 */
.insight-item.insight-task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  line-height: 1.5;
}
.insight-task-desc {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.insight-hand-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 1px solid rgba(0, 160, 255, 0.35);
  border-radius: 50%;
  background: rgba(0, 160, 255, 0.08);
  color: #00a0ff;
  cursor: pointer;
  transition: all 0.2s;
}
.insight-hand-btn:hover {
  background: rgba(0, 160, 255, 0.22);
  border-color: #00a0ff;
  transform: scale(1.12);
}
/* 底部指标栏 */
.znc-footer {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid rgba(100, 160, 220, 0.1);
}

.znc-metric {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgb(36 97 158 / 50%);
  border: 1px solid rgba(100, 150, 210, 0.08);
}
.znc-metric.clickable {
  cursor: pointer;
  transition: all 0.2s ease;
}
.znc-metric.clickable:hover {
  background: rgb(50 120 185 / 60%);
  border-color: rgba(100, 180, 255, 0.3);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 140, 255, 0.15);
}

.znc-metric-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.znc-metric-icon.env-icon {
  background: rgba(0, 200, 112, 0.1);
  color: #00c070;
}
.znc-metric-icon.device-icon {
  background: rgba(0, 140, 255, 0.1);
  color: #0090ee;
}
.znc-metric-icon.energy-icon {
  background: rgba(255, 160, 0, 0.1);
  color: #ffa000;
}
.znc-metric-body {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.znc-metric-label {
  font-size: 10px;
  font-weight: 700;
  color: #c9ddf1;
}
.znc-metric-value {
  font-size: 12px;
  font-weight: 900;
  line-height: 1.3;
}
.znc-metric-sub {
  font-size: 9px;
  font-weight: 700;
  color: #99aabb;
}

/* ========== 底部AI巡检流程条 ========== */
.zone-inspection-bar {
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid rgba(100, 150, 210, 0.1);
  display: flex;
  align-items: center;
  gap: 20px;
}

.inspection-flow {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.flow-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 64px;
  opacity: 0.4;
  transition: opacity 0.3s;
}
.flow-step.active { opacity: 1; }
.flow-step.done { opacity: 0.85; }
.flow-step-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--dot-color, #00a0ff);
  box-shadow: 0 0 8px var(--dot-color, #00a0ff);
  animation: flow-dot-blink 1.4s ease-in-out infinite;
}
@keyframes flow-dot-blink {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.25; transform: scale(0.7); }
}
.flow-step-label {
  font-size: 10px;
  font-weight: 800;
  color: #667888;
  white-space: nowrap;
}

.flow-step-desc {
  font-size: 9px;
  color: #99aab8;
  white-space: nowrap;
}

.flow-arrow {
  margin: 0 2px;
  flex-shrink: 0;
}
.flow-arrow-track {
  width: 28px;
  height: 2px;
  background: rgba(0, 160, 255, 0.12);
  border-radius: 2px;
  overflow: hidden;
  position: relative;
}
.flow-arrow-pulse {
  position: absolute;
  top: 0;
  left: 0;
  width: 10px;
  height: 100%;
  background: linear-gradient(90deg, transparent, #00a0ff, transparent);
  border-radius: 2px;
  animation: flow-arrow-move 1.6s linear infinite;
}
@keyframes flow-arrow-move {
  0% { transform: translateX(-10px); opacity: 0; }
  15% { opacity: 1; }
  85% { opacity: 1; }
  100% { transform: translateX(28px); opacity: 0; }
}

/* 巡检统计区域 */
.inspection-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
  margin-left: auto;
}
.inspect-stat-item {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.inspect-stat-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.inspect-stat-main {
  font-size: 12px;
  font-weight: 900;
}
.inspect-stat-sub {
  font-size: 10px;
  color: #8899aa;
  font-weight: 700;
}
.inspect-stat-divider {
  width: 1px;
  height: 36px;
  background: rgba(100, 150, 210, 0.15);
  flex-shrink: 0;
}
.inspect-stat-group {
  display: flex;
  align-items: center;
  gap: 12px;
}
.inspect-stat-label {
  font-size: 11px;
  font-weight: 800;
  color: #667788;
  white-space: nowrap;
}
.inspect-stat-numbers {
  display: flex;
  gap: 16px;
}
.stat-num {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.stat-num-value {
  font-size: 17px;
  font-weight: 900;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.stat-num-label {
  font-size: 9px;
  color: #8899aa;
  font-weight: 700;
}

/* 迷你圆环 */
.mini-ring-bg {
  fill: none;
  stroke: rgba(100, 160, 220, 0.15);
  stroke-width: 2.5;
}
.mini-ring-fill {
  fill: none;
  stroke: #00c878;
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-dasharray: 107;
  stroke-dashoffset: calc(107 * (1 - var(--p, 1)));
  transform: rotate(-90deg);
  transform-origin: center;
}
.mini-ring-text {
  font-size: 9px;
  font-weight: 900;
  fill: #00c878;
}

/* SVG渐变定义（通过CSS注入） */
.zone-cards-section {
  --ai-ring-color-1: var(--blue, #0090ee);
  --ai-ring-color-2: var(--blue-dark, #0060cc);
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
  /* 父级 .zone-viewport 已固定 510px 高度，无需在此重复设置 */
}

.zone-status-summary {
  display: flex;
  gap: 16px;
  margin-bottom: 14px;
}

.zone-status-summary-inline {
  display: flex;
  align-items: center;
  gap: 50px;
}

.zone-status-summary-inline .summary-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.zone-status-summary-inline .summary-icon {
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.zone-status-summary-inline .summary-icon svg {
  width: 14px;
  height: 14px;
}

.zone-status-summary-inline .summary-normal .summary-icon {
  color: #10b981;
}

.zone-status-summary-inline .summary-alarm .summary-icon {
  color: #f59e0b;
}

.zone-status-summary-inline .summary-label {
  font-size: 16px;
  color: rgba(255,255,255,0.6);
}

.zone-status-summary-inline .summary-value {
  font-size: 13px;
  font-weight: 700;
}

.zone-status-summary-inline .summary-normal .summary-value {
  color: #10b981;
}

.zone-status-summary-inline .summary-alarm .summary-value {
  color: #f59e0b;
}

.score-legend {
  margin-bottom: 14px;
  padding: 6px 12px;
  border-radius: 8px;
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(255,255,255,0.06);
  font-size: 11px;
  color: rgba(255,255,255,0.45);
  line-height: 1.5;
}

.zone-status-summary .summary-item {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border-radius: 10px;
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(255,255,255,0.06);
}

.zone-status-summary .summary-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.zone-status-summary .summary-normal .summary-icon {
  background: rgba(16, 185, 129, 0.12);
  color: #10b981;
}

.zone-status-summary .summary-alarm .summary-icon {
  background: rgba(245, 158, 11, 0.12);
  color: #f59e0b;
}

.zone-status-summary .summary-label {
  font-size: 16px;
  color: white;
}

.zone-status-summary .summary-value {
  font-size: 20px;
  font-weight: 700;
  margin-left: auto;
}

.zone-status-summary .summary-normal .summary-value {
  color: #10b981;
}

.zone-status-summary .summary-alarm .summary-value {
  color: #f59e0b;
}

.zone-cards-section.is-focused {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.zone-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  max-height: 450px;
  overflow-y: auto;
  padding-right: 4px;
  padding-top: 10px;
}

.zone-card-grid::-webkit-scrollbar {
  width: 4px;
}

.zone-card-grid::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
  border-radius: 4px;
}

[data-theme="dark"] .zone-card-grid::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.15);
}

.zone-card-shell {
  min-height: 0;
}

.zone-card-tile {
  position: relative;
  display: grid;
  gap: 10px;
  min-height: 174px;
  padding: 14px;
  overflow: hidden;
  cursor: pointer;
  outline: none;
  will-change: transform, opacity;
}

.zone-card-tile::after,
.zone-focus-card::after {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
      linear-gradient(115deg, color-mix(in srgb, var(--hover-color, var(--blue)) 14%, transparent), transparent 38%),
      radial-gradient(circle at 92% 12%, color-mix(in srgb, var(--hover-color, var(--blue)) 18%, transparent), transparent 26%);
  opacity: 0.7;
}

.zone-card-tile > *,
.zone-focus-card > * {
  position: relative;
  z-index: 1;
}

.zone-card-tile:hover,
.zone-card-tile:focus-visible {
  border-color: color-mix(in srgb, var(--hover-color) 70%, var(--line));
  box-shadow: 0 14px 26px color-mix(in srgb, var(--hover-color) 16%, transparent);
}

.zone-card-top,
.zone-focus-header,
.zone-ai-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.zone-card-name,
.zone-focus-name {
  font-size: 18px;
  line-height: 1.2;
  font-weight: 900;
  text-wrap: balance;
}
.zone-card-risk{
  line-height: 3 !important;
}
.zone-card-risk,
.zone-focus-sub,
.zone-ai-header span {
  margin-top: 4px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.45;
  font-weight: 700;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.zone-card-signal-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.zone-card-signal {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 9px 10px;
  border: none;
  background: none;
}

.zone-card-signal-row .zone-card-signal {
  flex: 1;
  padding: 4px 0;
}

.signal-value {
  color: var(--text);
  font-size: 26px;
  line-height: 1;
  font-weight: 900;
  font-variant-numeric: tabular-nums;
}

.zone-card-signal:first-of-type .signal-value {
  color: #10b981;
}

.signal-label {
  color: var(--muted);
  font-size: 11px;
  font-weight: 800;
}

.zone-card-metrics,
.zone-focus-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(80px, 1fr));
  gap: 4px 8px;
}

.zone-card-metric,
.zone-focus-metric {
  min-width: 0;
  padding: 8px 7px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.5);
  display: grid;
  gap: 3px;
  text-align: center;
}

[data-theme="dark"] .zone-card-metric,
[data-theme="dark"] .zone-focus-metric {
  background: rgba(255, 255, 255, 0.035);
}

.zone-card-metric span,
.zone-focus-metric span,
.score-label {
  color: var(--muted);
  font-size: 10px;
  font-weight: 800;
}

.zone-card-metric b,
.zone-focus-metric b {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 900;
}

.zone-card-metric small,
.zone-focus-metric small {
  color: var(--muted);
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.zone-card-metric small.orange-text,
.zone-focus-metric small.orange-text {
  color: var(--orange);
}

.zone-card-env {
  gap: 4px 8px;
  min-height: 20px;
}

.zone-cards-section .env-item {
  font-size: 11px;
}

.zone-cards-section .env-item b {
  font-size: 13px;
}

.zone-focus-layout {
  display: grid;
  grid-template-columns: minmax(300px, 0.92fr) minmax(360px, 1.08fr);
  gap: 14px;
  align-items: stretch;
  flex: 1;
  min-height: 0;
  max-height:46vh;
}

.zone-focus-layout.zone-focus-single-card {
  position: absolute;
  top: 12px;
  right: 12px;
  bottom: 12px;
  width: 520px;
  max-width: 50%;
  grid-template-columns: 1fr;
  z-index: 20;
  max-height: none;
  border: 1px solid rgba(10, 170, 97, 0.25);
  border-radius: 12px;
  padding: 12px;
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
  overflow-y: auto;
}

.zone-focus-card {
  --hover-color: var(--blue);
  position: relative;
  height: 100%;
  min-height: 340px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  overflow-x: hidden;
  will-change: transform, opacity;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.zone-focus-card::-webkit-scrollbar {
  display: none;
}

.zone-focus-back {
  width: 30px;
  height: 30px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--soft);
  color: var(--text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex: 0 0 auto;
  transition: transform 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.zone-focus-back:hover {
  color: var(--blue);
  border-color: var(--blue);
  transform: translateX(-2px);
}

.zone-focus-score {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  text-align: center;
}

.zone-focus-score > div {
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: color-mix(in srgb, var(--blue) 6%, var(--soft));
  display: grid;
  gap: 4px;
}

.zone-focus-score strong {
  color: var(--text);
  font-size: 24px;
  line-height: 1;
  font-weight: 900;
  font-variant-numeric: tabular-nums;
}

.zone-focus-section {
  display: grid;
  gap: 8px;
}

.zone-focus-section-title {
  color: var(--text);
  font-size: 12px;
  font-weight: 900;
}

.zone-focus-env {
  background: var(--soft);
  gap: 6px 10px;
}

.zone-focus-device-list {
  display: grid;
  gap: 8px;
  max-height: 260px;
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.zone-focus-device-list::-webkit-scrollbar {
  display: none;
}

.zone-focus-device {
  display: grid;
  gap: 3px;
  padding: 9px 10px;
  border-radius: 8px;
  border-left: 0.5px solid var(--orange);
  background: var(--soft);
}

.zone-focus-device b {
  color: var(--text);
  font-size: 12px;
}

.zone-focus-device span {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.4;
}

.zone-ai-insight-panel {
  height: 100%;
  min-height: 340px;
  padding: 16px;
  border-radius: 9px;
  border: 1px solid var(--line);
  background: var(--card-bg);
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: hidden;
  will-change: transform, opacity;
}

.zone-ai-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.zone-ai-title {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.title-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--blue);
  flex-shrink: 0;
}

.zone-ai-loading {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-radius: 8px;
  background: var(--soft);
}

.zone-ai-loading span {
  height: 10px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(18,104,255,0.08), rgba(18,104,255,0.2), rgba(18,104,255,0.08));
  background-size: 180% 100%;
  animation: zone-ai-loading 1.2s ease-in-out infinite;
}

.zone-ai-loading span:nth-child(2) { width: 84%; }
.zone-ai-loading span:nth-child(3) { width: 62%; }

@keyframes zone-ai-loading {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}

.zone-ai-content {
  min-height: 80px;
  max-height: none;
  overflow: auto;
  padding: 12px;
  border-radius: 8px;
  background: var(--soft);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.75;
}

.zone-ai-suggestion-box {
  padding: 12px;
  border-radius: 8px;
  background: var(--soft);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}

.zone-ai-task-list {
  display: grid;
  gap: 10px;
  overflow: auto;
  padding-right: 2px;
  min-height: 0;
}

.zone-ai-task {
  box-shadow: none;
  background: var(--soft);
  border: 1px solid var(--line);
  border-radius: 8px;
}

/* ===== 异常逐条分析：统计栏 ===== */
.anomaly-stats-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding: 10px;
  border-radius: 8px;
  background: var(--soft);
  border: 1px solid var(--line);
}
.anomaly-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.anomaly-stat-value {
  font-size: 18px;
  font-weight: 800;
  color: var(--text);
  line-height: 1.1;
}
.anomaly-stat-label {
  font-size: 11px;
  color: var(--muted);
}
.anomaly-stats-bar .anomaly-stat:nth-child(1) .anomaly-stat-value { color: #22c55e; }
.anomaly-stats-bar .anomaly-stat:nth-child(2) .anomaly-stat-value { color: #ef4444; }
.anomaly-stats-bar .anomaly-stat:nth-child(3) .anomaly-stat-value { color: #f59e0b; }
.anomaly-stats-bar .anomaly-stat:nth-child(4) .anomaly-stat-value { color: var(--blue); }

/* ===== 异常任务列表 ===== */
.zone-anomaly-task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 360px;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 2px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.zone-anomaly-task-list::-webkit-scrollbar {
  display: none;
}
.zone-anomaly-task-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.zone-anomaly-type {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}
.anomaly-type-env { background: rgba(6,182,212,0.12); color: #06b6d4; }
.anomaly-type-device { background: rgba(239,68,68,0.12); color: #ef4444; }
.anomaly-type-energy { background: rgba(245,158,11,0.12); color: #f59e0b; }
.anomaly-type-keliu { background: rgba(99,102,241,0.12); color: #6366f1; }
.zone-anomaly-desc {
  font-size: 12px;
  color: var(--text);
  font-weight: 600;
}
/* 任务卡片内：态势分析 + 建议 */
.zone-anomaly-analysis,
.zone-anomaly-suggestion {
  margin-bottom: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  border-left: 3px solid var(--blue);
  background: var(--soft);
}
.zone-anomaly-suggestion {
  border-left-color: #f59e0b;
}
.zone-anomaly-analysis-title,
.zone-anomaly-suggestion-title {
  font-size: 11px;
  font-weight: 700;
  color: var(--muted);
  margin-bottom: 4px;
}
.zone-anomaly-suggestion-title {
  color: #f59e0b;
}
.zone-anomaly-analysis-text,
.zone-anomaly-suggestion-text {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text);
}

.anomaly-task-actions {
  display: flex;
  gap: 8px;
}
.anomaly-task-actions .dispatch-btn {
  flex: 1;
  padding: 6px 10px;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  transition: all 0.18s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
/* 保存：青绿色，沉稳 */
.anomaly-save-btn {
  background: linear-gradient(135deg, #0d9488, #14b8a6) !important;
}
.anomaly-save-btn:hover {
  background: linear-gradient(135deg, #0f766e, #0d9488) !important;
  box-shadow: 0 2px 8px rgba(13,148,136,0.35) !important;
  transform: translateY(-1px);
}
/* 下发：蓝紫色，主操作 */
.zone-ai-task .anomaly-task-actions .dispatch-btn:not(.anomaly-save-btn):not(.anomaly-cancel-btn) {
  background: linear-gradient(135deg, #4f46e5, #6366f1) !important;
}
.zone-ai-task .anomaly-task-actions .dispatch-btn:not(.anomaly-save-btn):not(.anomaly-cancel-btn):hover {
  background: linear-gradient(135deg, #4338ca, #4f46e5) !important;
  box-shadow: 0 2px 8px rgba(79,70,229,0.35) !important;
  transform: translateY(-1px);
}
/* 取消：灰色描边，弱化 */
.anomaly-cancel-btn {
  background: transparent !important;
  color: var(--muted) !important;
  border: 1px solid var(--line) !important;
}
.anomaly-cancel-btn:hover {
  background: rgba(239,68,68,0.08) !important;
  color: #ef4444 !important;
  border-color: rgba(239,68,68,0.3) !important;
}
.dispatch-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none !important;
  box-shadow: none !important;
}
.anomaly-task-status {
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 4px;
}
.anomaly-task-status.saved { background: rgba(34,197,94,0.12); color: #22c55e; }
.anomaly-task-status.dispatched { background: rgba(99,102,241,0.12); color: #6366f1; }
.anomaly-task-status.cancelled { background: rgba(239,68,68,0.12); color: #ef4444; }

.zone-ai-task.task-saved { border-left: 3px solid #22c55e; opacity: 0.85; }
.zone-ai-task.task-cancelled { border-left: 3px solid #ef4444; opacity: 0.6; }

.anomaly-analyzing {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-radius: 8px;
  background: var(--soft);
}
.anomaly-analyzing-text {
  font-size: 12px;
  color: var(--muted);
  height: auto !important;
  background: none !important;
  animation: none !important;
}
.zone-anomaly-done {
  padding: 12px;
  border-radius: 8px;
  background: rgba(34,197,94,0.08);
  color: #22c55e;
  font-size: 12px;
  text-align: center;
  font-weight: 600;
}

/* ===== 下一条按钮 ===== */
.anomaly-next-bar {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}
.anomaly-next-btn {
  width: 100%;
  padding: 10px 16px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #6366f1, #818cf8);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}
.anomaly-next-btn:hover {
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(99,102,241,0.3);
}

.zone-empty {
  padding: 16px;
  border-radius: 8px;
  background: var(--soft);
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}

.zone-viewport {
  position: relative;
  width: 100%;
  /* 移除固定高度限制，改为最大高度+滚动 */
  max-height: 720px;
  min-height: 500px;
  overflow-y: auto;
  overflow-x: hidden;
  /* 自定义滚动条样式 */
  scrollbar-width: thin;
  scrollbar-color: rgba(100, 150, 220, 0.25) transparent;
}
.zone-viewport::-webkit-scrollbar {
  width: 5px;
}
.zone-viewport::-webkit-scrollbar-track {
  background: transparent;
  border-radius: 3px;
}
.zone-viewport::-webkit-scrollbar-thumb {
  background: rgba(100, 150, 220, 0.25);
  border-radius: 3px;
  transition: background 0.2s;
}
.zone-viewport::-webkit-scrollbar-thumb:hover {
  background: rgba(100, 150, 220, 0.4);
}
[data-theme="dark"] .zone-viewport {
  scrollbar-color: rgba(60, 110, 180, 0.2) transparent;
}
[data-theme="dark"] .zone-viewport::-webkit-scrollbar-thumb {
  background: rgba(60, 110, 180, 0.2);
}
[data-theme="dark"] .zone-viewport::-webkit-scrollbar-thumb:hover {
  background: rgba(60, 110, 180, 0.35);
}
/* 模型视图保持绝对定位（canvas需要） */
.zone-viewport > .zone-model-section {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
/* 卡片视图：取消绝对定位，允许自然流动和滚动 */
.zone-viewport > .zone-cards-section {
  position: relative;
  width: 100%;
  min-height: 100%;
}

.zone-model-section {
  position: relative;
  min-height: 500px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--soft);
  border: 1px solid var(--line);
}

.zone-model-section .heatmap-canvas {
  width: 100%;
  height: 510px;
  min-height: 360px;
  cursor: pointer;
  position: relative;
  display: block; /* canvas 默认 inline 会让 width:100% 失效 */
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
  background: linear-gradient(180deg, #1890ff1f 0%, #1890ff2e 100%);
}

.risk-box.orange-box {
  height: 100%;
  border-color: #ffa03d;
  background: linear-gradient(180deg, #fff7ef 0%, #ffffff 100%);
}

[data-theme="dark"] .risk-box.orange-box {
  border-color: var(--orange);
  background: linear-gradient(180deg, #1890ff12 0%, #1890ff36 100%);
}

.risk-box.blue-box {
  border-color: #72a5ff;
  background: linear-gradient(180deg, #f5f9ff 0%, #ffffff 100%);
}

[data-theme="dark"] .risk-box.blue-box {
  border-color: var(--blue);
  background: linear-gradient(180deg, rgba(0, 212, 255, 0.08) 0%, rgba(10, 25, 41, 0.7) 100%);
}

.zone-cards-section .zone-card-grid {
  gap: 20px 12px;
  max-height: 450px;
  overflow-y: auto;
}

.zone-cards-section .zone-card-tile {
  min-height: 174px;
  padding: 14px;
}

.zone-cards-section .zone-focus-card {
  min-height: 340px;
  padding: 16px;
  height: auto;
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
  background: rgba(255,255,255,0.02);
}

.metric .label {
  color: #17295e;
  font-size: 12px;
  font-weight: 700;
}

[data-theme="dark"] .metric .label {
  color: white;
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
  color: white;
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
  color: white;
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

/* ===== Agent协作 - 任务卡片列表 ===== */
.suggestions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 6px;
}

/* 隐藏滚动条 / 主题色滚动条 */
.suggestions::-webkit-scrollbar {
  width: 4px;
}
.suggestions::-webkit-scrollbar-track {
  background: transparent;
}
.suggestions::-webkit-scrollbar-thumb {
  background: rgba(59, 130, 246, 0.3);
  border-radius: 2px;
}
.suggestions::-webkit-scrollbar-thumb:hover {
  background: rgba(59, 130, 246, 0.5);
}

/* 空状态 */
.suggestion-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 32px 16px;
  color: rgba(255,255,255,0.3);
  font-size: 12px;
  font-weight: 500;
}
.suggestion-empty svg {
  color: rgba(16, 185, 129, 0.4);
}

/* 任务卡片 */
.task-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 10px;
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(255,255,255,0.05);
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}
.task-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 3px;
  height: 100%;
  background: linear-gradient(180deg, #3b82f6, #6366f1);
  opacity: 0;
  transition: opacity 0.25s ease;
}
.task-card:hover {
  background: rgba(255,255,255,0.04);
  border-color: rgba(59, 130, 246, 0.15);
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
}
.task-card:hover::before {
  opacity: 1;
}

/* 卡片头部 - 流向 + 状态 */
.task-card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.task-card-flow {
  display: flex;
  align-items: center;
  gap: 8px;
}

.flow-agent-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.02em;
  white-space: nowrap;
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.08);
}
.flow-agent-tag svg {
  flex-shrink: 0;
}

.flow-arrow-icon {
  color: rgba(255,255,255,0.25);
  flex-shrink: 0;
}

/* 状态标识 */
.task-card-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.status-wait {
  background: rgba(251, 191, 36, 0.1);
  color: rgba(251, 191, 36, 0.85);
}
.status-wait .status-dot {
  background: #fbbf24;
  box-shadow: 0 0 6px rgba(251, 191, 36, 0.4);
  animation: pulse-dot 2s ease-in-out infinite;
}
.status-done {
  background: rgba(74, 222, 128, 0.1);
  color: rgba(74, 222, 128, 0.85);
}
.status-done .status-dot {
  background: #4ade80;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}

/* 卡片内容 */
.task-card-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.task-problem-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(255,255,255,0.03);
  border-left: 0.5px solid rgba(251, 146, 60, 0.4);
  border-radius: 0 6px 6px 0;
}

.task-problem-row .decision-label {
  /* color: rgba(251, 146, 60, 0.8); */
}

.task-decision-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(255,255,255,0.03);
  border-left: 0.5px solid rgba(99, 102, 241, 0.4);
  border-radius: 0 6px 6px 0;
}

.decision-label {
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  flex-shrink: 0;
  opacity: 0.7;
}

.decision-content {
  font-size: 14px;
  line-height: 1.6;
  flex: 1;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3; /* 限制显示3行 */
}


.task-card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.meta-tag {
  padding: 2px 7px;
  border-radius: 4px;
  font-size: 9px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.meta-tag.tag-hint {
  color: white;
}
.meta-tag.tag-collaboration {
  color: white;
}

.meta-tag.tag-collab {
  color: rgba(192, 132, 252, 0.7);
}

.meta-domain {
  color: rgba(255,255,255,0.4);
  font-size: 10px;
  font-weight: 600;
}

.meta-level {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 9px;
  font-weight: 800;
  font-family: 'JetBrains Mono', monospace;
}
.meta-level.level-1 {
  background: rgba(239, 68, 68, 0.12);
  color: rgba(248, 113, 113, 0.85);
}
.meta-level.level-2 {
  background: rgba(245, 158, 11, 0.12);
  color: rgba(251, 191, 36, 0.85);
}
.meta-level.level-3 {
  background: rgba(59, 130, 246, 0.12);
  color: rgba(96, 165, 250, 0.85);
}

/* 卡片操作按钮 */
.task-card-actions {
  display: flex;
  gap: 6px;
  padding-top: 6px;
  border-top: 1px solid rgba(255,255,255,0.06);
  /* justify-content: flex-end;
      align-items: flex-end; */
}

.mini-dispatch-btn,
.mini-cancel-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 5px 10px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.mini-dispatch-btn {
  background: rgba(34, 197, 94, 0.15);
  color: #4ade80;
  border: 1px solid rgba(34, 197, 94, 0.25);
  margin-right: 16px;
}
.mini-dispatch-btn:hover {
  background: rgba(34, 197, 94, 0.25);
  transform: translateY(-1px);
}
.mini-dispatch-btn:active {
  transform: scale(0.96);
}

.mini-cancel-btn {
  background: transparent;
  color: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.2);
}
.mini-cancel-btn:hover {
  color: #fff;
  border-color: rgba(255, 255, 255, 0.6);
  background: transparent;
  transform: translateY(-1px);
}
.mini-cancel-btn:active {
  transform: scale(0.96);
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

.prediction-ring {
  --p: 100;
}

.ops-ring {
  --p: 100;
}

.prediction-ring-wrap {
  position: relative;
}

.prediction-tooltip {
  position: absolute;
  top: 40%;
  left: calc(100% - 20px);
  transform: translateY(-50%);
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid #e4edf7;
  border-radius: 8px;
  padding: 10px 14px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s ease;
  white-space: nowrap;
  z-index: 10;
}

[data-theme="dark"] .prediction-tooltip {
  background: rgba(20, 24, 40, 0.96);
  border-color: var(--line);
}

.prediction-ring-wrap:hover .prediction-tooltip {
  opacity: 1;
}

.tooltip-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  line-height: 1.8;
  color: #333;
}

[data-theme="dark"] .tooltip-item {
  color: #ccc;
}

.tooltip-item .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.tooltip-item b {
  font-weight: 700;
  margin-left: 2px;
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

.ring small {
  margin-top: 5px;
  font-size: 10px;
  color: #24365e;
  font-weight: 900;
}

[data-theme="dark"] .ring small {
  color: white;
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
  background: rgba(255,255,255,0.02);
  color: #a0c4e8;
  margin-top: 10px;
}

.agent-kv .right {
  text-align: right;
  color: #071744;
}

[data-theme="dark"] .agent-kv .right {
  color: white;
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
  max-height: 110px;
  overflow: hidden;
}

.k-legend span {
  white-space: nowrap;
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
  color: white;
}

/* 派单弹窗样式 */
.dispatch-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: overlay-fade-in 0.2s ease;
}
.dispatch-modal-content {
  background: linear-gradient(180deg, #0a1628 0%, #0d1f3c 100%);
  border: 1px solid rgba(0, 180, 255, 0.2);
  border-radius: 12px;
  width: 95%;
  max-width: 1200px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255,255,255,0.05);
  animation: modal-slide-up 0.3s ease;
  overflow: hidden;
  margin-top: -80px;
}
.dispatch-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px 28px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.dispatch-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--blue);
}
.dispatch-header-left svg {
  color: #00b4ff;
  flex-shrink: 0;
}
.dispatch-header-left h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #e8f0fe;
  letter-spacing: 0.5px;
}
.dispatch-subtitle {
  margin: 2px 0 0;
  font-size: 12px;
  color: rgba(255,255,255,0.35);
  font-weight: 400;
}
.dispatch-modal-close {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: rgba(255,255,255,0.4);
  font-size: 24px;
  cursor: pointer;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  flex-shrink: 0;
}
.dispatch-modal-close:hover {
  background: rgba(255,255,255,0.08);
  color: #fff;
}
.dispatch-modal-body {
  padding: 16px 20px 20px;
  flex: 1;
  overflow-y: auto;
}
.dispatch-three-cols {
  display: grid;
  grid-template-columns: 1fr 1.3fr 1fr;
  gap: 14px;
}
.dispatch-col {
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px;
  overflow: hidden;
}
.col-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  color: rgba(255,255,255,0.6);
  border-bottom: 1px solid rgba(255,255,255,0.06);
  background: rgba(255,255,255,0.02);
}
.col-title svg {
  color: rgba(0, 180, 255, 0.7);
}
.col-more {
  margin-left: auto;
  font-size: 11px;
  font-weight: 500;
  color: rgba(0, 180, 255, 0.5);
  cursor: pointer;
  transition: all 0.2s;
}
.col-more:hover {
  color: rgba(0, 180, 255, 0.9);
}
.col-content {
  padding: 14px 16px;
}
.overview-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid rgba(255,255,255,0.04);
  font-size: 13px;
  line-height: 1.5;
}
.overview-row:last-of-type {
  border-bottom: none;
}
.overview-label {
  color: rgba(255,255,255,0.4);
  font-weight: 500;
  flex-shrink: 0;
  min-width: 70px;
}
.overview-value {
  color: rgba(255,255,255,0.85);
  font-weight: 500;
}
.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.status-alert {
  background: rgba(255, 70, 70, 0.15);
  color: #ff4646;
  border: 1px solid rgba(255, 70, 70, 0.2);
}
.level-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.level-high {
  background: rgba(255, 70, 70, 0.15);
  color: #ff4646;
  border: 1px solid rgba(255, 70, 70, 0.2);
}
.realtime-divider {
  height: 1px;
  background: rgba(255,255,255,0.06);
  margin: 12px 0 10px;
}
.realtime-title {
  font-size: 12px;
  font-weight: 600;
  color: rgba(0, 180, 255, 0.7);
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.realtime-title::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #00b4ff;
  animation: realtime-pulse 1.5s infinite;
}
@keyframes realtime-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
.realtime-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.realtime-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.05);
  border-radius: 6px;
}
.realtime-label {
  font-size: 11px;
  color: rgba(255,255,255,0.35);
  font-weight: 500;
}
.realtime-value {
  font-size: 14px;
  font-weight: 700;
  color: rgba(255,255,255,0.85);
  font-family: 'Courier New', monospace;
}
.realtime-value.realtime-warn {
  color: #ff6b6b;
}

.diagnosis-summary {
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: 6px;
}
.diagnosis-label {
  color: rgba(255,255,255,0.4);
  font-weight: 500;
}
.diagnosis-value {
  color: #00b4ff;
  font-weight: 600;
}
.confidence-row {
  font-size: 13px;
  margin-bottom: 10px;
}
.confidence-label {
  color: rgba(255,255,255,0.4);
  font-weight: 500;
}
.confidence-value {
  color: #00b4ff;
  font-weight: 700;
  font-size: 15px;
}
.diagnosis-divider {
  height: 1px;
  background: rgba(255,255,255,0.06);
  margin: 12px 0;
}
.diagnosis-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.diagnosis-list li {
  position: relative;
  padding: 5px 0 5px 14px;
  font-size: 13px;
  color: rgba(255,255,255,0.7);
  line-height: 1.6;
}
.diagnosis-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(0, 180, 255, 0.6);
}
.suggestion-section {
  margin-top: 4px;
}
.suggestion-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: rgba(255,255,255,0.6);
  margin-bottom: 10px;
}
.suggestion-title svg {
  color: rgba(0, 180, 255, 0.7);
}
.suggestion-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.suggestion-list li {
  position: relative;
  padding: 5px 0 5px 14px;
  font-size: 13px;
  color: rgba(255,255,255,0.7);
  line-height: 1.6;
}
.suggestion-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(0, 180, 255, 0.6);
}
.dispatch-info-row {
  display: flex;
  align-items: center;
  padding: 6px 0;
  font-size: 13px;
  line-height: 1.5;
  border-bottom: 1px solid rgba(255,255,255,0.04);
}
.dispatch-info-row:last-of-type {
  border-bottom: none;
}
.dispatch-info-row .info-label {
  color: rgba(255,255,255,0.4);
  font-weight: 500;
  flex-shrink: 0;
  min-width: 72px;
}
.dispatch-info-row .info-value {
  color: rgba(255,255,255,0.85);
  font-weight: 500;
}
.urgency-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 3px;
  font-size: 12px;
  font-weight: 600;
}
.urgency-urgent {
  background: rgba(255, 70, 70, 0.15);
  color: #ff4646;
  border: 1px solid rgba(255, 70, 70, 0.2);
}
.match-value {
  color: #00b4ff;
  font-weight: 700;
}
.dispatch-divider {
  height: 1px;
  background: rgba(255,255,255,0.06);
  margin: 12px 0;
}
.link-section {
  margin-top: 4px;
}
.link-label {
  display: block;
  font-size: 12px;
  color: rgba(255,255,255,0.35);
  font-weight: 500;
  margin-bottom: 10px;
}
.link-buttons {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.link-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 8px;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 8px;
  color: rgba(255,255,255,0.6);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.link-btn:hover {
  background: rgba(0, 180, 255, 0.1);
  border-color: rgba(0, 180, 255, 0.3);
  color: #00b4ff;
}
.link-btn svg {
  flex-shrink: 0;
}
.dispatch-modal-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 28px;
  border-top: 1px solid rgba(255,255,255,0.06);
  background: rgba(0,0,0,0.15);
}
.footer-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.footer-right {
  display: flex;
  gap: 10px;
}
.footer-btn {
  padding: 8px 20px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid rgba(255,255,255,0.1);
  transition: all 0.15s;
}
.btn-cancel {
  background: transparent;
  color: white;
  border-color: rgba(255,255,255,0.1);
}
.btn-cancel:hover {
  background: rgba(255,255,255,0.06);
  color: rgba(255,255,255,0.8);
}
.btn-generate {
  background: rgba(0, 180, 255, 0.15);
  color: #00b4ff;
  border-color: rgba(0, 180, 255, 0.3);
}
.btn-generate:hover {
  background: rgba(0, 180, 255, 0.25);
}
.btn-dispatch {
  background: linear-gradient(135deg, #00b4ff, #0090e0);
  color: #fff;
  border: none;
  box-shadow: 0 2px 8px rgba(0, 180, 255, 0.3);
}
.btn-dispatch:hover {
  background: linear-gradient(135deg, #1ac4ff, #00a0f0);
  box-shadow: 0 4px 12px rgba(0, 180, 255, 0.4);
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

/* ===== 告警事项列表弹窗 ===== */
.alert-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: overlay-fade-in 0.2s ease;
}

.alert-modal {
  width: 95%;
  max-width: 1600px;
  max-height: 95vh;
  min-height: 600px;
  /* background: linear-gradient(180deg, #0a1628 0%, #0d1f3c 100%); */
  background: rgb(0,60,126);
  border: 1px solid rgba(0, 180, 255, 0.2);
  border-radius: 12px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255,255,255,0.05);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: modal-slide-up 0.3s ease;
}

.alert-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px 28px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}

.alert-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #e8f0fe;
}

.alert-header-left svg {
  color: #00b4ff;
}

.alert-header-left h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #e8f0fe;
  letter-spacing: 0.5px;
}

.alert-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: rgba(255,255,255,0.4);
}

.alert-modal-close {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: rgba(255,255,255,0.4);
  font-size: 24px;
  cursor: pointer;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  flex-shrink: 0;
}

.alert-modal-close:hover {
  background: rgba(255,255,255,0.08);
  color: #fff;
}

/* 弹窗头部右侧 */
.alert-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-selector {
  display: flex;
  background: rgba(255,255,255,0.05);
  border-radius: 6px;
  padding: 2px;
}

.filter-btn {
  padding: 6px 16px;
  border: none;
  background: transparent;
  color: rgba(255,255,255,0.5);
  font-size: 13px;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.15s;
}

.filter-btn:hover {
  color: rgba(255,255,255,0.8);
}

.filter-btn.active {
  background: rgba(59, 130, 246, 0.3);
  color: #fff;
  font-weight: 500;
}

.alert-modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px 20px;
}

.alert-modal-body::-webkit-scrollbar {
  width: 6px;
}

.alert-modal-body::-webkit-scrollbar-track {
  background: transparent;
}

.alert-modal-body::-webkit-scrollbar-thumb {
  background: rgba(255,255,255,0.12);
  border-radius: 3px;
}

.alert-modal-body::-webkit-scrollbar-thumb:hover {
  background: rgba(255,255,255,0.2);
}

.alert-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: rgba(255,255,255,0.4);
}

.alert-empty svg {
  margin-bottom: 16px;
  opacity: 0.3;
}

.alert-table-wrapper { margin-bottom: 16px; }

/* 告警分页 */
.alert-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 12px 0 4px;
}

.pagination-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid rgba(255,255,255,0.15);
  border-radius: 6px;
  background: rgba(255,255,255,0.05);
  color: rgba(255,255,255,0.7);
  cursor: pointer;
  transition: all 0.15s;
}

.pagination-btn:hover:not(:disabled) {
  background: rgba(255,255,255,0.1);
  color: #fff;
}

.pagination-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.pagination-info {
  font-size: 24px;
  color: rgba(255,255,255,0.5);
}
.alert-table { width: 100%; border-collapse: collapse; font-size: 24px; }
.alert-table th {
  background: rgba(255,255,255,0.04); padding: 12px 14px; text-align: left;
  font-weight: 600; border-bottom: 1px solid rgba(255,255,255,0.06);
  font-size: 24px;
}
.alert-table td {
  padding: 12px 14px; border-bottom: 1px solid rgba(255,255,255,0.06); color: rgba(255,255,255,0.7);
  font-size: 24px;
}
.alert-table .alert-info-cell { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.alert-table .alert-location-cell { max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.alert-level {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 24px;
  font-weight: 600;
  display: inline-block;
}

.alert-level.level-alarm {
  color: linear-gradient(135deg, #ff8c00, #ffa500);
}

.alert-level.level-fault {
  color: linear-gradient(135deg, #ff4646, #ff6b6b);
}

.alert-device {
  font-size: 13px;
  color: rgba(255,255,255,0.7);
}

.alert-time {
  font-size: 12px;
  color: white;
  white-space: nowrap;
}

.alert-status {
  padding: 3px 10px;
  border-radius: 4px;
  font-weight: 500;
  display: inline-block;
}

.alert-status.status-pending {
  background: rgba(255, 107, 0, 0.1);
  color: #ff6a00;
}

.alert-status.status-processing {
  background: rgba(18, 104, 255, 0.1);
  color: #1268ff;
}

.alert-actions { display: flex; gap: 6px; align-items: center; }

.alert-action-btn {
  background: none;
  border: 1px solid rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.7);
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
}

.alert-action-btn:hover {
  background: rgba(255,255,255,0.08);
  color: #fff;
}

.alert-action-btn.btn-known:hover {
  border-color: #00b4ff;
  color: #00b4ff;
  background: rgba(0, 180, 255, 0.15);
}
.btn-known{
  border-color: #00b4ff;
}
.alert-action-btn.btn-issue:hover {
  border-color: #ff6a00;
  color: #ff6a00;
  background: rgba(255, 106, 0, 0.15);
}
.btn-issue{
  border-color: #ff6a00;
}

/* ===== 自动诊断告警详情弹窗 ===== */
.auto-diagnosis-modal {
  width: 90vw;
  max-width: 90vw;
  max-height: 100vh;
  min-height: auto;
}

.auto-diagnosis-layout {
  display: flex;
  gap: 20px;
}

.alert-modal-columns {
  display: flex;
  gap: 20px;
  width: 100%;
}

.alert-modal-col {
  min-width: 0;
}

.alert-modal-col-left {
  flex: 0 0 450px;
}

.alert-modal-col-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 350px;
  position: relative;
}

.ai-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 16px;
  padding: 80px 24px;
  color: #fff;
  font-size: 22px;
  font-weight: 600;
}

.ai-analysis-section {
  background: rgba(59, 130, 246, 0.06);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 12px;
  padding: 16px 20px;
}

.alert-info-card {
  padding: 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
}

.alert-info-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  color: rgba(255,255,255,0.7);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}

.alert-info-header svg {
  color: #ff6a00;
}

.auto-diagnosis-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-label {
  min-width: 80px;
  font-size: 20px;
  color: rgba(255,255,255,0.7);
  flex-shrink: 0;
}

.detail-value {
  font-size: 20px;
  color: rgba(255,255,255,0.9);
}

.detail-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding-top: 16px;
  border-top: 1px solid rgba(255,255,255,0.06);
}

.auto-diagnosis-detail .alert-level {
  font-size: 20px;
  padding: 4px 12px;
}

.auto-diagnosis-detail .alert-status {
  font-size: 20px;
  padding: 4px 12px;
}

/* AI分析结果区域 */
.ai-analysis-section {
  padding: 16px;
  background: rgba(59, 130, 246, 0.08);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.ai-analysis-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 12px;
}

.ai-analysis-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.analysis-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.analysis-label {
  font-size: 20px;
  color: rgba(255,255,255,0.7);
}

.analysis-value {
  font-size: 20px;
  color: rgba(255,255,255,0.85);
  line-height: 1.5;
}

/* 派发任务卡片（独立列） */
.dispatch-tasks-card {
  padding: 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
}

.dispatch-tasks-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  color: rgba(255,255,255,0.7);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}

.dispatch-tasks-header svg {
  color: #3b82f6;
}

.dispatch-tasks-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 派发任务列表（旧样式保留） */
.dispatch-tasks {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(59, 130, 246, 0.15);
}

.task-header {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255,255,255,0.6);
  margin-bottom: 10px;
}

.task-card {
  padding: 16px;
  background: rgba(0,0,0,0.2);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 8px;
  margin-bottom: 12px;
}

.task-card:last-child {
  margin-bottom: 0;
}

.task-card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}

.task-card-title {
  font-size: 20px;
  font-weight: 600;
  color: rgba(255,255,255,0.9);
}

.task-detail-rows {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-card .detail-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.task-card .detail-label {
  font-size: 20px;
  color: rgba(255,255,255,0.7);
  flex-shrink: 0;
  min-width: 80px;
}

.task-card .detail-value {
  font-size: 20px;
  color: rgba(255,255,255,0.85);
  line-height: 1.5;
  flex: 1;
}

.task-title {
  font-size: 20px;
  font-weight: 500;
  color: rgba(255,255,255,0.9);
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.task-level {
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 20px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
}

.task-level.level-high { background: rgba(239, 68, 68, 0.8); }
.task-level.level-medium { background: rgba(245, 158, 11, 0.8); }
.task-level.level-low { background: rgba(16, 185, 129, 0.8); }

.task-desc {
  font-size: 20px;
  color: white;
  margin-bottom: 8px;
}

.task-decision {
  font-size: 20px;
  color: rgba(250, 204, 21, 0.8);
  margin-bottom: 8px;
}

.task-meta {
  display: flex;
  gap: 16px;
  font-size: 20px;
  margin-bottom: 6px;
}

.task-effect {
  font-size: 20px;
}

.task-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
  justify-content: flex-end;
}

.task-btn {
  padding: 8px 20px;
  border-radius: 6px;
  font-size: 20px;
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid;
}

.task-btn.btn-confirm {
  background: rgba(59, 130, 246, 0.15);
  border-color: rgba(59, 130, 246, 0.4);
  color: white;
}

.task-btn.btn-confirm:hover {
  background: rgba(59, 130, 246, 0.3);
}

.task-btn.btn-cancel {
  background: rgba(255,255,255,0.05);
  border-color: rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.5);
}

.task-btn.btn-cancel:hover {
  background: rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.8);
}

/* 任务执行结果卡片 */
.task-result-card {
  padding: 16px;
  background: rgba(16, 185, 129, 0.08);
  border: 1px solid rgba(16, 185, 129, 0.2);
  border-radius: 8px;
}

.task-result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  color: rgba(16, 185, 129, 0.9);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(16, 185, 129, 0.15);
}

.task-result-header svg {
  color: #10b981;
}

.task-result-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.result-label {
  font-size: 20px;
  color: rgba(255,255,255,0.4);
  flex-shrink: 0;
  min-width: 80px;
}

.result-value {
  font-size: 20px;
  color: rgba(255,255,255,0.85);
  line-height: 1.5;
}

.result-value.status-done {
  color: #10b981;
  font-weight: 600;
}

.result-value.result-data {
  flex: 1;
  word-break: break-all;
  white-space: pre-wrap;
}

/* AI分析加载中 */
.ai-loading {
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
  flex-direction: column;
  gap: 16px;
  padding: 280px 0 0 324px;
  color: #fff;
  font-size: 22px;
  font-weight: 600;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid rgba(59, 130, 246, 0.2);
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
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
  /* border-bottom: 1px solid var(--line); */
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

/* KPI行 */
.warning-kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}

.warning-kpi-card {
  /* background: var(--panel); */
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

.knowledge-chart {
  width: 120px;
  height: 120px;
}

.knowledge-ring small {
  margin-top: 5px;
  font-size: 12px;
}

[data-theme="dark"] .knowledge-ring small {
  color: white;
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
  .top-grid {
    grid-template-columns: 260px 1fr 280px;
  }

  .bottom-grid {
    grid-template-columns: repeat(2, minmax(320px, 1fr));
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

  .top-grid {
    grid-template-columns: 1fr;
  }

  .top-grid .left-column,
  .top-grid .right-column {
    flex-direction: row;
  }

  .bottom-grid {
    grid-template-columns: 1fr;
  }

  .card {
    padding: 12px;
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

  .zone-focus-layout {
    grid-template-columns: 1fr;
  }

  .zone-card-metrics,
  .zone-focus-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .zone-ai-content {
    max-height: 220px;
  }

  .suggestion {
    grid-template-columns: 30px 1fr;
  }

  .suggestion .action {
    grid-column: 2;
    width: max-content;
  }
}

@media (max-height: 1000px) {
  .suggestions {
    max-height: 56vh;
    overflow: auto;
  }
}

@media (max-height: 1500px) and (min-height: 1001px) {
  .suggestions {
    max-height: 57vh;
    overflow: auto;
  }
}
@media (min-height: 1501px) {
  .suggestions {
    max-height: 60vh;
    overflow: auto;
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
  display: block; /* canvas 默认 inline 会让 width:100% 失效 */
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
  flex: 1;
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

/* 固定实时监控视频 */
.fixed-zone-video {
  margin: 0 auto;
  width: 100%;
  z-index: 50;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.fixed-video-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  border-radius: 8px 8px 0 0;
}
.fixed-video-header svg {
  color: #ef4444;
}
.fixed-video-wrapper {
  position: relative;
  border-radius: 0 0 8px 8px;
  overflow: hidden;
  background: #000;
  cursor: pointer;
  aspect-ratio: 16 / 10;
  border: 1px solid rgba(255,255,255,0.15);
  border-top: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.fixed-video-wrapper:hover {
  border-color: rgba(0, 180, 255, 0.5);
  box-shadow: 0 4px 16px rgba(0, 180, 255, 0.15);
}
.fixed-video-wrapper:hover .fixed-video-play-overlay {
  opacity: 1;
}
.fixed-video-wrapper:hover .fixed-video-expand-icon {
  opacity: 1;
}
.fixed-video-player {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.fixed-video-play-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0,0,0,0.3);
  opacity: 0;
  transition: opacity 0.2s;
  pointer-events: none;
}
.fixed-video-play-overlay svg {
  color: rgba(255,255,255,0.9);
  filter: drop-shadow(0 2px 8px rgba(0,0,0,0.4));
}
.fixed-video-expand-icon {
  position: absolute;
  bottom: 6px;
  right: 6px;
  width: 24px;
  height: 24px;
  border-radius: 5px;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
  color: rgba(255,255,255,0.8);
  pointer-events: none;
}

/* 视频全屏播放弹窗 */
.video-fullscreen-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: overlay-fade-in 0.2s ease;
}
.video-fullscreen-modal {
  position: relative;
  width: 90%;
  max-width: 1200px;
  animation: modal-slide-up 0.3s ease;
}
.video-fullscreen-close {
  position: absolute;
  top: -40px;
  right: 0;
  background: transparent;
  border: none;
  color: rgba(255,255,255,0.6);
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  transition: all 0.15s;
}
.video-fullscreen-close:hover {
  color: #fff;
  background: rgba(255,255,255,0.1);
}
.video-fullscreen-player {
  width: 100%;
  border-radius: 12px;
  display: block;
  box-shadow: 0 8px 40px rgba(0,0,0,0.5);
}
.video-fullscreen-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding: 0 4px;
}
.video-live-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  color: #ef4444;
}
.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ef4444;
  animation: live-pulse 1.5s infinite;
}
@keyframes live-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
.video-zone-name {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255,255,255,0.7);
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
  border-left: 0.5px solid #ef4444;
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

/* 派发任务卡片 */
.dispatch-task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.dispatch-task-card {
  padding: 12px 14px;
  border-radius: 12px;
  border-left: 0.5px solid #6366f1;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  transition: all 0.3s ease;
}
.dispatch-task-card.task-dispatched {
  opacity: 0.7;
  border-left-color: #22c55e;
  border-left-width: 0.5px;
}
.dispatch-task-card.task-domain-device { border-left-color: #ef4444; }
.dispatch-task-card.task-domain-energy { border-left-color: #f59e0b; }
.dispatch-task-card.task-domain-env { border-left-color: #06b6d4; }
.dispatch-task-card.task-level-high .dispatch-task-level { background: rgba(239,68,68,0.15); color: #ef4444; }
.dispatch-task-card.task-level-高 .dispatch-task-level { background: rgba(239,68,68,0.15); color: #ef4444; }
.dispatch-task-card.task-level-中 .dispatch-task-level { background: rgba(245,158,11,0.15); color: #f59e0b; }
.dispatch-task-card.task-level-低 .dispatch-task-level { background: rgba(34,197,94,0.15); color: #22c55e; }

.dispatch-task-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.dispatch-task-domain {
  font-size: 12px;
  font-weight: 700;
  color: var(--text);
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(99,102,241,0.1);
  color: #6366f1;
}
.task-domain-device .dispatch-task-domain { background: rgba(239,68,68,0.1); color: #ef4444; }
.task-domain-energy .dispatch-task-domain { background: rgba(245,158,11,0.1); color: #f59e0b; }
.task-domain-env .dispatch-task-domain { background: rgba(6,182,212,0.1); color: #06b6d4; }

.dispatch-task-level {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
}
.dispatch-task-status {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  margin-left: auto;
}
.dispatch-task-status.pending { background: rgba(245,158,11,0.1); color: #f59e0b; }
.dispatch-task-status.dispatched { background: rgba(34,197,94,0.1); color: #22c55e; }

.dispatch-task-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}
.dispatch-task-field {
  display: flex;
  gap: 8px;
  font-size: 12px;
}
.dispatch-task-field .field-label {
  color: var(--text);
  font-weight: 600;
  min-width: 60px;
  flex-shrink: 0;
}
.dispatch-task-field .field-value {
  color: var(--muted);
  flex: 1;
}

.dispatch-task-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 8px;
  border-top: 1px solid rgba(255,255,255,0.06);
}
.dispatch-target-agent {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--muted);
  flex: 1;
}
.dispatch-target-agent svg { color: #6366f1; }
.dispatch-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #6366f1, #818cf8);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}
.dispatch-btn:hover {
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(99,102,241,0.3);
}
.dispatched-label {
  font-size: 11px;
  color: #22c55e;
  font-weight: 600;
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

.ai-orb {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  min-width: 40px;
  min-height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1868ff, white);
  color: #fff;
  font-size: 14px;
  font-weight: 900;
  box-shadow: 0 0 20px rgba(18, 104, 255, 0.4);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
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
  line-height: 3;
  color: var(--muted);
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
  border-left: 0.5px solid var(--green);
}

.modal-zone-card.orange-box {
  border-left: 0.5px solid var(--orange);
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
  border-left: 0.5px solid var(--orange);
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
  border-left: 0.5px solid #ff4444;
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

/* ===== 视频全屏播放弹窗 ===== */
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
  left: 20px;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(0, 180, 255, 0.15);
  border: 1px solid rgba(0, 180, 255, 0.3);
  color: #00b4ff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  z-index: 2001;
  backdrop-filter: blur(10px);
}

.knowledge-modal-close:hover {
  background: rgba(0, 180, 255, 0.3);
  border-color: rgba(0, 180, 255, 0.6);
  transform: scale(1.1);
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
  left: 20px;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(0, 180, 255, 0.15);
  border: 1px solid rgba(0, 180, 255, 0.3);
  color: #00b4ff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  z-index: 2001;
  backdrop-filter: blur(10px);
}

.assistant-modal-close:hover {
  background: rgba(0, 180, 255, 0.3);
  border-color: rgba(0, 180, 255, 0.6);
  transform: scale(1.1);
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
  background: rgba(0, 0, 0, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: overlay-fade-in 0.2s ease;
}

.health-modal {
  /* background: linear-gradient(180deg, #0a1628 0%, #0d1f3c 100%); */
  background: rgb(0, 66, 126);
  border: 1px solid rgba(0, 180, 255, 0.2);
  border-radius: 12px;
  width: 95%;
  max-width: 1200px;
  max-height: 95vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255,255,255,0.05);
  animation: modal-slide-up 0.3s ease;
  overflow: hidden;
}

.health-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px 28px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}

.health-modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #e8f0fe;
  letter-spacing: 0.5px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.export-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: rgba(59, 130, 246, 0.15);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 6px;
  color: #3b82f6;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.export-btn:hover {
  background: rgba(59, 130, 246, 0.25);
  border-color: rgba(59, 130, 246, 0.5);
}

.close-btn {
  background: none;
  border: none;
  color: rgba(255,255,255,0.4);
  font-size: 20px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.2s;
  line-height: 1;
}

.close-btn:hover {
  color: #fff;
  background: rgba(255,255,255,0.08);
}

.health-modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px 20px;
}

.health-modal-body::-webkit-scrollbar {
  width: 6px;
}

.health-modal-body::-webkit-scrollbar-track {
  background: transparent;
}

.health-modal-body::-webkit-scrollbar-thumb {
  background: rgba(255,255,255,0.1);
  border-radius: 3px;
}

.health-modal-body::-webkit-scrollbar-thumb:hover {
  background: rgba(255,255,255,0.2);
}

/* 统计概览 */
.health-stats-row {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.health-stat-card {
  flex: 1;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-label {
  font-size: 12px;
  color: rgba(255,255,255,0.4);
  font-weight: 500;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #e8f0fe;
}

.stat-value.green {
  color: #10b981;
}

.stat-level {
  font-size: 13px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  display: inline-block;
  width: fit-content;
}
.stat-level.健康 {
  color: #10b981;
  background: rgba(16, 185, 129, 0.15);
}
.stat-level.良好 {
  color: #f59e0b;
  background: rgba(245, 158, 11, 0.15);
}
.stat-level.一般 {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.15);
}

.stat-level-desc {
  font-size: 12px;
  color: white;
}

.health-trend-section {
  margin-top: 12px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 8px;
  padding: 12px 16px;
}

.health-trend-section h4 {
  font-size: 13px;
  color: rgba(255,255,255,0.6);
  margin: 0 0 8px 0;
  font-weight: 600;
}

.health-trend-chart {
  width: 100%;
  height: 120px;
}

.health-score-trend-card {
  display: flex;
  align-items: center;
  gap: 20px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 12px;
}

.health-score-trend-card .score-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 140px;
  flex-shrink: 0;
}

.health-score-trend-card .health-trend-chart {
  flex: 1;
  height: 80px;
}

.stat-value.rate {
  color: #3b82f6;
}

/* 表格区域 */
.health-table-section {
  margin-bottom: 20px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid rgba(255,255,255,0.06);
}

.health-table-section h4 {
  margin: 0;
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: rgba(255,255,255,0.85);
  letter-spacing: 0.3px;
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.08) 0%, rgba(0, 120, 255, 0.04) 100%);
  border-bottom: 1px solid rgba(255,255,255,0.06);
}

.health-table-section h4 .section-count {
  font-size: 13px;
  font-weight: 400;
  color: rgba(255,255,255,0.45);
}

.health-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  background: rgba(255,255,255,0.02);
}

.health-table thead th {
  background: rgba(255,255,255,0.04);
  padding: 10px 12px;
  text-align: left;
  color: rgba(255,255,255,0.4);
  font-weight: 600;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  font-size: 12px;
}

.health-table tbody td {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  color: rgba(255,255,255,0.7);
}

.health-table tbody tr:last-child td {
  border-bottom: none;
}

.health-table tbody tr:hover {
  background: rgba(255,255,255,0.02);
}

.category-cell {
  color: white !important;
  font-weight: 500;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 8px;
  vertical-align: middle;
}

.status-dot.green { background: #10b981; }
.status-dot.red { background: #ef4444; }
.status-dot.orange { background: #f59e0b; }
.status-dot.blue { background: #3b82f6; }
.status-dot.gray { background: #6b7280; }
.status-dot.dark { background: #374151; }

/* 客流Agent报表表格 */
.keliu-table .keliu-value {
  font-weight: 600;
  color: rgba(255,255,255,0.9);
}

.keliu-table .keliu-desc {
  color: rgba(255,255,255,0.4);
  font-size: 12px;
}

.pressure-low { color: #10b981; }
.pressure-low-mid { color: #34d399; }
.pressure-mid { color: #f59e0b; }
.pressure-high-mid { color: #f97316; }
.pressure-high { color: #ef4444; }

/* 设备事件统计表格 */
.event-table .event-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}
.event-table .event-remote { background: #3b82f6; }
.event-table .event-notification { background: #10b981; }
.event-table .event-alarm { background: #f59e0b; }
.event-table .event-fault { background: #ef4444; }

.event-table .event-count {
  font-weight: 600;
  color: rgba(255,255,255,0.9);
}

.event-table .event-total-row {
  background: rgba(255,255,255,0.04);
}

.event-table .event-total-row td {
  color: rgba(255,255,255,0.9);
  font-weight: 600;
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
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: overlay-fade-in 0.2s ease;
}
.device-analysis-modal {
  background: linear-gradient(180deg, #0a1628 0%, #0d1f3c 100%);
  border: 1px solid rgba(0, 180, 255, 0.2);
  border-radius: 12px;
  width: 95%;
  max-width: 1200px;
  max-height: 95vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255,255,255,0.05);
  animation: modal-slide-up 0.3s ease;
  overflow: hidden;
}
.device-analysis-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px 28px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.device-analysis-header .dispatch-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.device-analysis-header .dispatch-header-left svg {
  color: #00b4ff;
  flex-shrink: 0;
}
.device-analysis-header .dispatch-header-left h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #e8f0fe;
  letter-spacing: 0.5px;
}
.device-analysis-header .dispatch-subtitle {
  margin: 2px 0 0;
  font-size: 12px;
  color: rgba(255,255,255,0.35);
  font-weight: 400;
}
.device-analysis-header .dispatch-modal-close {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: rgba(255,255,255,0.4);
  font-size: 24px;
  cursor: pointer;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  flex-shrink: 0;
}
.device-analysis-header .dispatch-modal-close:hover {
  background: rgba(255,255,255,0.08);
  color: #fff;
}
.device-analysis-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px 20px;
}
.device-kpi-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.device-kpi-card {
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px;
  padding: 14px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.device-kpi-card.kpi-highlight {
  background: rgba(0, 180, 255, 0.08);
  border-color: rgba(0, 180, 255, 0.2);
}
.herver:hover {
  cursor: pointer;
}

.kpi-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}
.device-kpi-card:nth-child(1) .kpi-icon { background: rgba(0, 180, 255, 0.15); color: #00b4ff; }
.device-kpi-card:nth-child(2) .kpi-icon { background: rgba(0, 180, 255, 0.15); color: #00b4ff; }
.device-kpi-card:nth-child(3) .kpi-icon { background: rgba(255, 70, 70, 0.15); color: #ff4646; }
.device-kpi-card:nth-child(4) .kpi-icon { background: rgba(255, 106, 0, 0.15); color: #ff6a00; }
.device-kpi-card:nth-child(5) .kpi-icon { background: rgba(155, 89, 182, 0.15); color: #9b59b6; }
.kpi-label {
  font-size: 12px;
  color: rgba(255,255,255,0.4);
}
.kpi-value {
  font-size: 24px;
  font-weight: 800;
  color: rgba(255,255,255,0.85);
}
.kpi-health {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.kpi-health .health-percent {
  font-size: 24px;
  font-weight: 800;
  color: #00b4ff !important;
}
.kpi-health .health-count {
  font-size: 13px;
  font-weight: 600;
  margin-left: 4px;
  color: rgba(0, 180, 255, 0.7);
}
.kpi-blue { color: #00b4ff !important; }
.kpi-green { color: #08ae66 !important; }
.kpi-red { color: #ff4646 !important; }
.kpi-orange { color: #ff6a00 !important; }
.analysis-three-cols {
  display: grid;
  grid-template-columns: 1fr 1.2fr 1fr;
  gap: 14px;
  margin-bottom: 16px;
}
.analysis-col {
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px;
  overflow: hidden;
}
.col-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  color: rgba(255,255,255,0.6);
  border-bottom: 1px solid rgba(255,255,255,0.06);
  background: rgba(255,255,255,0.02);
}
.col-title svg {
  color: rgba(0, 180, 255, 0.7);
}
.col-more {
  margin-left: auto;
  font-size: 11px;
  font-weight: 500;
  color: rgba(0, 180, 255, 0.5);
  cursor: pointer;
  transition: all 0.2s;
}
.col-more:hover {
  color: rgba(0, 180, 255, 0.9);
}
.col-content {
  padding: 14px 16px;
}

/* 诊断总览 */
.diagnosis-overview {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 14px;
}
.diagnosis-score-chart {
  width: 100px;
  height: 100px;
  flex-shrink: 0;
}
.diagnosis-summary {
  flex: 1;
  font-size: 12px;
  color: rgba(255,255,255,0.55);
  line-height: 1.7;
  margin: 0;
}
.risk-judgment {
  background: rgba(255,255,255,0.02);
  border-radius: 8px;
  padding: 10px 12px;
}
.risk-title {
  font-size: 12px;
  font-weight: 600;
  color: rgba(255,255,255,0.6);
  margin-bottom: 8px;
}
.risk-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  color: rgba(255,255,255,0.65);
  line-height: 1.6;
  margin-bottom: 6px;
}
.risk-item:last-child { margin-bottom: 0; }
.risk-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 5px;
}
.risk-danger { background: #ff4646; }
.risk-warning { background: #ff6a00; }
.risk-info { background: #00b4ff; }

/* 重点异常设备 */
.key-device-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.key-device-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 8px;
  transition: all 0.2s;
}
.key-device-card:hover {
  border-color: rgba(0, 180, 255, 0.2);
}
.key-device-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: transparent !important;
}
.icon-fan { color: #ff4646; }
.icon-pump { color: #ff6a00; }
.icon-cabinet { color: #00b4ff; }
.key-device-info { flex: 1; min-width: 0; }
.key-device-title {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255,255,255,0.85);
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.severity-badge {
  font-size: 10px;
  padding: 1px 8px;
  border-radius: 4px;
  font-weight: 600;
}
.sev-danger { background: rgba(255, 70, 70, 0.2); color: #ff4646; }
.sev-warning { background: rgba(255, 106, 0, 0.2); color: #ff6a00; }
.sev-normal { background: rgba(0, 180, 255, 0.2); color: #00b4ff; }
.key-device-area {
  font-size: 11px;
  color: rgba(255,255,255,0.4);
  margin-bottom: 2px;
}
.key-device-detail {
  font-size: 11px;
  color: white;
}
.key-device-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 16px;
  border: 1px solid rgba(0, 180, 255, 0.3);
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.15), rgba(0, 180, 255, 0.05));
  color: #00b4ff;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
  animation: btn-glow 2.5s ease-in-out infinite;
}
@keyframes btn-glow {
  0%, 100% {
    box-shadow: 0 0 1px 1px rgba(0, 180, 255, 0.2);
  }
  50% {
    box-shadow: 0 0 2px 2px rgba(0, 180, 255, 0.45);
  }
}
.key-device-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(0, 180, 255, 0.2), transparent);
  transition: left 0.5s ease;
}
.key-device-btn:hover {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.3), rgba(0, 180, 255, 0.1));
  border-color: rgba(0, 180, 255, 0.6);
  transform: translateY(-1px);
}
.key-device-btn:hover::before {
  left: 100%;
}
.key-device-btn:hover {
  animation: none;
  box-shadow: 0 0 20px 5px rgba(0, 180, 255, 0.5);
}
.key-device-btn svg {
  flex-shrink: 0;
}

/* 维修进度 */
.maintenance-progress-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.maintenance-task {
  padding: 10px 12px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 8px;
}
.task-header {
  display: flex;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 8px;
}
.task-id { color: rgba(255,255,255,0.35); }
.task-device { color: rgba(255,255,255,0.85); font-weight: 600; }
.task-worker { color: rgba(255,255,255,0.35); }
.task-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.progress-bar {
  flex: 1;
  height: 6px;
  background: rgba(255,255,255,0.08);
  border-radius: 3px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #00b4ff, #69b1ff);
  border-radius: 3px;
  transition: width 0.5s ease;
}
.progress-fill.done {
  background: linear-gradient(90deg, #08ae66, #34d399);
}
.progress-fill.processing {
  background: linear-gradient(90deg, #00b4ff, #69b1ff);
}
.progress-fill.pending {
  background: linear-gradient(90deg, #8b5cf6, #a78bfa);
}
.progress-text {
  font-size: 11px;
  font-weight: 700;
  color: rgba(255,255,255,0.6);
  min-width: 36px;
  text-align: right;
}
.task-status {
  font-size: 11px;
  font-weight: 600;
  text-align: right;
}
.done-text { color: #08ae66; }
.processing-text { color: #00b4ff; }
.pending-text { color: #8b5cf6; }

/* 近7日异常趋势 */
.trend-section {
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px;
  overflow: hidden;
}
.device-trend-chart {
  width: 100%;
  height: 200px;
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

/* ===== Agent协作详情弹窗 ===== */
.agent-collab-overlay {
  position: fixed; inset: 0; background: rgba(0, 0, 0, 0.2);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
  animation: overlay-fade-in 0.2s ease;
}
.agent-collab-modal {
  /* background: linear-gradient(180deg, #0a1628 0%, #0d1f3c 100%); */
  background: rgb(0, 66, 126);
  border: 1px solid rgba(0, 180, 255, 0.2);
  border-radius: 12px;
  width: 95%; max-width: 1200px;
  max-height: 95vh;
  display: flex; flex-direction: column;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255,255,255,0.05);
  animation: modal-slide-up 0.3s ease;
  overflow: hidden;
}
.agent-collab-header {
  display: flex; justify-content: space-between; align-items: flex-start;
  padding: 20px 28px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.agent-collab-header h3 {
  margin: 0; font-size: 18px; font-weight: 700;
  color: #e8f0fe; letter-spacing: 0.5px;
}
.agent-collab-body {
  flex: 1; overflow-y: auto; padding: 16px 20px 20px;
}
.agent-collab-body::-webkit-scrollbar {
  width: 6px;
}
.agent-collab-body::-webkit-scrollbar-track {
  background: transparent;
}
.agent-collab-body::-webkit-scrollbar-thumb {
  background: rgba(255,255,255,0.12);
  border-radius: 3px;
}
.agent-collab-body::-webkit-scrollbar-thumb:hover {
  background: rgba(255,255,255,0.2);
}
.agent-collab-header .close-btn {
  width: 32px; height: 32px; border: none;
  background: transparent; color: rgba(255,255,255,0.4);
  font-size: 24px; cursor: pointer; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.15s; flex-shrink: 0;
}
.agent-collab-header .close-btn:hover {
  background: rgba(255,255,255,0.08); color: #fff;
}

.collab-stats-row {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 20px;
}
.collab-stat-card {
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px; padding: 16px; text-align: center;
  display: flex; flex-direction: column; gap: 4px;
}
.collab-stat-card .stat-label { font-size: 12px; color: rgba(255,255,255,0.4); }
.collab-stat-card .stat-value { font-size: 28px; font-weight: 800; color: #e8f0fe; }
.collab-stat-card .stat-value.dispatched { color: #00ff88; }
.collab-stat-card .stat-value.rate { color: #00b4ff; }

.collab-chart-wrapper { margin-bottom: 20px; }
.collab-chart-wrapper h4, .collab-table-wrapper h4 {
  margin: 0 0 12px; font-size: 14px; color: #e8f0fe;
}
.agent-collab-chart { width: 100%; height: 220px; }

.collab-table-wrapper { margin-bottom: 16px; }
.collab-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.collab-table th {
  background: rgba(255,255,255,0.04); padding: 8px 10px; text-align: left;
  color: rgba(255,255,255,0.4); font-weight: 600; border-bottom: 1px solid rgba(255,255,255,0.06);
}
.collab-table td {
  padding: 8px 10px; border-bottom: 1px solid rgba(255,255,255,0.06); color: rgba(255,255,255,0.7);
}
.collab-table .desc-cell { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.detail-btn {
  background: none; border: 1px solid #00b4ff; color: #00b4ff;
  padding: 2px 8px; border-radius: 4px; cursor: pointer; font-size: 11px;
}
.detail-btn:hover { background: rgba(0, 180, 255, 0.15); color: #00b4ff; }

.task-actions { display: flex; gap: 6px; align-items: center; }

.dispatch-btn {
  background: none; border: 1px solid #22c55e; color: #22c55e;
  padding: 2px 8px; border-radius: 4px; cursor: pointer; font-size: 11px;
}
.dispatch-btn:hover { background: rgba(34, 197, 94, 0.15); color: #22c55e; }

.cancel-btn {
  background: none; border: 1px solid #ef4444; color: #ef4444;
  padding: 2px 8px; border-radius: 4px; cursor: pointer; font-size: 11px;
}
.cancel-btn:hover { background: rgba(239, 68, 68, 0.15); color: #ef4444; }

.task-status.cancelled { color: #ef4444; opacity: 0.6; }

.detail-actions {
  justify-content: center;
  padding-top: 12px;
  border-top: 1px solid rgba(255,255,255,0.06);
  margin-top: 4px;
}
.detail-actions .dispatch-btn,
.detail-actions .cancel-btn {
  padding: 6px 20px;
  font-size: 13px;
  border-radius: 6px;
}

.collab-pagination {
  display: flex; align-items: center; justify-content: center; gap: 12px;
  padding: 12px 0; font-size: 12px; color: rgba(255,255,255,0.4);
}
.collab-pagination button {
  background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.08); color: rgba(255,255,255,0.7);
  padding: 4px 12px; border-radius: 6px; cursor: pointer;
}
.collab-pagination button:disabled { opacity: 0.4; cursor: not-allowed; }
.collab-pagination button:not(:disabled):hover { border-color: #00b4ff; color: #00b4ff; }

/* ===== 派发任务详情弹窗 ===== */
.task-detail-overlay {
  position: fixed; inset: 0; background: rgba(0, 0, 0, 0.6);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
  animation: overlay-fade-in 0.2s ease;
}
.task-detail-modal {
  background: rgb(0,60,126);
  border: 1px solid rgba(0, 180, 255, 0.2);
  border-radius: 12px;
  width: 95%; max-width: 600px;
  max-height: 95vh;
  display: flex; flex-direction: column;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255,255,255,0.05);
  animation: modal-slide-up 0.3s ease;
  overflow: hidden;
}
.task-detail-header {
  display: flex; justify-content: space-between; align-items: flex-start;
  padding: 20px 28px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.task-detail-header h3 {
  margin: 0; font-size: 18px; font-weight: 700;
  color: #e8f0fe; letter-spacing: 0.5px;
}
.task-detail-header .close-btn {
  width: 32px; height: 32px; border: none;
  background: transparent; color: rgba(255,255,255,0.4);
  font-size: 24px; cursor: pointer; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.15s; flex-shrink: 0;
}
.task-detail-header .close-btn:hover {
  background: rgba(255,255,255,0.08); color: #fff;
}
.task-detail-body {
  flex: 1; overflow-y: auto; padding: 16px 20px 20px;
}
.detail-row {
  display: flex; padding: 10px 0; border-bottom: 1px solid rgba(255,255,255,0.06);
  font-size: 13px;
}
.detail-row:last-child { border: none; }
.detail-row label {
  flex: 0 0 80px; color: rgba(255,255,255,0.4); font-weight: 600;
}
.detail-row span { flex: 1; color: rgba(255,255,255,0.7); word-break: break-all; }

/* ===== 服务分析详情弹窗 ===== */
.service-analysis-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 10, 30, 0.9);
  backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: saFadeIn 0.25s ease;
}

@keyframes saFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.service-analysis-modal {
  width: 95%;
  max-width: 1400px;
  height: 90vh;
  background: linear-gradient(180deg, #0a1a3a 0%, #061228 100%);
  border: 1px solid rgba(0, 150, 255, 0.2);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 80px rgba(0, 50, 150, 0.4), inset 0 1px 0 rgba(0, 150, 255, 0.1);
  animation: saSlideUp 0.3s ease;
  overflow: hidden;
}

@keyframes saSlideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

/* 弹窗头部 */
.sa-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: linear-gradient(90deg, rgba(0, 100, 255, 0.15) 0%, rgba(0, 150, 255, 0.05) 50%, rgba(0, 100, 255, 0.15) 100%);
  border-bottom: 1px solid rgba(0, 150, 255, 0.2);
  flex-shrink: 0;
}

.sa-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sa-header-left svg {
  color: #00aaff;
  filter: drop-shadow(0 0 8px rgba(0, 170, 255, 0.5));
}

.sa-header-left h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #e0f0ff;
  letter-spacing: 2px;
  text-shadow: 0 0 20px rgba(0, 150, 255, 0.3);
}

.sa-header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.sa-time {
  font-size: 13px;
  color: rgba(200, 220, 255, 0.6);
  letter-spacing: 1px;
}

.sa-modal-close {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: rgba(200, 220, 255, 0.7);
  cursor: pointer;
  padding: 6px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.sa-modal-close:hover {
  background: rgba(255, 80, 80, 0.2);
  border-color: rgba(255, 80, 80, 0.3);
  color: #ff6666;
}

.sa-modal-close svg {
  width: 18px;
  height: 18px;
}

/* 弹窗主体 */
.sa-modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.sa-modal-body::-webkit-scrollbar {
  width: 6px;
}

.sa-modal-body::-webkit-scrollbar-track {
  background: rgba(0, 50, 150, 0.1);
  border-radius: 3px;
}

.sa-modal-body::-webkit-scrollbar-thumb {
  background: rgba(0, 150, 255, 0.3);
  border-radius: 3px;
}

/* 顶部指标卡片 */
.sa-top-metrics {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

.sa-metric-card {
  background: linear-gradient(135deg, rgba(0, 80, 200, 0.12) 0%, rgba(0, 50, 150, 0.08) 100%);
  border: 1px solid rgba(0, 150, 255, 0.15);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  transition: all 0.2s;
}

.sa-metric-card:hover {
  border-color: rgba(0, 150, 255, 0.3);
  background: linear-gradient(135deg, rgba(0, 80, 200, 0.18) 0%, rgba(0, 50, 150, 0.12) 100%);
}

.sa-metric-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(0, 150, 255, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #00aaff;
}

.sa-metric-icon.green {
  background: rgba(0, 255, 136, 0.12);
  color: #00ff88;
}

.sa-metric-icon.blue {
  background: rgba(0, 150, 255, 0.15);
  color: #00aaff;
}

.sa-metric-icon.orange {
  background: rgba(255, 150, 50, 0.12);
  color: #ff9933;
}

.sa-metric-icon.cyan {
  background: rgba(0, 220, 255, 0.12);
  color: #00ddff;
}

.sa-metric-content {
  flex: 1;
  min-width: 0;
}

.sa-metric-label {
  font-size: 12px;
  color: rgba(180, 200, 230, 0.7);
  margin-bottom: 8px;
  letter-spacing: 0.5px;
}

.sa-metric-values {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sa-metric-value {
  font-size: 18px;
  font-weight: 700;
  color: #e0f0ff;
}

.sa-metric-value.arrive {
  color: #00aaff;
}

.sa-metric-value.depart {
  color: #00ff88;
}

.sa-metric-unit {
  font-size: 11px;
  font-weight: 400;
  color: rgba(180, 200, 230, 0.5);
  margin-left: 4px;
}

.sa-metric-score {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sa-score-value {
  font-size: 22px;
  font-weight: 800;
}

.sa-score-value.green {
  color: #00ff88;
  text-shadow: 0 0 12px rgba(0, 255, 136, 0.3);
}

.sa-score-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 600;
}

.sa-score-badge.green {
  background: rgba(0, 255, 136, 0.15);
  color: #00ff88;
  border: 1px solid rgba(0, 255, 136, 0.3);
}

.sa-grade {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sa-grade-value {
  font-size: 24px;
  font-weight: 800;
}

.sa-grade-value.blue {
  color: #00aaff;
  text-shadow: 0 0 12px rgba(0, 170, 255, 0.3);
}

.sa-grade-desc {
  font-size: 11px;
  color: rgba(180, 200, 230, 0.5);
}

.sa-alert-count {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sa-alert-value {
  font-size: 24px;
  font-weight: 800;
}

.sa-alert-value.orange {
  color: #ff9933;
  text-shadow: 0 0 12px rgba(255, 153, 51, 0.3);
}

.sa-alert-desc {
  font-size: 11px;
  color: rgba(180, 200, 230, 0.5);
}

.sa-peak-time {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sa-peak-value {
  font-size: 18px;
  font-weight: 700;
}

.sa-peak-value.cyan {
  color: #00ddff;
  text-shadow: 0 0 12px rgba(0, 221, 255, 0.3);
}

.sa-peak-desc {
  font-size: 11px;
  color: rgba(180, 200, 230, 0.5);
}

/* 中间区域 */
.sa-middle-section {
  display: grid;
  grid-template-columns: 280px 1fr 320px;
  gap: 16px;
  min-height: 320px;
}

.sa-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(200, 220, 255, 0.8);
  margin-bottom: 12px;
  letter-spacing: 1px;
  padding-left: 12px;
  border-left: 0.5px solid rgba(0, 150, 255, 0.5);
}

/* 雷达图面板 */
.sa-radar-panel {
  background: rgba(0, 50, 150, 0.08);
  border: 1px solid rgba(0, 150, 255, 0.12);
  border-radius: 12px;
  padding: 16px;
}

.sa-radar-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: calc(100% - 40px);
}

.sa-radar-svg {
  width: 100%;
  max-width: 220px;
  height: auto;
}

/* 客流趋势面板 */
.sa-trend-panel {
  background: rgba(0, 50, 150, 0.08);
  border: 1px solid rgba(0, 150, 255, 0.12);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.sa-trend-container {
  flex: 1;
  min-height: 160px;
}

/* 环境指标状态 */
.sa-env-status {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(0, 150, 255, 0.1);
}

.sa-env-title {
  font-size: 12px;
  color: rgba(180, 200, 230, 0.6);
  margin-bottom: 10px;
  letter-spacing: 0.5px;
}

.sa-env-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sa-env-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  background: rgba(0, 80, 200, 0.08);
  border-radius: 8px;
  font-size: 12px;
}

.sa-env-icon {
  font-size: 14px;
  width: 20px;
  text-align: center;
}

.sa-env-label {
  color: rgba(180, 200, 230, 0.6);
  width: 40px;
  flex-shrink: 0;
}

.sa-env-value {
  color: #e0f0ff;
  font-weight: 600;
  flex: 1;
}

.sa-env-status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 500;
}

.sa-env-status.ok {
  background: rgba(0, 255, 136, 0.12);
  color: #00ff88;
}

/* AI分析面板 */
.sa-ai-panel {
  background: rgba(0, 50, 150, 0.08);
  border: 1px solid rgba(0, 150, 255, 0.12);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sa-ai-header {
  display: flex;
  align-items: center;
}

.sa-ai-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: linear-gradient(135deg, rgba(0, 150, 255, 0.2) 0%, rgba(0, 100, 200, 0.15) 100%);
  border: 1px solid rgba(0, 150, 255, 0.3);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #00aaff;
  letter-spacing: 1px;
}

.sa-ai-badge svg {
  color: #00ddff;
}

.sa-ai-content {
  padding: 12px;
  background: rgba(0, 80, 200, 0.06);
  border-radius: 8px;
  border-left: 0.5px solid rgba(0, 150, 255, 0.4);
}

.sa-ai-text {
  margin: 0;
  font-size: 13px;
  color: rgba(200, 220, 255, 0.75);
  line-height: 1.7;
}

.sa-highlight {
  color: #00ff88;
  font-weight: 700;
}

.sa-ai-section {
  padding-left: 4px;
}

.sa-ai-section-title {
  font-size: 12px;
  font-weight: 600;
  color: rgba(200, 220, 255, 0.6);
  margin-bottom: 8px;
  letter-spacing: 0.5px;
}

.sa-ai-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.sa-ai-list li {
  position: relative;
  padding-left: 16px;
  font-size: 12px;
  color: rgba(180, 200, 230, 0.65);
  line-height: 1.7;
  margin-bottom: 6px;
}

.sa-ai-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 7px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(0, 150, 255, 0.5);
}

.sa-ai-actions {
  display: flex;
  gap: 12px;
  margin-top: auto;
  padding-top: 8px;
}

.sa-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
  letter-spacing: 0.5px;
}

.sa-btn.primary {
  background: linear-gradient(135deg, #0088ff 0%, #0066cc 100%);
  color: white;
  box-shadow: 0 4px 15px rgba(0, 136, 255, 0.3);
}

.sa-btn.primary:hover {
  box-shadow: 0 6px 20px rgba(0, 136, 255, 0.5);
  transform: translateY(-1px);
}

.sa-btn.secondary {
  background: rgba(0, 150, 255, 0.12);
  color: #00aaff;
  border: 1px solid rgba(0, 150, 255, 0.3);
}

.sa-btn.secondary:hover {
  background: rgba(0, 150, 255, 0.2);
  border-color: rgba(0, 150, 255, 0.5);
}

/* 底部服务事件 */
.sa-bottom-section {
  background: rgba(0, 50, 150, 0.08);
  border: 1px solid rgba(0, 150, 255, 0.12);
  border-radius: 12px;
  padding: 16px;
}

.sa-event-table {
  margin-top: 12px;
}

.sa-table-header {
  display: grid;
  grid-template-columns: 120px 1fr 80px 100px 1fr;
  gap: 12px;
  padding: 10px 14px;
  background: rgba(0, 80, 200, 0.1);
  border-radius: 8px;
  margin-bottom: 8px;
}

.sa-th {
  font-size: 12px;
  font-weight: 600;
  color: rgba(180, 200, 230, 0.6);
  letter-spacing: 0.5px;
}

.sa-table-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sa-tr {
  display: grid;
  grid-template-columns: 120px 1fr 80px 100px 1fr;
  gap: 12px;
  padding: 12px 14px;
  background: rgba(0, 60, 180, 0.06);
  border-radius: 8px;
  border: 1px solid rgba(0, 150, 255, 0.06);
  transition: all 0.2s;
}

.sa-tr:hover {
  background: rgba(0, 80, 200, 0.1);
  border-color: rgba(0, 150, 255, 0.15);
}

.sa-td {
  font-size: 12px;
  color: rgba(200, 220, 255, 0.7);
  display: flex;
  align-items: center;
}

.sa-td.time {
  color: rgba(180, 200, 230, 0.5);
  font-family: 'Courier New', monospace;
}

.sa-level-badge {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 999px;
  font-weight: 600;
}

.sa-level-badge.low {
  background: rgba(0, 255, 136, 0.12);
  color: #00ff88;
}

.sa-status-badge {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 999px;
  font-weight: 600;
}

.sa-status-badge.done {
  background: rgba(0, 255, 136, 0.12);
  color: #00ff88;
}

.sa-td.detail {
  color: rgba(180, 200, 230, 0.6);
}

/* ===== Dispatch任务加载动画 ===== */
.dispatch-loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.85);
  backdrop-filter: blur(12px);
  z-index: 100000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dispatch-loading-modal {
  position: relative;
  background: linear-gradient(145deg, rgba(10, 25, 60, 0.98), rgba(5, 15, 40, 0.98));
  border: 2px solid rgba(59, 130, 246, 0.4);
  border-radius: 24px;
  padding: 60px 80px;
  min-width: 500px;
  min-height: 400px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow:
      0 0 60px rgba(59, 130, 246, 0.3),
      0 0 120px rgba(59, 130, 246, 0.15),
      inset 0 1px 0 rgba(255, 255, 255, 0.1);
}

.dispatch-loading-close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.7);
  font-size: 24px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.dispatch-loading-close:hover {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.4);
  color: #fff;
}

.dispatch-loading-spinner {
  position: relative;
  width: 180px;
  height: 180px;
  margin-bottom: 40px;
}

.spinner-ring {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  border: 4px solid transparent;
}

.spinner-ring:nth-child(1) {
  border-top-color: #3b82f6;
  border-right-color: rgba(59, 130, 246, 0.6);
  animation: dispatchSpin 1.5s linear infinite;
}

.spinner-ring:nth-child(2) {
  width: 75%;
  height: 75%;
  top: 12.5%;
  left: 12.5%;
  border-bottom-color: #06b6d4;
  border-left-color: rgba(6, 182, 212, 0.6);
  animation: dispatchSpin 2s linear infinite reverse;
}

.spinner-ring:nth-child(3) {
  width: 50%;
  height: 50%;
  top: 25%;
  left: 25%;
  border-top-color: #8b5cf6;
  border-right-color: rgba(139, 92, 246, 0.6);
  animation: dispatchSpin 1.2s linear infinite;
}

.spinner-core {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 60px;
  height: 60px;
  background: linear-gradient(145deg, #1e3a8a, #0c1a4a);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #60a5fa;
  box-shadow:
      0 0 30px rgba(59, 130, 246, 0.5),
      0 0 60px rgba(59, 130, 246, 0.25);
  animation: dispatchPulse 2s ease-in-out infinite;
}

.spinner-core svg {
  width: 32px;
  height: 32px;
}

.dispatch-loading-text {
  text-align: center;
  margin-bottom: 24px;
}

.dispatch-loading-text h3 {
  font-size: 26px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 12px 0;
  text-shadow: 0 0 20px rgba(59, 130, 246, 0.5);
  letter-spacing: 1px;
}

.dispatch-loading-text p {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.6);
  margin: 0;
  line-height: 1.6;
}

.dispatch-loading-dots {
  display: flex;
  gap: 10px;
}

.dispatch-loading-dots span {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #3b82f6;
  animation: dispatchBounce 1.4s ease-in-out infinite;
}

.dispatch-loading-dots span:nth-child(2) {
  animation-delay: 0.2s;
  background: #06b6d4;
}

.dispatch-loading-dots span:nth-child(3) {
  animation-delay: 0.4s;
  background: #8b5cf6;
}

@keyframes dispatchSpin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

@keyframes dispatchPulse {
  0%, 100% {
    transform: translate(-50%, -50%) scale(1);
    box-shadow: 0 0 30px rgba(59, 130, 246, 0.5), 0 0 60px rgba(59, 130, 246, 0.25);
  }
  50% {
    transform: translate(-50%, -50%) scale(1.15);
    box-shadow: 0 0 50px rgba(59, 130, 246, 0.7), 0 0 100px rgba(59, 130, 246, 0.4);
  }
}

@keyframes dispatchBounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1.2);
    opacity: 1;
  }
}

/* Fade transition */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 环境分析按钮 */
.env-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(96, 165, 250, 0.1);
  border: 1px solid rgba(96, 165, 250, 0.25);
  color: #93c5fd;
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}

.env-btn:hover {
  background: rgba(96, 165, 250, 0.2);
  border-color: rgba(96, 165, 250, 0.4);
  color: #bfdbfe;
  box-shadow: 0 0 20px rgba(96, 165, 250, 0.15);
}

.env-btn svg {
  filter: drop-shadow(0 0 4px rgba(96, 165, 250, 0.4));
}

.env-btn-ready {
  border-color: #10b981 !important;
  color: #34d399 !important;
}

.env-btn-ready:hover {
  border-color: #10b981 !important;
  color: #6ee7b7 !important;
  box-shadow: 0 0 20px rgba(16, 185, 129, 0.15) !important;
}

.env-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-processing {
  background: #60a5fa;
  animation: envDotPulse 1.5s ease-in-out infinite;
}

.dot-ready {
  background: #10b981;
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.6);
}

@keyframes envDotPulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 1; }
}

</style>
