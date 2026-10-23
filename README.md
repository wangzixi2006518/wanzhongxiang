# 万众享平台 · Java 后端与 AI 运营助手

面向商户运营与用户消费场景的 Java 后端项目，涵盖商品与组合商品管理、购物车、订单处理和运营统计。在业务系统上接入 Spring AI，为管理端提供 **AI 流式问答、多轮对话与历史恢复、经营数据智能查询、规则问答与来源追溯、知识库版本管理**。

经营数字由后端工具查询真实订单，平台规则由生效知识库提供依据；资料不足时拒答，引用不合法时中止正常收尾并反馈失败。本地 **50 题固定集**中，Top-2 完整依据命中率与可回答题正确率均为 **37/40（92.5%）**，10 道无答案题均正确拒答。指标定义、测试条件和失败案例见下文「RAG 质量评测」。

本仓库提供**Java 后端、Vue 管理端、数据库初始化脚本、规则资料、Nginx 配置和 RAG 评测材料**。克隆后可按下文完成本地安装；MySQL、Redis、Milvus 与模型服务仍需自行配置。当前目录没有可独立运行的用户端小程序源码，用户端接口与管理端页面分开说明。

## 核心功能

| 模块 | 当前实现 |
| --- | --- |
| AI 流式问答 | Spring AI ChatClient + SSE 实时输出，统一处理完成、失败和取消状态 |
| 多轮对话与历史恢复 | MySQL 持久化会话和消息，校验管理员归属，按最近完整成功轮次重建 ChatMemory |
| 经营数据智能查询 | Tool Calling 查询昨天或指定过去日期的已完成订单数和营业额，校验日期，区分无订单与查询失败 |
| 规则问答与幻觉防控 | 按章节与 Token 分片，Embedding 后写入 Milvus；检索生效资料、核对本轮引用、返回来源，资料不足时拒答 |
| 知识库更新与版本管理 | MySQL 管理 PENDING / READY 与生效版本；索引写入后事务发布，失败保留旧版，重启复用持久化索引 |
| 业务管理与订单事务 | 管理员 JWT 认证、商品及组合商品管理、购物车、订单状态处理；事务统一控制订单、明细和购物车清理 |
| 经营区间对比 | SQL 聚合已完成订单，BigDecimal 计算客单价、营业额差值与变化率，处理日期边界和零基数 |
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
| 管理端 | Vue 2、TypeScript、Vue Router、Vuex、Element UI、Axios、ECharts、Nginx |

MySQL 保存订单、会话、消息和资料版本等业务事实；Milvus 保存规则片段向量、正文及元数据。聊天和 Embedding 使用各自的模型与 API Key。

## 关键实现

### 1. AI 流式问答：实时输出与生成状态

面向商户经营咨询，使用 Spring AI `ChatClient` 与 SSE 边生成边输出。会话、消息与工具调用贯穿同一次请求，正常回答在来源核对和数据库保存成功后才发送完成事件。

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
- 同一会话存在生成中回答时返回 HTTP 409。数据库行锁只覆盖检查与占位的短事务，不持续锁住整个模型生成过程。
- 流式链路使用 30 秒停滞超时，约束首片段及相邻片段等待；持续输出时整轮时长可以超过 30 秒。
- 取消连接时保存已生成内容快照和取消状态；本地取消不代表远端立即停止生成或不再计费。

### 2. 多轮对话与历史恢复：持久化、归属与上下文筛选

通过 MySQL 持久化会话、用户消息、助手消息及生成状态，支持重新打开会话和跨请求连续追问。`conversationId` 只用于定位会话，访问时还会按 JWT 对应管理员校验归属，避免通过修改会话 ID 读取其他人的历史。

