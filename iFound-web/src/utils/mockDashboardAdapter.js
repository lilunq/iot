/**
 * @file mockDashboardAdapter.js
 * @description 无后端环境下的演示数据适配器（供 /dashboard1 页面使用）。
 *              通过替换共享 axios 实例的 defaults.adapter，把 Dashboard 及其
 *              全部子组件（HealthModal / AgentCollabModal / TaskDetailModal 等）
 *              的请求拦截到本地假数据，无需启动任何后端服务。
 */

import {
  iotRequest, aiRequest, posRequest, keliuRequest, ragflowRequest, emsRequest
} from '@/utils/request'

// ========================= 工具函数 =========================

const pad = (n) => String(n).padStart(2, '0')

/** 距当前 minusMinutes 分钟前的时间，格式 yyyyMMddHHmmss */
function ts(minusMinutes = 0) {
  const d = new Date(Date.now() - minusMinutes * 60000)
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
}

/** 距当前 minusMinutes 分钟前的时间，格式 HH:mm */
function hm(minusMinutes = 0) {
  const d = new Date(Date.now() - minusMinutes * 60000)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 今天日期 yyyy-MM-dd */
function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function nowDateTime() {
  const d = new Date()
  return `${todayStr()} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function tryParseJSON(text) {
  if (!text || typeof text !== 'string') return null
  try { return JSON.parse(text) } catch { return null }
}

/**
 * 从请求里取参数：兼容 GET params 与 POST body，兼容 snake_case / camelCase。
 */
function pick(config, ...aliases) {
  const params = config.params || {}
  let body = config.data
  if (typeof body === 'string') body = tryParseJSON(body)
  if (body instanceof FormData) body = null
  for (const key of aliases) {
    if (params[key] !== undefined && params[key] !== '') return params[key]
    if (body && body[key] !== undefined && body[key] !== '') return body[key]
  }
  return undefined
}

function num(v, fallback = 0) {
  const n = Number(v)
  return Number.isFinite(n) ? n : fallback
}

/** 分页切片 */
function pageSlice(list, page, limit) {
  const p = Math.max(1, num(page, 1))
  const l = Math.max(1, num(limit, 10))
  return list.slice((p - 1) * l, p * l)
}

// ========================= Agent 协作任务（来源：dispatch_task 表两条真实记录） =========================

const AI_SUGGESTION_FOR_SENSOR = `设备[到达层10/0.4kV变电所传感器1]故障，派单维修。
—— AI处置建议 ——
根据提供的参考资料，**无法直接找到**针对“层10/0.4kV变电所传感器1”这一特定设备的具体故障处理方案，因为资料中未包含该特定位置传感器的详细故障代码或专属维护记录。

但是，根据资料2和资料4，变电所通常涉及**环境传感器**（监测温度、湿度等）或**气体传感器**（监测氧气、有害气体等）。您可以参考以下通用排查步骤进行处理：

### 1. 确认传感器类型及对应排查流程
*   **如果是环境传感器**（如温湿度、PM2.5）：
    *   **对比验证**：使用手持式温湿度计或气体检测仪在同一位置对比测量，确认是传感器数据异常还是环境确实异常。
    *   **检查供电**：确认供电是否正常（通常为 DC24V 或电池），电压是否在允许范围。
    *   **检查通信**：确认与 RTU/网关的通信线连接牢固，RS485 接线（A/B）是否正确。
    *   **检查安装位置**：确认传感器未被遮挡，且远离热源/冷源/门窗等干扰位置。
    *   **重新上电**：断开电源 30 秒后重新上电，观察数据是否恢复。
    *   **清洁探头**：PM2.5 探头用软毛刷清洁，温湿度探头用干燥软布擦拭。
    *   **校准/更换**：如支持现场校准，进行零点和量程校准；若以上步骤无效，需更换传感器。

*   **如果是气体传感器**（如氧气、CO、有害气体）：
    *   **安全确认**：前往现场前确认通风情况，必要时佩戴便携式气体检测仪。
    *   **对比验证**：使用便携式气体检测仪对比测量。
    *   **检查供电与通信**：确认电源指示灯亮，RS485 接线牢固。
    *   **检查探头状态**：查看探头是否有物理损坏、积尘或水渍。
    *   **重新上电**：断电 30 秒后重启。
    *   **校准/更换**：使用标准气体进行校准（需专业人员）；注意电化学传感器寿命通常为 1-2 年，到期需更换探头或整机。

### 2. 常见故障现象参考
*   **数据恒定不变/为0/空值**：可能原因为传感器死机、通信中断、传感器掉线或数据点未绑定（严重程度：中-高）。
*   **数据频繁跳变/异常**：可能原因为接线松动、电磁干扰、传感器漂移或校准失效（严重程度：中-低）。
*   **频繁误报**：可能原因为传感器漂移或环境干扰（严重程度：中）。

建议优先检查**供电、通信连接**及**传感器状态**，若问题依旧，请联系专业人员更换传感器备件。`

const TASK_SNAPSHOT_SENSOR = JSON.stringify({
  id: '1281902547317035008',
  location: null,
  org_id: '3',
  subarea_cid: '0.001.001.001-0.002',
  system_type: '0.005',
  device_id: '1226586217840119808',
  device_name: '到达层10/0.4kV变电所传感器1',
  event_level: 3,
  event_state: 1,
  event_info: 'LK246-遥信 TX23_变电所传感器_二合一传感器故障 故障(当前值=1)',
  happen_time: '20260908090303',
  receive_time: '20260908090303',
  process_state: 1,
  event_type: '0-002.004',
  data_type: 2,
  data_key: '2892',
  confirm_state: null,
  warning_level: null
})

const TASK_DECISION_DATA_AC = JSON.stringify([
  {
    deviceId: 119,
    deviceName: '候车层5A/6A岛式空调',
    externalId: '1258388439938633728',
    dataKey: '1258385511379111936',
    value: 26.5
  }
])

/** 内存中的协作任务表（可被派发/取消操作修改，模拟后端状态流转） */
const dispatchTaskStore = [
  {
    id: '2093608939938694552',
    subarea_cid: '0.001.001.001-0',
    creator_agent_id: 'commander',
    creator_agent_name: '系统Agent',
    parent_id: null,
    create_time: '2026-09-08 09:03:11',
    complete_time: '2026-09-08 09:03:13',
    business_domain: '空调巡检',
    thinking_overview: '温度决策因素：湿度高/室外潮湿、低客流',
    level: '低',
    problem_desc: '空调巡检完成，结果如下：湿度高/室外潮湿、低客流',
    data_snapshot: null,
    problem_summary: null,
    dispatched: 1,
    decision_content: '温度设定至26.5°C，涉及设备：候车层5A/6A岛式空调',
    decision_data: TASK_DECISION_DATA_AC,
    expected_effect: '制冷温度设定上升1.0°C（25.5→26.5），区域温度升高',
    target_agent_id: 'energy-agent',
    adopt_status: 1,
    user_feedback: null,
    remark: null,
    task_type: 'collaboration',
    target_agent_name: '能源管理Agent',
    adopt_time: '2026-09-08 09:03:13',
    dispatched_time: '2026-09-08 09:03:13',
    dispatched_user: 'admin',
    adopt_user: 'admin',
    result_execute_status: '1',
    result_execute_user: '能源管理Agent',
    result_data: '',
    result_desc: '候车层5A/6A岛式空调控制已下发'
  },
  {
    id: '2093608939938694553',
    subarea_cid: '0.001.001.001-0.002',
    creator_agent_id: 'commander',
    creator_agent_name: '系统Agent',
    parent_id: null,
    create_time: '2026-09-08 09:03:44',
    complete_time: null,
    business_domain: '维修工单',
    thinking_overview: '设备故障需立即派单维修',
    level: '高',
    problem_desc: 'LK246-遥信 TX23_变电所传感器_二合一传感器故障 故障(当前值=1)。LK246-遥信 TX23_变电所传感器_二合一传感器故障 正常(当前值=0) 2026-09-08 09:03:52。LK246-遥信 TX23_变电所传感器_二合一传感器故障 故障(当前值=1) 2026-09-08 09:10:22。LK246-遥信 TX23_变电所传感器_二合一传感器故障 正常(当前值=0) 2026-09-08 09:10:29',
    data_snapshot: TASK_SNAPSHOT_SENSOR,
    problem_summary: '到达层10/0.4kV变电所传感器1故障，需人工介入维修。',
    dispatched: 0,
    decision_content: AI_SUGGESTION_FOR_SENSOR,
    decision_data: null,
    expected_effect: '修复到达层10/0.4kV变电所传感器1故障，恢复设备功能',
    target_agent_id: '1226586217840119808',
    adopt_status: 0,
    user_feedback: null,
    remark: null,
    task_type: 'collaboration',
    target_agent_name: '设备运维Agent',
    adopt_time: null,
    dispatched_time: null,
    dispatched_user: null,
    adopt_user: null,
    result_execute_status: '0',
    result_execute_user: null,
    result_data: null,
    result_desc: null
  }
]

// 后端返回给前端的是 camelCase 结构
function toTaskDTO(t) {
  return {
    id: t.id,
    subareaCid: t.subarea_cid,
    creatorAgentId: t.creator_agent_id,
    creatorAgentName: t.creator_agent_name,
    parentId: t.parent_id,
    createTime: t.create_time,
    completeTime: t.complete_time,
    businessDomain: t.business_domain,
    thinkingOverview: t.thinking_overview,
    level: t.level,
    problemDesc: t.problem_desc,
    dataSnapshot: t.data_snapshot,
    problemSummary: t.problem_summary,
    dispatched: t.dispatched,
    decisionContent: t.decision_content,
    decisionData: t.decision_data,
    expectedEffect: t.expected_effect,
    targetAgentId: t.target_agent_id,
    adoptStatus: t.adopt_status == null ? null : String(t.adopt_status),
    userFeedback: t.user_feedback,
    remark: t.remark,
    taskType: t.task_type,
    targetAgentName: t.target_agent_name,
    adoptTime: t.adopt_time,
    dispatchedTime: t.dispatched_time,
    dispatchedUser: t.dispatched_user,
    adoptUser: t.adopt_user,
    resultExecuteStatus: t.result_execute_status,
    resultExecuteUser: t.result_execute_user,
    resultData: t.result_data,
    resultDesc: t.result_desc
  }
}

let taskAutoId = 2093608939938694554

// ========================= 设备事件 / 设备状态 =========================

const DEVICE_SENSOR = {
  id: '1226586217840119808',
  device_name: '到达层10/0.4kV变电所传感器1',
  device_type: '0.005.002 二合一传感器',
  device_id: '1226586217840119808',
  org_id: '3',
  system_type: '0.005',
  subarea_cid: '0.001.001.001-0.002',
  location: '到达层10',
  fault: 1,
  alarm: 0,
  event_info: 'LK246-遥信 TX23_变电所传感器_二合一传感器故障 故障(当前值=1)'
}

const DEVICE_AC = {
  id: '1258388439938633728',
  device_name: '候车层5A/6A岛式空调',
  device_type: '0.003.001 岛式空调',
  device_id: '1258388439938633728',
  org_id: '3',
  system_type: '0.003',
  subarea_cid: '0.001.001.001-0',
  location: '候车层5A/6A',
  fault: 0,
  alarm: 1,
  event_info: '候车层5A/6A岛式空调 回风温度越上限(当前值=27.8°C)'
}

const alarmDevicePool = [DEVICE_AC]

const faultDevicePool = [DEVICE_SENSOR]

function baseEvent(overrides) {
  return {
    id: '1281902547317035' + String(Math.floor(Math.random() * 900) + 100),
    org_id: '3',
    subarea_cid: '0.001.001.001-0',
    system_type: '0.003',
    device_id: '1258388439938633728',
    device_name: '候车层5A/6A岛式空调',
    event_level: 2,
    event_state: 1,
    location: '候车层5A/6A',
    event_info: '候车层5A/6A岛式空调 回风温度越上限',
    happen_time: ts(30),
    receive_time: ts(30),
    process_state: 0,
    event_type: '0-002.004',
    data_type: 2,
    data_key: '1258',
    confirm_state: null,
    warning_level: null,
    ...overrides
  }
}

/** 事件池：level 3 故障 / 2 告警 / 1 通知 / 0 遥控 */
const eventPools = {
  3: [
    baseEvent({
      id: '1281902547317035008',
      subarea_cid: '0.001.001.001-0.002',
      system_type: '0.005',
      device_id: '1226586217840119808',
      device_name: '到达层10/0.4kV变电所传感器1',
      location: '到达层10',
      event_level: 3,
      event_info: 'LK246-遥信 TX23_变电所传感器_二合一传感器故障 故障(当前值=1)',
      happen_time: ts(4),
      receive_time: ts(4),
      data_type: 2,
      data_key: '2892',
      event_type: '0-002.004'
    }),
    baseEvent({
      subarea_cid: '0.001.001.001-0.001',
      device_id: '1226586214233018368',
      device_name: '出发层多联机内机2',
      location: '出发层候车区',
      event_level: 3,
      event_info: '出发层多联机内机2 通讯中断 故障(当前值=1)',
      happen_time: ts(52),
      receive_time: ts(52),
      data_type: 2,
      data_key: '1310'
    }),
    baseEvent({
      subarea_cid: '0.001.001.001-0.003',
      device_id: '1226586217840119900',
      device_name: '地下层排风机P3',
      location: '地下层',
      event_level: 3,
      event_info: '地下层排风机P3 过载跳闸 故障(当前值=1)',
      happen_time: ts(126),
      receive_time: ts(126),
      data_type: 2,
      data_key: '1402'
    })
  ],
  2: [
    baseEvent({
      device_id: '1258388439938633728',
      device_name: '候车层5A/6A岛式空调',
      location: '候车层5A/6A',
      event_info: '候车层5A/6A岛式空调 回风温度越上限(当前值=27.8°C)',
      happen_time: ts(8),
      receive_time: ts(8)
    }),
    baseEvent({
      subarea_cid: '0.001.001.001-0.001',
      device_id: '1231342073462919168',
      device_name: 'TX43_6号岛式空调环境传感器',
      location: '出发层6A',
      event_info: 'TX43_6号岛式空调环境传感器 PM2.5浓度越限(当前值=98μg/m³)',
      happen_time: ts(21),
      receive_time: ts(21)
    }),
    baseEvent({
      subarea_cid: '0.001.001.001-0.002',
      device_id: '1226586217840120001',
      device_name: '到达层配电间温湿度传感器',
      location: '到达层配电间',
      event_info: '到达层配电间温湿度传感器 湿度越上限(当前值=82%RH)',
      happen_time: ts(35),
      receive_time: ts(35)
    }),
    baseEvent({
      subarea_cid: '0.001.001.001-0',
      device_id: '1258388439938633730',
      device_name: '候车层7A岛式空调',
      location: '候车层7A',
      event_info: '候车层7A岛式空调 出风温度偏差告警',
      happen_time: ts(48),
      receive_time: ts(48)
    }),
    baseEvent({
      subarea_cid: '0.001.001.001-0.001',
      device_id: '1226586217840120010',
      device_name: '出发层10kV开关柜',
      location: '出发层配电室',
      system_type: '0.005',
      event_info: '出发层10kV开关柜 A相电压越上限(当前值=10.8kV)',
      happen_time: ts(66),
      receive_time: ts(66)
    }),
    baseEvent({
      subarea_cid: '0.001.001.001-0.003',
      device_id: '1226586217840120021',
      device_name: '地下层水浸传感器',
      location: '地下层',
      event_info: '地下层水浸传感器 检测到漏水风险',
      happen_time: ts(88),
      receive_time: ts(88)
    }),
    baseEvent({
      subarea_cid: '0.001.001.001-0',
      device_id: '1258388439938633741',
      device_name: '候车层3A扶梯',
      location: '候车层3A',
      event_info: '候车层3A扶梯 运行电流偏高',
      happen_time: ts(102),
      receive_time: ts(102)
    })
  ],
  1: [
    baseEvent({
      event_level: 1,
      device_name: '候车层2A门禁',
      device_id: '1226586217840120100',
      location: '候车层2A',
      event_info: '候车层2A门禁 远程开启成功',
      happen_time: ts(15),
      receive_time: ts(15)
    }),
    baseEvent({
      event_level: 1,
      subarea_cid: '0.001.001.001-0.001',
      device_name: '出发层照明回路L3',
      device_id: '1226586217840120101',
      location: '出发层候车区',
      event_info: '出发层照明回路L3 定时开启',
      happen_time: ts(26),
      receive_time: ts(26)
    }),
    baseEvent({
      event_level: 1,
      subarea_cid: '0.001.001.001-0.002',
      device_name: '到达层广播分区B2',
      device_id: '1226586217840120102',
      location: '到达层出站口',
      event_info: '到达层广播分区B2 开始播放到站信息',
      happen_time: ts(40),
      receive_time: ts(40)
    }),
    baseEvent({
      event_level: 1,
      subarea_cid: '0.001.001.001-0',
      device_name: '候车层安检仪A3',
      device_id: '1226586217840120103',
      location: '候车层安检区',
      event_info: '候车层安检仪A3 班次交接完成',
      happen_time: ts(75),
      receive_time: ts(75)
    }),
    baseEvent({
      event_level: 1,
      subarea_cid: '0.001.001.001-0.003',
      device_name: '地下层新风机X1',
      device_id: '1226586217840120104',
      location: '地下层',
      event_info: '地下层新风机X1 过滤网更换提醒',
      happen_time: ts(140),
      receive_time: ts(140)
    })
  ],
  0: [
    baseEvent({
      event_level: 0,
      device_name: '候车层1A照明回路',
      device_id: '1226586217840120200',
      location: '候车层1A',
      event_info: '候车层1A照明回路 遥控合闸成功',
      happen_time: ts(12),
      receive_time: ts(12)
    }),
    baseEvent({
      event_level: 0,
      device_id: '1258388439938633728',
      device_name: '候车层5A/6A岛式空调',
      location: '候车层5A/6A',
      event_info: '候车层5A/6A岛式空调 遥控开机成功',
      happen_time: ts(18),
      receive_time: ts(18)
    }),
    baseEvent({
      event_level: 0,
      subarea_cid: '0.001.001.001-0.001',
      device_name: '出发层排风机P1',
      device_id: '1226586217840120202',
      location: '出发层设备间',
      event_info: '出发层排风机P1 遥控启动成功',
      happen_time: ts(29),
      receive_time: ts(29)
    }),
    baseEvent({
      event_level: 0,
      subarea_cid: '0.001.001.001-0.002',
      device_name: '到达层扶梯E2',
      device_id: '1226586217840120203',
      location: '到达层到达口',
      event_info: '到达层扶梯E2 遥控切换运行方向',
      happen_time: ts(45),
      receive_time: ts(45)
    }),
    baseEvent({
      event_level: 0,
      subarea_cid: '0.001.001.001-0',
      device_name: '候车层电动排烟窗W1',
      device_id: '1226586217840120204',
      location: '候车层顶部',
      event_info: '候车层电动排烟窗W1 遥控关闭成功',
      happen_time: ts(60),
      receive_time: ts(60)
    }),
    baseEvent({
      event_level: 0,
      subarea_cid: '0.001.001.001-0.003',
      device_name: '地下层风机P3',
      device_id: '1226586217840120205',
      location: '地下层',
      event_info: '地下层风机P3 遥控停止成功',
      happen_time: ts(110),
      receive_time: ts(110)
    })
  ]
}

// ========================= 分区态势（候车层 / 出发层 / 到达层 / 出发层） =========================

const SUBAREA_ZONES = [
  {
    subarea_name: '候车层',
    subarea_cid: '0.001.001.001-0',
    subarea_coord: '120,80,0',
    subarea_alter: '1',
    deviceStateCount: { total: 42, fault_total: 0, alarm_total: 1 },
    environment: { totalDevices: 8, abnormalDevices: 1 },
    keliu: { value: '0.42', status: '0' },
    energy: { value: '3265', status: '0' }
  },
  {
    subarea_name: '出发层',
    subarea_cid: '0.001.001.001-0.001',
    subarea_coord: '430,230,0',
    subarea_alter: '0',
    deviceStateCount: { total: 38, fault_total: 0, alarm_total: 0 },
    environment: { totalDevices: 6, abnormalDevices: 0 },
    keliu: { value: '0.28', status: '0' },
    energy: { value: '2980', status: '0' }
  },
  {
    subarea_name: '到达层',
    subarea_cid: '0.001.001.001-0.002',
    subarea_coord: '780,100,0',
    subarea_alter: '1',
    deviceStateCount: { total: 21, fault_total: 1, alarm_total: 0 },
    environment: { totalDevices: 5, abnormalDevices: 0 },
    keliu: { value: '0.19', status: '0' },
    energy: { value: '1856', status: '0' }
  },
  {
    subarea_name: '地下层',
    subarea_cid: '0.001.001.001-0.003',
    subarea_coord: '1080,270,0',
    subarea_alter: '0',
    deviceStateCount: { total: 12, fault_total: 0, alarm_total: 0 },
    environment: { totalDevices: 4, abnormalDevices: 0 },
    keliu: { value: '0.22', status: '0' },
    energy: { value: '1420', status: '0' }
  }
]

const SUBAREA_LIST = SUBAREA_ZONES.map((z, i) => ({
  id: String(i + 1),
  name: z.subarea_name,
  cascade_id: z.subarea_cid
}))

const ALL_ZONE_ANALYSIS_TEXT = `【西安东站全分区态势综合研判】
候车层：当前在管设备42台，1台空调回风温度越上限，密度0.42人/㎡，能耗3265kWh。结合今日湿度偏高、室外潮湿且客流处于低位的情况，AI已将5A/6A岛式空调制冷温度由25.5°C上调至26.5°C，预计区域温度平稳上升，兼顾舒适与节能。
出发层：设备38台全部正常，环境指标均在允许范围内，客流密度0.28人/㎡，运行平稳。夹层排风机P3曾出现过载跳闸，已复位并纳入观察清单。
到达层：10/0.4kV变电所传感器1出现二合一传感器故障告警，系统已自动生成维修工单并派发至设备运维Agent，建议按AI处置建议优先检查供电与RS485通信回路，必要时更换传感器备件。
地下层：设备12台全正常，能耗1420kWh，注意新风机X1过滤网更换提醒。
总体评估：站房整体健康度92分，无高风险运行状态，重点跟踪到达层传感器维修进度与候车层空调温度调节效果。`

const ZONE_ANALYSIS_TEXT = `该分区当前运行整体平稳。设备层面，故障与告警设备已按优先级排序并联动工单系统；环境层面，温湿度、PM2.5等指标均在允许范围内波动。建议持续关注异常设备的处置进度，并结合客流与能耗趋势在低谷时段安排设备巡检，实现舒适度与能耗的平衡。`

// ========================= 健康 / 能耗 / KPI 等指标 =========================

function historyScoreList() {
  const base = [88, 89, 91, 90, 92, 92.6]
  return base.map((score, i) => ({
    totalScore: score,
    totalLevel: '良好',
    levelDesc: '各系统运行正常，设备健康度良好',
    createTime: `2026-09-08T${pad(9 + i - (base.length - 1))}:${pad(10 + i * 7)}:00`
  }))
}

function lastNDates(n) {
  const list = []
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date(Date.now() - i * 86400000)
    list.push(`${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`)
  }
  return list
}

function agentCollabStatsData() {
  const agentNames = ['系统Agent', '能源管理Agent', '设备运维Agent']
  const gen = [
    { 系统Agent: 1, 能源管理Agent: 1, 设备运维Agent: 0 },
    { 系统Agent: 2, 能源管理Agent: 1, 设备运维Agent: 1 },
    { 系统Agent: 1, 能源管理Agent: 2, 设备运维Agent: 0 },
    { 系统Agent: 2, 能源管理Agent: 1, 设备运维Agent: 1 },
    { 系统Agent: 1, 能源管理Agent: 0, 设备运维Agent: 1 },
    { 系统Agent: 2, 能源管理Agent: 1, 设备运维Agent: 0 },
    { 系统Agent: 2, 能源管理Agent: 1, 设备运维Agent: 1 }
  ]
  const dates = lastNDates(7)
  const dailyStats = dates.map((date, i) => ({
    date,
    generatedCount: gen[i].系统Agent + gen[i].能源管理Agent + gen[i].设备运维Agent,
    targetCount: gen[i].系统Agent + gen[i].能源管理Agent + gen[i].设备运维Agent,
    agentGenerated: { ...gen[i] },
    agentTarget: { 系统Agent: gen[i].系统Agent, 能源管理Agent: gen[i].能源管理Agent, 设备运维Agent: gen[i].设备运维Agent }
  }))
  return {
    totalTasks: 2,
    dispatchedTasks: 1,
    dispatchRate: 50,
    agentNames,
    dailyStats
  }
}

function monthEnergyArray() {
  // 每日能耗（kWh），今日 2920，昨日 3015
  const days = new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0).getDate()
  return Array.from({ length: days }, (_, i) => {
    if (i === days - 1) return 2920
    if (i === days - 2) return 3015
    return 2650 + Math.round(Math.sin(i * 1.7) * 260) + (i % 5) * 22
  })
}

function yearEnergyArray() {
  // 每月能耗（kWh），本月按已过天数折算
  const m = new Date().getMonth()
  return Array.from({ length: 12 }, (_, i) =>
    i < m ? 86500 + Math.round(Math.sin(i * 2.3) * 6800) : (i === m ? 84600 : 0)
  )
}

function envAnalyzeResult() {
  const results = {}
  const defs = [
    ['0.001.001.001-0', '候车层', 8, 1, ['5A/6A岛式空调回风温度偏高，建议结合低客流上调制冷设定温度', '安检区上午时段PM2.5略有抬升，建议加大新风量']],
    ['0.001.001.001-0.001', '出发层', 6, 0, ['各环境指标正常，建议维持当前运行策略']],
    ['0.001.001.001-0.002', '到达层', 5, 0, ['各环境指标正常，建议维持当前运行策略']],
    ['0.001.001.001-0.003', '地下层', 4, 0, ['各环境指标正常，注意地下层湿度变化趋势']]
  ]
  defs.forEach(([cid, name, total, abnormal, suggestions]) => {
    results[cid] = {
      subareaName: name,
      result: JSON.stringify({
        totalDevices: total,
        abnormalDevices: abnormal,
        score: abnormal > 0 ? 88 : 96,
        suggestions,
        deviceResults: [
          {
            name: abnormal > 0 ? '候车层5A/6A岛式空调环境传感器' : `${name}综合环境传感器`,
            deviceName: abnormal > 0 ? '候车层5A/6A岛式空调环境传感器' : `${name}综合环境传感器`,
            type: '温湿度/PM2.5',
            value: abnormal > 0 ? '27.8°C' : '24.6°C',
            status: abnormal > 0 ? '异常' : '正常',
            violations: abnormal > 0
              ? [{ item: '回风温度', value: '27.8°C', suggestion: '上调制冷温度设定至26.5°C，低客流时段适当轮停' }]
              : []
          }
        ]
      })
    }
  })
  return results
}

function healthEvolutionData() {
  const timeline = []
  const agents = [
    { name: '系统Agent' },
    { name: '能源管理Agent' },
    { name: '设备运维Agent' }
  ]
  for (let i = 5; i >= 0; i--) {
    const d = new Date(Date.now() - i * 3600000)
    timeline.push({
      time: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:00`,
      averageScore: 88.5 + (5 - i) * 0.8,
      agents: agents.map((a, idx) => ({
        ...a,
        dispatchedCount: i === 0 ? (idx === 1 || idx === 2 ? 1 : 0) : (i % 2 === idx % 2 ? 1 : 0)
      }))
    })
  }
  return {
    summary: { totalTasks: 2, dispatchedTasks: 1, averageScore: 92.6 },
    startTime: timeline[0].time,
    endTime: timeline[timeline.length - 1].time,
    timeline
  }
}

