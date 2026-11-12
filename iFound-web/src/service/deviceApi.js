import { iotRequest } from '@/utils/request'

/**
 * @file deviceApi.js
 * @description 设备相关接口封装（对接后端 SCADA /base/device/event/*、/base/device/StateListPage 等接口）
 * @author   auto-generated
 */

/**
 * 设备事件分页查询
 *
 * 接口地址：POST /base/device/event/listPage
 * 接口描述：根据条件查询设备事件列表。
 * 必选参数：org_id(单位id)、start_time(起始时间)
 * 可选参数：system_type(系统类型)、subarea_cid(设备分区)、
 *          event_level(事项等级)、event_state(事项状态 0恢复 1异常)、
 *          device_id(设备id)、process_state(处理状态 0/1)、end_time(截止时间)
 *
 * @typedef {Object} DeviceEventQueryParams
 * @property {string}  page           页码，required，示例：'1'
 * @property {string}  limit          每页数量，required，示例：'10'
 * @property {string}  orgId          单位id，固定为3（西安东站），required，示例：'3'
 * @property {string}  subareaCid     区域id，查询整个站台时为 '0.001.001.001-0'，required，示例：'0.001.001.001-0'
 * @property {string}  startTime      事件范围查询的开始时间，格式 yyyyMMddHHmmss，required，示例：'20260511101308'
 * @property {string}  [endTime]      事件范围查询的结束时间，格式 yyyyMMddHHmmss，示例：'20260619101308'
 * @property {string}  [systemType]   系统类型
 * @property {string}  [eventLevel]   事件等级：3 故障事项 / 2 告警事项 / 1 通知事项 / 10 火警事项，示例：'3'
 * @property {string}  [eventState]   事项状态：0 恢复 / 1 异常，示例：'1'
 * @property {string}  [processState] 事项处理状态：1 恢复 / 0 异常，示例：'0'
 * @property {string}  [deviceId]     设备id
 *
 * @param {DeviceEventQueryParams} params 请求体参数（与后端 ScadaEventQueryDto 字段对应）
 * @returns {Promise<{code?: string, Status?: string, data?: any[], count?: number}>}
 *   统一返回结构：{ code|Status, data, count }
 *   - data  设备事件列表
 *   - count 总条数
 */
export const listDeviceEventPage = (params) => {
  return iotRequest.post('/base/device/event/listPage', params, {
    headers: { 'Content-Type': 'application/json' }
  })
}

/**
 * 设备列表与设备状态分页查询
 *
 * 接口地址：POST /base/device/StateListPage
 * 接口描述：根据条件查询设备列表与设备状态。
 * 必选参数：page(页码)、limit(每页数量)、orgId(组织ID，固定为3)
 * 可选参数：subareaCid(区域CID，右模糊查询)、deviceType(设备类型，右模糊查询)、
 *          systemType(系统类型)、faultState(只查故障设备)、alarmState(只查告警设备)、
 *          addChangeStateTime(是否添加告警和故障设备的变更时间)
 *
 * @typedef {Object} ScadaPageQueryParams
 * @property {string}  page                 页码，required，示例：'1'
 * @property {string}  limit                每页数量，required，示例：'10'
 * @property {string}  orgId                组织ID，固定为3，required，示例：'3'
 * @property {string}  [subareaCid]         区域CID，右模糊查询，示例：'0.001.001.001-0'
 * @property {string}  [deviceType]         设备类型，右模糊查询，示例：'1.001'
 * @property {string}  [systemType]         系统类型
 * @property {string}  [faultState]         有该参数代表只查询故障的设备
 * @property {string}  [alarmState]         有该参数代表只查询告警的设备
 * @property {number}  [addChangeStateTime] 是否添加告警和故障设备的变更时间，0 否 / 1 是，示例：1
 *
 * @param {ScadaPageQueryParams} params 请求体参数（与后端 ScadaPageQueryDto 字段对应）
 * @returns {Promise<{code?: string, Status?: string, data?: any[], count?: number}>}
 *   统一返回结构：{ code|Status, data, count }
 *   - data  设备列表（含设备状态）
 *   - count 总条数
 */
export const listDeviceStatePage = (params) => {
  return iotRequest.post('/base/device/StateListPage', params, {
    headers: { 'Content-Type': 'application/json' }
  })
}