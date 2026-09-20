[CmdletBinding(DefaultParameterSetName = "None")]
param(
    [Parameter(
        Mandatory = $true,
        ParameterSetName = "Preflight"
    )]
    [switch]$Preflight,

    [Parameter(
        Mandatory = $true,
        ParameterSetName = "Execute"
    )]
    [switch]$Execute,

    [Parameter(
        Mandatory = $true,
        ParameterSetName = "Report"
    )]
    [switch]$Report,

    [Parameter(
        Mandatory = $true,
        ParameterSetName = "Preflight"
    )]
    [Parameter(
        Mandatory = $true,
        ParameterSetName = "Execute"
    )]
    [Parameter(
        Mandatory = $true,
        ParameterSetName = "Report"
    )]
    [ValidateNotNullOrEmpty()]
    [string]$AttemptId,

    [Parameter(
        Mandatory = $true,
        ParameterSetName = "Execute"
    )]
    [ValidateNotNullOrEmpty()]
    [string]$ControllerAuthorization
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ExpectedR1Hash =
    "3c98886b76031189bbde7d1ba7329ad100f0c5db1d118ccde342239e62950263"

$ExpectedR2Hash =
    "929d5d43184eb97176294c9feb5eadd6989a8a784c7818ffccd06337a952e2e7"

$ExpectedProtocolHash =
    "6b81ada13f48f28ed97767278c606673c4bc7941bf69cce22a3c20ca6c6a29f1"

$ExpectedPromptHash =
    "d9ee37eddddff37d75ecdeb933639c356054966f70e2e76ba5694efc7c655947"

$ExpectedSchemaHash =
    "7247e79e1cb5be50e7baeb28ac185caeab68fbc8336736f9c8c212d091768ea4"

$ScriptDirectory =
    Split-Path -Parent $MyInvocation.MyCommand.Path

$RepositoryRoot =
    (Resolve-Path (
        Join-Path $ScriptDirectory ".."
    )).Path

$BackendRoot =
    Join-Path $RepositoryRoot "backend"

$MeasuredRoot =
    Join-Path $RepositoryRoot (
        "experiments\e6-llm-reliability\results\measured"
    )

$ProtocolFile =
    Join-Path $RepositoryRoot (
        "experiments\e6-llm-reliability\protocol\e6-measured-v1.json"
    )

$PromptFile =
    Join-Path $RepositoryRoot (
        "experiments\e6-llm-reliability\prompts\proposal-v1.txt"
    )

$SchemaFile =
    Join-Path $RepositoryRoot (
        "experiments\e6-llm-reliability\schema\" +
        "contract-change-proposal.schema.json"
    )

function Get-Sha256 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "STOP: required file is absent: $Path"
    }

    return (
        Get-FileHash `
            -LiteralPath $Path `
            -Algorithm SHA256
    ).Hash.ToLowerInvariant()
}

function Assert-Hash {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Expected,

        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    $Actual = Get-Sha256 -Path $Path

    if ($Actual -ne $Expected) {
        throw (
            "STOP: $Label SHA-256 changed. " +
            "Expected=$Expected Actual=$Actual"
        )
    }

    Write-Host "PASS: $Label hash verified."
}

function Get-ResultPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Id
    )

    if (
        [string]::IsNullOrWhiteSpace($Id) -or
        $Id -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$'
    ) {
        throw "STOP: invalid explicit attempt ID."
    }

    $AttemptDirectory =
        Join-Path $MeasuredRoot $Id

    return Join-Path $AttemptDirectory "result.json"
}

function Assert-PermanentEvidence {
    $R1 =
        Get-ResultPath `
            -Id "llm-001-undocumented-operation-r1"

    $R2 =
        Get-ResultPath `
            -Id "llm-001-undocumented-operation-r2"

    Assert-Hash `
        -Path $R1 `
        -Expected $ExpectedR1Hash `
        -Label "permanent r1"

    Assert-Hash `
        -Path $R2 `
        -Expected $ExpectedR2Hash `
        -Label "permanent r2"
}