const LATEST_REPORT_HTML = `<h3>西安东站运行报告（${todayStr()}）</h3>
<p><b>一、总体情况</b><br/>站房整体健康度评分92.6分，评级"良好"。全天未发生高风险事件，设备在线率98.2%。</p>
<p><b>二、Agent协作</b><br/>系统Agent完成空调巡检1项，生成协作任务2项：能源管理Agent已采纳空调温度调节决策并下发控制；设备运维Agent待处置到达层变电所传感器维修工单。</p>
<p><b>三、能耗</b><br/>今日能耗2920kWh，较昨日3015kWh下降3.1%；本月累计能耗84600kWh，同比持平。</p>
<p><b>四、建议</b><br/>1. 跟踪到达层传感器维修进度；2. 低客流时段继续执行空调温度动态调节策略。</p>`

// ========================= mock 数据路由 =========================

const MOCK_ROUTES = [
  // ---------- xian（AI 服务） ----------
  {
    test: (url) => url.includes('/xian/subareaState'),
    handle: () => ({ code: '200', Status: 'Ok', data: SUBAREA_ZONES })
  },
  {
    test: (url) => url.includes('/xian/subarea/list'),
    handle: () => ({ Status: 'Ok', data: SUBAREA_LIST })
  },
  {
    test: (url) => url.includes('/xian/dispatch/pending-insights'),
    handle: () => ({
      code: '200',
      data: {
        total: 2,
        groups: [
          {
            subareaCid: '0.001.001.001-0',
            count: 1,
            tasks: [{ id: '2093608939938694552', problemDesc: '空调巡检完成，结果如下：湿度高/室外潮湿、低客流', targetAgentId: 'energy-agent' }]
          },
          {
            subareaCid: '0.001.001.001-0.002',
            count: 1,
            tasks: [{ id: '2093608939938694553', problemDesc: '到达层10/0.4kV变电所传感器1故障，需人工介入维修。', targetAgentId: 'ops-agent' }]
          }
        ]
      }
    })
  },
  {
    test: (url) => url.includes('/xian/dispatch/list'),
    handle: (config) => {
      let list = dispatchTaskStore.slice()
      const adoptStatus = pick(config, 'adoptStatus')
      const dispatched = pick(config, 'dispatched')
      const resultExecuteStatus = pick(config, 'resultExecuteStatus')
      const targetAgentName = pick(config, 'targetAgentName')
      const targetAgentId = pick(config, 'targetAgentId')
      const decisionContent = pick(config, 'decisionContent')
      const subareaCid = pick(config, 'subareaCid')
      if (adoptStatus !== undefined) list = list.filter(t => String(t.adopt_status) === String(adoptStatus))
      if (dispatched !== undefined && dispatched !== '') list = list.filter(t => String(t.dispatched) === String(dispatched))
      if (resultExecuteStatus !== undefined && resultExecuteStatus !== '') list = list.filter(t => String(t.result_execute_status) === String(resultExecuteStatus))
      if (targetAgentName) list = list.filter(t => t.target_agent_name.includes(targetAgentName))
      // 兼容 dispatch_task 中 target_agent_id 存的是设备ID的情况：按目标Agent名称反查
      const agentIdByName = { '能源管理Agent': 'energy-agent', '设备运维Agent': 'ops-agent', '车站运营Agent': 'foot-agent', '系统Agent': 'commander' }
      if (targetAgentId) list = list.filter(t => t.target_agent_id === targetAgentId || agentIdByName[t.target_agent_name] === targetAgentId)
      if (decisionContent) list = list.filter(t => (t.decision_content || '').includes(decisionContent))
      if (subareaCid) list = list.filter(t => t.subarea_cid === subareaCid)
      const pageNum = num(pick(config, 'pageNum', 'page'), 1)
      const pageSize = num(pick(config, 'pageSize', 'limit'), 20)
      return {
        code: '200',
        Status: 'Ok',
        data: {
          records: pageSlice(list, pageNum, pageSize).map(toTaskDTO),
          total: list.length,
          pageNum,
          pageSize
        }
      }
    }
  },
  {
    test: (url) => url.includes('/xian/dispatch/agent-collab-stats'),
    handle: () => ({ code: '200', data: agentCollabStatsData() })
  },
  {
    test: (url) => url.includes('/xian/dispatch/health-collab-evolution'),
    handle: () => ({ code: '200', data: healthEvolutionData() })
  },
  {
    test: (url) => /\/xian\/dispatch\/adopt\//.test(url),
    handle: () => {
      // 演示模式：仅返回成功，不改变任务状态。
      // 运维页「上报+AI诊断」成功后会自动调用 adopt；若在此改写任务状态，
      // 第二条任务会变成「详情已采纳但卡片待决策」的不一致表现。
      // 采纳状态的流转只由 Dashboard 的「派发」操作（/dispatch/dispatch）驱动。
      return { code: '200', Status: 'Ok', message: '采纳成功' }
    }
  },
  {
    test: (url) => /\/xian\/dispatch\/cancel\//.test(url),
    handle: (config) => {
      const id = config.url.split('/').pop()
      const task = dispatchTaskStore.find(t => String(t.id) === String(id))
      if (task) {
        task.dispatched = 2
        task.result_execute_status = '0'
      }
      return { code: '200', Status: 'Ok', message: '取消成功' }
    }
  },
  {
    test: (url) => url.includes('/xian/dispatch/dispatch'),
    handle: (config) => {
      const body = tryParseJSON(config.data) || {}
      const task = dispatchTaskStore.find(t => String(t.id) === String(body.id))
      if (task) {
        task.dispatched = 1
        task.adopt_status = 1
        task.adopt_time = nowDateTime()
        task.dispatched_time = nowDateTime()
        task.dispatched_user = body.dispatchedUser || 'admin'
        task.adopt_user = body.dispatchedUser || 'admin'
        task.result_execute_status = '1'
        task.result_execute_user = task.target_agent_name
        task.result_desc = `${task.target_agent_name}已接收并执行（模拟环境）`
      }
      return { code: '200', Status: 'Ok', message: '派发成功', data: true }
    }
  },
  {
    test: (url) => url.includes('/xian/dispatch/user-feedback'),
    handle: (config) => {
      const body = tryParseJSON(config.data) || {}
      const task = dispatchTaskStore.find(t => String(t.id) === String(body.id))
      if (task) {
        task.user_feedback = body.userFeedback || ''
        task.result_execute_status = body.resultExecuteStatus || task.result_execute_status
      }
      return { code: '200', Status: 'Ok', message: '反馈成功' }
    }
  },
  {
    test: (url) => /\/xian\/ai-diagnosis\/by-source\//.test(url),
    handle: () => ({ code: '200', Status: 'Ok', data: null })
  },
  {
    test: (url) => url.includes('/xian/ai-diagnosis'),
    handle: () => ({ code: '200', Status: 'Ok', data: 'mock-record-001' })
  },
  {
    test: (url) => /\/xian\/dispatch\/\d+$/.test(url),
    handle: (config) => {
      const id = config.url.split('/').pop()
      const task = dispatchTaskStore.find(t => String(t.id) === String(id))
      return { code: '200', Status: 'Ok', data: task ? toTaskDTO(task) : null }
    }
  },
  {
    test: (url) => url.includes('/xian/dispatch'),
    handle: (config) => {
      // 新建协作任务
      const body = tryParseJSON(config.data) || {}
      const id = String(taskAutoId++)
      dispatchTaskStore.unshift({
        id,
        subarea_cid: body.subareaCid || '',
        creator_agent_id: body.creatorAgentId || 'commander',
        creator_agent_name: body.creatorAgentName || '系统Agent',
        parent_id: null,
        create_time: nowDateTime(),
        complete_time: null,
        business_domain: body.businessDomain || '',
        thinking_overview: body.thinkingOverview || '',
        level: body.level || '中',
        problem_desc: body.problemDesc || '',
        data_snapshot: null,
        problem_summary: body.problemSummary || null,
        dispatched: num(body.dispatched, 0),
        decision_content: body.decisionContent || '',
        decision_data: null,
        expected_effect: body.expectedEffect || '',
        target_agent_id: body.targetAgentId || '',
        adopt_status: 0,
        user_feedback: null,
        remark: null,
        task_type: 'collaboration',
        target_agent_name: body.targetAgentId === 'energy-agent' ? '能源管理Agent'
          : body.targetAgentId === 'ops-agent' ? '设备运维Agent'
          : body.targetAgentId === 'foot-agent' ? '车站运营Agent' : '系統Agent',
        adopt_time: null,
        dispatched_time: null,
        dispatched_user: null,
        adopt_user: null,
        result_execute_status: '0',
        result_execute_user: null,
        result_data: null,
        result_desc: null
      })
      return { code: '200', Status: 'Ok', data: { id } }
    }
  },
  {
    test: (url) => url.includes('/xian/getHistoryScore'),
    handle: () => ({ code: '200', data: historyScoreList() })
  },
  {
    test: (url) => url.includes('/xian/getTunnelRunScore'),
    handle: () => ({
      code: '200',
      data: { totalScore: 92.6, scoreLevel: '良好', levelDesc: '各系统运行正常，设备健康度良好' }
    })
  },
  {
    test: (url) => url.includes('/xian/report/latest'),
    handle: () => ({ code: '200', data: LATEST_REPORT_HTML })
  },
  {
    test: (url) => url.includes('/xian/computing-power'),
    handle: () => ({
      code: '200',
      Status: 'Ok',
      data: {
        gpuUtilizationRate: 63.5,
        processingRequest: 12,
        queueRequest: 3,
        throughput: 1846,
        reasoningTime: 320,
        successRequest: 15283
      }
    })
  },
  {
    test: (url) => url.includes('/xian/env/analyze/result'),
    handle: () => ({
      code: '200',
      data: { status: 'ready', analyzeTime: nowDateTime(), results: envAnalyzeResult() }
    })
  },
  {
    test: (url) => url.includes('/xian/analysis/allZoneAnalysis'),
    handle: () => ({ code: '200', Status: 'Ok', data: ALL_ZONE_ANALYSIS_TEXT })
  },
  {
    test: (url) => url.includes('/xian/analysis/zoneAnalysis'),
    handle: () => ({ code: '200', Status: 'Ok', data: ZONE_ANALYSIS_TEXT })
  },
  {
    test: (url) => url.includes('/xian/analysis/deviceAlarmAnalysis'),
    handle: (config) => {
      const body = tryParseJSON(config.data) || {}
      return {
        Status: 'Ok',
        data: {
          alarmAnalysis: `经分析，设备【${body.device_name || '目标设备'}】当前产生${body.event_level === 3 ? '故障' : '告警'}事项：${body.event_info || '状态异常'}。该事项与近期同类设备运行特征一致，属于可自动处置范围，建议按处置建议执行联动控制。`,
          suggestion: '建议立即检查设备供电与通信回路，并执行AI联动策略；若现场处置后仍未恢复，请转人工维修流程。',
          dispatchTasks: [
            {
              id: 'mock-task-' + Date.now(),
              level: body.event_level === 3 ? '高' : '中',
              problemDesc: body.event_info || '设备状态异常',
              decision_content: `针对【${body.device_name || '目标设备'}】生成联动处置方案，执行自动诊断与控制下发`,
              targetAgentName: '系統Agent',
              target_agent_id: 'commander',
              businessDomain: body.event_level === 3 ? '维修工单' : '设备控制',
              expectedEffect: '消除设备异常状态，恢复正常运行，预计10分钟内生效'
            }
          ]
        }
      }
    }
  },

  // ---------- iot（物联平台） ----------
  {
    test: (url) => url.includes('/iot/base/device/event/listPage'),
    handle: (config) => {
      const level = num(pick(config, 'event_level', 'eventLevel'), -1)
      const page = num(pick(config, 'page'), 1)
      const limit = num(pick(config, 'limit'), 10)
      let list = level >= 0 && level in eventPools ? eventPools[level] : [].concat(eventPools[0], eventPools[1], eventPools[2], eventPools[3])
      const subareaCid = pick(config, 'subarea_cid', 'subareaCid')
      if (subareaCid) list = list.filter(e => e.subarea_cid === subareaCid)
      return { code: '200', Status: 'Ok', data: pageSlice(list, page, limit), count: list.length }
    }
  },
  {
    test: (url) => url.includes('/iot/base/device/StateListPage'),
    handle: (config) => {
      const page = num(pick(config, 'page'), 1)
      const limit = num(pick(config, 'limit'), 10)
      const faultState = pick(config, 'fault_state', 'faultState')
      const alarmState = pick(config, 'alarm_state', 'alarmState')
      const deviceId = pick(config, 'device_id', 'deviceId')
      const subareaCid = pick(config, 'subarea_cid', 'subareaCid')
      let list = []
      if (deviceId) {
        list = [].concat(faultDevicePool, alarmDevicePool).filter(d => String(d.device_id) === String(deviceId))
      } else if (faultState !== undefined) {
        list = faultDevicePool
      } else if (alarmState !== undefined) {
        list = alarmDevicePool
      } else if (subareaCid) {
        list = [].concat(faultDevicePool, alarmDevicePool).filter(d => d.subarea_cid.startsWith(subareaCid))
      }
      return { code: '200', Status: 'Ok', data: pageSlice(list, page, limit), count: deviceId ? list.length : (faultState !== undefined || alarmState !== undefined ? list.length : 113) }
    }
  },
  {
    test: (url) => url.includes('/iot/base/device/event/processEvent'),
    handle: () => ({ code: '200', Status: 'Ok', message: '处理成功' })
  },
  {
    test: (url) => url.includes('/iot/base/device/data/listOneDeviceDataOrState'),
    handle: (config) => {
      const deviceId = pick(config, 'device_id', 'deviceId')
      const isFault = String(deviceId) === String(DEVICE_SENSOR.device_id)
      const items = isFault
        ? [
            { name: '遥信状态', value: 1 }, { name: '供电电压', value: 21.4 },
            { name: 'RS485通信', value: 0 }, { name: '温度', value: 26.8 },
            { name: '湿度', value: 79.2 }
          ]
        : [
            { name: '回风温度', value: 26.5 }, { name: '出风温度', value: 18.2 },
            { name: '运行电流', value: 12.6 }, { name: '设定温度', value: 26.5 },
            { name: '风机状态', value: 1 }
          ]
      const data = items.map((it, i) => ({
        device_id: String(deviceId || ''),
        create_time: ts(19 - i),
        data_name: it.name,
        data_short_name: it.name,
        dataValue: it.value,
        value: it.value,
        state: isFault && i < 2 ? 1 : 0,
        event_level: isFault && i < 2 ? 3 : 0,
        event_type_name: isFault && i < 2 ? '故障' : '正常'
      }))
      return { code: '200', Status: 'Ok', data }
    }
  },
  {
    test: (url) => url.includes('/iot/report/listData'),
    handle: (config) => {
      const timeType = pick(config, 'timeType')
      const dateTimes = String(pick(config, 'dateTimes') || '')
      const subCids = String(pick(config, 'subCids') || '0.001.001.001')
      const values = timeType === 'year' ? yearEnergyArray() : monthEnergyArray()
      return { code: '200', Status: 'Ok', sub: { [dateTimes]: { [subCids]: values } } }
    }
  },
  {
    test: (url) => url.includes('/iot/tunnel/statistics/getTunnelsFaultCount'),
    handle: () => ({
      Status: 'Ok',
      data: {
        '0.001.001.001-0': { fault_count: 0, alarm_count: 1 },
        '0.001.001.001-0.001': { fault_count: 0, alarm_count: 0 },
        '0.001.001.001-0.002': { fault_count: 1, alarm_count: 0 },
        '0.001.001.001-0.003': { fault_count: 0, alarm_count: 0 }
      }
    })
  },
  {
    test: (url) => url.includes('/iot/passenger-flow/station/realtime'),
    handle: () => ({
      code: '200',
      data: {
        stationCurrPeo: 48652,
        stationInPeo: 31210,
        stationOutPeo: 29874,
        updateTime: hm(1)
      }
    })
  },
  {
    test: (url) => url.includes('/iot/passenger-flow/station/crowded'),
    handle: () => ({ code: '200', data: [] })
  },

  // ---------- pos（运维平台） ----------
  {
    test: (url) => url.includes('/pos/asset-info'),
    handle: (config) => {
      const usage = num(pick(config, 'usageStatuses'), -1)
      const dist = { 10: 120, 20: 68, 30: 88, 40: 42, 50: 20, 60: 8 }
      const count = usage >= 0 ? (dist[usage] || 0) : 346
      return { Status: 'Ok', count, data: [] }
    }
  },
  {
    test: (url) => url.includes('/pos/diagnosis/action/create-by-ai'),
    handle: () => ({
      Status: 'Ok',
      data: {
        confidence: 0.92,
        reason: '二合一传感器（TX23）遥信回路故障',
        reasonDetail: '设备遥信值频繁翻转（1→0→1→0），判断为供电电压波动或RS485通信干扰导致传感单元工作异常，排除真实环境量突变。',
        conclusion: '建议现场检查传感器供电与通信回路，必要时更换传感器探头',
        conclusionDetail: '按AI处置建议流程执行：1) 测量DC24V供电；2) 紧固RS485接线；3) 断电重启；4) 仍异常则更换备件并重新绑定数据点。',
        measures: JSON.stringify([
          '测量传感器供电电压（DC24V±5%），异常则更换电源模块',
          '检查RS485 A/B接线并紧固端子，测量总线电压',
          '断电30秒后重新上电，观察遥信值是否稳定',
          '上述步骤无效时更换同型号传感器探头并重新绑定数据点'
        ]),
        executeUserId: '1002',
        executeUserName: '李强',
        tools: '万用表、螺丝刀、备用传感器探头',
        priority: 'HIGH',
        arrivalTime: '30分钟内',
        completionTime: '2小时内',
        orderId: 'MOCK-20260908-001'
      }
    })
  },
  {
    test: (url) => url.includes('/pos/diagnosis'),
    handle: (config) => {
      const today = pick(config, 'createTimeBegin')
      return { Status: 'Ok', count: today ? 8 : 156, data: [] }
    }
  },
  {
    test: (url) => url.includes('/pos/ins/orders'),
    handle: () => ({ Status: 'Ok', count: 23, data: [] })
  },
  {
    test: (url) => /\/pos\/repair\/orders\/[^/]+\/action\/update-staff$/.test(url),
    handle: () => ({ Status: 'Ok', message: '更新成功' })
  },
  {
    test: (url) => /\/pos\/repair\/orders\/[^/]+\/flow$/.test(url),
    handle: () => ({ Status: 'Ok', data: null })
  },
  {
    test: (url) => /\/pos\/repair\/orders\/[^/]+$/.test(url),
    handle: () => ({
      Status: 'Ok',
      data: { orderTitle: '到达层10/0.4kV变电所传感器1维修', orderNo: 'MOCK-20260908-001', status: 30 }
    })
  },
  {
    test: (url) => url.includes('/pos/repair/staff'),
    handle: () => ({
      Status: 'Ok',
      data: [
        { id: '1', userId: '1001', name: '张伟', skillsDesc: '变配电设备检修', mobile: '138****1234', email: 'zhangwei@demo.com' },
        { id: '2', userId: '1002', name: '李强', skillsDesc: '暖通空调/传感器仪表', mobile: '139****5678', email: 'liqiang@demo.com' },
        { id: '3', userId: '1003', name: '王芳', skillsDesc: '自控与通信系统', mobile: '136****9012', email: 'wangfang@demo.com' }
      ]
    })
  },
  {
    test: (url) => url.includes('/pos/repair/orders'),
    handle: (config) => {
      const todayCreate = pick(config, 'createTimeBegin')
      const todayDispatch = pick(config, 'dispatchTimeBegin')
      const dispatchType = pick(config, 'dispatchType')
      const status = pick(config, 'status')
      let count = 89
      if (todayCreate || todayDispatch) count = todayDispatch ? 5 : 6
      else if (dispatchType === 'AUTO') count = 42
      else if (status !== undefined && String(status).indexOf(',') < 0) {
        const perStatus = { 10: 12, 20: 8, 30: 15, 40: 6, 50: 48 }
        count = perStatus[String(status)] || 0
      }
      return { Status: 'Ok', count, data: [] }
    }
  },
  {
    test: (url) => url.includes('/pos/repair/issue'),
    handle: (config) => {
      if (String(config.method || '').toLowerCase() === 'get') {
        const list = [
          {
            id: 'mock-issue-001',
            issueTitle: '到达层10/0.4kV变电所传感器1故障',
            issueContent: 'LK246-遥信 TX23_变电所传感器_二合一传感器故障 故障(当前值=1)',
            foundTime: nowDateTime(),
            status: 'TO_HANDLE',
            issueLevel: 'CRITICAL',
            asset: { areaName: '到达层', location: '到达层10' }
          },
          {
            id: 'mock-issue-002',
            issueTitle: '出发层多联机内机2通讯中断',
            issueContent: '设备通讯中断，数据停止更新',
            foundTime: nowDateTime(),
            status: 'TO_HANDLE',
            issueLevel: 'HIGH',
            asset: { areaName: '出发层', location: '出发层候车区' }
          }
        ]
        return { Status: 'Ok', data: list, count: list.length }
      }
      return { Status: 'Ok', data: 'mock-issue-' + Date.now(), message: '上报成功' }
    }
  },

  // ---------- ragflow（知识库） ----------
  {
    test: (url) => url.includes('/ragflow/ragflowStatistics'),
    handle: () => ({
      success: true,
      data: {
        queryCount: 1289,
        hitRate: 0.876,
        knowledgeFileCount: [
          { name: '设备操作手册', count: 42 },
          { name: '运维检修规程', count: 35 },
          { name: '应急预案', count: 18 },
          { name: '故障案例库', count: 27 }
        ],
        knowledgeFileTotal: 122
      }
    })
  },

  // ---------- keliu（客流） ----------
  {
    test: (url) => url.includes('/keliuApi/v1/monitor/overviewCards'),
    handle: () => ({
      code: 200,
      data: [
        { name: '今日告警数量', value: 7 },
        { name: '今日严重事项数量', value: 3 },
        { name: '今日一般事项数量', value: 7 },
        { name: '今日通知事项数量', value: 5 },
        { name: '当前运营状态', value: '运营中' },
        { name: '当前客流量', value: '48652人' },
        { name: '当前车流量', value: '126列' },
        { name: '当前拥挤区域个数', value: 0 }
      ]
    })
  },

  // ---------- ems（能源） ----------
  {
    test: (url) => url.includes('/emsAgent/api/collab/statistics/summary'),
    handle: () => ({
      code: 200,
      data: {
        todayTotal: 12,
        yesterdayCompareRate: -3.2,
        todayDecisionPassRate: 91.7,
        todayDecisionSuccessRate: 88.9,
        total: 386,
        decisionPassRate: 90.2,
        decisionSuccessRate: 87.5,
        timeoutCount: 5,
        timeoutRate: 1.3
      }
    })
  }
]

