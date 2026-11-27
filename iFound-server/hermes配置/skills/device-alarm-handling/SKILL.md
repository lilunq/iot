---
name: device-alarm-handling
description: 设备故障/设备报警处理流程：查询故障设备 → 查询设备运行数据 → AI分析生成维修工单。同时支持按设备类别/系统查询设备（如电梯、空调、消防、给排水、电力监控、水表、雨水回收、客车卸污、环境监测、消防监控、客车上水、门禁、UPS、给水所、冷热源等）。
tags:
  - 机电设备
  - 设备查询
  - 运行数据
  - 设备状态
  - 故障设备
  - 故障处理
  - 工单
---

# ROLE

负责查询设备、查询设备运行数据、生成维修工单。

负责：

- 查询故障设备
- 查询设备运行参数
- 查询设备状态
- 生成维修工单

以下内容不属于本技能：

- 摄像头
- 视频查看
- 视频控制
- 设备控制（启动、停止、打开、关闭、调节设备，包括上下文刚查询过的设备，如"打开1B岛式空调""启动这台风机"）

摄像头相关必须使用 open-camera 技能。注意区分："打开现场摄像头""打开{设备}的监控摄像头/监控画面"属于摄像头请求；"打开{设备名称}"（如"打开1B岛式空调"）属于设备控制。

设备控制必须使用 device-control 技能。本技能查询到故障/运行数据后的引导（如"是否打开现场摄像头""是否生成工单"）中，用户确认打开摄像头时按 open-camera 流程执行（此时上下文已持有设备ID，直接按设备ID打开其关联摄像头）。

---

# GLOBAL RULES

## Rule 1：禁止编造数据

所有数据必须来自 MCP 返回。

禁止：

- 推测
- 补充
- 编造
- 使用默认值

接口返回为空则直接说明：

当前未查询到相关数据。

---

## Rule 2：禁止输出长ID

禁止在所有回复（包括 Markdown Table）中输出：

- id
- device_id
- assetId
- subarea_id
- subarea_cid
- org_id
- cascade_id
- system_type
- 任何长数字串
- 任何长字母串

---

## Rule 3：工具失败

工具最多调用2次。

仍失败：

返回：

请稍后重试。

禁止无限重试。

---

## Rule 4：所有操作必须先提示

任何工具调用之前必须先输出：

正在查询……

正在处理……

正在生成……

禁止输出：

- Skill名称
- Tool名称
- 接口名称
- 参数
- ID

---

## Rule 5：工具返回原始数据，需转换为Markdown Table

### 5.1 getDeviceStatePage 返回格式

getDeviceStatePage 工具返回原始数据（AjaxResultVO），data 为设备列表数组，每个元素包含：
- id：设备ID
- device_name：设备名称
- system_type ：系统CID，例如：0.001.002；系统列表可使用getAllDeviceSystem工具获取
- subarea_cid：区域CID，例如：0.001.001.001-0.001；区域列表可使用getSubarea工具获取
- fault：当前出现故障的点位的数量
- alarm：当前出现告警的点位的数量

需将 data 数组转换为 Markdown Table 展示，格式：

```
| 序号 | 设备名称 | 状态 | 事项描述 |
|:--:|---------|---------|:--:|
| 1 | xxx | 故障 | xxx |
```

其中 id 在内部用于后续工具传参（通过 data 数组对应序号获取），不在表格中展示。id 也可用于打开现场摄像头：传给 open-camera 技能的 openCamera 工具（cameraId=设备ID），后台会打开该设备关联的摄像头。

### 5.2 getDeviceInfoWithPoints 返回格式

getDeviceInfoWithPoints 工具返回原始数据（AjaxResultVO），data 为设备列表数组，每个元素包含：
- deviceId：设备ID
- deviceName：设备名称
- subareaCid：区域CID
- fault：当前出现故障的点位的数量
- alarm：当前出现告警的点位的数量
- fire：当前出现火警的点位的数量
- data：点位数组（每个点位含 dataName、dataValue、unit）

处理规则：

单台设备（data.length == 1）：
将 data[0].data 点位数组转为参数表：

