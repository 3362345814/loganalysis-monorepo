# LogAnalysis Monorepo

一个面向生产场景的日志采集、检索、聚合、智能分析与告警平台。\
本仓库包含：

- 后端服务（Spring Boot）
- 前端控制台（Vue 3 + Element Plus）
- 跨平台运维 CLI（Go，基于 Docker Compose 一键部署）

## 核心功能

- 项目管理：多项目隔离，按项目管理采集源和日志数据
- 日志采集：支持本地/远程日志源采集、启动停止采集器、采集状态查询
- 日志查询：按条件检索、分页查询、Trace 维度追踪
- 日志聚合：聚合组管理、上下文查看、未分析项管理
- 智能分析：对聚合日志进行根因分析与建议生成（支持 LLM 配置）
- 告警中心：规则管理、告警记录流转、趋势统计、级别分布
- 通知渠道：钉钉 / 飞书 / 企业微信通知配置与测试
- 一键部署运维：`up/down/status/logs/doctor/upgrade/uninstall`

## 架构组件

- `frontend`：Web 控制台
- `backend`：API 服务（默认 `8080`）
- `postgres`：关系型存储
- `redis`：缓存/状态
- `rabbitmq`：消息队列
- `elasticsearch`、`kibana`、`minio`：`full` profile 下启用

CLI 提供 3 个部署 profile：

- `db`：仅数据库依赖（Postgres + Redis）
- `minimal`：核心可用栈（前后端 + Postgres + Redis + RabbitMQ）
- `full`：完整栈（在 `minimal` 基础上增加 ES/Kibana/MinIO）

## 快速开始

### 0) 前置条件

- Docker Desktop / Docker Engine（需支持 `docker compose`）
- 可访问 GitHub 与 GHCR

先检查：

```bash
docker --version
docker compose version
```

### 1) 安装 CLI（方式一：命令行拉取，一键安装）

这是推荐方式，适合绝大多数用户。

#### macOS / Linux

```bash
curl -fsSL "https://raw.githubusercontent.com/3362345814/loganalysis-monorepo/v0.2.1/scripts/install.sh" | sh
```

#### Windows PowerShell

```powershell
irm "https://raw.githubusercontent.com/3362345814/loganalysis-monorepo/v0.2.1/scripts/install.ps1" | iex
```

安装后验证：

```bash
loganalysis version
```

如果终端提示找不到命令，重开终端后再试。

### 2) 安装 CLI（方式二：从 Release 直接下载后运行）

适合内网环境、手动分发、或不希望修改 PATH 的场景。

1. 打开 Release 页面下载对应系统文件：
   - `loganalysis-linux-amd64` / `loganalysis-linux-arm64`
   - `loganalysis-darwin-amd64` / `loganalysis-darwin-arm64`
   - `loganalysis-windows-amd64.exe` / `loganalysis-windows-arm64.exe`
2. （可选）下载 `checksums.txt` 并校验
3. 直接运行二进制

示例（Windows PowerShell）：

```powershell
$bin = "$env:TEMP\loganalysis-windows-amd64.exe"
curl.exe -L --fail -o $bin "https://github.com/3362345814/loganalysis-monorepo/releases/download/v0.2.1/loganalysis-windows-amd64.exe"
& $bin version
& $bin doctor
```

示例（Linux/macOS）：

```bash
curl -fL -o /tmp/loganalysis "https://github.com/3362345814/loganalysis-monorepo/releases/download/v0.2.1/loganalysis-linux-amd64"
chmod +x /tmp/loganalysis
/tmp/loganalysis version
/tmp/loganalysis doctor
```

### 3) 启动系统

```bash
loganalysis doctor
loganalysis up --profile minimal --version v0.2.1 --auto-port
loganalysis status
```

默认访问地址：

- 前端：<http://localhost:3000>
- 后端：<http://localhost:8080>

## 常用 CLI 命令

```bash
loganalysis up --profile full --version v0.2.1 --auto-port
loganalysis down
loganalysis down --remove-volumes
loganalysis status
loganalysis logs backend -f
loganalysis doctor
loganalysis config list
loganalysis config get release_repo
loganalysis config set release_repo 3362345814/loganalysis-monorepo
loganalysis upgrade --to v0.2.1
loganalysis uninstall
loganalysis uninstall --purge-data
```

## 关键配置说明

CLI 运行时文件目录：

- 配置：`~/.loganalysis/config.json`
- 状态：`~/.loganalysis/state.json`
- 运行时 Compose：`~/.loganalysis/runtime/compose.yaml`

常用配置项：

- `release_repo`：CLI 升级/自更新使用的 GitHub Release 仓库
- `image_registry`：镜像仓库前缀（默认 `ghcr.io/3362345814`）
- `backend_image` / `frontend_image`：可直接指定完整镜像名覆盖默认值
- `ports.*`：各组件端口

## 从源码运行（开发者）

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
mvn spring-boot:run
```

### 前端

```bash
cd loganalysis_frontend
npm install
npm run dev
```

## 常见问题（FAQ）

### 1) `loganalysis` 命令找不到

- 重开终端（PATH 变更通常在新会话生效）
- Windows 可直接执行：`$HOME\.local\bin\loganalysis.exe`
- Linux/macOS 可直接执行：`~/.local/bin/loganalysis`

### 2) `docker daemon is not ready`

- 确认 Docker Desktop 已启动并处于 Running
- 重新执行：`loganalysis doctor`

### 3) 端口冲突（3000/8080 等已占用）

- 启动时加 `--auto-port` 自动避让
- 或手工改端口：

```bash
loganalysis config set ports.frontend 13000
loganalysis config set ports.backend 18080
```

### 4) GitHub/GHCR 网络波动导致下载失败

- 重试安装命令（很多情况下可直接恢复）
- 先执行 `loganalysis doctor` 看 `registry connectivity`
- 必要时切换网络或代理后重试

### 5) Windows 下报 `Null 值表达式`、`irm 不是命令`

- `irm` 需要在 PowerShell 中执行，不是 `cmd`

