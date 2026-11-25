# IndexTTS 2.5 与 CosyVoice 3 离线试用

这两款模型共用 [audio.cpp](https://github.com/0xShug0/audio.cpp/tree/a2423eb37617bce38b618e5a2ea2bb124f72c7ee) 的原生 CPU 推理库，试用版要求 Android 12+ ARM64。模型权重按需下载，在模拟器与真机之间使用相同的 ZIP。大型模型的生成速度和内存占用取决于设备。

| 品牌及厂商 | 本地模型 | ZIP 内文件 | 权重大小 |
| --- | --- | --- | ---: |
| 哔哩哔哩 IndexTeam | IndexTTS 2.5 | `index-tts2_5-q8_0.gguf` | 约 3.50 GB |
| 阿里云 FunAudioLLM | Fun‑CosyVoice 3.0 0.5B | `cosyvoice3-q8_0.gguf` | 约 2.26 GB |

在「设置 → 本地模型」选中所需型号，然后下载或导入 ZIP。回到主页，选「本地离线」和对应模型。录制或导入参考录音并保存音色，再用中文、日语或英语生成。CosyVoice 3 使用参考音频跨语种模式。两个模型都支持语速和音量。阿里云百炼 CosyVoice 的联网 API 仍在「联网」下使用独立音色和密钥。

权重及许可见 [固定版本的 GGUF 权重仓库](https://huggingface.co/audio-cpp/audio.cpp-gguf/tree/02f5de92a82339ebb722ea6fdadc14b7bec43a7f)、[IndexTTS 2.5 发布页](https://huggingface.co/IndexTeam/IndexTTS-2.5) 与 [CosyVoice 3 发布页](https://huggingface.co/FunAudioLLM/Fun-CosyVoice3-0.5B-2512)。

Windows 构建需要 Android NDK、CMake、Ninja 和 Git。

```powershell
.\scripts\prepare-audiocpp.ps1 -NdkPath <NDK目录> -CmakePath <cmake.exe> -NinjaPath <ninja.exe>
.\gradlew.bat assembleDebug
```

Gradle 在检测到 `build/audiocpp-runtime` 中对应 ABI 的 JNI 库后启用调试版模型。`-PflAudioCpp=false` 可关闭这一可选构建项。
