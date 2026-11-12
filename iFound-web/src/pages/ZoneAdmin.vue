<template>
  <div class="config-admin" data-theme="dark">
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
      <!-- Sidebar -->
      <aside class="ca-sidebar">
        <nav class="ca-nav">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            class="ca-nav-item"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            <component :is="tab.icon" />
            <span>{{ tab.label }}</span>
          </button>
        </nav>
      </aside>

      <!-- Content -->
      <main class="ca-main">
        <!-- Zone Config Panel -->
        <div v-if="activeTab === 'zone'" class="ca-panel">
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
        </div>

        <!-- Voice Permission Panel -->
        <div v-else-if="activeTab === 'voice'" class="ca-panel">
          <div class="ca-toolbar">
            <div class="ca-search">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
              </svg>
              <input v-model="voiceSearch" type="text" placeholder="搜索用户名称" />
            </div>
            <div class="ca-toolbar-right">
              <button class="ca-btn-ghost" @click="fetchVoiceList" title="刷新">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                  <path d="M23 4v6h-6M1 20v-6h6"/>
                  <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
                </svg>
              </button>
              <button class="ca-btn-primary" @click="openVoiceAdd">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                  <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
                </svg>
                新增用户
              </button>
            </div>
          </div>

          <div class="ca-voice-grid" v-if="filteredVoiceList.length > 0">
            <article v-for="(vp, idx) in filteredVoiceList" :key="vp.id" class="ca-voice-card" :class="{ 'is-stranger': vp.id === '-1' }" :style="{ '--i': idx }">
              <div class="ca-voice-card-top">
                <div class="ca-voice-avatar" :style="{ background: vp.id === '-1' ? '#6b7280' : palette[(idx - 1 + palette.length) % palette.length] }">
                  <svg v-if="vp.id === '-1'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
                  </svg>
                </div>
                <div class="ca-voice-info">
                  <h3 class="ca-voice-name">{{ vp.name }}</h3>
                  <span v-if="vp.id === '-1'" class="ca-voice-badge stranger">陌生人</span>
                  <span v-else class="ca-voice-badge user">已注册</span>
                </div>
              </div>
              <div class="ca-voice-perms">
                <label class="ca-perm-toggle" :class="{ active: vp.canAwake }">
                  <input type="checkbox" :checked="vp.canAwake" @change="toggleVoicePerm(vp, 'canAwake', $event)" :disabled="savingVoice" />
                  <span class="ca-perm-track"></span>
                  <span class="ca-perm-label">允许唤醒</span>
                </label>
                <label class="ca-perm-toggle" :class="{ active: vp.canYk }">
                  <input type="checkbox" :checked="vp.canYk" @change="toggleVoicePerm(vp, 'canYk', $event)" :disabled="savingVoice" />
                  <span class="ca-perm-track"></span>
                  <span class="ca-perm-label">允许遥控</span>
                </label>
              </div>
              <div class="ca-voice-card-actions">
                <button class="ca-btn-icon" @click="openVoiceEdit(vp)" :disabled="vp.id === '-1'">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                  </svg>
                </button>
                <button class="ca-btn-icon ca-btn-danger" @click="deleteVoice(vp)" :disabled="vp.id === '-1'">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                    <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                  </svg>
                </button>
              </div>
            </article>
          </div>

          <div v-else class="ca-empty">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="48" height="48">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
              <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
              <line x1="12" y1="19" x2="12" y2="23"/>
              <line x1="8" y1="23" x2="16" y2="23"/>
            </svg>
            <p>暂无语音权限数据</p>
            <button class="ca-btn-primary" @click="openVoiceAdd">新增用户</button>
          </div>
        </div>

        <!-- Placeholder for future configs -->
        <div v-else class="ca-placeholder">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="56" height="56">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
          </svg>
          <h3>{{ tabs.find(t => t.key === activeTab)?.label }} 配置</h3>
          <p>该配置模块正在开发中</p>
        </div>
      </main>
    </div>

    <!-- Toast -->
    <Toast
      v-if="toast.show"
      :type="toast.type"
      :title="toast.title"
      :description="toast.description"
      :duration="toast.duration"
      @close="toast.show = false"
    />

    <!-- Zone Dialog -->
    <Teleport to="body">
      <div v-if="dialogVisible" class="ca-overlay" @click.self="closeDialog">
        <div class="ca-dialog">
          <div class="ca-dialog-head">
            <h3>{{ isEdit ? '编辑分区' : '新增分区' }}</h3>
            <button class="ca-dialog-x" @click="closeDialog">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <path d="M18 6L6 18M6 6l12 12"/>
              </svg>
            </button>
          </div>
          <div class="ca-dialog-body">
            <div class="ca-field">
              <label>分区名称 <span class="req">*</span></label>
              <input v-model="formData.name" type="text" placeholder="如：候车厅A区" />
            </div>
            <div class="ca-field">
              <label>级联ID <span class="req">*</span></label>
              <input v-model="formData.cascadeId" type="text" placeholder="如 0.001.001.001.001" />
            </div>
            <div class="ca-field">
              <label>父级ID</label>
              <input v-model="formData.parentId" type="text" placeholder="顶级分区留空" />
            </div>
            <div class="ca-field-row">
              <div class="ca-field">
                <label>X</label>
                <input v-model.number="formData.coordX" type="number" step="0.01" />
              </div>
              <div class="ca-field">
                <label>Y</label>
                <input v-model.number="formData.coordY" type="number" step="0.01" />
              </div>
              <div class="ca-field">
                <label>Z</label>
                <input v-model.number="formData.coordZ" type="number" step="0.01" />
              </div>
            </div>
            <div class="ca-field-row">
              <div class="ca-field">
                <label>排序</label>
                <input v-model.number="formData.sortOrder" type="number" />
              </div>
              <div class="ca-field">
                <label>状态</label>
                <label class="ca-switch">
                  <input type="checkbox" v-model="formData.status" :true-value="1" :false-value="0" />
                  <span class="ca-switch-track"></span>
                </label>
                <span class="ca-switch-label">{{ formData.status === 1 ? '启用' : '禁用' }}</span>
              </div>
            </div>
            <div class="ca-field">
              <label>备注</label>
              <textarea v-model="formData.remark" rows="2" placeholder="可选"></textarea>
            </div>
          </div>
          <div class="ca-dialog-foot">
            <button class="ca-btn-ghost" @click="closeDialog">取消</button>
            <button class="ca-btn-primary" @click="handleSave">保存</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Voice Permission Dialog -->
    <Teleport to="body">
      <div v-if="voiceDialogVisible" class="ca-overlay" @click.self="voiceDialogVisible = false">
        <div class="ca-dialog">
          <div class="ca-dialog-head">
            <h3>{{ voiceForm.id ? '编辑用户' : '新增用户' }}</h3>
            <button class="ca-dialog-x" @click="voiceDialogVisible = false">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="ca-dialog-body">
            <div class="ca-field">
              <label>用户名称</label>
              <input v-model="voiceForm.name" placeholder="请输入用户名称" />
            </div>
            <div class="ca-field" v-if="voiceForm.id">
              <label>声纹特征</label>
              <textarea v-model="voiceForm.embedding" rows="3" placeholder="声纹特征（base64编码）"></textarea>
            </div>
            <div class="ca-field">
              <label class="ca-perm-toggle" :class="{ active: voiceForm.canAwake }">
                <input type="checkbox" v-model="voiceForm.canAwake" />
                <span class="ca-perm-track"></span>
                <span class="ca-perm-label">允许唤醒</span>
              </label>
            </div>
            <div class="ca-field">
              <label class="ca-perm-toggle" :class="{ active: voiceForm.canYk }">
                <input type="checkbox" v-model="voiceForm.canYk" />
                <span class="ca-perm-track"></span>
                <span class="ca-perm-label">允许遥控</span>
              </label>
            </div>
          </div>
          <div class="ca-dialog-foot">
            <button class="ca-btn-ghost" @click="voiceDialogVisible = false">取消</button>
            <button class="ca-btn-primary" @click="handleVoiceSave" :disabled="savingVoice">
              {{ savingVoice ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, h } from 'vue'
import { useRouter } from 'vue-router'
import { aiRequest } from '@/utils/request'
import Toast from '@/components/Toast.vue'

const router = useRouter()
const activeTab = ref('zone')
const zoneList = ref([])
const searchKeyword = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const formData = ref(getEmptyForm())
const syncing = ref(false)
const toast = ref({ show: false, type: 'info', title: '', description: '', duration: 3000 })

function showToast(type, title, description = '', duration = 3000) {
  toast.value = { show: true, type, title, description, duration }
}

const palette = [
  '#1268ff', '#08ae66', '#f59e0b', '#ef4444',
  '#8b5cf6', '#06b6d4', '#ec4899', '#10b981',
]

// Tab definitions - easy to extend
const tabs = [
  {
    key: 'zone',
    label: '分区配置',
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '18', height: '18' }, [
      h('path', { d: 'M12 2L2 7l10 5 10-5-10-5z' }),
      h('path', { d: 'M2 17l10 5 10-5' }),
      h('path', { d: 'M2 12l10 5 10-5' }),
    ]),
  },
  {
    key: 'voice',
    label: '语音权限',
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '18', height: '18' }, [
      h('path', { d: 'M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z' }),
      h('path', { d: 'M19 10v2a7 7 0 0 1-14 0v-2' }),
      h('line', { x1: '12', y1: '19', x2: '12', y2: '23' }),
      h('line', { x1: '8', y1: '23', x2: '16', y2: '23' }),
    ]),
  },
]

