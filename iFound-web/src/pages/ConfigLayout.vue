<template>
  <div class="config-admin">
    <header class="ca-header">
      <div class="ca-header-inner">
        <div class="ca-brand">
          <div class="ca-logo">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="22" height="22">
              <circle cx="12" cy="12" r="3"/>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
            </svg>
          </div>
          <div class="ca-titles">
            <h1>配置管理</h1>
            <span class="ca-sub">SYSTEM CONFIG</span>
          </div>
        </div>
        <button class="ca-btn-ghost" @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          返回
        </button>
      </div>
    </header>

    <div class="ca-layout">
      <aside class="ca-sidebar">
        <nav class="ca-nav">
          <router-link
            v-for="item in menuItems"
            :key="item.key"
            :to="item.path"
            class="ca-nav-item"
            active-class="active"
          >
            <component :is="item.icon" />
            <span>{{ item.label }}</span>
          </router-link>
        </nav>
      </aside>

      <main class="ca-main">
        <router-view />
      </main>
    </div>

    <Toast
      v-if="toast.show"
      :type="toast.type"
      :title="toast.title"
      :description="toast.description"
      :duration="toast.duration"
      @close="toast.show = false"
    />
  </div>
</template>

<script setup>
import { ref, h, provide } from 'vue'
import { useRouter } from 'vue-router'
import Toast from '@/components/Toast.vue'

const router = useRouter()

const menuItems = [
  // {
  //   key: 'zone',
  //   label: '区域配置',
  //   path: '/config/zone',
  //   icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '18', height: '18' }, [
  //     h('path', { d: 'M12 2L2 7l10 5 10-5-10-5z' }),
  //     h('path', { d: 'M2 17l10 5 10-5' }),
  //     h('path', { d: 'M2 12l10 5 10-5' }),
  //   ]),
  // },
  {
    key: 'voice',
    label: '语音权限',
    path: '/config/voice',
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '18', height: '18' }, [
      h('path', { d: 'M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z' }),
      h('path', { d: 'M19 10v2a7 7 0 0 1-14 0v-2' }),
      h('line', { x1: '12', y1: '19', x2: '12', y2: '23' }),
      h('line', { x1: '8', y1: '23', x2: '16', y2: '23' }),
    ]),
  },
  {
    key: 'stream-chat-records',
    label: 'AI语音日志',
    path: '/config/stream-chat-records',
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '18', height: '18' }, [
      h('path', { d: 'M3 12h4l3-9 4 18 3-9h4' }),
    ]),
  },
  {
    key: 'agent',
    label: 'Agent配置',
    path: '/config/agent',
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '18', height: '18' }, [
      h('circle', { cx: '12', cy: '12', r: '3' }),
      h('path', { d: 'M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z' }),
    ]),
  },
  {
    key: 'task',
    label: 'Agent协作日志',
    path: '/config/task',
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '18', height: '18' }, [
      h('path', { d: 'M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2' }),
      h('rect', { x: '9', y: '3', width: '6', height: '4', rx: '2' }),
      h('path', { d: 'M9 14l2 2 4-4' }),
    ]),
  },
  // {
  //   key: 'tts-records',
  //   label: 'AI语音日志',
  //   path: '/config/tts-records',
  //   icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '18', height: '18' }, [
  //     h('path', { d: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z' }),
  //   ]),
  // },
  // {
  //   key: 'diagnosis',
  //   label: '运维Agent诊断日志',
  //   path: '/config/diagnosis',
  //   icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '18', height: '18' }, [
  //     h('path', { d: '14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z' }),
  //     h('polyline', { points: '14 2 14 8 20 8' }),
  //     h('line', { x1: '16', y1: '13', x2: '8', y2: '13' }),
  //     h('line', { x1: '16', y1: '17', x2: '8', y2: '17' }),
  //     h('polyline', { points: '10 9 9 9 8 9' }),
  //   ]),
  // },
]

const toast = ref({ show: false, type: 'success', title: '', description: '', duration: 3000 })

function showToast(type, title, description = '', duration = 3000) {
  toast.value = { show: true, type, title, description, duration }
}

function goBack() {
  router.push('/dashboard')
}

provide('showToast', showToast)
</script>

<style scoped>
.config-admin {
  --bg: rgb(0, 60, 126);
  --surface: rgb(0, 60, 126);
  --surface-soft: rgba(255, 255, 255, 0.03);
  --surface-hover: rgba(255, 255, 255, 0.06);
  --sidebar-bg: rgb(0, 60, 126);
  --card-bg: rgba(255, 255, 255, 0.03);
  --border: rgba(255, 255, 255, 0.06);
  --border-hover: rgba(0, 180, 255, 0.28);
  --text: #e8f0fe;
  --text-2: rgba(255, 255, 255, 0.7);
  --muted: rgba(255, 255, 255, 0.4);
  --accent: #00b4ff;
  --accent-soft: rgba(0, 180, 255, 0.15);
  --primary: #00b4ff;
  --primary-hover: #0090e0;
  --danger: #ef4444;
  --success: #00ff88;
  --warning: #ff9f1c;
  --radius: 10px;
  --radius-sm: 8px;
  --mono: 'SF Mono', 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  --sidebar-w: 220px;

  min-height: 100vh;
  background: var(--bg);
  color: var(--text);
  color-scheme: dark;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.ca-header {
  position: sticky;
  top: 0;
  z-index: 100;
  padding: 14px 24px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.24), inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

.ca-header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 auto;
}

