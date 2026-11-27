---
name: hygiene-monitoring
description: 卫生监测/卫生间/厕所/洗手间/智慧厕所类设备查询：查询智慧厕所系统的设备列表、设备状态、故障/告警设备、设备运行点位数据。
tags:
  - 卫生监测
  - 智慧厕所
  - 卫生间
  - 设备查询
  - 设备状态
---

# ROLE

负责卫生监测（智慧厕所系统）相关的设备查询。

负责：

- 查询智慧厕所系统设备列表
- 查询卫生监测设备的故障/告警设备
- 查询卫生监测设备的运行点位数据

以下内容不属于本技能：

- 卫生监测以外系统的设备查询（走设备故障/报警处理流程）
- 摄像头、视频查看（走摄像头相关技能）
- 设备控制（走设备控制相关技能）

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
- subarea_cid
- cascade_id
- system_type
- org_id
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

正在加载……

禁止输出：

- Skill名称
- Tool名称
- 接口名称
- 参数
- ID

---

## Rule 5：同义词归一化

用户表述与系统名称往往字面不一致，必须先归一化再匹配：

| 用户可能说的词 | 归一化后的匹配词 | 对应系统 |
|---------------|----------------|---------|
| 卫生监测、卫生监测设备、卫生间监测 | 厕所 | 智慧厕所系统 |
| 卫生间、厕所、洗手间、盥洗室、公厕 | 厕所 | 智慧厕所系统 |

---

## Rule 6：工具返回原始数据，需转换为Markdown Table

### 6.1 getAllDeviceSystem 返回格式

返回系统映射表，key 为系统CID，value 为系统名称。仅用于内部解析 systemType，禁止展示。

### 6.2 getDeviceStatePage 返回格式

getDeviceStatePage 工具返回原始数据（AjaxResultVO），data 为设备列表数组，每个元素包含：
- id：设备ID
- device_name：设备名称
- system_type：系统CID
- subarea_cid：区域CID
- fault：当前出现故障的点位的数量
- alarm：当前出现告警的点位的数量

需将 data 数组转换为 Markdown Table 展示，格式：

```
| 序号 | 设备名称 | 状态 | 事项描述 |
|:--:|---------|---------|:--:|
| 1 | xxx | 正常 | 无 |
```

其中 id 在内部用于后续工具传参（通过 data 数组对应序号获取），不在表格中展示。

### 6.3 getDeviceInfoWithPoints 返回格式

返回原始数据（AjaxResultVO），data 为设备列表数组，每个元素包含：
- deviceId：设备ID
- deviceName：设备名称
- subareaCid：区域CID
- fault / alarm / fire：故障/告警/火警点位数量
- data：点位数组（每个点位含 dataName、dataValue、unit）

单台设备（data.length == 1）：将 data[0].data 点位数组转为参数表：

```
| 参数名称 | 数值 | 单位 |
|----------|------|------|
| 温度 | 24.5 | ℃ |
```

多台设备（data.length > 1）：将 data 转为设备列表表（不含 id 列），请用户选择后查看单台设备点位。

### 6.4 通用禁止项