function getEmptyForm() {
  return {
    id: null, orgId: '3', parentId: '', name: '', cascadeId: '',
    coordX: 0, coordY: 0, coordZ: 0, sortOrder: 0, status: 1, remark: ''
  }
}

const filteredZoneList = computed(() => {
  if (!searchKeyword.value) return zoneList.value
  const kw = searchKeyword.value.toLowerCase()
  return zoneList.value.filter(z =>
    z.name.toLowerCase().includes(kw) || z.cascadeId.toLowerCase().includes(kw)
  )
})

function goBack() { router.push('/') }

async function fetchZoneList() {
  try {
    const res = await aiRequest.get('/zone/list', { params: { orgId: '3' } })
    if (res && res.code === '200') zoneList.value = res.data || []
  } catch (e) { console.error('获取分区列表失败:', e) }
}

async function handleSync() {
  syncing.value = true
  try {
    const res = await aiRequest.post('/zone/sync', null, { params: { orgId: '3' } })
    if (res && res.code === '200') {
      const { addCount, updateCount } = res.data || {}
      showToast('success', '同步成功', `新增 ${addCount} 条，更新 ${updateCount} 条`)
      await fetchZoneList()
    } else {
      showToast('error', '同步失败', res.msg)
    }
  } catch (e) {
    console.error('同步失败:', e)
    showToast('error', '同步失败')
  } finally {
    syncing.value = false
  }
}

