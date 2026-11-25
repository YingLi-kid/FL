# OmniVoice 离线试用

FL音坊通过 [omnivoice.cpp](https://github.com/ServeurpersoCom/omnivoice.cpp/tree/040c8b344d8c670ce1475194751d119b5ef82c78) 在设备上运行 OmniVoice。试用包面向 Android 12+ ARM64，建议手机有 8 GB 内存。权重约 1.39 GB，单独下载。

在「设置 → 本地模型」选择「OmniVoice 0.6B」，点「下载模型」或「导入 ZIP」。ZIP 包含 `omnivoice-base-Q8_0.gguf` 和 `omnivoice-tokenizer-F32.gguf`，权重来自 [Serveurperso GGUF 仓库](https://huggingface.co/Serveurperso/OmniVoice-GGUF/tree/361609388ae572a820d085185bbbe2a2aac4b30e)。

从主页选择「本地离线 → OmniVoice 0.6B」，在「克隆音色」导入或录制样本，填写样本里实际说的话并保存。然后在「文字转语音」选择中文、日语或英语。录音和原文保存在本机，删除音色时一并移除。

[OmniVoice 预训练权重](https://huggingface.co/k2-fsa/OmniVoice#license)使用 CC BY-NC 许可。量化仓库列有对应模型和编解码器的来源。下载或导入由用户在 APP 中发起。

Windows 构建需要 Android NDK、CMake、Ninja 和 Git。

```powershell
.\scripts\prepare-omnivoice.ps1 -NdkPath <NDK目录> -CmakePath <cmake.exe> -NinjaPath <ninja.exe>
.\gradlew.bat assembleDebug
```

推理库放在 `build/omnivoice-runtime`。Gradle 在库已准备好的情况下启用 OmniVoice 调试版功能，也可传入 `-PflOmniVoice=false` 构建其他功能。
