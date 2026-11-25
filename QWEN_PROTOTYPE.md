# 千问离线试验

千问试验使用 Android 12+ ARM64 的 CPU 本地推理。调试 APK 包含推理库，模型权重单独下载或导入。使用主页顶部的「本地离线 / 联网」切换模式，并在下拉框选择模型或服务商。本地模型集中在「设置 → 本地模型」，云密钥在「服务商管理」。

## 构建试验包

在 Windows 项目根目录运行：

```powershell
.\scripts\prepare-qwen-prototype.ps1
.\gradlew.bat assembleDebug
```

脚本校验社区 [安卓参考实现 v0.1.0](https://github.com/Danmoreng/qwen3-tts-android/releases/tag/v0.1.0) 的 APK，再提取 CPU 推理库。原生源码在独立的 [qwen3-tts.cpp 上游](https://github.com/Danmoreng/qwen3-tts.cpp/tree/4562731dc612cb87b4cd1eedac275a17d1078773)，模型权重由 [Qwen3-TTS GGUF 发布页](https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF/tree/main) 提供。

## 下载或导入

打开「设置 → 本地模型」，在列表选中型号，再点页面顶部的「导入 ZIP」。国际源以分块方式下载，暂停或失败后可以接着下载。自备 HTTPS 镜像必须提供相同文件。内置国内加速源待验证。

| 型号 | 主模型 | 总大小 |
| --- | --- | --- |
| Qwen3‑TTS 0.6B Base | `qwen-talker-0.6b-base-Q4_K_M.gguf` | 约 884 MB |
| Qwen3‑TTS 1.7B Base | `qwen-talker-1.7b-base-Q4_K_M.gguf` | 约 1.47 GB |

ZIP 包含所选主模型和共用的 `qwen-tokenizer-12hz-Q4_K_M.gguf`，允许把这两份文件放在同一个子目录。两个型号独立安装，已创建的本地千问音色可以在两者之间使用。

只有两份文件的大小和 SHA-256 都核对通过后才会安装。导入成功后，返回主页并切到「本地离线」。在「克隆音色」录制或导入已获授权的录音并保存，然后在「文字转语音」选择该音色与目标语言，输入台词、调整音量、试听或导出 WAV。真机性能和音色还原仍待实测。

本地和阿里云模式的合成文本均可输入最多 2000 字。千问在手机上分段合成并合并 WAV，长稿所需时间取决于设备和稿件长度。

千问离线模式提供 10 种样本与合成语言。录音结束后可以先试听、清除或重录。两个模式的生成结果都可在主页的「生成记录」中试听、导出和删除，清除缓存不影响记录。
