<template>
  <div class="ca-panel">
    <div class="ca-toolbar">
      <div class="ca-search">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
          <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
        </svg>
        <input v-model="voiceSearch" type="text" placeholder="搜索用户名称" />
      </div>
      <div class="ca-toolbar-right">
        <button
          class="ca-btn-ghost ca-auto-btn"
          :class="{ 'is-on': autoControl.enabled }"
          :disabled="autoControlLoading"
          @click="toggleAutoControl"
          :title="autoControl.enabled ? '点击关闭自动控制' : '点击开启自动控制'"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
          </svg>
          自动控制：{{ autoControl.enabled ? '已开启' : '已关闭' }}
        </button>
        <button class="ca-btn-ghost" @click="handleSync" :disabled="syncing">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          {{ syncing ? '同步中...' : '一键同步' }}
        </button>
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
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
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

    <!-- Voice Dialog -->
    <Teleport to="body">
      <div v-if="voiceDialogVisible" class="ca-overlay ca-config-overlay" @click.self="voiceDialogVisible = false">
        <div class="ca-dialog ca-config-dialog">
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
            <div class="ca-field">
              <label>组织ID</label>
              <input v-model="voiceForm.orgId" placeholder="请输入组织ID" />
            </div>
            <div class="ca-field">
              <label>声纹特征</label>
              <div class="ca-embedding-row">
                <span class="ca-embedding-badge" :class="voiceForm.embedding ? 'recorded' : 'unrecorded'">
                  {{ voiceForm.embedding ? '已录入' : '未录入' }}
                </span>
                <button class="ca-btn-icon" @click="openVoiceprintDialog" title="录入声纹">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                    <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                    <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                    <line x1="12" y1="19" x2="12" y2="23"/>
                    <line x1="8" y1="23" x2="16" y2="23"/>
                  </svg>
                  录入
                </button>
                <button class="ca-btn-icon ca-btn-danger" @click="clearEmbedding" :disabled="!voiceForm.embedding" title="删除声纹">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                    <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                  </svg>
                </button>
              </div>
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

    <!-- 录入声纹 Dialog -->
    <Teleport to="body">
      <div v-if="voiceprintDialogVisible" class="ca-overlay ca-config-overlay" @click.self="closeVoiceprintDialog">
        <div class="ca-dialog ca-config-dialog ca-dialog-voiceprint">
          <div class="ca-dialog-head">
            <h3>录入声纹</h3>
            <button class="ca-dialog-x" @click="closeVoiceprintDialog">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="ca-dialog-body">
            <p class="ca-vp-hint">请确保环境安静后，点击录音按钮，并朗读以下文字：</p>
            <div class="ca-vp-text-card">
              现在开始录入声纹，接下来我将朗读数字：一二三四五六七八九十，接下来我将朗读字母：ABCDEFG
            </div>
            <div class="ca-vp-actions">
              <button class="ca-vp-record-btn" :class="{ recording: isRecording }" @click="toggleRecording">
                <span class="ca-vp-record-dot" v-if="isRecording"></span>
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                  <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                  <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                  <line x1="12" y1="19" x2="12" y2="23"/>
                  <line x1="8" y1="23" x2="16" y2="23"/>
                </svg>
                {{ isRecording ? '停止录音' : '开始录音' }}
              </button>
              <button class="ca-btn-primary" @click="submitVoiceprint" :disabled="!hasRecorded || isSubmitting">
                {{ isSubmitting ? '提交中...' : '确定' }}
              </button>
            </div>
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

const voiceList = ref([])
const voiceSearch = ref('')
const voiceDialogVisible = ref(false)
const savingVoice = ref(false)
const syncing = ref(false)
const voiceForm = ref({ id: '', name: '', orgId: '', canAwake: false, canYk: false, embedding: '' })

// 自动控制状态（Redis存储，场景2 energy-agent 上报任务自动采纳）
const autoControl = ref({ enabled: false, username: '' })
const autoControlLoading = ref(false)

// 录入声纹相关状态
const voiceprintDialogVisible = ref(false)
const isRecording = ref(false)
const hasRecorded = ref(false)
const isSubmitting = ref(false)
const audioChunks = []
let audioContext = null
let mediaStream = null
let scriptProcessor = null
let sourceNode = null

const palette = ['#6366f1', '#8b5cf6', '#a78bfa', '#3b82f6', '#06b6d4', '#14b8a6', '#22c55e', '#eab308', '#f97316', '#ef4444']

const filteredVoiceList = computed(() => {
  if (!voiceSearch.value) return voiceList.value
  const kw = voiceSearch.value.toLowerCase()
  return voiceList.value.filter(v => v.name.toLowerCase().includes(kw))
})

