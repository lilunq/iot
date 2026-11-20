<template>
  <aside class="panel2 detail-panel2">
    <div class="panel2-head">
      <div><span class="section-kicker">ENTITY MODEL</span><h3>实体模型详情</h3></div>
      <span v-if="detail" class="detail-type">{{ typeLabel || detail.entityType }}</span>
    </div>
    <div v-if="loading" class="detail-empty">正在读取实体模型…</div>
    <div v-else-if="detail" class="detail-content">
      <div class="detail-name">{{ detail.name || '未命名实体' }}</div>
      <div class="detail-id">{{ detail.entityType }} · {{ detail.entityId }}</div>
      <div v-if="detail.relations?.length" class="detail-block">
        <h4>关联实体 <small>{{ detail.relations.length }} 项</small></h4>
        <div class="relation-list">
          <div v-for="relation in detail.relations" :key="relation.entityType" class="relation-card">
            <div class="relation-heading">
              <strong>{{ relation.label }}</strong>
              <span :class="relation.found ? 'relation-found' : 'relation-missing'">
                {{ relation.found ? '已关联' : '索引中未找到' }}
              </span>
            </div>
            <div class="relation-name">{{ relation.name || '未找到关联名称' }}</div>
            <code class="relation-id">{{ relation.entityId }}</code>
            <div class="relation-aliases">
              <small>业务别名</small>
              <div v-if="relation.aliases?.length" class="chips">
                <span v-for="alias in relation.aliases" :key="alias.id">{{ alias.aliasName }} <small>{{ alias.source }}</small></span>
              </div>
              <span v-else class="muted">尚未配置业务别名</span>
            </div>
          </div>
        </div>
      </div>
      <div v-if="detail.entityType === 'DEVICE' && detail.source?.device_alias" class="detail-block">
        <h4>源系统原始别名</h4>
        <div class="chips"><span>{{ detail.source.device_alias }}</span></div>
        <p class="muted">来自 SCADA 的 device_alias 字段，直接参与 ES 检索，不重复写入业务别名表。</p>
      </div>
      <div class="detail-block">
        <div class="detail-alias-heading">
          <h4>业务别名 <small>{{ detail.aliases?.length || 0 }}</small></h4>
          <span class="muted">直接为当前实体添加</span>
        </div>
        <div v-if="detail.aliases?.length" class="chips">
          <span v-for="alias in detail.aliases" :key="alias.id">{{ alias.aliasName }} <small>{{ alias.source }}</small></span>
        </div>
        <p v-else class="muted">尚未配置业务别名</p>
        <div class="detail-alias-form">
          <input :value="aliasName" aria-label="新增业务别名"
            placeholder="输入当前实体的业务别名，例如：冷机2号"
            @input="$emit('update:aliasName', $event.target.value)"
            @keydown.enter.prevent="$emit('add-alias')" />
          <button class="btn primary" :disabled="savingAlias || !aliasName.trim()" @click="$emit('add-alias')">
            {{ savingAlias ? '添加中…' : '＋ 添加别名' }}
          </button>
        </div>
        <p v-if="aliasMessage" class="detail-alias-message" :class="{ error: aliasError }">{{ aliasMessage }}</p>
      </div>
      <div class="detail-block">
        <h4>全拼检索分析</h4>
        <div v-if="detail.pinyinAvailable && detail.pinyinTokens?.length" class="chips pinyin-chips">
          <span v-for="(token, index) in detail.pinyinTokens" :key="index">{{ token }}</span>
        </div>
        <p v-else class="muted">拼音由 Elasticsearch Pinyin Analyzer 在检索时处理，当前未返回分析 token。</p>
      </div>
      <div class="detail-block">
        <h4>完整模型字段 <small>{{ Object.keys(detail.source || {}).length }} 项</small></h4>
        <div class="field-list">
          <div v-for="(value, key) in detail.source" :key="key" class="field-row">
            <span>{{ key }}</span><code>{{ displayValue(value) }}</code>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="detail-empty">{{ emptyText }}</div>
  </aside>
