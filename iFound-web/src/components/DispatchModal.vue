<script setup>
import { ref, nextTick } from 'vue'

const emit = defineEmits(['close', 'confirm'])

const isDispatchModalOpen = ref(false)
const dispatchModalFromDeviceAnalysis = ref(false)
const dispatchModalType = ref('repair')
const dispatchOrder = ref({
  deviceName: '',
  deviceId: '',
  deviceType: '',
  deviceLocation: '',
  orderNumber: '',
  faultReason: '',
  repairSteps: '',
  recommendedPerson: null,
  notes: ''
})
const recommendedPerson = ref(null)
const dispatchFaultText = ref('')
const dispatchStepsText = ref('')
const dispatchFaultComplete = ref(false)
const dispatchStepsComplete = ref(false)
let dispatchFaultTimer = null
let dispatchStepsTimer = null

function open(action, modalType = 'repair', deviceInfo = null) {
  dispatchModalType.value = modalType

  if (!deviceInfo) {
    deviceInfo = {
      name: action.title.split(' ')[0],
      id: 'DEV-UNKNOWN',
      type: '未知设备',
      location: '待确认',
      zoneName: '未知区域'
    }
  }

  const orderNum = 'WO-' + Date.now().toString().slice(-8)
  dispatchOrder.value = {
    deviceName: deviceInfo.name,
    deviceId: deviceInfo.id,
    deviceType: deviceInfo.type || '未知',
    deviceLocation: deviceInfo.location || '待确认',
    orderNumber: orderNum,
    faultReason: '',
    repairSteps: '',
    recommendedPerson: null,
    notes: ''
  }

  recommendedPerson.value = {
    name: '张工',
    title: '高级维修工程师',
    employeeId: '工号169',
    phone: '138-0013-8000',
    avatar: null,
    matchRate: 95,
    status: '空闲',
    currentTasks: 1,
    reason: '该维修人员擅长处理' + (deviceInfo.type || '相关') + '设备故障，当前状态空闲，距离' + (deviceInfo.zoneName || '该区域') + '最近，历史维修成功率98.5%'
  }

  dispatchFaultText.value = ''
  dispatchStepsText.value = ''
  dispatchFaultComplete.value = false
  dispatchStepsComplete.value = false

  isDispatchModalOpen.value = true

  let faultText, stepsText
  if (modalType === 'repair') {
    faultText = `根据设备${deviceInfo.name}（${deviceInfo.id}）的运行数据分析，当前故障原因为：${action.detail}。该设备位于${deviceInfo.zoneName}，建议尽快安排维修人员前往现场处理。`
    stepsText = `维修步骤：1. 到达现场后先确认设备状态指示灯；2. 使用万用表检测电路电压是否正常；3. 根据故障代码查阅维修手册；4. 更换故障部件或进行参数校准；5. 重启设备并观察运行状态30分钟；6. 填写维修记录并上报系统。`
  } else if (modalType === 'check') {
    faultText = `根据设备${deviceInfo.name}（${deviceInfo.id}）的运行数据分析，当前告警信息为：${action.detail}。该设备位于${deviceInfo.zoneName}，建议尽快安排人员前往现场排查确认。`
    stepsText = `排查步骤：1. 到达现场后确认设备运行状态；2. 检查设备参数是否在正常范围内；3. 查看设备日志和告警记录；4. 使用检测工具测量关键指标；5. 确认是否存在真实故障或误报；6. 填写排查记录并上报结果。`
  } else {
    faultText = `根据设备${deviceInfo.name}（${deviceInfo.id}）的运行数据分析，该设备位于${deviceInfo.zoneName}，当前运行状态正常。建议安排巡检人员前往现场进行例行检查，确保设备持续稳定运行。`
    stepsText = `巡检步骤：1. 到达现场后记录设备外观状态；2. 检查设备运行参数是否在正常范围；3. 检测设备温度和振动情况；4. 清理设备表面和周围环境；5. 记录巡检数据并拍照存档；6. 填写巡检报告并上报系统。`
  }
  startDispatchFaultTypewriter(faultText)

  setTimeout(() => {
    startDispatchStepsTypewriter(stepsText)
  }, faultText.length * 25 + 500)
}

