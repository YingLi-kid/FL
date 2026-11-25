$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$folder = New-Item -ItemType Directory -Force (Join-Path $project 'build/qwen-runtime')
$archive = Join-Path $folder.FullName 'upstream-arm64.apk'
$url = 'https://github.com/Danmoreng/qwen3-tts-android/releases/download/v0.1.0/qwen3-tts-android-0.1.0-arm64-v8a.apk'
$expected = '4d4987bb329081330ed02d60b96aa84e470b362a936df90109c6feb16ae54e4a'
if (!(Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive).Hash.ToLowerInvariant() -ne $expected) {
    Invoke-WebRequest -Uri $url -OutFile $archive -TimeoutSec 120
}
if ((Get-FileHash -LiteralPath $archive).Hash.ToLowerInvariant() -ne $expected) {
    throw 'The SHA-256 of the upstream release does not match.'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($archive)
try {
    $abi = New-Item -ItemType Directory -Force (Join-Path $folder.FullName 'arm64-v8a')
    foreach ($name in @('libqwen3_tts_jni.so', 'libggml.so', 'libggml-base.so', 'libggml-cpu.so', 'libc++_shared.so')) {
        $entry = $zip.GetEntry("lib/arm64-v8a/$name")
        if (!$entry) { throw "Missing $name in the upstream release." }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $abi.FullName $name), $true)
    }
} finally {
    $zip.Dispose()
}
Write-Output 'Build the debug prototype with: .\gradlew.bat assembleDebug -PflQwenPrototype=true'
