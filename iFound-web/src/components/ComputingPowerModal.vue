<script setup>
/**
 * 算力实时指标 - 详细弹窗
 * 展示所有算力指标数据（除 npu_smi_raw 原始文本外），每张 NPU 卡用独立卡片呈现
 */
const props = defineProps({
  data: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['close'])

function closeModal() {
  emit('close')
}

/** 根据 npu_id 获取该卡对应的独立温度字段 */
function cardTemp(npuId) {
  const map = { 1: props.data.temperature1, 3: props.data.temperature3, 5: props.data.temperature5, 7: props.data.temperature7 }
  return map[npuId]
}

/** 根据 npu_id 获取该卡对应的独立功耗字段 */
function cardPower(npuId) {
  const map = { 1: props.data.power1, 3: props.data.power3, 5: props.data.power5, 7: props.data.power7 }
  return map[npuId]
}
</script>

<template>
  <div class="cpm-overlay" @click.self="closeModal">
    <div class="cpm-modal">
      <!-- 头部 -->
      <div class="cpm-header">
        <div class="cpm-header-left">
          <svg class="cpm-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
            <line x1="8" y1="21" x2="16" y2="21"/>
            <line x1="12" y1="17" x2="12" y2="21"/>
          </svg>
          <span>算力实时指标 - 详细数据</span>
        </div>
        <button class="cpm-close-btn" type="button" aria-label="关闭" @click="closeModal">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
            <path d="M18 6L6 18M6 6l12 12"/>
          </svg>
        </button>
      </div>

      <!-- 内容区域：无滚动条，全部可见 -->
      <div class="cpm-body">
        <!-- 第一行：整体概览指标 -->
        <div class="cpm-section">
          <div class="cpm-section-title">整体概览</div>
          <div class="cpm-overview-grid">
            <!-- 显存 -->
            <div class="cpm-ov-card">
              <div class="cpm-ov-label">总显存</div>
              <div class="cpm-ov-value">{{ Number(data.hbmTotal).toLocaleString() }}<small> GB</small></div>
            </div>
            <div class="cpm-ov-card">
              <div class="cpm-ov-label">已用显存</div>
              <div class="cpm-ov-value">{{ Number(data.hbmUsed).toLocaleString() }}<small> GB</small></div>
            </div>
            <div class="cpm-ov-card">
              <div class="cpm-ov-label">显存使用率</div>
              <div class="cpm-ov-value">{{ data.hbmUsage }}<small>%</small></div>
            </div>

            <!-- GPU利用率 & 请求 -->
            <div class="cpm-ov-card">
              <div class="cpm-ov-label">NPU 核心使用率</div>
              <div class="cpm-ov-value">{{ data.gpuUtilizationRate }}<small>%</small></div>
            </div>
            <div class="cpm-ov-card">
              <div class="cpm-ov-label">正在处理</div>
              <div class="cpm-ov-value">{{ data.processingRequest }}<small>个</small></div>
            </div>
            <div class="cpm-ov-card">
              <div class="cpm-ov-label">排队请求</div>
              <div class="cpm-ov-value">{{ data.queueRequest }}<small>个</small></div>
            </div>
            <div class="cpm-ov-card">
              <div class="cpm-ov-label">吞吐量</div>
              <div class="cpm-ov-value">{{ Number(data.throughput).toLocaleString() }}<small> token/s</small></div>
            </div>
            <div class="cpm-ov-card">
              <div class="cpm-ov-label">推理耗时</div>
              <div class="cpm-ov-value">{{ data.reasoningTime }}<small> ms</small></div>
            </div>
            <div class="cpm-ov-card">
              <div class="cpm-ov-label">成功请求数</div>
              <div class="cpm-ov-value">{{ data.successRequest }}<small>个</small></div>
            </div>

            <!-- 温度 -->
            <div class="cpm-ov-card">
              <div class="cpm-ov-label">平均温度</div>
              <div class="cpm-ov-value cpm-hot">{{ data.avgTemperature }}<small>°C</small></div>
            </div>
            <div class="cpm-ov-card">
              <div class="cpm-ov-label">最高温度</div>
              <div class="cpm-ov-value cpm-hot">{{ data.maxTemperature }}<small>°C</small></div>
            </div>

            <!-- 功耗 -->
            <div class="cpm-ov-card">
              <div class="cpm-ov-label">平均功耗</div>
              <div class="cpm-ov-value">{{ data.avgPower }}<small> W</small></div>
            </div>
            <div class="cpm-ov-card">
              <div class="cpm-ov-label">最大功耗</div>
              <div class="cpm-ov-value">{{ data.maxPower }}<small> W</small></div>
            </div>
          </div>
        </div>

        <!-- 第二行：每张卡详细信息 -->
        <div class="cpm-section">
          <div class="cpm-section-title">NPU 卡详情</div>
          <div class="cpm-cards-row">
            <div
              v-for="card in (data.cards || [])"
              :key="card.npu_id"
              class="cpm-npu-card"
            >
              <div class="cpm-npu-head">
                <svg class="cpm-npu-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="4" y="4" width="16" height="16" rx="2"/>
                  <path d="M12 8v8M8 12h8"/>
                </svg>
                <span class="cpm-npu-name">{{ card.name }}</span>
                <span class="cpm-npu-id">#{{ card.npu_id }}</span>
                <span :class="['cpm-npu-health', card.health === 'OK' ? 'cpm-health-ok' : 'cpm-health-err']">
                  {{ card.health }}
                </span>
              </div>
              <div class="cpm-npu-body">
                <div class="cpm-npu-row">
                  <span class="cpm-npu-label">温度</span>
                  <span class="cpm-npu-val cpm-hot">{{ card.temp_c }} °C</span>
                </div>
                <div class="cpm-npu-row">
                  <span class="cpm-npu-label">独立测温</span>
                  <span class="cpm-npu-val cpm-hot">{{ cardTemp(card.npu_id) }} °C</span>
                </div>
                <div class="cpm-npu-row">
                  <span class="cpm-npu-label">功耗</span>
                  <span class="cpm-npu-val">{{ card.power_w }} W</span>
                </div>
                <div class="cpm-npu-row">
                  <span class="cpm-npu-label">独立功耗</span>
                  <span class="cpm-npu-val">{{ cardPower(card.npu_id) }} W</span>
                </div>
                <div class="cpm-npu-row">
                  <span class="cpm-npu-label">AI Core 使用率</span>
                  <span class="cpm-npu-val">{{ card.aicore_usage_percent }}%</span>
                </div>
                <div class="cpm-npu-row">
                  <span class="cpm-npu-label">HBM 已用</span>
                  <span class="cpm-npu-val">{{ Number(card.hbm_used_mb).toLocaleString() }} MB</span>
                </div>
                <div class="cpm-npu-row">
                  <span class="cpm-npu-label">HBM 总量</span>
                  <span class="cpm-npu-val">{{ Number(card.hbm_total_mb).toLocaleString() }} MB</span>
                </div>
                <div class="cpm-npu-row">
                  <span class="cpm-npu-label">HBM 使用率</span>
                  <span class="cpm-npu-val">{{ ((card.hbm_used_mb / card.hbm_total_mb) * 100).toFixed(1) }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped>
/* ── 遮罩 ── */
.cpm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: cpm-fade-in 0.25s ease;
}

/* ── 弹窗容器 ── */
.cpm-modal {
  background: rgb(0, 66, 126);
  border: 1px solid rgba(0, 180, 255, 0.15);
  border-radius: 16px;
  width: 90vw;
  max-width: 90vw;
  max-height: 92vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.6), 0 0 1px rgba(0, 180, 255, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.05);
  animation: cpm-scale-in 0.35s cubic-bezier(0.16, 1, 0.3, 1);
  overflow: hidden;
  position: relative;
  padding-bottom: 40px;
}

