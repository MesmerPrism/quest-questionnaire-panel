param(
    [int]$RecordSeconds = 15,
    [string]$OutputRoot = "artifacts\recording-comparison"
)

$ErrorActionPreference = 'Stop'

$AutomationPackage = 'io.github.mesmerprism.questquestionnaire.questuiautomation'
$Runner = "$AutomationPackage.test/androidx.test.runner.AndroidJUnitRunner"
$BrbActivity = 'org.thebigredbuttoninstitute.app/com.unity3d.player.UnityPlayerGameActivity'
$ProjectionAction = 'io.github.mesmerprism.questquestionnaire.questuiautomation.RECORD_MEDIA_PROJECTION'
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$RunDir = Join-Path $OutputRoot $Stamp
New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
$AdbPath = (Get-Command adb -ErrorAction Stop).Source

function Invoke-Adb {
    & $script:AdbPath @args
}

function Invoke-AdbShell {
    Invoke-Adb shell @args
}

function Write-RunLog {
    param([string]$Message)
    $line = "[{0}] {1}" -f (Get-Date -Format o), $Message
    $line | Tee-Object -FilePath (Join-Path $RunDir 'run.log') -Append | Out-Null
}

function Disable-MetricsOverlay {
    foreach ($action in @(
        'DISABLE_OVERLAY',
        'DISABLE_OVERLAY_CAPTURE',
        'DISABLE_GRAPH',
        'DISABLE_STATS',
        'DISABLE_CSV'
    )) {
        Invoke-AdbShell am broadcast `
            -a "com.oculus.ovrmonitormetricsservice.$action" `
            -n com.oculus.ovrmonitormetricsservice/.SettingsBroadcastReceiver | Out-Null
    }
}

function Invoke-BrbStart {
    param([string[]]$Extras)
    $command = "am start -W -n $BrbActivity $($Extras -join ' ')"
    Write-RunLog "BRB $command"
    Invoke-AdbShell am start -W -n $BrbActivity @Extras |
        Set-Content -Path (Join-Path $RunDir 'last-brb-command.txt')
}

function Invoke-ComparisonStimulus {
    Start-Sleep -Milliseconds 900
    Invoke-BrbStart @('--es', 'brb.runtimeCommand', 'center_button')
    Start-Sleep -Milliseconds 900
    Invoke-BrbStart @('--es', 'brb.runtimeCommand', 'blink_button:1')
    Start-Sleep -Milliseconds 1800
    Invoke-BrbStart @('--es', 'brb.runtimeCommand', 'press_button')
    Start-Sleep -Milliseconds 1500
    Invoke-BrbStart @(
        '--ez', 'brb.questionnaireOpen', 'true',
        '--es', 'brb.questionnaireTrigger', 'final',
        '--es', 'brb.questionnaireCommandScript', 'final:1,next,submit',
        '--ei', 'brb.questionnaireCommandIntervalMs', '700'
    )
}

function Set-MetacamOptions {
    param([string]$OptionTargets)
    $instrument = "am instrument -w " +
        "-e scenario settingsChildPageProbe " +
        "-e childTargets 'camera:Aspect ratio,camera:Bit rate,camera:Frame rate,camera:Image stabilization' " +
        "-e childTargetRole dropdown " +
        "-e clickModes coordinate " +
        "-e optionTargets '$OptionTargets' " +
        "-e allowOptionSelect true " +
        "-e optionClickMode coordinate " +
        "-e maxContentScrolls 5 -e maxNavScrolls 10 " +
        "-e dumpChildAccessibility false " +
        $Runner
    Invoke-AdbShell $instrument
}

function Get-VideoMetadata {
    param([string]$Path)
    if (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
        return ''
    }
    (& ffprobe -v error -select_streams v:0 `
        -show_entries stream=width,height,avg_frame_rate,nb_frames,duration,bit_rate `
        -of csv=p=0 "$Path" 2>$null) -join "`n"
}