function startDispatchFaultTypewriter(text) {
  clearDispatchTypewriters()
  dispatchFaultText.value = ''
  dispatchFaultComplete.value = false
  let idx = 0
  function tick() {
    if (idx < text.length) {
      dispatchFaultText.value += text[idx]
      idx++
      dispatchFaultTimer = setTimeout(tick, 25)
    } else {
      dispatchFaultComplete.value = true
    }
  }
  tick()
}

function startDispatchStepsTypewriter(text) {
  dispatchStepsText.value = ''
  dispatchStepsComplete.value = false
  let idx = 0
  function tick() {
    if (idx < text.length) {
      dispatchStepsText.value += text[idx]
      idx++
      dispatchStepsTimer = setTimeout(tick, 25)
    } else {
      dispatchStepsComplete.value = true
    }
  }
  tick()
}

function clearDispatchTypewriters() {
  if (dispatchFaultTimer) {
    clearTimeout(dispatchFaultTimer)
    dispatchFaultTimer = null
  }
  if (dispatchStepsTimer) {
    clearTimeout(dispatchStepsTimer)
    dispatchStepsTimer = null
  }
}

function closeDispatchModal() {
  isDispatchModalOpen.value = false
  clearDispatchTypewriters()
  if (dispatchModalFromDeviceAnalysis.value) {
    dispatchModalFromDeviceAnalysis.value = false
  }
  emit('close')
}

function confirmDispatchOrder() {
  dispatchOrder.value.faultReason = dispatchFaultText.value
  dispatchOrder.value.repairSteps = dispatchStepsText.value
  emit('confirm', { ...dispatchOrder.value })
  closeDispatchModal()
}

function cancelDispatchOrder() {
  closeDispatchModal()
}

defineExpose({ open })
</script>