- 历史页面保留消息及终态；模型上下文只读取有成功助手回答的完整轮次，以 `turn_id` 关联用户与助手消息，并按 `seq` 排序。
- 失败、取消及生成未完成的消息不作为完整历史恢复，避免半截回答干扰后续追问。
- 每次从数据库恢复最近最多 **8 条历史消息**，普通一问一答约为 4 轮；`MessageWindowChatMemory` 配置 `maxMessages(10)`。
- 数据库承担长期保存，ChatMemory 承担本轮模型上下文；最近消息窗口限制上下文规模，但**条数不等于轮数或 Token 预算**，当前没有按 Token 计数截断或自动摘要。
- 同一会话的并发生成由短事务中的归属检查、生成状态检查和消息占位协调，防止同会话回答交错。

### 3. 经营数据智能查询：工具执行与结果边界

模型负责理解问题、选择工具和组织语言，Java 后端负责参数校验与真实数据查询。通过预先注册的只读工具复用经营统计和规则检索服务，不让模型直接生成并执行任意 SQL。

主聊天注册的工具如下：

| 工具 | 用途 |
| --- | --- |
| `query_yesterday_turnover` | 查询昨天已完成订单的笔数和营业额 |
| `query_turnover_by_date` | 查询明确指定的过去完整日期，校验日期格式、真实性并拒绝今天及未来日期 |
| `search_platform_rules` | 检索本轮平台规则正文与来源，供主聊天生成回答 |

日期工具由 `BusinessStatisticsTools` 提供并复用 `WorkspaceService`，规则工具由 `RuleRetrievalTools` 提供。指定日期需要符合 `yyyy-MM-dd`、可解析为真实日期，且早于服务端当天；只读完整过去日期，避免把今天尚未结束的数据当成完整日结果。

查询成功但没有已完成订单时，明确返回「没有已完成订单」与实际统计金额；数据库查询异常沿失败路径传播，不伪装成零营业额。工具得到的事实再交给模型组织回答，工具返回成功本身并不证明模型的最终表述完全正确。

经营区间对比目前由 `ReportService` 的独立 HTTP 接口提供，**尚未直接注册成区间对比工具**。AI 日期工具与区间对比接口复用的服务和日期边界实现不同，不能直接视为同一条查询链路。

### 4. 规则问答与幻觉防控：检索依据、拒答与来源追溯

平台规则不能依赖模型预训练知识猜测。将已确认规则按章节切分、向量化后保存到 Milvus，查询时只检索 MySQL 目录中的生效版本，并把片段正文和来源交给模型。

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

主聊天已经接入规则检索工具；独立规则窗口仍可调用 `/admin/ai/rag/answer`。本轮检索是否发生、取回哪些候选，由请求级 `RuleRetrievalContext` 记录；历史回答中的旧来源不能直接作为本轮引用依据。

**风险处理与来源校验：**

| 场景 | 当前处理 |
| --- | --- |
| 没有达到阈值的检索候选 | 独立规则接口直接返回「当前检索资料不足以回答该问题。」及空来源，不请求聊天模型 |
| 有候选但不足以回答 | 提示词要求依据不足时使用统一拒答文本；独立接口识别该文本并返回空来源，这是模型判断，仍需评测误答与误拒答 |
| 规则回答缺少来源，或引用本轮不存在的片段 | 后端提取来源编号并与本轮真实候选核对，校验失败抛出异常，不返回正常的规则回答结果 |
| 来源核对通过 | 从真实 `Document` 组装文档名、版本、片段编号与正文，供用户核对依据 |
| Milvus、Embedding 或聊天模型异常 | 作为技术失败反馈，不包装成「没有规则」或正常业务结果 |

主聊天的 SSE 在生成结束后核对来源：校验失败时保存 `FAILED` 并发送 `error`，不会发送正常的 `sources → done`。**已经输出的 `delta` 文本可能已被用户看到，当前不是生成前审核或逐句实时拦截。** 普通经营回答和澄清没有检索规则时允许无来源，但不能凭空带入规则引用。

