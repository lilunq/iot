<template>
  <div class="ca-panel">
    <div class="ca-toolbar">
      <div class="ca-search">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
          <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
        </svg>
        <input v-model="searchKeyword" type="text" placeholder="搜索Agent名称或ID" />
      </div>
      <div class="ca-toolbar-right">
        <button class="ca-btn-ghost" @click="fetchList" title="刷新">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <path d="M23 4v6h-6M1 20v-6h6"/>
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
          </svg>
        </button>
        <button class="ca-btn-primary" @click="openAdd">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          新增 Agent
        </button>
      </div>
    </div>

    <div class="ca-grid" v-if="filteredList.length > 0">
      <article v-for="(agent, idx) in filteredList" :key="agent.agentId" class="ca-card" :style="{ '--i': idx }">
        <div class="ca-card-top">
          <div class="ca-card-marker" :style="{ background: agent.available ? '#22c55e' : '#6b7280' }"></div>
          <div class="ca-card-identity">
            <h3 class="ca-card-name">{{ agent.agentName }}</h3>
            <code class="ca-card-cid">{{ agent.agentId }}</code>
          </div>
          <span class="ca-card-status" :class="agent.available ? 'on' : 'off'">
            {{ agent.available ? '可用' : '停用' }}
          </span>
        </div>
        <div class="ca-card-meta">
          <div class="ca-meta-item">
            <span class="ca-meta-label">类型</span>
            <span class="ca-meta-val">{{ agent.agentType }}</span>
          </div>
          <div class="ca-meta-item">
            <span class="ca-meta-label">业务域</span>
            <span class="ca-meta-val">{{ agent.businessDomains }}</span>
          </div>
          <div class="ca-meta-item">
            <span class="ca-meta-label">接收接口</span>
            <span class="ca-meta-val mono">{{ agent.receiveTaskUrl }}</span>
          </div>
          <div class="ca-meta-item">
            <span class="ca-meta-label">查询接口</span>
            <span class="ca-meta-val mono">{{ agent.queryTaskUrl }}</span>
          </div>
          <div class="ca-meta-item" v-if="agent.resultCheckUrl">
            <span class="ca-meta-label">结果校验</span>
            <span class="ca-meta-val mono">{{ agent.resultCheckUrl }}</span>
          </div>
          <div class="ca-meta-item" v-if="agent.resultQueryUrl">
            <span class="ca-meta-label">结果查询</span>
            <span class="ca-meta-val mono">{{ agent.resultQueryUrl }}</span>
          </div>
          <div class="ca-meta-item" v-if="agent.hermesUrl">
            <span class="ca-meta-label">Hermes接口</span>
            <span class="ca-meta-val mono">{{ agent.hermesUrl }}</span>
          </div>
        </div>
        <div class="ca-card-actions">
          <button class="ca-card-btn edit" @click="openEdit(agent)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
            编辑
          </button>
          <button class="ca-card-btn del" @click="handleDelete(agent)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
              <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
            </svg>
            删除
          </button>
        </div>
      </article>
    </div>

    <div v-else class="ca-empty">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="48" height="48">
        <circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/>
      </svg>
      <p>暂无 Agent 配置</p>
      <button class="ca-btn-primary" @click="openAdd">新增 Agent</button>
    </div>

    <!-- Dialog -->
    <Teleport to="body">
      <div v-if="dialogVisible" class="ca-overlay ca-config-overlay" @click.self="dialogVisible = false">
        <div class="ca-dialog ca-config-dialog">
          <div class="ca-dialog-head">
            <h3>{{ form.agentId ? '编辑 Agent' : '新增 Agent' }}</h3>
            <button class="ca-dialog-x" @click="dialogVisible = false">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="ca-dialog-body">
            <div class="ca-field">
              <label>Agent ID <span class="req">*</span></label>
              <input v-model="form.agentId" :disabled="!!form.agentId" placeholder="如: ops-agent" />
            </div>
            <div class="ca-field">
              <label>Agent 名称 <span class="req">*</span></label>
              <input v-model="form.agentName" placeholder="如: 设备运维Agent" />
            </div>
            <div class="ca-field">
              <label>Agent 类型</label>
              <input v-model="form.agentType" placeholder="如: maintenance" />
            </div>
            <div class="ca-field">
              <label>业务域（逗号分隔）</label>
              <input v-model="form.businessDomains" placeholder="如: 设备,故障,告警" />
            </div>
            <div class="ca-field">
              <label>接收任务接口</label>
              <input v-model="form.receiveTaskUrl" placeholder="http://..." />
            </div>
            <div class="ca-field">
              <label>查询任务接口</label>
              <input v-model="form.queryTaskUrl" placeholder="http://..." />
            </div>
            <div class="ca-field">
              <label>结果校验接口</label>
              <input v-model="form.resultCheckUrl" placeholder="http://..." />
            </div>
            <div class="ca-field">
              <label>结果查询接口</label>
              <input v-model="form.resultQueryUrl" placeholder="http://..." />
            </div>
            <div class="ca-field">
              <label>Hermes对话接口</label>
              <input v-model="form.hermesUrl" placeholder="http://..." />
            </div>
            <div class="ca-field">
              <label>是否可用</label>
              <select v-model.number="form.available" class="ca-select">
                <option :value="1">可用</option>
                <option :value="0">停用</option>
              </select>
            </div>
          </div>
          <div class="ca-dialog-foot">
            <button class="ca-btn-ghost" @click="dialogVisible = false">取消</button>
            <button class="ca-btn-primary" @click="handleSave" :disabled="saving">
              {{ saving ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, inject } from 'vue'
import { aiRequest } from '@/utils/request'

const showToast = inject('showToast') || (() => {})

const agentList = ref([])
const searchKeyword = ref('')
const dialogVisible = ref(false)
const saving = ref(false)

const form = ref({
  agentId: '',
  agentName: '',
  agentType: '',
  businessDomains: '',
  receiveTaskUrl: '',
  queryTaskUrl: '',
  resultCheckUrl: '',
  resultQueryUrl: '',
  hermesUrl: '',
  available: 1
})

const filteredList = computed(() => {
  if (!searchKeyword.value) return agentList.value
  const kw = searchKeyword.value.toLowerCase()
  return agentList.value.filter(a =>
    a.agentName.toLowerCase().includes(kw) ||
    a.agentId.toLowerCase().includes(kw)
  )
})

async function fetchList() {
  try {
    const res = await aiRequest.get('/agent/list')
    if (res && (res.code === '200' || res.code === 200)) {
      agentList.value = res.data || []
    }
  } catch (e) {
    console.error('获取Agent列表失败:', e)
  }
}

function openAdd() {
  form.value = { agentId: '', agentName: '', agentType: '', businessDomains: '', receiveTaskUrl: '', queryTaskUrl: '', resultCheckUrl: '', resultQueryUrl: '', hermesUrl: '', available: 1 }
  dialogVisible.value = true
}

function openEdit(agent) {
  form.value = { ...agent }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.agentId || !form.value.agentName) {
    showToast('error', 'Agent ID 和名称不能为空')
    return
  }
  saving.value = true
  try {
    let res
    if (form.value.agentId) {
      res = await aiRequest.put('/agent', form.value)
    } else {
      res = await aiRequest.post('/agent', form.value)
    }
    if (res && (res.code === '200' || res.code === 200)) {
      dialogVisible.value = false
      await fetchList()
      showToast('success', '保存成功')
    } else {
      showToast('error', res?.message || '保存失败')
    }
  } catch (e) {
    console.error('保存失败:', e)
    showToast('error', '保存失败: ' + (e.message || ''))
  } finally {
    saving.value = false
  }
}

