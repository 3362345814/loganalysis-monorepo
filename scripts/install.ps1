param(
  [string]$Repo = $env:LOGANALYSIS_REPO,
  [string]$Version = $env:LOGANALYSIS_VERSION,
  [string]$InstallDir = $env:LOGANALYSIS_INSTALL_DIR
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

if ($Version -eq 'latest') {
  $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/latest"
  if (-not $release.tag_name) {
    throw 'failed to resolve latest release tag'
  }
  $Version = $release.tag_name
}

$archRaw = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString().ToLowerInvariant()
switch ($archRaw) {
  'x64' { $arch = 'amd64' }
  'arm64' { $arch = 'arm64' }
  default { throw "unsupported architecture: $archRaw" }
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
