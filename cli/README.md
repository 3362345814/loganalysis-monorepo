# loganalysis CLI

Cross-platform CLI for deploying and operating the LogAnalysis stack with Docker Compose.

## Build

```bash
cd cli
go build ./cmd/loganalysis
```

## Commands

- `loganalysis up --version v1.2.0`
- `loganalysis down --remove-volumes`
- `loganalysis status`
- `loganalysis logs backend -f`
- `loganalysis doctor`
- `loganalysis config get image_registry`
- `loganalysis auth set-admin --username admin`
- `loganalysis auth passwd`
- `loganalysis auth show`
- `loganalysis upgrade --to v1.3.0`
- `loganalysis uninstall --purge-data`
- `loganalysis version`

## Runtime files

- Config: `~/.loganalysis/config.json`
- State: `~/.loganalysis/state.json`
- Active compose: `~/.loganalysis/runtime/compose.yaml`

## Auto port avoidance

`loganalysis up` and `loganalysis upgrade` now enable auto-port avoidance by default and persist remapped host ports to `~/.loganalysis/config.json`.

- `--auto-port`: compatibility flag, same behavior as default
- `--no-auto-port`: disable automatic remap for this run

## Auth bootstrap

`up` requires admin credentials when auth is enabled (enabled by default).

- Disable auth for local/dev usage: `loganalysis config set auth.enabled false`
- First run in TTY: `up` enters interactive setup if missing credentials.
- Non-TTY environments: run `loganalysis auth set-admin --username <name>` first.
- `loganalysis auth passwd` updates password hash and restarts backend automatically when backend is running.

## Image naming convention

By default, the CLI pulls images from:

- `ghcr.io/<owner>/loganalysis-backend:<tag>`
- `ghcr.io/<owner>/loganalysis-frontend:<tag>`

You can override with `loganalysis config set image_registry <registry-prefix>` or explicit image values via `backend_image`/`frontend_image`.
