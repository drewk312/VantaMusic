<# 
.SYNOPSIS
  Smart C: drive cleaner/mover for offloading safe bulk data to a secondary HDD.

.DESCRIPTION
  Defaults to dry-run mode. With -Apply, it:
    - Cleans known rebuildable caches/temp folders.
    - Moves known large, safe-to-relocate folders to D:\MovedFromC with junctions.
    - Skips Windows, Program Files, ProgramData, live editor profiles, and unsafe paths unless
      they are explicitly listed as managed targets.
    - Writes logs and a manifest under D:\MovedFromC\.smart-transfer.

  This script intentionally does not move C:\Windows, Program Files, ProgramData wholesale,
  pagefile.sys, user registry hives, OneDrive roots, or arbitrary AppData folders.

.EXAMPLE
  .\scripts\smart-hdd-transfer.ps1
  Shows what it would do.

.EXAMPLE
  .\scripts\smart-hdd-transfer.ps1 -Apply
  Performs safe cache cleanup and managed folder moves.

.EXAMPLE
  .\scripts\smart-hdd-transfer.ps1 -Apply -IncludeCursorWhenClosed
  Also moves Cursor user state if Cursor is not running.

.EXAMPLE
  .\scripts\smart-hdd-transfer.ps1 -ReportOnly -TopFiles
  Prints size report and largest-file candidates only.
#>

[CmdletBinding(SupportsShouldProcess = $true)]
param(
  [string]$SourceDrive = "C:",
  [string]$DestinationRoot = "D:\MovedFromC",
  [switch]$Apply,
  [switch]$ReportOnly,
  [switch]$TopFiles,
  [switch]$IncludeCursorWhenClosed,
  [switch]$SkipCacheClean,
  [switch]$InstallScheduledTask,
  [switch]$UninstallScheduledTask,
  [string]$ScheduledTaskName = "Smart HDD Transfer",
  [string]$ScheduleTime = "03:00",
  [double]$MinimumMoveGB = 0.25,
  [int]$TempOlderThanMinutes = 30
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$UserProfilePath = [Environment]::GetFolderPath("UserProfile")
$LocalAppData = [Environment]::GetFolderPath("LocalApplicationData")
$RoamingAppData = [Environment]::GetFolderPath("ApplicationData")
$ProgramData = [Environment]::GetFolderPath("CommonApplicationData")

$StateRoot = Join-Path $DestinationRoot ".smart-transfer"
$LogPath = Join-Path $StateRoot ("run-{0:yyyyMMdd-HHmmss}.log" -f (Get-Date))
$ManifestPath = Join-Path $StateRoot "manifest.json"

function Initialize-State {
  if ($Apply) {
    New-Item -ItemType Directory -Force -Path $StateRoot | Out-Null
    "Started {0:u}" -f (Get-Date) | Out-File -FilePath $LogPath -Encoding utf8
  }
}

function Write-Log {
  param([string]$Message)
  $line = "[{0:HH:mm:ss}] {1}" -f (Get-Date), $Message
  Write-Host $line
  if ($Apply) {
    Add-Content -LiteralPath $LogPath -Value $line
  }
}

function Get-DriveFreeGB {
  param([string]$DriveName)
  $name = $DriveName.TrimEnd(":")
  $drive = Get-PSDrive -Name $name -PSProvider FileSystem
  [math]::Round($drive.Free / 1GB, 2)
}

function Get-PathSizeBytes {
  param([Parameter(Mandatory)][string]$Path)
  if (-not (Test-Path -LiteralPath $Path)) { return 0L }

  $sum = 0L
  Get-ChildItem -LiteralPath $Path -Force -File -Recurse -ErrorAction SilentlyContinue |
    ForEach-Object {
      try { $sum += $_.Length } catch {}
    }
  return $sum
}

function Get-PathSizeGB {
  param([string]$Path)
  [math]::Round((Get-PathSizeBytes -Path $Path) / 1GB, 2)
}

function Test-PathIsJunction {
  param([string]$Path)
  if (-not (Test-Path -LiteralPath $Path)) { return $false }
  $item = Get-Item -LiteralPath $Path -Force
  return [bool]($item.Attributes -band [IO.FileAttributes]::ReparsePoint)
}

function Test-UnderPath {
  param([string]$Path, [string]$Root)
  $fullPath = [IO.Path]::GetFullPath($Path)
  $fullRoot = [IO.Path]::GetFullPath($Root)
  return $fullPath.StartsWith($fullRoot, [StringComparison]::OrdinalIgnoreCase)
}

function Assert-SafeDeletePath {
  param([string]$Path)
  $full = [IO.Path]::GetFullPath($Path)
  $allowed = @(
    (Join-Path $UserProfilePath ""),
    "C:\tmp\",
    "C:\temp\",
    "C:\Program Files (x86)\Steam\steamapps\workshop\downloads\",
    "C:\ProgramData\NVIDIA Corporation\NVIDIA App\UpdateFramework\ota-artifacts\"
  )

  foreach ($prefix in $allowed) {
    if ($full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
      return
    }
  }

  throw "Refusing to delete unsafe path: $full"
}

function Invoke-DeletePath {
  param(
    [string]$Path,
    [string]$Reason
  )
  if (-not (Test-Path -LiteralPath $Path)) { return }
  Assert-SafeDeletePath -Path $Path
  $sizeGB = Get-PathSizeGB -Path $Path

  if (-not $Apply) {
    Write-Log ("DRY-RUN delete {0} GB: {1} ({2})" -f $sizeGB, $Path, $Reason)
    return
  }

  Write-Log ("Deleting {0} GB: {1} ({2})" -f $sizeGB, $Path, $Reason)
  Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction SilentlyContinue
}

function Invoke-CleanDirectoryContents {
  param(
    [string]$Path,
    [string]$Reason,
    [datetime]$OlderThan = [datetime]::MinValue
  )
  if (-not (Test-Path -LiteralPath $Path)) { return }
  Assert-SafeDeletePath -Path $Path
  $sizeGB = Get-PathSizeGB -Path $Path

  if (-not $Apply) {
    Write-Log ("DRY-RUN clean contents {0} GB: {1} ({2})" -f $sizeGB, $Path, $Reason)
    return
  }

  Write-Log ("Cleaning contents {0} GB: {1} ({2})" -f $sizeGB, $Path, $Reason)
  Get-ChildItem -LiteralPath $Path -Force -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -lt $OlderThan } |
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
}

