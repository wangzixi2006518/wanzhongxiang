# 万众享平台 · Java 后端与 AI 运营助手

面向商户运营与用户消费场景的 Java 后端项目，涵盖商品与组合商品管理、购物车、订单处理和运营统计。在业务系统上接入 Spring AI，提供流式多轮对话、经营数据查询，以及带引用来源和资料版本管理的平台规则问答。

本仓库保存**后端源码**。管理端前端、数据库初始化脚本、规则资料与 Nginx 配置目前位于独立工作区，需要另行准备；克隆本仓库不等于获得完整可直接启动的系统。下文按当前代码说明功能、运行前提与实现边界。

## 核心功能

| 模块 | 当前实现 |
| --- | --- |
| 业务管理 | 管理员 JWT 认证，商品、组合商品、购物车、订单状态处理、运营统计与报表 |
| 订单事务 | Spring 声明式事务统一控制订单主表、明细写入及购物车清理 |
| 经营区间对比 | SQL 聚合已完成订单，Java 使用 BigDecimal 计算客单价、营业额差值与变化率，处理日期边界和零基数 |
| AI 流式对话 | Spring AI ChatClient + SSE，支持生成终态、取消、历史恢复与连续追问 |
| 会话管理 | MySQL 保存会话和消息，按认证管理员校验归属；从最近完整成功轮次恢复 ChatMemory |
| 经营数据工具 | Tool Calling 查询昨天或指定过去日期的已完成订单数和营业额，复用后端统计服务 |
| RAG 规则问答 | 按章节与 Token 分片，Embedding 后写入 Milvus，按生效版本检索并核对本轮引用 |
| 资料版本发布 | MySQL 管理 PENDING / READY 与当前生效版本；先写索引，再事务发布，失败保留旧生效版本 |
| 固定分析流程 | 后端依次读取订单事实、检索规则、调用模型和核对来源，分析读取时刻是否满足开始派送前提 |

## 技术栈

| 层次 | 技术与配置 |
| --- | --- |
| 后端 | Java 17、Spring Boot **3.5.16**、Maven 多模块 |
| 数据访问与认证 | MyBatis、MySQL、Redis、JWT |
| AI 应用 | Spring AI **1.1.8**、ChatClient、MessageChatMemoryAdvisor、Tool Calling、Reactor Flux |
| 聊天模型 | DeepSeek，通过 `DEEPSEEK_API_KEY` 配置 |
| Embedding | 阿里云百炼 `text-embedding-v4`，OpenAI 兼容协议，当前 **1024 维** |
| 向量存储 | Milvus；本地验收使用 **2.5.8**，集合 `wzx_rule_chunks`，`COSINE` 度量、`FLAT` 索引 |
| 接口与通信 | Springdoc OpenAPI、SSE、WebSocket |
| 独立配套管理端 | Vue 2、TypeScript、Vue Router、Vuex、Element UI、Axios、ECharts、Nginx |

MySQL 保存订单、会话、消息和资料版本等业务事实；Milvus 保存规则片段向量、正文及元数据。聊天和 Embedding 使用各自的模型与 API Key。

## 关键实现

### 1. 订单事务与经营区间对比

下单方法将订单主表写入、明细批量写入和当前用户购物车清理放入同一数据库事务。该事务范围不包含远端支付或向量库，也没有在这条下单流程中额外实现商品库存扣减。

经营对比接口接收当前周期与基准周期的四个日期：

- 日期非空、开始不晚于结束，两个周期结束日期均早于上海时区的今天。
- 每段包含的自然日数量不超过 31 天，两段天数相同。
- 基准周期完全早于当前周期，不重叠。
- SQL 以 `status = 5` 的已完成订单为范围，按 `order_time` 使用 `>= 开始日零点`、`< 结束日次日零点` 筛选。
- `COUNT(*)` 统计笔数，`COALESCE(SUM(amount), 0)` 统计营业额；Java 计算客单价、差值及变化率。
- 金额和客单价保留两位小数，变化率保留四位，使用 `HALF_UP`；基准营业额为零时变化率返回 `null`，订单数为零时客单价为 `0.00`。

变化率返回比例，例如 `0.2000` 表示 `20%`。查询失败不作为零营业额返回。当前口径是“按下单时间筛选的已完成订单”，不等同于按收款时间统计财务流水。

### 2. 流式会话、历史与工具调用

