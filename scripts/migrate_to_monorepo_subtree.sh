#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   bash scripts/migrate_to_monorepo_subtree.sh \
#     --mono-dir /Users/xxx/loganalysis-monorepo \
#     --backend-dir /Users/xxx/project/loganalysis \
#     --frontend-dir /Users/xxx/project/loganalysis_frontend \
#     --work-dir /Users/xxx/project \
#     --new-remote https://github.com/<owner>/<repo>.git
#
# Notes:
# - This script preserves backend/frontend git history using git subtree.
# - CLI/scripts/.github are copied from --work-dir as a new commit.

MONO_DIR="/Users/cityseason/Documents/graduation_project/loganalysis-monorepo"
BACKEND_DIR="/Users/cityseason/Documents/graduation_project/project/loganalysis"
FRONTEND_DIR="/Users/cityseason/Documents/graduation_project/project/loganalysis_frontend"
WORK_DIR="/Users/cityseason/Documents/graduation_project/project"
NEW_REMOTE="https://github.com/3362345814/loganalysis-monorepo.git"
SKIP_PUSH="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mono-dir)
      MONO_DIR="$2"
      shift 2
      ;;
    --backend-dir)
      BACKEND_DIR="$2"
      shift 2
      ;;
    --frontend-dir)
      FRONTEND_DIR="$2"
      shift 2
      ;;
    --work-dir)
      WORK_DIR="$2"
      shift 2
      ;;
    --new-remote)
      NEW_REMOTE="$2"
      shift 2
      ;;
    --skip-push)
      SKIP_PUSH="true"
      shift
      ;;
    -h|--help)
      sed -n '1,38p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown arg: $1" >&2
      exit 1
      ;;
  esac
done

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_cmd git
require_cmd rsync
require_cmd sed

detect_repo_branch() {
  local repo="$1"

  if git -C "$repo" show-ref --verify --quiet refs/remotes/origin/main; then
    echo "main"
    return 0
  fi
  if git -C "$repo" show-ref --verify --quiet refs/remotes/origin/master; then
    echo "master"
    return 0
  fi

  local local_branch=""
  local_branch="$(git -C "$repo" symbolic-ref --short HEAD 2>/dev/null || true)"
  if [[ -n "$local_branch" ]]; then
    echo "$local_branch"
    return 0
  fi

  local first_branch=""
  first_branch="$(git -C "$repo" for-each-ref --format='%(refname:short)' refs/heads | head -n 1)"
  if [[ -n "$first_branch" ]]; then
    echo "$first_branch"
    return 0
  fi

  return 1
}

if [[ ! -d "$BACKEND_DIR/.git" ]]; then
  echo "Backend repo not found: $BACKEND_DIR" >&2
  exit 1
fi
if [[ ! -d "$FRONTEND_DIR/.git" ]]; then
  echo "Frontend repo not found: $FRONTEND_DIR" >&2
  exit 1
fi
if [[ ! -d "$WORK_DIR" ]]; then
  echo "Work dir not found: $WORK_DIR" >&2
  exit 1
fi

if [[ -d "$MONO_DIR/.git" ]]; then
  echo "Target monorepo already exists: $MONO_DIR" >&2
  echo "Please remove it or choose another --mono-dir." >&2
  exit 1
fi

echo "==> Create monorepo at: $MONO_DIR"
mkdir -p "$MONO_DIR"
cd "$MONO_DIR"
git init -b main
git commit --allow-empty -m "chore: initialize monorepo"

BACKEND_BRANCH="$(detect_repo_branch "$BACKEND_DIR" || true)"
FRONTEND_BRANCH="$(detect_repo_branch "$FRONTEND_DIR" || true)"

if [[ -z "$BACKEND_BRANCH" ]]; then
  echo "Failed to detect backend branch from $BACKEND_DIR" >&2
  exit 1
fi
if [[ -z "$FRONTEND_BRANCH" ]]; then
  echo "Failed to detect frontend branch from $FRONTEND_DIR" >&2
  exit 1
fi

echo "==> Backend default branch: $BACKEND_BRANCH"
echo "==> Frontend default branch: $FRONTEND_BRANCH"

echo "==> Import backend history into loganalysis/"
git remote add backend "$BACKEND_DIR"
git fetch backend
git subtree add --prefix=loganalysis backend "$BACKEND_BRANCH"

echo "==> Import frontend history into loganalysis_frontend/"
git remote add frontend "$FRONTEND_DIR"
git fetch frontend
git subtree add --prefix=loganalysis_frontend frontend "$FRONTEND_BRANCH"

echo "==> Copy cli/scripts/.github from work dir"
if [[ -d "$WORK_DIR/cli" ]]; then
  rsync -a --exclude '.git' "$WORK_DIR/cli/" "$MONO_DIR/cli/"
fi
if [[ -d "$WORK_DIR/scripts" ]]; then
  rsync -a --exclude '.git' "$WORK_DIR/scripts/" "$MONO_DIR/scripts/"
fi
if [[ -d "$WORK_DIR/.github" ]]; then
  rsync -a --exclude '.git' "$WORK_DIR/.github/" "$MONO_DIR/.github/"
fi

git add .
if ! git diff --cached --quiet; then
  git commit -m "feat: add cli, installer scripts and release workflows"
else
  echo "No additional files to commit from work dir."
fi

echo "==> Add origin and push"
if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$NEW_REMOTE"
else
  git remote add origin "$NEW_REMOTE"
fi

if [[ "$SKIP_PUSH" == "true" ]]; then
  echo "Skip push enabled."
  echo "Run manually:"
  echo "  cd $MONO_DIR"
  echo "  git push -u origin main"
else
  git push -u origin main
fi

echo "==> Done"
echo "Monorepo path: $MONO_DIR"
echo "Next:"
echo "  cd $MONO_DIR"
echo "  git tag v0.1.0 && git push origin v0.1.0"