async function fetchVoiceList() {
  try {
    const res = await aiRequest.get('/speaker-permissions')
    if (res && res.code === 200) {
      voiceList.value = res.data || []
    }
  } catch (e) {
    console.error('获取语音权限列表失败:', e)
  }
}

async function fetchAutoControl() {
  try {
    const res = await aiRequest.get('/collab/auto-control/status')
    if (res && (res.code === 200 || res.code === '200') && res.data) {
      autoControl.value = { enabled: !!res.data.enabled, username: res.data.username || '' }
    }
  } catch (e) {
    console.error('获取自动控制状态失败:', e)
  }
}

async function toggleAutoControl() {
  const next = !autoControl.value.enabled
  const action = next ? '开启' : '关闭'
  const tip = next
    ? '开启后，能耗Agent（energy-agent）上报的任务将自动采纳，无需人工确认。确定要开启自动控制吗？'
    : '确定要关闭自动控制吗？'
  if (!confirm(tip)) return
  autoControlLoading.value = true
  try {
    const res = await aiRequest.post('/collab/auto-control/set', {
      enabled: next,
      username: autoControl.value.username || 'admin'
    })
    if (res && (res.code === 200 || res.code === '200')) {
      autoControl.value.enabled = next
      showToast('success', `已${action}自动控制`)
    } else {
      showToast('error', res?.msg || res?.message || `${action}失败`)
    }
  } catch (e) {
    console.error('切换自动控制失败:', e)
    showToast('error', `${action}失败: ` + (e.message || ''))
  } finally {
    autoControlLoading.value = false
  }
}

function openVoiceAdd() {
  voiceForm.value = { id: '', name: '', orgId: '', canAwake: false, canYk: false, embedding: '' }
  voiceDialogVisible.value = true
}

function openVoiceEdit(vp) {
  voiceForm.value = { ...vp }
  voiceDialogVisible.value = true
}

// ===== 录入声纹 =====
function openVoiceprintDialog() {
  voiceprintDialogVisible.value = true
  isRecording.value = false
  hasRecorded.value = false
  isSubmitting.value = false
  audioChunks.length = 0
}

function clearEmbedding() {
  voiceForm.value.embedding = ''
}

function closeVoiceprintDialog() {
  voiceprintDialogVisible.value = false
  stopRecording()
}

async function toggleRecording() {
  if (isRecording.value) {
    stopRecording()
  } else {
    await startRecording()
  }
}

async function startRecording() {
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: { sampleRate: 16000, channelCount: 1, echoCancellation: true, noiseSuppression: true }
    })
    audioContext = new AudioContext({ sampleRate: 16000 })
    sourceNode = audioContext.createMediaStreamSource(mediaStream)
    // 使用 ScriptProcessorNode 采集原始 PCM 数据
    scriptProcessor = audioContext.createScriptProcessor(4096, 1, 1)
    audioChunks.length = 0
    hasRecorded.value = false

    scriptProcessor.onaudioprocess = (e) => {
      if (isRecording.value) {
        const data = e.inputBuffer.getChannelData(0)
        audioChunks.push(new Float32Array(data))
      }
    }

    sourceNode.connect(scriptProcessor)
    scriptProcessor.connect(audioContext.destination)
    isRecording.value = true
  } catch (e) {
    console.error('启动录音失败:', e)
    showToast('error', '无法访问麦克风，请检查权限设置')
  }
}

function stopRecording() {
  if (scriptProcessor) {
    scriptProcessor.disconnect()
    scriptProcessor = null
  }
  if (sourceNode) {
    sourceNode.disconnect()
    sourceNode = null
  }
  if (audioContext) {
    audioContext.close()
    audioContext = null
  }
  if (mediaStream) {
    mediaStream.getTracks().forEach(t => t.stop())
    mediaStream = null
  }
  if (isRecording.value) {
    isRecording.value = false
    if (audioChunks.length > 0) {
      hasRecorded.value = true
    }
  }
}