function Add-Result {
    param(
        [string]$Route,
        [string]$LocalPath,
        [string]$Notes
    )
    $metadata = if ($LocalPath -and (Test-Path -LiteralPath $LocalPath)) { Get-VideoMetadata $LocalPath } else { '' }
    $resolved = if ($LocalPath -and (Test-Path -LiteralPath $LocalPath)) {
        (Resolve-Path -LiteralPath $LocalPath -ErrorAction SilentlyContinue).Path
    } else {
        ''
    }
    [PSCustomObject]@{
        route = $Route
        localPath = $resolved
        metadata = $metadata
        notes = $Notes
    } | ConvertTo-Json -Compress |
        Add-Content -Path (Join-Path $RunDir 'results.jsonl')
}

function Get-DeviceFileSize {
    param([string]$DevicePath)
    $raw = (Invoke-AdbShell "stat -c %s $DevicePath 2>/dev/null") -join ''
    $size = 0L
    if ([long]::TryParse($raw.Trim(), [ref]$size)) {
        return $size
    }
    return -1L
}

function Wait-DeviceFileStable {
    param([string]$DevicePath)
    $lastSize = -1L
    $stableCount = 0
    for ($i = 0; $i -lt 30; $i += 1) {
        Start-Sleep -Seconds 1
        $size = Get-DeviceFileSize $DevicePath
        if ($size -gt 0 -and $size -eq $lastSize) {
            $stableCount += 1
            if ($stableCount -ge 3) {
                return $true
            }
        } else {
            $stableCount = 0
        }
        $lastSize = $size
    }
    return $false
}

function Get-DeviceMp4List {
    @(Invoke-AdbShell "ls -1t /sdcard/Oculus/VideoShots/*.mp4 2>/dev/null") |
        Where-Object { $_ -and $_.Trim() } |
        ForEach-Object { $_.Trim() }
}

function Get-NewestDeviceMp4 {
    param(
        [string[]]$Before,
        [string]$Route
    )
    $after = @()
    $new = $null
    for ($i = 0; $i -lt 30; $i += 1) {
        $after = @(Get-DeviceMp4List)
        $after | Set-Content -Path (Join-Path $RunDir "$Route-after.txt")
        $new = $after | Where-Object { $Before -notcontains $_ } | Select-Object -First 1
        if ($new) {
            break
        }
        Start-Sleep -Seconds 1
    }
    $new = $after | Where-Object { $Before -notcontains $_ } | Select-Object -First 1
    if (-not $new) {
        throw 'No new Metacam MP4 found after recording.'
    }
    $new = $new.Trim()
    $stable = Wait-DeviceFileStable $new
    return [PSCustomObject]@{
        path = $new
        stable = $stable
    }
}

function Run-MetacamCapture {
    param(
        [string]$Route,
        [string]$AspectOption
    )
    Write-RunLog "Starting $Route"
    Disable-MetricsOverlay
    Invoke-BrbStart @('--es', 'brb.runtimeCommand', 'center_button')
    Start-Sleep -Seconds 2
    Set-MetacamOptions "Aspect ratio=$AspectOption;Bit rate=14 mbps;Frame rate=60 fps;Image stabilization=High" |
        Set-Content -Path (Join-Path $RunDir "$Route-settings.txt")

    $before = @(Get-DeviceMp4List)
    $before | Set-Content -Path (Join-Path $RunDir "$Route-before.txt")
    $recordMs = $RecordSeconds * 1000
    $job = Start-Job -ArgumentList $script:AdbPath, $recordMs, $Runner -ScriptBlock {
        param($Adb, $RecordMs, $RunnerName)
        & $Adb shell am instrument -w -e scenario metacamRecordProbe -e recordMs $RecordMs $RunnerName
    }
    Start-Sleep -Seconds 3
    Invoke-ComparisonStimulus
    Wait-Job $job | Out-Null
    Receive-Job $job | Set-Content -Path (Join-Path $RunDir "$Route-metacamRecordProbe.txt")
    Remove-Job $job

    Start-Sleep -Seconds 2
    $deviceResult = Get-NewestDeviceMp4 $before $Route
    $devicePath = $deviceResult.path
    $localPath = Join-Path $RunDir "$Route.mp4"
    Invoke-Adb pull $devicePath $localPath | Out-Null
    $stableNote = if ($deviceResult.stable) { 'file stabilized before pull' } else { 'file did not stabilize before pull' }
    Add-Result $Route $localPath "Built-in Metacam recorder, $AspectOption, 14 mbps, 60 fps, High stabilization; $stableNote."
}