function openAddDialog() {
  isEdit.value = false
  formData.value = getEmptyForm()
  dialogVisible.value = true
}

function openEditDialog(zone) {
  isEdit.value = true
  formData.value = { ...zone }
  dialogVisible.value = true
}

function closeDialog() { dialogVisible.value = false }

async function handleSave() {
  if (!formData.value.name || !formData.value.cascadeId) {
    showToast('error', '提示', '分区名称和级联ID不能为空')
    return
  }
  try {
    const url = isEdit.value ? '/zone/update' : '/zone/add'
    const res = await aiRequest.post(url, formData.value)
    if (res && res.code === '200') {
      showToast('success', '保存成功')
      await fetchZoneList()
      closeDialog()
    } else {
      showToast('error', '操作失败', res.msg)
    }
  } catch (e) {
    console.error('保存失败:', e)
    showToast('error', '保存失败')
  }
}

async function handleDelete(zone) {
  if (!confirm(`确定删除「${zone.name}」？`)) return
  try {
    const res = await aiRequest.post('/zone/delete', null, { params: { id: zone.id } })
    if (res && res.code === '200') {
      showToast('success', '删除成功')
      await fetchZoneList()
    } else {
      showToast('error', '删除失败', res.msg)
    }
  } catch (e) {
    console.error('删除失败:', e)
    showToast('error', '删除失败')
  }
}

// ========== Voice Permission ==========
const voiceList = ref([])
const voiceSearch = ref('')
const voiceDialogVisible = ref(false)
const savingVoice = ref(false)
const voiceForm = ref({ id: '', name: '', canAwake: false, canYk: false, embedding: '' })

const filteredVoiceList = computed(() => {
  if (!voiceSearch.value) return voiceList.value
  const kw = voiceSearch.value.toLowerCase()
  return voiceList.value.filter(v => v.name.toLowerCase().includes(kw))
})

async function fetchVoiceList() {
  try {
    const res = await aiRequest.get('/api/speaker-permissions')
    if (res && res.code === 200) {
      voiceList.value = res.data || []
    }
  } catch (e) {
    console.error('获取语音权限列表失败:', e)
  }
}

