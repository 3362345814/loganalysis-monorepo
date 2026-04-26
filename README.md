# LogAnalysis Monorepo

一个面向生产场景的日志采集、检索、聚合、智能分析与告警平台。
本仓库包含：

- 后端服务（Spring Boot）
- 前端控制台（Vue 3 + Element Plus）
- 跨平台运维 CLI（Go，基于 Docker Compose 一键部署）

当前文档对应版本：`v1.2.3`（发布于 `2026-04-26`）

## 系统功能全景

### 1) 项目与多租户隔离

- 项目管理：支持项目创建、更新、启用/停用、删除
- 资源隔离：日志源、日志查询、聚合与告警均可按项目维度隔离
- 页面入口：`项目管理`、`日志采集`、`日志查询`、`告警管理` 都支持项目筛选

### 2) 日志采集与接入治理

- 日志源管理：创建/编辑/删除日志源，支持启用状态切换
- 采集器生命周期：支持 `启动`、`停止`、`状态查询`、队列状态查看
- 日志格式：`LOG4J(可自定义)`、`NGINX`、`JSON`
- 接入前验证：支持 SSH 连通性测试、路径可达性测试
- 采集增强：支持聚合、敏感信息脱敏规则配置

### 3) 日志检索与 Trace 追踪

- 原始日志查询：支持按日志源、时间范围、级别、关键词、分页查询
- 单条日志详情：支持按日志 ID 查看完整内容
- Trace 查询：支持按 `traceId` 分页查询、全量查询、数量统计
- ES 高级检索（`full` profile）：支持关键字/高亮/时间分桶
- 前端体验：日志终端视图 + 详情面板 + Trace 时间线联动

### 4) 聚合与上下文还原

- 聚合组检索：支持分页、状态过滤、严重级别过滤
- 聚合组上下文：可提取错误前后 N 条上下文日志用于定位问题
- 未分析聚合组：可快速筛选待 AI 处理的异常组
- 运维清理：支持过期聚合组清理

### 5) AI 智能分析

- 手动触发分析：可对指定聚合组立即执行根因分析
- 分析结果查询：支持按聚合 ID 获取结果、按最近 N 条查看
- 分析策略配置：支持上下文行数、自动分析级别、自动分析开关
- LLM 配置管理：支持多配置维护、默认配置、活跃配置、API Key 验证

### 6) 告警中心与通知闭环

- 规则管理：创建/更新/删除/分页查询规则，支持启停切换
- 规则类型：关键词、正则、级别、阈值、组合条件
- 告警记录流转：待处理、确认、分配、升级、解决
- 统计分析：告警总览、趋势统计、级别分布
- 通知渠道：钉钉、飞书、企业微信、邮箱、WebHook
- 渠道配置：支持批量保存和 UPSERT（存在即更新）

### 7) 运维与可观测性

- 一键部署与销毁：`up/down/uninstall`
- 运行态观测：`status/logs`
- 环境诊断：`doctor`（Docker/Compose/端口/磁盘/镜像仓库连通性）
- 版本升级：`upgrade`（支持主版本保护与回滚）

## 架构组件与部署档位

### 组件

- `frontend`：Web 控制台（默认映射 `3000`）
- `backend`：API 服务（默认映射 `8080`）
- `postgres`：业务数据存储
- `redis`：缓存与状态
- `rabbitmq`：异步消息
- `elasticsearch`、`kibana`、`minio`：完整检索与对象存储能力（仅 `full`）

### 部署策略

- 当前 CLI 仅保留 `full` 一种启动策略：`frontend + backend + postgres + redis + rabbitmq + elasticsearch + kibana + minio`

### 默认端口

- `frontend=3000`
- `backend=8080`
- `postgres=5432`
- `redis=6379`
- `rabbitmq=5672`
- `rabbitmq_management=15672`
- `elasticsearch=9200`
- `elasticsearch_transport=9300`
- `kibana=5601`
- `minio_api=9000`
- `minio_console=9001`

## 快速开始

### 0) 前置条件

- Docker Desktop / Docker Engine（需支持 `docker compose`）
- 可访问 GitHub 与 Docker Hub

先检查：

```bash
docker --version
docker compose version
```

### 1) 安装 CLI（方式一：脚本一键安装，推荐）

#### macOS / Linux

```bash
curl -fsSL "https://raw.githubusercontent.com/3362345814/loganalysis-monorepo/v1.2.3/scripts/install.sh" | sh
```

#### Windows PowerShell

```powershell
irm "https://raw.githubusercontent.com/3362345814/loganalysis-monorepo/v1.2.3/scripts/install.ps1" | iex
```

安装后验证：

```bash
loganalysis version
```

### 2) 安装 CLI（方式二：下载二进制后直接运行）

适合内网分发、临时机器、或不希望改 PATH 的场景。