```text
管理员问题 + 可选 conversationId
 → JWT 验证身份，按管理员校验会话归属
 → 短事务锁定会话，检查是否已有 GENERATING 回答
 → 写入用户消息与助手占位消息
 → 从 MySQL 读取完整成功轮次，恢复 ChatMemory
 → ChatClient 注册经营统计工具与规则检索工具
 → 模型按问题提出工具调用，Java 服务执行真实查询或检索
 → SSE 增量输出
 → 核对本轮来源，保存完整回答与 COMPLETED 状态
```

- 正常事件顺序：`meta → delta... → sources → done`；异常时返回 SSE `error`。
- 助手状态从 `GENERATING` 进入 `COMPLETED`、`FAILED` 或 `CANCELLED`。终态更新带 `status = 'GENERATING'` 条件，限制重复覆盖。
- 历史页面保留消息与终态；模型上下文只读取有成功助手回答的完整轮次，以 `turn_id` 关联并按 `seq` 排序。
- 每次恢复最近最多 **8 条历史消息**，普通一问一答约为 4 轮；`MessageWindowChatMemory` 配置 `maxMessages(10)`，条数不等于轮数或 Token 数。
- 同一会话存在生成中回答时返回 HTTP 409。数据库行锁只覆盖检查与占位的短事务，不持续锁住整个模型生成过程。
- 流式链路使用 30 秒停滞超时，约束首片段及相邻片段等待；持续输出时整轮时长可以超过 30 秒。
- 取消连接时保存已生成内容快照和取消状态；本地取消不代表远端立即停止生成或不再计费。

经营工具由 `BusinessStatisticsTools` 提供：

| 工具 | 用途 |
| --- | --- |
| `query_yesterday_turnover` | 查询昨天已完成订单的笔数和营业额 |
| `query_turnover_by_date` | 查询明确指定的过去完整日期，校验日期格式、真实性并拒绝今天及未来日期 |
| `search_platform_rules` | 检索本轮平台规则正文与来源，供主聊天生成回答 |

日期工具复用 `WorkspaceService`。经营区间对比目前由 `ReportService` 的独立 HTTP 接口提供，**尚未直接注册成区间对比工具**。

### 3. RAG 文档处理与来源核对

```text
规则资料
 → TextReader 读取，附加文档、版本和来源元数据
 → 按独占行的 “## ” 识别章节
 → TokenTextSplitter 对章节切片，保留章节标题
 → 为最终片段补充 chunk_id、章节信息和稳定 UUID
 → Embedding + Milvus

本轮规则问题
 → 从 MySQL 查询启用文档及其 READY 生效版本
 → 按 (document_id AND version) 配对条件过滤
 → 问题 Embedding、Milvus 相似度检索
 → 组织片段正文与来源信息，交给模型
 → 提取回答中的来源编号，只在本轮真实候选内核对
```

当前切片目标约 400 Token，检索使用 `topK=2`、`similarityThreshold=0.5`。阈值是候选筛选门槛，不是答案正确概率。

主聊天已经接入规则检索工具；独立规则窗口仍可调用 `/admin/ai/rag/answer`。本轮检索是否发生、取回哪些候选，由请求级 `RuleRetrievalContext` 记录；历史回答中的旧来源不能直接作为本轮引用依据。没有候选时返回资料不足；向量服务或模型调用异常按技术失败处理。

来源核对会拒绝本轮不存在的引用，并从真实 `Document` 组装文档名、版本、片段编号和正文。它验证来源对应关系，**不保证模型对规则的每一句解释都正确**。

### 4. 资料版本发布与索引持久化

```text
登记新资料版本（PENDING）
 → 查询待导入 spec
 → 读取并切分资料
 → 按该 document_id + version 清理待导入版本残留片段
 → 向 Milvus 写入全部新片段
 → MySQL 事务：版本改 READY + 更新 active_version
 → 后续查询按新生效版本检索
```

- 查询要求文档已启用、版本为 READY，且与目录当前生效版本一致；多个文档以成对条件 OR 组合。
- 导入失败时不发布新版本，已有生效版本继续可用；首次导入若没有旧版，则仍需明确资料未就绪。
- `publishVersion` 的事务只保护 MySQL 两项更新，不能回滚 Milvus。写入失败或发布失败可能留下未生效的新片段，重试时清理目标 PENDING 版本后再导入。
- 稳定片段 UUID 不等于自动 upsert。当前版本的 Milvus 适配器使用插入路径，项目通过状态、版本清理与发布顺序组织重试。
- `synchronized` 导入只约束同一 JVM，未实现多实例分布式导入互斥和自动补偿。
- 资料索引已持久化到 Milvus，**应用或 Milvus 服务重启后可复用原索引，不需要因为重启重新导入**。新的问题仍需要查询 Embedding，生成回答仍需要聊天模型。