function openVoiceAdd() {
  voiceForm.value = { id: '', name: '', canAwake: false, canYk: false, embedding: '' }
  voiceDialogVisible.value = true
}

function openVoiceEdit(vp) {
  voiceForm.value = { ...vp }
  voiceDialogVisible.value = true
}

async function handleVoiceSave() {
  if (!voiceForm.value.name) {
    showToast('error', '用户名称不能为空')
    return
  }
  if (!voiceForm.value.id && !voiceForm.value.embedding) {
    showToast('error', '声纹特征不能为空')
    return
  }
  savingVoice.value = true
  try {
    const payload = { ...voiceForm.value }
    let res
    if (payload.id) {
      res = await aiRequest.put(`/api/speaker-permissions/${payload.id}`, payload)
    } else {
      res = await aiRequest.post('/api/speaker-permissions', payload)
    }
    if (res && res.code === 200) {
      voiceDialogVisible.value = false
      await fetchVoiceList()
      showToast('success', '保存成功')
    } else {
      showToast('error', res?.message || '保存失败')
    }
  } catch (e) {
    console.error('保存语音权限失败:', e)
    showToast('error', '保存失败: ' + (e.message || ''))
  } finally {
    savingVoice.value = false
  }
}

async function toggleVoicePerm(vp, field, event) {
  const newVal = event.target.checked
  const payload = { ...vp, [field]: newVal }
  try {
    const res = await aiRequest.put(`/api/speaker-permissions/${vp.id}`, payload)
    if (res && res.code === 200) {
      vp[field] = newVal
      showToast('success', '权限已更新')
    } else {
      event.target.checked = !newVal
      showToast('error', '更新失败')
    }
  } catch (e) {
    event.target.checked = !newVal
    console.error('更新权限失败:', e)
  }
}

async function deleteVoice(vp) {
  if (vp.id === '-1') return
  if (!confirm(`确定删除用户 "${vp.name}" 的语音权限吗？`)) return
  try {
    const res = await aiRequest.delete(`/api/speaker-permissions/${vp.id}`)
    if (res && res.code === 200) {
      await fetchVoiceList()
      showToast('success', '删除成功')
    } else {
      showToast('error', res?.message || '删除失败')
    }
  } catch (e) {
    console.error('删除语音权限失败:', e)
    showToast('error', '删除失败')
  }
}

onMounted(() => fetchZoneList())
</script>

<style scoped>
/* ---------- tokens ---------- */
.config-admin {
  --bg: #080e1a;
  --surface: rgba(14, 26, 48, 0.7);
  --surface-hover: rgba(18, 34, 60, 0.85);
  --sidebar-bg: rgba(10, 18, 34, 0.95);
  --border: rgba(255, 255, 255, 0.06);
  --border-hover: rgba(18, 104, 255, 0.35);
  --text: #e8edf5;
  --text-2: #7a8ba7;
  --accent: #1268ff;
  --accent-soft: rgba(18, 104, 255, 0.12);
  --danger: #ef4444;
  --success: #08ae66;
  --radius: 12px;
  --radius-sm: 8px;
  --mono: 'SF Mono', 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  --sidebar-w: 200px;

  min-height: 100vh;
  background: var(--bg);
  color: var(--text);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

/* ---------- header ---------- */
.ca-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(8, 14, 26, 0.88);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-bottom: 1px solid var(--border);
}

.ca-header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
}

.ca-brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.ca-logo {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--accent);
  display: grid;
  place-items: center;
  color: #fff;
}

.ca-titles h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text);
}

.ca-sub {
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--text-2);
  font-family: var(--mono);
}

/* ---------- buttons ---------- */
.ca-btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-2);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.ca-btn-ghost:hover {
  border-color: var(--border-hover);
  background: var(--accent-soft);
}

.ca-btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  background: var(--accent);
  border: none;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.ca-btn-primary:hover {
  background: #0d5ce6;
  transform: translateY(-1px);
}

.ca-btn-primary:active {
  transform: translateY(0) scale(0.98);
}