来源校验验证的是「引用属于本轮候选」，**不保证结论被正文支持，也不代表幻觉已完全消除**。当前未实现独立的语义事实核验器或人工审核流转；通过固定题集继续检查回答质量和拒答边界。

### 5. 知识库更新与版本管理：先建索引，再事务发布

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

本地对 4 份文档、18 个片段做过目录、版本过滤、发布失败保留旧版以及重启读回验证。验收需要核对目录状态、原片段身份与正文，并重新检索，不能仅凭服务健康检查判断资料仍然可用。这些属于功能与恢复场景验证，与下面的问答质量评测分开统计，不代表备份恢复或高可用保障。

### 业务基础：订单事务与经营区间对比

下单方法将订单主表写入、明细批量写入和当前用户购物车清理放入同一数据库事务。该事务范围不包含远端支付或向量库，也没有在这条下单流程中额外实现商品库存扣减。

经营对比接口接收当前周期与基准周期的四个日期：

- 日期非空、开始不晚于结束，两个周期结束日期均早于上海时区的今天。
- 每段包含的自然日数量不超过 31 天，两段天数相同。
- 基准周期完全早于当前周期，不重叠。
- SQL 以 `status = 5` 的已完成订单为范围，按 `order_time` 使用 `>= 开始日零点`、`< 结束日次日零点` 筛选。
- `COUNT(*)` 统计笔数，`COALESCE(SUM(amount), 0)` 统计营业额；Java 计算客单价、差值及变化率。
- 金额和客单价保留两位小数，变化率保留四位，使用 `HALF_UP`；基准营业额为零时变化率返回 `null`，订单数为零时客单价为 `0.00`。

变化率返回比例，例如 `0.2000` 表示 `20%`。查询失败不作为零营业额返回。当前口径是「按下单时间筛选的已完成订单」，不等同于按收款时间统计财务流水。

## RAG 质量评测

### 测试条件与题集

2026-10-21，在本地运行中的 `POST /admin/ai/rag/answer` 上完成一轮 **50 道固定问题**评测。该接口经过真实 Embedding、Milvus 检索与 DeepSeek 生成；未更改业务实现或生效索引。

| 项目 | 本轮条件 |
| --- | --- |
| 知识库 | 4 份规则文档、18 个生效片段，来自 2026-10-09 源码说明快照 |
| 题集 | 20 道直接问题、10 道同义改写、10 道多片段问题、10 道无答案问题 |
| 预设依据 | 请求模型前固定题目、必要依据组与答案要点，运行后核对题集哈希未变 |
| 向量检索 | `text-embedding-v4`、1024 维、Milvus `COSINE / FLAT`、Top-2、阈值 0.5，按生效文档与版本过滤 |
| 生成与负载 | DeepSeek，温度 0.0；HTTP 并发 2，无自动重试 |
| 判分 | Codex 辅助逐题语义复核并保留原因，尚未经过独立人工复核 |

### 指标与结果

| 指标 | 定义与统计范围 | 本轮结果 |
| --- | --- | --- |
| **Top-2 完整依据命中率** | 40 道可回答题中，前两个候选覆盖全部必要依据 | **37/40（92.5%）** |
| **可回答题正确率** | 按题意与规则正文判断，误答、误拒答均不通过 | **37/40（92.5%）** |
| **无答案题正确拒答率** | 10 道资料不足题明确拒答，且不编造具体规则或事实 | **10/10（100%）** |
| 任一依据 Hit@2 | 至少找到一组必要依据，不要求完整 | 40/40（100%） |
| Recall@2 宏平均 | 每题已找回依据组数 / 必要依据组数，再对 40 题平均 | 96.25% |
| 可回答题误拒答率 | 资料有答案但被返回资料不足 | 1/40（2.5%） |
| 预设要点严格完整率 | 覆盖题集预写的全部要点，保留一项标注超出题意的差异 | 36/40（90%） |
| 接口成功 | HTTP 200 且业务 `code=1`；不代表回答正确 | 50/50（100%） |