本地验收曾对 4 份文档、18 个片段做过目录、版本过滤和重启读回验证；这些是开发验收数据，不代表生产规模、完整准确率评估或备份恢复保障。

## 项目结构与代码入口

```text
wanzhongxiang/
├── platform-common/                # 常量、上下文、异常与配置属性
├── platform-pojo/                  # Entity、DTO、VO
├── platform-server/
│   └── src/main/
│       ├── java/com/wanzhongxiang/
│       │   ├── controller/         # 管理端与用户端入口
│       │   ├── service/            # 业务、统计与 AI 会话服务
│       │   ├── mapper/             # MyBatis 数据访问
│       │   ├── tool/               # 经营统计与规则检索工具
│       │   ├── rag/                # 分片、目录、索引、检索与引用核对
│       │   └── workflow/           # 订单派送分析固定流程
│       └── resources/              # 配置、Mapper XML、报表模板
├── pom.xml
└── README.md
```

| 实现 | 源码 |
| --- | --- |
| 订单提交事务 | [OrderServiceImpl](platform-server/src/main/java/com/wanzhongxiang/service/impl/OrderServiceImpl.java) |
| 经营区间对比 | [ReportServiceImpl](platform-server/src/main/java/com/wanzhongxiang/service/impl/ReportServiceImpl.java)、[OrderMapper.xml](platform-server/src/main/resources/mapper/OrderMapper.xml) |
| SSE 与消息生命周期 | [AiController](platform-server/src/main/java/com/wanzhongxiang/controller/admin/AiController.java)、[AiMessageServiceImpl](platform-server/src/main/java/com/wanzhongxiang/service/impl/AiMessageServiceImpl.java) |
| 记忆恢复与工具注册 | [AiChatServiceImpl](platform-server/src/main/java/com/wanzhongxiang/service/impl/AiChatServiceImpl.java) |
| 经营工具与规则工具 | [BusinessStatisticsTools](platform-server/src/main/java/com/wanzhongxiang/tool/BusinessStatisticsTools.java)、[RuleRetrievalTools](platform-server/src/main/java/com/wanzhongxiang/tool/RuleRetrievalTools.java) |
| 文档处理 | [RagDocumentLoader](platform-server/src/main/java/com/wanzhongxiang/rag/RagDocumentLoader.java) |
| 索引导入与版本发布 | [RagDocumentImportService](platform-server/src/main/java/com/wanzhongxiang/rag/RagDocumentImportService.java)、[RagIndexService](platform-server/src/main/java/com/wanzhongxiang/rag/RagIndexService.java)、[RagDocumentCatalogService](platform-server/src/main/java/com/wanzhongxiang/rag/RagDocumentCatalogService.java) |
| 检索与来源 | [RagRetrievalService](platform-server/src/main/java/com/wanzhongxiang/rag/RagRetrievalService.java)、[RagAnswerService](platform-server/src/main/java/com/wanzhongxiang/rag/RagAnswerService.java) |
| 固定分析流程 | [OrderDispatchWorkflowService](platform-server/src/main/java/com/wanzhongxiang/workflow/OrderDispatchWorkflowService.java) |

## 管理端接口