function Assert-FrozenScientificHashes {
    Assert-Hash `
        -Path $ProtocolFile `
        -Expected $ExpectedProtocolHash `
        -Label "measured protocol"

    Assert-Hash `
        -Path $PromptFile `
        -Expected $ExpectedPromptHash `
        -Label "frozen prompt"

    Assert-Hash `
        -Path $SchemaFile `
        -Expected $ExpectedSchemaHash `
        -Label "frozen schema"
}

function Assert-RepositorySecrets {
    $Leaks = @()

    Get-ChildItem `
        -LiteralPath $RepositoryRoot `
        -Recurse `
        -File |
        Where-Object {
            $_.FullName -notmatch '\\.git\\' -and
            $_.FullName -notmatch '\\target\\' -and
            $_.FullName -notmatch '\\node_modules\\' -and
            $_.FullName -notmatch '\\results\\measured\\'
        } |
        ForEach-Object {
            $Path = $_.FullName

            try {
                foreach (
                    $Line in Get-Content `
                        -LiteralPath $Path `
                        -ErrorAction Stop
                ) {
                    if (
                        $Line -match
                        '^\s*GROQ_API_KEY\s*=(.*)$'
                    ) {
                        $Value = $Matches[1].Trim()

                        if ($Value.Length -gt 0) {
                            $Placeholder =
                                $Value -match (
                                    '^(?i)' +
                                    '(your[-_ ]?key[-_ ]?here|' +
                                    'replace[-_ ]?me|' +
                                    'example|' +
                                    'placeholder)$'
                                ) -or
                                $Value -match '^(?i)<.*>$'

                            if (-not $Placeholder) {
                                $Leaks +=
                                    [PSCustomObject]@{
                                        Path = $Path
                                        Category =
                                            "Suspicious GROQ_API_KEY assignment"
                                    }
                            }
                        }
                    }

                    if (
                        $Line -match
                        '\bgsk_[A-Za-z0-9_-]{12,}\b'
                    ) {
                        $Leaks +=
                            [PSCustomObject]@{
                                Path = $Path
                                Category =
                                    "Probable Groq token"
                            }
                    }

                    if (
                        $Line -match
                        '(?i)Authorization\s*:\s*Bearer\s+\S+'
                    ) {
                        $Leaks +=
                            [PSCustomObject]@{
                                Path = $Path
                                Category =
                                    "Bearer credential"
                            }
                    }
                }
            }
            catch {
                # Ignore unreadable/binary files.
            }
        }

    if ($Leaks.Count -gt 0) {
        $Leaks |
            Select-Object Path, Category |
            Format-Table -AutoSize

        throw (
            "STOP: probable repository secret " +
            "exposure detected."
        )
    }

    Write-Host (
        "PASS: no probable Groq/Bearer " +
        "secret values detected."
    )
}

function Show-GitState {
    Push-Location $RepositoryRoot

    try {
        Write-Host ""
        Write-Host "Git branch:"
        git branch --show-current

        Write-Host ""
        Write-Host "Git status:"
        git status --short

        if ($LASTEXITCODE -ne 0) {
            throw "STOP: git status failed."
        }
    }
    finally {
        Pop-Location
    }
}