.ca-btn-sync {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: linear-gradient(135deg, #08ae66, #059669);
  border: none;
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.ca-btn-sync:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(8, 174, 102, 0.3);
}

.ca-btn-sync:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---------- layout ---------- */
.ca-layout {
  display: flex;
  min-height: calc(100vh - 73px);
}

/* ---------- sidebar ---------- */
.ca-sidebar {
  width: var(--sidebar-w);
  flex-shrink: 0;
  background: var(--sidebar-bg);
  border-right: 1px solid var(--border);
  padding: 20px 0;
}

.ca-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0 12px;
}

.ca-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--text-2);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s ease;
  text-align: left;
}

.ca-nav-item:hover {
  background: rgba(255, 255, 255, 0.04);
  color: var(--text);
}

.ca-nav-item.active {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

.ca-nav-item svg {
  flex-shrink: 0;
}

/* ---------- main ---------- */
.ca-main {
  flex: 1;
  padding: 28px 32px 60px;
  min-width: 0;
}

.ca-panel {
  animation: fade 0.2s ease;
}

@keyframes fade {
  from { opacity: 0; }
  to { opacity: 1; }
}

.ca-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}

.ca-search {
  flex: 1;
  max-width: 360px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  transition: border-color 0.15s;
}

.ca-search:focus-within {
  border-color: var(--accent);
}

.ca-search svg { color: var(--text-2); flex-shrink: 0; }

.ca-search input {
  flex: 1;
  background: none;
  border: none;
  outline: none;
  color: var(--text);
  font-size: 14px;
}

.ca-search input::placeholder { color: var(--text-2); }

.ca-toolbar-right {
  display: flex;
  gap: 10px;
}

/* ---------- card grid ---------- */
.ca-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 18px;
}

.ca-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  transition: all 0.2s ease;
  animation: card-in 0.35s ease both;
  animation-delay: calc(var(--i) * 40ms);
}

@keyframes card-in {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.ca-card:hover {
  border-color: var(--border-hover);
  background: var(--surface-hover);
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25);
}

.ca-card-top {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 18px 14px;
}

.ca-card-marker {
  width: 4px;
  height: 36px;
  border-radius: 4px;
  flex-shrink: 0;
}

.ca-card-identity {
  flex: 1;
  min-width: 0;
}

.ca-card-name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ca-card-cid {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--text-2);
  background: rgba(255, 255, 255, 0.04);
  padding: 2px 6px;
  border-radius: 4px;
}

.ca-card-status {
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 100px;
  flex-shrink: 0;
}

.ca-card-status.on {
  background: rgba(8, 174, 102, 0.12);
  color: var(--success);
}

.ca-card-status.off {
  background: rgba(239, 68, 68, 0.12);
  color: var(--danger);
}

.ca-card-meta {
  padding: 0 18px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ca-meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.ca-meta-item svg { color: var(--text-2); flex-shrink: 0; }

.ca-meta-label {
  color: var(--text-2);
  min-width: 36px;
}

.ca-meta-val {
  color: var(--text);
}

.ca-meta-val.mono {
  font-family: var(--mono);
  font-size: 12px;
}

.ca-card-actions {
  display: flex;
  border-top: 1px solid var(--border);
}

.ca-card-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 11px;
  background: transparent;
  border: none;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
  color: var(--text-2);
}

.ca-card-btn.edit { border-right: 1px solid var(--border); }

.ca-card-btn.edit:hover {
  background: var(--accent-soft);
  color: var(--accent);
}

.ca-card-btn.del:hover {
  background: rgba(239, 68, 68, 0.08);
  color: var(--danger);
}

/* ---------- empty / placeholder ---------- */
.ca-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 0;
  gap: 16px;
  color: var(--text-2);
}

.ca-empty svg { opacity: 0.4; }

.ca-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120px 0;
  gap: 12px;
  color: var(--text-2);
}

.ca-placeholder svg { opacity: 0.3; }

.ca-placeholder h3 {
  margin: 0;
  font-size: 18px;
  color: var(--text);
  font-weight: 600;
}

.ca-placeholder p {
  margin: 0;
  font-size: 14px;
}

