function hasValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== ''
}

function displayValue(value, fallback = '-') {
  return hasValue(value) ? String(value) : fallback
}

function statusClass(status) {
  const text = displayValue(status, '')
  if (text.includes('故障') || text.includes('高') || text.includes('异常')) return 'red-text'
  if (text.includes('注意') || text.includes('告警') || text.includes('中')) return 'orange-text'
  return 'blue-text'
}

function countDeviceIssues(zone) {
  const rawDev = zone?._rawDev || {}
  return Number(rawDev.fault_total || 0)
}

function countDeviceAlarms(zone) {
  const rawDev = zone?._rawDev || {}
  return Number(rawDev.alarm_total || 0)
}

function countEnvIssues(zone) {
  // environment 为对象格式：{ totalDevices, abnormalDevices }
  const env = zone?._rawEnvironment
  if (env && typeof env === 'object' && !Array.isArray(env)) {
    return Number(env.abnormalDevices || 0)
  }
  return 0
}

function deriveEnvScore(zone, envIssueCount) {
  const score = Math.max(60, 100 - envIssueCount * 8)
  return `${score}分`
}

function getPrimaryRiskText(zone, anomalyTotal) {
  const desc = Array.isArray(zone?._desc) ? zone._desc.filter(Boolean) : []
  const riskDesc = desc.filter(item => !String(item).includes('正常运行'))
  if (riskDesc.length > 0) return riskDesc.slice(0, 3).join('；')
  if (anomalyTotal > 0) return `发现${anomalyTotal}项需关注指标`
  return '当前分区运行平稳'
}

export function buildZoneCardSnapshot(zone = {}) {
  const envIssueCount = countEnvIssues(zone)
  const deviceIssueCount = countDeviceIssues(zone)
  const deviceAlarmCount = countDeviceAlarms(zone)
  const anomalyTotal = envIssueCount + deviceIssueCount + deviceAlarmCount
  const envStatus = envIssueCount > 0 ? '注意' : '正常'
  const envScore = envIssueCount > 0 ? '有'+envIssueCount+'个问题' : '100分'

  // 总分计算
  const energyPenalty = displayValue(zone?.energyStatus, '').includes('注意') ? 5 : 0
  const totalScore = Math.max(60, 100 - envIssueCount * 8 - deviceIssueCount * 6 - deviceAlarmCount * 3 - energyPenalty)

  const metrics = [
    {
      key: 'env',
      label: '环境',
      value: envStatus,
      detail: envScore,
      className: envIssueCount > 0 ? 'orange-text' : 'blue-text'
    },
    {
      key: 'device',
      label: '设备',
      value: deviceIssueCount > 0 ? `${deviceIssueCount}台故障` : '无故障',
      detail: deviceAlarmCount > 0 ? `${deviceAlarmCount}台告警` : '无告警',
      className: deviceIssueCount > 0
        ? displayValue(zone.deviceStatusClass, 'red-text')
        : 'blue-text',
      detailClassName: deviceAlarmCount > 0 ? 'orange-text' : ''
    },
    {
      key: 'energy',
      label: '能耗',
      value: displayValue(zone.energyToday),
      detail: displayValue(zone.energyStatus, '-'),
      className: displayValue(zone.energyStatusClass, statusClass(zone.energyStatus))
    }
  ]

  return {
    name: displayValue(zone.name, '未知区域'),
    status: displayValue(zone.status, anomalyTotal > 0 ? '注意' : '正常'),
    envScore,
    envStatus,
    totalScore,
    anomalyTotal,
    envIssueCount,
    deviceIssueCount,
    deviceAlarmCount,
    primaryRiskText: getPrimaryRiskText(zone, anomalyTotal),
    envItems: [],
    energyToday: displayValue(zone.energyToday || zone._rawEnergy?.value, '-'),
    energyStatus: displayValue(zone.energyStatus || zone._rawEnergy?.status, '-'),
    metrics
  }
}

export function buildZoneAnalysisPayload(zone = {}) {
  const subareaCid = zone._rawSubareaCid || zone.subareaCid || zone.subarea_cid
  const zoneName = zone.name || zone.zoneName || zone.zone_name
  const deviceFaultCount = Number(zone._rawDev?.fault_total || zone.deviceFaultCount || zone.device_fault_count || 0)
  const deviceAlarmCount = Number(zone._rawDev?.alarm_total || zone.deviceAlarmCount || zone.device_alarm_count || 0)
  const deviceList = (zone.anomalyDevices || zone.deviceList || zone.device_list || []).map(device => ({
    id: device.id,
    name: device.name,
    type: device.type,
    status: device.status,
    currentData: device.currentData,
    location: device.location
  }))

  // environment 为对象格式：{ totalDevices, abnormalDevices }
  const rawEnv = zone._rawEnvironment
  const envTotalDevices = (rawEnv && typeof rawEnv === 'object' && !Array.isArray(rawEnv))
    ? Number(rawEnv.totalDevices || 0)
    : 0
  const envAbnormalDevices = (rawEnv && typeof rawEnv === 'object' && !Array.isArray(rawEnv))
    ? Number(rawEnv.abnormalDevices || 0)
    : 0

  return {
    subareaCid,
    subarea_cid: subareaCid,
    zoneName,
    zone_name: zoneName,
    deviceFaultCount,
    device_fault_count: deviceFaultCount,
    deviceAlarmCount,
    device_alarm_count: deviceAlarmCount,
    envTotalDevices,
    envAbnormalDevices,
    deviceList,
    device_list: deviceList,
    keliu: zone._rawKeliu || {},
    energy: zone._rawEnergy || {},
    desc: Array.isArray(zone._desc) ? zone._desc : []
  }
}
