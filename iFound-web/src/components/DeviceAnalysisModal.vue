<script setup>
import { nextTick, ref } from 'vue'
import * as echarts from 'echarts'
import { listDeviceStatePage } from '@/service/deviceApi'

const emit = defineEmits(['close', 'dispatch'])

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

function getDomainClass(domain) {
  if (!domain) return 'env'
  if (domain.includes('设备')) return 'device'
  if (domain.includes('能耗')) return 'energy'
  return 'env'
}

function handleAnomalyAction(btnText, action) {
  if (btnText === '维修') {
    emit('dispatch', action, 'repair')
  } else if (btnText === '排查') {
    emit('dispatch', action, 'check')
  } else if (btnText === '巡检') {
    emit('dispatch', action, 'inspect')
  }
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
  emit('close')
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

defineExpose({
  openDeviceAnalysis,
  closeDeviceAnalysis
})
</script>

<template>
  <div v-if="isDeviceAnalysisOpen" class="device-analysis-overlay" @click.self="closeDeviceAnalysis">
    <div class="device-analysis-modal">
      <div class="device-analysis-header">
        <div class="dispatch-header-left">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
          </svg>
          <div>
            <h3>设备分析</h3>
            <p class="dispatch-subtitle">运行状态 · 异常诊断 · 维修进度</p>
          </div>
        </div>
        <button class="dispatch-modal-close" @click="closeDeviceAnalysis">×</button>
      </div>

      <div class="device-analysis-body">
        <!-- KPI指标 -->
        <div class="device-kpi-row">
          <div class="device-kpi-card">
            <div class="kpi-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg></div>
            <div class="kpi-label">总设备数</div>
            <div class="kpi-value">{{ deviceAnalysisData.totalDevices }}</div>
          </div>
          <div class="device-kpi-card kpi-highlight">
            <div class="kpi-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg></div>
            <div class="kpi-label">设备健康度</div>
            <div class="kpi-value kpi-blue kpi-health">
              <span class="health-percent">{{ deviceAnalysisData.healthScore }}%</span>
              <span class="health-count">{{ deviceAnalysisData.normalDevices }}台</span>
            </div>
          </div>
          <div class="device-kpi-card">
            <div class="kpi-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg></div>
            <div class="kpi-label">异常设备</div>
            <div class="kpi-value kpi-red">{{ deviceAnalysisData.faultDevices }}</div>
          </div>
          <div class="device-kpi-card">
            <div class="kpi-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg></div>
            <div class="kpi-label">告警设备</div>
            <div class="kpi-value kpi-orange">{{ deviceAnalysisData.alarmDevices }}</div>
          </div>
          <div class="device-kpi-card">
            <div class="kpi-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg></div>
            <div class="kpi-label">维修设备</div>
            <div class="kpi-value kpi-orange">{{ deviceAnalysisData.maintenanceDevices }}</div>
          </div>
        </div>

        <!-- 诊断总览 + 重点异常设备 + 维修进度 三栏 -->
        <div class="analysis-three-cols">
          <!-- 左栏：诊断总览 -->
          <div class="analysis-col">
            <div class="col-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
              诊断总览
            </div>
            <div class="col-content">
              <div class="diagnosis-overview">
                <div ref="deviceHealthScoreChartRef" class="diagnosis-score-chart"></div>
                <p class="diagnosis-summary">当前站房设备运行总体平稳，健康度良好。检测到 {{ deviceAnalysisData.faultDevices + deviceAnalysisData.alarmDevices }} 台设备存在异常，其中 {{ deviceAnalysisData.faultDevices }} 台风险较高，建议优先处理，确保系统稳定运行。</p>
              </div>
              <div class="risk-judgment">
                <div class="risk-title">风险判断</div>
                <div v-for="(item, idx) in deviceAnalysisData.riskItems" :key="idx" class="risk-item">
                  <span class="risk-dot" :class="'risk-' + item.level"></span>
                  <span>{{ item.text }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 中栏：重点异常设备 -->
          <div class="analysis-col">
            <div class="col-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
              重点异常设备
              <span class="col-more">更多</span>
            </div>
            <div class="col-content">
              <div class="key-device-list">
                <div v-for="(dev, idx) in deviceAnalysisData.keyDevices" :key="idx" class="key-device-card">
                  <div class="key-device-icon" :class="'icon-' + dev.icon">
                    <svg v-if="dev.icon === 'fan'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" width="32" height="32">
                      <circle cx="12" cy="12" r="10" stroke-width="1.2"/>
                      <circle cx="12" cy="12" r="2.5" fill="currentColor"/>
                      <path d="M12 9.5c-1.5-3-3-5.5-3-7a3 3 0 0 1 6 0c0 1.5-1.5 4-3 7z" fill="currentColor" opacity="0.35"/>
                      <path d="M14.5 12c3-1.5 5.5-3 7-3a3 3 0 0 1 0 6c-1.5 0-4-1.5-7-3z" fill="currentColor" opacity="0.35"/>
                      <path d="M12 14.5c1.5 3 3 5.5 3 7a3 3 0 0 1-6 0c0-1.5 1.5-4 3-7z" fill="currentColor" opacity="0.35"/>
                      <path d="M9.5 12c-3 1.5-5.5 3-7 3a3 3 0 0 1 0-6c1.5 0 4 1.5 7 3z" fill="currentColor" opacity="0.35"/>
                    </svg>
                    <svg v-else-if="dev.icon === 'pump'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" width="32" height="32">
                      <rect x="2" y="7" width="14" height="12" rx="2" fill="currentColor" opacity="0.15"/>
                      <rect x="2" y="7" width="14" height="12" rx="2"/>
                      <path d="M16 10h4a2 2 0 0 1 2 2v0a2 2 0 0 1-2 2h-4" fill="currentColor" opacity="0.15"/>
                      <path d="M16 10h4a2 2 0 0 1 2 2v0a2 2 0 0 1-2 2h-4"/>
                      <circle cx="9" cy="13" r="3" fill="currentColor" opacity="0.2"/>
                      <circle cx="9" cy="13" r="3"/>
                      <path d="M5 7V5a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2"/>
                      <path d="M8 4v3" stroke-width="1"/>
                    </svg>
                    <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" width="32" height="32">
                      <rect x="3" y="2" width="18" height="20" rx="2" fill="currentColor" opacity="0.1"/>
                      <rect x="3" y="2" width="18" height="20" rx="2"/>
                      <line x1="3" y1="7" x2="21" y2="7"/>
                      <circle cx="8" cy="11" r="1.2" fill="currentColor"/>
                      <circle cx="12" cy="11" r="1.2" fill="currentColor"/>
                      <circle cx="16" cy="11" r="1.2" fill="currentColor"/>
                      <circle cx="8" cy="16" r="1.2" fill="currentColor"/>
                      <circle cx="12" cy="16" r="1.2" fill="currentColor"/>
                      <circle cx="16" cy="16" r="1.2" fill="currentColor"/>
                      <path d="M9 4h6" stroke-width="1"/>
                    </svg>
                  </div>
                  <div class="key-device-info">
                    <div class="key-device-title">
                      {{ dev.title }}
                      <span class="severity-badge" :class="dev.severityClass">{{ dev.severity }}</span>
                    </div>
                    <div class="key-device-area">区域：{{ dev.area }}</div>
                    <div class="key-device-detail">异常：{{ dev.detail }}</div>
                  </div>
                  <button class="key-device-btn" @click.stop="handleAnomalyAction('排查', dev)">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 14a1 1 0 1 1 1-1 1 1 0 0 1-1 1zm1-4.5a1 1 0 0 1-2 0V7a1 1 0 0 1 2 0z"/></svg>
                      AI诊断
                    </button>
                </div>
              </div>
            </div>
          </div>

          <!-- 右栏：维修进度 -->
          <div class="analysis-col">
            <div class="col-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
              维修进度
              <span class="col-more">更多</span>
            </div>
            <div class="col-content">
              <div class="maintenance-progress-list">
                <div v-for="(task, idx) in deviceAnalysisData.maintenanceProgress" :key="idx" class="maintenance-task">
                  <div class="task-header">
                    <span class="task-id">{{ task.id }}</span>
                    <span class="task-device">{{ task.device }}</span>
                    <span class="task-worker">{{ task.worker }}</span>
                  </div>
                  <div class="task-progress">
                    <div class="progress-bar">
                      <div class="progress-fill" :style="{ width: task.progress + '%' }" :class="task.statusClass"></div>
                    </div>
                    <span class="progress-text">{{ task.progress }}%</span>
                  </div>
                  <div class="task-status" :class="task.statusClass + '-text'">{{ task.status }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 近7日异常趋势 -->
        <div class="trend-section">
          <div class="col-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
            近7日异常趋势
          </div>
          <div ref="deviceTrendChartRef" class="device-trend-chart"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.herver:hover {
  cursor: pointer;
}

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
@keyframes overlay-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes modal-slide-up {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>