# 万众享平台 · Java 后端 + AI 运营助手

万众享平台面向商户运营和用户交易场景，提供商品、组合商品、订单及运营统计等业务功能。在已有 Java 后端上接入 Spring AI，实现流式对话、会话历史、短期记忆、经营数据工具调用、规则检索与订单派送分析。

本仓库保存后端源码。管理端前端、数据库初始化脚本和 Nginx 配置目前位于独立工作区，运行完整系统时需要另行准备。

## 功能概览

### 业务功能

- 管理员登录与 JWT 身份校验。
- 商品分类、商品和组合商品管理。
- 订单查询、接单、派送、完成及取消等状态处理。
- 商户营业状态、运营数据统计与报表。
- 文件上传与阿里云 OSS 对接。
- WebSocket 订单提醒基础服务。

### AI 运营助手

| 能力 | 当前实现 |
| --- | --- |
| 模型接入 | 使用 Spring AI 接入 DeepSeek，提供同步问答与流式问答 |
| SSE 流式输出 | 使用 Reactor Flux 输出 `meta / delta / done / error` 事件，支持取消生成 |
| 会话历史 | 会话及消息保存到 MySQL，记录生成中、完成、失败和取消状态；按当前管理员校验会话归属 |
| Chat Memory | 按会话隔离上下文，每次从数据库恢复最近完整轮次，再交给消息窗口与 Memory Advisor |
| Tool Calling | 模型按问题选择经营统计工具，由后端查询真实已完成订单的笔数和营业额 |
| RAG 检索 | 规则文档读取、Token 切分、Embedding 向量化和相似度检索 |
| 规则问答与引用 | 用检索正文生成回答，从本轮候选中核对引用并返回来源标题、版本、片段标识和原文 |
| 固定 Workflow | 后端按固定顺序组合订单事实、规则检索、模型分析及引用核对，判断订单在读取时刻是否满足开始派送前提 |

经营统计工具目前支持“昨天”和明确指定的过去日期，例如：

- “昨天已完成订单多少笔，营业额多少元？”
- “2026-09-28 已完成订单多少笔，营业额多少元？”

数据由业务 Service 与数据库查询提供。明确日期工具会检查日期格式并拒绝今天及未来日期。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot **3.5.16**、Maven 多模块 |
| AI | Spring AI **1.1.8**、ChatClient、Memory Advisor、Tool Calling、Reactor Flux |
| 聊天模型 | DeepSeek |
| Embedding | 阿里云百炼 `text-embedding-v4`，通过 OpenAI 兼容协议接入，当前为 **1024 维** |
| 检索存储 | Spring AI `SimpleVectorStore`，当前使用内存索引 |
| 数据与认证 | MyBatis、MySQL、Redis、JWT |
| 接口与通信 | Springdoc OpenAPI、SSE、WebSocket |
| 配套管理端 | Vue 2、TypeScript、Vue Router、Vuex、Element UI、Axios、ECharts |
| 配套部署 | Nginx |

DeepSeek 聊天和百炼 Embedding 使用各自的 API Key。

## 项目结构

```text
wanzhongxiang/
├── platform-common/                # 公共常量、上下文、异常、配置属性和工具类
├── platform-pojo/                  # 实体、DTO、VO
├── platform-server/                # Spring Boot 启动与业务模块
│   └── src/main/
│       ├── java/com/wanzhongxiang/
│       │   ├── controller/         # 管理端、用户端接口
│       │   ├── service/            # 业务服务与 AI 会话服务
│       │   ├── mapper/             # MyBatis 数据访问
│       │   ├── tool/               # 经营统计工具
│       │   ├── rag/                # 文档加载、向量化、检索、问答与引用
│       │   └── workflow/           # 订单派送分析固定流程
│       └── resources/              # Spring 配置、Mapper XML、报表模板
├── pom.xml
└── README.md
```

配套工作区资源：

```text
MySql/
├── wanzhongxiang-platform.sql       # 业务数据库初始化
└── wanzhongxiang-ai.sql             # AI 会话与消息表，旧版需补齐 turn_id

万众享平台前端源码/
└── wanzhongxiang-admin-vue-ts/      # 独立管理端工程

nginx-1.20.2/                       # 前端静态站点与反向代理
```