/* ── 头部 ── */
.cpm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px 0;
  flex-shrink: 0;
}
.cpm-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 800;
  color: #fff;
}
.cpm-ico {
  width: 22px;
  height: 22px;
  color: var(--blue, #00d8ff);
}
.cpm-close-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}
.cpm-close-btn:hover {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
}

/* ── 内容（无滚动条） ── */
.cpm-body {
  flex: 1;
  padding: 14px 24px 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ── 区块标题 ── */
.cpm-section-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--blue, #00d8ff);
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 8px;
}

/* ── 整体概览网格 ── */
.cpm-overview-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
}
.cpm-ov-card {
  background: rgba(0, 20, 60, 0.5);
  border: 1px solid rgba(0, 180, 255, 0.1);
  border-radius: 10px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.cpm-ov-label {
  font-size: 11px;
  color: var(--text-label, #a2b9d6);
  font-weight: 600;
  white-space: nowrap;
}
.cpm-ov-value {
  font-size: 17px;
  font-weight: 800;
  color: #fff;
  line-height: 1.2;
}
.cpm-ov-value small {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-tertiary, #90caf9);
  margin-left: 2px;
}
.cpm-hot {
  color: #ff9933;
}

/* ── NPU 卡行 ── */
.cpm-cards-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.cpm-npu-card {
  background: rgba(0, 20, 60, 0.5);
  border: 1px solid rgba(0, 180, 255, 0.12);
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.cpm-npu-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  background: rgba(0, 40, 100, 0.4);
  border-bottom: 1px solid rgba(0, 180, 255, 0.08);
  font-size: 13px;
  font-weight: 700;
  color: #fff;
}
.cpm-npu-ico {
  width: 16px;
  height: 16px;
  color: var(--blue, #00d8ff);
  flex-shrink: 0;
}
.cpm-npu-name {
  font-weight: 800;
}
.cpm-npu-id {
  color: var(--text-tertiary, #90caf9);
  font-weight: 600;
}
.cpm-npu-health {
  margin-left: auto;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
  line-height: 1.4;
}
.cpm-health-ok {
  background: rgba(0, 255, 200, 0.12);
  color: #00ffcc;
}
.cpm-health-err {
  background: rgba(255, 50, 50, 0.15);
  color: #ff4444;
}

.cpm-npu-body {
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.cpm-npu-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}
.cpm-npu-label {
  color: var(--text-label, #a2b9d6);
  font-weight: 600;
}
.cpm-npu-val {
  color: #fff;
  font-weight: 700;
}

/* ── 底部 ── */
.cpm-footer {
  display: flex;
  justify-content: center;
  padding: 12px 24px 16px;
  flex-shrink: 0;
}
.cpm-footer-btn {
  background: rgba(0, 180, 255, 0.12);
  border: 1px solid rgba(0, 180, 255, 0.2);
  border-radius: 8px;
  padding: 8px 28px;
  font-size: 14px;
  font-weight: 700;
  color: var(--blue, #00d8ff);
  cursor: pointer;
  transition: all 0.2s;
}
.cpm-footer-btn:hover {
  background: rgba(0, 180, 255, 0.2);
}

/* ── 动画 ── */
@keyframes cpm-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes cpm-scale-in {
  from { opacity: 0; transform: scale(0.92); }
  to { opacity: 1; transform: scale(1); }
}
</style>
