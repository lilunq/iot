import { describe, expect, it } from 'vitest'
import { buildZoneAnalysisPayload, buildZoneCardSnapshot } from './zoneSituation'

describe('zone situation helpers', () => {
  it('builds card display values from processed zone data when AI fields are still empty', () => {
    const zone = {
      name: '站厅A区',
      status: '注意',
      envScore: '',
      envStatus: '',
      suggestion: '',
      anomalyDevices: [
        { id: 'DEV-1', name: '排风机1#', type: '风机', status: '告警', location: '站厅A区' }
      ],
      deviceStatus: '1台告警',
      passengerDensity: '32人/㎡',
      passengerStatus: '正常',
      energyToday: '126kWh',
      energyStatus: '注意',
      _rawDev: { fault_total: 0, alarm_total: 1 },
      _rawEnvironment: { totalDevices: 30, abnormalDevices: 5 },
      _desc: ['环境异常5处', '设备告警1台']
    }

    const snapshot = buildZoneCardSnapshot(zone)

    expect(snapshot.envScore).not.toBe('')
    expect(snapshot.envStatus).toBe('注意')
    expect(snapshot.envIssueCount).toBe(5)
    expect(snapshot.anomalyTotal).toBe(6)
    expect(snapshot.primaryRiskText).toContain('环境异常5处')
    expect(snapshot.metrics.map(item => item.label)).toEqual(['环境', '设备', '能耗'])
  })

  it('builds an AI analysis payload for every zone, including normal zones', () => {
    const zone = {
      name: '候车厅B区',
      _rawSubareaCid: 'subarea-02',
      _rawIsWarn: false,
      _rawDev: { fault_total: 0, alarm_total: 0 },
      _rawEnvironment: { totalDevices: 20, abnormalDevices: 0 },
      _rawKeliu: { status: '0', value: 18 },
      _rawEnergy: { status: '0', value: 84 },
      _desc: ['正常运行'],
      anomalyDevices: []
    }

    const payload = buildZoneAnalysisPayload(zone)

    expect(payload.subarea_cid).toBe('subarea-02')
    expect(payload.zone_name).toBe('候车厅B区')
    expect(payload.device_fault_count).toBe(0)
    expect(payload.envTotalDevices).toBe(20)
    expect(payload.envAbnormalDevices).toBe(0)
    expect(payload.desc).toEqual(['正常运行'])
  })
})