function Run-AdbScreenrecordCapture {
    Write-RunLog 'Starting adb-screenrecord'
    Disable-MetricsOverlay
    Invoke-BrbStart @('--es', 'brb.runtimeCommand', 'center_button')
    Start-Sleep -Seconds 2
    $devicePath = "/sdcard/Download/brb-comparison-adb-$Stamp.mp4"
    Invoke-AdbShell rm -f $devicePath | Out-Null
    $job = Start-Job -ArgumentList $script:AdbPath, $devicePath, $RecordSeconds -ScriptBlock {
        param($Adb, $DevicePath, $Seconds)
        & $Adb shell screenrecord --verbose --size 3664x1920 --bit-rate 40000000 --time-limit $Seconds $DevicePath
    }
    Start-Sleep -Seconds 1
    Invoke-ComparisonStimulus
    Wait-Job $job | Out-Null
    Receive-Job $job | Set-Content -Path (Join-Path $RunDir 'adb-screenrecord.txt')
    Remove-Job $job
    $localPath = Join-Path $RunDir 'adb-screenrecord.mp4'
    Invoke-Adb pull $devicePath $localPath | Out-Null
    Invoke-AdbShell rm -f $devicePath | Out-Null
    Add-Result 'adb-screenrecord' $localPath 'ADB screenrecord at 3664x1920 and 40 Mbps.'
}

function Read-ProjectionStatus {
    $statusPath = "/sdcard/Android/data/$AutomationPackage/files/media-projection-record-status.json"
    $raw = (Invoke-AdbShell "cat $statusPath 2>/dev/null") -join ''
    if ($raw.Trim().StartsWith('{')) {
        return $raw | ConvertFrom-Json
    }
    return $null
}

