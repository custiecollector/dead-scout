param(
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$InnoDir = "$env:LOCALAPPDATA\Programs\Inno Setup 6",
    [string]$InnoSetupUrl = 'https://github.com/jrsoftware/issrc/releases/download/is-6_7_3/innosetup-6.7.3.exe',
    [switch]$InstallInno
)

$ErrorActionPreference = 'Stop'

function Resolve-RepoRoot {
    $scriptDir = Split-Path -Parent $PSCommandPath
    return (Resolve-Path (Join-Path $scriptDir '..\..')).Path
}

function Resolve-JavaTool {
    param([string]$Name)
    $candidates = @()
    if ($JdkHome) { $candidates += (Join-Path $JdkHome "bin\$Name") }
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) { $candidates += $command.Source }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) { return (Resolve-Path $candidate).Path }
    }
    throw "Could not find $Name. Set JAVA_HOME to a JDK 17+ directory."
}

function Resolve-InnoCompiler {
    param([string]$RepoRoot)
    $command = Get-Command iscc.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $candidates = @(
        (Join-Path $InnoDir 'ISCC.exe'),
        "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
        "$env:ProgramFiles\Inno Setup 6\ISCC.exe",
        "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe"
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) { return (Resolve-Path $candidate).Path }
    }
    if (-not $InstallInno) { return $null }
    New-Item -ItemType Directory -Force -Path $InnoDir | Out-Null
    $winget = Get-Command winget.exe -ErrorAction SilentlyContinue
    if ($winget) {
        Write-Host "Installing Inno Setup with winget into $InnoDir"
        & $winget.Source install --id JRSoftware.InnoSetup -e --accept-package-agreements --accept-source-agreements --scope user --location $InnoDir --silent
        if ($LASTEXITCODE -ne 0) { throw "winget failed installing Inno Setup with exit code $LASTEXITCODE" }
    } else {
        $downloadDir = Join-Path (Join-Path $RepoRoot 'build') 'windows-native\downloads'
        New-Item -ItemType Directory -Force -Path $downloadDir | Out-Null
        $installer = Join-Path $downloadDir 'innosetup.exe'
        Write-Host "Downloading Inno Setup to $installer"
        & curl.exe -L --fail --retry 3 -sS --output $installer $InnoSetupUrl
        if ($LASTEXITCODE -ne 0) { throw "curl failed downloading Inno Setup with exit code $LASTEXITCODE" }
        $installProcess = Start-Process -FilePath $installer -ArgumentList @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART','/CURRENTUSER','/NOICONS',"/DIR=$InnoDir") -PassThru -Wait
        if ($installProcess.ExitCode -ne 0) { throw "Inno Setup installer failed with exit code $($installProcess.ExitCode)" }
    }
    $iscc = Join-Path $InnoDir 'ISCC.exe'
    if (Test-Path $iscc) { return (Resolve-Path $iscc).Path }
    throw "Inno Setup install completed but ISCC.exe was not found at $iscc"
}

function Get-VersionName {
    param([string]$RepoRoot)
    $gradle = Get-Content (Join-Path $RepoRoot 'app\build.gradle') -Raw
    if ($gradle -notmatch "versionName\s+'([^']+)'") { throw 'versionName not found in app/build.gradle' }
    return $Matches[1]
}

function Get-NumericVersion {
    param([string]$VersionName)
    if ($VersionName -match '(\d+\.\d+\.\d+)') { return $Matches[1] }
    return '0.0.0'
}

$repoRoot = Resolve-RepoRoot
$versionName = Get-VersionName $repoRoot
$numericVersion = Get-NumericVersion $versionName
$innoVersion = "$numericVersion.0"
$buildRoot = Join-Path $repoRoot 'build'
$classes = Join-Path $buildRoot 'desktop\classes'
$jarPath = Join-Path $buildRoot 'deadscout-desktop.jar'
$nativeRoot = Join-Path $buildRoot 'windows-native'
$inputDir = Join-Path $nativeRoot 'input'
$appImageRoot = Join-Path $nativeRoot 'app-image'
$appDir = Join-Path $appImageRoot 'DeadScout Desktop'
$installerDir = Join-Path $nativeRoot 'installer'
$issPath = Join-Path $repoRoot 'packaging\windows\DeadScout.iss'

$javac = Resolve-JavaTool 'javac.exe'
$jarTool = Resolve-JavaTool 'jar.exe'
$java = Resolve-JavaTool 'java.exe'
$jpackage = Resolve-JavaTool 'jpackage.exe'

Write-Host "DeadScout Windows native build"
Write-Host "repo=$repoRoot"
Write-Host "version=$versionName"
Write-Host "javac=$javac"
Write-Host "jpackage=$jpackage"

Remove-Item -Recurse -Force $classes, $inputDir, $appImageRoot, $installerDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes, $inputDir, $installerDir | Out-Null

