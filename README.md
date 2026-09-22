# wx-1008 动力伞航线锚点适配系统

## 项目简介

动力伞航线锚点适配系统，包含 Spring Boot 后端、Vue/Vite 前端、MySQL 和 Redis。项目已统一为 UTF-8 编码，并通过 Docker Compose 固定端口交付。

## 端口

- 前端: http://localhost:3208 / http://127.0.0.1:3208
- 后端 API: http://localhost:3308/api
- MySQL: 127.0.0.1:3408
- Redis: 127.0.0.1:6508

## 地勤资质与开航值守

系统已支持地勤人员资质证与航线/飞行日值守：

- 资质证记录适用风级、可负责锚点区域、生效日、到期日，状态为待生效/有效/已过期/已吊销。
- 每条航线每个飞行日安排一名操作员和一名复核员；两人不得相同。
- 资格按同一判定引擎同时校验航线当前风级与该航线**全部在用锚点所在区域**，逐项列出人员缺口。
- 状态链为：草拟 → 待复核（操作员本人到位）→ 就绪（复核员本人确认）→ 可由安全主管取消。
- 跨午夜任务采用**计划起飞时刻/起飞日**口径；不要求证书覆盖整个预计飞行区间，页面和接口会同步说明该取舍。
- 吊销证书、取消已就绪值守由后端校验安全主管身份；普通值班员只能确认自己的到位。
- 就绪时冻结证书编号、适用范围、人员姓名快照；后续改名、改证或吊销不影响已结束历史。

前端身份切换通过 `X-Actor-Id` 请求头传递，权限以服务端拒绝结果为准。

## 构建与启动

```bash
cd /Users/Admin/Desktop/solo-0601/wx-0701/wx-组1/wx-1008
cd backend && mvn compile -q
cd ../frontend && npm install && npm run build
cd .. && docker compose up -d --build
```

也可以执行：

```bash
./start.sh
```

## Docker 构建缓存

- 后端 Dockerfile 先复制 `pom.xml` 和 `settings.xml` 并下载 Maven 依赖，再复制 `src` 编译。
- 前端 Dockerfile 先复制 `package.json` 并安装 npm 依赖，再复制源码执行构建。
- `.dockerignore` 排除了 `node_modules`、`dist`、`target`、日志、临时文件、截图和 IDE 配置。