/* ---------- voice permission ---------- */
.ca-voice-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.ca-voice-card {
  background: var(--card-bg);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  animation: cardIn 0.3s ease both;
  animation-delay: calc(var(--i) * 0.05s);
}
.ca-voice-card.is-stranger {
  border-color: rgba(107,114,128,0.3);
  background: rgba(107,114,128,0.05);
}
.ca-voice-card-top {
  display: flex;
  align-items: center;
  gap: 12px;
}
.ca-voice-avatar {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.ca-voice-info {
  flex: 1;
  min-width: 0;
}
.ca-voice-name {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
}
.ca-voice-badge {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 20px;
  font-weight: 600;
}
.ca-voice-badge.stranger {
  background: rgba(107,114,128,0.15);
  color: #9ca3af;
}
.ca-voice-badge.user {
  background: rgba(99,102,241,0.15);
  color: #818cf8;
}
.ca-voice-perms {
  display: flex;
  gap: 16px;
}
.ca-perm-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-2);
  user-select: none;
}
.ca-perm-toggle input {
  display: none;
}
.ca-perm-track {
  width: 36px;
  height: 20px;
  border-radius: 10px;
  background: var(--border);
  position: relative;
  transition: background 0.2s;
  flex-shrink: 0;
}
.ca-perm-track::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.2s;
}
.ca-perm-toggle.active .ca-perm-track {
  background: #6366f1;
}
.ca-perm-toggle.active .ca-perm-track::after {
  transform: translateX(16px);
}
.ca-perm-label {
  font-weight: 500;
}
.ca-voice-card-actions {
  display: flex;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}
.ca-btn-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: transparent;
  color: var(--text-2);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.ca-btn-icon:hover:not(:disabled) {
  border-color: #6366f1;
  color: #6366f1;
}
.ca-btn-icon.ca-btn-danger:hover:not(:disabled) {
  border-color: #ef4444;
  color: #ef4444;
}
.ca-btn-icon:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

/* ---------- overlay & dialog ---------- */
.ca-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: rgba(0, 0, 0, 0.72);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  display: grid;
  place-items: center;
  animation: fade 0.15s ease;
}

.ca-dialog {
  background: #0e1a30;
  border: 1px solid var(--border-hover);
  border-radius: 16px;
  width: 480px;
  max-height: 88vh;
  overflow-y: auto;
  animation: slide 0.2s ease;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.5);
}

@keyframes slide {
  from { opacity: 0; transform: translateY(16px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.ca-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
}

.ca-dialog-head h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

.ca-dialog-x {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text-2);
  cursor: pointer;
  transition: all 0.15s;
}

.ca-dialog-x:hover {
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
  color: var(--danger);
}

.ca-dialog-body { padding: 24px; }

.ca-field {
  margin-bottom: 16px;
}

.ca-field label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--text-2);
  font-weight: 500;
}

.ca-field .req { color: var(--danger); }

.ca-field input,
.ca-field textarea {
  width: 100%;
  padding: 10px 14px;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text);
  font-size: 14px;
  transition: border-color 0.15s;
  box-sizing: border-box;
}

.ca-field input:focus,
.ca-field textarea:focus {
  outline: none;
  border-color: var(--accent);
}

.ca-field textarea { resize: vertical; min-height: 56px; }

.ca-field-row {
  display: flex;
  gap: 12px;
}

.ca-field-row .ca-field { flex: 1; }

/* switch */
.ca-field .ca-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  vertical-align: middle;
  margin-right: 8px;
}

.ca-switch input { opacity: 0; width: 0; height: 0; }

.ca-switch-track {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 24px;
  cursor: pointer;
  transition: 0.2s;
}

.ca-switch-track::before {
  content: '';
  position: absolute;
  width: 18px;
  height: 18px;
  left: 3px;
  bottom: 3px;
  background: #fff;
  border-radius: 50%;
  transition: 0.2s;
}

.ca-switch input:checked + .ca-switch-track {
  background: var(--accent);
}

.ca-switch input:checked + .ca-switch-track::before {
  transform: translateX(20px);
}

.ca-switch-label {
  font-size: 13px;
  color: var(--text-2);
}

.ca-dialog-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid var(--border);
}

/* ---------- responsive ---------- */
@media (max-width: 768px) {
  .ca-header-inner { padding: 14px 16px; }
  .ca-layout { flex-direction: column; }
  .ca-sidebar {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--border);
    padding: 12px 0;
  }
  .ca-nav {
    flex-direction: row;
    overflow-x: auto;
    padding: 0 12px;
  }
  .ca-nav-item { white-space: nowrap; }
  .ca-main { padding: 20px 16px 48px; }
  .ca-toolbar { flex-direction: column; }
  .ca-search { max-width: none; }
  .ca-grid { grid-template-columns: 1fr; }
  .ca-dialog { width: calc(100vw - 32px); }
}
</style>