function Get-RelativeMovePath {
  param([string]$Source)
  $full = [IO.Path]::GetFullPath($Source)
  $driveRoot = [IO.Path]::GetPathRoot($full)
  $withoutDrive = $full.Substring($driveRoot.Length)
  return Join-Path $DestinationRoot $withoutDrive
}

function Save-ManifestEntry {
  param(
    [string]$Source,
    [string]$Destination,
    [double]$SizeGB
  )

  if (-not $Apply) { return }

  $entries = @()
  if (Test-Path -LiteralPath $ManifestPath) {
    try { $entries = @(Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json) } catch { $entries = @() }
  }

  $entry = [PSCustomObject]@{
    source = $Source
    destination = $Destination
    sizeGB = $SizeGB
    movedAt = (Get-Date).ToString("o")
    junction = $true
  }

  $entries = @($entries | Where-Object { $_.source -ne $Source }) + $entry
  $entries | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $ManifestPath -Encoding utf8
}

function Invoke-MoveWithJunction {
  param(
    [Parameter(Mandatory)][string]$Source,
    [string]$Destination = $null,
    [string]$Reason = "managed move"
  )

  if (-not (Test-Path -LiteralPath $Source)) { return }
  if (Test-PathIsJunction -Path $Source) {
    Write-Log "Already junctioned: $Source"
    return
  }

  if (-not $Destination) { $Destination = Get-RelativeMovePath -Source $Source }
  $sizeGB = Get-PathSizeGB -Path $Source
  if ($sizeGB -lt $MinimumMoveGB) {
    Write-Log ("Skipping small folder {0} GB: {1}" -f $sizeGB, $Source)
    return
  }

  if (-not $Apply) {
    Write-Log ("DRY-RUN move+junction {0} GB: {1} -> {2} ({3})" -f $sizeGB, $Source, $Destination, $Reason)
    return
  }

  Write-Log ("Moving {0} GB: {1} -> {2} ({3})" -f $sizeGB, $Source, $Destination, $Reason)
  New-Item -ItemType Directory -Force -Path $Destination | Out-Null
  robocopy $Source $Destination /E /MOVE /COPY:DAT /DCOPY:DAT /R:0 /W:0 /XJ /MT:16 /NFL /NDL /NP /NJH /NJS | Out-Null

  $remaining = Get-PathSizeBytes -Path $Source
  if ($remaining -gt 0) {
    $remainingGB = [math]::Round($remaining / 1GB, 2)
    Write-Log "Partial move remains on source: $remainingGB GB at $Source"
    return
  }

  Remove-Item -LiteralPath $Source -Recurse -Force -ErrorAction SilentlyContinue
  if (Test-Path -LiteralPath $Source) {
    Write-Log "Could not remove source for junction: $Source"
    return
  }

  New-Item -ItemType Junction -Path $Source -Target $Destination | Out-Null
  Save-ManifestEntry -Source $Source -Destination $Destination -SizeGB $sizeGB
  Write-Log "Junction created: $Source -> $Destination"
}

