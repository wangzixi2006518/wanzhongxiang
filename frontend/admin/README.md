# 万众享管理端

Vue 2 + TypeScript + Element UI 管理端，包含登录、商品与组合商品、订单、经营统计和 AI 助手页面。完整启动顺序见仓库根目录的 [README](../../README.md)。

## 开发

```bash
npm ci --legacy-peer-deps
npm run serve
```

默认地址 `http://localhost:8888/#/login`，`/api` 代理到 `http://localhost:8080/admin`，WebSocket 连接 `ws://localhost:8080/ws/`。AI 默认调用真实后端，不启用 Mock。

需要改地址时，新建已忽略的 `.env.development.local`，覆盖 `VUE_APP_URL` / `VUE_APP_SOCKET_URL`。所有 `VUE_APP_*` 会被打包进浏览器，模型密钥只配置在后端。

## 构建与检查

```bash
npm run build
npm run test:unit -- --runInBand --watch=false
```

构建产物在 `dist/`，通过根目录 `deploy/nginx/nginx.conf` 代理后端。项目沿用 Vue CLI 3 / Webpack 4，构建脚本包含 `--openssl-legacy-provider`；使用支持此参数的 Node.js（本次验证版本见根目录 README），安装时沿用已提交的 `package-lock.json`。

前端源自现有模板并进行业务与 AI 功能开发，原始 MIT 许可证及版权声明保留在 [LICENSE](LICENSE)。