## 两条 AI 调用链

### 经营数据对话

```text
管理员问题 + conversationId
 → 校验管理员身份和会话归属
 → 保存用户消息与生成中的助手消息
 → 从数据库恢复最近完整轮次，准备 Chat Memory
 → ChatClient + 经营统计工具
 → 模型选择工具，后端查询数据库，模型组织回答
 → SSE 增量输出
 → 保存完成、失败或取消状态
```

数据库保留会话历史；Memory 负责模型请求中的上下文窗口。当前窗口最多 10 条消息，每次先恢复最近最多 8 条完整历史消息。

### 订单派送分析

```text
管理员指定订单主键
 → Controller 调用 OrderDispatchWorkflowService.analyze(orderId)
 → Mapper 读取订单状态，记录 queriedAt
 → 检索“开始派送”相关规则
 → 将数据库事实、状态名称、规则正文和分析任务交给模型
 → 核对本轮引用来源
 → 返回 orderId / orderStatus / queriedAt / answer / sources
```

这条流程的执行顺序由后端代码确定。模型负责阅读事实和规则、生成解释，分析接口本身不会修改订单状态或执行派送。

相关实现：
[会话服务](platform-server/src/main/java/com/wanzhongxiang/service/impl/AiChatServiceImpl.java)、
[经营工具](platform-server/src/main/java/com/wanzhongxiang/tool/BusinessStatisticsTools.java)、
[规则检索](platform-server/src/main/java/com/wanzhongxiang/rag/RagRetrievalService.java)、
[规则问答](platform-server/src/main/java/com/wanzhongxiang/rag/RagAnswerService.java)、
[订单 Workflow](platform-server/src/main/java/com/wanzhongxiang/workflow/OrderDispatchWorkflowService.java)。

## AI 接口

下表对应仓库当前已提交的后端入口，均沿用管理端 JWT，请求头为 `token`。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/admin/ai/chat` | 同步单次问答 |
| POST | `/admin/ai/chat/stream` | 带会话的 SSE 流式问答、Memory 与 Tool Calling |
| GET | `/admin/ai/health` | 通过一次聊天模型调用检查可用性 |
| POST | `/admin/ai/conversations` | 新建会话 |
| GET | `/admin/ai/conversations` | 查询当前管理员的会话 |
| GET | `/admin/ai/conversations/{conversationId}/messages` | 查询所属会话历史 |
| DELETE | `/admin/ai/conversations/{conversationId}` | 删除所属会话并清理对应 Memory |
| POST | `/admin/ai/rag/index` | 显式建立规则索引，返回实际片段数 |
| GET | `/admin/ai/orders/{orderId}/dispatch-analysis` | 结合订单事实与规则生成派送前提分析 |

流式请求示例：先创建会话，再使用返回的真实 `conversationId`。

```json
{
  "message": "昨天已完成订单多少笔，营业额多少元？",
  "conversationId": "<创建会话接口返回的ID>"
}
```

同步单次问答只返回回答文本；Memory 和经营统计工具注册在流式会话路径。通用规则问答由 `RagAnswerService.answerWithSources(question)` 提供，HTTP 入口以 [AiController](platform-server/src/main/java/com/wanzhongxiang/controller/admin/AiController.java) 的实际映射为准。

## 本地运行

### 1. 准备环境与数据库

准备 JDK 17、Maven、MySQL 和 Redis。完整管理端还需要配套前端工程及其 Node.js 环境。

```bash
git clone https://github.com/wangzixi2006518/wanzhongxiang.git
cd wanzhongxiang
```

创建数据库 `wanzhongxiang_platform`，导入配套业务 SQL 和 AI 表结构。当前消息 Mapper 依赖 `ai_message.turn_id` 与 `seq`：旧版 AI 建表脚本未包含 `turn_id` 时，需要在目标库确认字段不存在后补充：

```sql
ALTER TABLE ai_message
    ADD COLUMN turn_id VARCHAR(64) NULL AFTER conversation_id;
