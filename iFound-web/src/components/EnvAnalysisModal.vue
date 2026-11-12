<script setup>
import { ref, computed, onBeforeUnmount, nextTick, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { aiRequest } from '@/utils/request'
import * as echarts from 'echarts'

const emit = defineEmits(['close'])
const userStore = useUserStore()

const loading = ref(false)
const processing = ref(false)
const error = ref(null)
const subareaResults = ref([])
const chartRefs = ref([])

function setChartRef(el, idx) {
  if (el) {
    if (!chartRefs.value) chartRefs.value = []
    chartRefs.value[idx] = el
  }
}
const showToast = ref(false)
const toastMsg = ref('')

// 展开/折叠时重新渲染饼图
watch(subareaResults, () => {
  nextTick(() => {
    subareaResults.value.forEach((subarea, idx) => {
      if (subarea._expanded && !subarea.error) {
        const el = chartRefs.value ? chartRefs.value[idx] : null
        if (el) renderPieChart(el, subarea)
      }
    })
  })
}, { deep: true })

function scoreClass(score) {
  if (score >= 80) return 'env-score-green'
  return 'env-score-yellow'
}

function renderPieChart(el, subarea) {
  if (!el || !subarea) return
  try {
    let chart = echarts.getInstanceByDom(el)
    if (!chart) chart = echarts.init(el)
    const normal = (subarea.totalDevices || 0) - (subarea.abnormalDevices || 0)
    const abnormal = subarea.abnormalDevices || 0
    const hasData = normal > 0 || abnormal > 0
    chart.setOption({
      tooltip: { show: false },
      legend: { show: false },
      series: [{
        type: 'pie',
        radius: ['60%', '80%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: false,
        silent: true,
        animationDuration: 800,
        data: hasData ? [
          { value: normal || 1, name: '正常', itemStyle: { color: '#10b981' } },
          { value: abnormal || 0.1, name: '异常', itemStyle: { color: '#ef4444' } }
        ] : [
          { value: 1, name: '无数据', itemStyle: { color: 'rgba(255,255,255,0.08)' } }
        ],
        label: { show: false },
        labelLine: { show: false }
      }]
    })
  } catch (e) {
    console.error('渲染饼图失败:', e)
  }
}

async function fetchResult() {
  loading.value = true
  processing.value = false
  error.value = null
  subareaResults.value = []
  try {
    const res = await aiRequest.get('/env/analyze/result', {
      params: { org_id: userStore.org_id }
    })
    if (res && (res.code === '200' || res.Status === 'Ok') && res.data) {
      const data = res.data
      if (data.status === 'ready' && data.results) {
        // 有缓存 → 解析结果
        const results = Object.values(data.results)
        results.forEach(r => {
          if (r.result) {
            try {
              const parsed = typeof r.result === 'string' ? JSON.parse(r.result) : r.result
              subareaResults.value.push({
                subareaName: r.subareaName,
                _expanded: true,
                ...parsed
              })
            } catch (e) {
              subareaResults.value.push({
                subareaName: r.subareaName,
                _expanded: true,
                error: '解析结果失败'
              })
            }
          }
        })
        loading.value = false
        // 等DOM更新后渲染饼图
        nextTick(() => {
          subareaResults.value.forEach((_, idx) => {
            const el = chartRefs.value ? chartRefs.value[idx] : null
            if (el) renderPieChart(el, subareaResults.value[idx])
          })
        })
      } else if (data.status === 'processing') {
        processing.value = true
        loading.value = false
        showToast.value = true
        toastMsg.value = 'AI环境分析中，请稍后查看'
        setTimeout(() => { closeModal() }, 3000)
      } else {
        error.value = '未知状态'
        loading.value = false
      }
    } else {
      error.value = '获取环境分析结果失败'
      loading.value = false
    }
  } catch (err) {
    console.error('环境分析失败:', err)
    error.value = '环境分析异常: ' + (err.message || '网络错误')
    loading.value = false
  }
}

fetchResult()

function closeModal() {
  emit('close')
}

onBeforeUnmount(() => {
  // 清理
})
</script>

<template>
  <div class="env-modal-overlay" @click.self="closeModal">
    <div class="env-modal">
      <div class="env-modal-header">
        <div class="env-header-left">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24">
            <path d="M12 2a10 10 0 1 0 10 10H12V2z"/>
            <path d="M12 2a10 10 0 0 1 10 10"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
          <div>
            <h3>环境分析报告</h3>
            <p class="env-modal-subtitle">基于环境标准对全部环境设备进行评分与异常检测</p>
          </div>
        </div>
        <button class="env-modal-close" @click="closeModal">×</button>
      </div>

      <div class="env-modal-body">
        <!-- 分析中 -->
        <div v-if="loading" class="env-loading">
          <div class="env-loading-spinner">
            <div class="spinner-ring"></div>
            <div class="spinner-ring"></div>
            <div class="spinner-ring"></div>
          </div>
          <div class="env-loading-text">
            <h3>获取环境分析结果</h3>
          </div>
        </div>

        <!-- 处理中提示 -->
        <div v-else-if="processing" class="env-processing">
          <div class="env-loading-spinner">
            <div class="spinner-ring"></div>
            <div class="spinner-ring"></div>
            <div class="spinner-ring"></div>
          </div>
          <div class="env-loading-text">
            <h3>AI 环境分析中</h3>
            <p>后台正在分析环境数据，完成后将通过通知提醒您查看</p>
          </div>
          <div class="env-toast" v-if="showToast">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>
            </svg>
            {{ toastMsg }}
          </div>
        </div>

        <!-- 分析结果 -->
        <div v-else class="env-result">
          <!-- 错误提示 -->
          <div v-if="error" class="env-error">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="48" height="48">
              <circle cx="12" cy="12" r="10"/>
              <line x1="15" y1="9" x2="9" y2="15"/>
              <line x1="9" y1="9" x2="15" y2="15"/>
            </svg>
            <h3>分析失败</h3>
            <p>{{ error }}</p>
          </div>

          <!-- 逐区域结果 -->
          <template v-else>
            <div v-for="(subarea, si) in subareaResults" :key="si" class="env-subarea-section">
              <div class="env-subarea-header" @click="subarea._expanded = !subarea._expanded">
                <div class="env-subarea-title">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                    <circle cx="12" cy="10" r="3"/>
                  </svg>
                  <span>{{ subarea.subareaName }}</span>
                </div>
                <div class="env-subarea-meta">
                  <span v-if="subarea.error" class="env-tag env-tag-error">分析失败</span>
                  <template v-else>
                    <span class="env-subarea-score" :class="scoreClass(subarea.totalScore)">{{ subarea.totalScore }}分</span>
                    <span v-if="subarea.abnormalDevices > 0" class="env-tag env-tag-warn">{{ subarea.abnormalDevices }}项异常</span>
                    <span v-else class="env-tag env-tag-ok">正常</span>
                  </template>
                  <svg :class="['env-chevron', { expanded: subarea._expanded }]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                    <polyline points="6 9 12 15 18 9"/>
                  </svg>
                </div>
              </div>

              <div v-if="subarea._expanded && !subarea.error" class="env-subarea-body">
                  <!-- 评分概览 + 整改建议：左右并排 -->
                <div class="env-score-suggestion-row">
                  <!-- 评分概览 -->
                  <div class="env-score-section">
                    <div class="env-score-card">
                      <div class="env-score-value" :class="scoreClass(subarea.totalScore)">{{ subarea.totalScore }}</div>
                      <div class="env-score-label">区域评分</div>
                    </div>
                    <div class="env-score-chart" :ref="el => setChartRef(el, si)"></div>
                    <div class="env-score-meta">
                      <div class="env-meta-item">
                        <span class="env-meta-value">{{ subarea.totalDevices }}</span>
                        <span class="env-meta-label">设备</span>
                      </div>
                      <div class="env-meta-item">
                        <span class="env-meta-value env-abnormal-count">{{ subarea.abnormalDevices }}</span>
                        <span class="env-meta-label">异常</span>
                      </div>
                      <div class="env-meta-item">
                        <span class="env-meta-value">{{ subarea.totalDevices - subarea.abnormalDevices }}</span>
                        <span class="env-meta-label">正常</span>
                      </div>
                    </div>
                  </div>

                  <!-- 区域建议 -->
                  <div v-if="subarea.suggestions && subarea.suggestions.length > 0" class="env-suggestion-section">
                    <h4 class="env-section-title">整改建议</h4>
                    <div class="env-suggestion-list">
                      <div v-for="(s, si) in subarea.suggestions" :key="si" class="env-suggestion-item">
                        <span class="env-suggestion-num">{{ si + 1 }}</span>
                        <span>{{ s }}</span>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 设备检测列表 -->
                <div v-if="subarea.deviceResults && subarea.deviceResults.length > 0" class="env-device-section">
                  <h4 class="env-section-title">设备检测详情</h4>
                  <div class="env-device-grid">
                    <div v-for="(device, di) in subarea.deviceResults" :key="di" class="env-device-card" :class="device.status === '异常' ? 'env-device-abnormal' : 'env-device-normal'">
                      <div class="env-device-header">
                        <div class="env-device-info">
                          <span class="env-device-name">{{ device.deviceName }}</span>
                          <span class="env-device-location">{{ device.location }}</span>
                        </div>
                        <span class="env-device-status" :class="device.status === '异常' ? 'env-status-bad' : 'env-status-good'">{{ device.status }}</span>
                      </div>
                      <div v-if="device.violations && device.violations.length > 0" class="env-violations">
                        <div v-for="(v, vi) in device.violations" :key="vi" class="env-violation-item">
                          <div class="env-violation-header">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14" class="env-violation-icon">
                              <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
                            </svg>
                            <span class="env-violation-indicator">{{ v.indicator }}</span>
                          </div>
                          <div class="env-violation-detail">
                            <span class="env-v-detail-label">当前值</span>
                            <span class="env-v-detail-value env-v-warn">{{ v.value }}{{ v.unit || '' }}</span>
                            <span class="env-v-detail-label">标准值</span>
                            <span class="env-v-detail-value">{{ v.standard }}</span>
                          </div>
                          <div class="env-violation-source" v-if="v.source">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
                              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                              <polyline points="14 2 14 8 20 8"/>
                            </svg>
                            {{ v.source }}
                          </div>
                          <div class="env-violation-suggestion" v-if="v.suggestion">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
                              <circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>
                            </svg>
                            {{ v.suggestion }}
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 无数据 -->
            <div v-if="subareaResults.length === 0" class="env-empty">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="48" height="48">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
              <p>未获取到环境分析数据</p>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.env-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: overlay-fade-in 0.25s ease;
}

.env-modal {
  background: rgb(0, 60, 126);
  border: 1px solid rgba(0, 180, 255, 0.2);
  border-radius: 12px;
  width: 90vw;
  max-width: 90vw;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  box-shadow:
    0 8px 40px rgba(0, 0, 0, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.05);
  animation: modal-slide-up 0.3s ease;
  overflow: hidden;
  position: relative;
}

.env-modal::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 2px;
  background: linear-gradient(90deg,
    transparent,
    rgba(0, 180, 255, 0.6),
    rgba(96, 165, 250, 0.4),
    rgba(0, 180, 255, 0.6),
    transparent
  );
  z-index: 10;
}

