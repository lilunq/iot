# 统一业务实体解析中心

本目录用于承载统一业务实体解析中心模块。Java 包路径为 `com.sysware.xian.module.entity`，具体实现遵循 `docs/西安东站统一业务实体解析中心技术实施规范.md`。

## 模块边界

- `common`：实体类型、统一模型与文本标准化。
- `config`：Elasticsearch 及模块配置。
- `init`：索引初始化、别名管理、数据源适配和同步编排。
- `resolver`：查询构建、候选排序、消歧及 REST/MCP 接口。

## 关键约束

- 区域稳定业务标识使用 `cascade_cid`。
- 系统稳定业务标识使用 `element_key`。
- 设备和摄像头使用独立 Elasticsearch 索引。
- 摄像头不做区域解析和区域过滤。
- 拼音由 Elasticsearch Pinyin Analyzer 处理，Java 层不生成拼音或首字母。
