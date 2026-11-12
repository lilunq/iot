<template>
  <div class="ca-panel">
    <div class="ca-toolbar">
      <div class="ca-search">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
          <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
        </svg>
        <input v-model="searchKeyword" type="text" placeholder="搜索分区名称或级联ID" />
      </div>
      <div class="ca-toolbar-right">
        <button class="ca-btn-sync" @click="handleSync" :disabled="syncing">
          <svg v-if="!syncing" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <polyline points="23 4 23 10 17 10"/>
            <polyline points="1 20 1 14 7 14"/>
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
          </svg>
          <svg v-else class="spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <path d="M23 4v6h-6M1 20v-6h6"/>
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
          </svg>
          {{ syncing ? '同步中...' : '一键同步' }}
        </button>
        <button class="ca-btn-ghost" @click="fetchZoneList" title="刷新">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <path d="M23 4v6h-6M1 20v-6h6"/>
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
          </svg>
        </button>
        <button class="ca-btn-primary" @click="openAddDialog">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          新增
        </button>
      </div>
    </div>

    <!-- Zone Cards -->
    <div class="ca-grid" v-if="filteredZoneList.length > 0">
      <article v-for="(zone, idx) in filteredZoneList" :key="zone.id" class="ca-card" :style="{ '--i': idx }">
        <div class="ca-card-top">
          <div class="ca-card-marker" :style="{ background: palette[(zone.id - 1) % palette.length] }"></div>
          <div class="ca-card-identity">
            <h3 class="ca-card-name">{{ zone.name }}</h3>
            <code class="ca-card-cid">{{ zone.cascadeId }}</code>
          </div>
          <span class="ca-card-status" :class="zone.status === 1 ? 'on' : 'off'">
            {{ zone.status === 1 ? '启用' : '禁用' }}
          </span>
        </div>
        <div class="ca-card-meta">
          <div class="ca-meta-item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
              <circle cx="12" cy="12" r="3"/><path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
            </svg>
            <span class="ca-meta-label">坐标</span>
            <span class="ca-meta-val mono">{{ zone.coordX }}, {{ zone.coordY }}, {{ zone.coordZ }}</span>
          </div>
          <div class="ca-meta-item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
              <line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/>
              <line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/>
            </svg>
            <span class="ca-meta-label">排序</span>
            <span class="ca-meta-val">{{ zone.sortOrder }}</span>
          </div>
          <div class="ca-meta-item" v-if="zone.parentId">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
              <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
              <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
            </svg>
            <span class="ca-meta-label">父级</span>
            <span class="ca-meta-val">{{ getParentName(zone.parentId) }}</span>
          </div>
        </div>
        <div class="ca-card-actions">
          <button class="ca-card-btn edit" @click="openEditDialog(zone)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
            编辑
          </button>
          <button class="ca-card-btn del" @click="handleDelete(zone)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
            </svg>
            删除
          </button>
        </div>
      </article>
    </div>

    <div v-else class="ca-empty">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="48" height="48">
        <rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/>
      </svg>
      <p>暂无分区数据</p>
      <button class="ca-btn-primary" @click="openAddDialog">新增分区</button>
    </div>

    <!-- Dialog -->
    <Teleport to="body">
      <div v-if="dialogVisible" class="ca-overlay ca-config-overlay" @click.self="closeDialog">
        <div class="ca-dialog ca-config-dialog">
          <div class="ca-dialog-head">
            <h3>{{ isEdit ? '编辑分区' : '新增分区' }}</h3>
            <button class="ca-dialog-x" @click="closeDialog">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="ca-dialog-body">
            <div class="ca-field">
              <label>分区名称 <span class="req">*</span></label>
              <input v-model="form.name" placeholder="请输入分区名称" />
            </div>
            <div class="ca-field">
              <label>级联ID <span class="req">*</span></label>
              <input v-model="form.cascadeId" placeholder="如 0.001.001.001" />
            </div>
            <div class="ca-field-row">
              <div class="ca-field">
                <label>X坐标</label>
                <input v-model.number="form.coordX" type="number" step="0.001" />
              </div>
              <div class="ca-field">
                <label>Y坐标</label>
                <input v-model.number="form.coordY" type="number" step="0.001" />
              </div>
              <div class="ca-field">
                <label>Z坐标</label>
                <input v-model.number="form.coordZ" type="number" step="0.001" />
              </div>
            </div>
            <div class="ca-field-row">
              <div class="ca-field">
                <label>排序</label>
                <input v-model.number="form.sortOrder" type="number" />
              </div>
              <div class="ca-field">
                <label>状态</label>
                <select v-model.number="form.status" class="ca-select">
                  <option :value="1">启用</option>
                  <option :value="0">禁用</option>
                </select>
              </div>
            </div>
            <div class="ca-field">
              <label>父级区域</label>
              <select v-model="form.parentId" class="ca-select">
                <option :value="0">— 无（根分区）—</option>
                <option v-for="z in availableParents" :key="z.id" :value="z.id">
                  {{ z.name }}（{{ z.cascadeId }}）
                </option>
              </select>
              <small class="ca-field-hint">用于在大屏拓扑图上绘制父子辐射连线</small>
            </div>
          </div>
          <div class="ca-dialog-foot">
            <button class="ca-btn-ghost" @click="closeDialog">取消</button>
            <button class="ca-btn-primary" @click="handleSave">保存</button>
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

