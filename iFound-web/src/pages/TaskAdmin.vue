<template>
  <div class="task-admin" data-theme="dark">
    <header class="ta-header">
      <div class="ta-header-inner">
        <div class="ta-brand">
          <div class="ta-logo">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="22" height="22">
              <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/>
              <rect x="9" y="3" width="6" height="4" rx="2"/>
              <path d="M9 14l2 2 4-4"/>
            </svg>
          </div>
          <div class="ta-titles">
            <h1>派发任务管理</h1>
            <span class="ta-sub">DISPATCH TASKS</span>
          </div>
        </div>
        <button class="ta-btn-ghost" @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          返回
        </button>
      </div>
    </header>

    <div class="ta-layout">
      <main class="ta-main">
        <div class="ta-toolbar">
          <div class="ta-filters">
            <select v-model="filterDispatched" @change="fetchList">
              <option value="all">全部</option>
              <option value="0">未派发</option>
              <option value="1">已派发</option>
            </select>
            <select v-model="filterDomain" @change="fetchList">
              <option value="all">全部业务域</option>
              <option value="设备">设备</option>
              <option value="能耗">能耗</option>
              <option value="环境">环境</option>
            </select>
          </div>
          <div class="ta-toolbar-right">
            <button class="ta-btn-ghost" @click="fetchList" title="刷新">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                <path d="M23 4v6h-6M1 20v-6h6"/>
                <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
              </svg>
            </button>
          </div>
        </div>

        <div class="ta-table-wrap">
          <table class="ta-table">
            <thead>
              <tr>
                <th>任务ID</th>
                <th>分区CID</th>
                <th>业务域</th>
                <th>等级</th>
                <th>问题描述</th>
                <th>目标Agent</th>
                <th>状态</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in filteredTasks" :key="task.id" :class="{ 'row-dispatched': task.dispatched === 1 }">
                <td class="td-id">{{ task.id?.substring(0, 8) }}...</td>
                <td>{{ task.subareaCid }}</td>
                <td>
                  <span class="ta-domain-tag" :class="'domain-' + getDomainClass(task.businessDomain)">
                    {{ task.businessDomain }}
                  </span>
                </td>
                <td>
                  <span class="ta-level-tag" :class="'level-' + task.level">{{ task.level }}</span>
                </td>
                <td class="td-desc" :title="task.problemDesc">{{ task.problemDesc }}</td>
                <td>{{ task.targetAgentId }}</td>
                <td>
                  <span class="ta-status-tag" :class="task.dispatched === 1 ? 'dispatched' : 'pending'">
                    {{ task.dispatched === 1 ? '已派发' : '待派发' }}
                  </span>
                </td>
                <td class="td-time">{{ formatTime(task.createTime) }}</td>
                <td>
                  <div class="ta-actions">
                    <button v-if="task.dispatched !== 1" class="ta-btn-dispatch" @click="handleDispatch(task)">
                      派发
                    </button>
                    <button class="ta-btn-detail" @click="viewDetail(task)">
                      详情
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="filteredTasks.length === 0" class="ta-empty">
            <p>暂无任务数据</p>
          </div>
        </div>
      </main>
    </div>

    <!-- Detail Dialog -->
    <div v-if="detailVisible" class="ta-dialog-mask" @click.self="detailVisible = false">
      <div class="ta-dialog">
        <div class="ta-dialog-header">
          <h3>任务详情</h3>
          <button class="ta-dialog-close" @click="detailVisible = false">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="ta-dialog-body" v-if="detailTask">
          <div class="ta-detail-grid">
            <div class="ta-detail-item">
              <label>任务ID</label>
              <span>{{ detailTask.id }}</span>
            </div>
            <div class="ta-detail-item">
              <label>分区CID</label>
              <span>{{ detailTask.subareaCid }}</span>
            </div>
            <div class="ta-detail-item">
              <label>业务域</label>
              <span>{{ detailTask.businessDomain }}</span>
            </div>
            <div class="ta-detail-item">
              <label>等级</label>
              <span>{{ detailTask.level }}</span>
            </div>
            <div class="ta-detail-item full">
              <label>问题描述</label>
              <span>{{ detailTask.problemDesc }}</span>
            </div>
            <div class="ta-detail-item full">
              <label>思考过程</label>
              <span>{{ detailTask.thinkingOverview || '-' }}</span>
            </div>
            <div class="ta-detail-item full">
              <label>问题总结</label>
              <span>{{ detailTask.problemSummary || '-' }}</span>
            </div>
            <div class="ta-detail-item full">
              <label>决策内容</label>
              <span>{{ detailTask.decisionContent || '-' }}</span>
            </div>
            <div class="ta-detail-item full">
              <label>预期成效</label>
              <span>{{ detailTask.expectedEffect || '-' }}</span>
            </div>
            <div class="ta-detail-item">
              <label>目标Agent</label>
              <span>{{ detailTask.targetAgentId }}</span>
            </div>
            <div class="ta-detail-item">
              <label>派发状态</label>
              <span>{{ detailTask.dispatched === 1 ? '已派发' : '待派发' }}</span>
            </div>
            <div class="ta-detail-item full" v-if="detailTask.dataSnapshot">
              <label>数据快照</label>
              <pre class="ta-snapshot">{{ detailTask.dataSnapshot }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import iotRequest from '@/api/request'

const router = useRouter()
const taskList = ref([])
const filterDispatched = ref('all')
const filterDomain = ref('all')
const detailVisible = ref(false)
const detailTask = ref(null)

const filteredTasks = computed(() => {
  let list = taskList.value
  if (filterDispatched.value !== 'all') {
    list = list.filter(t => t.dispatched === parseInt(filterDispatched.value))
  }
  if (filterDomain.value !== 'all') {
    list = list.filter(t => t.businessDomain === filterDomain.value)
  }
  return list
})

function goBack() {
  router.push('/dashboard')
}

async function fetchList() {
  try {
    const res = await iotRequest.get('/dispatch/list')
    if (res && (res.code === '200' || res.Status === 'Ok')) {
      taskList.value = res.data || []
    }
  } catch (e) {
    console.error('获取任务列表失败:', e)
  }
}

function getDomainClass(domain) {
  if (!domain) return 'env'
  if (domain.includes('设备')) return 'device'
  if (domain.includes('能耗')) return 'energy'
  return 'env'
}

function formatTime(timeStr) {
  if (!timeStr) return '-'
  const s = typeof timeStr === 'string' ? timeStr : String(timeStr)
  if (s.length >= 19) return s.substring(0, 19).replace('T', ' ')
  return s
}

async function handleDispatch(task) {
  try {
    const res = await iotRequest.post(`/dispatch/dispatch/${task.id}`)
    if (res && (res.code === '200' || res.Status === 'Ok')) {
      task.dispatched = 1
    } else {
      alert('派发失败: ' + (res.message || ''))
    }
  } catch (e) {
    console.error('派发请求异常:', e)
    alert('派发异常: ' + (e.message || ''))
  }
}

function viewDetail(task) {
  detailTask.value = task
  detailVisible.value = true
}

onMounted(fetchList)
</script>

<style scoped>
.task-admin {
  min-height: 100vh;
  background: #0a0e17;
  color: #e2e8f0;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
}
.ta-header {
  border-bottom: 1px solid rgba(255,255,255,0.06);
  padding: 16px 32px;
}
.ta-header-inner {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ta-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}
.ta-logo {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.ta-titles h1 {
  font-size: 18px;
  font-weight: 700;
  margin: 0;
  color: #f1f5f9;
}
.ta-sub {
  font-size: 11px;
  color: #64748b;
  letter-spacing: 1px;
}
.ta-btn-ghost {
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
.ta-btn-ghost:hover {
  border-color: rgba(255,255,255,0.2);
  color: #e2e8f0;
}
.ta-layout {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px 32px;
}
.ta-toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
}
.ta-filters {
  display: flex;
  gap: 10px;
}
.ta-filters select {
  padding: 8px 12px;
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px;
  background: #111827;
  color: #e2e8f0;
  font-size: 13px;
  outline: none;
}
.ta-toolbar-right {
  display: flex;
  gap: 10px;
}
.ta-table-wrap {
  background: #111827;
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,0.06);
  overflow: hidden;
}
.ta-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.ta-table thead {
  background: rgba(255,255,255,0.03);
}
.ta-table th {
  padding: 12px 16px;
  text-align: left;
  font-weight: 600;
  color: #94a3b8;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  white-space: nowrap;
}
.ta-table td {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.04);
  color: #cbd5e1;
}
.ta-table tbody tr:hover {
  background: rgba(255,255,255,0.02);
}
.ta-table tbody tr.row-dispatched {
  opacity: 0.6;
}
.td-id {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: #64748b;
}
.td-desc {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.td-time {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  white-space: nowrap;
}
.ta-domain-tag {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.ta-domain-tag.domain-device { background: rgba(239,68,68,0.15); color: #ef4444; }
.ta-domain-tag.domain-energy { background: rgba(245,158,11,0.15); color: #f59e0b; }
.ta-domain-tag.domain-env { background: rgba(6,182,212,0.15); color: #06b6d4; }
.ta-level-tag {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.ta-level-tag.level-高 { background: rgba(239,68,68,0.15); color: #ef4444; }
.ta-level-tag.level-中 { background: rgba(245,158,11,0.15); color: #f59e0b; }
.ta-level-tag.level-低 { background: rgba(34,197,94,0.15); color: #22c55e; }
.ta-status-tag {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.ta-status-tag.pending { background: rgba(245,158,11,0.15); color: #f59e0b; }
.ta-status-tag.dispatched { background: rgba(34,197,94,0.15); color: #22c55e; }
.ta-actions {
  display: flex;
  gap: 6px;
}
.ta-btn-dispatch {
  padding: 4px 12px;
  border: none;
  border-radius: 6px;
  background: linear-gradient(135deg, #6366f1, #818cf8);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}
.ta-btn-dispatch:hover {
  background: linear-gradient(135deg, #4f46e5, #6366f1);
}
.ta-btn-detail {
  padding: 4px 12px;
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 6px;
  background: transparent;
  color: #94a3b8;
  font-size: 11px;
  cursor: pointer;
}
.ta-btn-detail:hover {
  border-color: rgba(255,255,255,0.2);
  color: #e2e8f0;
}
.ta-empty {
  padding: 60px 0;
  text-align: center;
  color: #64748b;
}

/* Dialog */
.ta-dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.ta-dialog {
  background: #1e293b;
  border-radius: 16px;
  border: 1px solid rgba(255,255,255,0.08);
  width: 640px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}
.ta-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.ta-dialog-header h3 {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
  color: #f1f5f9;
}
.ta-dialog-close {
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  padding: 4px;
}
.ta-dialog-close:hover { color: #e2e8f0; }
.ta-dialog-body {
  padding: 20px 24px;
  overflow-y: auto;
}
.ta-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.ta-detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ta-detail-item.full {
  grid-column: 1 / -1;
}
.ta-detail-item label {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.ta-detail-item span {
  font-size: 13px;
  color: #e2e8f0;
}
.ta-snapshot {
  background: #0f172a;
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 8px;
  padding: 12px;
  font-size: 11px;
  color: #94a3b8;
  font-family: 'JetBrains Mono', monospace;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
