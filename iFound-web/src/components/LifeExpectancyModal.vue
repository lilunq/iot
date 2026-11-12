<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { aiRequest } from '@/utils/request'

const props = defineProps({
  orgId: { type: [String, Number], default: 3 }
})

const emit = defineEmits(['close'])
const chartRef = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const emptyMessage = ref('')
const timeRange = ref('')
const summary = ref({
  latestScore: null,
  maxScore: null,
  minScore: null,
  taskCount: 0,
  dispatchedCount: 0,
  agentCount: 0,
  agentNames: []
})

const CACHE_TTL = 5 * 60 * 1000
const HOURS = 24 * 10
const BUCKET_MINUTES = 1440
const dispatchedFilter = ref(null)
let chart = null
let resizeObserver = null

const agentColorMap = ref({})
const agentColorPalette = [
  '#6366f1', '#06b6d4', '#22c55e', '#f59e0b', '#ef4444',
  '#8b5cf6', '#ec4899', '#14b8a6', '#3b82f6', '#f97316'
]

function getAgentColor(name) {
  if (!agentColorMap.value[name]) {
    const idx = Object.keys(agentColorMap.value).length
    agentColorMap.value[name] = agentColorPalette[idx % agentColorPalette.length]
  }
  return agentColorMap.value[name]
}

function cacheKey() {
  return `health-collab-evolution:v3:${props.orgId}:${HOURS}:${BUCKET_MINUTES}:${dispatchedFilter.value ?? 'all'}`
}

function readCache() {
  try {
    const cached = JSON.parse(sessionStorage.getItem(cacheKey()) || 'null')
    if (cached?.timestamp && Date.now() - cached.timestamp < CACHE_TTL && cached.data) {
      return cached.data
    }
  } catch (error) {
    console.warn('读取演变数据缓存失败:', error)
  }
  return null
}

function writeCache(data) {
  try {
    sessionStorage.setItem(cacheKey(), JSON.stringify({ timestamp: Date.now(), data }))
  } catch (error) {
    console.warn('写入演变数据缓存失败:', error)
  }
}

async function loadData(forceRefresh = false) {
  loading.value = true
  errorMessage.value = ''
  emptyMessage.value = ''

  try {
    let data = forceRefresh ? null : readCache()
    if (!data) {
      const params = {
        org_id: props.orgId,
        hours: HOURS,
        bucket_minutes: BUCKET_MINUTES
      }
      if (dispatchedFilter.value !== null) {
        params.dispatched = dispatchedFilter.value
      }
      const res = await aiRequest.get('/dispatch/health-collab-evolution', { params })
      if (!res || (res.code !== 200 && res.code !== '200') || !res.data) {
        const backendMessage = res?.message || res?.msg || '接口返回异常'
        if (backendMessage === '任务不存在') {
          throw new Error('演变接口尚未在运行中的后端生效，请重新编译并重启 xian 服务')
        }
        throw new Error(backendMessage)
      }
      data = res.data
      writeCache(data)
    }

    summary.value = { ...summary.value, ...(data.summary || {}) }
    timeRange.value = data.startTime && data.endTime ? `${data.startTime} 至 ${data.endTime}` : ''
    const timeline = Array.isArray(data.timeline) ? data.timeline : []
    const hasData = timeline.some(item => item.averageScore != null || (item.agents && item.agents.length > 0))
    if (!hasData) {
      emptyMessage.value = '暂无评分或Agent协作记录'
      chart?.clear()
      return
    }

    await nextTick()
    renderChart(timeline)
  } catch (error) {
    console.error('获取站房状态与Agent协作演变失败:', error)
    errorMessage.value = error?.message || '数据加载失败，请稍后重试'
    chart?.clear()
  } finally {
    loading.value = false
  }
}

