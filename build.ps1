# Build whale-sysmon APK -- plain command line, no Gradle / Android Studio.
# Usage: powershell -ExecutionPolicy Bypass -File build.ps1
$ErrorActionPreference = "Continue"

$BUILD_ROOT = "C:\Users\zhang\WorkBuddy\_tools\android-build"
$JDK   = "$BUILD_ROOT\jdk\jdk-17.0.13+11"
$SDK   = "$BUILD_ROOT\sdkroot"
$BT    = "$SDK\build-tools\34.0.0"
$AJAR  = "$SDK\platforms\android-34\android.jar"
$APP   = $PSScriptRoot

$env:JAVA_HOME = $JDK
$env:Path = "$JDK\bin;$env:Path"

$BUILD = "$APP\build"
if (Test-Path $BUILD) { Remove-Item $BUILD -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$BUILD\classes", "$BUILD\dex", "$BUILD\gen" | Out-Null

Write-Host "[1/7] aapt2 compile resources" -ForegroundColor Cyan
& "$BT\aapt2.exe" compile --dir "$APP\res" -o "$BUILD\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "[2/7] aapt2 link" -ForegroundColor Cyan
& "$BT\aapt2.exe" link -o "$BUILD\base.apk" `
    -I $AJAR `
    --manifest "$APP\AndroidManifest.xml" `
    --java "$BUILD\gen" `
    --min-sdk-version 24 --target-sdk-version 34 `
    --version-code 1 --version-name "0.1-alpha" `
    "$BUILD\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[3/7] javac" -ForegroundColor Cyan
$srcFiles = @(Get-ChildItem -Path "$APP\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$genFiles = @(Get-ChildItem -Path "$BUILD\gen" -Recurse -Filter *.java -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
$shizukuGen = @(Get-ChildItem -Path "$APP\shizuku\gen" -Recurse -Filter *.java -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
$allJava = $srcFiles + $genFiles + $shizukuGen
Write-Host ("      {0} source files" -f $allJava.Count)
$argFile = "$BUILD\javac.args"
$allJava | ForEach-Object { '"' + ($_ -replace '\\','/') + '"' } | Set-Content -Path $argFile -Encoding ASCII
& "$JDK\bin\javac.exe" --release 8 -encoding UTF-8 -nowarn `
    -classpath $AJAR -d "$BUILD\classes" "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "[4/7] d8" -ForegroundColor Cyan
$classes = @(Get-ChildItem -Path "$BUILD\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName -replace '\\','/' })
$classArg = "$BUILD\d8.args"
$classes | Set-Content -Path $classArg -Encoding ASCII
Write-Host ("      {0} classes" -f $classes.Count)
& "$BT\d8.bat" --lib $AJAR --min-api 24 --output "$BUILD\dex" "@$classArg"
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[5/7] inject classes.dex" -ForegroundColor Cyan
$py = "C:\Users\zhang\AppData\Local\Programs\Python\Python312\python.exe"
$inject = @'
import zipfile, shutil, sys, os
base, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]
shutil.copyfile(base, out)
with zipfile.ZipFile(out, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
print("  ok:", os.path.getsize(out), "bytes")
'@
$inject | Out-File -FilePath "$BUILD\_inject.py" -Encoding UTF8
& $py "$BUILD\_inject.py" "$BUILD\base.apk" "$BUILD\dex\classes.dex" "$BUILD\unsigned.apk"
if ($LASTEXITCODE -ne 0) { throw "dex inject failed" }

Write-Host "[6/7] zipalign" -ForegroundColor Cyan
& "$BT\zipalign.exe" -f -p 4 "$BUILD\unsigned.apk" "$BUILD\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[7/7] sign" -ForegroundColor Cyan
$KS = "$APP\sysmon.jks"
if (-not (Test-Path $KS)) {
    & "$JDK\bin\keytool.exe" -genkeypair -keystore $KS -alias sysmon `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass sysmon123 -keypass sysmon123 `
        -dname "CN=WhaleSysMon, OU=Personal, O=Whale, C=CN" 2>$null | Out-Null
    if (-not (Test-Path $KS)) { throw "keystore generation failed" }
}
& "$BT\apksigner.bat" sign --ks $KS --ks-pass pass:sysmon123 --key-pass pass:sysmon123 --out "$APP\SysMon.apk" "$BUILD\aligned.apk" 2>$null
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Host ""
Write-Host "DONE: $APP\SysMon.apk" -ForegroundColor Green
"{0:N0} bytes" -f (Get-Item "$APP\SysMon.apk").Length