.ca-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ca-logo {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(0, 180, 255, 0.28);
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
}

.ca-titles h1 {
  margin: 0;
  color: var(--text);
  font-size: 18px;
  font-weight: 800;
}

.ca-sub {
  color: var(--muted);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
}

.ca-layout {
  display: flex;
  gap: 20px;
  min-height: calc(100vh - 67px);
  margin: 0 auto;
  padding: 20px 24px;
}

.ca-sidebar {
  width: var(--sidebar-w);
  flex-shrink: 0;
}

.ca-nav {
  position: sticky;
  top: 87px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px;
  background: var(--card-bg);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.ca-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 10px 14px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  color: var(--text-2);
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
  transition: background 0.18s ease, border-color 0.18s ease, color 0.18s ease;
}

.ca-nav-item:hover {
  background: var(--surface-hover);
  border-color: var(--border-hover);
  color: var(--text);
}

.ca-nav-item.active {
  background: var(--accent-soft);
  border-color: rgba(0, 180, 255, 0.42);
  color: var(--accent);
}

.ca-nav-item svg {
  flex-shrink: 0;
}

.ca-main {
  flex: 1;
  min-width: 0;
}

:deep(.ca-panel) {
  color: var(--text);
}

:deep(.ca-toolbar) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

:deep(.ca-toolbar-right),
:deep(.ca-filters) {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

:deep(.ca-search),
:deep(.ca-input-date),
:deep(.batch-input) {
  background: var(--card-bg) !important;
  border: 1px solid var(--border) !important;
  border-radius: var(--radius-sm) !important;
  color: var(--text) !important;
}

:deep(.ca-search) {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 320px;
  padding: 8px 14px;
}

:deep(.ca-search input),
:deep(.ca-field input),
:deep(.ca-field textarea),
:deep(.ca-field select),
:deep(.ca-pagination select) {
  background: rgba(255, 255, 255, 0.03) !important;
  border-color: var(--border) !important;
  color: var(--text) !important;
}

:deep(.ca-search input) {
  background: transparent !important;
  border: none !important;
}

:deep(input::placeholder),
:deep(textarea::placeholder) {
  color: var(--muted) !important;
}

:deep(.ca-btn-ghost),
:deep(.ca-card-btn),
:deep(.ca-btn-icon),
:deep(.ca-page-btn),
:deep(.batch-item) {
  background: rgba(255, 255, 255, 0.03) !important;
  border: 1px solid var(--border) !important;
  border-radius: var(--radius-sm) !important;
  color: var(--text-2) !important;
}

:deep(.ca-btn-ghost:hover),
:deep(.ca-card-btn.edit:hover),
:deep(.ca-btn-icon:hover:not(:disabled)),
:deep(.ca-page-btn:hover:not(:disabled)),
:deep(.batch-item:hover) {
  background: var(--surface-hover) !important;
  border-color: var(--border-hover) !important;
  color: var(--accent) !important;
}

:deep(.ca-btn-primary) {
  background: linear-gradient(135deg, #00b4ff, #0090e0) !important;
  border: none !important;
  border-radius: var(--radius-sm) !important;
  color: #fff !important;
  font-weight: 800;
}

:deep(.ca-btn-primary:hover) {
  background: linear-gradient(135deg, #20c7ff, #00a2f2) !important;
  box-shadow: 0 4px 16px rgba(0, 180, 255, 0.24);
}

:deep(.ca-btn-sync) {
  background: rgba(0, 255, 136, 0.08) !important;
  border: 1px solid rgba(0, 255, 136, 0.24) !important;
  color: var(--success) !important;
}

:deep(.ca-btn-danger),
:deep(.ca-card-btn.del:hover),
:deep(.ca-btn-icon.ca-btn-danger:hover:not(:disabled)) {
  background: rgba(239, 68, 68, 0.08) !important;
  border-color: rgba(239, 68, 68, 0.36) !important;
  color: var(--danger) !important;
}

:deep(.ca-grid),
:deep(.ca-voice-grid) {
  gap: 16px;
}

:deep(.ca-card),
:deep(.ca-voice-card),
:deep(.records-table-wrap),
:deep(.records-content) {
  background: var(--card-bg) !important;
  border: 1px solid var(--border) !important;
  border-radius: var(--radius) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

:deep(.ca-card),
:deep(.ca-voice-card) {
  padding: 18px !important;
}

:deep(.ca-card-name),
:deep(.ca-voice-name),
:deep(.content-title) {
  color: var(--text) !important;
}

:deep(.ca-card-cid),
:deep(.ca-meta-label),
:deep(.ca-date-sep),
:deep(.avatar-name),
:deep(.chat-time),
:deep(.td-id),
:deep(.ca-page-info),
:deep(.ca-field-hint) {
  color: var(--muted) !important;
}

:deep(.ca-meta-item),
:deep(.ca-meta-val),
:deep(.ca-perm-toggle),
:deep(.ca-empty),
:deep(.content-empty) {
  color: var(--text-2) !important;
}

:deep(.ca-card-actions),
:deep(.ca-voice-card-actions),
:deep(.ca-pagination),
:deep(.content-header),
:deep(.chat-divider),
:deep(.batch-custom) {
  border-color: var(--border) !important;
}

:deep(.ca-card-status.on),
:deep(.ca-voice-badge.user),
:deep(.ca-embedding-badge.recorded) {
  background: rgba(0, 255, 136, 0.12) !important;
  color: var(--success) !important;
}

:deep(.ca-card-status.off),
:deep(.ca-voice-card.is-stranger),
:deep(.ca-voice-badge.stranger),
:deep(.ca-embedding-badge.unrecorded) {
  background: rgba(255, 255, 255, 0.04) !important;
  color: var(--muted) !important;
}

:deep(.ca-perm-track) {
  background: rgba(255, 255, 255, 0.12) !important;
}

:deep(.ca-perm-toggle.active .ca-perm-track) {
  background: var(--accent) !important;
}

:deep(.records-table-inner),
:deep(.chat-body) {
  background: transparent !important;
}

:deep(.ca-table thead),
:deep(.ca-pagination),
:deep(.content-header),
:deep(.batch-delete-dropdown) {
  background: rgb(0, 60, 126) !important;
}

:deep(.ca-table th) {
  color: var(--muted) !important;
  border-bottom: 1px solid var(--border) !important;
}

:deep(.ca-table td) {
  color: var(--text-2) !important;
  border-bottom: 1px solid var(--border) !important;
}

:deep(.ca-table tbody tr:hover),
:deep(.ca-table tbody tr.row-active) {
  background: rgba(0, 180, 255, 0.1) !important;
}

:deep(.chat-avatar),
:deep(.chat-user-avatar) {
  background: var(--accent) !important;
}

:deep(.chat-user-bubble) {
  background: linear-gradient(135deg, #00b4ff, #0090e0) !important;
  color: #fff !important;
}

:deep(.chat-ai-bubble),
:deep(.ca-vp-text-card) {
  background: rgba(255, 255, 255, 0.03) !important;
  border: 1px solid var(--border) !important;
  color: var(--text-2) !important;
}

:deep(.ca-vp-hint) {
  color: var(--text-2) !important;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 768px) {
  .ca-layout {
    flex-direction: column;
    padding: 16px;
  }

  .ca-sidebar {
    width: 100%;
  }

  .ca-nav {
    position: static;
    flex-direction: row;
    overflow-x: auto;
  }

  .ca-nav-item {
    white-space: nowrap;
  }

  :deep(.ca-grid),
  :deep(.ca-voice-grid) {
    grid-template-columns: 1fr;
  }
}

:global(.ca-config-overlay) {
  background: rgba(0, 0, 0, 0.7) !important;
}

:global(.ca-config-overlay .ca-config-dialog) {
  --dialog-bg: rgb(0, 60, 126) !important;
  --dialog-text: #e8f0fe !important;
  --dialog-text-2: rgba(255, 255, 255, 0.7) !important;
  --dialog-muted: rgba(255, 255, 255, 0.4) !important;
  --dialog-border: rgba(255, 255, 255, 0.06) !important;
  --dialog-input-bg: rgba(255, 255, 255, 0.03) !important;
  background: rgb(0, 60, 126) !important;
  color: #e8f0fe !important;
  border: 1px solid rgba(0, 180, 255, 0.2) !important;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

:global(.ca-config-overlay .ca-dialog-head),
:global(.ca-config-overlay .ca-dialog-foot) {
  border-color: rgba(255, 255, 255, 0.06) !important;
}

:global(.ca-config-overlay .ca-dialog-head h3) {
  color: #e8f0fe !important;
}

:global(.ca-config-overlay .ca-field label),
:global(.ca-config-overlay .ca-vp-hint) {
  color: rgba(255, 255, 255, 0.7) !important;
}

:global(.ca-config-overlay .ca-field input),
:global(.ca-config-overlay .ca-field textarea),
:global(.ca-config-overlay .ca-field select),
:global(.ca-config-overlay .ca-vp-text-card) {
  background: rgba(255, 255, 255, 0.03) !important;
  color: #e8f0fe !important;
  border-color: rgba(255, 255, 255, 0.06) !important;
}

:global(.ca-config-overlay .ca-dialog-x) {
  color: rgba(255, 255, 255, 0.4) !important;
}

:global(.ca-config-overlay .ca-dialog-x:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
  color: #fff !important;
}
</style>