function renderChart(timeline) {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  let latestScore = null
  let cumulativeDispatches = 0
  const frames = timeline.flatMap(item => {
    if (item.averageScore != null) latestScore = Number(item.averageScore)
    const agents = Array.isArray(item.agents) ? item.agents : []
    const frameDispatched = agents.reduce((sum, a) => sum + Number(a.dispatchedCount || 0), 0)
    cumulativeDispatches += frameDispatched
    if (!Number.isFinite(latestScore)) return []
    return [{
      ...item,
      score: latestScore,
      cumulativeDispatches,
      agents
    }]
  })

  if (!frames.length) {
    emptyMessage.value = '最近暂无可关联的站房评分记录'
    chart.clear()
    return
  }

  const timelineLabels = frames.map(item => item.time?.slice(0, 10) || '')
  const maxDispatches = Math.max(1, ...frames.map(item => item.cumulativeDispatches))

  const allAgentNames = [...new Set(frames.flatMap(f => (f.agents || []).map(a => a.name)))]
  // sync colors with legend
  allAgentNames.forEach((name, idx) => {
    if (!agentColorMap.value[name]) {
      const existing = Object.keys(agentColorMap.value).length
      agentColorMap.value[name] = agentColorPalette[(idx + existing) % agentColorPalette.length]
    }
  })

  const sphereStyle = {
    shadowBlur: 22,
    shadowOffsetX: 4,
    shadowOffsetY: 8,
    shadowColor: 'rgba(0,0,0,.55)',
    borderColor: 'rgba(255,255,255,.6)',
    borderWidth: 2
  }

  const bubbleSize = value => Math.max(42, Math.min(110, 42 + Math.sqrt(Math.max(0, value[2] || 0)) * 18))

  function buildFrameSeries(frame) {
    const agents = frame.agents || []
    const count = agents.length
    const series = []

    agents.forEach((agent, idx) => {
      const agentName = agent.name
      const agentTaskCount = Number(agent.taskCount || 0)
      const agentDispatchedCount = Number(agent.dispatchedCount || 0)
      const jitter = count > 1 ? ((idx / (count - 1)) - 0.5) * 10 : 0
      const color = agentColorMap.value[agentName] || agentColorPalette[0]
      series.push({
        name: agentName,
        type: 'scatter',
        data: [[
          frame.score + jitter,
          frame.cumulativeDispatches,
          agentTaskCount,
          agentName,
          agentTaskCount,
          agentDispatchedCount,
          frame.minScore,
          frame.maxScore
        ]],
        symbolSize: bubbleSize,
        itemStyle: {
          ...sphereStyle,
          color: new echarts.graphic.RadialGradient(.3, .3, .72, [
            { offset: 0, color: '#fff' },
            { offset: .3, color: color },
            { offset: 1, color: color.replace(')', ',0.4)').replace('rgb', 'rgba') }
          ])
        },
        emphasis: { scale: 1.15 }
      })
    })

    if (count === 0) {
      series.push({
        name: '站房评分',
        type: 'scatter',
        data: [[frame.score, frame.cumulativeDispatches, 1, '站房评分', 0, 0, frame.minScore, frame.maxScore]],
        symbolSize: bubbleSize,
        itemStyle: {
          ...sphereStyle,
          color: new echarts.graphic.RadialGradient(.3, .3, .72, [
            { offset: 0, color: '#b9e7ff' },
            { offset: .28, color: '#57b8f4' },
            { offset: .68, color: '#1677b8' },
            { offset: 1, color: '#07446f' }
          ])
        },
        emphasis: { scale: 1.15 }
      })
    }

    return series
  }

  const option = {
    baseOption: {
      timeline: {
        axisType: 'category',
        orient: 'vertical',
        autoPlay: true,
        inverse: true,
        playInterval: 1200,
        right: 6,
        top: 28,
        bottom: 24,
        width: 88,
        symbol: 'none',
        checkpointStyle: { borderWidth: 2, borderColor: '#00d8ff', color: '#0879bd' },
        controlStyle: { showNextBtn: false, showPrevBtn: false, color: '#80d8ff', borderColor: '#80d8ff' },
        lineStyle: { color: 'rgba(0,162,255,.34)' },
        label: { color: '#90caf9', fontSize: 13 },
        itemStyle: { color: '#0072ff' },
        data: timelineLabels
      },
      title: [
        {
          text: timelineLabels[0],
          textAlign: 'center',
          left: '61%',
          top: '54%',
          textStyle: { fontSize: 90, color: 'rgba(0,216,255,.12)', fontWeight: 'bold', textShadowColor: 'rgba(0,216,255,.25)', textShadowBlur: 28 }
        },
        {
          text: '真实评分与Agent协作演变',
          left: 'center',
          top: 10,
          textStyle: { fontWeight: 'normal', fontSize: 20, color: '#e0f0ff', textShadowColor: 'rgba(0,150,255,.22)', textShadowBlur: 10 }
        }
      ],
      tooltip: {
        padding: 12,
        borderWidth: 1,
        backgroundColor: 'rgba(7,25,48,.96)',
        borderColor: 'rgba(0,180,255,.35)',
        textStyle: { color: '#e8f0fe', fontSize: 14 },
        formatter(params) {
          const value = params.value || []
          return [
            `<b>${params.seriesName}</b>`,
            `站房评分：${value[0]?.toFixed?.(1) ?? value[0]}`,
            `评分范围：${value[6] ?? '—'} ~ ${value[7] ?? '—'}`,
            `该Agent创建任务：${value[4]}`,
            `该Agent已派发：${value[5]}`
          ].join('<br/>')
        }
      },
      grid: { top: 80, bottom: 56, left: 72, right: 120 },
      xAxis: {
        type: 'value',
        name: '站房评分',
        min: 0,
        max: 100,
        nameGap: 32,
        nameLocation: 'middle',
        nameTextStyle: { fontSize: 17, color: '#90caf9' },
        splitLine: { lineStyle: { color: 'rgba(0,162,255,.09)' } },
        axisLabel: { color: '#90caf9', fontSize: 13 },
        axisLine: { lineStyle: { color: 'rgba(0,162,255,.3)' } }
      },
      yAxis: {
        type: 'value',
        name: '累计Agent协作',
        min: 0,
        max: Math.ceil(maxDispatches * 1.25),
        minInterval: 1,
        nameTextStyle: { fontSize: 17, color: '#90caf9' },
        splitLine: { lineStyle: { color: 'rgba(0,162,255,.09)' } },
        axisLabel: { color: '#90caf9', fontSize: 13 },
        axisLine: { lineStyle: { color: 'rgba(0,162,255,.3)' } }
      },
      series: buildFrameSeries(frames[0]),
      animationDurationUpdate: 1000,
      animationEasingUpdate: 'quinticInOut'
    },
    options: frames.map((item, index) => ({
      title: [
        { show: true, text: timelineLabels[index], textAlign: 'center', left: '61%', top: '54%', textStyle: { fontSize: 90, color: 'rgba(0,216,255,.12)', fontWeight: 'bold', textShadowColor: 'rgba(0,216,255,.25)', textShadowBlur: 28 } },
        { show: true, text: '站房评分与Agent协作演变', left: 'center', top: 10, textStyle: { fontWeight: 'normal', fontSize: 20, color: '#e0f0ff', textShadowColor: 'rgba(0,150,255,.22)', textShadowBlur: 10 } }
      ],
      series: buildFrameSeries(item)
    }))
  }

  chart.setOption(option, true)
  chart.resize()
}

