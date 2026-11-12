<template>
  <div class="entity-center-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">ENTITY RESOLUTION CENTER</div>
        <h2>统一业务实体解析中心</h2>
        <p>统一管理实体别名、执行数据同步，并验证自然语言实体解析结果。</p>
      </div>
      <span class="module-tag">DEV MODULE</span>
    </div>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>实体解析测试</h3>
          <p>验证实体名称、业务编号和别名能否准确映射到稳定业务标识。</p>
        </div>
        <span class="hint">POST /api/entity-center/resolve</span>
      </div>
      <div class="form-grid">
        <label>
          <span>实体类型</span>
          <select v-model="resolveForm.entityType">
            <option value="AREA">区域 AREA</option>
            <option value="SYSTEM">系统 SYSTEM</option>
            <option value="DEVICE_TYPE">设备分类 DEVICE_TYPE</option>
            <option value="DEVICE">设备 DEVICE</option>
            <option value="CAMERA">摄像头 CAMERA</option>
          </select>
        </label>
        <label class="query-field">
          <span>名称 / 编号 / 别名</span>
          <input v-model.trim="resolveForm.query" placeholder="例如：2号冷水机组" @keyup.enter="resolveEntity" />
        </label>
        <label>
          <span>区域 cascade_cid（设备可选）</span>
          <input v-model.trim="resolveForm.areaId" placeholder="输入区域级联标识" :disabled="resolveForm.entityType !== 'DEVICE'" />
        </label>
        <label>
          <span>系统 element_key（设备可选）</span>
          <input v-model.trim="resolveForm.systemKey" placeholder="输入系统稳定标识" :disabled="resolveForm.entityType !== 'DEVICE'" />
        </label>
        <label>
          <span>设备分类 id（设备可选）</span>
          <input v-model.trim="resolveForm.deviceTypeId" placeholder="输入设备分类ID" :disabled="resolveForm.entityType !== 'DEVICE'" />
        </label>
        <label>
          <span>候选数量</span>
          <select v-model.number="resolveForm.limit">
            <option :value="5">5</option>
            <option :value="10">10</option>
            <option :value="20">20</option>
          </select>
        </label>
      </div>
      <div class="panel-actions">
        <button class="btn-primary" :disabled="resolving || !resolveForm.query" @click="resolveEntity">
          {{ resolving ? '解析中…' : '开始解析' }}
        </button>
        <button class="btn-secondary" @click="clearResolve">清空</button>
      </div>

      <div v-if="resolveResult" class="result-block">
        <div class="result-summary">
          <div>
            <span class="result-label">解析状态</span>
            <strong :class="'status-' + resolveResult.status">{{ statusText(resolveResult.status) }}</strong>
          </div>
          <div>
            <span class="result-label">标准化查询</span>
            <code>{{ resolveResult.normalizedQuery || '—' }}</code>
          </div>
          <p>{{ resolveResult.message }}</p>
        </div>
        <div v-if="resolveResult.resolved" class="resolved-card">
          <span>已确认实体</span>
          <strong>{{ resolveResult.resolved.name || '未命名实体' }}</strong>
          <code>{{ resolveResult.resolved.entityType }} / {{ resolveResult.resolved.entityId }}</code>
        </div>
        <div class="candidate-list" v-if="resolveResult.candidates?.length">
          <div class="candidate-head">
            <span>候选实体</span><span>相关度分值</span>
          </div>
          <div v-for="item in resolveResult.candidates" :key="item.entityId" class="candidate-row">
            <div>
              <strong>{{ item.name || '未命名实体' }}</strong>
              <small>{{ item.entityType }} · {{ item.entityId }}</small>
            </div>
            <code>{{ Number(item.score || 0).toFixed(2) }}</code>
          </div>
        </div>
      </div>
    </section>

    <section class="two-column">
      <div class="panel">
        <div class="panel-heading">
          <div>
            <h3>数据同步</h3>
            <p>对已接入源系统执行手动幂等同步。</p>
          </div>
          <span class="hint">POST /api/entity-center/admin/sync</span>
        </div>
        <div class="sync-grid">
          <button v-for="item in syncItems" :key="item.key" class="sync-item"
            :disabled="syncing !== ''" @click="runSync(item)">
            <span class="sync-icon">{{ item.icon }}</span>
            <span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
            <span class="sync-arrow">→</span>
          </button>
        </div>
        <div v-if="syncMessage" class="sync-message" :class="{ error: syncError }">{{ syncMessage }}</div>
        <p class="footnote">当前同步采用稳定业务ID进行 Bulk upsert，不会自动删除源系统已不存在的旧记录。设备分类数据源尚待确认。</p>
      </div>

      <div class="panel">
        <div class="panel-heading">
          <div>
            <h3>别名维护</h3>
            <p>为实体添加业务常用称呼，解析时按标准化别名精确回查实体ID。</p>
          </div>
          <span class="hint">/api/entity-center/aliases</span>
        </div>
        <div class="alias-form">
          <label><span>实体类型</span>
            <select v-model="aliasForm.entityType">
              <option value="AREA">区域 AREA</option><option value="SYSTEM">系统 SYSTEM</option>
              <option value="DEVICE_TYPE">设备分类 DEVICE_TYPE</option><option value="DEVICE">设备 DEVICE</option>
              <option value="CAMERA">摄像头 CAMERA</option>
            </select>
          </label>
          <label><span>实体稳定业务标识</span><input v-model.trim="aliasForm.dataId" placeholder="区域cascade_cid / 系统element_key / 其他实体id" /></label>
          <label><span>别名</span><input v-model.trim="aliasForm.aliasName" placeholder="例如：冷机2号" /></label>
          <label><span>备注（可选）</span><input v-model.trim="aliasForm.remark" placeholder="别名来源或说明" /></label>
        </div>
        <div class="panel-actions">
          <button class="btn-primary" :disabled="savingAlias || !aliasForm.dataId || !aliasForm.aliasName" @click="saveAlias">
            {{ savingAlias ? '保存中…' : '保存别名' }}
          </button>
          <button class="btn-secondary" @click="clearAlias">清空</button>
        </div>
        <div class="alias-lookup">
          <div class="lookup-title">查询已配置别名</div>
          <div class="lookup-controls">
            <input v-model.trim="lookupDataId" placeholder="输入实体稳定业务标识" />
            <button class="btn-secondary" :disabled="loadingAliases || !lookupDataId" @click="loadAliases">
              {{ loadingAliases ? '查询中…' : '查询' }}
            </button>
          </div>
          <div v-if="aliases.length" class="alias-chips">
            <span v-for="item in aliases" :key="item.id">{{ item.aliasName }} <small>{{ item.source }}</small></span>
          </div>
          <p v-else-if="aliasQueried" class="empty-note">没有查询到已启用的别名。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { aiRequest } from '@/utils/request'