两个 92.5% 分别衡量检索与回答，不能互相替代。多片段组问题级正确率为 **7/10**；单看任一依据 Hit@2 的 100% 会掩盖必要章节遗漏。

10 道无答案题中，**2 道由零候选分支直接拒答，8 道有候选但由模型拒答**。39 条非拒答响应共返回 52 条引用边，编号、正文和版本均能映射到生效片段；其中仍存在误答，因此引用合法不能代替语义判分。

本轮平均端到端耗时 **1605.66 ms**，P95（最近秩法）**2293.23 ms**。这是并发 2 的单轮观测，包含网络和云模型开销；不是高并发容量、SSE 首字延迟或优化前后提升。

### 失败案例与改进方向

| 题目 | 实际问题 | 下一步验证方向 |
| --- | --- | --- |
| Q31：用户取消与管理端拒单比较 | 漏掉用户取消章节，回答错误且前后矛盾 | 验证多片段覆盖与缺依据时的回答约束 |
| Q39：商品停售与套餐联动 | 漏掉联动章节，模型混淆商品停售与直接修改套餐两条流程 | 增加相似业务边界题，检查召回与条件区分 |
| Q40：接单与派送校验比较 | 未取回接单章节，对资料中存在答案的问题误拒答 | 同时关注漏检与误拒答，不能仅提高拒答比例 |

另有 Q32 标注范围差异：题干只问原订单状态，回答正确；预写要点还要求完成后状态和送达时间，因此问题级正确计通过、严格要点完整计不通过。保留原题集并分别报告 **92.5% 正确率**与 **90% 严格完整率**。

**评测范围与复核材料：**

- 真实 HTTP 响应之外，检索候选使用相同模型、维度、过滤和检索参数独立重放取得；全部 52 条返回引用均在重放候选中，但重放不是运行中 Java 进程的内部埋点。
- 本轮仅评估独立规则接口，不涵盖 SSE 工具选择、多轮记忆或经营工具查询；旧规则快照中的统计边界不替代后来区间对比接口的实现。
- [评测报告与复现说明](docs/evaluation/rag-20261021/REPORT.md)、[固定题集](docs/evaluation/rag-20261021/dataset.json)、[原始响应](docs/evaluation/rag-20261021/raw-results.jsonl)、[逐题复核](docs/evaluation/rag-20261021/cases.md)及[指标汇总](docs/evaluation/rag-20261021/summary.json)随仓库提供；不包含模型密钥或管理员 Token。
- 这是小知识库的一次固定集结果，没有优化前后对照，不代表生产或通用准确率。后续比较 Top-K、分片或提示词时，应固定其他条件、保留失败题，并用未参与调参的问题复验；当前未实现混合检索、重排或多智能体编排。

## 项目结构与代码入口