@keyframes modal-slide-up {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.env-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.15), transparent);
  flex-shrink: 0;
}

.env-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.env-header-left svg {
  color: #60a5fa;
  filter: drop-shadow(0 0 8px rgba(96, 165, 250, 0.4));
}

.env-header-left h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #e8f0fe;
}

.env-modal-subtitle {
  margin: 2px 0 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

.env-modal-close {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.4);
  font-size: 24px;
  cursor: pointer;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  flex-shrink: 0;
}

.env-modal-close:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

.env-modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px 20px;
}

.env-modal-body::-webkit-scrollbar { width: 6px; }
.env-modal-body::-webkit-scrollbar-track { background: transparent; }
.env-modal-body::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 3px;
}

/* 加载动画 */
.env-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 0;
}

.env-loading-spinner {
  position: relative;
  width: 80px;
  height: 80px;
  margin-bottom: 24px;
}

.env-loading-spinner .spinner-ring {
  position: absolute;
  inset: 0;
  border: 2px solid transparent;
  border-top-color: #60a5fa;
  border-radius: 50%;
  animation: envSpin 1.2s cubic-bezier(0.5, 0, 0.5, 1) infinite;
}

.env-loading-spinner .spinner-ring:nth-child(1) { animation-duration: 1.2s; }
.env-loading-spinner .spinner-ring:nth-child(2) {
  inset: 8px;
  border-top-color: #34d399;
  animation-duration: 0.9s;
  animation-direction: reverse;
}
.env-loading-spinner .spinner-ring:nth-child(3) {
  inset: 16px;
  border-top-color: #f472b6;
  animation-duration: 0.6s;
}

