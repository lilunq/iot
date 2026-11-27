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

## Rule 6：区域/系统参数必须解析后再查询

getDeviceStatePage 的 subareaCid、systemType 必须是 CID，禁止直接把用户说的名称当参数传。

### 6.1 用户指定区域（楼层、区域、候车厅、站台、房间等）

先调用 getSubarea：

- 返回区域列表，每项含 name（区域名称）、cascade_id（区域CID）
- 按 name 与用户表述做模糊匹配（包含匹配）
- 唯一匹配：cascade_id 作为 subareaCid
- 多个匹配：列出候选 Markdown Table（序号、区域名称），请用户选择后再查询，禁止自行猜测
- 无匹配：回复"未查询到相关区域。"流程结束

### 6.2 系统解析（每次查询设备都必须执行）

无论用户是否明确提到"系统"，只要调用 getDeviceStatePage，就必须先调用 getAllDeviceSystem：

- 返回系统映射表，key 为系统CID，value 为系统名称
- 用用户表述中的设备类别关键词（如电梯、扶梯、空调、风机、水泵、照明、给排水、消防、通风、电力监控、水表、雨水回收、卸污、上水、门禁、UPS、给水所、冷热源等）与 value 做模糊匹配（包含匹配）
- **同义词对照表**（用户表述往往与系统名称字面不一致，必须先归一化再匹配，禁止因字面不同就直接判"无匹配"）：

| 用户可能说的词 | 归一化后的匹配词 | 对应系统 |
|---------------|----------------|---------|
| 电梯、直梯 | 电梯 | 电梯系统 |
| 扶梯、扶手电梯、自动扶梯 | 扶梯 | 扶梯系统 |
| 空调、中央空调 | 空调 | 空调系统 |
| 风机、排风机、送风机 | 风机 | 风机系统 |
| 水泵、给排水、排水 | 给排水 | 给排水系统 |
| 照明、灯光 | 照明 | 照明系统 |
| 消防 | 消防 | 消防系统 |
| 通风 | 通风 | 通风系统 |
| 电力监控、电力 | 电力监控 | 电力监控系统 |
| 水表 | 水表 | 水表 |
| 雨水回收 | 雨水回收 | 雨水回收系统 |
| 卸污、客车卸污 | 卸污 | 客车卸污系统 |
| 环境监测 | 环境监测 | 环境监测 |
| 消防监控 | 消防监控 | 消防监控系统 |
| 上水、客车上水 | 上水 | 客车上水系统 |
| 门禁 | 门禁 | 门禁 |
| UPS | UPS | UPS系统 |
| 给水所 | 给水 | 给水系统（给水所） |
| 冷热源、冷源、热源 | 冷热源 | 冷热源系统 |

- 匹配规则：先查上表将用户词归一化，再与系统名称做包含匹配；用户词与系统名称能直接包含匹配的也直接命中。上表未覆盖的词按语义相近原则匹配
- 卫生监测、卫生间、厕所、洗手间、盥洗室、公厕类请求不属于本技能，由 hygiene-monitoring 技能处理
- 唯一匹配：key 作为 systemType
- 多个匹配：列出候选 Markdown Table（序号、系统名称），请用户选择后继续
- 无匹配：不传 systemType，正常继续查询，禁止因无匹配而结束流程

歧义消解规则：

- "给水所"匹配给水系统（给水所）；"给排水、排水泵"匹配给排水系统，两者不得混淆
- "消防"可能同时命中"消防系统"与"消防监控系统"：按包含匹配得到多个候选时，列出候选让用户选择，禁止自行猜测
- 环境监测类歧义：查询到达层/出发层/候车层的温湿度、CO₂等环境指标数据走 env-monitoring 技能；查询环境监测系统下的设备台账/状态（如设备列表、故障）才走本技能

示例："查看候车层的电梯" → 先取系统列表，用"电梯"匹配到电梯/扶梯系统 → 传 systemType 再查询设备。

示例："查看给水所的泵" → 先取系统列表，用"给水所"匹配到给水系统（给水所）→ 传 systemType 再查询设备（"泵"可作为设备名称关键词 deviceName 或不做额外过滤，以查询结果为准）。

### 6.3 注意

- 每次设备查询（getDeviceStatePage）前都必须调用 getAllDeviceSystem，不允许跳过
- 区域与系统都解析成功时，两个结果必须同时传入
- 用户明确指定区域时才调用 getSubarea；未提及区域则不传 subareaCid，禁止编造或用默认值
- 解析出的 CID 仅内部使用，禁止展示给用户

---

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

凡是要调用 getDeviceStatePage（无论是查故障设备还是查普通设备），必须按以下固定顺序调用工具，一步都不能少：

```
第1步（必做）：getAllDeviceSystem          ← 永远第一个调用
第2步（用户提到区域时必做）：getSubarea
第3步：getDeviceStatePage
```

违规判定（满足任意一条即为违规，必须重新按顺序执行）：