1. 打开 Release 页面下载对应系统文件
2. （可选）下载 `checksums.txt` 做校验
3. 直接执行二进制

示例（Windows PowerShell）：

```powershell
$bin = "$env:TEMP\loganalysis-windows-amd64.exe"
curl.exe -L --fail -o $bin "https://github.com/3362345814/loganalysis-monorepo/releases/download/v1.2.3/loganalysis-windows-amd64.exe"
& $bin version
& $bin doctor
```

示例（Linux/macOS）：

```bash
curl -fL -o /tmp/loganalysis "https://github.com/3362345814/loganalysis-monorepo/releases/download/v1.2.3/loganalysis-linux-amd64"
chmod +x /tmp/loganalysis
/tmp/loganalysis version
/tmp/loganalysis doctor
```

### 3) 首次启动（推荐顺序）

```bash
loganalysis doctor
loganalysis auth set-admin --username admin
loganalysis up --version v1.2.3
loganalysis status
```

如你希望本地环境不开启鉴权，可先关闭后再启动：

```bash
loganalysis config set auth.enabled false
loganalysis up --version v1.2.3
```

默认访问地址：

- 前端：<http://localhost:3000>
- 后端：<http://localhost:8080>

## CLI 关键字详解（命令手册）

> 查看总帮助：`loganalysis help`

### 1) `up`

用途：按 `full` 策略渲染并启动 Docker Compose 栈。

关键参数：

- `--version vX.Y.Z|latest`：镜像 tag（默认取 `config.default_version`；未固定时使用当前 CLI 版本）
- `--auto-port`：兼容参数，行为同默认值（自动检测并避让端口冲突）
- `--no-auto-port`：关闭自动端口避让

示例：

```bash
# 默认使用当前 CLI 版本对应的镜像
loganalysis up 

# 指定版本（默认也会自动避让端口）
loganalysis up --version v1.2.3

# 显式关闭自动避让
loganalysis up --no-auto-port
```

### 2) `down`

用途：停止当前栈并移除 orphan 容器。

关键参数：

- `--remove-volumes`：同时删除 compose 相关 volumes（数据会丢失）

示例：

```bash
# 保留数据，停止服务
loganalysis down

# 连同 volumes 一起删除
loganalysis down --remove-volumes
```

### 3) `status`

用途：查看当前栈容器状态（等价于 compose `ps`）。

示例：

```bash
loganalysis status
```

### 4) `logs`

用途：查看容器日志，支持追踪输出。

关键参数：

- `[service]`：可选服务名，如 `frontend`、`backend`、`postgres`、`redis`、`rabbitmq`、`elasticsearch`、`kibana`、`minio`
- `-f`：持续跟随日志
- `--tail N`：显示最近 N 行（默认 `200`）

示例：

```bash
# 查看 backend 最近 300 行日志
loganalysis logs backend --tail 300

# 持续跟踪 backend
loganalysis logs backend -f

# 查看所有服务的最近日志
loganalysis logs --tail 100
```

### 5) `doctor`

用途：环境预检，帮助定位“启动失败/连通性失败/端口冲突”问题。

会检查：

- `docker` 命令是否存在
- `docker compose` 是否可用
- Docker daemon 是否运行
- 当前 `image_registry` 对应仓库连通性（例如 `docker.io:443`）
- 配置端口是否被占用
- Docker 磁盘统计是否可读取
- 数据目录剩余磁盘空间（低于 5GB 会报 FAIL）

示例：

```bash
loganalysis doctor
```

### 6) `config`

用途：管理 CLI 配置。

子命令：

- `loganalysis config list`：打印全部配置 JSON
- `loganalysis config get <key>`：读取单项配置
- `loganalysis config set <key> <value>`：更新配置
- `loganalysis config path`：输出配置文件路径

示例：

```bash
# 查看配置文件路径
loganalysis config path

# 修改镜像仓库前缀（示例：Docker Hub）
loganalysis config set image_registry docker.io/<dockerhub_user>

# 修改前端端口
loganalysis config set ports.frontend 13000

# 关闭鉴权（本地开发常用）
loganalysis config set auth.enabled false
```

### 7) `auth`

用途：配置管理员账号（单管理员，无注册）。

子命令：

- `loganalysis auth set-admin --username <name>`：交互设置管理员密码并保存 BCrypt hash
- `loganalysis auth passwd`：交互改密；若 backend 正在运行会自动重建 backend 立即生效
- `loganalysis auth show`：脱敏展示当前鉴权配置

示例：

```bash
loganalysis auth set-admin --username admin
loganalysis auth show
loganalysis auth passwd
```

说明：