<template>
  <div v-if="isDispatchModalOpen" class="dispatch-modal-overlay" @click.self="closeDispatchModal">
    <div class="dispatch-modal-content">
      <div class="dispatch-modal-header">
        <div class="dispatch-header-left">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24">
            <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
            <line x1="8" y1="21" x2="16" y2="21"/>
            <line x1="12" y1="17" x2="12" y2="21"/>
          </svg>
          <div>
            <h3>设备诊断与派单</h3>
            <p class="dispatch-subtitle">告警分析 · 处置建议 · 工单下发</p>
          </div>
        </div>
        <button class="dispatch-modal-close" @click="closeDispatchModal">×</button>
      </div>

      <div class="dispatch-modal-body">
        <div class="dispatch-three-cols">
          <div class="dispatch-col dispatch-col-left">
            <div class="col-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
                <line x1="8" y1="21" x2="16" y2="21"/>
                <line x1="12" y1="17" x2="12" y2="21"/>
              </svg>
              设备概况
            </div>
            <div class="col-content">
              <div class="overview-row">
                <span class="overview-label">设备名称：</span>
                <span class="overview-value">{{ dispatchOrder.deviceName }}</span>
              </div>
              <div class="overview-row">
                <span class="overview-label">所属区域：</span>
                <span class="overview-value">{{ dispatchOrder.deviceLocation }}</span>
              </div>
              <div class="overview-row">
                <span class="overview-label">当前状态：</span>
                <span class="status-badge status-alert">告警中</span>
              </div>
              <div class="overview-row">
                <span class="overview-label">告警等级：</span>
                <span class="level-badge level-high">高</span>
              </div>
              <div class="overview-row">
                <span class="overview-label">异常类型：</span>
                <span class="overview-value">振动超标、回风温度偏高</span>
              </div>
              <div class="realtime-divider"></div>
              <div class="realtime-title">实时数据</div>
              <div class="realtime-grid">
                <div class="realtime-item">
                  <span class="realtime-label">振动值</span>
                  <span class="realtime-value realtime-warn">4.8 mm/s</span>
                </div>
                <div class="realtime-item">
                  <span class="realtime-label">电机温度</span>
                  <span class="realtime-value realtime-warn">68°C</span>
                </div>
                <div class="realtime-item">
                  <span class="realtime-label">运行电流</span>
                  <span class="realtime-value">12.5 A</span>
                </div>
                <div class="realtime-item">
                  <span class="realtime-label">回风温度</span>
                  <span class="realtime-value realtime-warn">28.5°C</span>
                </div>
                <div class="realtime-item">
                  <span class="realtime-label">转速</span>
                  <span class="realtime-value">1450 rpm</span>
                </div>
                <div class="realtime-item">
                  <span class="realtime-label">运行时长</span>
                  <span class="realtime-value">2360 h</span>
                </div>
              </div>
            </div>
          </div>

          <div class="dispatch-col dispatch-col-middle">
            <div class="col-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
              </svg>
              诊断结果
            </div>
            <div class="col-content">
              <div class="diagnosis-summary">
                <span class="diagnosis-label">综合判断：</span>
                <span class="diagnosis-value">疑似轴承磨损与联轴器偏移</span>
              </div>
              <div class="confidence-row">
                <span class="confidence-label">诊断置信度：</span>
                <span class="confidence-value">92%</span>
              </div>
              <div class="diagnosis-divider"></div>
              <ul class="diagnosis-list">
                <li>振动值 4.8mm/s，持续升高</li>
                <li>电机电流波动异常</li>
                <li>回风温度 28.5°C，高于阈值</li>
                <li>历史相似故障 3 次</li>
              </ul>
              <div class="diagnosis-divider"></div>
              <div class="suggestion-section">
                <div class="suggestion-title">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                    <line x1="16" y1="13" x2="8" y2="13"/>
                    <line x1="16" y1="17" x2="8" y2="17"/>
                    <polyline points="10 9 9 9 8 9"/>
                  </svg>
                  处置建议
                </div>
                <ul class="suggestion-list">
                  <li>立即安排巡检确认轴承与联轴器状态</li>
                  <li>检查空调回风系统与风机运行状态</li>
                  <li>若振动继续上升，建议停机检修</li>
                </ul>
              </div>
            </div>
          </div>

          <div class="dispatch-col dispatch-col-right">
            <div class="col-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
              派单信息
            </div>
            <div class="col-content">
              <div class="dispatch-info-row">
                <span class="info-label">工单编号：</span>
                <span class="info-value">{{ dispatchOrder.orderNumber }}</span>
              </div>
              <div class="dispatch-info-row">
                <span class="info-label">工单类型：</span>
                <span class="info-value">设备故障处理</span>
              </div>
              <div class="dispatch-info-row">
                <span class="info-label">紧急程度：</span>
                <span class="urgency-badge urgency-urgent">紧急</span>
              </div>
              <div class="dispatch-info-row">
                <span class="info-label">建议班组：</span>
                <span class="info-value">机电维修一班</span>
              </div>
              <div class="dispatch-info-row">
                <span class="info-label">推荐人员：</span>
                <span class="info-value">{{ recommendedPerson ? recommendedPerson.name : '张工 / 李工' }}</span>
              </div>
              <div class="dispatch-info-row">
                <span class="info-label">技能匹配度：</span>
                <span class="match-value">{{ recommendedPerson ? recommendedPerson.matchRate : '96' }}%</span>
              </div>
              <div class="dispatch-info-row">
                <span class="info-label">预计到场：</span>
                <span class="info-value">8分钟</span>
              </div>
              <div class="dispatch-info-row">
                <span class="info-label">预计工时：</span>
                <span class="info-value">1.5小时</span>
              </div>
              <div class="dispatch-info-row">
                <span class="info-label">所需工具：</span>
                <span class="info-value">测振仪、扳手、红外测温仪</span>
              </div>
              <div class="dispatch-divider"></div>
              <div class="link-section">
                <span class="link-label">联动查看</span>
                <div class="link-buttons">
                  <button class="link-btn">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                      <line x1="18" y1="20" x2="18" y2="10"/>
                      <line x1="12" y1="20" x2="12" y2="4"/>
                      <line x1="6" y1="20" x2="6" y2="14"/>
                    </svg>
                    历史趋势
                  </button>
                  <button class="link-btn">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                      <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                      <circle cx="12" cy="10" r="3"/>
                    </svg>
                    BIM定位
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="dispatch-modal-footer">
        <div class="footer-left">
        </div>
        <div class="footer-right">
          <button class="footer-btn btn-cancel" @click="closeDispatchModal">取消</button>
          <button class="footer-btn btn-generate">生成工单</button>
          <button class="footer-btn btn-dispatch" @click="confirmDispatchOrder">一键派单</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes overlay-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes modal-slide-up {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes realtime-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
.dispatch-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: overlay-fade-in 0.2s ease;
}
.dispatch-modal-content {
  background: linear-gradient(180deg, #0a1628 0%, #0d1f3c 100%);
  border: 1px solid rgba(0, 180, 255, 0.2);
  border-radius: 12px;
  width: 95%;
  max-width: 1200px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255,255,255,0.05);
  animation: modal-slide-up 0.3s ease;
  overflow: hidden;
  margin-top: -80px;
}
.dispatch-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px 28px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.dispatch-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #00d8ff;
}
.dispatch-header-left svg {
  color: #00b4ff;
  flex-shrink: 0;
}
.dispatch-header-left h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #e8f0fe;
  letter-spacing: 0.5px;
}
.dispatch-subtitle {
  margin: 2px 0 0;
  font-size: 12px;
  color: rgba(255,255,255,0.35);
  font-weight: 400;
}
.dispatch-modal-close {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: rgba(255,255,255,0.4);
  font-size: 24px;
  cursor: pointer;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  flex-shrink: 0;
}
.dispatch-modal-close:hover {
  background: rgba(255,255,255,0.08);
  color: #fff;
}
.dispatch-modal-body {
  padding: 16px 20px 20px;
  flex: 1;
  overflow-y: auto;
}
.dispatch-three-cols {
  display: grid;
  grid-template-columns: 1fr 1.3fr 1fr;
  gap: 14px;
}
.dispatch-col {
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px;
  overflow: hidden;
}
.col-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  color: rgba(255,255,255,0.6);
  border-bottom: 1px solid rgba(255,255,255,0.06);
  background: rgba(255,255,255,0.02);
}
.col-title svg {
  color: rgba(0, 180, 255, 0.7);
}
.col-more {
  margin-left: auto;
  font-size: 11px;
  font-weight: 500;
  color: rgba(0, 180, 255, 0.5);
  cursor: pointer;
  transition: all 0.2s;
}
.col-more:hover {
  color: rgba(0, 180, 255, 0.9);
}
.col-content {
  padding: 14px 16px;
}
.overview-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid rgba(255,255,255,0.04);
  font-size: 13px;
  line-height: 1.5;
}
.overview-row:last-of-type {
  border-bottom: none;
}
.overview-label {
  color: rgba(255,255,255,0.4);
  font-weight: 500;
  flex-shrink: 0;
  min-width: 70px;
}
.overview-value {
  color: rgba(255,255,255,0.85);
  font-weight: 500;
}
.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.status-alert {
  background: rgba(255, 70, 70, 0.15);
  color: #ff4646;
  border: 1px solid rgba(255, 70, 70, 0.2);
}
.level-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.level-high {
  background: rgba(255, 70, 70, 0.15);
  color: #ff4646;
  border: 1px solid rgba(255, 70, 70, 0.2);
}
.realtime-divider {
  height: 1px;
  background: rgba(255,255,255,0.06);
  margin: 12px 0 10px;
}
.realtime-title {
  font-size: 12px;
  font-weight: 600;
  color: rgba(0, 180, 255, 0.7);
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.realtime-title::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #00b4ff;
  animation: realtime-pulse 1.5s infinite;
}
.realtime-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.realtime-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.05);
  border-radius: 6px;
}
.realtime-label {
  font-size: 11px;
  color: rgba(255,255,255,0.35);
  font-weight: 500;
}
.realtime-value {
  font-size: 14px;
  font-weight: 700;
  color: rgba(255,255,255,0.85);
  font-family: 'Courier New', monospace;
}
.realtime-value.realtime-warn {
  color: #ff6b6b;
}
.diagnosis-summary {
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: 6px;
}
.diagnosis-label {
  color: rgba(255,255,255,0.4);
  font-weight: 500;
}
.diagnosis-value {
  color: #00b4ff;
  font-weight: 600;
}
.confidence-row {
  font-size: 13px;
  margin-bottom: 10px;
}
.confidence-label {
  color: rgba(255,255,255,0.4);
  font-weight: 500;
}
.confidence-value {
  color: #00b4ff;
  font-weight: 700;
  font-size: 15px;
}
.diagnosis-divider {
  height: 1px;
  background: rgba(255,255,255,0.06);
  margin: 12px 0;
}
.diagnosis-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.diagnosis-list li {
  position: relative;
  padding: 5px 0 5px 14px;
  font-size: 13px;
  color: rgba(255,255,255,0.7);
  line-height: 1.6;
}
.diagnosis-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(0, 180, 255, 0.6);
}
.suggestion-section {
  margin-top: 4px;
}
.suggestion-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: rgba(255,255,255,0.6);
  margin-bottom: 10px;
}
.suggestion-title svg {
  color: rgba(0, 180, 255, 0.7);
}
.suggestion-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.suggestion-list li {
  position: relative;
  padding: 5px 0 5px 14px;
  font-size: 13px;
  color: rgba(255,255,255,0.7);
  line-height: 1.6;
}
.suggestion-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(0, 180, 255, 0.6);
}
.dispatch-info-row {
  display: flex;
  align-items: center;
  padding: 6px 0;
  font-size: 13px;
  line-height: 1.5;
  border-bottom: 1px solid rgba(255,255,255,0.04);
}
.dispatch-info-row:last-of-type {
  border-bottom: none;
}
.dispatch-info-row .info-label {
  color: rgba(255,255,255,0.4);
  font-weight: 500;
  flex-shrink: 0;
  min-width: 72px;
}
.dispatch-info-row .info-value {
  color: rgba(255,255,255,0.85);
  font-weight: 500;
}
.urgency-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 3px;
  font-size: 12px;
  font-weight: 600;
}
.urgency-urgent {
  background: rgba(255, 70, 70, 0.15);
  color: #ff4646;
  border: 1px solid rgba(255, 70, 70, 0.2);
}
.match-value {
  color: #00b4ff;
  font-weight: 700;
}
.dispatch-divider {
  height: 1px;
  background: rgba(255,255,255,0.06);
  margin: 12px 0;
}
.link-section {
  margin-top: 4px;
}
.link-label {
  display: block;
  font-size: 12px;
  color: rgba(255,255,255,0.35);
  font-weight: 500;
  margin-bottom: 10px;
}
.link-buttons {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.link-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 8px;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 8px;
  color: rgba(255,255,255,0.6);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.link-btn:hover {
  background: rgba(0, 180, 255, 0.1);
  border-color: rgba(0, 180, 255, 0.3);
  color: #00b4ff;
}
.link-btn svg {
  flex-shrink: 0;
}
.dispatch-modal-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 28px;
  border-top: 1px solid rgba(255,255,255,0.06);
  background: rgba(0,0,0,0.15);
}
.footer-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.footer-right {
  display: flex;
  gap: 10px;
}
.footer-btn {
  padding: 8px 20px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid rgba(255,255,255,0.1);
  transition: all 0.15s;
}
.btn-cancel {
  background: transparent;
  color: white;
  border-color: rgba(255,255,255,0.1);
}
.btn-cancel:hover {
  background: rgba(255,255,255,0.06);
  color: rgba(255,255,255,0.8);
}
.btn-generate {
  background: rgba(0, 180, 255, 0.15);
  color: #00b4ff;
  border-color: rgba(0, 180, 255, 0.3);
}
.btn-generate:hover {
  background: rgba(0, 180, 255, 0.25);
}
.btn-dispatch {
  background: linear-gradient(135deg, #00b4ff, #0090e0);
  color: #fff;
  border: none;
  box-shadow: 0 2px 8px rgba(0, 180, 255, 0.3);
}
.btn-dispatch:hover {
  background: linear-gradient(135deg, #1ac4ff, #00a0f0);
  box-shadow: 0 4px 12px rgba(0, 180, 255, 0.4);
}
</style>