@keyframes envSpin {
  to { transform: rotate(360deg); }
}

.env-loading-text {
  text-align: center;
  margin-bottom: 20px;
}

.env-loading-text h3 {
  font-size: 20px;
  font-weight: 700;
  color: #e8f0fe;
  margin: 0 0 8px;
}

.env-loading-text p {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  margin: 0;
}

.env-loading-text strong {
  color: #93c5fd;
}

.env-loading-bar {
  width: 240px;
  height: 4px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 2px;
  overflow: hidden;
  margin-top: 16px;
}

.env-loading-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #60a5fa, #34d399);
  border-radius: 2px;
  transition: width 0.5s ease;
}

/* 错误提示 */
.env-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 0;
  color: #ef4444;
}

.env-error h3 {
  margin: 16px 0 8px;
  font-size: 18px;
}

.env-error p {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  text-align: center;
}

/* 处理中状态 */
.env-processing {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 0;
}

.env-toast {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 10px 18px;
  background: rgba(96, 165, 250, 0.1);
  border: 1px solid rgba(96, 165, 250, 0.2);
  border-radius: 8px;
  font-size: 13px;
  color: #93c5fd;
  animation: envToastIn 0.4s ease;
}

@keyframes envToastIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 评分概览 */
.env-score-section {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 16px 20px;
  background: rgba(59, 130, 246, 0.04);
  border: 1px solid rgba(59, 130, 246, 0.12);
  border-radius: 12px;
  margin-bottom: 16px;
}

