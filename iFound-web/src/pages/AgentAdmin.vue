<template>
  <div class="agent-admin" data-theme="dark">
    <header class="aa-header">
      <div class="aa-header-inner">
        <div class="aa-brand">
          <div class="aa-logo">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="22" height="22">
              <circle cx="12" cy="12" r="3"/>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
            </svg>
          </div>
          <div class="aa-titles">
            <h1>Agent 配置管理</h1>
            <span class="aa-sub">AGENT CONFIGURATION</span>
          </div>
        </div>
        <button class="aa-btn-ghost" @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          返回
        </button>
      </div>
    </header>

    <div class="aa-layout">
      <main class="aa-main">
        <div class="aa-toolbar">
          <div class="aa-toolbar-right">
            <button class="aa-btn-ghost" @click="fetchList" title="刷新">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                <path d="M23 4v6h-6M1 20v-6h6"/>
                <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
              </svg>
            </button>
            <button class="aa-btn-primary" @click="openAdd">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
              新增 Agent
            </button>
          </div>
        </div>

        <div class="aa-grid" v-if="agentList.length > 0">
          <article v-for="(agent, idx) in agentList" :key="agent.agentId" class="aa-card" :style="{ '--i': idx }">
            <div class="aa-card-top">
              <div class="aa-card-marker" :style="{ background: agent.available ? '#22c55e' : '#6b7280' }"></div>
              <div class="aa-card-identity">
                <h3 class="aa-card-name">{{ agent.agentName }}</h3>
                <code class="aa-card-id">{{ agent.agentId }}</code>
              </div>
              <span class="aa-card-status" :class="agent.available ? 'on' : 'off'">
                {{ agent.available ? '可用' : '停用' }}
              </span>
            </div>
            <div class="aa-card-meta">
              <div class="aa-meta-item">
                <span class="aa-meta-label">类型</span>
                <span class="aa-meta-value">{{ agent.agentType }}</span>
              </div>
              <div class="aa-meta-item">
                <span class="aa-meta-label">业务域</span>
                <span class="aa-meta-value">{{ agent.businessDomains }}</span>
              </div>
              <div class="aa-meta-item">
                <span class="aa-meta-label">接收接口</span>
                <span class="aa-meta-value aa-url">{{ agent.receiveTaskUrl }}</span>
              </div>
              <div class="aa-meta-item">
                <span class="aa-meta-label">查询接口</span>
                <span class="aa-meta-value aa-url">{{ agent.queryTaskUrl }}</span>
              </div>
            </div>
            <div class="aa-card-actions">
              <button class="aa-btn-sm" @click="openEdit(agent)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
                编辑
              </button>
              <button class="aa-btn-sm aa-btn-danger" @click="handleDelete(agent)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                  <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                </svg>
                删除
              </button>
            </div>
          </article>
        </div>

        <div v-else class="aa-empty">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="48" height="48">
            <circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/>
          </svg>
          <p>暂无 Agent 配置</p>
          <button class="aa-btn-primary" @click="openAdd">新增 Agent</button>
        </div>
      </main>
    </div>

    <!-- Dialog -->
    <div v-if="dialogVisible" class="aa-dialog-mask" @click.self="dialogVisible = false">
      <div class="aa-dialog">
        <div class="aa-dialog-header">
          <h3>{{ form.agentId ? '编辑 Agent' : '新增 Agent' }}</h3>
          <button class="aa-dialog-close" @click="dialogVisible = false">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="aa-dialog-body">
          <div class="aa-form-item">
            <label>Agent ID</label>
            <input v-model="form.agentId" :disabled="!!form.agentId" placeholder="如: ops-agent" />
          </div>
          <div class="aa-form-item">
            <label>Agent 名称</label>
            <input v-model="form.agentName" placeholder="如: 设备运维Agent" />
          </div>
          <div class="aa-form-item">
            <label>Agent 类型</label>
            <input v-model="form.agentType" placeholder="如: maintenance" />
          </div>
          <div class="aa-form-item">
            <label>业务域（逗号分隔）</label>
            <input v-model="form.businessDomains" placeholder="如: 设备,故障,告警" />
          </div>
          <div class="aa-form-item">
            <label>接收任务接口</label>
            <input v-model="form.receiveTaskUrl" placeholder="http://..." />
          </div>
          <div class="aa-form-item">
            <label>查询任务接口</label>
            <input v-model="form.queryTaskUrl" placeholder="http://..." />
          </div>
          <div class="aa-form-item">
            <label>是否可用</label>
            <select v-model="form.available">
              <option :value="1">可用</option>
              <option :value="0">停用</option>
            </select>
          </div>
        </div>
        <div class="aa-dialog-footer">
          <button class="aa-btn-ghost" @click="dialogVisible = false">取消</button>
          <button class="aa-btn-primary" @click="handleSave" :disabled="saving">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import iotRequest from '@/api/request'

const router = useRouter()
const agentList = ref([])
const dialogVisible = ref(false)
const saving = ref(false)

const form = ref({
  agentId: '',
  agentName: '',
  agentType: '',
  businessDomains: '',
  receiveTaskUrl: '',
  queryTaskUrl: '',
  available: 1
})

function goBack() {
  router.push('/dashboard')
}