- `up` 时若鉴权启用但管理员凭据缺失：TTY 会进入交互初始化；非 TTY 会提示先执行 `auth set-admin`
- `auth passwd` 仅写入密码 hash，不会保存明文密码
- `auth passwd` 会轮换 JWT secret，已登录 token 会失效
- 若已执行 `loganalysis config set auth.enabled false`，则无需再配置管理员账号

### 8) `upgrade`

用途：升级运行栈（必要时回滚），并尝试自更新 CLI 二进制。

关键参数：

- `--to vX.Y.Z|latest`：目标版本（默认 `latest`）
- `--allow-major`：允许跨主版本升级（例如 `v1.x -> v2.x`）
- `--force`：当本地版本与目标版本一致时，仍强制拉取镜像并重启
- `--auto-port`：兼容参数，行为同默认值（自动检测并避让端口冲突）
- `--no-auto-port`：关闭升级过程中的自动端口避让

示例：

```bash
# 升级到最新 release（自动解析）
loganalysis upgrade --to latest

# 升级到指定版本
loganalysis upgrade --to v1.2.3

# 允许主版本升级
loganalysis upgrade --to v2.0.0 --allow-major

# 同版本强制重拉并重启
loganalysis upgrade --to latest --force

# 若你希望严格按固定端口升级（不自动避让）
loganalysis upgrade --to v1.2.3 --no-auto-port
```

说明：

- 默认会阻止主版本变更，避免误升级。
- 当本地栈版本与目标版本一致时，默认跳过容器重拉并提示可使用 `--force`；若 CLI 二进制版本落后，仍会尝试自更新。
- 升级失败会尝试回滚到旧版本。

### 9) `uninstall`

用途：卸载 CLI 与运行态文件，可选彻底清理数据。

关键参数：

- `--purge-data`：删除 `~/.loganalysis`（包括配置、状态和数据）
- `--keep-cli`：保留 `loganalysis` CLI 二进制，只清理运行态文件

示例：

```bash
# 卸载 CLI，并清理 runtime/state，保留配置和数据
loganalysis uninstall

# 只清理 runtime/state，保留 CLI、配置和数据
loganalysis uninstall --keep-cli

# 彻底清理（不可恢复）
loganalysis uninstall --purge-data
```

### 10) `version`

用途：输出 CLI 版本、commit、构建时间。

示例：

```bash
loganalysis version
```

### 11) `help`

用途：查看命令帮助。

示例：

```bash
loganalysis help
loganalysis --help
loganalysis -h
```

## `config` 支持的全部 key

### 基础项

- `project_name`：compose project 名（容器名前缀）
- `default_version`：默认镜像 tag
- `image_registry`：镜像仓库前缀
- `backend_image`：后端完整镜像名（设置后优先于 `image_registry`）
- `frontend_image`：前端完整镜像名（设置后优先于 `image_registry`）
- `release_repo`：升级与自更新使用的 GitHub 仓库（形如 `owner/repo`）
- `data_dir`：运行数据目录
- `auth.enabled`：是否启用鉴权（`true/false`）
- `auth.admin_username`：管理员用户名
- `auth.admin_password_hash`：管理员密码 BCrypt hash
- `auth.jwt_secret`：JWT 签名密钥（可留空，后端会自动生成临时密钥）
- `auth.jwt_ttl_hours`：JWT 过期时间（小时）

### 端口项

- `ports.frontend`
- `ports.backend`
- `ports.postgres`
- `ports.redis`
- `ports.rabbitmq`
- `ports.rabbitmq_management`
- `ports.elasticsearch`
- `ports.elasticsearch_transport`
- `ports.kibana`
- `ports.minio_api`
- `ports.minio_console`

示例（批量调整常见端口）：

```bash
loganalysis config set ports.frontend 13000
loganalysis config set ports.backend 18080
loganalysis config set ports.postgres 15432
loganalysis config set ports.redis 16379
```

## 非 CLI 部署的鉴权注入（可选）

若你不通过 CLI 部署，也可以在运行环境直接注入以下变量：

```bash
AUTH_ENABLED=true
AUTH_ADMIN_USERNAME=admin
AUTH_ADMIN_PASSWORD_HASH=$2y$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
# 或者直接使用明文（开发环境更方便）
# AUTH_ADMIN_PASSWORD=admin
# AUTH_JWT_SECRET 可选；留空时后端会在启动时自动生成进程内临时密钥
# AUTH_JWT_SECRET=replace-with-random-secret
AUTH_JWT_TTL_HOURS=24
```

说明：

- `AUTH_ADMIN_PASSWORD_HASH` 必须是 BCrypt hash（不是明文）
- 也支持 `AUTH_ADMIN_PASSWORD` 明文（推荐仅开发环境使用）
- 若同时配置 `AUTH_ADMIN_PASSWORD_HASH` 与 `AUTH_ADMIN_PASSWORD`，优先使用 hash
- 若 `AUTH_ENABLED=false`，后端不会要求登录
- 未设置 `AUTH_JWT_SECRET` 时，后端会自动生成临时密钥；后端重启后旧 token 会失效
- 生产环境建议显式设置固定 `AUTH_JWT_SECRET`