function Assert-CampaignState {
    if (-not (Test-Path -LiteralPath $MeasuredRoot)) {
        throw "STOP: measured campaign root is absent."
    }

    $Results =
        @(
            Get-ChildItem `
                -LiteralPath $MeasuredRoot `
                -Recurse `
                -File `
                -Filter "result.json"
        )

    Write-Host (
        "Measured result count = " +
        $Results.Count
    )

    foreach ($Result in $Results) {
        $Relative =
            $Result.FullName.Substring(
                $MeasuredRoot.Length
            ).TrimStart('\', '/')

        Write-Host "  $Relative"
    }
}

function Assert-ExplicitScheduledAttempt {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Id
    )

    if ([string]::IsNullOrWhiteSpace($Id)) {
        throw "STOP: attempt ID is required explicitly."
    }

    if (
        $Id -notmatch
        '^llm-\d{3}-[a-z0-9-]+-r[1-4]$'
    ) {
        throw (
            "STOP: attempt ID does not match the " +
            "frozen measured identity format."
        )
    }

    $ScenarioCatalog =
        Join-Path $RepositoryRoot (
            "experiments\e6-llm-reliability\" +
            "scenarios\ground-truth.json"
        )

    if (-not (
        Test-Path `
            -LiteralPath $ScenarioCatalog `
            -PathType Leaf
    )) {
        throw (
            "STOP: frozen ground-truth scenario " +
            "catalog is absent."
        )
    }

    $GroundTruth =
        Get-Content `
            -LiteralPath $ScenarioCatalog `
            -Raw |
        ConvertFrom-Json

    $ScheduledAttemptIds =
        @(
            foreach ($Scenario in $GroundTruth) {
                for ($Repetition = 1;
                     $Repetition -le 4;
                     $Repetition++) {

                    "$($Scenario.scenarioId)-r$Repetition"
                }
            }
        )

    if ($Id -notin $ScheduledAttemptIds) {
        throw (
            "STOP: attempt ID is not present in the " +
            "frozen measured schedule: $Id"
        )
    }

    Write-Host "PASS: explicit scheduled attempt = $Id"
}

function Assert-NotRecorded {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Id
    )

    $Result = Get-ResultPath -Id $Id

    if (Test-Path -LiteralPath $Result) {
        throw (
            "STOP: measured attempt already exists. " +
            "No rerun/overwrite is permitted: $Id"
        )
    }

    Write-Host "PASS: selected attempt is unrecorded."
}

function Invoke-PreflightChecks {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Id,

        [switch]$RequireUnrecorded
    )

    Write-Host "E6 measured campaign preflight"
    Write-Host "Repository root = $RepositoryRoot"
    Write-Host "Attempt ID = $Id"

    Assert-ExplicitScheduledAttempt -Id $Id
    Assert-FrozenScientificHashes
    Assert-PermanentEvidence
    Assert-CampaignState
    Assert-RepositorySecrets
    Show-GitState

    if ($RequireUnrecorded) {
        Assert-NotRecorded -Id $Id
    }
}