async function handleDelete(agent) {
  if (!confirm(`确定删除 Agent "${agent.agentName}" 吗？`)) return
  try {
    const res = await aiRequest.delete(`/agent/${agent.agentId}`)
    if (res && (res.code === '200' || res.code === 200)) {
      await fetchList()
      showToast('success', '删除成功')
    } else {
      showToast('error', res?.message || '删除失败')
    }
  } catch (e) {
    console.error('删除失败:', e)
    showToast('error', '删除失败')
  }
}

onMounted(fetchList)
</script>

<style scoped>
/* ===== Design Tokens ===== */
.config-admin {
  --bg: #f5f3f0;
  --card-bg: #ffffff !important;
  --border: rgba(0, 0, 0, 0.08);
  --text: #1a1a1a;
  --text-2: #5a5a5a;
  --muted: #999;
  --primary: #1268ff;
  --primary-hover: #0d5ce6;
  --danger: #ef4444;
  --success: #08ae66;
  --warning: #f59e0b;
}

.ca-panel { animation: fadeIn 0.3s ease; }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

.ca-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  gap: 12px;
  flex-wrap: wrap;
}
.ca-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--card-bg);
  color: var(--muted);
  flex: 1;
  max-width: 320px;
}
.ca-search input {
  border: none;
  background: transparent;
  color: var(--text);
  font-size: 13px;
  outline: none;
  width: 100%;
}
.ca-search input::placeholder { color: var(--muted); }
.ca-toolbar-right { display: flex; gap: 8px; }

.ca-btn-ghost {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: transparent;
  color: var(--text-2);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.ca-btn-ghost:hover { border-color: rgba(255,255,255,0.15);  }
.ca-btn-primary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--primary), #818cf8);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  color:white;
}
.ca-btn-primary:hover { background: linear-gradient(135deg, var(--primary-hover), var(--primary)); transform: translateY(-1px); }