const zoneList = ref([])
const searchKeyword = ref('')
const syncing = ref(false)

const palette = ['#6366f1', '#8b5cf6', '#a78bfa', '#3b82f6', '#06b6d4', '#14b8a6', '#22c55e', '#eab308', '#f97316', '#ef4444']

const filteredZoneList = computed(() => {
  if (!searchKeyword.value) return zoneList.value
  const kw = searchKeyword.value.toLowerCase()
  return zoneList.value.filter(z =>
    z.name.toLowerCase().includes(kw) ||
    z.cascadeId.toLowerCase().includes(kw)
  )
})

async function fetchZoneList() {
  try {
    const res = await aiRequest.get('/zoneConfig/list')
    if (res && (res.code === '200' || res.code === 200)) {
      // 兼容后端未实现 parentId：缺失时默认为 0
      zoneList.value = (res.data || []).map(z => ({ ...z, parentId: Number(z.parentId) || 0 }))
      // 缓存区域信息到 localStorage
      try {
        localStorage.setItem('zoneListCache', JSON.stringify(zoneList.value))
      } catch (e) {
        console.warn('缓存区域数据失败:', e)
      }
    }
  } catch (e) {
    console.error('获取分区列表失败:', e)
  }
}

// 加载缓存的区域数据
function loadCachedZoneList() {
  try {
    const cached = localStorage.getItem('zoneListCache')
    if (cached) {
      zoneList.value = JSON.parse(cached)
    }
  } catch (e) {
    console.warn('加载缓存区域数据失败:', e)
  }
}

async function handleSync() {
  syncing.value = true
  try {
    const res = await aiRequest.post('/zoneConfig/sync')
    if (res && (res.code === '200' || res.code === 200)) {
      await fetchZoneList()
      showToast('success', '同步成功', `已同步 ${res.data || 0} 条数据`)
    } else {
      showToast('error', '同步失败', res?.message || '')
    }
  } catch (e) {
    console.error('同步失败:', e)
    showToast('error', '同步失败', e.message || '')
  } finally {
    syncing.value = false
  }
}

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({ id: '', name: '', cascadeId: '', coordX: 0, coordY: 0, coordZ: 0, sortOrder: 0, status: 1, parentId: 0 })

function getParentName(pid) {
  if (!pid) return '—'
  const found = zoneList.value.find(z => Number(z.id) === Number(pid))
  return found ? found.name : `#${pid}`
}