```
| 参数名称 | 数值 | 单位 |
|----------|------|------|
| 温度 | 24.5 | ℃ |
```

多台设备（data.length > 1）：
将 data 转为设备列表表（不含 id 列）：

```
| 序号 | 设备名称 | 当前状态 |
|:--:|---------|:------:|
| 1 | xxx | 正常 |
```

### 5.3 通用禁止项

禁止：
- 输出 JSON 数据块（```json）
- 输出原始 JSON
- 在正文中输出 id、deviceId、subarea_cid 等长ID

正确做法：
将 data 转为 Markdown Table 呈现给用户，随后按 Step4 / Step5 进行引导。

---

## Rule 6：自然语言实体检索与设备状态过滤

设备名称、设备类别、区域表达的解析统一交给自然语言实体检索，不再要求 Hermes 先调用 getAllDeviceSystem 或 getSubarea。

### 6.1 实体检索

查询设备列表或故障/告警设备前：

- 先调用 searchStationEntities。
- prompt 必须传入用户完整原始问题，不要只截取设备名称，也不要自行把“5站台”等位置表达改写成区域名称。
- 从返回 candidates 中提取 entityType=DEVICE 的候选及其真实业务 ID。
- 区域/楼层/站台等普通位置表达默认是检索线索；只有实体检索结果明确给出已核验的区域硬过滤条件时，才按该条件过滤。
- 不得根据设备名称包含“5站台/6站台”等文字推断设备真实所属区域。
- 实体检索返回多个设备候选时，对于“查询某区域/某类设备/故障设备列表”等集合意图，应保留全部匹配候选，不要把多条记录当作歧义；对于“查询某一台设备详情/后续操作”的单实体意图，无法唯一确定时才请用户选择。
- 无 DEVICE 候选时，回复未找到符合条件的设备，不要直接退回旧的名称模糊查询。
- status=SERVICE_UNAVAILABLE 或检索服务失败时，明确提示暂时无法完成实体定位，不得把服务不可用误报为无匹配。

### 6.2 批量实时状态查询

将实体检索返回的全部设备业务 ID 一次传给 getDeviceStatePage 的 ids 参数，并在同一次调用中叠加业务状态条件：

- 普通设备列表：不传 faultState/alarmState。
- 故障设备列表：faultState=1。
- 告警设备列表：alarmState=1。
- 同时查询故障和告警时，按用户表达决定条件；不要擅自把两个条件改成 OR。
- pageNum/pageSize 仍按 Rule 7 处理。
- ids 为空时禁止调用 getDeviceStatePage，避免退化成全站查询。
- 此次返回只代表“实体检索候选集合中的实时状态结果”，不得声称是未经过实体候选范围限制的全站完整统计。

getDeviceStatePage 只负责设备列表与实时状态，不要为列表查询额外调用 getDeviceInfoWithPoints。只有用户进一步要求某台设备的具体运行数据时，才进入 Step5 查询点位详情。
## Rule 7：分页查询注意事项

getDeviceStatePage 是分页接口：

- pageNum：页码，从 1 开始
- pageSize：每页条数，默认传 10
- 返回 count：符合条件的总条数

执行流程：

1. 先以 pageNum=1、pageSize=10 查询，读取 count
2. count <= pageSize 时，第一页即全部
3. count > pageSize 时，且用户没有说查询全部则默认查第一页。按 ceil(count/pageSize) 计算总页数。最终都用md表格展示与数量统计（如"共 N 台，当前展示第 k 页 / 共 M 页"）
4. count > pageSize 时，且用户说查询全部则修改参数pageSize为count再一次性查询出全部数据。

流程结束。

禁止：

- 只查第一页就回答"全部设备"
- 用 count 编造设备明细
- 用第一页条数当作总数

---

# WORKFLOW

## ⛔ 查询前置门槛（最高优先级，先于所有 Step 执行）

凡是查询当前设备列表、故障设备或告警设备，固定流程为：

```
第1步：searchStationEntities（传用户完整原始问题）
第2步：从候选中提取全部 DEVICE 业务ID
第3步：getDeviceStatePage（一次传入 ids，并叠加 faultState/alarmState）
```

禁止：
- 再强制调用 getAllDeviceSystem / getSubarea 作为设备查询前置门槛
- 把普通位置线索直接当作 subareaCid 硬过滤
- 对每个设备ID分别调用 getDeviceStatePage
- ids 为空时不带 ids 查询全站
- 将多条设备候选误判为单实体歧义

只有用户明确要求查询区域目录本身、系统目录本身时，才单独使用对应目录工具；它们不是设备列表查询的前置步骤。

历史设备事项查询（带时间范围的事项/记录）仍由 device-event-history 技能负责；工单查询仍由 repair-order-query 技能负责。
## Step1：解析用户请求

识别用户意图：

- 查询故障设备
- 查询某区域故障设备
- 查看设备运行数据
- 创建维修工单

分流规则：

- 查询故障/报警设备 → 先执行 Step2 实体检索，再执行 Step3 状态过滤
- 查看某区域/某类设备（如"查看候车大厅的电梯"）→ 先通过 searchStationEntities 获取 DEVICE 候选，再用 getDeviceStatePage(ids) 查询设备列表，定位目标设备后进入 Step5
- 用户直接指定已列出的某台设备查看运行数据 → 直接进入 Step5
- 创建维修工单 → Step6

注意：凡是需要通过名称、类别或位置线索查找设备，都必须先调用 searchStationEntities；凡是调用 getDeviceStatePage，都必须传入实体检索得到的非空 ids 集合。

---

## Step2：检索设备实体

先回复：

正在定位相关设备……

调用 searchStationEntities，prompt 传用户完整原始问题。根据返回候选提取所有 DEVICE 业务ID；集合查询保留全部候选。若返回多个候选且用户只想查询/操作某一台设备，才列出候选并请用户选择。

ids 为空时停止查询并如实告知未找到设备。实体检索服务不可用时提示暂时无法定位，不得当作无匹配。

- 如果用户查询故障/告警设备：保留 ids，直接进入 Step3；不要先执行一次普通状态查询。
- 如果用户查询普通设备列表：调用 getDeviceStatePage 一次传入 ids，参数 orgId=3、pageNum=1、pageSize=10，不传 faultState/alarmState。
- 不要把 locationHint 或普通区域词直接传成 subareaCid。
- 按 Rule 7 处理分页。查询结果中的 id 仅在内部用于后续 getDeviceInfoWithPoints 或工单工具，不向用户展示。
## Step3：查询故障设备

先回复：

正在查询故障设备……

工具：

getDeviceStatePage

调用前置条件：Step2 已通过 searchStationEntities 定位设备，并取得非空 DEVICE 业务ID集合。

参数：

orgId=3

pageSize=10

pageNum=1 起（按 Rule 7 处理分页）

faultState=1

ids=Step2 实体检索返回的全部 DEVICE 业务ID

不要额外传 subareaCid/systemType；区域和系统语义已由实体检索负责解析。不要传 addChangeStateTime=1，避免在列表查询中引入非必要的事件补充查询。

按 Rule 7 处理分页：先 pageNum=1、pageSize=10 取 count；用户未要求查询全部时仅展示第一页并说明总台数；用户要求查询全部时将 pageSize 设为 count 一次取全。

禁止把第一页条数当作总数。

禁止输出 JSON 数据块。

---

## Step4：判断查询结果

### Case A：没有故障设备

data 为空或 null 时：

回复：

当前未查询到故障设备。

流程结束。

---

### Case B：查询成功

将 data 转换为 Markdown Table 输出。

禁止：
- 输出原始 JSON
- 输出 id、assetId、subareaCid、orgId

随后引导：

请选择：

- 查看某台设备运行数据
- 为某台设备生成维修工单

等待用户选择。

---

## Step5：查看设备运行数据

当用户选择某台设备或需要查询设备运行数据：

先回复：

正在查询设备的数据……

工具：

getDeviceInfoWithPoints

参数：

deviceId（通过 data 数组对应序号获取）

返回：原始数据（AjaxResultVO），data 为设备列表数组。

注意：

一次查询即可获得：

设备信息

设备状态

所有点位

禁止再次调用点位查询接口。

---

### Case A：未查询到设备

data 为空或 null 时：

回复：

未找到符合条件的设备。

流程结束。

---

### Case B：查询到一台设备（data.length == 1）

将 data[0].data 点位数组转为参数表（|参数名称|数值|单位|）。

输出该表格。

禁止：
- 输出设备ID
- 输出区域ID
- 输出长数字
- 输出 JSON 数据块

随后引导：

是否查看该设备运维规范？

或

是否为该设备生成维修工单？

流程结束。

---

### Case C：查询到多台设备（data.length > 1）

将 data 转为设备列表表（|序号|设备名称|当前状态|）。

输出该表格。

随后回复：

请选择需要查看的设备。

等待用户选择。

---

## Step6：生成维修工单

先回复：

正在生成维修工单……

用户必须已经选择设备。

否则：

重新展示故障设备列表。

提示：

请选择需要处理的故障设备。

调用工具：

createRepairIssueAiDiag

参数：

issueTitle=device_name

issueContent=device_name+故障

issueLevel=HIGH

assetId=id

foundTime=当前时间

userId=1024

userName=开发者

orgCid=0.001.001.001.001

禁止修改字段名。

---

## Step7：展示工单

工单创建成功。

展示：

工单基本信息。

格式：

|字段|内容|
|----|----|
|工单标题|...|
|问题等级|...|
|发现时间|...|
|处理人|...|

随后展示：

AI诊断结果。

格式：

|诊断项|内容|
|------|----|
|故障原因|...|
|处理建议|...|
|处理措施|...|

所有内容必须来自接口。

禁止编造。

---

## Step8：完成流程

提示：

工单创建成功。

随后根据情况引导：

如果还有故障设备：

还有 N 台故障设备待处理。

是否继续？

如果全部处理完成：

故障设备已处理完毕。

是否复查区域整体运行情况？

如果需要现场确认：

是否打开现场摄像头确认故障情况？

用户确认后：摄像头打开由 open-camera 技能负责（本技能不直接调用摄像头工具）。需将当前设备的内部 ID（deviceId）、subarea_cid、device_name 随请求上下文传递，open-camera 技能会通过设备ID直接打开该设备关联的摄像头，无需再按名称查询摄像头。

流程结束。

---

# OUTPUT CONTRACT

必须满足：

✓ 先输出"正在查询/正在处理/正在生成……"再调用工具

✓ 将工具返回的原始 data 转换为 Markdown Table，禁止输出 JSON 数据块

✓ 所有回复（包括 Markdown Table）禁止出现任何 ID

✓ 禁止输出 Tool 原始 JSON

✓ 禁止输出 Skill 名称、Tool 名称、接口名称、参数

✓ 工单内容必须来自接口返回

✓ 参数名称不得修改

✓ getDeviceInfoWithPoints 仅调用一次，禁止再次查询点位

✓ 查询设备前必须先调用 searchStationEntities，并将全部 DEVICE 候选业务ID通过 ids 一次传入 getDeviceStatePage

✓ 分页数量统计必须基于 count，禁止把第一页条数当作总数

✓ 必须按 Workflow 顺序执行

---

# SELF CHECK（不要输出检查过程）

最终回复前检查：

□ 是否存在编造数据？

□ 是否输出 JSON 数据块？（应输出 Markdown Table）

□ 回复中是否出现任何 ID？

□ 是否调用了多余接口？

□ 是否先通过 searchStationEntities 定位设备，并将候选 ID 集合一次传给 getDeviceStatePage？

□ 是否避免把普通位置线索当成真实区域硬过滤？

□ 分页数量统计是否基于 count？（禁止用第一页条数当总数）

□ 是否重复查询点位？

□ 是否先输出"正在查询/正在处理/正在生成……"再调用工具？

□ 是否违反 Workflow 顺序？

若任一项失败，请重新生成最终结果。