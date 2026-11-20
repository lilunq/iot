<template>
  <div class="entity-center">
    <header class="page-header">
      <div>
        <div class="eyebrow">ENTITY RESOLUTION CENTER</div>
        <h2>统一业务实体解析中心</h2>
        <p>以稳定业务标识为核心，统一管理实体目录、自然语言解析与多维检索。</p>
      </div>
      <div class="header-actions">
        <span class="status-pill"><i></i> ES 数据目录</span>
        <button class="btn ghost" :disabled="loadingSummary" @click="loadSummary">刷新数据</button>
      </div>
    </header>

    <section class="summary-grid">
      <button v-for="item in summaryItems" :key="item.entityType"
        class="summary-card" :class="{ active: activeType === item.entityType }"
        @click="selectType(item.entityType)">
        <span class="summary-icon">{{ typeIcon[item.entityType] }}</span>
        <span class="summary-label">{{ item.label }}</span>
        <strong>{{ formatNumber(item.count) }}</strong>
        <small>{{ item.indexAlias }}</small>
      </button>
      <div class="summary-total">
        <span>实体总量</span>
        <strong>{{ formatNumber(summaryTotal) }}</strong>
        <small>五类实体 · 稳定ID索引</small>
      </div>
    </section>

    <nav class="workspace-tabs" aria-label="实体解析中心工作区">
      <button v-for="tab in workspaceTabs" :key="tab.key"
        :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">
        <span class="tab-index">{{ tab.index }}</span>
        <span>{{ tab.label }}</span>
        <small>{{ tab.description }}</small>
      </button>
    </nav>

    <section v-if="activeTab === 'catalog'" class="workspace">
      <div class="section-title">
        <div><span class="section-kicker">01 / CATALOG</span><h3>实体目录</h3></div>
        <div class="section-tools">
          <span class="section-note">点击实体查看完整模型、别名与拼音分析</span>
          <button class="btn ghost" @click="catalogExpanded = !catalogExpanded">
            {{ catalogExpanded ? '收起工作区' : '放大工作区' }}
          </button>
        </div>
      </div>
      <div class="catalog-grid" :class="{ expanded: catalogExpanded }">
        <button v-if="catalogExpanded" class="btn ghost expand-close" @click="catalogExpanded = false">收起工作区 ×</button>
        <div class="panel2 catalog-panel2">
          <div class="catalog-toolbar">
            <div class="type-tabs">
              <button v-for="item in summaryItems" :key="item.entityType"
                :class="{ selected: activeType === item.entityType }"
                @click="selectType(item.entityType)">{{ item.label }}</button>
            </div>
            <div class="search-line">
              <input v-model.trim="catalogKeyword" placeholder="按名称、业务编号、别名或全拼检索"
                @keyup.enter="loadCatalog(1)" />
              <button class="btn primary" :disabled="loadingCatalog" @click="loadCatalog(1)">
                {{ loadingCatalog ? '检索中…' : '检索' }}
              </button>
            </div>
          </div>
          <div v-if="catalogError" class="inline-error">{{ catalogError }}</div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>实体名称</th><th>稳定业务标识</th><th>业务别名</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="row in catalogRows" :key="row.entityId"
                  :class="{ chosen: selectedDetail?.entityId === row.entityId }"
                  @click="loadDetail(row.entityId)">
                  <td><strong>{{ row.name || '未命名实体' }}</strong><small>{{ activeTypeLabel }}</small></td>
                  <td><code>{{ row.entityId }}</code></td>
                  <td><span v-if="row.aliases?.length" class="alias-inline">{{ row.aliases.join(' / ') }}</span><span v-else class="muted">—</span></td>
                  <td><button class="text-action" @click.stop="loadDetail(row.entityId)">查看详情 →</button></td>
                </tr>
                <tr v-if="!catalogRows.length && !loadingCatalog"><td colspan="4" class="empty-cell">暂无数据，请先完成索引初始化和数据同步。</td></tr>
              </tbody>
            </table>
          </div>
          <div class="pagination">
            <span>共 {{ catalogTotal }} 条 · 第 {{ catalogPage }} / {{ catalogPages || 1 }} 页</span>
            <div><button class="btn ghost" :disabled="catalogPage <= 1 || loadingCatalog" @click="loadCatalog(catalogPage - 1)">上一页</button>
              <button class="btn ghost" :disabled="catalogPage >= catalogPages || loadingCatalog" @click="loadCatalog(catalogPage + 1)">下一页</button></div>
          </div>
        </div>

        <EntityModelDetail
          class="detail-panel2"
          :detail="selectedDetail"
          :loading="detailLoading"
          :type-label="activeTypeLabel"
          :alias-name="detailAliasName"
          :saving-alias="savingDetailAlias"
          :alias-message="detailAliasMessage"
          :alias-error="detailAliasError"
          empty-text="从左侧列表选择实体，查看完整模型详情。"
          @update:alias-name="detailAliasName = $event"
          @add-alias="addDetailAlias()"
        />
      </div>
    </section>

    <section v-if="activeTab === 'assistant'" class="workspace">
      <div class="section-title">
        <div><span class="section-kicker">02 / AI RESOLUTION</span><h3>自然语言实体解析</h3></div>
        <span class="section-note">LLM 负责理解与结构化 · Elasticsearch 负责真实数据召回</span>
      </div>
      <div class="panel2 assistant-panel2">
        <div class="assistant-composer">
          <div class="composer-heading">
            <div>
              <strong>输入自然语言检索需求</strong>
              <p>直接描述设备名称、所在区域、所属系统或设备分类，无需填写业务 ID。</p>
            </div>
            <span class="composer-shortcut">Ctrl + Enter 提交</span>
          </div>
          <div class="prompt-row">
            <textarea
              v-model="assistantPrompt"
              rows="3"
              aria-label="自然语言实体检索输入框"
              placeholder="例如：帮我查找地下层冷冻机房的排风机，优先匹配设备名称和别名…"
              @keydown.ctrl.enter="runAssistant"
            ></textarea>
            <button class="btn primary assistant-submit" :disabled="assistantLoading || !assistantPrompt.trim()" @click="runAssistant">
              {{ assistantLoading ? 'AI 正在解析…' : 'AI 解析并检索' }}
            </button>
          </div>
          <div class="quick-prompts">
            <span>常用检索</span>
            <button v-for="item in quickPrompts" :key="item" type="button"
              :disabled="assistantLoading" @click="runQuickSearch(item)">{{ item }}</button>
          </div>
        </div>
        <div v-if="assistantError" class="inline-error">{{ assistantError }}</div>
        <div v-if="assistantResult" class="assistant-result-layout">
          <div class="assistant-result">
            <div class="panel2-head"><div><span class="section-kicker">LLM STRUCTURED OUTPUT</span><h3>自然语言结构化结果</h3></div><span class="ai-tag">LLM</span></div>
            <div class="parsed-grid">
              <div><small>实体类型</small><strong>{{ assistantResult.parsed.entityLabel }} · {{ assistantResult.parsed.entityType }}</strong></div>
              <div><small>检索关键词（后端校正后）</small><strong>{{ assistantResult.parsed.keyword }}</strong></div>
              <div><small>辅助检索词</small><strong>{{ (assistantResult.parsed.keywordVariants || []).join('、') || '无' }}</strong></div>
              <div><small>区域（真实词典匹配）</small><strong>{{ assistantResult.parsed.areaName || '未指定' }}</strong></div>
              <div><small>系统（真实词典匹配）</small><strong>{{ assistantResult.parsed.systemName || '未指定' }}</strong></div>
              <div><small>识别分类偏好（不硬过滤）</small><strong>{{ assistantResult.parsed.deviceTypeName || '未指定' }}</strong></div>
              <div><small>区域稳定 ID</small><code>{{ assistantResult.parsed.areaId || '未指定' }}</code></div>
              <div><small>系统稳定 Key</small><code>{{ assistantResult.parsed.systemKey || '未指定' }}</code></div>
              <div><small>分类稳定 ID</small><code>{{ assistantResult.parsed.deviceTypeId || '未指定' }}</code></div>
              <div><small>意图说明</small><strong>{{ assistantResult.parsed.intent }}</strong></div>
            </div>
            <div class="search-strategy-note">
              明确指定的区域、系统：确认后执行范围过滤；设备分类：仅用于相关性加权；设备名称：同时匹配原始关键词和名称主体。未配置分类的设备不会因此被排除。
            </div>
            <div v-if="assistantResult.status === 'NEEDS_CLARIFICATION'" class="filter-warning">
              区域或系统条件存在歧义或未找到，已暂停 ES 检索。设备分类不会作为必选过滤条件。
            </div>
            <div v-if="Object.keys(assistantResult.filterResolutions || {}).length" class="filter-resolution-list">
              <div v-for="(item, key) in assistantResult.filterResolutions" :key="key">
                <strong>{{ key === 'area' ? '区域' : key === 'system' ? '系统' : '设备分类' }}</strong>
                <span :class="'resolve-' + item.status">{{ item.status }}</span>
                <small>{{ item.message }}</small>
                <div v-if="item.candidates?.length" class="filter-candidates">
                  <span v-for="candidate in item.candidates" :key="candidate.entityId">
                    {{ candidate.name }} · {{ candidate.entityId }}
                  </span>
                </div>
              </div>
            </div>
            <div v-if="assistantResult.vocabulary" class="search-strategy-note">
              本次使用区域词典 {{ assistantResult.vocabulary.areaCount }} 项、系统词典 {{ assistantResult.vocabulary.systemCount }} 项；
              后端区域匹配：{{ assistantResult.vocabulary.areaMatchedByBackend ? '已命中' : '未命中' }}，
              系统匹配：{{ assistantResult.vocabulary.systemMatchedByBackend ? '已命中' : '未命中' }}。
            </div>
            <details class="raw-json"><summary>查看 LLM 原始响应（以下结构化结果已由后端真实词典校正）</summary><pre>{{ assistantResult.llmRaw }}</pre></details>
          </div>
          <div class="assistant-candidate-detail-grid">
            <div class="assistant-result assistant-candidate-panel">
              <div class="panel2-head"><div><span class="section-kicker">ELASTICSEARCH TOP 20</span><h3>ES 检索候选</h3></div><span class="result-count">{{ assistantResult.searchResult.total }} 条 · {{ assistantResult.searchResult.rankingStatus === 'LLM_RERANKED' ? '语义重排' : assistantResult.searchResult.rankingStatus === 'ES_FALLBACK' ? 'ES原排序' : 'ES召回' }}</span></div>
            <div v-if="assistantResult.searchResult.rankingMessage" class="search-strategy-note">{{ assistantResult.searchResult.rankingMessage }}</div>
            <div v-if="assistantResult.searchResult.records?.length" class="candidate-results">
              <button v-for="(row, index) in assistantResult.searchResult.records" :key="row.entityId"
                class="candidate-card" :class="{ selected: selectedAssistantDetail?.entityId === row.entityId }"
                @click="loadAssistantDetail(row)">
                <span><strong>{{ row.name || '未命名实体' }}</strong><small>{{ [row.subarea_name, row.system_name || row.system_key, row.device_type_name].filter(Boolean).join(' · ') || '未维护区域/系统/分类信息' }} · {{ row.entityId }}</small></span>
                <span class="candidate-metrics">
                  <strong v-if="assistantResult.searchResult.rankingStatus === 'LLM_RERANKED'" class="candidate-rank">TOP {{ String(index + 1).padStart(2, '0') }}</strong>
                  <small class="candidate-score" title="Elasticsearch 自动计算的 _score 相关性分值，不是百分比">ES 匹配度 {{ Number(row.score || 0).toFixed(2) }}</small>
                </span>
              </button>
            </div>
              <div v-else class="detail-empty">
                {{ assistantResult.status === 'NEEDS_CLARIFICATION' ? '区域或系统过滤条件需要确认，已暂停检索。' : 'ES 没有召回匹配实体。' }}
              </div>
            </div>
            <EntityModelDetail
              class="assistant-entity-detail"
              :detail="selectedAssistantDetail"
              :loading="assistantDetailLoading"
              :type-label="selectedAssistantDetail?.entityType || assistantResult.searchResult.entityType || assistantResult.parsed.entityType"
              :alias-name="detailAliasName"
              :saving-alias="savingDetailAlias"
              :alias-message="detailAliasMessage"
              :alias-error="detailAliasError"
              empty-text="点击左侧 ES 候选，右侧展示该实体的完整模型详情。"
              @update:alias-name="detailAliasName = $event"
              @add-alias="addAssistantDetailAlias"
            />
          </div>
        </div>
        <div v-else class="assistant-placeholder">输入自然语言检索需求，系统将先由 LLM 提取实体类型、关键词和明确过滤条件，再由 ES 返回真实候选，不会让模型生成实体 ID。</div>
      </div>
    </section>

    <section v-if="activeTab === 'operations'" class="workspace">
      <div class="section-title">
        <div><span class="section-kicker">03 / OPERATIONS</span><h3>数据同步与别名维护</h3></div>
        <span class="section-note">保留现有管理能力 · 手动触发 · 幂等写入</span>
      </div>
      <div class="operations-grid">
        <div class="panel2">
          <div class="panel2-head"><div><h3>数据同步</h3><p>同步源系统数据至五类 ES 索引。</p></div></div>
          <div class="sync-actions">
            <button class="btn ghost" :disabled="indexInitializing" @click="initializeIndexes">{{ indexInitializing ? '初始化中…' : '初始化 ES 索引' }}</button>
            <button v-for="item in syncItems" :key="item.key" class="btn ghost"
              :disabled="syncing !== ''" @click="runSync(item)">{{ syncing === item.key ? '同步中…' : item.label }}</button>
          </div>
          <div v-if="syncMessage" class="sync-message" :class="{ error: syncError }">{{ syncMessage }}</div>
          <p class="footnote">区域稳定 ID 为 cascade_cid，系统为 element_key，分类/设备/摄像头为 id。设备分类按 cascade_id 映射；摄像头不做区域过滤。普通同步不自动删除源系统已不存在的记录。</p>
        </div>
        <div class="panel2">
          <div class="panel2-head"><div><h3>业务别名维护</h3><p>为实体添加常用简称，别名精确回查实体稳定 ID。</p></div></div>
          <div class="alias-form">
            <label>实体类型<select v-model="aliasForm.entityType"><option v-for="item in summaryItems" :key="item.entityType" :value="item.entityType">{{ item.label }}</option></select></label>
            <label>稳定业务标识<input v-model.trim="aliasForm.dataId" placeholder="输入 cascade_cid / element_key / id" /></label>
            <label>别名<input v-model.trim="aliasForm.aliasName" placeholder="例如：冷机2号" /></label>
            <label>备注<input v-model.trim="aliasForm.remark" placeholder="可选" /></label>
          </div>
          <div class="action-line"><button class="btn primary" :disabled="savingAlias || !aliasForm.dataId || !aliasForm.aliasName" @click="saveAlias">{{ savingAlias ? '保存中…' : '保存别名' }}</button></div>
          <div class="alias-query"><input v-model.trim="lookupDataId" placeholder="按实体稳定标识查询别名" />
            <button class="btn ghost" :disabled="loadingAliases || !lookupDataId" @click="loadAliases">{{ loadingAliases ? '查询中…' : '查询别名' }}</button></div>
          <div v-if="aliases.length" class="chips"><span v-for="alias in aliases" :key="alias.id">{{ alias.aliasName }} <small>{{ alias.source }}</small></span></div>
          <p v-else-if="aliasQueried" class="muted">没有查询到已启用的别名。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { aiRequest } from '@/utils/request'
