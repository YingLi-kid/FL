param(
    [Parameter(Mandatory)] [string] $NdkPath,
    [string] $CmakePath = 'cmake',
    [string] $NinjaPath = 'ninja',
    [ValidateSet('arm64-v8a', 'x86_64')] [string[]] $Abi = @('arm64-v8a')
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$work = Join-Path $repo 'build/audiocpp-qa'
$source = Join-Path $work 'audio.cpp'
$origin = 'https://github.com/0xShug0/audio.cpp.git'
$revision = 'a2423eb37617bce38b618e5a2ea2bb124f72c7ee'
$ndk = (Resolve-Path -LiteralPath $NdkPath).Path
$strip = Join-Path $ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe'
$bridge = Join-Path $repo 'app/src/main/cpp/audiocpp_android.cmake'

function Invoke-AudioCppTool([string] $Program, [string[]] $Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE." }
}

New-Item -ItemType Directory -Force -Path $work | Out-Null
if (!(Test-Path -LiteralPath (Join-Path $source '.git'))) {
    Invoke-AudioCppTool git @('init', $source)
}
& git -C $source cat-file -e "$revision^{commit}" 2>$null
if ($LASTEXITCODE -ne 0) {
    Invoke-AudioCppTool git @('-C', $source, 'fetch', '--depth', '1', $origin, $revision)
}
Invoke-AudioCppTool git @('-C', $source, 'checkout', '--detach', $revision)

$assets = Join-Path $repo 'build/audiocpp-assets'
New-Item -ItemType Directory -Force -Path "$assets/audiocpp-specs", "$assets/audiocpp-licenses" | Out-Null
foreach ($family in @('cosyvoice3','index_tts2')) {
    Copy-Item -LiteralPath "$source/model_specs/$family.json" -Destination "$assets/audiocpp-specs/$family.json" -Force
}
$licenses = [ordered]@{
    'audio.cpp.txt' = 'LICENSE'
    'ggml.txt' = 'external/ggml/LICENSE'
    'sentencepiece.txt' = 'external/sentencepiece/LICENSE'
    'libyaml.txt' = 'external/libyaml/License'
    'cJSON.txt' = 'external/cJSON/LICENSE'
    'utf8proc.txt' = 'external/utf8proc/LICENSE.md'
}
foreach ($name in $licenses.Keys) {
    Copy-Item -LiteralPath (Join-Path $source $licenses[$name]) -Destination "$assets/audiocpp-licenses/$name" -Force
}

foreach ($architecture in $Abi) {
    $output = Join-Path $work "engine-$architecture"
    Invoke-AudioCppTool $CmakePath @('-S', $source, '-B', $output, '-G', 'Ninja',
        "-DCMAKE_PROJECT_INCLUDE=$bridge", "-DCMAKE_MAKE_PROGRAM=$NinjaPath", "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake",
        "-DANDROID_ABI=$architecture", '-DANDROID_PLATFORM=android-31', '-DANDROID_STL=c++_static', '-DCMAKE_BUILD_TYPE=Release',
        '-DCMAKE_POSITION_INDEPENDENT_CODE=ON', '-DBUILD_SHARED_LIBS=OFF', '-DAUDIOCPP_BUILD_C_API=ON', '-DAUDIOCPP_BUILD_C_API_STATIC=ON',
        '-DAUDIOCPP_MODEL_SET=custom', '-DAUDIOCPP_MODELS=index_tts2,cosyvoice3', '-DENGINE_ENABLE_NATIVE_CPU=OFF',
        '-DENGINE_ENABLE_OPENMP=OFF', '-DGGML_OPENMP=OFF', '-DENGINE_ENABLE_LLAMAFILE=OFF', '-DAUDIOCPP_BUILD_NATIVE_MODEL_MANAGER=OFF',
        '-DENGINE_ENABLE_CUDA=OFF', '-DENGINE_ENABLE_VULKAN=OFF')
    Invoke-AudioCppTool $CmakePath @('--build', $output, '--target', 'fl_audiocpp_jni', '-j', '4')
    $destination = Join-Path $repo "build/audiocpp-runtime/$architecture"
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    Invoke-AudioCppTool $strip @('--strip-unneeded', '-o', "$destination/libfl_audiocpp_jni.so", "$output/bin/libfl_audiocpp_jni.so")
}