// 编辑时可作为父级的候选区域列表（排除自身及其后代，防止成环）
const availableParents = computed(() => {
  if (!isEdit.value || !form.value.id) return zoneList.value.filter(z => z.status === 1)
  const descendants = collectDescendants(form.value.id)
  descendants.add(form.value.id)
  return zoneList.value.filter(z => z.status === 1 && !descendants.has(z.id))
})

function collectDescendants(rootId) {
  const result = new Set()
  const stack = [rootId]
  while (stack.length) {
    const cur = stack.pop()
    zoneList.value.forEach(z => {
      const pid = Number(z.parentId) || 0
      if (pid === Number(cur) && !result.has(z.id)) {
        result.add(z.id)
        stack.push(z.id)
      }
    })
  }
  return result
}

function openAddDialog() {
  isEdit.value = false
  form.value = { id: '', name: '', cascadeId: '', coordX: 0, coordY: 0, coordZ: 0, sortOrder: 0, status: 1, parentId: 0 }
  dialogVisible.value = true
}

function openEditDialog(zone) {
  isEdit.value = true
  form.value = { ...zone, parentId: Number(zone.parentId) || 0 }
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
}

async function handleSave() {
  if (!form.value.name || !form.value.cascadeId) {
    showToast('error', '分区名称和级联ID不能为空')
    return
  }
  try {
    let res
    if (isEdit.value) {
      res = await aiRequest.post('/zoneConfig/update', form.value)
    } else {
      res = await aiRequest.post('/zoneConfig/add', form.value)
    }
    if (res && (res.code === '200' || res.code === 200)) {
      closeDialog()
      await fetchZoneList()
      // 通知同标签页其他组件刷新（storage 事件只在跨标签页触发）
      window.dispatchEvent(new CustomEvent('zone-config-changed'))
      showToast('success', '保存成功')
    } else {
      showToast('error', res?.message || '保存失败')
    }
  } catch (e) {
    console.error('保存失败:', e)
    showToast('error', '保存失败: ' + (e.message || ''))
  }
}

async function handleDelete(zone) {
  if (!confirm(`确定删除分区 "${zone.name}" 吗？`)) return
  try {
    const res = await aiRequest.post('/zoneConfig/delete', null, { params: { id: zone.id } })
    if (res && (res.code === '200' || res.code === 200)) {
      await fetchZoneList()
      showToast('success', '删除成功')
    } else {
      showToast('error', res?.message || '删除失败')
    }
  } catch (e) {
    console.error('删除失败:', e)
    showToast('error', '删除失败')
  }
}

onMounted(() => {
  loadCachedZoneList()
  fetchZoneList()
})
</script>

<style scoped>
/* ===== Design Tokens ===== */
.config-admin {
  --bg: #f5f3f0;
  --card-bg: #ffffff;
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
.ca-btn-ghost:hover { border-color: rgba(255,255,255,0.15); }
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
.ca-btn-sync {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid rgba(34,197,94,0.3);
  border-radius: 8px;
  background: rgba(34,197,94,0.08);
  color: var(--success);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.ca-btn-sync:hover:not(:disabled) { background: rgba(34,197,94,0.15); }
.ca-btn-sync:disabled { opacity: 0.6; cursor: not-allowed; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.ca-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
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
.ca-meta-item { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-2); }
.ca-meta-item svg { color: var(--muted); flex-shrink: 0; }
.ca-meta-label { color: var(--muted); min-width: 40px; }
.ca-meta-val { color: var(--text-2); }
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
.ca-dialog-body { padding: 24px; overflow-y: auto; }
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
.ca-field textarea,
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
.ca-field textarea:focus,
.ca-field select:focus { border-color: var(--primary); }
.ca-field textarea { resize: vertical; min-height: 56px; }
.ca-field-hint { display: block; margin-top: 4px; font-size: 11px; color: var(--muted); }
.ca-field-row { display: flex; gap: 12px; }
.ca-field-row .ca-field { flex: 1; }

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
.ca-config-overlay .ca-field textarea,
.ca-config-overlay .ca-field select {
  background: var(--dialog-input-bg);
  color: var(--dialog-text);
  border-color: var(--dialog-border);
}
</style>
