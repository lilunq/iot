<template>
  <div v-if="visible" class="acm-overlay" @click.self="closeAgentCollabModal">
    <div class="acm-modal">
      <div class="acm-header">
        <div class="acm-header-title">
          <div class="acm-header-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="22" height="22">
              <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/>
            </svg>
          </div>
          <h3>Agent协作任务</h3>
        </div>
        <div class="acm-header-actions">
          <button class="acm-close-btn" type="button" @click="closeAgentCollabModal">✕</button>
        </div>
      </div>

      <div class="acm-body">
        <!-- 筛选栏 -->
        <div class="acm-filter-bar">
          <div class="acm-filter-item">
            <label>区域</label>
            <select v-model="filters.subareaCid" @change="onFilterChange">
              <option value="">全部区域</option>
              <option v-for="area in areaList" :key="area.id" :value="area.cascade_id">{{ area.name }}</option>
            </select>
          </div>
          <div class="acm-filter-item">
            <label>是否派发</label>
            <select v-model="filters.dispatched" @change="onFilterChange">
              <option value="">全部</option>
              <option value="0">未派发</option>
              <option value="1">已派发</option>
              <option value="2">已取消</option>
            </select>
          </div>
          <div class="acm-filter-item">
            <label>是否完成</label>
            <select v-model="filters.resultExecuteStatus" @change="onFilterChange">
              <option value="">全部</option>
              <option value="0">未完成</option>
              <option value="1">已完成</option>
              <option value="2">失败</option>
            </select>
          </div>
          <div class="acm-filter-item">
            <label>目标Agent名称</label>
            <select v-model="filters.targetAgentName" @change="onFilterChange">
              <option value="">全部</option>
              <option value="能源管理Agent">能源管理Agent</option>
              <option value="设备运维Agent">设备运维Agent</option>
            </select>
          </div>
          <button class="acm-filter-btn" @click="onFilterChange">筛选</button>
          <button class="acm-filter-reset" @click="onFilterReset">重置</button>
        </div>

        <!-- 任务列表 -->
        <div class="acm-card">
          <div class="acm-card-head">
            <div class="acm-card-tg">
              <span class="acm-bar"></span>
              <h4>任务列表</h4>
              <span class="acm-count-badge">{{ agentCollabTotal }} 条</span>
            </div>
          </div>
          <div class="acm-card-body">
            <table class="acm-table">
              <thead>
                <tr>
                  <th>业务域</th>
                  <th>类型</th>
                  <th>等级</th>
                  <th>问题描述</th>
                  <th>决策建议</th>
                  <th>目标Agent</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="task in agentCollabTaskList" :key="task.id" :data-task-id="task.id">
                  <td>{{ task.businessDomain }}</td>
                  <td>
                    <span class="task-type-tag" :class="'tag-' + task.taskType">
                      {{ task.taskType === 'hint' ? '提示' : '协同' }}
                    </span>
                  </td>
                  <td><span class="task-level" :class="'level-' + task.level">{{ task.level }}</span></td>
                  <td class="desc-cell" :title="task.problemDesc">{{ task.problemDesc }}</td>
                  <td class="desc-cell" :title="task.decisionContent">{{ task.decisionContent }}</td>
                  <td>{{ task.targetAgentName || task.targetAgentId }}</td>
                  <td>
                    <span class="task-status" :class="task.dispatched === 1 ||task.resultExecuteStatus==1? 'dispatched' : task.dispatched === 2 ? 'cancelled' : 'pending'">
                      {{task.resultExecuteStatus==1?'已完成': task.resultExecuteStatus==2?'失败': task.dispatched === 1 ? '已采纳' : task.dispatched === 2 ? '已取消' : '待决策' }}
                    </span>
                  </td>
                  <td>
                    <div class="task-actions">
                      <button class="detail-btn" @click="openTaskDetail(task)">详情</button>
                      <template v-if="task.taskType !== 'hint' && task.resultExecuteStatus===0&&task.dispatched === 0">
                        <button class="dispatch-btn" @click.stop="handleDispatch(task)">派发</button>
                        <button class="cancel-btn" @click.stop="handleCancel(task)">取消</button>
                      </template>
                    </div>
                  </td>
                </tr>
                <tr v-if="agentCollabTaskList.length === 0">
                  <td colspan="8" class="acm-empty">暂无任务数据</td>
                </tr>
              </tbody>
            </table>
            <div class="acm-pagination">
              <button :disabled="agentCollabPage <= 1" @click="agentCollabPage--; fetchAgentCollabTasks()">上一页</button>
              <span>第 {{ agentCollabPage }} 页 / 共 {{ Math.ceil(agentCollabTotal / agentCollabPageSize) || 1 }} 页</span>
              <button :disabled="agentCollabPage >= Math.ceil(agentCollabTotal / agentCollabPageSize)" @click="agentCollabPage++; fetchAgentCollabTasks()">下一页</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 任务详情弹窗 -->
  <TaskDetailModal
    :visible="isTaskDetailModalOpen"
    :task="selectedTaskDetail"
    @close="closeTaskDetailModal"
  />

  <!-- 内联Toast通知 -->
  <Transition name="toast-slide">
    <div v-if="toastConfig.show" class="agent-toast" :class="toastConfig.type">
      <div class="agent-toast-icon">
        <svg v-if="toastConfig.type === 'success'" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.5">
          <path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
          <polyline points="22 4 12 14.01 9 11.01"/>
        </svg>
        <svg v-else-if="toastConfig.type === 'error'" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.5">
          <circle cx="12" cy="12" r="10"/>
          <line x1="15" y1="9" x2="9" y2="15"/>
          <line x1="9" y1="9" x2="15" y2="15"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.5">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
      </div>
      <div class="agent-toast-body">
        <div class="agent-toast-title">{{ toastConfig.title }}</div>
        <div v-if="toastConfig.description" class="agent-toast-desc">{{ toastConfig.description }}</div>
      </div>
      <button class="agent-toast-close" @click="toastConfig.show = false">&times;</button>
      <div class="agent-toast-progress"></div>
    </div>
  </Transition>