function Show-ScientificReport {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Id
    )

    $ResultPath = Get-ResultPath -Id $Id

    if (-not (
        Test-Path `
            -LiteralPath $ResultPath `
            -PathType Leaf
    )) {
        throw (
            "STOP: no measured result exists for: $Id"
        )
    }

    $Hash =
        Get-Sha256 -Path $ResultPath

    $Result =
        Get-Content `
            -LiteralPath $ResultPath `
            -Raw |
        ConvertFrom-Json

    Write-Host ""
    Write-Host "Artifact"
    Write-Host "--------"
    Write-Host "path   = $ResultPath"
    Write-Host "sha256 = $Hash"

    Write-Host ""
    Write-Host "Identity"
    Write-Host "--------"
    Write-Host "attemptId           = $($Result.attemptId)"
    Write-Host "scenarioId          = $($Result.scenarioId)"
    Write-Host "repetition          = $($Result.repetition)"
    Write-Host (
        "evidenceFingerprint = " +
        $Result.evidenceFingerprint
    )

    Write-Host ""
    Write-Host "1. provider/infrastructure"
    Write-Host "--------------------------"
    Write-Host (
        "providerInvocationCount = " +
        $Result.providerInvocationCount
    )
    Write-Host "retryCount              = $($Result.retryCount)"
    Write-Host "httpStatus              = $($Result.httpStatus)"
    Write-Host (
        "providerCallStatus       = " +
        $Result.providerCallStatus
    )
    Write-Host (
        "providerDiagnostic       = " +
        $Result.providerDiagnostic
    )

    $TransportDiagnosticProperty =
        $Result.PSObject.Properties[
            "transportDiagnostic"
        ]

    if (
        $null -ne $TransportDiagnosticProperty -and
        $null -ne $TransportDiagnosticProperty.Value
    ) {
        $TransportDiagnostic =
            $TransportDiagnosticProperty.Value

        Write-Host (
            "transportFailureKind    = " +
            $TransportDiagnostic.transportFailureKind
        )

        Write-Host (
            "transportExceptionClass = " +
            $TransportDiagnostic.transportExceptionClass
        )

        Write-Host (
            "transportCauseClass     = " +
            $TransportDiagnostic.transportCauseClass
        )

        Write-Host (
            "transportDiagnosticCode = " +
            $TransportDiagnostic.transportDiagnosticCode
        )
    }
    else {
        Write-Host (
            "transportDiagnostic     = " +
            "<not retained>"
        )
    }

    Write-Host ""
    Write-Host "2. structured-output parsing"
    Write-Host "----------------------------"
    Write-Host (
        "proposalParseStatus = " +
        $Result.proposalParseStatus
    )

    Write-Host ""
    Write-Host "3. deterministic validation"
    Write-Host "---------------------------"
    Write-Host (
        "applicability = " +
        $Result.validatorApplicability
    )
    Write-Host (
        "accepted      = " +
        $Result.validatorAccepted
    )
    Write-Host (
        "codes         = " +
        ($Result.validatorCodes -join ", ")
    )

    Write-Host ""
    Write-Host "4. ground-truth correctness"
    Write-Host "---------------------------"
    Write-Host (
        "applicability = " +
        $Result.groundTruthApplicability
    )
    Write-Host (
        "exactMatch    = " +
        $Result.groundTruthExactMatch
    )

    if (
        $null -ne
        $Result.groundTruthFieldComparison
    ) {
        $Result.groundTruthFieldComparison |
            Format-List
    }

    Write-Host ""
    Write-Host "5. hallucination"
    Write-Host "----------------"
    Write-Host (
        "applicability = " +
        $Result.hallucinationApplicability
    )
    Write-Host (
        "hallucinated  = " +
        $Result.hallucinated
    )
    Write-Host (
        "reasons       = " +
        ($Result.hallucinationReasonCodes -join ", ")
    )

    Write-Host ""
    Write-Host "Reproducibility metadata"
    Write-Host "------------------------"
    Write-Host "provider            = $($Result.provider)"
    Write-Host "requestedModel      = $($Result.requestedModel)"
    Write-Host "returnedModel       = $($Result.returnedModel)"
    Write-Host "systemFingerprint   = $($Result.systemFingerprint)"
    Write-Host "serviceTier         = $($Result.serviceTier)"
    Write-Host "temperature         = $($Result.temperature)"
    Write-Host "topP                = $($Result.topP)"
    Write-Host "seed                = $($Result.seed)"
    Write-Host (
        "maxCompletionTokens = " +
        $Result.maxCompletionTokens
    )
    Write-Host (
        "timeoutMilliseconds = " +
        $Result.timeoutMilliseconds
    )
    Write-Host (
        "latencyMilliseconds = " +
        $Result.latencyMilliseconds
    )
    Write-Host "inputTokens         = $($Result.inputTokens)"
    Write-Host "outputTokens        = $($Result.outputTokens)"
    Write-Host "reasoningTokens     = $($Result.reasoningTokens)"
    Write-Host "totalTokens         = $($Result.totalTokens)"
    Write-Host "finishReason        = $($Result.finishReason)"
    Write-Host "timestamp           = $($Result.timestamp)"
    Write-Host (
        "measuredCampaignAttempt = " +
        $Result.measuredCampaignAttempt
    )
}