import EntityModelDetail from './EntityModelDetail.vue'

const workspaceTabs = [
  { key: 'catalog', index: '01', label: '实体目录', description: '目录检索与模型详情' },
  { key: 'assistant', index: '02', label: '自然语言实体解析', description: 'AI 理解、ES 召回与语义重排' },
  { key: 'operations', index: '03', label: '数据同步与别名维护', description: '索引初始化、同步和业务别名' }
]
const quickPrompts = [
  '候车大厅1号空调',
  '候车大厅的岛式空调1',
  '候车大厅摄像头',
  '地下层冷冻机房排风机',
  '候车层照明设备'
]
const activeTab = ref('catalog')
const typeIcon = { AREA: '⌖', SYSTEM: '▦', DEVICE_TYPE: '▤', DEVICE: '▣', CAMERA: '◉' }
const summaryItems = ref([])
const summaryTotal = ref(0)
const loadingSummary = ref(false)
const activeType = ref('DEVICE')
const activeTypeLabel = computed(() => summaryItems.value.find(item => item.entityType === activeType.value)?.label || '设备')
const catalogKeyword = ref('')
const catalogRows = ref([])
const catalogTotal = ref(0)
const catalogPage = ref(1)
const catalogPages = ref(0)
const loadingCatalog = ref(false)
const catalogError = ref('')
const selectedDetail = ref(null)
const detailLoading = ref(false)
const detailAliasName = ref('')
const savingDetailAlias = ref(false)
const detailAliasMessage = ref('')
const detailAliasError = ref(false)
const catalogExpanded = ref(false)