onMounted(() => {
  resizeObserver = new ResizeObserver(() => chart?.resize())
  if (chartRef.value) resizeObserver.observe(chartRef.value)
  loadData()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
})

function closeModal() {
  emit('close')
}
</script>

<template>
  <div class="life-overlay" @click.self="closeModal">
    <section class="life-modal" role="dialog" aria-modal="true" aria-labelledby="life-modal-title">
      <header class="life-modal-header">
        <div class="life-header-left">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"
            aria-hidden="true">
            <circle cx="12" cy="12" r="10" />
            <polyline points="12 6 12 12 16 14" />
          </svg>
          <div>
            <h3 id="life-modal-title">站房状态与Agent协作关系演变</h3>
            <p>{{ timeRange || '最近2天真实数据，按自然日聚合' }}</p>
          </div>
        </div>
        <div class="life-header-actions">
          <select v-model="dispatchedFilter" class="life-filter-select" @change="loadData(true)">
            <option :value="null">全部任务</option>
            <option :value="1">已派发</option>
          </select>
          <button type="button" class="life-refresh" :disabled="loading" @click="loadData(true)">刷新</button>
          <button type="button" class="life-modal-close" aria-label="关闭关系演变弹窗" @click="closeModal">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"
              aria-hidden="true">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
      </header>

      <div class="life-modal-body">
        <div class="life-summary" aria-label="关系演变摘要">
          <div><span>最新评分</span><strong>{{ summary.latestScore ?? '—' }}</strong></div>
          <div><span>最高分</span><strong>{{ summary.maxScore ?? '—' }}</strong></div>
          <div><span>最低分</span><strong>{{ summary.minScore ?? '—' }}</strong></div>
          <div><span>创建任务</span><strong>{{ summary.taskCount }}</strong></div>
          <div><span>已派发协作</span><strong>{{ summary.dispatchedCount }}</strong></div>
          <div class="life-agents-cell">
            <span>涉及Agent ({{ summary.agentCount }})</span>
            <div class="life-agent-legend">
              <span v-for="name in summary.agentNames" :key="name" class="life-agent-tag">
                <span class="life-agent-dot" :style="{ background: getAgentColor(name) }"></span>
                {{ name }}
              </span>
            </div>
          </div>
        </div>

        <div class="life-chart-stage">
          <div ref="chartRef" class="life-chart-container"
            :class="{ 'is-hidden': loading || errorMessage || emptyMessage }"></div>
          <div v-if="loading" class="life-state">
            <span class="life-loader" aria-hidden="true"></span>
            正在加载评分与Agent协作记录…
          </div>
          <div v-else-if="errorMessage" class="life-state is-error">
            <strong>数据加载失败</strong>
            <span>{{ errorMessage }}</span>
            <button type="button" @click="loadData(true)">重新加载</button>
          </div>
          <div v-else-if="emptyMessage" class="life-state">
            <strong>暂无可展示的数据</strong>
            <span>{{ emptyMessage }}</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.life-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, .62);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3000;
  animation: life-fade .22s ease
}

