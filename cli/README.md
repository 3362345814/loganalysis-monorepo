# loganalysis CLI

Cross-platform CLI for deploying and operating the LogAnalysis stack with Docker Compose.

## Build

```bash
cd cli
go build ./cmd/loganalysis
```

## Commands

- `loganalysis up --profile full --version v1.2.0 --auto-port`
- `loganalysis down --remove-volumes`
- `loganalysis status`
- `loganalysis logs backend -f`
- `loganalysis doctor`
- `loganalysis config set default_profile minimal`
- `loganalysis config get image_registry`
- `loganalysis upgrade --to v1.3.0`
- `loganalysis uninstall --purge-data`
- `loganalysis version`

## Runtime files

- Config: `~/.loganalysis/config.json`
- State: `~/.loganalysis/state.json`
- Active compose: `~/.loganalysis/runtime/compose.yaml`

## Auto port avoidance

Use `--auto-port` during `up` to automatically remap occupied host ports for the selected profile and persist the new values to `~/.loganalysis/config.json`.

## Image naming convention

By default, the CLI pulls images from:

- `ghcr.io/<owner>/loganalysis-backend:<tag>`
- `ghcr.io/<owner>/loganalysis-frontend:<tag>`

You can override with `loganalysis config set image_registry <registry-prefix>` or explicit image values via `backend_image`/`frontend_image`.