.env-score-card {
  text-align: center;
  flex-shrink: 0;
}

.env-score-value {
  font-size: 42px;
  font-weight: 900;
  line-height: 1;
  margin-bottom: 4px;
}

.env-score-green { color: #10b981; text-shadow: 0 0 20px rgba(16, 185, 129, 0.3); }
.env-score-yellow { color: #f59e0b; text-shadow: 0 0 20px rgba(245, 158, 11, 0.3); }
.env-score-red { color: #f59e0b; text-shadow: 0 0 20px rgba(245, 158, 11, 0.3); }

.env-score-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  font-weight: 700;
}

.env-score-meta {
  display: flex;
  gap: 28px;
}

.env-meta-item {
  text-align: center;
}

.env-meta-value {
  display: block;
  font-size: 24px;
  font-weight: 800;
  color: #e8f0fe;
}

.env-abnormal-count { color: #ef4444; }

.env-meta-label {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.35);
  font-weight: 700;
}

/* 子区域折叠卡片 */
.env-subarea-section {
  margin-bottom: 10px;
  background: rgba(255, 255, 255, 0.025);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 10px;
  overflow: hidden;
  transition: all 0.2s;
  animation: subarea-slide-in 0.4s ease;
}

@keyframes subarea-slide-in {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.env-subarea-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: transparent;
  cursor: pointer;
  transition: background 0.15s;
  user-select: none;
}

.env-subarea-header:hover {
  background: rgba(255, 255, 255, 0.035);
}

.env-subarea-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #e8f0fe;
}

.env-subarea-title svg {
  color: #60a5fa;
  flex-shrink: 0;
}

.env-subarea-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.env-subarea-score {
  font-size: 14px;
  font-weight: 800;
}

.env-tag {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
}

.env-tag-ok {
  background: rgba(16, 185, 129, 0.12);
  color: #10b981;
}

.env-tag-warn {
  background: rgba(239, 68, 68, 0.12);
  color: #ef4444;
}

.env-tag-error {
  background: rgba(239, 68, 68, 0.15);
  color: #fca5a5;
}

.env-chevron {
  color: rgba(255, 255, 255, 0.3);
  transition: transform 0.25s ease;
  flex-shrink: 0;
}

.env-chevron.expanded {
  transform: rotate(180deg);
}

.env-subarea-body {
  padding: 0 16px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.04);
}