.life-modal {
  width: min(1800px, 90vw);
  height: min(900px, 90vh);
  min-height: 600px;
  background: #0d3967;
  border: 1px solid rgba(0, 162, 255, .42);
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 18px 54px rgba(0, 0, 0, .48);
  overflow: hidden;
  animation: life-enter .32s cubic-bezier(.16, 1, .3, 1)
}

.life-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 20px 30px;
  background: linear-gradient(90deg, rgba(0, 100, 255, .14), rgba(0, 150, 255, .04));
  border-bottom: 1px solid rgba(0, 162, 255, .25);
  flex-shrink: 0
}

.life-header-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0
}

.life-header-left>svg {
  color: #4db8ff;
  filter: drop-shadow(0 0 8px rgba(0, 170, 255, .42));
  flex-shrink: 0;
  width: 26px;
  height: 26px
}

.life-header-left h3 {
  margin: 0;
  color: #e8f0fe;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: .4px
}

.life-header-left p {
  margin: 5px 0 0;
  color: rgba(200, 220, 240, .55);
  font-size: 13px
}

.life-header-actions {
  display: flex;
  align-items: center;
  gap: 10px
}

.life-filter-select {
  border: 1px solid rgba(147, 197, 253, .24);
  background: rgba(255, 255, 255, .06);
  color: #bfdbfe;
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 14px;
  cursor: pointer;
  outline: none;
  appearance: auto
}

.life-filter-select:hover,
.life-filter-select:focus {
  background: rgba(96, 165, 250, .16);
  border-color: rgba(147, 197, 253, .55);
  color: #fff
}

.life-filter-select option {
  background: #0d3967;
  color: #bfdbfe
}