const resolveForm = reactive({
  query: '',
  entityType: 'DEVICE',
  areaId: '',
  systemKey: '',
  deviceTypeId: '',
  limit: 5
})
const resolveResult = ref(null)
const resolving = ref(false)

const syncItems = [
  { key: 'available', label: '同步已接入数据源', description: '区域、系统、设备、摄像头', icon: '↻' },
  { key: 'areas', label: '区域数据', description: 'cascade_cid', icon: '⌖' },
  { key: 'systems', label: '系统枚举', description: 'element_key', icon: '▦' },
  { key: 'devices', label: '设备目录', description: '设备稳定ID', icon: '▣' },
  { key: 'cameras', label: '摄像头目录', description: '独立索引，不做区域过滤', icon: '◉' }
]
const syncing = ref('')
const syncMessage = ref('')
const syncError = ref(false)

const aliasForm = reactive({ entityType: 'DEVICE', dataId: '', aliasName: '', remark: '' })
const savingAlias = ref(false)
const lookupDataId = ref('')
const aliases = ref([])
const loadingAliases = ref(false)
const aliasQueried = ref(false)

function statusText(status) {
  return ({ FOUND: '已确认', AMBIGUOUS: '需要消歧', NOT_FOUND: '未找到', SERVICE_UNAVAILABLE: '服务不可用' })[status] || status || '未知'
}

async function resolveEntity() {
  resolving.value = true
  resolveResult.value = null
  const payload = { ...resolveForm }
  if (payload.entityType !== 'DEVICE') {
    delete payload.areaId
    delete payload.systemKey
    delete payload.deviceTypeId
  }
  try {
    const response = await aiRequest.post('/api/entity-center/resolve', payload)
    resolveResult.value = response
  } catch (error) {
    resolveResult.value = { status: 'SERVICE_UNAVAILABLE', message: error?.response?.data?.message || error.message, candidates: [] }
  } finally {
    resolving.value = false
  }
}

function clearResolve() {
  resolveForm.query = ''
  resolveResult.value = null
}

