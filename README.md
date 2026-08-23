# 万众享平台

万众享平台是一个面向商户运营和用户交易场景的综合业务平台，提供商品管理、组合商品、订单管理、商户管理和运营统计等功能。

项目基于既有业务系统进行平台化改造，保留成熟的前后端分离架构，并对业务名称、接口路径、数据库脚本和管理端页面进行统一适配。

## 技术栈

### 后端

- Java 17+
- Spring Boot 2.7.17
- MyBatis
- MySQL
- Redis
- JWT
- WebSocket
- Knife4j
- Maven

### 前端

- Vue 2
- TypeScript
- Vue Router
- Vuex
- Element UI
- Axios
- ECharts

### 部署

- Nginx

## 功能模块

- 管理员登录与权限认证
- 商品分类与商品管理
- 组合商品管理
- 订单查询与状态处理
- 商户营业状态管理
- 运营数据统计
- 文件上传与对象存储配置
- WebSocket 实时通信

## 项目结构

```text
wanzhongxiang-platform/
├── platform-common/                 # 通用配置、工具类和公共常量
├── platform-pojo/                   # 实体类、DTO、VO 和枚举
├── platform-server/                 # Spring Boot 启动模块、控制器和业务代码
│   └── src/main/
│       ├── java/com/wanzhongxiang/  # 后端 Java 源码
│       └── resources/               # MyBatis 映射文件和 Spring 配置
└── pom.xml

项目工作区/
├── MySql/wanzhongxiang-platform.sql
├── 万众享平台前端源码/wanzhongxiang-admin-vue-ts/
└── nginx-1.20.2/
```

## 运行环境

启动前请准备以下服务：

- JDK 17 或更高版本
- Maven 3.8+
- MySQL 8.x
- Redis 3.x 或更高版本
- Node.js 16.x 或项目实际兼容版本

## 数据库配置

1. 创建数据库 `wanzhongxiang_platform`。
2. 导入工作区中的 `MySql/wanzhongxiang-platform.sql`。
3. 在以下文件中配置本机数据库和 Redis 连接信息：

   `platform-server/src/main/resources/application-local.yml`

该文件已被 Git 忽略，请根据本机环境填写，不要将数据库密码、Redis 密码、JWT 密钥或对象存储密钥提交到仓库。

管理员登录账号为：

```text
用户名：admin
密码：123456
```

密码必须与 `employee` 表中的 MD5 密文匹配；普通用户数据位于 `user` 表中，与管理员账号不是同一类数据。

## 启动后端

### IntelliJ IDEA

运行：

`platform-server/src/main/java/com/wanzhongxiang/WanZhongXiangApplication.java`

后端默认监听 `8080` 端口。

### Maven 和 JAR

在项目目录 `Code/wanzhongxiang-platform` 下执行：

```bash
mvn clean package -DskipTests
java -jar platform-server/target/platform-server-1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Windows 使用 CMD 时，如果当前目录不是项目目录，请先切换目录：

```bat
cd /d D:\JavaProjects\WanZhongXiang\Code\wanzhongxiang-platform
```

## 启动前端开发服务

在前端目录执行：

```bat
cd /d D:\JavaProjects\WanZhongXiang\万众享平台前端源码\wanzhongxiang-admin-vue-ts
npm install
set NODE_OPTIONS=--openssl-legacy-provider
npm run serve
```

前端开发地址：

`http://localhost:8888/#/login`

前端开发服务通过配置将 `/api` 请求转发到后端 `8080` 端口，因此后端必须同时启动。

## 端口说明

| 服务 | 端口 | 用途 |
| --- | ---: | --- |
| Vue 开发服务器 | 8888 | 本地开发和调试前端页面 |
| Spring Boot | 8080 | 后端 API、Swagger/Knife4j 和 WebSocket |
| Nginx | 80 | 生产构建后的前端页面及反向代理 |
| MySQL | 3306 | 业务数据库 |
| Redis | 6379 | 缓存和会话相关数据 |

Redis 启动后端前必须处于运行状态。Windows Redis 目录下应使用 Redis 服务端命令：

```bat
redis-server.exe redis.windows.conf
```

`redis-cli.exe` 用于连接和操作已经启动的 Redis，不能用来启动 Redis 服务。

## 接口文档

后端启动后访问：

`http://localhost:8080/doc.html`

如果通过 Nginx 访问，则使用 Nginx 的反向代理配置和实际入口地址。

## 生产构建

在前端目录执行：

```bat
set NODE_OPTIONS=--openssl-legacy-provider
npm run build
```

构建完成后，将生成的 `dist` 目录内容部署到 Nginx 的：

`nginx-1.20.2/html/wanzhongxiang`

启动 Nginx 后，通过 `http://localhost/` 访问前端。Nginx 会将管理端 `/api/` 请求代理到后端 `8080` 端口。

## 开发说明

- 后端配置按 `local` profile 加载，敏感配置只保存在本地配置文件中。
- 修改后端接口路径后，需要同步检查前端请求地址和 Nginx 代理规则。
- 涉及登录、订单、缓存和 WebSocket 的功能调整后，应结合后端日志完成联调验证。
- 图片和对象存储地址需要根据实际部署环境配置，示例数据中的地址不代表生产环境地址。