除登录外，以下业务入口使用管理端 JWT，请求头为 `token`。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/admin/employee/login` | 管理员登录，取得 Token |
| POST | `/admin/ai/chat` | 同步单次问答；不使用流式路径中的 Memory 与工具注册 |
| POST | `/admin/ai/chat/stream` | SSE 多轮对话、经营工具和规则检索 |
| GET | `/admin/ai/health` | 实际调用一次聊天模型，返回模型状态与目录级 ragReady |
| POST | `/admin/ai/conversations` | 新建会话 |
| GET | `/admin/ai/conversations` | 当前管理员会话列表 |
| PUT | `/admin/ai/conversations/{conversationId}/title` | 修改所属会话标题 |
| GET | `/admin/ai/conversations/{conversationId}/messages` | 所属会话历史 |
| DELETE | `/admin/ai/conversations/{conversationId}` | 删除所属会话并清理 Memory |
| POST | `/admin/ai/rag/versions/import` | 导入已登记的 PENDING 版本并发布，返回片段数 |
| GET | `/admin/ai/rag/ready` | 查询是否存在生效目录 |
| POST | `/admin/ai/rag/answer` | 独立规则问答与经核对的来源 |
| GET | `/admin/ai/orders/{orderId}/dispatch-analysis` | 读取订单事实与规则，生成派送前提分析 |
| GET | `/admin/report/businessComparison` | 当前周期与基准周期的经营数据对比 |

流式请求示例；首次可不传 `conversationId`，后续使用服务端返回的真实 ID：

```json
{
  "message": "昨天已完成订单多少笔，营业额多少元？",
  "conversationId": "<已有会话ID>"
}
```

区间查询参数示例：

```text
/admin/report/businessComparison?currentBegin=2026-10-05&currentEnd=2026-10-11&baselineBegin=2026-09-28&baselineEnd=2026-10-04
```

日期示例需按实际当前日期调整。核心返回字段包括两个周期的 `ValidOrderCount`、`Turnover`、`UnitPrice`，以及 `turnoverDifference`、`turnoverChangeRate`；订单数以已完成订单为口径。

## 本地运行

### 1. 环境与外部资源

准备 JDK 17、Maven、MySQL、Redis 和可连接的 Milvus；完整页面还需要独立管理端与 Nginx。默认后端端口 `8080`，Milvus 客户端端口默认 `19530`。

```bash
git clone https://github.com/wangzixi2006518/wanzhongxiang.git
cd wanzhongxiang
```

业务初始化脚本 `wanzhongxiang-platform.sql`、AI 脚本 `wanzhongxiang-ai.sql` 和规则 TXT 当前未纳入本仓库，需要从配套工作区准备，不能省略建表直接运行。AI 表至少需要：

| 表 | 关键字段与约束 |
| --- | --- |
| `ai_conversation` | `id`、`employee_id`、`title`、创建/更新时间 |
| `ai_message` | `id`、`conversation_id`、`turn_id`、`role`、`content`、`status`、创建/更新时间、唯一递增 `seq`；消息关联会话 |
| `ai_rule_document` | `document_id` 主键、标题、`enabled`、`active_version`、创建/更新时间 |
| `ai_rule_document_version` | 自增主键、`document_id`、`version`、来源类型/路径、状态、片段数、创建/更新时间；文档与版本联合唯一 |

旧 AI 建表脚本可能没有 `turn_id`。先检查目标表，确认缺失后补充，已有该列不要重复执行：

```sql
SHOW COLUMNS FROM ai_message LIKE 'turn_id';
-- 仅在确认不存在时执行：
ALTER TABLE ai_message ADD COLUMN turn_id VARCHAR(64) NULL AFTER conversation_id;
```

### 2. 配置本机连接与模型

默认加载 `local` profile。首次运行可复制已提交的配置模板；已有本机配置时直接编辑，不要覆盖：

```powershell
Copy-Item platform-server/src/main/resources/application-dev.yml platform-server/src/main/resources/application-local.yml
```

确认 `wzx.datasource`、`wzx.redis` 的主机、端口、数据库和认证；OSS 与微信功能按需要填写配套参数。`application-local.yml` 已被 Git 忽略。

在启动后端的 PowerShell 或 IDEA 运行配置中提供环境变量：

```powershell
$env:SPRING_AI_MODEL_CHAT = "deepseek"
$env:DEEPSEEK_API_KEY = "<DeepSeek API Key>"
$env:DASHSCOPE_API_KEY = "<百炼 API Key>"
$env:WZX_DATASOURCE_USERNAME = "<数据库用户名>"
$env:WZX_DATASOURCE_PASSWORD = "<数据库密码>"
$env:WZX_REDIS_PASSWORD = "<Redis密码；无密码时为空字符串>"
$env:WZX_MILVUS_HOST = "<Milvus主机地址>"
$env:WZX_MILVUS_PORT = "19530"
$env:WZX_JWT_ADMIN_SECRET_KEY = "<管理员JWT签名密钥>"
$env:WZX_JWT_USER_SECRET_KEY = "<用户JWT签名密钥>"
```

尖括号为占位符，使用时替换整个值。不要把真实凭据写入 README 或提交到 Git。百炼 Embedding 使用 `https://dashscope.aliyuncs.com/compatible-mode` 与 `/v1/embeddings`，模型、地域、API Key 和 1024 维配置应一致。