async function runSync(item) {
  syncing.value = item.key
  syncMessage.value = ''
  syncError.value = false
  try {
    const response = await aiRequest.post('/api/entity-center/admin/sync/' + item.key)
    syncMessage.value = item.key === 'available'
      ? '同步完成：' + Object.entries(response || {}).map(([key, value]) => key + ' ' + value + ' 条').join('，')
      : item.label + '同步完成，共写入 ' + response + ' 条。'
  } catch (error) {
    syncError.value = true
    syncMessage.value = error?.response?.data?.message || error.message || '同步失败，请检查后端功能开关和服务日志。'
  } finally {
    syncing.value = ''
  }
}

async function saveAlias() {
  savingAlias.value = true
  try {
    await aiRequest.post('/api/entity-center/aliases', { ...aliasForm, enabled: 1, source: 'MANUAL' })
    syncMessage.value = '别名已保存。'
    syncError.value = false
    aliasForm.aliasName = ''
    aliasForm.remark = ''
    if (lookupDataId.value === aliasForm.dataId) await loadAliases()
  } catch (error) {
    syncMessage.value = error?.response?.data?.message || error.message || '别名保存失败。'
    syncError.value = true
  } finally {
    savingAlias.value = false
  }
}

function clearAlias() {
  aliasForm.dataId = ''
  aliasForm.aliasName = ''
  aliasForm.remark = ''
}

async function loadAliases() {
  loadingAliases.value = true
  aliasQueried.value = false
  try {
    aliases.value = await aiRequest.get('/api/entity-center/aliases', {
      params: { entityType: aliasForm.entityType, dataId: lookupDataId.value }
    })
    aliasQueried.value = true
  } catch (error) {
    aliases.value = []
    aliasQueried.value = true
    syncError.value = true
    syncMessage.value = error?.response?.data?.message || error.message || '别名查询失败。'
  } finally {
    loadingAliases.value = false
  }
}
</script>