</template>

<script setup>
import { ref, nextTick, watch, onBeforeUnmount } from 'vue'
import gsap from 'gsap'
import { useRouter } from 'vue-router'
import { aiRequest } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { useAreaStore } from '@/stores/area'
import TaskDetailModal from '@/components/TaskDetailModal.vue'

const props = defineProps({
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['close', 'openEnergyAgent'])

const userStore = useUserStore()
const areaStore = useAreaStore()
const router = useRouter()

// 区域列表
const areaList = ref([])

// Toast
const toastConfig = ref({ show: false, type: 'info', title: '', description: '' })
let toastTimer = null

function showToast(type, title, description = '') {
  clearTimeout(toastTimer)
  toastConfig.value = { show: true, type, title, description }
  toastTimer = setTimeout(() => {
    toastConfig.value.show = false
  }, 3000)
}

// Data refs
const agentCollabPage = ref(1)
const agentCollabPageSize = 10
const agentCollabTotal = ref(0)
const agentCollabTaskList = ref([])

// 筛选条件
const filters = ref({
  dispatched: '',
  resultExecuteStatus: '',
  targetAgentName: '',
  subareaCid: ''
})

// 任务详情
const isTaskDetailModalOpen = ref(false)
const selectedTaskDetail = ref(null)

// 公开方法：供父组件调用
async function open() {
  // 从缓存获取区域列表
  await areaStore.fetchAreaList()
  areaList.value = areaStore.areaList || []
  agentCollabPage.value = 1
  await fetchAgentCollabTasks()
}

function closeAgentCollabModal() {
  emit('close')
}

async function fetchAgentCollabTasks() {
  try {
    const params = { pageNum: agentCollabPage.value, pageSize: agentCollabPageSize }
    if (filters.value.dispatched !== '') params.dispatched = filters.value.dispatched
    if (filters.value.resultExecuteStatus !== '') params.resultExecuteStatus = filters.value.resultExecuteStatus
    if (filters.value.targetAgentName) params.targetAgentName = filters.value.targetAgentName
    if (filters.value.subareaCid) params.subareaCid = filters.value.subareaCid

    const res = await aiRequest.get('/dispatch/list', { params })
    if (res && (res.code === '200' || res.code === 200) && res.data) {
      agentCollabTaskList.value = res.data.records || []
      agentCollabTotal.value = res.data.total || 0
      nextTick(() => animateTaskRows())
    }
  } catch (err) {
    console.error('获取Agent协作任务列表失败:', err)
  }
}

// 筛选变化
function onFilterChange() {
  agentCollabPage.value = 1
  fetchAgentCollabTasks()
}

function onFilterReset() {
  filters.value = { dispatched: '', resultExecuteStatus: '', targetAgentName: '', subareaCid: '' }
  agentCollabPage.value = 1
  fetchAgentCollabTasks()
}

// 任务行入场动画
function animateTaskRows() {
  const rows = document.querySelectorAll('.acm-table tbody tr')
  if (rows.length === 0) return
  gsap.fromTo(rows,
    { opacity: 0, y: 8 },
    { opacity: 1, y: 0, duration: 0.3, stagger: 0.04, ease: 'power2.out' }
  )
}

// 派发任务
async function handleDispatch(task) {
// 如果目标Agent是设备运维Agent，跳转到运维Agent页面并自动触发协作记录的派发
if (task.targetAgentId === 'ops-agent') {
emit('close')
router.push({
path: '/operations',
query: {
autoDispatchTask: 'true',
taskId: task.id || ''
}
})
return
}

try {
const taskBody = { ...task, dispatchedUser: userStore.user_name }
const res = await aiRequest.post('/dispatch/dispatch', taskBody)
if (res && (res.code === '200' || res.code === 200)) {
task.dispatched = 1
gsap.to(`[data-task-id="${task.id}"]`, {
scale: 1.02, duration: 0.15, yoyo: true, repeat: 1, ease: 'power2.out'
})
showToast('success', '派发成功', `任务已派发至${task.targetAgentName}`)
  // 如果目标Agent是能源管理Agent，通知父组件打开能源管理弹窗
  if (task.targetAgentId === 'energy-agent') {
    emit('openEnergyAgent')
    return
  }
} else {
showToast('error', '派发失败', res?.msg || '未知错误')
}
} catch (err) {
showToast('error', '后台执行中', err.message || '网络异常')
console.error('派发任务失败:', err)
}
}

// 取消任务
async function handleCancel(task) {
  try {
    const res = await aiRequest.post(`/dispatch/cancel/${task.id}`, { dispatchedUser: userStore.user_name })
    if (res && (res.code === '200' || res.code === 200)) {
      task.dispatched = 2
      gsap.to(`[data-task-id="${task.id}"]`, {
        opacity: 0.5, duration: 0.3, ease: 'power2.out'
      })
      showToast('success', '取消成功', '任务已取消')
await fetchAgentCollabTasks()
    } else {
      showToast('error', '取消失败', res?.msg || '未知错误')
    }
  } catch (err) {
    showToast('error', '取消失败', err.message || '网络异常')
    console.error('取消任务失败:', err)
  }
}

// 任务详情弹窗
function openTaskDetail(task) {
  selectedTaskDetail.value = task
  isTaskDetailModalOpen.value = true
}

function closeTaskDetailModal() {
  isTaskDetailModalOpen.value = false
  selectedTaskDetail.value = null
}

// Watch visible prop to auto-open
watch(() => props.visible, (val) => {
  if (val) {
    open()
  }
}, { immediate: true })

onBeforeUnmount(() => {
  clearTimeout(toastTimer)
})

defineExpose({ open })
</script>

<style scoped>
/* ===== Agent协作中心弹窗（与HealthModal同风格） ===== */
.acm-overlay{position:fixed;inset:0;background:rgba(0,0,0,0.5);backdrop-filter:blur(8px);display:flex;align-items:center;justify-content:center;z-index:1000;animation:acm-fade-in .25s ease}
.acm-modal{background:rgb(0,66,126);border:1px solid rgba(0,180,255,.15);border-radius:16px;width:85vw;max-width:92vw;max-height:100vh;display:flex;flex-direction:column;box-shadow:0 24px 80px rgba(0,0,0,.6),0 0 1px rgba(0,180,255,.3),inset 0 1px 0 rgba(255,255,255,.05);animation:acm-scale-in .35s cubic-bezier(.16,1,.3,1);overflow:hidden;position:relative}
.acm-modal::before{content:'';position:absolute;top:0;left:0;right:0;height:2px;background:linear-gradient(90deg,transparent,rgba(0,180,255,.6),rgba(96,165,250,.4),rgba(0,180,255,.6),transparent);z-index:10}
.acm-header{display:flex;justify-content:space-between;align-items:center;padding:16px 24px;border-bottom:1px solid rgba(255,255,255,.06);background:linear-gradient(180deg,rgba(0,0,0,.15),transparent);flex-shrink:0}
.acm-header-title{display:flex;align-items:center;gap:12px}
.acm-header-icon{width:36px;height:36px;border-radius:10px;background:linear-gradient(135deg,rgba(59,130,246,.2),rgba(96,165,250,.08));border:1px solid rgba(59,130,246,.3);display:flex;align-items:center;justify-content:center;color:#60a5fa;animation:acm-icon-pulse 3s ease-in-out infinite}
@keyframes acm-icon-pulse{0%,100%{box-shadow:0 0 8px rgba(59,130,246,.3)}50%{box-shadow:0 0 16px rgba(59,130,246,.5),0 0 24px rgba(96,165,250,.2)}}
.acm-header h3{margin:0;font-size:19px;font-weight:700;color:#e8f0fe;letter-spacing:.5px}
.acm-header-actions{display:flex;align-items:center;gap:8px}
.acm-close-btn{background:none;border:none;color:rgba(255,255,255,.35);font-size:20px;cursor:pointer;width:34px;height:34px;border-radius:8px;transition:all .2s;line-height:1;display:flex;align-items:center;justify-content:center}
.acm-close-btn:hover{color:#fff;background:rgba(255,255,255,.08)}

.acm-body{flex:1;overflow-y:auto;padding:16px 20px 20px;display:flex;flex-direction:column;gap:14px}
.acm-body::-webkit-scrollbar{width:6px}
.acm-body::-webkit-scrollbar-track{background:transparent}
.acm-body::-webkit-scrollbar-thumb{background:rgba(255,255,255,.12);border-radius:3px}
.acm-body::-webkit-scrollbar-thumb:hover{background:rgba(255,255,255,.22)}

/* ===== 筛选栏 ===== */
.acm-filter-bar{display:flex;flex-wrap:wrap;align-items:flex-end;gap:12px;padding:14px 18px;background:rgba(255,255,255,.025);border:1px solid rgba(255,255,255,.07);border-radius:12px}
.acm-filter-item{display:flex;flex-direction:column;gap:4px}
.acm-filter-item label{font-size:11px;color:rgba(255,255,255,.5);font-weight:600}
.acm-filter-item select,.acm-filter-item input{background:rgba(0,0,0,.2);border:1px solid rgba(255,255,255,.1);border-radius:6px;color:rgba(255,255,255,.85);font-size:12px;padding:6px 10px;outline:none;transition:border-color .2s;min-width:120px}
.acm-filter-item select:focus,.acm-filter-item input:focus{border-color:rgba(59,130,246,.5)}
.acm-filter-item select option{background:#0a1530;color:#e8f0fe}
.acm-filter-btn{padding:6px 18px;background:rgba(59,130,246,.12);border:1px solid rgba(59,130,246,.3);border-radius:6px;color:#60a5fa;font-size:12px;cursor:pointer;transition:all .2s;height:30px}
.acm-filter-btn:hover{background:rgba(59,130,246,.22);border-color:rgba(59,130,246,.5);box-shadow:0 0 8px rgba(59,130,246,.3)}
.acm-filter-reset{padding:6px 14px;background:rgba(255,255,255,.04);border:1px solid rgba(255,255,255,.1);border-radius:6px;color:rgba(255,255,255,.5);font-size:12px;cursor:pointer;transition:all .2s;height:30px}
.acm-filter-reset:hover{color:#fff;border-color:rgba(255,255,255,.3)}

/* ===== 卡片 ===== */
.acm-card{background:rgba(255,255,255,.025);border:1px solid rgba(255,255,255,.07);border-radius:12px;overflow:hidden;transition:border-color .3s}
.acm-card:hover{border-color:rgba(59,130,246,.2)}
.acm-card-head{display:flex;align-items:center;justify-content:space-between;padding:12px 18px;border-bottom:1px solid rgba(255,255,255,.05)}
.acm-card-tg{display:flex;align-items:center;gap:10px}
.acm-bar{width:3px;height:15px;border-radius:2px;background:#3b82f6;flex-shrink:0}
.acm-card-head h4{margin:0;font-size:15px;font-weight:700;color:rgba(255,255,255,.9);letter-spacing:.3px}
.acm-count-badge{font-size:12px;color:rgba(255,255,255,.4);padding:2px 8px;background:rgba(255,255,255,.05);border-radius:6px}
.acm-card-body{padding:0 18px 16px}

/* ===== 表格 ===== */
.acm-table{width:100%;border-collapse:collapse;font-size:22px;margin-top:12px}
.acm-table th{background:rgba(255,255,255,.04);padding:9px 10px;text-align:left;color:rgba(255,255,255,.5);font-weight:600;border-bottom:1px solid rgba(255,255,255,.08);white-space:nowrap}
.acm-table td{padding:9px 10px;border-bottom:1px solid rgba(255,255,255,.05);color:rgba(255,255,255,.72);transition:background .2s}
.acm-table tbody tr:hover td{background:rgba(59,130,246,.04)}
.acm-table .desc-cell{max-width:220px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.acm-table th:last-child,.acm-table td:last-child{width:1%;white-space:nowrap}
.acm-empty{text-align:center;color:rgba(255,255,255,.35);padding:32px 0;font-size:13px}

.task-type-tag{display:inline-block;padding:2px 8px;border-radius:4px;font-size:11px;font-weight:600}
.tag-hint{background:rgba(96,165,250,.12);color:#60a5fa;border:1px solid rgba(96,165,250,.25)}
.tag-collaboration{background:rgba(139,92,246,.12);color:#a78bfa;border:1px solid rgba(139,92,246,.25)}

.task-level{display:inline-block;padding:2px 8px;border-radius:4px;font-size:11px;font-weight:600}
.level-紧急{background:rgba(239,68,68,.12);color:#ef4444;border:1px solid rgba(239,68,68,.25)}
.level-注意{background:rgba(245,158,11,.12);color:#f59e0b;border:1px solid rgba(245,158,11,.25)}
.level-正常{background:rgba(16,185,129,.12);color:#10b981;border:1px solid rgba(16,185,129,.25)}

.task-status{display:inline-block;padding:2px 8px;border-radius:4px;font-size:11px;font-weight:600}
.task-status.dispatched{color:#10b981;background:rgba(16,185,129,.1)}
.task-status.cancelled{color:#ef4444;opacity:.6;background:rgba(239,68,68,.1)}
.task-status.pending{color:#f59e0b;background:rgba(245,158,11,.1)}

.task-actions{display:flex;gap:6px;align-items:center}
.detail-btn{background:none;border:1px solid rgba(96,165,250,.4);color:#60a5fa;padding:6px 16px;border-radius:6px;cursor:pointer;font-size:14px;transition:all .2s}
.detail-btn:hover{background:rgba(96,165,250,.15)}
.dispatch-btn{background:none;border:1px solid rgba(34,197,94,.4);color:#22c55e;padding:6px 16px;border-radius:6px;cursor:pointer;font-size:14px;transition:all .2s}
.dispatch-btn:hover{background:rgba(34,197,94,.15)}
.cancel-btn{background:none;border:1px solid rgba(239,68,68,.4);color:#ef4444;padding:6px 16px;border-radius:6px;cursor:pointer;font-size:14px;transition:all .2s}
.cancel-btn:hover{background:rgba(239,68,68,.15)}

/* ===== 分页 ===== */
.acm-pagination{display:flex;align-items:center;justify-content:center;gap:12px;padding:14px 0 4px;font-size:12px;color:rgba(255,255,255,.45)}
.acm-pagination button{background:rgba(255,255,255,.04);border:1px solid rgba(255,255,255,.08);color:rgba(255,255,255,.7);padding:5px 14px;border-radius:6px;cursor:pointer;transition:all .2s}
.acm-pagination button:disabled{opacity:.35;cursor:not-allowed}
.acm-pagination button:not(:disabled):hover{border-color:rgba(59,130,246,.4);color:#60a5fa}

/* ===== 内联Toast ===== */
.agent-toast{position:fixed;top:20px;left:50%;transform:translateX(-50%);z-index:99999;display:flex;align-items:flex-start;gap:10px;min-width:320px;max-width:460px;padding:14px 40px 14px 16px;border-radius:10px;background:rgba(15,25,45,.95);backdrop-filter:blur(12px);border:1px solid rgba(255,255,255,.08);box-shadow:0 8px 32px rgba(0,0,0,.4);overflow:hidden}
.agent-toast.success{border-color:rgba(16,185,129,.3)}
.agent-toast.success .agent-toast-icon{color:#10b981}
.agent-toast.success .agent-toast-progress{background:linear-gradient(90deg,#10b981,#059669)}
.agent-toast.error{border-color:rgba(239,68,68,.3)}
.agent-toast.error .agent-toast-icon{color:#ef4444}
.agent-toast.error .agent-toast-progress{background:linear-gradient(90deg,#ef4444,#dc2626)}
.agent-toast.info{border-color:rgba(59,130,246,.3)}
.agent-toast.info .agent-toast-icon{color:#3b82f6}
.agent-toast.info .agent-toast-progress{background:linear-gradient(90deg,#3b82f6,#2563eb)}
.agent-toast-icon{flex-shrink:0;display:flex;align-items:center;margin-top:1px}
.agent-toast-body{flex:1;min-width:0}
.agent-toast-title{font-size:14px;font-weight:600;color:#f0f4f8;line-height:1.4}
.agent-toast-desc{font-size:12px;color:#8899aa;margin-top:4px;line-height:1.5;word-break:break-all}
.agent-toast-close{position:absolute;top:8px;right:10px;background:none;border:none;color:#556677;font-size:18px;cursor:pointer;line-height:1;padding:2px 4px;transition:color .2s}
.agent-toast-close:hover{color:#aabbcc}
.agent-toast-progress{position:absolute;bottom:0;left:0;height:3px;width:100%;animation:toast-shrink 3s linear forwards;transform-origin:left}
@keyframes toast-shrink{from{transform:scaleX(1)}to{transform:scaleX(0)}}
.toast-slide-enter-active{transition:all .35s cubic-bezier(.22,1,.36,1)}
.toast-slide-leave-active{transition:all .3s cubic-bezier(.4,0,1,1)}
.toast-slide-enter-from{opacity:0;transform:translateX(-50%) translateY(-30px)}
.toast-slide-leave-to{opacity:0;transform:translateX(-50%) translateY(-20px)}

@keyframes acm-fade-in{from{opacity:0}to{opacity:1}}
@keyframes acm-scale-in{from{opacity:0;transform:scale(.95) translateY(10px)}to{opacity:1;transform:scale(1) translateY(0)}}
</style>
