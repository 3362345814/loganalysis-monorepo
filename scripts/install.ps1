param(
  [string]$Repo = $env:LOGANALYSIS_REPO,
  [string]$Version = $env:LOGANALYSIS_VERSION,
  [string]$InstallDir = $env:LOGANALYSIS_INSTALL_DIR,
  [string]$Arch = $env:LOGANALYSIS_ARCH
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Repo)) {
  $Repo = '3362345814/loganalysis-monorepo'
}
if ([string]::IsNullOrWhiteSpace($Version)) {
  $Version = 'latest'
}
if ([string]::IsNullOrWhiteSpace($InstallDir)) {
  $InstallDir = Join-Path $HOME '.local\bin'
}

function Resolve-LatestVersion([string]$repo) {
  $tag = $null

  # Prefer github.com redirect so environments that block/intercept
  # api.github.com can still resolve latest release tag.
  try {
    $resp = Invoke-WebRequest -Uri "https://github.com/$repo/releases/latest" -MaximumRedirection 5
    $finalUrl = $resp.BaseResponse.ResponseUri.AbsoluteUri
    if ($finalUrl -match '/tag/([^/?#]+)') {
      $tag = $Matches[1]
    }
  }
  catch {
  }

  if (-not $tag) {
    try {
      $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/latest"
      if ($release.tag_name) {
        $tag = $release.tag_name
      }
    }
    catch {
    }
  }

  if (-not $tag) {
    throw 'failed to resolve latest release tag; set LOGANALYSIS_VERSION=vX.Y.Z to bypass auto detection'
  }

  return $tag
}

if ($Version -eq 'latest') {
  $Version = Resolve-LatestVersion $Repo
}

function Resolve-Arch([string]$raw) {
  if ([string]::IsNullOrWhiteSpace($raw)) {
    return $null
  }
  switch -Regex ($raw.Trim().ToLowerInvariant()) {
    '^(x64|amd64)$' { return 'amd64' }
    '^arm64$' { return 'arm64' }
    default { return $null }
  }
}

if (-not [string]::IsNullOrWhiteSpace($Arch)) {
  $arch = Resolve-Arch $Arch
  if (-not $arch) {
    throw "unsupported architecture override: $Arch (expected: amd64|arm64)"
  }
} else {
  $archCandidates = New-Object System.Collections.Generic.List[string]

  try {
    $runtimeArch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture
    if ($null -ne $runtimeArch) {
      $archCandidates.Add($runtimeArch.ToString())
    }
  } catch {
  }

  if (-not [string]::IsNullOrWhiteSpace($env:PROCESSOR_ARCHITEW6432)) {
    $archCandidates.Add($env:PROCESSOR_ARCHITEW6432)
  }
  if (-not [string]::IsNullOrWhiteSpace($env:PROCESSOR_ARCHITECTURE)) {
    $archCandidates.Add($env:PROCESSOR_ARCHITECTURE)
  }

  foreach ($candidate in $archCandidates) {
    $resolved = Resolve-Arch $candidate
    if ($resolved) {
      $arch = $resolved
      break
    }
  }

  if (-not $arch) {
    $joined = ($archCandidates -join ',')
    throw "failed to detect supported architecture from candidates: [$joined] (set LOGANALYSIS_ARCH=amd64 or arm64 to override)"
  }
}

$asset = "loganalysis-windows-$arch.exe"
$baseUrl = "https://github.com/$Repo/releases/download/$Version"

$tempDir = Join-Path $env:TEMP ("loganalysis-install-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempDir | Out-Null

try {
  $exePath = Join-Path $tempDir $asset
  $checksumsPath = Join-Path $tempDir 'checksums.txt'

  Invoke-WebRequest -Uri "$baseUrl/$asset" -OutFile $exePath
  Invoke-WebRequest -Uri "$baseUrl/checksums.txt" -OutFile $checksumsPath

  $escapedAsset = [Regex]::Escape($asset)
  $checksumMatch = Select-String -Path $checksumsPath -Pattern "^\s*([a-fA-F0-9]{64})\s+$escapedAsset$" | Select-Object -First 1
  if (-not $checksumMatch) {
    throw "checksum entry not found for $asset in checksums.txt (repo=$Repo version=$Version)"
  }

  $expected = $checksumMatch.Matches[0].Groups[1].Value

  $actual = (Get-FileHash -Path $exePath -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($expected.ToLowerInvariant() -ne $actual) {
    throw "checksum mismatch for $asset"
  }

  if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
  }

  $target = Join-Path $InstallDir 'loganalysis.exe'
  Copy-Item -Path $exePath -Destination $target -Force

  $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
  if (-not $userPath.Split(';').Contains($InstallDir)) {
    [Environment]::SetEnvironmentVariable('Path', "$userPath;$InstallDir", 'User')
    Write-Host "PATH updated for current user. restart terminal to apply."
  }

  Write-Host "loganalysis installed to $target"
}
finally {
  Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
}