- 调用 getDeviceStatePage 时，本次对话中尚未调用过 getAllDeviceSystem
- 用户提到了区域/楼层/大厅/站台等，但调用 getDeviceStatePage 前未调用 getSubarea

第1步的目的：把用户话里的设备类别词（电梯、扶梯、空调、风机、水泵、照明、给排水、消防、通风、排风机、电力监控、水表、雨水回收、卸污、上水、门禁、UPS、给水所、冷热源等）经 Rule 6.2 同义词对照表归一化后，与系统名称匹配出 systemType，再传给 getDeviceStatePage。

示例："查看候车大厅的电梯"

1. 先调用 getAllDeviceSystem → 用"电梯"匹配到电梯系统 → 得到 systemType
2. 再调用 getSubarea → 用"候车大厅"匹配到区域 → 得到 subareaCid
3. 最后调用 getDeviceStatePage（带 systemType + subareaCid）

卫生监测、卫生间、厕所、洗手间类请求不适用本流程，由 hygiene-monitoring 技能负责。

历史设备事项查询（如"昨日设备故障情况""上周告警记录""最近24小时设备故障"等带时间范围的事项/记录查询）不适用本流程，由 device-event-history 技能负责。本技能只查询当前实时设备状态。

工单查询类请求（查询工单列表、工单详情、工单进度、工单处理状态、历史工单、设备维修记录）不适用本流程，由 repair-order-query 技能负责。本技能只负责创建工单。

禁止跳过第1步直接调用 getDeviceStatePage，即使用户没有提到任何系统名称。

---

## Step1：解析用户请求

识别用户意图：

- 查询故障设备
- 查询某区域故障设备
- 查看设备运行数据
- 创建维修工单

分流规则：

- 查询故障/报警设备 → Step2/Step3
- 查看某区域/某类设备（如"查看候车大厅的电梯"）→ 先按上方"查询前置门槛"完成 getAllDeviceSystem/getSubarea 解析，再用 getDeviceStatePage（不传 faultState）查询设备列表，定位目标设备后进入 Step5
- 用户直接指定已列出的某台设备查看运行数据 → 直接进入 Step5
- 创建维修工单 → Step6

注意：无论走哪条分流，只要最终要调用 getDeviceStatePage，都必须先通过上方"查询前置门槛"。

---

## Step2：解析区域与系统（系统必做，区域可选）

本步骤即"查询前置门槛"的落地，调用顺序必须是：getAllDeviceSystem → getSubarea（可选）→ getDeviceStatePage。

### 2.1 区域解析

用户提到楼层、区域、候车厅、站台、房间等内容时：

工具：

getSubarea

规则：

按 Rule 6.1 将区域名称模糊匹配为 cascade_id，作为后续查询的 subareaCid。

### 2.2 系统解析（必做）

无论用户是否提到系统名称，都必须执行：

工具：

getAllDeviceSystem

规则：

按 Rule 6.2 用设备类别关键词做匹配：能匹配到系统则得到 systemType，匹配不到则不传 systemType 并正常继续查询。

禁止跳过本步骤直接查询设备。

### 2.3 注意

- 匹配到多个时，先让用户选择，禁止自行猜测
- 区域无匹配时回复"未查询到相关区域。"流程结束；系统无匹配时正常继续查询
- subareaCid、systemType 仅供下一步工具使用，禁止展示给用户

---

## Step3：查询故障设备

先回复：

正在查询故障设备……

工具：

getDeviceStatePage

调用前置条件：Step2 已完成 getAllDeviceSystem 调用（必做）及 getSubarea 调用（用户提到区域时）。

参数：

orgId=3

pageSize=10

pageNum=1 起（按 Rule 7 处理分页）

faultState=1

subareaCid（Step2 区域解析结果，可选）

systemType（Step2 系统解析结果，可选）

addChangeStateTime=1（补充故障设备的最近变更时间与事件描述）

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

✓ 调用 getDeviceStatePage 前，本次对话必须已调用 getAllDeviceSystem；用户提到区域时还必须已调用 getSubarea（硬性门槛，违反即重新执行）

✓ 分页数量统计必须基于 count，禁止把第一页条数当作总数

✓ 必须按 Workflow 顺序执行

---

# SELF CHECK（不要输出检查过程）

最终回复前检查：

□ 是否存在编造数据？

□ 是否输出 JSON 数据块？（应输出 Markdown Table）

□ 回复中是否出现任何 ID？

□ 是否调用了多余接口？

□ 调用 getDeviceStatePage 之前，是否已经调用过 getAllDeviceSystem？（未调用即违规，必须重新按门槛顺序执行）

□ 用户提到区域时，调用 getDeviceStatePage 之前是否已经调用过 getSubarea？

□ 分页数量统计是否基于 count？（禁止用第一页条数当总数）

□ 是否重复查询点位？

□ 是否先输出"正在查询/正在处理/正在生成……"再调用工具？

□ 是否违反 Workflow 顺序？

若任一项失败，请重新生成最终结果。