.ca-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 16px;
}
.ca-card {
  background: var(--card-bg);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 18px;
  animation: cardIn 0.3s ease both;
  animation-delay: calc(var(--i) * 0.05s);
}
@keyframes cardIn { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }
.ca-card-top { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.ca-card-marker { width: 4px; height: 36px; border-radius: 2px; flex-shrink: 0; }
.ca-card-identity { flex: 1; min-width: 0; }
.ca-card-name { font-size: 15px; font-weight: 700; margin: 0 0 2px; color: var(--text); }
.ca-card-cid { font-size: 11px; color: var(--muted); font-family: 'JetBrains Mono', monospace; }
.ca-card-status { font-size: 11px; padding: 3px 10px; border-radius: 20px; font-weight: 600; }
.ca-card-status.on { background: rgba(34,197,94,0.15); color: var(--success); }
.ca-card-status.off { background: rgba(107,114,128,0.15); color: #6b7280; }
.ca-card-meta { display: flex; flex-direction: column; gap: 8px; margin-bottom: 14px; }
.ca-meta-item { display: flex; gap: 8px; font-size: 12px; }
.ca-meta-label { color: var(--muted); min-width: 70px; flex-shrink: 0; }
.ca-meta-val { color: var(--text-2); flex: 1; }
.ca-meta-val.mono { font-family: 'JetBrains Mono', monospace; font-size: 11px; word-break: break-all; }
.ca-card-actions { display: flex; gap: 8px; padding-top: 12px; border-top: 1px solid var(--border); }
.ca-card-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: transparent;
  color: var(--text-2);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.ca-card-btn.edit:hover { border-color: var(--primary); color: var(--primary); }
.ca-card-btn.del:hover { border-color: var(--danger); color: var(--danger); }

.ca-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 0;
  gap: 16px;
  color: var(--text-2);
}
.ca-empty svg { opacity: 0.4; }

.ca-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: rgba(0,0,0,0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: fadeIn 0.2s ease;
}
.ca-dialog {
  width: 520px;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 64px);
  background: var(--card-bg);
  border: 1px solid var(--border);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  animation: slideUp 0.25s ease;
}
@keyframes slideUp { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: translateY(0); } }
.ca-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid var(--border);
}
.ca-dialog-head h3 { margin: 0; font-size: 16px; font-weight: 700; color: var(--text); }
.ca-dialog-x {
  background: none;
  border: none;
  color: var(--muted);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: all 0.2s;
}
.ca-dialog-x:hover { color: var(--text); background: rgba(0,0,0,0.06); }
.ca-dialog-body {
  padding: 24px;
  overflow-y: auto;
  scrollbar-width: none; /* Firefox */
}
.ca-dialog-body::-webkit-scrollbar {
  display: none; /* Chrome/Safari/Edge */
}
.ca-dialog-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid var(--border);
}

.ca-field { margin-bottom: 16px; }
.ca-field:last-child { margin-bottom: 0; }
.ca-field label { display: block; font-size: 12px; font-weight: 600; color: var(--text-2); margin-bottom: 6px; }
.ca-field .req { color: var(--danger); }
.ca-field input,
.ca-field select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg);
  color: var(--text);
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
}
.ca-field input:focus,
.ca-field select:focus { border-color: var(--primary); }
.ca-field input:disabled { opacity: 0.5; cursor: not-allowed; }

/* 配置页面弹窗专用样式，避免被全局 dark theme 覆盖 */
.ca-config-overlay .ca-config-dialog {
  --dialog-bg: #ffffff;
  --dialog-text: #1a1a1a;
  --dialog-text-2: #5a5a5a;
  --dialog-muted: #999;
  --dialog-border: rgba(0, 0, 0, 0.08);
  --dialog-input-bg: #f5f3f0;
}
.ca-config-overlay .ca-config-dialog {
  background: var(--dialog-bg);
  color: var(--dialog-text);
  border: 1px solid var(--dialog-border);
}
.ca-config-overlay .ca-dialog-head {
  border-bottom-color: var(--dialog-border);
}
.ca-config-overlay .ca-dialog-head h3 {
  color: var(--dialog-text);
}
.ca-config-overlay .ca-dialog-x {
  color: var(--dialog-muted);
}
.ca-config-overlay .ca-dialog-x:hover {
  color: var(--dialog-text);
  background: rgba(0, 0, 0, 0.06);
}
.ca-config-overlay .ca-dialog-foot {
  border-top-color: var(--dialog-border);
}
.ca-config-overlay .ca-field label {
  color: var(--dialog-text-2);
}
.ca-config-overlay .ca-field input,
.ca-config-overlay .ca-field select {
  background: var(--dialog-input-bg);
  color: var(--dialog-text);
  border-color: var(--dialog-border);
}
</style>
