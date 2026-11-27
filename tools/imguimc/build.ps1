param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$sourceDirectory = Join-Path $projectRoot '.gradle/imguimc-upstream'
$repositoryDirectory = Join-Path $projectRoot '.gradle/imguimc-maven'
$commit = 'ee1b528a1390f4de1ed80da00b759b62c90d8ec7'
$version = '2.0.5-itemlab.ee1b528.3'
$patchPath = Join-Path $PSScriptRoot '26.3.patch'

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]] $GitArguments)
    & git -c "safe.directory=$sourceDirectory" @GitArguments
    if ($LASTEXITCODE -ne 0) { throw "git failed with exit code $LASTEXITCODE" }
}

if (!(Test-Path -LiteralPath $sourceDirectory)) {
    Invoke-Git clone --no-checkout https://github.com/FoundryMC/imguimc.git $sourceDirectory
    Invoke-Git -C $sourceDirectory checkout --detach $commit
} else {
    $currentCommit = Invoke-Git -C $sourceDirectory rev-parse HEAD
    if ($currentCommit.Trim() -ne $commit) {
        throw "ImGuiMC checkout is at $currentCommit; expected $commit. Use a clean checkout at the pinned revision."
    }
}

$expectedPatch = (Get-Content -LiteralPath $patchPath -Raw -Encoding UTF8).Replace("`r`n", "`n").Trim()
# Let Git write UTF-8 directly; Windows PowerShell's native-output decoder can
# otherwise corrupt non-ASCII source comments before the patch comparison.
$currentPatchPath = Join-Path $projectRoot '.gradle/imguimc-working.patch'
Invoke-Git -C $sourceDirectory diff HEAD --no-ext-diff --binary "--output=$currentPatchPath"
$currentPatch = ([string](Get-Content -LiteralPath $currentPatchPath -Raw -Encoding UTF8)).Replace("`r`n", "`n").Trim()
if (!$currentPatch) {
    Invoke-Git -C $sourceDirectory apply -- $patchPath
} elseif ($currentPatch -ne $expectedPatch) {
    throw 'The pinned ImGuiMC checkout has changes beyond tools/imguimc/26.3.patch. Preserve or resolve them before rebuilding.'
}

$previousJavaHome = $env:JAVA_HOME
try {
    if (!$env:JAVA_HOME -or !(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
        # Windows PowerShell treats java's successful stderr output as errors.
        $ErrorActionPreference = 'Continue'
        try {
            $javaProperties = & java -XshowSettings:properties -version 2>&1
        } finally {
            $ErrorActionPreference = 'Stop'
        }
        $javaHomeProperty = $javaProperties | Select-String '^\s*java.home = (.+)$'
        if (!$javaHomeProperty) { throw 'Install Java 25 and make java available on PATH or set JAVA_HOME.' }
        $env:JAVA_HOME = $javaHomeProperty.Matches[0].Groups[1].Value.Trim()
    }

    Push-Location $sourceDirectory
    try {
        & .\gradlew.bat "-Dmaven.repo.local=$repositoryDirectory" "-Pmod.version=$version" `
            :fabric:26.3:publishToMavenLocal --configure-on-demand --no-configuration-cache --console=plain
        if ($LASTEXITCODE -ne 0) { throw "ImGuiMC build failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
}

Write-Output "Built ImGuiMC $version from $commit in $repositoryDirectory"