async function submitVoiceprint() {
  if (audioChunks.length === 0) {
    showToast('error', '请先录音')
    return
  }

  isSubmitting.value = true
  try {
    // 合并所有音频片段为 16kHz PCM Float32Array
    const totalLength = audioChunks.reduce((acc, chunk) => acc + chunk.length, 0)
    const merged = new Float32Array(totalLength)
    let offset = 0
    for (const chunk of audioChunks) {
      merged.set(chunk, offset)
      offset += chunk.length
    }

    // 提交原始 PCM Float32 数据到声纹接口
    const response = await fetch('http://192.168.128.235:21001/api/voiceprint', {
      method: 'POST',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: merged.buffer
    })

    if (response.ok) {
      const result = await response.json()
      // 将 embedding 写入表单
      voiceForm.value.embedding = result.embedding
      voiceprintDialogVisible.value = false
      showToast('success', '声纹录入成功')
    } else {
      showToast('error', '声纹录入失败')
    }
  } catch (e) {
    console.error('提交声纹失败:', e)
    showToast('error', '提交失败: ' + (e.message || ''))
  } finally {
    isSubmitting.value = false
  }
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
      res = await aiRequest.put(`/speaker-permissions/${payload.id}`, payload)
    } else {
      res = await aiRequest.post('/speaker-permissions', payload)
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
    const res = await aiRequest.put(`/speaker-permissions/${vp.id}`, payload)
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
    const res = await aiRequest.delete(`/speaker-permissions/${vp.id}`)
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

async function handleSync() {
  syncing.value = true
  try {
    const res = await aiRequest.post('/speaker-permissions/sync')
    if (res && res.code === 200) {
      const count = res.data || 0
      await fetchVoiceList()
      showToast('success', `同步成功，共同步 ${count.addCount+count.updateCount} 个用户`)
    } else {
      showToast('error', res?.message || '同步失败')
    }
  } catch (e) {
    console.error('同步语音权限失败:', e)
    showToast('error', '同步失败: ' + (e.message || ''))
  } finally {
    syncing.value = false
  }
}

onMounted(() => {
  fetchVoiceList()
  fetchAutoControl()
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
.ca-auto-btn.is-on {
  border-color: rgba(8, 174, 102, 0.45);
  color: var(--success);
  background: rgba(8, 174, 102, 0.08);
  font-weight: 600;
}
.ca-auto-btn.is-on:hover { border-color: var(--success); }
.ca-auto-btn:disabled { opacity: 0.5; cursor: not-allowed; }
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
.ca-voice-card-top { display: flex; align-items: center; gap: 12px; }
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
.ca-voice-info { flex: 1; min-width: 0; }
.ca-voice-name { margin: 0 0 4px; font-size: 15px; font-weight: 700; color: var(--text); }
.ca-voice-badge { font-size: 10px; padding: 2px 8px; border-radius: 20px; font-weight: 600; }
.ca-voice-badge.stranger { background: rgba(107,114,128,0.15); color: #9ca3af; }
.ca-voice-badge.user { background: rgba(99,102,241,0.15); color: #818cf8; }
.ca-voice-perms { display: flex; gap: 16px; }
.ca-perm-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-2);
  user-select: none;
}
.ca-perm-toggle input { display: none; }
.ca-perm-track {
  width: 36px;
  height: 20px;
  border-radius: 10px;
  background: #d1d5db;
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
.ca-perm-toggle.active .ca-perm-track { background: #1268ff; }
.ca-perm-toggle.active .ca-perm-track::after { transform: translateX(16px); }
.ca-perm-label { font-weight: 500; }
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
.ca-btn-icon:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); }
.ca-btn-icon.ca-btn-danger:hover:not(:disabled) { border-color: var(--danger); color: var(--danger); }
.ca-btn-icon:disabled { opacity: 0.3; cursor: not-allowed; }

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
.ca-field > label:not(.ca-perm-toggle) { display: block; font-size: 12px; font-weight: 600; color: var(--text-2); margin-bottom: 6px; }
.ca-field input,
.ca-field textarea {
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
.ca-field textarea:focus { border-color: var(--primary); }
.ca-field textarea { resize: vertical; min-height: 56px; }

/* ===== 声纹特征行 ===== */
.ca-embedding-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ca-embedding-badge {
  font-size: 12px;
  padding: 3px 12px;
  border-radius: 20px;
  font-weight: 600;
}
.ca-embedding-badge.recorded { background: rgba(8,174,102,0.12); color: #08ae66; }
.ca-embedding-badge.unrecorded { background: rgba(0,0,0,0.06); color: #999; }

/* ===== 录入声纹弹窗 ===== */
.ca-vp-hint {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.6;
}
.ca-vp-text-card {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 16px 20px;
  font-size: 14px;
  color: var(--text);
  line-height: 1.8;
  margin-bottom: 20px;
}
.ca-vp-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}
.ca-vp-record-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  border: 2px solid var(--danger);
  border-radius: 24px;
  background: transparent;
  color: var(--danger);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.ca-vp-record-btn:hover { background: rgba(239,68,68,0.06); }
.ca-vp-record-btn.recording {
  background: var(--danger);
  color: #fff;
  animation: vpPulse 1.2s ease-in-out infinite;
}
@keyframes vpPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239,68,68,0.4); }
  50% { box-shadow: 0 0 0 10px rgba(239,68,68,0); }
}
.ca-vp-record-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #fff;
  animation: dotBlink 0.8s ease-in-out infinite;
}
@keyframes dotBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

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
