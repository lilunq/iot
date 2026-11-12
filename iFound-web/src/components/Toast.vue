<template>
  <Transition name="toast-slide">
    <div v-if="visible" class="toast-container" :class="type">
      <div class="toast-icon">
        <!-- 成功 -->
        <svg v-if="type === 'success'" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.5">
          <path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
          <polyline points="22 4 12 14.01 9 11.01"/>
        </svg>
        <!-- 失败 -->
        <svg v-else-if="type === 'error'" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.5">
          <circle cx="12" cy="12" r="10"/>
          <line x1="15" y1="9" x2="9" y2="15"/>
          <line x1="9" y1="9" x2="15" y2="15"/>
        </svg>
        <!-- 提示 -->
        <svg v-else viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.5">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
      </div>
      <div class="toast-body">
        <div class="toast-title">{{ title }}</div>
        <div v-if="description" class="toast-desc">{{ description }}</div>
      </div>
      <button class="toast-close" @click="close">&times;</button>
      <div class="toast-progress" :style="{ animationDuration: duration + 'ms' }"></div>
    </div>
  </Transition>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'

const props = defineProps({
  type: { type: String, default: 'info' },      // success | error | info
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  duration: { type: Number, default: 3000 }
})

const emit = defineEmits(['close'])

const visible = ref(true)
let timer = null

function close() {
  visible.value = false
  clearTimeout(timer)
  emit('close')
}

function startTimer() {
  clearTimeout(timer)
  timer = setTimeout(close, props.duration)
}

watch(() => props.title, () => {
  visible.value = true
  startTimer()
}, { immediate: true })

onBeforeUnmount(() => clearTimeout(timer))
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 99999;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 320px;
  max-width: 460px;
  padding: 14px 40px 14px 16px;
  border-radius: 10px;
  background: rgba(15, 25, 45, 0.95);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  overflow: hidden;
}

/* 类型配色 */
.toast-container.success {
  border-color: rgba(16, 185, 129, 0.3);
}
.toast-container.success .toast-icon {
  color: #10b981;
}
.toast-container.success .toast-progress {
  background: linear-gradient(90deg, #10b981, #059669);
}

.toast-container.error {
  border-color: rgba(239, 68, 68, 0.3);
}
.toast-container.error .toast-icon {
  color: #ef4444;
}
.toast-container.error .toast-progress {
  background: linear-gradient(90deg, #ef4444, #dc2626);
}

.toast-container.info {
  border-color: rgba(59, 130, 246, 0.3);
}
.toast-container.info .toast-icon {
  color: #3b82f6;
}
.toast-container.info .toast-progress {
  background: linear-gradient(90deg, #3b82f6, #2563eb);
}

.toast-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  margin-top: 1px;
}

.toast-body {
  flex: 1;
  min-width: 0;
}

.toast-title {
  font-size: 14px;
  font-weight: 600;
  color: #f0f4f8;
  line-height: 1.4;
}

.toast-desc {
  font-size: 12px;
  color: #8899aa;
  margin-top: 4px;
  line-height: 1.5;
  word-break: break-all;
}

.toast-close {
  position: absolute;
  top: 8px;
  right: 10px;
  background: none;
  border: none;
  color: #556677;
  font-size: 18px;
  cursor: pointer;
  line-height: 1;
  padding: 2px 4px;
  transition: color 0.2s;
}
.toast-close:hover {
  color: #aabbcc;
}

.toast-progress {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 3px;
  width: 100%;
  animation: toast-shrink linear forwards;
  transform-origin: left;
}

@keyframes toast-shrink {
  from { transform: scaleX(1); }
  to   { transform: scaleX(0); }
}

/* 过渡动画 */
.toast-slide-enter-active {
  transition: all 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}
.toast-slide-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 1, 1);
}
.toast-slide-enter-from {
  opacity: 0;
  transform: translateX(-50%) translateY(-30px);
}
.toast-slide-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-20px);
}
</style>