<style scoped>
.entity-center-page { color: #e8f1ff; display: flex; flex-direction: column; gap: 18px; }
.page-heading { display:flex; justify-content:space-between; align-items:flex-start; gap:20px; margin-bottom:2px; }
.eyebrow { color:#42bdf5; font-size:10px; letter-spacing:2px; font-weight:800; margin-bottom:7px; }
.page-heading h2 { margin:0; font-size:23px; color:#f4f8ff; letter-spacing:.2px; }
.page-heading p,.panel-heading p { color:rgba(226,239,255,.58); font-size:12px; margin:7px 0 0; line-height:1.6; }
.module-tag,.hint { color:#7fcdf5; font-size:10px; border:1px solid rgba(76,185,239,.22); border-radius:20px; padding:5px 9px; white-space:nowrap; }
.panel { background:rgba(5,30,62,.74); border:1px solid rgba(132,190,232,.14); border-radius:13px; padding:20px; box-shadow:0 12px 36px rgba(0,0,0,.12); min-width:0; }
.panel-heading { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; margin-bottom:17px; }
.panel-heading h3 { color:#f1f7ff; margin:0; font-size:15px; font-weight:750; }
.form-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:13px; }
.form-grid label,.alias-form label { display:flex; flex-direction:column; gap:7px; min-width:0; }
.form-grid label span,.alias-form label span { font-size:11px; color:rgba(226,239,255,.68); }
input,select { width:100%; box-sizing:border-box; min-height:37px; border:1px solid rgba(142,190,228,.2); border-radius:7px; background:rgba(1,17,39,.62); color:#e8f3ff; padding:8px 10px; font-size:12px; outline:none; }
input:focus,select:focus { border-color:rgba(42,177,239,.75); box-shadow:0 0 0 2px rgba(42,177,239,.1); }
input:disabled { opacity:.38; cursor:not-allowed; }
input::placeholder { color:rgba(211,229,248,.3); }
.panel-actions { display:flex; gap:9px; margin-top:14px; }
button { cursor:pointer; transition:.18s ease; }
button:disabled { opacity:.45; cursor:not-allowed; }
.btn-primary,.btn-secondary { border-radius:7px; min-height:34px; padding:0 14px; font-size:12px; font-weight:700; }
.btn-primary { color:#05213b; background:linear-gradient(135deg,#4ed0ff,#32aaf0); border:0; }
.btn-primary:hover:not(:disabled) { filter:brightness(1.08); }
.btn-secondary { color:#c9e6fb; background:rgba(255,255,255,.04); border:1px solid rgba(143,193,230,.18); }
.btn-secondary:hover:not(:disabled) { background:rgba(255,255,255,.09); }
.result-block { margin-top:16px; border-top:1px solid rgba(144,193,230,.12); padding-top:15px; }
.result-summary { display:grid; grid-template-columns:150px 1fr; gap:8px 18px; align-items:center; }
.result-summary>div { display:flex; flex-direction:column; gap:5px; }
.result-label { color:rgba(226,239,255,.48); font-size:10px; }
.result-summary strong { font-size:13px; }
.result-summary p { grid-column:1/-1; color:rgba(226,239,255,.65); font-size:12px; margin:4px 0 0; }
.status-FOUND { color:#54d6a2; }.status-AMBIGUOUS { color:#ffc56b; }.status-NOT_FOUND,.status-SERVICE_UNAVAILABLE { color:#ff8989; }
.result-summary code,.candidate-row code,.resolved-card code { color:#9fc9e9; font-size:11px; }
.resolved-card { margin-top:13px; display:flex; flex-direction:column; gap:5px; padding:12px; border:1px solid rgba(73,206,158,.25); border-radius:8px; background:rgba(42,171,125,.07); }
.resolved-card span { font-size:10px; color:#71d9b2; }.resolved-card strong { font-size:14px; }
.candidate-list { margin-top:13px; border:1px solid rgba(142,190,228,.12); border-radius:8px; overflow:hidden; }
.candidate-head,.candidate-row { display:flex; justify-content:space-between; align-items:center; gap:12px; padding:10px 12px; }
.candidate-head { color:rgba(226,239,255,.48); background:rgba(255,255,255,.035); font-size:10px; }
.candidate-row { border-top:1px solid rgba(142,190,228,.09); }
.candidate-row>div { display:flex; flex-direction:column; gap:4px; }.candidate-row strong { font-size:12px; }.candidate-row small { color:rgba(226,239,255,.45); font-size:10px; }
.two-column { display:grid; grid-template-columns:1fr 1fr; gap:18px; align-items:start; }
.sync-grid { display:grid; grid-template-columns:1fr 1fr; gap:9px; }
.sync-item { display:flex; align-items:center; gap:10px; text-align:left; min-height:62px; padding:10px; border-radius:8px; border:1px solid rgba(142,190,228,.14); color:#e8f3ff; background:rgba(255,255,255,.025); }
.sync-item:hover:not(:disabled) { border-color:rgba(63,185,240,.5); background:rgba(43,151,207,.08); }
.sync-icon { display:grid; place-items:center; width:30px; height:30px; border-radius:8px; background:rgba(42,177,239,.12); color:#59c8f5; font-size:18px; flex-shrink:0; }
.sync-item>span:nth-child(2) { display:flex; flex-direction:column; gap:4px; min-width:0; }.sync-item strong { font-size:11px; }.sync-item small { color:rgba(226,239,255,.45); font-size:9px; line-height:1.4; }
.sync-arrow { margin-left:auto; color:#6fc8ef; }
.sync-message { margin-top:12px; border-radius:7px; padding:9px 11px; color:#83e0bb; background:rgba(36,167,119,.1); border:1px solid rgba(36,167,119,.2); font-size:11px; line-height:1.6; overflow-wrap:anywhere; }
.sync-message.error { color:#ffabab; background:rgba(220,70,70,.08); border-color:rgba(220,70,70,.2); }
.footnote,.empty-note { color:rgba(226,239,255,.4); font-size:10px; line-height:1.6; margin:12px 0 0; }
.alias-form { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
.alias-lookup { margin-top:18px; padding-top:14px; border-top:1px solid rgba(142,190,228,.12); }
.lookup-title { color:#dceeff; font-size:11px; font-weight:700; margin-bottom:9px; }
.lookup-controls { display:flex; gap:8px; }.lookup-controls input { flex:1; min-width:0; }
.alias-chips { display:flex; flex-wrap:wrap; gap:7px; margin-top:10px; }
.alias-chips span { border:1px solid rgba(86,188,236,.2); background:rgba(55,153,205,.09); color:#bde7ff; padding:6px 9px; border-radius:20px; font-size:10px; }
.alias-chips small { color:rgba(226,239,255,.4); margin-left:4px; }
@media(max-width:1100px) { .two-column { grid-template-columns:1fr; } }
@media(max-width:760px) { .form-grid,.alias-form,.sync-grid { grid-template-columns:1fr; } .panel-heading { flex-direction:column; } .result-summary { grid-template-columns:1fr; } .page-heading h2 { font-size:19px; } }
</style>
