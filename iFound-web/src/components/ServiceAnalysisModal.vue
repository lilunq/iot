<script setup>
import { nextTick, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts'

const emit = defineEmits(['close'])

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

onUnmounted(() => {
  if (saTrendChart) {
    saTrendChart.dispose()
    saTrendChart = null
  }
})

function closeModal() {
  emit('close')
}
</script>

<template>
  <div class="service-analysis-overlay" @click.self="closeModal">
    <div class="service-analysis-modal">
      <!-- 弹窗头部 -->
      <div class="sa-modal-header">
        <div class="sa-header-left">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="28" height="28">
            <path d="M12 2L2 7l10 5 10-5-10-5z"/>
            <path d="M2 17l10 5 10-5"/>
            <path d="M2 12l10 5 10-5"/>
          </svg>
          <h3>服务分析详情</h3>
        </div>
        <div class="sa-header-right">
          <span class="sa-time">2025-05-27 10:30:45</span>
          <button class="sa-modal-close" @click="closeModal" aria-label="关闭">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
      </div>

      <!-- 弹窗主体 -->
      <div class="sa-modal-body">
        <!-- 顶部5个指标卡片 -->
        <div class="sa-top-metrics">
          <div class="sa-metric-card">
            <div class="sa-metric-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </div>
            <div class="sa-metric-content">
              <div class="sa-metric-label">客流量(到/发)</div>
              <div class="sa-metric-values">
                <div class="sa-metric-value arrive">12,458 <span class="sa-metric-unit">到站</span></div>
                <div class="sa-metric-value depart">11,832 <span class="sa-metric-unit">发站</span></div>
              </div>
            </div>
          </div>

          <div class="sa-metric-card">
            <div class="sa-metric-icon green">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                <polyline points="9 12 11 14 15 10"/>
              </svg>
            </div>
            <div class="sa-metric-content">
              <div class="sa-metric-label">安全指数</div>
              <div class="sa-metric-score">
                <span class="sa-score-value green">83分</span>
                <span class="sa-score-badge green">安全</span>
              </div>
            </div>
          </div>

          <div class="sa-metric-card">
            <div class="sa-metric-icon blue">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
              </svg>
            </div>
            <div class="sa-metric-content">
              <div class="sa-metric-label">服务等级</div>
              <div class="sa-grade">
                <span class="sa-grade-value blue">优</span>
                <span class="sa-grade-desc">综合评价</span>
              </div>
            </div>
          </div>

          <div class="sa-metric-card">
            <div class="sa-metric-icon orange">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              </svg>
            </div>
            <div class="sa-metric-content">
              <div class="sa-metric-label">今日告警</div>
              <div class="sa-alert-count">
                <span class="sa-alert-value orange">2</span>
                <span class="sa-alert-desc">未处理告警</span>
              </div>
            </div>
          </div>

          <div class="sa-metric-card">
            <div class="sa-metric-icon cyan">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>
            </div>
            <div class="sa-metric-content">
              <div class="sa-metric-label">高峰时段</div>
              <div class="sa-peak-time">
                <span class="sa-peak-value cyan">08:00-10:00</span>
                <span class="sa-peak-desc">客流高峰时段</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 中间区域：雷达图 + 客流趋势 + AI分析 -->
        <div class="sa-middle-section">
          <!-- 左侧：服务质量雷达图 -->
          <div class="sa-radar-panel">
            <div class="sa-panel-title">服务质量雷达图</div>
            <div class="sa-radar-container">
              <svg viewBox="0 0 200 200" class="sa-radar-svg">
                <!-- 五边形网格 -->
                <polygon points="100,20 170,65 145,155 55,155 30,65" fill="rgba(0,150,255,0.05)" stroke="rgba(0,150,255,0.2)" stroke-width="1"/>
                <polygon points="100,40 150,72 132,140 68,140 50,72" fill="rgba(0,150,255,0.05)" stroke="rgba(0,150,255,0.15)" stroke-width="1"/>
                <polygon points="100,60 130,80 120,125 80,125 70,80" fill="rgba(0,150,255,0.05)" stroke="rgba(0,150,255,0.1)" stroke-width="1"/>
                <!-- 轴线 -->
                <line x1="100" y1="100" x2="100" y2="20" stroke="rgba(0,150,255,0.2)" stroke-width="1"/>
                <line x1="100" y1="100" x2="170" y2="65" stroke="rgba(0,150,255,0.2)" stroke-width="1"/>
                <line x1="100" y1="100" x2="145" y2="155" stroke="rgba(0,150,255,0.2)" stroke-width="1"/>
                <line x1="100" y1="100" x2="55" y2="155" stroke="rgba(0,150,255,0.2)" stroke-width="1"/>
                <line x1="100" y1="100" x2="30" y2="65" stroke="rgba(0,150,255,0.2)" stroke-width="1"/>
                <!-- 数据区域 -->
                <polygon points="100,30 160,68 138,145 62,145 38,68" fill="rgba(0,120,255,0.25)" stroke="#0088ff" stroke-width="2"/>
                <!-- 数据点 -->
                <circle cx="100" cy="30" r="4" fill="#00aaff"/>
                <circle cx="160" cy="68" r="4" fill="#00aaff"/>
                <circle cx="138" cy="145" r="4" fill="#00aaff"/>
                <circle cx="62" cy="145" r="4" fill="#00aaff"/>
                <circle cx="38" cy="68" r="4" fill="#00aaff"/>
                <!-- 标签 -->
                <text x="100" y="14" text-anchor="middle" fill="white" font-size="11">温度</text>
                <text x="178" y="65" text-anchor="start" fill="white" font-size="11">湿度</text>
                <text x="152" y="168" text-anchor="middle" fill="white" font-size="11">照度</text>
                <text x="48" y="168" text-anchor="middle" fill="white" font-size="11">气味</text>
                <text x="22" y="65" text-anchor="end" fill="white" font-size="11">噪声</text>
                <!-- 中心分数 -->
                <text x="100" y="106" text-anchor="middle" fill="#00ff88" font-size="24" font-weight="900">90</text>
              </svg>
            </div>
          </div>

          <!-- 中间：当日客流趋势 -->
          <div class="sa-trend-panel">
            <div class="sa-panel-title">当日客流趋势（人次）</div>
            <div class="sa-trend-container" ref="saTrendChartRef"></div>
            <!-- 环境指标状态 -->
            <div class="sa-env-status">
              <div class="sa-env-title">环境指标状态</div>
              <div class="sa-env-list">
                <div class="sa-env-item">
                  <span class="sa-env-icon">🌡️</span>
                  <span class="sa-env-label">温度</span>
                  <span class="sa-env-value">24.8°C</span>
                  <span class="sa-env-status ok">正常</span>
                </div>
                <div class="sa-env-item">
                  <span class="sa-env-icon">💧</span>
                  <span class="sa-env-label">湿度</span>
                  <span class="sa-env-value">48%</span>
                  <span class="sa-env-status ok">正常</span>
                </div>
                <div class="sa-env-item">
                  <span class="sa-env-icon">☁️</span>
                  <span class="sa-env-label">CO₂</span>
                  <span class="sa-env-value">612 ppm</span>
                  <span class="sa-env-status ok">正常</span>
                </div>
                <div class="sa-env-item">
                  <span class="sa-env-icon">🔊</span>
                  <span class="sa-env-label">噪声</span>
                  <span class="sa-env-value">52 dB</span>
                  <span class="sa-env-status ok">正常</span>
                </div>
                <div class="sa-env-item">
                  <span class="sa-env-icon">☀️</span>
                  <span class="sa-env-label">照度</span>
                  <span class="sa-env-value">420 lux</span>
                  <span class="sa-env-status ok">正常</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 右侧：AI分析结论 -->
          <div class="sa-ai-panel">
            <div class="sa-ai-header">
              <div class="sa-ai-badge">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                  <path d="M12 2a4 4 0 0 1 4 4v2a4 4 0 0 1-8 0V6a4 4 0 0 1 4-4z"/>
                  <path d="M16 14H8a4 4 0 0 0-4 4v2h16v-2a4 4 0 0 0-4-4z"/>
                </svg>
                AI分析结论
              </div>
            </div>
            <div class="sa-ai-content">
              <p class="sa-ai-text">当前站房客流平稳，安全指数<span class="sa-highlight">94分</span>处于安全区间，环境整体达标，服务保障态势良好。</p>
            </div>
            <div class="sa-ai-section">
              <div class="sa-ai-section-title">影响因素</div>
              <ul class="sa-ai-list">
                <li>客流平稳有序，未出现拥堵</li>
                <li>环境指标均在舒适区间</li>
                <li>设备运行稳定，未见异常告警</li>
              </ul>
            </div>
            <div class="sa-ai-section">
              <div class="sa-ai-section-title">建议措施</div>
              <ul class="sa-ai-list">
                <li>动态关注早晚高峰客流变化，提前做好疏导准备</li>
                <li>结合外部天气变化，优化温湿度自动调节策略</li>
                <li>持续开展环境巡检，确保设备健康运行</li>
                <li>优化服务广播策略，提升旅客信息获取体验</li>
              </ul>
            </div>
            <div class="sa-ai-actions">
              <button class="sa-btn primary">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                  <polyline points="22 4 12 14.01 9 11.01"/>
                </svg>
                一键优化
              </button>
              <button class="sa-btn secondary">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="16" y1="13" x2="8" y2="13"/>
                  <line x1="16" y1="17" x2="8" y2="17"/>
                </svg>
                生成工单
              </button>
            </div>
          </div>
        </div>

        <!-- 底部：近期服务事件 -->
        <div class="sa-bottom-section">
          <div class="sa-panel-title">近期服务事件</div>
          <div class="sa-event-table">
            <div class="sa-table-header">
              <div class="sa-th time">时间</div>
              <div class="sa-th event">事件</div>
              <div class="sa-th level">级别</div>
              <div class="sa-th status">状态</div>
              <div class="sa-th detail">处置情况</div>
            </div>
            <div class="sa-table-body">
              <div class="sa-tr">
                <div class="sa-td time">05-27 09:35</div>
                <div class="sa-td event">客流高峰监测（08:00-10:00）</div>
                <div class="sa-td level"><span class="sa-level-badge low">低</span></div>
                <div class="sa-td status"><span class="sa-status-badge done">已完成</span></div>
                <div class="sa-td detail">客流高峰平稳，未见异常</div>
              </div>
              <div class="sa-tr">
                <div class="sa-td time">05-27 08:20</div>
                <div class="sa-td event">环境巡检完成</div>
                <div class="sa-td level"><span class="sa-level-badge low">低</span></div>
                <div class="sa-td status"><span class="sa-status-badge done">已完成</span></div>
                <div class="sa-td detail">各项环境指标正常</div>
              </div>
              <div class="sa-tr">
                <div class="sa-td time">05-27 07:50</div>
                <div class="sa-td event">服务广播策略优化</div>
                <div class="sa-td level"><span class="sa-level-badge low">低</span></div>
                <div class="sa-td status"><span class="sa-status-badge done">已完成</span></div>
                <div class="sa-td detail">优化完成，旅客反馈良好</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
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
</style>