```text
wanzhongxiang/
├── frontend/admin/                 # Vue 2 + TypeScript 管理端、锁文件与单元测试
├── database/                       # 表结构、合成演示数据、待导入规则目录
├── deploy/nginx/                   # 静态页面、API、SSE、WebSocket 代理配置
├── docs/evaluation/rag-20261021/    # 50 题评测原始结果与复核材料
├── .mvn/、mvnw、mvnw.cmd           # Maven Wrapper
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
│       └── resources/              # 配置、Mapper XML、报表模板与 knowledge 规则
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

### 1. 环境与仓库

- JDK **17**（确认 `JAVA_HOME` 与 `java -version`，不要误用系统中的 JDK 8）。
- Node.js 与 npm：沿用 Vue CLI 3 / Webpack 4，脚本已包含 `--openssl-legacy-provider`；本次使用 Node **24.18.0**、npm **11.16.0** 验证。
- MySQL **8**、Redis，以及可连接的 Milvus（本地验收版本 **2.5.8**）。
- DeepSeek API Key 与百炼 Embedding API Key；图片上传、微信登录和支付另需对应服务配置。

```bash
git clone https://github.com/wangzixi2006518/wanzhongxiang.git
cd wanzhongxiang
```

仓库包含 Maven Wrapper，无需单独安装 Maven；首次运行仍需联网下载 Maven 与依赖。前端锁文件已将过期淘宝源替换为 npm 官方源，保留依赖版本与完整性校验。

### 2. 初始化空数据库

在仓库根目录启动 MySQL 客户端，例如 `mysql -u root -p --default-character-set=utf8mb4`，然后执行：

```sql
CREATE DATABASE wanzhongxiang_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE wanzhongxiang_platform;
SOURCE database/01-business-schema.sql;
SOURCE database/02-ai-schema.sql;
SOURCE database/03-demo-data.sql;
SOURCE database/04-rule-catalog.sql;
```

脚本用于**新建空库**，不对现有业务库做自动迁移。包含 11 张业务表、4 张 AI 表，消息表已经带有 `turn_id`；不需要再手工补列。详见 [数据库说明](database/README.md)。

演示账号：**`admin / Demo123456!`**。商品与组合商品均为合成数据，初始没有用户、订单或聊天记录，经营查询返回无订单属于正常情况。默认密码仅供本地体验，正式使用前修改；当前登录服务沿用 MD5 存储，不应把演示认证当作生产安全方案。

### 3. 配置并启动后端

新安装使用已提交的 [application-demo.yml](platform-server/src/main/resources/application-demo.yml)，通过环境变量覆盖连接和凭据，**不需要复制本机私有配置**。在启动后端的同一个 PowerShell 窗口中填写：

```powershell
$env:WZX_DATASOURCE_USERNAME = "<MySQL用户名>"
$env:WZX_DATASOURCE_PASSWORD = "<MySQL密码；无密码则为空字符串>"
$env:DEEPSEEK_API_KEY = "<DeepSeek API Key>"
$env:DASHSCOPE_API_KEY = "<百炼 API Key>"
$env:WZX_JWT_ADMIN_SECRET_KEY = "<至少32字节的管理员签名密钥>"
$env:WZX_JWT_USER_SECRET_KEY = "<至少32字节的用户端签名密钥>"
$env:WZX_MILVUS_HOST = "<Milvus主机地址>"
```

尖括号是占位符，替换整个值。聊天使用 DeepSeek，Embedding 使用百炼 `text-embedding-v4`、1024 维，两个 Key 不通用。环境变量只在后端使用，不能放入前端的 `VUE_APP_*`。

| 可选变量 | demo 默认值 / 作用 |
| --- | --- |
| `WZX_DATASOURCE_HOST` / `WZX_DATASOURCE_PORT` | `localhost` / `3306` |
| `WZX_DATASOURCE_DATABASE` | `wanzhongxiang_platform` |
| `WZX_REDIS_HOST` / `WZX_REDIS_PORT` | `localhost` / `6379` |
| `WZX_REDIS_PASSWORD` / `WZX_REDIS_DATABASE` | 空密码 / `10`，建议使用项目独立逻辑库 |
| `WZX_MILVUS_HOST` / `WZX_MILVUS_PORT` | `localhost` / `19530` |
| `WZX_OSS_ENDPOINT` / `WZX_OSS_ACCESS_KEY_ID` / `WZX_OSS_ACCESS_KEY_SECRET` / `WZX_OSS_BUCKET_NAME` | 需要上传图片时配置；未配置不能使用 OSS 上传 |
| `WZX_WECHAT_*` | 需要真实微信登录、支付和退款时填写，字段见 demo 配置 |

Windows 构建与启动：

```powershell
.\mvnw.cmd -Dmaven.test.skip=true package
java -jar platform-server/target/platform-server-1.0-SNAPSHOT.jar --spring.profiles.active=demo
```

macOS / Linux 使用 `chmod +x mvnw` 后执行 `./mvnw -Dmaven.test.skip=true package`，再用相同 Java 命令启动；环境变量用当前 shell 的 `export` 设置。上面的打包命令跳过测试，不代表所有依赖外部服务的测试均已执行。

IDEA 可以运行 `WanZhongXiangApplication`，同时设置相同环境变量与 active profile `demo`。已有 `application-local.yml` 的开发者可继续使用自己的 `local` profile；默认 `application.yml` 仍保留原有本地设置，不会覆盖私有文件。

默认后端端口 **8080**，文档入口：`http://localhost:8080/swagger-ui/index.html`，OpenAPI：`http://localhost:8080/v3/api-docs`。Milvus 的 `initialize-schema: true` 允许创建集合，不会自动导入规则正文。