const assistantPrompt = ref('')
const assistantLoading = ref(false)
const assistantError = ref('')
const assistantResult = ref(null)
const selectedAssistantDetail = ref(null)
const assistantDetailLoading = ref(false)

const syncItems = [
  { key: 'available', label: '同步全部五类实体' },
  { key: 'areas', label: '同步区域' },
  { key: 'systems', label: '同步系统' },
  { key: 'device-types', label: '同步设备分类' },
  { key: 'devices', label: '同步设备' },
  { key: 'cameras', label: '同步摄像头' }
]
const syncing = ref('')
const indexInitializing = ref(false)
const syncMessage = ref('')
const syncError = ref(false)
const aliasForm = reactive({ entityType: 'DEVICE', dataId: '', aliasName: '', remark: '' })
const savingAlias = ref(false)
const lookupDataId = ref('')
const aliases = ref([])
const loadingAliases = ref(false)
const aliasQueried = ref(false)

function formatNumber(value) { return new Intl.NumberFormat('zh-CN').format(value || 0) }
function displayValue(value) {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
async function loadSummary() {
  loadingSummary.value = true
  try {
    const response = await aiRequest.get('/entity-center/catalog/summary')
    summaryItems.value = response.items || []
    summaryTotal.value = response.total || 0
  } catch (error) {
    catalogError.value = error?.response?.data?.message || error.message || '实体统计加载失败'
  } finally { loadingSummary.value = false }
}
async function selectType(type) {
  activeTab.value = 'catalog'
  if (activeType.value !== type) {
    activeType.value = type
    selectedDetail.value = null
  }
  await loadCatalog(1)
}
async function loadCatalog(page = 1) {
  loadingCatalog.value = true
  catalogError.value = ''
  try {
    const response = await aiRequest.get('/entity-center/catalog/' + activeType.value, {
      params: { keyword: catalogKeyword.value || undefined, page, size: 20 }
    })
    catalogRows.value = response.records || []
    catalogTotal.value = response.total || 0
    catalogPage.value = response.page || page
    catalogPages.value = response.pages || 0
  } catch (error) {
    catalogError.value = error?.response?.data?.message || error.message || '实体目录加载失败'
  } finally { loadingCatalog.value = false }
}
async function loadDetail(id, entityType = activeType.value) {
  const normalizedType = String(entityType || '').trim().toUpperCase()
  const allowedTypes = ['AREA', 'SYSTEM', 'DEVICE_TYPE', 'DEVICE', 'CAMERA']
  if (!allowedTypes.includes(normalizedType) || !id) {
    catalogError.value = '实体详情请求缺少有效的实体类型或实体标识。'
    return
  }
  detailLoading.value = true
  try {
    selectedDetail.value = await aiRequest.get('/entity-center/catalog/' + normalizedType + '/' + encodeURIComponent(id))
  } catch (error) {
    catalogError.value = error?.response?.data?.message || error.message || '实体详情加载失败'
  } finally { detailLoading.value = false }
}
async function loadAssistantDetail(row) {
  const entityType = String(
    row?.entityType ||
    assistantResult.value?.searchResult?.entityType ||
    assistantResult.value?.parsed?.entityType ||
    ''
  ).trim().toUpperCase()
  const entityId = String(row?.entityId || '').trim()
  const allowedTypes = ['AREA', 'SYSTEM', 'DEVICE_TYPE', 'DEVICE', 'CAMERA']
  if (!allowedTypes.includes(entityType) || !entityId) {
    assistantError.value = 'ES 候选缺少有效的实体类型或实体标识，已阻止发送无效详情请求。'
    return
  }
  assistantDetailLoading.value = true
  selectedAssistantDetail.value = null
  detailAliasMessage.value = ''
  detailAliasName.value = ''
  try {
    selectedAssistantDetail.value = await aiRequest.get(
      '/entity-center/catalog/' + entityType + '/' + encodeURIComponent(entityId)
    )
  } catch (error) {
    assistantError.value = error?.response?.data?.message || error.message || '候选实体详情加载失败'
  } finally { assistantDetailLoading.value = false }
}
async function addDetailAlias(current = selectedDetail.value, context = 'catalog') {
  const aliasName = detailAliasName.value.trim()
  if (!current || !aliasName || savingDetailAlias.value) return
  savingDetailAlias.value = true
  detailAliasMessage.value = ''
  detailAliasError.value = false
  try {
    await aiRequest.post('/entity-center/aliases', {
      entityType: current.entityType,
      dataId: current.entityId,
      aliasName,
      remark: '',
      enabled: 1,
      source: 'MANUAL'
    })
    detailAliasName.value = ''
    detailAliasMessage.value = '别名添加成功'
    if (context === 'assistant') {
      await loadAssistantDetail({ entityType: current.entityType, entityId: current.entityId })
    } else {
      await loadDetail(current.entityId, current.entityType)
      await loadCatalog(catalogPage.value)
    }
  } catch (error) {
    detailAliasError.value = true
    detailAliasMessage.value = error?.response?.data?.message || error.message || '别名添加失败'
  } finally {
    savingDetailAlias.value = false
  }
}
async function addAssistantDetailAlias() {
  await addDetailAlias(selectedAssistantDetail.value, 'assistant')
}
async function runQuickSearch(query) {
  assistantPrompt.value = query
  await runAssistant(query)
}
async function runAssistant(promptOverride) {
  if (typeof promptOverride === 'string') assistantPrompt.value = promptOverride
  const prompt = assistantPrompt.value.trim()
  if (!prompt || assistantLoading.value) return
  activeTab.value = 'assistant'
  assistantLoading.value = true
  assistantError.value = ''
  assistantResult.value = null
  selectedAssistantDetail.value = null
  try {
    assistantResult.value = await aiRequest.post('/entity-center/assistant/search', { prompt })
  } catch (error) {
    assistantError.value = error?.response?.data?.message || error.message || '自然语言解析失败'
  } finally { assistantLoading.value = false }
}
async function initializeIndexes() {
  indexInitializing.value = true
  syncMessage.value = ''
  try {
    const response = await aiRequest.post('/entity-center/admin/indexes/initialize')
    syncMessage.value = '索引初始化完成：' + Object.entries(response || {}).map(([key, value]) => key + ' ' + value).join('，')
    syncError.value = false
    await loadSummary()
  } catch (error) {
    syncError.value = true
    syncMessage.value = error?.response?.data?.message || error.message || '索引初始化失败'
  } finally { indexInitializing.value = false }
}
async function runSync(item) {
  syncing.value = item.key
  syncMessage.value = ''
  try {
    const response = await aiRequest.post('/entity-center/admin/sync/' + item.key)
    syncMessage.value = item.key === 'available'
      ? '同步完成：' + Object.entries(response || {}).map(([key, value]) => key + ' ' + value + ' 条').join('，')
      : item.label + '完成，写入 ' + response + ' 条。'
    syncError.value = false
    await loadSummary()
    await loadCatalog(1)
  } catch (error) {
    syncError.value = true
    syncMessage.value = error?.response?.data?.message || error.message || '数据同步失败'
  } finally { syncing.value = '' }
}
async function saveAlias() {
  savingAlias.value = true
  try {
    await aiRequest.post('/entity-center/aliases', { ...aliasForm, enabled: 1, source: 'MANUAL' })
    syncMessage.value = '别名保存成功'
    syncError.value = false
    aliasForm.aliasName = ''
    if (lookupDataId.value === aliasForm.dataId) await loadAliases()
    await loadCatalog(catalogPage.value)
    if (selectedDetail.value?.entityId === aliasForm.dataId) await loadDetail(aliasForm.dataId, selectedDetail.value.entityType)
  } catch (error) {
    syncMessage.value = error?.response?.data?.message || error.message || '别名保存失败'
    syncError.value = true
  } finally { savingAlias.value = false }
}
async function loadAliases() {
  loadingAliases.value = true
  aliasQueried.value = false
  try {
    aliases.value = await aiRequest.get('/entity-center/aliases', {
      params: { entityType: aliasForm.entityType, dataId: lookupDataId.value }
    })
    aliasQueried.value = true
  } catch (error) {
    aliases.value = []
    aliasQueried.value = true
    syncError.value = true
    syncMessage.value = error?.response?.data?.message || error.message || '别名查询失败'
  } finally { loadingAliases.value = false }
}
watch(activeType, type => { aliasForm.entityType = type })
onMounted(async () => {
  await loadSummary()
  await loadCatalog(1)
})
</script>

<style scoped>
.entity-center { color:#eaf3ff; display:flex; flex-direction:column; gap:22px; min-width:0; }
.page-header { display:flex; align-items:flex-start; justify-content:space-between; gap:18px;height: 110px; }
.eyebrow,.section-kicker { color:#51c5f3; font-size:10px; letter-spacing:1.8px; font-weight:800; }
.page-header h2 { margin:7px 0 0; color:#f4f8ff; font-size:24px; letter-spacing:.2px; }
.page-header p,.panel2-head p { margin:7px 0 0; color:rgba(225,239,255,.56); font-size:12px; line-height:1.6; }
.header-actions { display:flex; align-items:center; gap:10px; }
.status-pill { color:#8ce0be; font-size:10px; border:1px solid rgba(90,214,165,.22); background:rgba(57,178,128,.08); border-radius:20px; padding:7px 10px; white-space:nowrap; }
.status-pill i { display:inline-block; width:6px; height:6px; border-radius:50%; background:#5ad5a0; margin-right:6px; }
.summary-grid { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)) 1.1fr; gap:10px; }
.summary-card,.summary-total { min-width:0; border:1px solid rgba(133,185,229,.15); background:linear-gradient(145deg,rgba(9,39,74,.86),rgba(4,23,49,.9)); border-radius:12px; padding:13px 14px; text-align:left; color:#eaf3ff; display:flex; flex-direction:column; align-items:flex-start; gap:5px; }
.summary-card { cursor:pointer; transition:.18s ease; }
.summary-card:hover,.summary-card.active { border-color:rgba(69,190,244,.6); background:linear-gradient(145deg,rgba(15,61,99,.94),rgba(5,31,59,.95)); transform:translateY(-1px); }
.summary-icon { color:#5bcaf5; font-size:18px; }
.summary-label { color:rgba(225,239,255,.68); font-size:11px; }
.summary-card strong,.summary-total strong { font-size:25px; line-height:1.2; font-weight:750; font-variant-numeric:tabular-nums; }
.summary-card small,.summary-total small { color:rgba(225,239,255,.35); font-size:9px; overflow:hidden; text-overflow:ellipsis; max-width:100%; }
.summary-total { justify-content:center; background:linear-gradient(145deg,rgba(15,71,106,.9),rgba(5,33,61,.96)); }
.workspace { display:flex; flex-direction:column; gap:12px; }
.section-title { display:flex; align-items:flex-end; justify-content:space-between; gap:14px; }
.section-title h3 { margin:4px 0 0; font-size:17px; color:#f2f7ff; }
.section-note { color:rgba(220,237,255,.42); font-size:10px; text-align:right; }
.section-tools { display:flex; align-items:center; gap:10px; }
.catalog-grid { display:grid; grid-template-columns:minmax(0,1.55fr) minmax(320px,1fr); gap:13px; align-items:stretch; }
.catalog-grid.expanded { position:fixed; inset:16px; z-index:1000; padding:54px 18px 18px; overflow:auto; background:#06182e; border:1px solid rgba(105,190,235,.24); border-radius:14px; box-shadow:0 24px 90px rgba(0,0,0,.55); grid-template-columns:minmax(0,1.55fr) minmax(360px,1fr); }
.expand-close { position:absolute; top:12px; right:18px; z-index:3; }
.catalog-grid.expanded .catalog-panel2,.catalog-grid.expanded .detail-panel2 { max-height:calc(100vh - 54px); overflow:auto; }
.panel2 { min-width:0; border:1px solid rgba(132,190,232,.14); border-radius:13px; padding:17px; background:rgba(5,30,62,.76); box-shadow:0 12px 34px rgba(0,0,0,.1); }
.catalog-toolbar { display:flex; flex-direction:column; gap:12px; }
.type-tabs { display:flex; flex-wrap:wrap; gap:6px; }
.type-tabs button { color:rgba(224,239,255,.62); background:rgba(255,255,255,.035); border:1px solid rgba(140,190,228,.13); border-radius:6px; padding:7px 11px; font-size:10px; }
.type-tabs button.selected { color:#8cddff; border-color:rgba(69,190,244,.48); background:rgba(48,161,216,.1); }
.search-line,.alias-query { display:flex; gap:8px; }
input,textarea,select { box-sizing:border-box; width:100%; color:#eaf3ff; background:rgba(1,17,39,.66); border:1px solid rgba(142,190,228,.2); border-radius:7px; padding:9px 11px; font-size:11px; outline:none; }
input:focus,textarea:focus,select:focus { border-color:rgba(69,190,244,.72); box-shadow:0 0 0 2px rgba(69,190,244,.1); }
input::placeholder,textarea::placeholder { color:rgba(211,229,248,.32); }
.btn { border-radius:7px; min-height:34px; padding:0 13px; font-size:10px; font-weight:700; white-space:nowrap; cursor:pointer; }
.btn:disabled { opacity:.45; cursor:not-allowed; }
.btn.primary { color:#06243d; border:0; background:linear-gradient(135deg,#62d6ff,#32aaf0); }
.btn.ghost { color:#cde8fc; background:rgba(255,255,255,.045); border:1px solid rgba(143,193,230,.18); }
.table-wrap { margin-top:14px; overflow:auto; max-height:520px; border:1px solid rgba(142,190,228,.1); border-radius:8px; }
table { border-collapse:collapse; width:100%; min-width:650px; font-size:10px; }
th { text-align:left; padding:10px; color:rgba(225,239,255,.48); background:rgba(255,255,255,.035); font-weight:600; position:sticky; top:0; }
td { padding:10px; border-top:1px solid rgba(142,190,228,.09); color:#dcecff; }
tbody tr { cursor:pointer; } tbody tr:hover,tbody tr.chosen { background:rgba(43,151,207,.09); }
td strong,td small { display:block; } td strong { font-size:11px; } td small { color:rgba(225,239,255,.38); margin-top:4px; font-size:9px; }
td code,.detail-id,.field-row code { color:#9bc8e9; font-size:9px; overflow-wrap:anywhere; }
.alias-inline { color:#8bd6f4; }.muted { color:rgba(225,239,255,.38); font-size:10px; }
.text-action { color:#69cef6; background:none; border:0; font-size:10px; white-space:nowrap; }
.empty-cell,.detail-empty { color:rgba(225,239,255,.38); text-align:center; padding:26px 12px; font-size:11px; }
.pagination { display:flex; justify-content:space-between; align-items:center; gap:10px; margin-top:12px; color:rgba(225,239,255,.42); font-size:10px; }
.pagination>div { display:flex; gap:6px; }
.panel2-head { display:flex; justify-content:space-between; align-items:center; gap:10px; margin-bottom:14px; }
.panel2-head h3 { color:#f1f7ff; margin:0; font-size:14px; }
.detail-type,.ai-tag,.result-count { color:#80d8ff; border:1px solid rgba(76,185,239,.2); border-radius:20px; padding:5px 8px; font-size:9px; white-space:nowrap; }
.detail-content { max-height:700px; overflow:auto; }
.detail-name { color:#f2f8ff; font-size:17px; font-weight:750; }.detail-id { margin-top:5px; }
.detail-alias-heading { display:flex; align-items:center; justify-content:space-between; gap:8px; }
.detail-alias-heading h4 { margin-bottom:9px; }
.detail-alias-form { display:flex; gap:7px; margin-top:10px; }
.detail-alias-form input { min-width:0; flex:1; }
.detail-alias-form .btn { flex:none; }
.detail-alias-message { margin:7px 0 0; color:#71d9b2; font-size:10px; }
.detail-alias-message.error { color:#ffabab; }
.relation-list { display:flex; flex-direction:column; gap:8px; }
.relation-card { padding:10px; border:1px solid rgba(142,190,228,.12); border-radius:8px; background:rgba(1,17,39,.22); }
.relation-heading { display:flex; align-items:center; justify-content:space-between; gap:8px; }
.relation-heading strong { color:#8ed9fa; font-size:10px; }
.relation-heading span { font-size:9px; }
.relation-found { color:#71d9b2; }.relation-missing { color:#ffabab; }
.relation-name { margin-top:7px; color:#eaf3ff; font-size:11px; font-weight:650; }
.relation-id { display:block; margin-top:4px; color:#9bc8e9; font-size:9px; overflow-wrap:anywhere; }
.relation-aliases { margin-top:8px; }.relation-aliases>small { color:rgba(225,239,255,.42); font-size:9px; }
.detail-block { margin-top:16px; padding-top:12px; border-top:1px solid rgba(142,190,228,.12); }
.detail-block h4 { margin:0 0 9px; font-size:11px; color:#d8eaff; }.detail-block h4 small { color:rgba(225,239,255,.4); font-weight:400; margin-left:5px; }
.chips { display:flex; flex-wrap:wrap; gap:6px; }.chips span { color:#bce8ff; background:rgba(55,153,205,.09); border:1px solid rgba(86,188,236,.2); border-radius:18px; padding:6px 9px; font-size:9px; }.chips small { color:rgba(225,239,255,.4); margin-left:4px; }
.pinyin-chips span { color:#a8e4ca; border-color:rgba(90,214,165,.18); background:rgba(57,178,128,.07); }
.field-list { border:1px solid rgba(142,190,228,.1); border-radius:7px; overflow:hidden; }
.field-row { display:grid; grid-template-columns:minmax(110px,.8fr) minmax(0,1.5fr); gap:8px; padding:7px 9px; border-top:1px solid rgba(142,190,228,.08); }
.field-row:first-child { border-top:0; }.field-row>span { color:rgba(225,239,255,.5); font-size:9px; }.field-row code { text-align:right; }
.assistant-composer { padding:14px; border:1px solid rgba(69,190,244,.24); border-radius:10px; background:linear-gradient(135deg,rgba(20,73,112,.28),rgba(5,24,49,.38)); }
.composer-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; margin-bottom:10px; }
.composer-heading strong { display:block; color:#e8f6ff; font-size:12px; }
.composer-heading p { margin:5px 0 0; color:rgba(225,239,255,.48); font-size:10px; line-height:1.5; }
.composer-shortcut { color:#8ed9fa; font-size:9px; white-space:nowrap; }
.prompt-row { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:10px; align-items:stretch; }
.prompt-row textarea { resize:vertical; min-height:84px; padding:12px 13px; line-height:1.65; font-size:12px; }
.assistant-submit { min-width:138px; align-self:stretch; }
.assistant-columns { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-top:16px; }
.assistant-result { min-width:0; border:1px solid rgba(142,190,228,.12); border-radius:9px; padding:13px; background:rgba(1,17,39,.22); }
.parsed-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; }.parsed-grid>div { display:flex; flex-direction:column; gap:5px; min-width:0; }.parsed-grid small { color:rgba(225,239,255,.42); font-size:9px; }.parsed-grid strong,.parsed-grid code { color:#d9edff; font-size:10px; overflow-wrap:anywhere; }
.filter-warning { margin-top:12px; padding:9px 10px; border:1px solid rgba(255,185,83,.24); border-radius:7px; background:rgba(255,185,83,.07); color:#ffd18b; font-size:10px; line-height:1.5; }
.search-strategy-note { margin-top:11px; padding:9px 10px; border:1px solid rgba(69,190,244,.16); border-radius:7px; background:rgba(69,190,244,.045); color:rgba(207,231,250,.62); font-size:9px; line-height:1.6; }
.filter-resolution-list { display:flex; flex-direction:column; gap:6px; margin-top:10px; }
.filter-resolution-list>div { display:grid; grid-template-columns:60px auto 1fr; gap:8px; align-items:center; font-size:9px; }
.filter-resolution-list strong { color:rgba(225,239,255,.7); }.filter-resolution-list small { color:rgba(225,239,255,.42); }
.filter-candidates { grid-column:1/-1; display:flex; flex-wrap:wrap; gap:5px; }.filter-candidates span { color:#ffd18b; background:rgba(255,185,83,.06); border:1px solid rgba(255,185,83,.16); border-radius:5px; padding:4px 6px; }
.resolve-FOUND { color:#71d9b2; }.resolve-AMBIGUOUS { color:#ffd18b; }.resolve-NOT_FOUND { color:#ffabab; }
.raw-json { margin-top:13px; color:#8ed9fa; font-size:10px; }.raw-json pre { white-space:pre-wrap; overflow-wrap:anywhere; color:rgba(225,239,255,.55); font-size:9px; }
.candidate-results { display:flex; flex-direction:column; gap:6px; max-height:280px; overflow:auto; }.candidate-card { display:flex; justify-content:space-between; align-items:center; gap:12px; text-align:left; border:1px solid rgba(142,190,228,.12); border-radius:7px; padding:9px 10px; color:#e8f3ff; background:rgba(255,255,255,.025); cursor:pointer; }.candidate-card:hover { border-color:rgba(69,190,244,.5); }.candidate-card strong,.candidate-card small { display:block; }.candidate-card strong { font-size:10px; }.candidate-card small { color:rgba(225,239,255,.4); margin-top:4px; font-size:9px; }.candidate-score { color:#8ed9fa; font-size:10px; }
.assistant-placeholder { margin-top:13px; border:1px dashed rgba(142,190,228,.18); border-radius:8px; padding:18px; color:rgba(225,239,255,.4); font-size:11px; text-align:center; }
.inline-error { margin-top:10px; padding:9px 11px; border-radius:7px; color:#ffaaaa; border:1px solid rgba(220,70,70,.2); background:rgba(220,70,70,.07); font-size:10px; }
.operations-grid { display:grid; grid-template-columns:1fr 1fr; gap:13px; }.sync-actions { display:flex; flex-wrap:wrap; gap:7px; }.sync-message { margin-top:11px; padding:9px 11px; border-radius:7px; color:#83e0bb; background:rgba(36,167,119,.1); font-size:10px; }.sync-message.error { color:#ffabab; background:rgba(220,70,70,.08); }
.footnote { color:rgba(225,239,255,.36); font-size:9px; line-height:1.6; margin:12px 0 0; }.alias-form { display:grid; grid-template-columns:1fr 1fr; gap:10px; }.alias-form label { display:flex; flex-direction:column; gap:6px; color:rgba(225,239,255,.56); font-size:10px; }.action-line { margin-top:11px; }.alias-query { margin-top:12px; }.alias-query input { flex:1; min-width:0; }.chips { margin-top:8px; }
@media(max-width:1250px) { .summary-grid { grid-template-columns:repeat(3,minmax(0,1fr)); }.catalog-grid { grid-template-columns:1fr; }.catalog-grid.expanded { grid-template-columns:1fr; }.detail-content { max-height:none; } }
@media(max-width:800px) { .page-header,.section-title { flex-direction:column; align-items:flex-start; }.summary-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.assistant-columns,.operations-grid { grid-template-columns:1fr; }.prompt-row { grid-template-columns:1fr; }.assistant-submit { min-height:38px; }.section-note { text-align:left; }.pagination { align-items:flex-start; flex-direction:column; } }
</style>