.env-score-suggestion-row {
  display: flex;
  gap: 16px;
  margin-top: 14px;
  align-items: stretch;
}

.env-score-suggestion-row .env-score-section {
  flex: 0 0 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  margin-bottom: 0;
  margin-top: 0;
  padding: 14px 16px;
}

.env-score-suggestion-row .env-score-card {
  text-align: center;
}

.env-score-suggestion-row .env-score-value {
  font-size: 36px;
}

.env-score-suggestion-row .env-score-meta {
  gap: 16px;
}

.env-score-suggestion-row .env-meta-value {
  font-size: 18px;
}

.env-score-chart {
  width: 72px;
  height: 72px;
}

.env-score-suggestion-row .env-suggestion-section {
  flex: 1;
  min-width: 0;
  margin-top: 0;
}

.env-score-suggestion-row .env-section-title {
  margin-top: 0;
}

/* 设备检测列表 */
.env-section-title {
  font-size: 13px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.6);
  margin: 0 0 10px;
}

.env-device-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.env-device-card {
  background: rgba(255, 255, 255, 0.025);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 10px;
  padding: 12px 14px;
  transition: all 0.2s;
}

.env-device-card:hover {
  background: rgba(255, 255, 255, 0.04);
}

.env-device-abnormal {
  border-left: 3px solid #ef4444;
}

.env-device-normal {
  border-left: 3px solid #10b981;
}

.env-device-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.env-device-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.env-device-name {
  font-size: 13px;
  font-weight: 700;
  color: #e8f0fe;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.env-device-location {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.35);
  font-weight: 600;
  white-space: nowrap;
}

.env-device-status {
  font-size: 11px;
  font-weight: 800;
  padding: 2px 8px;
  border-radius: 20px;
  flex-shrink: 0;
}

.env-status-good {
  background: rgba(16, 185, 129, 0.12);
  color: #10b981;
}

.env-status-bad {
  background: rgba(239, 68, 68, 0.12);
  color: #ef4444;
}

/* 违规详情 */
.env-violations {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.env-violation-item {
  background: rgba(239, 68, 68, 0.04);
  border: 1px solid rgba(239, 68, 68, 0.1);
  border-radius: 8px;
  padding: 8px 10px;
}

.env-violation-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.env-violation-icon {
  color: #ef4444;
  flex-shrink: 0;
}

.env-violation-indicator {
  font-size: 12px;
  font-weight: 700;
  color: #fca5a5;
}

.env-violation-detail {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  font-size: 12px;
  flex-wrap: wrap;
}

.env-v-detail-label {
  color: rgba(255, 255, 255, 0.35);
  font-weight: 600;
}

.env-v-detail-value {
  color: #e8f0fe;
  font-weight: 700;
}

.env-v-warn {
  color: #fbbf24;
}

.env-violation-source {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.3);
  margin-bottom: 2px;
}

.env-violation-suggestion {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  font-size: 11px;
  color: #93c5fd;
  line-height: 1.4;
}

.env-violation-suggestion svg {
  margin-top: 2px;
  flex-shrink: 0;
}

/* 整体建议 */
.env-suggestion-section {
  margin-top: 4px;
}

.env-suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.env-suggestion-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.65);
  line-height: 1.5;
  padding: 8px 12px;
  background: rgba(59, 130, 246, 0.05);
  border: 1px solid rgba(59, 130, 246, 0.1);
  border-radius: 8px;
}

.env-suggestion-num {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(59, 130, 246, 0.2);
  color: #60a5fa;
  font-size: 10px;
  font-weight: 800;
  border-radius: 50%;
}

/* 空状态 */
.env-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 0;
  color: #10b981;
}

.env-empty p {
  margin-top: 12px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.5);
}

@keyframes overlay-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
