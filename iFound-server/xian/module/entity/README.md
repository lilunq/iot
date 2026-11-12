# 统一业务实体解析中心

本目录是统一业务实体解析中心的模块入口。后端 Java 包路径：
`com.sysware.xian.module.entity`。实现依据仓库文档
`docs/西安东站统一业务实体解析中心技术实施规范.md`。

## 已提交的基础能力

- `common`：五类实体枚举、解析请求/响应/候选模型、Unicode 文本规范化。
- `config`：可选 Elasticsearch REST 连接，默认关闭，不影响现有服务启动。
- `repository`：ES 查询与 Bulk 幂等写入访问边界。
- `init`：五类实体索引 Mapping 和逻辑 Alias 初始化。
- `init/alias`：MyBatis-Plus 别名表实体、Mapper、别名查询和维护接口。
- `resolver`：类型路由、设备过滤、精确标识/别名、IK 文本与拼音字段召回、候选置信度消歧。
- `src/main/resources/db/entity-center`：别名表 SQL 初始化脚本。

## 关键业务约束

1. AREA 的稳定业务标识为 `cascade_cid`。
2. SYSTEM 的稳定业务标识为 `element_key`。
3. DEVICE_TYPE、DEVICE、CAMERA 的稳定业务标识均为源系统 `id`。
4. DEVICE 和 CAMERA 使用独立索引。
5. CAMERA 没有区域归属，不做区域解析或区域过滤。
6. DEVICE 的区域过滤字段为 `subarea_cid`，按 `cascade_cid` 前缀匹配区域及其下级区域；系统过滤字段为 `system_key`，调用方传入的系统 ID 必须是 `element_key`。
7. Java 只做 Unicode/空白/英文大小写规范化，不生成拼音和首字母；拼音召回由 ES Pinyin Analyzer 处理。

## 配置

在 Nacos 或部署环境配置，模块默认关闭：

```yaml
entity-center:
  elasticsearch:
    enabled: true
    uri: http://127.0.0.1:9200
    username: ${ENTITY_ES_USERNAME:}
    password: ${ENTITY_ES_PASSWORD:}
    admin-api-enabled: false
    sync-api-enabled: false
  resolver:
    default-limit: 5
    max-limit: 20
    minimum-score: 3.0
    minimum-score-gap: 1.0
  alias-api-enabled: false
```

Elasticsearch 9.5.6 需要预先安装与 ES 版本匹配的 IK 和 Pinyin 插件。
首次创建索引前先验证插件：

- `ik_max_word`、`ik_smart` 可用。
- `pinyin` tokenizer 可用。
- 服务使用 ES 逻辑 Alias：`station_areas`、`station_systems`、
  `station_device_types`、`station_devices`、`station_cameras`。
- 首次物理索引名使用逻辑名加 `_v1` 后缀。

## API

启用索引管理接口后：

`POST /api/entity-center/admin/indexes/initialize`

启用解析模块后：

`POST /api/entity-center/resolve`

请求示例：

```json
{
  "query": "2号冷水机组",
  "entityType": "DEVICE",
  "areaId": "区域cascade_cid",
  "systemKey": "系统element_key",
  "deviceTypeId": "设备分类id",
  "limit": 5
}
```

响应状态：`FOUND`（唯一高置信度）、`AMBIGUOUS`（候选需要消歧）、
`NOT_FOUND`（无候选）。

启用别名维护接口后：

- `GET /api/entity-center/aliases?entityType=DEVICE&dataId=设备id`
- `POST /api/entity-center/aliases`

别名 SQL 位于 `src/main/resources/db/entity-center/V1__create_biz_entity_alias.sql`。
需确认目标业务库后手工执行，不会随应用启动自动执行。别名接口启用前应通过
现有网关/权限体系限制访问。

## 同步接口

首版已接入当前工程中能够确认的数据源：

- 区域：`AdminClient.getSubareaList`，源字段 `cascade_id` 映射为实体中心字段 `cascade_cid`，区域名称取 `name`；通过 `parent_id` 关联父区域并补充父级 `cascade_cid`。
- 系统：`AdminClient.getEnumKeyValues("device_system_type")`，接口直接返回列表，以 `element_key` 作为稳定 ID，`element_value` 作为名称。
- 设备分类：`ScadaClient.listDeviceTypes` 对应 `/base/device/type/list`，以分类记录 `id` 作为稳定 ID，同时保存 `cascade_id` 层级编码。
- 设备：`ScadaClient.listPage` 对应 `/base/device/listPage` 分页接口，以设备 `id` 作为稳定 ID；设备源字段 `device_type` 保存分类 `cascade_id`，同步时通过分类列表映射为统一实体分类 `id`。
- 摄像头：`VideoClient.listPage` 对应 `/camera/listPage` 分页接口，以摄像头 `id` 作为稳定 ID；不写入区域字段，也不传入区域查询条件。

同步 API 默认关闭，启用前应先执行 SQL、初始化索引，并由现有网关/权限体系限制访问：

- `POST /api/entity-center/admin/sync/available`
- `POST /api/entity-center/admin/sync/areas`
- `POST /api/entity-center/admin/sync/systems`
- `POST /api/entity-center/admin/sync/device-types`
- `POST /api/entity-center/admin/sync/devices`
- `POST /api/entity-center/admin/sync/cameras`

同步采用稳定业务 ID 作为 ES 文档 `_id`，当前行为是幂等 Bulk upsert；
不会自动删除源系统已不存在的旧文档，也不会在同步失败时切换索引 Alias。

## MCP 与 Vue3

- MCP Tool：`resolveBusinessEntity`，由 `mcp-server` 注册，转发到 Nacos 服务
  `xian-ai` 的 `/api/entity-center/resolve`。当解析状态为 `AMBIGUOUS` 时，
  Agent 应展示候选并向用户澄清，不得自行猜测。
- Vue 页面：`/config/entity-center`，已加入配置管理导航，包含解析测试、已接入数据源同步和别名维护入口。

## 当前开发边界

当前提交已包含可选 ES 基础设施、五类索引 Mapping、别名持久化、解析 API，
区域/系统/设备分类/设备/摄像头的首版手动分页同步、MCP Tool 和 Vue3 管理页面。
尚未完成设备分类数据源适配、增量同步游标/事件监听、同步任务审计与失败重试、
索引重建和安全 Alias 切换，以及完整的端到端联调。上述功能需在确认真实源系统字段、
同步频率和权限边界后继续实现。
