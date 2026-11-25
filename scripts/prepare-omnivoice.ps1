param(
    [Parameter(Mandatory)] [string] $NdkPath,
    [string] $CmakePath = 'cmake',
    [string] $NinjaPath = 'ninja',
    [ValidateSet('arm64-v8a', 'x86_64')] [string[]] $Abi = @('arm64-v8a')
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$work = Join-Path $repo 'build/omnivoice-qa'
$source = Join-Path $work 'omnivoice.cpp'
$runtimeRevision = '040c8b344d8c670ce1475194751d119b5ef82c78'
$ndk = (Resolve-Path -LiteralPath $NdkPath).Path
$tools = Join-Path $ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin'
$bridge = Join-Path $repo 'app/src/main/cpp/omnivoice_bridge.cpp'

function Invoke-Native([string] $Program, [string[]] $Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE." }
}

New-Item -ItemType Directory -Force -Path $work | Out-Null
if (!(Test-Path -LiteralPath (Join-Path $source '.git'))) {
    Invoke-Native git @('clone', '--no-checkout', '--filter=blob:none', 'https://github.com/ServeurpersoCom/omnivoice.cpp.git', $source)
}
Invoke-Native git @('-C', $source, 'checkout', '--detach', $runtimeRevision)
Invoke-Native git @('-C', $source, 'submodule', 'update', '--init', '--recursive')

$notices = Join-Path $repo 'build/omnivoice-notices'
New-Item -ItemType Directory -Force -Path $notices | Out-Null
Copy-Item -LiteralPath "$source/LICENSE" -Destination "$notices/omnivoice-cpp-MIT.txt" -Force
Copy-Item -LiteralPath "$source/ggml/LICENSE" -Destination "$notices/omnivoice-ggml-MIT.txt" -Force

foreach ($architecture in $Abi) {
    $output = Join-Path $work "engine-$architecture"
    $sse42 = if ($architecture -eq 'x86_64') { 'ON' } else { 'OFF' }
    Invoke-Native $CmakePath @('-S', $source, '-B', $output, '-G', 'Ninja',
        "-DCMAKE_MAKE_PROGRAM=$NinjaPath", "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake",
        "-DANDROID_ABI=$architecture", '-DANDROID_PLATFORM=android-31', '-DANDROID_STL=c++_static',
        '-DCMAKE_BUILD_TYPE=Release', '-DCMAKE_POSITION_INDEPENDENT_CODE=ON', '-DBUILD_SHARED_LIBS=OFF',
        '-DOMNIVOICE_SHARED=ON', '-DGGML_NATIVE=OFF', '-DGGML_OPENMP=OFF', '-DGGML_CPU_KLEIDIAI=OFF',
        "-DGGML_SSE42=$sse42", '-DGGML_BUILD_TESTS=OFF', '-DGGML_BUILD_EXAMPLES=OFF',
        '-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--exclude-libs,ALL')
    Invoke-Native $CmakePath @('--build', $output, '--target', 'omnivoice', '-j', '4')
    $triplet = if ($architecture -eq 'arm64-v8a') { 'aarch64-linux-android31' } else { 'x86_64-linux-android31' }
    Invoke-Native (Join-Path $tools 'clang++.exe') @("--target=$triplet", '-std=c++17', '-O2', '-shared', '-fPIC',
        '-fvisibility=hidden', '-static-libstdc++', '-Wl,--exclude-libs,ALL', "-I$source/src", $bridge,
        "$output/libomnivoice.so", '-o', "$output/libfl_omnivoice_jni.so")
    $dest = Join-Path $repo "build/omnivoice-runtime/$architecture"
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    foreach ($name in @('libomnivoice.so', 'libfl_omnivoice_jni.so')) {
        Invoke-Native (Join-Path $tools 'llvm-strip.exe') @('--strip-unneeded', '-o', "$dest/$name", "$output/$name")
    }
}