/** 兜底响应：未匹配到的接口统一返回成功空数据，保证页面不报错 */
function fallbackResponse() {
  return { code: '200', Status: 'Ok', success: true, data: null, count: 0 }
}

/**
 * axios 自定义 adapter：按 URL 路由到本地假数据
 */
function dashboardMockAdapter(config) {
  const fullUrl = (config.baseURL || '') + (config.url || '')
  const route = MOCK_ROUTES.find(r => r.test(fullUrl))
  const body = route ? route.handle(config) : fallbackResponse()
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({
        data: body,
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
        request: {}
      })
    }, 60 + Math.random() * 90)
  })
}

/**
 * 将 mock adapter 安装到共享 axios 实例上（页面及其全部子组件生效）。
 *
 * 采用引用计数 + 延迟还原：Vue 的 onUnmounted 在渲染后的 post 队列执行，
 * SPA 跳转时"新页面 setup 安装"会先于"旧页面 onUnmounted 还原"之前完成，
 * 若每次安装/还原都独立保存/恢复，旧页面会把 adapter 还原成真实请求，
 * 导致新页面首屏请求全部失败（首次进入图表与列表无数据）。
 * 因此仅在完全退出所有演示页面（计数归零）后再延迟还原真实适配器。
 *
 * @returns {Function} uninstall 卸载函数（onUnmounted 时调用）
 */