function Run-MediaProjectionCapture {
    param([int]$Attempt = 1)
    Write-RunLog 'Starting media-projection'
    Disable-MetricsOverlay
    Invoke-BrbStart @('--es', 'brb.runtimeCommand', 'center_button')
    Start-Sleep -Seconds 2
    Invoke-AdbShell cmd appops set $AutomationPackage PROJECT_MEDIA allow | Out-Null
    Invoke-AdbShell rm -f "/sdcard/Android/data/$AutomationPackage/files/media-projection-record-status.json" | Out-Null
    Invoke-AdbShell am start -W `
        -a $ProjectionAction `
        -n "$AutomationPackage/.ProjectionRecordingActivity" `
        --ei durationMs ($RecordSeconds * 1000) `
        --ei width 1920 `
        --ei height 1080 `
        --ei frameRate 60 `
        --ei bitRate 40000000 |
        Set-Content -Path (Join-Path $RunDir 'media-projection-start.txt')

    $started = $false
    for ($i = 0; $i -lt 20; $i += 1) {
        Start-Sleep -Milliseconds 500
        $status = Read-ProjectionStatus
        if ($null -ne $status -and $status.state -eq 'recording_started') {
            $started = $true
            break
        }
    }
    if (-not $started) {
        Write-RunLog 'MediaProjection status did not report recording_started before stimulus.'
    }
    Invoke-ComparisonStimulus

    $finalStatus = $null
    for ($i = 0; $i -lt 40; $i += 1) {
        Start-Sleep -Milliseconds 500
        $finalStatus = Read-ProjectionStatus
        if ($null -ne $finalStatus -and ($finalStatus.state -eq 'recording_stopped' -or $finalStatus.state -eq 'recording_stop_error')) {
            break
        }
    }
    $finalStatus | ConvertTo-Json -Depth 8 |
        Set-Content -Path (Join-Path $RunDir 'media-projection-status.json')
    Invoke-AdbShell cmd appops set $AutomationPackage PROJECT_MEDIA default | Out-Null

    $devicePath = if ($finalStatus -and $finalStatus.details) { $finalStatus.details.outputFile } else { '' }
    if (-not $devicePath) {
        $candidate = (Invoke-AdbShell "ls -1t /sdcard/Android/data/$AutomationPackage/files/recordings/*.mp4 2>/dev/null") |
            Select-Object -First 1
        $devicePath = if ($candidate) { $candidate.Trim() } else { '' }
    }
    if (-not $devicePath) {
        $state = if ($finalStatus) { $finalStatus.state } else { 'no_status' }
        $errorClass = if ($finalStatus -and $finalStatus.details -and $finalStatus.details.recordingError) {
            $finalStatus.details.recordingError.class
        } else {
            ''
        }
        Add-Result 'media-projection' '' "FAILED: no MP4 found after recording. Final state: $state. Error: $errorClass"
        return
    }
    [void](Wait-DeviceFileStable $devicePath)
    $localPath = Join-Path $RunDir 'media-projection.mp4'
    if ($Attempt -gt 1) {
        $localPath = Join-Path $RunDir "media-projection-attempt-$Attempt.mp4"
    }
    Invoke-Adb pull $devicePath $localPath | Out-Null
    $localItem = Get-Item -LiteralPath $localPath -ErrorAction SilentlyContinue
    $metadata = Get-VideoMetadata $localPath
    $badVideo = $finalStatus -and $finalStatus.state -eq 'recording_stop_error'
    $badVideo = $badVideo -or ($localItem -and $localItem.Length -lt 100000)
    $badVideo = $badVideo -or ($metadata -match '^0,0,')
    if ($badVideo -and $Attempt -lt 2) {
        Write-RunLog 'MediaProjection produced an invalid artifact; retrying once.'
        Run-MediaProjectionCapture -Attempt ($Attempt + 1)
        return
    }
    Add-Result 'media-projection' $localPath 'App-owned MediaProjection via lab activity, 1920x1080, 60 fps, 40 Mbps target.'
}

function Invoke-CaptureRoute {
    param(
        [string]$Route,
        [scriptblock]$Capture
    )
    try {
        & $Capture
    } catch {
        $message = $_.Exception.Message
        Write-RunLog "$Route failed: $message"
        Add-Result $Route '' "FAILED: $message"
    }
}

Write-RunLog "Recording comparison run directory: $RunDir"
try {
    Invoke-CaptureRoute 'metacam-landscape' { Run-MetacamCapture 'metacam-landscape' 'Landscape 16:9' }
    Invoke-CaptureRoute 'metacam-portrait' { Run-MetacamCapture 'metacam-portrait' 'Portrait 9:16' }
    Invoke-CaptureRoute 'adb-screenrecord' { Run-AdbScreenrecordCapture }
    Invoke-CaptureRoute 'media-projection' { Run-MediaProjectionCapture }
} finally {
    Invoke-AdbShell cmd appops set $AutomationPackage PROJECT_MEDIA default | Out-Null
    Set-MetacamOptions 'Aspect ratio=Landscape 16:9;Bit rate=3 mbps;Frame rate=30 fps;Image stabilization=Off' |
        Set-Content -Path (Join-Path $RunDir 'restore-metacam-defaults.txt')
}

Write-Output "RUN_DIR=$((Resolve-Path $RunDir).Path)"
