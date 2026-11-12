<script setup>
import { onMounted, ref, onUnmounted } from 'vue'
import * as echarts from 'echarts'

const emit = defineEmits(['close'])

const chartRef = ref(null)
let chart = null

onMounted(() => {
  chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(10, 22, 40, 0.95)',
      borderColor: 'rgba(0, 180, 255, 0.3)',
      textStyle: { color: '#e8f0fe', fontSize: 13 }
    },
    series: [
      {
        type: 'treemap',
        roam: false,
        nodeClick: false,
        width: '94%',
        height: '88%',
        top: '4%',
        left: '3%',
        squareRatio: 1,
        label: {
          show: true,
          position: 'inside',
          color: '#e1f5fe',
          fontSize: 14,
          fontWeight: 600,
          textShadowColor: 'rgba(0,0,0,0.5)',
          textShadowBlur: 4
        },
        itemStyle: {
          gapWidth: 4,
          borderColor: 'rgba(0, 162, 255, 0.4)',
          borderRadius: 8
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 12,
            shadowColor: 'rgba(0, 114, 255, 0.5)',
            borderColor: '#00d8ff',
            borderWidth: 2
          }
        },
        levels: [
          {
            colorSaturation: [0.3, 0.7],
            itemStyle: {
              color: '#0a3a5c'
            }
          }
        ],
        data: [
          {
            name: 'Austurland',
            value: 423,
            itemStyle: { color: '#0072ff' }
          },
          {
            name: 'Suðurland',
            value: 256,
            itemStyle: { color: '#00d8ff' }
          },
          {
            name: 'Norðurland vestra',
            value: 489,
            itemStyle: { color: '#0099ff' }
          },
          {
            name: 'Norðurland eystra',
            value: 51,
            itemStyle: { color: '#0a5a9c' }
          },
          {
            name: 'Vesturland',
            value: 312,
            itemStyle: { color: '#0caf64' }
          },
          {
            name: 'Vestfirðir',
            value: 178,
            itemStyle: { color: '#aa66ff' }
          },
          {
            name: 'Reykjavik',
            value: 891,
            itemStyle: { color: '#00e5ff' }
          }
        ]
      }
    ]
  })
})

onUnmounted(() => {
  if (chart) {
    chart.dispose()
    chart = null
  }
})

function closeModal() {
  emit('close')
}
</script>

<template>
  <div class="map-overlay" @click.self="closeModal">
    <div class="map-modal">
      <div class="map-modal-header">
        <div class="map-header-left">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
            <rect x="3" y="3" width="7" height="7" rx="1"/>
            <rect x="14" y="3" width="7" height="7" rx="1"/>
            <rect x="3" y="14" width="7" height="7" rx="1"/>
            <rect x="14" y="14" width="7" height="7" rx="1"/>
          </svg>
          <h3>区域状态总览</h3>
        </div>
        <button class="map-modal-close" @click="closeModal">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
      <div class="map-modal-body">
        <div ref="chartRef" class="map-chart-container"></div>
      </div>
    </div>
  </div>
</template>

<style>
.map-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3000;
}
.map-modal {
  width: 90%;
  max-width: 1000px;
  height: 80vh;
  background: var(--modal-bg, #0d3967);
  border: 1px solid var(--line-strong, rgba(0, 162, 255, 0.5));
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  box-shadow: var(--glow-strong, 0 0 20px rgba(0, 216, 255, 0.6));
  overflow: hidden;
}
.map-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: linear-gradient(90deg, rgba(0, 100, 255, 0.15), rgba(0, 150, 255, 0.05), rgba(0, 100, 255, 0.15));
  border-bottom: 1px solid var(--line, rgba(0, 162, 255, 0.3));
  flex-shrink: 0;
}
.map-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.map-header-left svg {
  color: #00aaff;
  filter: drop-shadow(0 0 8px rgba(0, 170, 255, 0.5));
}
.map-header-left h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #e0f0ff;
  letter-spacing: 2px;
  text-shadow: 0 0 20px rgba(0, 150, 255, 0.3);
}
.map-modal-close {
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
.map-modal-close:hover {
  background: rgba(255, 80, 80, 0.2);
  border-color: rgba(255, 80, 80, 0.3);
  color: #ff6666;
}
.map-modal-body {
  flex: 1;
  padding: 16px;
  overflow: hidden;
}
.map-chart-container {
  width: 100%;
  height: 100%;
}
</style>