## 常见运维操作示例

### 场景 1：本机已有 3000/8080，被占用

```bash
loganalysis up --version v1.2.3
loganalysis status
loganalysis config get ports.frontend
loganalysis config get ports.backend
```

### 场景 2：排查后端启动失败

```bash
loganalysis doctor
loganalysis logs backend --tail 300
loganalysis logs backend -f
```

### 场景 3：升级并验证

```bash
loganalysis upgrade --to v1.2.3
loganalysis status
loganalysis version
```

## 运行时文件与目录

CLI 默认使用 `~/.loganalysis`：

- 配置：`~/.loganalysis/config.json`
- 状态：`~/.loganalysis/state.json`
- 运行时 Compose：`~/.loganalysis/runtime/compose.yaml`
- 升级回滚备份：`~/.loganalysis/runtime/compose.backup.yaml`
- 默认数据目录：`~/.loganalysis/data`

## 从源码运行（开发者）

### 开发环境要求（本地与 CI 对齐）

- JDK：`21.x`（必须，CI 使用 `actions/setup-java@v4` + `java-version: 21`）
- Maven：`3.9+`
- Node.js：`20+`（前端）
- Go：`1.22+`（CLI）

建议先检查：

```bash
java -version
mvn -version
node -v
go version
```

若本机装有多个 JDK，先切到 Java 21 再执行 Maven（macOS 示例）：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
java -version
```

### CLI

```bash
cd cli
go test ./...
go build ./cmd/loganalysis
./loganalysis version
```

### 后端

```bash
cd loganalysis
export JAVA_HOME=$(/usr/libexec/java_home -v 21)  # macOS 可选
mvn -v
mvn test
mvn spring-boot:run
```

30 分钟快速跑通（新同学推荐）：

```bash
cd loganalysis
export JAVA_HOME=$(/usr/libexec/java_home -v 21)  # macOS 可选
mvn -v
mvn test
mvn spring-boot:run
```

### 前端

```bash
cd loganalysis_frontend
npm install
npm run dev
```

## FAQ

### 1) `loganalysis` 命令找不到

- 重开终端（PATH 变更通常在新会话生效）
- Windows 可直接执行：`$HOME\.local\bin\loganalysis.exe`
- Linux/macOS 可直接执行：`~/.local/bin/loganalysis`

### 2) `docker daemon is not ready`

- 确认 Docker Desktop 已启动并为 Running
- 执行：`loganalysis doctor`

### 3) 端口冲突（3000/8080 等）

- 默认自动端口避让（`--auto-port` 为兼容参数）
- 需要关闭时使用 `--no-auto-port`
- 或手工改端口：

```bash
loganalysis config set ports.frontend 13000
loganalysis config set ports.backend 18080
```

### 4) 登录接口返回“鉴权未启用”

- 确认运行时环境变量已注入 `AUTH_ENABLED=true`
- 若使用 IDE 启动，请优先在启动配置 `env` 中显式设置上述 `AUTH_*` 变量
- 修改环境变量后需彻底重启后端进程

### 5) 配置 `AUTH_ENABLED=false` 但前端仍显示登录页

- 确认后端已更新到包含 `/api/v1/auth/status` 的版本
- 确认前端已更新到支持鉴权状态探测的版本
- 修改后端配置后重启后端，并强制刷新浏览器（清缓存）

### 6) GitHub/镜像仓库网络波动导致下载失败

- 先执行 `loganalysis doctor` 查看 `registry connectivity`
- 重试安装/升级命令
- 必要时切换网络或代理后重试

### 7) Windows 下报 `irm 不是命令` 或 `网络错误`

- `irm` 需要在 PowerShell(64位) 中执行，不是 `cmd`

### 8) Elasticsearch 启动失败并显示 `exit code 137`

`137` 通常表示容器进程被 Docker Desktop / WSL2 因内存不足杀掉。处理方式：

- 在 Docker Desktop Settings -> Resources 中将 Memory 调高，建议至少 6GB
- 关闭其他占用内存较高的容器或应用后重新执行 `loganalysis up`
- 如果已反复失败，可先执行 `loganalysis down`，确认不需要保留 ES 数据时再执行 `loganalysis down --remove-volumes` 后重试

### 9) `mvn test` 失败并提示 Java 版本不匹配

- 本项目已固定 JDK 版本为 `21`，若使用 `17/24` 等版本会被 Maven Enforcer 拦截
- 先执行 `java -version` 和 `mvn -v`，确认当前 Maven 使用的 Java Home
- 切换到 Java 21 后重试（macOS）：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn -v
mvn test
```