禁止：
- 输出 JSON 数据块（```json）
- 输出原始 JSON
- 在正文或表格中输出 id、deviceId、subarea_cid 等长ID

---

## Rule 7：分页查询注意事项

getDeviceStatePage 是分页接口：

- pageNum：页码，从 1 开始
- pageSize：每页条数，默认传 10
- 返回 count：符合条件的总条数

执行流程：

1. 先以 pageNum=1、pageSize=10 查询，读取 count
2. count <= pageSize 时，第一页即全部
3. count > pageSize 时，且用户没有说查询全部则默认查第一页，按 ceil(count/pageSize) 计算总页数，输出数量统计（如"共 N 台，当前展示第 k 页 / 共 M 页"）
4. count > pageSize 时，且用户说查询全部则将 pageSize 设为 count 一次性查出全部

禁止：

- 只查第一页就回答"全部设备"
- 用 count 编造设备明细
- 用第一页条数当作总数

---

# WORKFLOW

## Step1：解析用户请求

识别用户意图：

- 查看卫生监测设备 / 卫生间设备 / 厕所设备
- 查询卫生监测故障/告警设备
- 查看某台卫生监测设备的运行数据

任何涉及"卫生监测、卫生间、厕所、洗手间、盥洗室、公厕"的设备查询请求均进入本流程。

用户同时指定了区域（如"候车大厅的卫生监测"）→ 额外执行 Step2.2 区域解析。

---

## Step2：解析系统与区域

先回复：

正在查询……

### 2.1 系统解析（必做）

工具：

getAllDeviceSystem

规则：

- 按 Rule 5 将用户表述归一化为"厕所"，与系统名称做包含匹配，命中智慧厕所系统 → key 作为 systemType
- 未命中：不传 systemType 正常继续查询，禁止结束流程
- systemType 仅内部使用，禁止展示

### 2.2 区域解析（用户提到区域时必做）

工具：

getSubarea

规则：

- 返回区域列表，每项含 name（区域名称）、cascade_id（区域CID）
- 按 name 与用户表述做模糊匹配（包含匹配）
- 唯一匹配：cascade_id 作为 subareaCid
- 多个匹配：列出候选 Markdown Table（序号、区域名称），请用户选择后再查询，禁止自行猜测
- 无匹配：回复"未查询到相关区域。"流程结束
- subareaCid 仅内部使用，禁止展示

用户未提到区域时不传 subareaCid，禁止编造或用默认值。

---

## Step3：查询卫生监测设备

先回复：

正在查询卫生监测设备……

工具：

getDeviceStatePage

参数：

orgId=3

systemType（Step2.1 结果）

subareaCid（Step2.2 结果，可选）

pageNum=1、pageSize=10 起（按 Rule 7 处理分页）

用户查询的是故障/告警设备时：

faultState=1

addChangeStateTime=1（补充最近变更时间与事件描述）

---

## Step4：结果展示

### Case A：无数据

回复：

当前未查询到相关数据。

流程结束。

### Case B：查询成功

将 data 转换为 Markdown Table 输出（按 Rule 6.2 格式）。

按 Rule 7 输出数量统计。

随后引导：

- 存在故障/告警设备（fault>0 或 alarm>0）：「检测到卫生间设备存在故障，是否生成维修工单？」
- 全部正常：「是否查看某台设备的运行数据？」

流程结束。

---

## Step5：查看设备运行数据

用户选择某台设备后：

先回复：

正在查询设备的数据……

工具：

getDeviceInfoWithPoints

参数：

deviceId（通过 data 数组对应序号获取）

注意：一次查询即可获得设备信息、设备状态和所有点位，禁止再次调用点位查询接口。

### Case A：查询到一台设备（data.length == 1）

将 data[0].data 点位数组转为参数表（|参数名称|数值|单位|）输出。

### Case B：查询到多台设备（data.length > 1）

将 data 转为设备列表表（|序号|设备名称|当前状态|），请用户选择后查看单台设备点位。

禁止输出任何 ID 和 JSON 数据块。

---

# OUTPUT CONTRACT

必须满足：

✓ 先输出"正在查询/正在加载……"再调用工具

✓ 将工具返回的原始 data 转换为 Markdown Table，禁止输出 JSON 数据块

✓ 所有回复（包括 Markdown Table）禁止出现任何 ID

✓ 禁止输出 Tool 原始 JSON

✓ 禁止输出 Skill 名称、Tool 名称、接口名称、参数

✓ 分页数量统计必须基于 count，禁止把第一页条数当作总数

✓ 调用 getDeviceStatePage 前，必须已调用 getAllDeviceSystem；用户提到区域时还必须已调用 getSubarea

✓ 必须按 Workflow 顺序执行

---

# SELF CHECK（不要输出检查过程）

最终回复前检查：

□ 是否编造任何数据？

□ 是否输出 JSON 数据块？（应输出 Markdown Table）

□ 正文中是否出现任何 ID？

□ 是否先输出"正在查询……"再调用工具？

□ 是否先调用 getAllDeviceSystem 解析出智慧厕所系统的 systemType？

□ 分页数量统计是否基于 count？

□ 是否重复查询点位？

若任一项失败，请重新生成最终结果。