Milvus 连接与集合参数在 [application.yml](platform-server/src/main/resources/application.yml) 中配置；使用虚拟机时先确认已开机、地址可达及 Milvus 服务正常。当前 `initialize-schema: true` 允许创建所需集合，不是每次启动重新导入资料。

### 3. 准备资料并登记待导入版本

新资料采用 UTF-8 TXT，每章以独占行 `## ` 开头，标题和正文均不能为空。将资料放入 `platform-server/src/main/resources/knowledge/` 后，以 `classpath:knowledge/...` 记录路径；先准备文件再启动或重新打包。

下面是**新文档首次登记示例**，文件与编号均为示例，需要自行准备：

```text
knowledge/order-rules-demo.txt

## 规则主题
在这里填写从实际业务确认的规则正文。

## 另一个规则主题
在这里填写对应的条件、限制与处理说明。
```

可调用 `RagDocumentCatalogService.registerVersion(spec)` 登记。当前没有单独的版本登记 HTTP 接口；通过数据库管理时，先确认文档与版本不存在，再按实际表结构登记，例如：

```sql
START TRANSACTION;
INSERT INTO ai_rule_document
(document_id, document_title, enabled, active_version, create_time, update_time)
VALUES ('demo_order_rules', '订单规则示例', 1, NULL, NOW(), NOW());

INSERT INTO ai_rule_document_version
(document_id, version, source_type, source_path, status, chunk_count, create_time, update_time)
VALUES ('demo_order_rules', 'v1', 'manual',
        'classpath:knowledge/order-rules-demo.txt', 'PENDING', 0, NOW(), NOW());
COMMIT;
```

已有文档更新时登记新的版本，不覆盖已发布版本或提前修改 `active_version`。资料就绪后登录管理端，调用 `POST /admin/ai/rag/versions/import`：

```json
{
  "documentId": "demo_order_rules",
  "version": "v1"
}
```

接口只接收已登记文档 ID 与版本。成功后返回片段数并发布；同一 READY 版本不能再作为 PENDING 导入。旧的 `/admin/ai/rag/index` 已不再提供建立内存索引入口。

### 4. 启动后端与配套管理端

IDEA 运行 `platform-server/src/main/java/com/wanzhongxiang/WanZhongXiangApplication.java`，或在仓库根目录执行：

```bash
mvn clean package -DskipTests
java -jar platform-server/target/platform-server-1.0-SNAPSHOT.jar --spring.profiles.active=local
```

启动前准备好数据库、Redis、Milvus 和模型配置。接口文档：

- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

独立管理端工程使用 `npm install`、`npm run serve`，开发地址为 `http://localhost:8888/#/login`，联调时关闭 AI Mock。前端 `/api/` 对应后端 `/admin/`；生产构建需要重新执行 `npm run build` 并同步 Nginx 静态目录。

SSE 反向代理需要关闭缓冲，下面是配套 Nginx 的基本示例：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080/admin/;
}

location = /api/ai/chat/stream {
    proxy_pass http://127.0.0.1:8080/admin/ai/chat/stream;
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 120s;
}
```

WebSocket 提醒另需匹配前端连接地址与 Upgrade 转发配置。

## 验证与实现边界

- 已有本地功能、定向测试与联调记录，覆盖会话归属/终态、规则来源、资料版本过滤、真实存储读回及区间计算等；部分验证由工具辅助。仓库忽略测试目录与 `*Test.java`，不将本地记录描述为已公开的 CI 测试体系。
- `GET /admin/ai/health` 会实际调用聊天模型，可能产生调用成本。`ragReady` 和 `/rag/ready` 依据生效目录判断，不等于 Embedding、Milvus 和整条问答链路均可用。
- 聊天开始前的问题长度与归属错误通过 HTTP 响应处理；流式输出后的模型或来源错误通过 SSE `error` 表达，HTTP 200 不代表整轮成功。
- 当前结构化来源卡片主要通过本轮 `sources` 返回，消息历史未完整保存来源快照，不保证刷新后重现原卡片。
- 应用突然退出可能遗留 GENERATING，资料导入也未实现分布式任务接管、自动补偿、备份恢复或高可用保障。
- 固定订单派送分析只解释数据库读取时刻的事实与规则，不修改订单状态，也不执行真实派送。
- 配套管理端已进行本地业务与 AI 联调；旧小程序和真实支付链路不能由模拟订单或管理端测试推定为已经完整验收。
