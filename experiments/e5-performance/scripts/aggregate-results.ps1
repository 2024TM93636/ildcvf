param(
    [Parameter(Mandatory = $true)]
    [string[]]$InputFiles
)

$ErrorActionPreference = "Stop"

function Get-MetricValue {
    param(
        [Parameter(Mandatory = $true)] $Metric,
        [Parameter(Mandatory = $true)] [string]$Name
    )

    $property = $Metric.values.PSObject.Properties[$Name]

    if ($null -eq $property) {
        throw "Required metric value '$Name' is missing."
    }

    return [double]$property.Value
}

function Mean {
    param([double[]]$Values)

    if ($null -eq $Values -or $Values.Count -eq 0) {
        return $null
    }

    return ($Values | Measure-Object -Average).Average
}

$runs = @()

foreach ($file in $InputFiles) {
    if (-not (Test-Path $file)) {
        throw "Result file does not exist: $file"
    }

    $data = Get-Content $file -Raw | ConvertFrom-Json

    if ($null -eq $data.metrics.http_req_duration) {
        throw "Missing http_req_duration in: $file"
    }

    if ($null -eq $data.metrics.http_reqs) {
        throw "Missing http_reqs in: $file"
    }

    if ($null -eq $data.metrics.http_req_failed) {
        throw "Missing http_req_failed in: $file"
    }

    if ($null -eq $data.state.testRunDurationMs) {
        throw "Missing state.testRunDurationMs in: $file"
    }

    $duration = $data.metrics.http_req_duration
    $totalRequests = [double]$data.metrics.http_reqs.values.count
    $failureRate = [double]$data.metrics.http_req_failed.values.rate

    $failedRequests = $totalRequests * $failureRate
    $successfulRequests = $totalRequests - $failedRequests

    $measuredSeconds = [double]$data.state.testRunDurationMs / 1000.0

    if ($measuredSeconds -le 0) {
        throw "Invalid measured duration in: $file"
    }

    $successfulThroughput = $successfulRequests / $measuredSeconds

    $fileName = [System.IO.Path]::GetFileNameWithoutExtension($file)

    if ($fileName -notmatch '^(?<path>DIRECT|ILDCVF)-(?<profile>smoke|steady|moderate)-(?<run>.+)$') {
        throw @"
Unexpected result filename '$fileName'.
Expected:
DIRECT-<profile>-<run>.json
or
ILDCVF-<profile>-<run>.json
"@
    }

    $runs += [PSCustomObject]@{
        File                 = $file
        PathType             = $Matches.path
        Profile              = $Matches.profile
        RunId                = $Matches.run
        TotalRequests        = $totalRequests
        FailedRequests       = $failedRequests
        SuccessfulRequests   = $successfulRequests
        MeasuredSeconds      = $measuredSeconds
        ThroughputReqPerSec  = $successfulThroughput
        MeanMs               = Get-MetricValue $duration 'avg'
        P50Ms                = Get-MetricValue $duration 'med'
        P95Ms                = Get-MetricValue $duration 'p(95)'
        P99Ms                = Get-MetricValue $duration 'p(99)'
    }
}

Write-Host "`n===== INDIVIDUAL RUNS ====="

$runs |
    Sort-Object Profile, PathType, RunId |
    Format-Table `
        PathType,
        Profile,
        RunId,
        TotalRequests,
        FailedRequests,
        SuccessfulRequests,
        MeasuredSeconds,
        ThroughputReqPerSec,
        MeanMs,
        P50Ms,
        P95Ms,
        P99Ms `
        -AutoSize

$aggregates = @()

foreach ($group in ($runs | Group-Object Profile, PathType)) {
    $items = @($group.Group)

    $aggregates += [PSCustomObject]@{
        Profile             = $items[0].Profile
        PathType            = $items[0].PathType
        Runs                = $items.Count
        MeanMs              = Mean @($items.MeanMs)
        P50Ms               = Mean @($items.P50Ms)
        P95Ms               = Mean @($items.P95Ms)
        P99Ms               = Mean @($items.P99Ms)
        ThroughputReqPerSec = Mean @($items.ThroughputReqPerSec)
    }
}

Write-Host "`n===== AGGREGATED PATH RESULTS ====="

$aggregates |
    Sort-Object Profile, PathType |
    Format-Table -AutoSize

Write-Host "`n===== DIRECT vs ILDCVF ====="

$comparisons = @()

foreach ($profile in ($aggregates.Profile | Sort-Object -Unique)) {
    $direct = $aggregates |
        Where-Object {
            $_.Profile -eq $profile -and
            $_.PathType -eq 'DIRECT'
        } |
        Select-Object -First 1

    $proxy = $aggregates |
        Where-Object {
            $_.Profile -eq $profile -and
            $_.PathType -eq 'ILDCVF'
        } |
        Select-Object -First 1

    if ($null -eq $direct -or $null -eq $proxy) {
        Write-Warning "Profile '$profile' does not have both DIRECT and ILDCVF results."
        continue
    }

    foreach ($metric in @('MeanMs', 'P50Ms', 'P95Ms', 'P99Ms')) {
        $directValue = [double]$direct.$metric
        $proxyValue = [double]$proxy.$metric

        $absolute = $proxyValue - $directValue

        $relative = if ($directValue -eq 0) {
            $null
        }
        else {
            ($absolute / $directValue) * 100.0
        }

        $comparisons += [PSCustomObject]@{
            Profile             = $profile
            Metric              = $metric
            DirectMs            = $directValue
            IldcvfMs            = $proxyValue
            AbsoluteOverheadMs  = $absolute
            RelativeOverheadPct = $relative
        }
    }

    $comparisons += [PSCustomObject]@{
        Profile             = $profile
        Metric              = 'ThroughputReqPerSec'
        DirectMs            = $direct.ThroughputReqPerSec
        IldcvfMs            = $proxy.ThroughputReqPerSec
        AbsoluteOverheadMs  = $null
        RelativeOverheadPct = $null
    }
}

$comparisons |
    Format-Table -AutoSize