### 4. 启动管理端

在另一个终端执行：

```powershell
cd frontend/admin
$env:CYPRESS_INSTALL_BINARY = "0"
npm ci --legacy-peer-deps
npm run serve
```

开发地址：**`http://localhost:8888/#/login`**。默认 `/api` 转发到后端 `/admin`，WebSocket 直连本机后端，AI Mock 默认关闭。`CYPRESS_INSTALL_BINARY=0` 仅跳过本地开发不需要的 Cypress 浏览器下载，不影响应用构建与单元测试；需要运行 E2E 时自行安装对应浏览器。

后端不在本机时，新建已忽略的 `frontend/admin/.env.development.local`，覆盖 `VUE_APP_URL` 和 `VUE_APP_SOCKET_URL`。详见 [管理端说明](frontend/admin/README.md)。

### 5. 导入随仓库提供的规则

[knowledge/day13](platform-server/src/main/resources/knowledge/day13) 包含 4 份按源码整理的规则快照，初始化 SQL 只登记为 `PENDING`，不会假装索引已经存在。

后端启动后，在仓库根目录的 PowerShell 中登录并逐份导入：

```powershell
$loginBody = @{ username = "admin"; password = "Demo123456!" } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/admin/employee/login" -ContentType "application/json" -Body $loginBody
$headers = @{ token = $login.data.token }
$documents = @(
    @{ documentId = "wx_order_rules"; version = "v2-20261009" },
    @{ documentId = "wx_product_rules"; version = "v1-20261009" },
    @{ documentId = "wx_shop_rules"; version = "v1-20261009" },
    @{ documentId = "wx_statistics_rules"; version = "v1-20261009" }
)
foreach ($document in $documents) {
    Invoke-RestMethod -Method Post -Uri "http://localhost:8080/admin/ai/rag/versions/import" -Headers $headers -ContentType "application/json" -Body ($document | ConvertTo-Json)
}
Invoke-RestMethod -Uri "http://localhost:8080/admin/ai/rag/ready" -Headers $headers
```

检查每次返回的业务 `code` 和片段数，再测试问答；HTTP 200 本身不表示导入成功。导入会调用 Embedding 模型，必须能连接百炼和 Milvus。成功后版本改为 `READY` 并发布，同一 READY 版本不能重复作为 PENDING 导入；失败时检查日志与目录状态后再重试。

规则采用 UTF-8 TXT、独占行 `## ` 章节标题。更新资料应新增文档版本，先登记 `source_path` 与 `PENDING` 状态，再调用导入接口，不覆盖旧生效版本或提前修改 `active_version`。当前没有独立版本登记 HTTP 接口；数据库字段和登记示例见 [规则目录初始化 SQL](database/04-rule-catalog.sql)。

这些资料是源码说明快照，不是正式运营制度；评测中的 18 个片段与其版本绑定。应用或 Milvus 重启后复用持久化索引，不需要因重启再次导入。

### 6. 生产构建与 Nginx 本地预览