.life-refresh,
.life-modal-close {
  border: 1px solid rgba(147, 197, 253, .24);
  background: rgba(255, 255, 255, .06);
  color: #bfdbfe;
  border-radius: 8px;
  cursor: pointer;
  transition: background .2s, border-color .2s, color .2s
}

.life-refresh {
  padding: 8px 18px;
  font-size: 14px
}

.life-refresh:disabled {
  opacity: .45;
  cursor: wait
}

.life-modal-close {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center
}

.life-refresh:not(:disabled):hover,
.life-modal-close:hover {
  background: rgba(96, 165, 250, .16);
  border-color: rgba(147, 197, 253, .55);
  color: #fff
}

.life-refresh:focus-visible,
.life-modal-close:focus-visible,
.life-state button:focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 2px
}

.life-modal-body {
  flex: 1;
  min-height: 0;
  padding: 20px 26px 26px;
  display: flex;
  flex-direction: column;
  gap: 18px
}

.life-summary {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 14px;
  flex-shrink: 0
}

.life-summary>div {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 14px 18px;
  background: rgba(255, 255, 255, .035);
  border: 1px solid rgba(255, 255, 255, .07);
  border-radius: 10px
}

.life-summary span {
  font-size: 14px;
  color: rgba(220, 234, 248, .58)
}

.life-summary strong {
  font-size: 26px;
  color: #f3f8ff;
  font-variant-numeric: tabular-nums
}

.life-agents-cell {
  /* grid-column: 1/-1 */
}

.life-agent-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px
}

.life-agent-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  background: rgba(255, 255, 255, .06);
  border: 1px solid rgba(255, 255, 255, .12);
  border-radius: 20px;
  font-size: 13px;
  color: #dbeafe
}

.life-agent-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex-shrink: 0
}

.life-chart-stage {
  position: relative;
  flex: 1;
  min-height: 0;
  background: rgba(2, 18, 36, .18);
  border-radius: 10px;
  overflow: hidden
}

.life-chart-container {
  width: 100%;
  height: 100%;
  min-height: 460px
}

.life-chart-container.is-hidden {
  visibility: hidden
}

.life-state {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: rgba(220, 234, 248, .62);
  font-size: 15px;
  text-align: center;
  padding: 24px
}

.life-state strong {
  color: #e8f0fe;
  font-size: 18px
}

.life-state.is-error span {
  color: #fca5a5
}

.life-state button {
  margin-top: 8px;
  padding: 9px 18px;
  border: 1px solid rgba(96, 165, 250, .45);
  border-radius: 8px;
  background: rgba(96, 165, 250, .12);
  color: #bfdbfe;
  cursor: pointer;
  font-size: 14px
}

.life-loader {
  width: 34px;
  height: 34px;
  border: 3px solid rgba(96, 165, 250, .18);
  border-top-color: #60a5fa;
  border-radius: 50%;
  animation: life-spin .8s linear infinite
}

@keyframes life-spin {
  to {
    transform: rotate(360deg)
  }
}

@keyframes life-fade {
  from {
    opacity: 0
  }

  to {
    opacity: 1
  }
}

@keyframes life-enter {
  from {
    opacity: 0;
    transform: translateY(12px) scale(.98)
  }

  to {
    opacity: 1;
    transform: none
  }
}

@media(max-width:900px) {
  .life-modal {
    width: 96vw;
    height: 92vh;
    min-height: 0
  }

  .life-modal-header {
    padding: 14px 16px
  }

  .life-header-left h3 {
    font-size: 17px
  }

  .life-summary {
    text-align: center;
    grid-template-columns: repeat(2, 1fr)
  }

  .life-summary>div:nth-child(n+3) {
    grid-column: 1/-1
  }

  .life-chart-container {
    min-height: 380px
  }
}

@media(prefers-reduced-motion:reduce) {

  .life-overlay,
  .life-modal,
  .life-loader {
    animation: none
  }

  .life-refresh,
  .life-modal-close {
    transition: none
  }
}
</style>