$coreSources = Get-ChildItem -Path (Join-Path $repoRoot 'app\src\main\java\org\deadscout\core') -Filter '*.java' | Sort-Object FullName | Select-Object -ExpandProperty FullName
$desktopSources = Get-ChildItem -Path (Join-Path $repoRoot 'desktop\src\org\deadscout\desktop') -Filter '*.java' | Sort-Object FullName | Select-Object -ExpandProperty FullName
$sources = @($coreSources) + @($desktopSources)

& $javac -encoding UTF-8 -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

New-Item -ItemType Directory -Force -Path $buildRoot | Out-Null
& $jarTool --create --file $jarPath --main-class org.deadscout.desktop.DeadScoutDesktopGui -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar create failed with exit code $LASTEXITCODE" }
Copy-Item -Force $jarPath (Join-Path $inputDir 'deadscout-desktop.jar')

Write-Host 'Running desktop build check'
& $java -jar $jarPath --self-test
if ($LASTEXITCODE -ne 0) { throw "desktop build check failed with exit code $LASTEXITCODE" }

$jpackageArgs = @(
    '--type', 'app-image',
    '--name', 'DeadScout Desktop',
    '--vendor', 'DeadScout',
    '--app-version', $numericVersion,
    '--input', $inputDir,
    '--main-jar', 'deadscout-desktop.jar',
    '--main-class', 'org.deadscout.desktop.DeadScoutDesktopGui',
    '--dest', $appImageRoot,
    '--java-options', '-Dfile.encoding=UTF-8',
    '--add-modules', 'java.desktop,java.logging'
)

Write-Host 'Creating native Windows app image'
& $jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image failed with exit code $LASTEXITCODE" }

Copy-Item -Force (Join-Path $repoRoot 'LICENSE') $appDir
Copy-Item -Force (Join-Path $repoRoot 'docs\desktop-readme.txt') $appDir
Copy-Item -Force (Join-Path $repoRoot 'docs\capture-helpers.md') $appDir
Copy-Item -Force (Join-Path $repoRoot 'packaging\windows\README-WINDOWS.txt') $appDir
Copy-Item -Force (Join-Path $repoRoot 'packaging\windows\setup-capture-helpers-windows.ps1') $appDir
Copy-Item -Force (Join-Path $repoRoot 'packaging\windows\setup-capture-helpers-windows.cmd') $appDir
Copy-Item -Force (Join-Path $repoRoot 'packaging\windows\deadscout-windows-capture-helper.ps1') $appDir
Copy-Item -Force (Join-Path $repoRoot 'packaging\windows\deadscout-windows-capture-helper.cmd') $appDir

$exe = Join-Path $appDir 'DeadScout Desktop.exe'
if (-not (Test-Path $exe)) { throw "Native launcher was not created: $exe" }

$iscc = Resolve-InnoCompiler $repoRoot
$setupExe = $null
if ($iscc) {
    Write-Host "Building Inno Setup installer with $iscc"
    & $iscc "/DSourceDir=$appDir" "/DOutputDir=$installerDir" "/DMyAppVersion=$versionName" "/DMyAppVersionNumeric=$innoVersion" $issPath
    if ($LASTEXITCODE -ne 0) { throw "Inno Setup compiler failed with exit code $LASTEXITCODE" }
    $setupExe = Join-Path $installerDir "DeadScout-Desktop-$versionName-Setup.exe"
    if (-not (Test-Path $setupExe)) { throw "Expected installer not found: $setupExe" }
} else {
    Write-Warning 'Inno Setup compiler not found. App image was built, but installer .exe was not. Re-run with -InstallInno or install Inno Setup 6.'
}

$zipPath = Join-Path $buildRoot "deadscout-$versionName-windows-native.zip"
Remove-Item -Force $zipPath -ErrorAction SilentlyContinue
Compress-Archive -Path $appDir -DestinationPath $zipPath -Force

$sumPath = Join-Path $buildRoot "SHA256SUMS-$versionName-windows-native.txt"
$hashLines = @()
$hashLines += "$((Get-FileHash $jarPath -Algorithm SHA256).Hash.ToLower())  deadscout-desktop.jar"
$hashLines += "$((Get-FileHash $exe -Algorithm SHA256).Hash.ToLower())  DeadScout Desktop.exe"
$hashLines += "$((Get-FileHash $zipPath -Algorithm SHA256).Hash.ToLower())  $(Split-Path $zipPath -Leaf)"
if ($setupExe) { $hashLines += "$((Get-FileHash $setupExe -Algorithm SHA256).Hash.ToLower())  $(Split-Path $setupExe -Leaf)" }
Set-Content -Path $sumPath -Value $hashLines -Encoding ascii

Write-Host 'Created:'
Write-Host "  $jarPath"
Write-Host "  $exe"
Write-Host "  $zipPath"
if ($setupExe) { Write-Host "  $setupExe" }
Write-Host "  $sumPath"