async function fetchList() {
  try {
    const res = await iotRequest.get('/agent/list')
    if (res && (res.code === '200' || res.Status === 'Ok')) {
      agentList.value = res.data || []
    }
  } catch (e) {
    console.error('获取Agent列表失败:', e)
  }
}

function openAdd() {
  form.value = { agentId: '', agentName: '', agentType: '', businessDomains: '', receiveTaskUrl: '', queryTaskUrl: '', available: 1 }
  dialogVisible.value = true
}

function openEdit(agent) {
  form.value = { ...agent }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.agentId || !form.value.agentName) {
    alert('Agent ID 和名称不能为空')
    return
  }
  saving.value = true
  try {
    const url = form.value.agentId ? '/agent' : '/agent'
    const method = form.value.agentId ? 'put' : 'post'
    const res = await iotRequest[method](url, form.value)
    if (res && (res.code === '200' || res.Status === 'Ok')) {
      dialogVisible.value = false
      await fetchList()
    }
  } catch (e) {
    console.error('保存失败:', e)
    alert('保存失败: ' + (e.message || ''))
  } finally {
    saving.value = false
  }
}

async function handleDelete(agent) {
  if (!confirm(`确定删除 Agent "${agent.agentName}" 吗？`)) return
  try {
    const res = await iotRequest.delete(`/agent/${agent.agentId}`)
    if (res && (res.code === '200' || res.Status === 'Ok')) {
      await fetchList()
    }
  } catch (e) {
    console.error('删除失败:', e)
  }
}

onMounted(fetchList)
</script>

<style scoped>
.agent-admin {
  min-height: 100vh;
  background: #0a0e17;
  color: #e2e8f0;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
}
.aa-header {
  border-bottom: 1px solid rgba(255,255,255,0.06);
  padding: 16px 32px;
}
.aa-header-inner {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.aa-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}
.aa-logo {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #818cf8);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.aa-titles h1 {
  font-size: 18px;
  font-weight: 700;
  margin: 0;
  color: #f1f5f9;
}
.aa-sub {
  font-size: 11px;
  color: #64748b;
  letter-spacing: 1px;
}
.aa-btn-ghost {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px;
  background: transparent;
  color: #94a3b8;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.aa-btn-ghost:hover {
  border-color: rgba(255,255,255,0.2);
  color: #e2e8f0;
}
.aa-layout {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px 32px;
}
.aa-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 20px;
}
.aa-toolbar-right {
  display: flex;
  gap: 10px;
}
.aa-btn-primary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #6366f1, #818cf8);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.aa-btn-primary:hover {
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  transform: translateY(-1px);
}
.aa-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 16px;
}
.aa-card {
  background: #111827;
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,0.06);
  padding: 16px;
  animation: cardIn 0.3s ease both;
  animation-delay: calc(var(--i) * 0.05s);
}
@keyframes cardIn {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}
.aa-card-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.aa-card-marker {
  width: 4px;
  height: 36px;
  border-radius: 2px;
  flex-shrink: 0;
}
.aa-card-identity {
  flex: 1;
  min-width: 0;
}
.aa-card-name {
  font-size: 15px;
  font-weight: 700;
  margin: 0 0 2px;
  color: #f1f5f9;
}
.aa-card-id {
  font-size: 11px;
  color: #64748b;
  font-family: 'JetBrains Mono', monospace;
}
.aa-card-status {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 20px;
  font-weight: 600;
}
.aa-card-status.on { background: rgba(34,197,94,0.15); color: #22c55e; }
.aa-card-status.off { background: rgba(107,114,128,0.15); color: #6b7280; }
.aa-card-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}
.aa-meta-item {
  display: flex;
  gap: 8px;
  font-size: 12px;
}
.aa-meta-label {
  color: #64748b;
  min-width: 70px;
  flex-shrink: 0;
}
.aa-meta-value {
  color: #cbd5e1;
  flex: 1;
}
.aa-url {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  word-break: break-all;
}
.aa-card-actions {
  display: flex;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid rgba(255,255,255,0.06);
}
.aa-btn-sm {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 6px;
  background: transparent;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.aa-btn-sm:hover {
  border-color: #6366f1;
  color: #6366f1;
}
.aa-btn-danger:hover {
  border-color: #ef4444;
  color: #ef4444;
}
.aa-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 80px 0;
  color: #64748b;
}
.aa-empty svg { color: #334155; }
.aa-empty p { font-size: 14px; margin: 0; }

/* Dialog */
.aa-dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.aa-dialog {
  background: #1e293b;
  border-radius: 16px;
  border: 1px solid rgba(255,255,255,0.08);
  width: 520px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}
.aa-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.aa-dialog-header h3 {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
  color: #f1f5f9;
}
.aa-dialog-close {
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  padding: 4px;
}
.aa-dialog-close:hover { color: #e2e8f0; }
.aa-dialog-body {
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}
.aa-form-item label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  margin-bottom: 6px;
}
.aa-form-item input,
.aa-form-item select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
}
.aa-form-item input:focus,
.aa-form-item select:focus {
  border-color: #6366f1;
}
.aa-form-item input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.aa-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid rgba(255,255,255,0.06);
}
</style>