let mockActiveCount = 0
let savedRealAdapters = null
let restoreTimer = null

export function installDashboardMock() {
  const instances = [iotRequest, aiRequest, posRequest, keliuRequest, ragflowRequest, emsRequest]
  if (restoreTimer) {
    clearTimeout(restoreTimer)
    restoreTimer = null
  }
  if (mockActiveCount === 0) {
    savedRealAdapters = new Map()
    instances.forEach((inst) => {
      savedRealAdapters.set(inst, inst.defaults.adapter)
      inst.defaults.adapter = dashboardMockAdapter
    })
    console.info('[dashboard1] 已启用演示数据适配器（无后端模式）')
  }
  mockActiveCount++
  return function uninstallDashboardMock() {
    mockActiveCount = Math.max(0, mockActiveCount - 1)
    if (mockActiveCount === 0 && savedRealAdapters) {
      // 延迟恢复，等待路由过渡与旧页面 onUnmounted 完成后再还原
      restoreTimer = setTimeout(() => {
        restoreTimer = null
        if (mockActiveCount === 0 && savedRealAdapters) {
          savedRealAdapters.forEach((adapter, inst) => {
            inst.defaults.adapter = adapter
          })
          savedRealAdapters = null
          console.info('[dashboard1] 已恢复真实请求适配器')
        }
      }, 2000)
    }
  }
}

export default dashboardMockAdapter
