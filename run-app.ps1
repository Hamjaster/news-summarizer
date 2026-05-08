$ErrorActionPreference = "Stop"
# Runs the program with Ctrl+Alt+N by calling this script directly.
Set-Location $PSScriptRoot

# Keep paths for optional source compile fallback.
$sourceDir = Join-Path $PSScriptRoot "src/main/java/com/example/newssummarizer"
$outDir = Join-Path $PSScriptRoot "out"
$jsonJar = Join-Path $PSScriptRoot "lib/json-20231013.jar"

# Prefer JAVA_HOME if set; otherwise try the bundled JDK 25 path.
$defaultJdk = Join-Path $env:LOCALAPPDATA "Programs\Microsoft VS Code\jdk-25.0.2"
if (-not $env:JAVA_HOME -and (Test-Path $defaultJdk)) {
    $env:JAVA_HOME = $defaultJdk
}

# Ensure Maven is available for JavaFX run.
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    $defaultMaven = Join-Path $env:USERPROFILE ".maven\maven-3.9.15\bin\mvn.cmd"
    if (Test-Path $defaultMaven) {
        $env:PATH = (Split-Path $defaultMaven) + ";" + $env:PATH
    }
}

# Fail fast with a clear message if Maven is still missing.
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Error "Maven (mvn) was not found. Install Maven or add it to PATH."
    exit 1
}

function Import-DotEnvFile {
    param(
        [string]$filePath
    )

    if (-not (Test-Path $filePath)) {
        return
    }

    Get-Content -Path $filePath | ForEach-Object {
        $line = $_.Trim()

        if ([string]::IsNullOrWhiteSpace($line)) {
            return
        }

        if ($line.StartsWith("#")) {
            return
        }

        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -le 0) {
            return
        }

        $key = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1).Trim()

        if ($value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')) {
            $value = $value.Substring(1, $value.Length - 2)
        } elseif ($value.Length -ge 2 -and $value.StartsWith("'") -and $value.EndsWith("'")) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
}

$rootEnvPath = Join-Path $PSScriptRoot ".env"
$sourceEnvPath = Join-Path $sourceDir ".env"

if (Test-Path $rootEnvPath) {
    Import-DotEnvFile -filePath $rootEnvPath
} elseif (Test-Path $sourceEnvPath) {
    Import-DotEnvFile -filePath $sourceEnvPath
}

# Use Maven to run JavaFX so module paths and resources are handled correctly.
mvn -q -DskipTests javafx:run
exit $LASTEXITCODE