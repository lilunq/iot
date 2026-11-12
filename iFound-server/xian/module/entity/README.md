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
6. DEVICE 的区域过滤字段为 `subarea_cid`，系统过滤字段为 `system_key`；调用方传入的区域 ID 必须是 `cascade_cid`，系统 ID 必须是 `element_key`。
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

## 当前开发边界

当前提交完成了可选的 ES 基础设施、五类索引 Mapping、别名持久化和解析 API。
尚未完成源系统分页适配、五类实体全量/增量/单实体同步、同步任务审计与失败重试、
MCP Tool 注册以及 Vue3 管理页面。摄像头和设备源接口已在现有工程中定位；
系统枚举和设备分类数据源还需确认其真实接口与字段映射，不能以猜测字段上线。