function Test-ProcessRunning {
  param([string[]]$Names)
  foreach ($name in $Names) {
    if (Get-Process -Name $name -ErrorAction SilentlyContinue) { return $true }
  }
  return $false
}

function Get-ManagedMoveTargets {
  $targets = @(
    [PSCustomObject]@{
      Source = Join-Path $UserProfilePath ".android\avd"
      Reason = "Android emulator images"
      Processes = @("emulator", "qemu-system-x86_64", "adb")
    },
    [PSCustomObject]@{
      Source = Join-Path $LocalAppData "Android\Sdk"
      Reason = "Android SDK"
      Processes = @("studio64", "adb", "gradle", "java")
    },
    [PSCustomObject]@{
      Source = Join-Path $UserProfilePath ".vscode\extensions"
      Reason = "VS Code extensions"
      Processes = @("Code")
    },
    [PSCustomObject]@{
      Source = Join-Path $RoamingAppData "Code\User"
      Reason = "VS Code user state"
      Processes = @("Code")
    },
    [PSCustomObject]@{
      Source = Join-Path $UserProfilePath ".gemini\antigravity-backup"
      Reason = "Antigravity backup data"
      Processes = @("Antigravity", "Antigravity IDE")
    },
    [PSCustomObject]@{
      Source = Join-Path $UserProfilePath ".gemini\antigravity-ide"
      Reason = "Antigravity IDE state"
      Processes = @("Antigravity IDE")
    }
  )

  if ($IncludeCursorWhenClosed) {
    $targets += [PSCustomObject]@{
      Source = Join-Path $RoamingAppData "Cursor\User"
      Reason = "Cursor user state"
      Processes = @("Cursor")
    }
  }

  return $targets
}

function Invoke-CacheCleanup {
  $tempCutoff = (Get-Date).AddMinutes(-1 * $TempOlderThanMinutes)
  Invoke-CleanDirectoryContents -Path (Join-Path $LocalAppData "Temp") -Reason "stale user temp files" -OlderThan $tempCutoff

  $deleteTargets = @(
    @{ Path = Join-Path $UserProfilePath ".gradle\caches"; Reason = "Gradle cache" },
    @{ Path = Join-Path $UserProfilePath ".gradle\.tmp"; Reason = "Gradle temp" },
    @{ Path = Join-Path $UserProfilePath ".gradle\daemon"; Reason = "Gradle daemon logs/cache" },
    @{ Path = Join-Path $LocalAppData "npm-cache"; Reason = "npm cache" },
    @{ Path = Join-Path $LocalAppData "NuGet"; Reason = "NuGet cache" },
    @{ Path = Join-Path $RoamingAppData "Code\CachedExtensionVSIXs"; Reason = "VS Code cached VSIXs" },
    @{ Path = Join-Path $RoamingAppData "Code\Crashpad"; Reason = "VS Code crash dumps" },
    @{ Path = Join-Path $RoamingAppData "Code\logs"; Reason = "VS Code logs" },
    @{ Path = Join-Path $RoamingAppData "Cursor\snapshots"; Reason = "Cursor snapshots" },
    @{ Path = Join-Path $RoamingAppData "Cursor\logs"; Reason = "Cursor logs" },
    @{ Path = Join-Path $LocalAppData "Microsoft\vscode-cpptools"; Reason = "VS Code C++ tools cache" },
    @{ Path = Join-Path $LocalAppData "Google\Chrome\User Data\OptGuideOnDeviceModel"; Reason = "Chrome optimization model cache" },
    @{ Path = Join-Path $LocalAppData "Google\Chrome\User Data\OptGuideOnDeviceClassifierModel"; Reason = "Chrome optimization model cache" },
    @{ Path = Join-Path $LocalAppData "Google\Chrome\User Data\optimization_guide_model_store"; Reason = "Chrome optimization model cache" },
    @{ Path = "C:\ProgramData\NVIDIA Corporation\NVIDIA App\UpdateFramework\ota-artifacts"; Reason = "NVIDIA updater artifacts" },
    @{ Path = "C:\Program Files (x86)\Steam\steamapps\workshop\downloads"; Reason = "Steam workshop download cache" }
  )

  foreach ($target in $deleteTargets) {
    Invoke-DeletePath -Path $target.Path -Reason $target.Reason
  }
}