</template>

<script setup>
defineProps({
  detail: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  typeLabel: { type: String, default: '' },
  emptyText: { type: String, default: '从列表选择实体，查看完整模型详情。' },
  aliasName: { type: String, default: '' },
  savingAlias: { type: Boolean, default: false },
  aliasMessage: { type: String, default: '' },
  aliasError: { type: Boolean, default: false }
})
defineEmits(['update:aliasName', 'add-alias'])

function displayValue(value) {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
</script>

<style scoped>
.detail-panel2 { min-width:0; border:1px solid rgba(132,190,232,.14); border-radius:13px; padding:17px; background:rgba(5,30,62,.76); box-shadow:0 12px 34px rgba(0,0,0,.1); }
.section-kicker { color:#51c5f3; font-size:10px; letter-spacing:1.8px; font-weight:800; }
.panel2-head { display:flex; justify-content:space-between; align-items:center; gap:10px; margin-bottom:14px; }
.panel2-head h3 { color:#f1f7ff; margin:0; font-size:14px; }
.detail-type { color:#80d8ff; border:1px solid rgba(76,185,239,.2); border-radius:20px; padding:5px 8px; font-size:9px; white-space:nowrap; }
.detail-content { max-height:760px; overflow:auto; }
.detail-name { color:#f2f8ff; font-size:17px; font-weight:750; }
.detail-id { margin-top:5px; color:#9bc8e9; font-size:9px; overflow-wrap:anywhere; }
.detail-empty { color:rgba(225,239,255,.38); text-align:center; padding:26px 12px; font-size:11px; }
.detail-alias-heading { display:flex; align-items:center; justify-content:space-between; gap:8px; }
.detail-alias-heading h4 { margin-bottom:9px; }
.detail-alias-form { display:flex; gap:7px; margin-top:10px; }
.detail-alias-form input { box-sizing:border-box; min-width:0; flex:1; width:100%; color:#eaf3ff; background:rgba(1,17,39,.66); border:1px solid rgba(142,190,228,.2); border-radius:7px; padding:9px 11px; font-size:11px; outline:none; }
.detail-alias-form .btn { flex:none; }
.btn { border-radius:7px; min-height:34px; padding:0 13px; font-size:10px; font-weight:700; white-space:nowrap; cursor:pointer; }
.btn:disabled { opacity:.45; cursor:not-allowed; }
.btn.primary { color:#06243d; border:0; background:linear-gradient(135deg,#62d6ff,#32aaf0); }
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
.relation-aliases { margin-top:8px; }
.relation-aliases>small { color:rgba(225,239,255,.42); font-size:9px; }
.detail-block { margin-top:16px; padding-top:12px; border-top:1px solid rgba(142,190,228,.12); }
.detail-block h4 { margin:0 0 9px; font-size:11px; color:#d8eaff; }
.detail-block h4 small { color:rgba(225,239,255,.4); font-weight:400; margin-left:5px; }
.chips { display:flex; flex-wrap:wrap; gap:6px; margin-top:8px; }
.chips span { color:#bce8ff; background:rgba(55,153,205,.09); border:1px solid rgba(86,188,236,.2); border-radius:18px; padding:6px 9px; font-size:9px; }
.chips small { color:rgba(225,239,255,.4); margin-left:4px; }
.pinyin-chips span { color:#a8e4ca; border-color:rgba(90,214,165,.18); background:rgba(57,178,128,.07); }
.field-list { border:1px solid rgba(142,190,228,.1); border-radius:7px; overflow:hidden; }
.field-row { display:grid; grid-template-columns:minmax(90px,.8fr) minmax(0,1.5fr); gap:8px; padding:7px 9px; border-top:1px solid rgba(142,190,228,.08); }
.field-row:first-child { border-top:0; }
.field-row>span { color:rgba(225,239,255,.5); font-size:9px; }
.field-row code { color:#9bc8e9; font-size:9px; text-align:right; overflow-wrap:anywhere; }
.muted { color:rgba(225,239,255,.38); font-size:10px; }
</style>