```powershell
cd frontend/admin
npm run build
cd ../..
New-Item -ItemType Directory -Force deploy/nginx/logs | Out-Null
# 将 nginx 替换为本机安装的 Nginx 可执行文件路径。
nginx -e stderr -p "$((Get-Location).Path.Replace('\','/'))/" -c deploy/nginx/nginx.conf -t
nginx -e stderr -p "$((Get-Location).Path.Replace('\','/'))/" -c deploy/nginx/nginx.conf
```

配置文件为 [deploy/nginx/nginx.conf](deploy/nginx/nginx.conf)，**以前述仓库根目录为 prefix**，静态目录为 `frontend/admin/dist`，页面地址 `http://localhost:8081/#/login`。管理 API 转发 `/api/ → /admin/`，SSE 关闭代理缓冲，WebSocket 配置 Upgrade。默认后端目标为 `127.0.0.1:8080`；异机部署时修改配置中的目标地址。

Linux 同样先创建 `deploy/nginx/logs`，使用 `nginx -e stderr -p /absolute/path/to/wanzhongxiang/ -c deploy/nginx/nginx.conf`。此配置用于本地或自行部署，不会附带 Windows Nginx 二进制、证书或机器上的旧静态文件；公网部署另行配置域名与 HTTPS。

### 7. 检查与评测复现

```powershell
cd frontend/admin
npm run test:unit -- --runInBand --watch=false
```

RAG 复现见 [评测报告](docs/evaluation/rag-20261021/REPORT.md)，Python 依赖在 [requirements.txt](docs/evaluation/requirements.txt)。复测需要运行中的后端、生效规则、数据库只读查询权限及模型 Key；输出到新目录，并重新逐题复核，不覆盖原始报告或直接沿用旧判分。没有匹配规则快照时，不能把新运行与原 50 题结果直接比较。

## 验证与实现边界

2026-10-23 仓库整合检查：

| 检查 | 结果 |
| --- | --- |
| JDK 17 + Maven Wrapper 后端打包 | 通过；跳过后端测试 |
| 管理端从锁文件全新安装、生产构建 | 通过；构建仍有现有资源体积提示 |
| 前端单元测试 | 5 个测试套件、32 项测试通过 |
| 新库 SQL 初始化 | 临时数据库验证通过，共 15 张表；演示登录摘要、`turn_id` 和 4 份 PENDING 规则目录符合预期 |
| Nginx 配置与静态资源 | 配置校验、页面及 17 个构建资源读取通过 |
| 评测材料完整性 | 题集、原始响应与资料快照的原始哈希保持一致；本轮未重新调用模型评测 |

本轮新库完整启动联调未完成：验证环境的 Milvus 服务不可达。运行时须先确认向量服务与模型配置就绪，不能以构建通过代替完整业务验收。

- 已有本地功能、定向测试与联调记录，覆盖会话归属/终态、规则来源、资料版本过滤、真实存储读回及区间计算等；部分验证由工具辅助。前端单元测试随仓库提供；原后端学习验收测试仍保留在本地忽略目录，不将本地记录描述为已公开的 CI 测试体系。
- `GET /admin/ai/health` 会实际调用聊天模型，可能产生调用成本。`ragReady` 和 `/rag/ready` 依据生效目录判断，不等于 Embedding、Milvus 和整条问答链路均可用。
- 聊天开始前的问题长度与归属错误通过 HTTP 响应处理；流式输出后的模型或来源错误通过 SSE `error` 表达，HTTP 200 不代表整轮成功。
- 当前结构化来源卡片主要通过本轮 `sources` 返回，消息历史未完整保存来源快照，不保证刷新后重现原卡片。
- 应用突然退出可能遗留 GENERATING，资料导入也未实现分布式任务接管、自动补偿、备份恢复或高可用保障。
- 固定订单派送分析只解释数据库读取时刻的事实与规则，不修改订单状态，也不执行真实派送。
- 管理端源码与配置已纳入仓库；当前用户端小程序源码未提供，真实微信登录、支付与退款需要外部账号和证书，不能由模拟订单或管理端测试推定为已经完整验收。