switch ($PSCmdlet.ParameterSetName) {
    "Preflight" {
        if (
            -not [string]::IsNullOrWhiteSpace(
                $env:GROQ_API_KEY
            ) -or
            -not [string]::IsNullOrWhiteSpace(
                $env:ILDCVF_E6_MEASURED_AUTHORIZATION
            )
        ) {
            throw (
                "STOP: preflight must run without " +
                "measured credentials in the environment."
            )
        }

        Invoke-PreflightChecks `
            -Id $AttemptId `
            -RequireUnrecorded

        Write-Host ""
        Write-Host (
            "PASS: preflight completed. " +
            "No provider execution was requested."
        )
    }

    "Report" {
        if (
            -not [string]::IsNullOrWhiteSpace(
                $env:GROQ_API_KEY
            ) -or
            -not [string]::IsNullOrWhiteSpace(
                $env:ILDCVF_E6_MEASURED_AUTHORIZATION
            )
        ) {
            throw (
                "STOP: report mode must run without " +
                "measured credentials in the environment."
            )
        }

        Assert-ExplicitScheduledAttempt -Id $AttemptId
        Assert-PermanentEvidence

        Show-ScientificReport -Id $AttemptId
    }

    "Execute" {
        /*
         * Execution is deliberately isolated in its own
         * parameter set. Merely running the script cannot
         * fall through into this branch.
         */

        Invoke-PreflightChecks `
            -Id $AttemptId `
            -RequireUnrecorded

        if (
            [string]::IsNullOrWhiteSpace(
                $env:GROQ_API_KEY
            )
        ) {
            throw (
                "STOP: GROQ_API_KEY must already be " +
                "supplied session-only by the operator."
            )
        }

        if (
            [string]::IsNullOrWhiteSpace(
                $ControllerAuthorization
            )
        ) {
            throw (
                "STOP: explicit controller authorization " +
                "is required."
            )
        }

        try {
            $env:ILDCVF_E6_MEASURED_AUTHORIZATION =
                $ControllerAuthorization

            Push-Location $BackendRoot

            try {
                & .\mvnw.cmd test-compile

                if ($LASTEXITCODE -ne 0) {
                    throw "STOP: Maven test-compile failed."
                }

                $ExecArgs =
                    "'$RepositoryRoot' " +
                    "'$ControllerAuthorization' " +
                    "'$AttemptId'"

                & .\mvnw.cmd `
                    "org.codehaus.mojo:exec-maven-plugin:3.5.0:java" `
                    "-Dexec.mainClass=com.ildcvf.experiment.llm.measured.execution.GroqMeasuredSingleAttemptLauncher" `
                    "-Dexec.classpathScope=test" `
                    "-Dexec.args=$ExecArgs"

                if ($LASTEXITCODE -ne 0) {
                    throw (
                        "Measured launcher returned a " +
                        "non-zero exit code."
                    )
                }
            }
            finally {
                Pop-Location
            }
        }
        finally {
            Remove-Item `
                Env:ILDCVF_E6_MEASURED_AUTHORIZATION `
                -ErrorAction SilentlyContinue

            Remove-Item `
                Env:GROQ_API_KEY `
                -ErrorAction SilentlyContinue
        }

        $Generated =
            Get-ResultPath -Id $AttemptId

        if (-not (
            Test-Path `
                -LiteralPath $Generated `
                -PathType Leaf
        )) {
            throw (
                "STOP: authorized execution returned " +
                "without a measured result."
            )
        }

        Write-Host ""
        Write-Host (
            "Execution returned. Credentials were removed."
        )
        Write-Host (
            "GROQ_API_KEY present after cleanup = " +
            (-not [string]::IsNullOrWhiteSpace(
                $env:GROQ_API_KEY
            ))
        )
        Write-Host (
            "Measured authorization present after cleanup = " +
            (-not [string]::IsNullOrWhiteSpace(
                $env:ILDCVF_E6_MEASURED_AUTHORIZATION
            ))
        )

        Show-ScientificReport -Id $AttemptId
    }

    default {
        throw (
            "Specify exactly one operation: " +
            "-Preflight, -Execute, or -Report."
        )
    }
}