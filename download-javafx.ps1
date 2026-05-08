param(
    [string]$version = "21.0.5",
    [string]$arch = "windows-x64"
)

$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$destDir = Join-Path $PSScriptRoot "lib\javafx-sdk-$version"
$zipName = "openjfx-$version_$arch_bin-sdk.zip"
$url = "https://download2.gluonhq.com/openjfx/$version/openjfx-$version_$arch_bin-sdk.zip"

Write-Host "Downloading JavaFX $version ($arch) from $url"

if (Test-Path $destDir) {
    Write-Host "JavaFX SDK already exists at $destDir"
    exit 0
}

$tmp = Join-Path $env:TEMP $zipName
try {
    Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing -ErrorAction Stop
} catch {
    Write-Error "Failed to download JavaFX SDK. Please download manually from https://gluonhq.com/products/javafx and place it under lib/"
    exit 1
}

Write-Host "Extracting to $destDir"
try {
    Expand-Archive -Path $tmp -DestinationPath $PSScriptRoot\lib -Force
    # The zip typically contains a folder named javafx-sdk-<version>
    Remove-Item $tmp -Force
} catch {
    Write-Error "Failed to extract JavaFX SDK: $_"
    exit 1
}

Write-Host "JavaFX SDK downloaded and extracted to $destDir"
exit 0