```

管理员账号来自 `employee` 表，普通用户来自 `user` 表；登录信息以实际导入数据为准。

### 2. 配置本机连接和模型凭据

默认加载 `local` profile。首次运行可复制已有配置模板，再编辑本机信息：

```powershell
Copy-Item platform-server/src/main/resources/application-dev.yml platform-server/src/main/resources/application-local.yml
```

在 `application-local.yml` 中确认 `wzx.datasource`、`wzx.redis` 的主机、端口、数据库和认证信息。OSS 与微信对接参数也由该配置提供，使用对应功能时填写实际值。本机配置已被 Git 忽略。

在启动后端的 PowerShell 中设置环境变量：

```powershell
$env:SPRING_AI_MODEL_CHAT = "deepseek"
$env:DEEPSEEK_API_KEY = "<你的DeepSeek API Key>"
$env:DASHSCOPE_API_KEY = "<你的百炼API Key>"
$env:WZX_DATASOURCE_USERNAME = "<数据库用户名>"
$env:WZX_DATASOURCE_PASSWORD = "<数据库密码>"
$env:WZX_REDIS_PASSWORD = "<Redis密码；无密码时设为空字符串>"
$env:WZX_JWT_ADMIN_SECRET_KEY = "<自定义管理员JWT签名密钥>"
$env:WZX_JWT_USER_SECRET_KEY = "<自定义用户JWT签名密钥>"
```

以上尖括号内容是占位符，填写时替换整个占位符。使用 IDEA 时，在运行配置中提供相同变量；已有本机配置覆盖的值以实际配置优先级为准。不要将真实凭据写入 README 或提交到 Git。

当前百炼 Embedding 地址为 `https://dashscope.aliyuncs.com/compatible-mode`，请求路径为 `/v1/embeddings`；使用与该地域匹配的 Key。

### 3. 准备规则文档

文档加载器读取：

```text
platform-server/src/main/resources/knowledge/order-processing-rules-v1.txt
```

该规则资源目前保存在本地工作区，需要随运行环境另行提供。现有版本为从订单业务代码整理的“万众享订单处理规则说明”，涉及取消、拒单、派送与完成等条件；文档与元数据版本应保持一致。

首次规则检索或订单分析前，登录管理端并调用 `POST /admin/ai/rag/index`。后端重启后需重新建立内存索引。

### 4. 启动后端

IDEA 运行入口：

```text
platform-server/src/main/java/com/wanzhongxiang/WanZhongXiangApplication.java
```

或在仓库根目录执行：

```bash
mvn clean package -DskipTests
java -jar platform-server/target/platform-server-1.0-SNAPSHOT.jar --spring.profiles.active=local
```

MySQL、Redis 和模型配置需在启动前准备好；默认后端端口为 `8080`。

接口文档：

- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

### 5. 启动配套管理端

在独立前端工程目录执行：

```bash
npm install
npm run serve
```

开发地址为 `http://localhost:8888/#/login`。前端使用 `/api` 作为管理端请求前缀，开发代理目标为 `http://localhost:8080/admin`；联调时关闭 AI Mock。

生产构建执行 `npm run build`，将 `dist` 内容复制到 Nginx 的 `html/wanzhongxiang`。访问 `http://localhost/` 时，页面来自这份静态构建，源码修改后需重新构建并同步。

Nginx 中管理端代理的基本配置：

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

WebSocket 提醒另需匹配前端连接地址与 Nginx 的 Upgrade 转发配置。

## 当前实现范围

- 向量索引使用内存存储，当前检索参数为 `topK=2`、`similarityThreshold=0.5`，尚未接入持久化向量数据库。
- 检索相关性只用于筛选候选；空结果返回资料不足，Embedding 或聊天调用失败继续作为异常处理。引用核对保证来源来自本轮真实片段，不保证模型结论必然正确。
- 健康接口会实际请求聊天模型；当前 `ragReady` 字段仍固定为 `false`，不能据此判断是否已经建立索引。
- 配套管理端已做本地业务与 AI 联调；旧小程序未完成本次平台改造的适配，不能将现有模拟订单当作真实小程序下单、支付链路已经验证。
- 本地工作区有定向测试与验收记录；当前 Git 忽略了测试目录及 `*Test.java`，不将其描述为仓库已有的 CI 或完整生产保障体系。