function Show-Report {
  Write-Log ("C: free: {0} GB" -f (Get-DriveFreeGB -DriveName "C:"))
  Write-Log ("D: free: {0} GB" -f (Get-DriveFreeGB -DriveName "D:"))

  $reportTargets = @(
    (Join-Path $UserProfilePath ".gemini"),
    (Join-Path $UserProfilePath ".android"),
    (Join-Path $LocalAppData "Android"),
    (Join-Path $UserProfilePath ".gradle"),
    (Join-Path $UserProfilePath ".vscode"),
    (Join-Path $RoamingAppData "Code"),
    (Join-Path $RoamingAppData "Cursor"),
    (Join-Path $LocalAppData "Google"),
    (Join-Path $LocalAppData "Temp"),
    (Join-Path $LocalAppData "Programs"),
    "C:\ProgramData\Microsoft\VisualStudio\Packages",
    "C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA",
    "C:\Program Files (x86)\Steam\steamapps\workshop\downloads"
  )

  $rows = foreach ($path in $reportTargets) {
    if (Test-Path -LiteralPath $path) {
      [PSCustomObject]@{
        Path = $path
        SizeGB = Get-PathSizeGB -Path $path
        Junction = Test-PathIsJunction -Path $path
      }
    }
  }

  $rows | Sort-Object SizeGB -Descending | Format-Table -AutoSize
}

function Show-TopFiles {
  $roots = @(
    (Join-Path $UserProfilePath ".gemini"),
    (Join-Path $RoamingAppData "Cursor"),
    (Join-Path $RoamingAppData "Code"),
    (Join-Path $LocalAppData "Programs"),
    "C:\ProgramData",
    "C:\Program Files",
    "C:\Program Files (x86)"
  )

  foreach ($root in $roots) {
    if (-not (Test-Path -LiteralPath $root)) { continue }
    Write-Host ""
    Write-Host "Largest files under $root"
    Get-ChildItem -LiteralPath $root -Force -File -Recurse -ErrorAction SilentlyContinue |
      Sort-Object Length -Descending |
      Select-Object -First 15 FullName, @{Name = "SizeGB"; Expression = { [math]::Round($_.Length / 1GB, 2) } } |
      Format-Table -AutoSize
  }
}

function Install-SmartTransferTask {
  $scriptPath = $PSCommandPath
  if (-not $scriptPath) { throw "Cannot determine script path for scheduled task." }

  $arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`" -Apply -IncludeCursorWhenClosed"
  $action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $arguments
  $trigger = New-ScheduledTaskTrigger -Daily -At $ScheduleTime
  $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel LeastPrivilege
  $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Hours 4)

  Register-ScheduledTask -TaskName $ScheduledTaskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
  Write-Log "Installed scheduled task '$ScheduledTaskName' for daily run at $ScheduleTime."
}

function Uninstall-SmartTransferTask {
  $task = Get-ScheduledTask -TaskName $ScheduledTaskName -ErrorAction SilentlyContinue
  if ($task) {
    Unregister-ScheduledTask -TaskName $ScheduledTaskName -Confirm:$false
    Write-Log "Uninstalled scheduled task '$ScheduledTaskName'."
  } else {
    Write-Log "Scheduled task '$ScheduledTaskName' is not installed."
  }
}

Initialize-State
Write-Log "Mode: $(if ($Apply) { 'APPLY' } else { 'DRY-RUN' })"

if ($InstallScheduledTask) {
  Install-SmartTransferTask
  exit 0
}

if ($UninstallScheduledTask) {
  Uninstall-SmartTransferTask
  exit 0
}

if (-not (Test-Path -LiteralPath $DestinationRoot)) {
  if ($Apply) {
    New-Item -ItemType Directory -Force -Path $DestinationRoot | Out-Null
  } else {
    Write-Log "DRY-RUN would create destination: $DestinationRoot"
  }
}

Show-Report
if ($TopFiles) { Show-TopFiles }

if ($ReportOnly) {
  Write-Log "ReportOnly set; no cleanup or moves attempted."
  exit 0
}

if (-not $SkipCacheClean) {
  Invoke-CacheCleanup
}

foreach ($target in Get-ManagedMoveTargets) {
  if (-not (Test-Path -LiteralPath $target.Source)) { continue }

  if (Test-ProcessRunning -Names $target.Processes) {
    Write-Log ("Skipping live target because related process is running: {0}" -f $target.Source)
    continue
  }

  Invoke-MoveWithJunction -Source $target.Source -Reason $target.Reason
}

Write-Log ("Done. C: free: {0} GB" -f (Get-DriveFreeGB -DriveName "C:"))
if ($Apply) {
  Write-Log "Log: $LogPath"
  Write-Log "Manifest: $ManifestPath"
}
