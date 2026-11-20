# FL音坊

<img src="branding/fl-fox.png" alt="FL音坊" width="128" />

**中文** | [English](README.en.md) | [下载测试版](https://github.com/YingLi-kid/FL/releases/tag/v1.0.1) | [官方使用文档](https://www.fluentic.cn/config/soft/)

基于 Kotlin、Jetpack Compose 和 Material 3 的 Android 声音复刻与文字转语音客户端。当前接入阿里云百炼 CosyVoice，使用者自行提供 API Key。

> 早期版本：核心流程已在 Android 模拟器验证，仍在完善真机兼容性与异常场景处理。仓库名称为 FL。

## 下载

[**下载 FL音坊 1.0.1 公开测试版 APK**](https://github.com/YingLi-kid/FL/releases/download/v1.0.1/FLYinFang.apk) · [发布说明及历史版本](https://github.com/YingLi-kid/FL/releases) · [SHA-256 校验文件](https://github.com/YingLi-kid/FL/releases/download/v1.0.1/SHA256SUMS.txt)

支持 Android 7.0 及以上。请下载发布附件中的 `FLYinFang.apk`；GitHub 自动生成的 Source code 压缩包是源码，不能直接安装。安装后仍需自行配置阿里云 API Key。

首版为公开测试版。如果手机此前安装了开发用 Debug 包，可能因签名不同而无法直接覆盖；请先保存所需音频和必要配置，再决定是否卸载旧包。卸载会删除应用本地数据。

## 名称由来

「FL音坊」的名字源于作者个人网站域名中的 `fluentic`，寓意「流利的音色处理工坊」。

## 功能

- **音色克隆**：现场录音或通过系统文件选择器导入音频；本地校验实际时长为 5～30 秒、大小不超过 10 MB。
- **审核状态恢复**：服务返回音色 ID 后立即持久化；APP 运行且服务商已配置、启用时，自动查询待审核音色。
- **文字转语音**：选择预设音色或审核通过的克隆音色，输入最多 500 字符，调整语速、音量和音调。
- **播放与导出**：播放、暂停、继续、重播，通过系统保存对话框导出 MP3。
- **草稿保留**：切换标签页、打开设置或发生可恢复的界面重建时保留表单；音频缓存被清理后需要重新选择样本。
- **服务商配置**：填写并保存自己的密钥，手动启用服务商。未配置密钥时会提示配置，不生成模拟成功结果。
- **深色界面**：霓虹渐变与玻璃风格组件。

目前仅阿里云服务商已接入，数据模型为后续扩展保留了入口。界面主要为中文，中英文 README 不代表 APP 已有英文界面。

## 使用方法

完整教程请查看[FL音坊官方使用文档](https://www.fluentic.cn/config/soft/)，APP 设置页也提供入口。

1. 在[阿里云百炼控制台](https://bailian.console.aliyun.com/)开通所需语音模型服务，创建可访问相应模型的 API Key。当前代码使用北京地域接口。
2. 打开 APP → 右上角「设置」→「服务商管理」→「阿里云 · 百炼 CosyVoice」，填写密钥并启用。
3. 在「克隆音色」页确认声音授权、填写名称、选择样本语言，然后录音或上传清晰的单人语音。
4. 点击「开始克隆音色」。音色将显示在「我的音色」中，等待状态变为「可使用」。查询中断时可重新打开 APP 继续查询。
5. 点击已可用音色，或切换到「文字转语音」选择音色；输入文字并生成，使用播放按钮试听或下载按钮保存。

### 模型、语言与费用

- 当前复刻与合成均使用 `cosyvoice-v3-flash`。复刻音色绑定模型，不能任意换到其他模型使用。
- 界面可选中文、英语、日语、韩语、法语、德语、西班牙语、俄语和粤语。具体语言效果受模型与音色能力影响；语言选择不会翻译输入文字，也不保证所有组合可用。建议先使用与样本相同的语言合成。
- APP 不提供免费 API Key，也不代收服务费用。克隆与合成的实际价格、额度、开通条件以阿里云控制台为准，费用由使用者承担。
- 官方参考：[声音复刻](https://help.aliyun.com/zh/model-studio/voice-clone-design-http-api)。

## 隐私与声音授权

- 仅克隆本人声音或已获得明确授权的声音。
- 点击克隆时，选定的音频将上传到阿里云；点击生成时，文字、音色 ID 与合成参数会发送到阿里云。请求使用用户填写的密钥鉴权。
- 密钥和音色档案保存在应用私有目录的 DataStore 中；录音、导入样本与合成结果使用本地缓存，导出的音频保存在用户选择的位置。
- **当前密钥存储尚未使用 Android Keystore 加密，系统备份规则也尚未专门排除密钥。** 请使用权限受限的测试密钥，并在共享设备或发布前评估此限制。
- 仓库不包含可用密钥、账号配置、签名文件、测试录音或生成音频。不要把这些内容提交到 Git。

## 构建

当前版本配置如下，以下版本来自仓库配置，并非通用的最低版本建议：

| 项目 | 当前配置 |
| --- | --- |
| Android Gradle Plugin | 9.4.0 |
| Gradle Wrapper | 9.8.0 |
| Gradle daemon JDK | 25 |
| Compose compiler plugin | 2.2.10 |
| Compose BOM | 2026.02.01 |
| compileSdk / targetSdk | 37 / 37 |
| minSdk | 24（Android 7.0） |

准备支持上述工具链的 Android Studio，安装 Android SDK Platform 37，并配置 JDK 25。Gradle daemon 的 Java 版本由 `gradle/gradle-daemon-jvm.properties` 指定；Java 源码兼容级别仍为 11，这与运行 Gradle 所需的 JDK 版本不同。

Android Studio 会生成本机 `local.properties`。也可手动创建该文件，指定 SDK，例如：

```properties
# 示例；替换为本机 SDK 路径。不要提交此文件。
sdk.dir=/path/to/Android/Sdk
```

如果 Gradle 无法定位 JDK 25，可在**用户级** `~/.gradle/gradle.properties` 中配置 `org.gradle.java.installations.paths`。Windows 示例：`org.gradle.java.installations.paths=C\:/path/to/jdk-25`。不要把个人绝对路径写进仓库。

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
```

macOS / Linux：

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```

首次构建需要联网下载 Gradle、插件和依赖；JDK 自动解析也可能需要联网。APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。通过 Android Studio 安装到模拟器或手机，或执行 `adb install -r <APK路径>`。

## 正式签名打包

Debug APK 用于开发测试。公开分发时应使用你自己长期保管的签名文件构建 Release APK。

1. 准备自己的 Android 发布密钥，将其放在本机 `signing/` 目录。
2. 将 `keystore.properties.example` 复制为根目录的 `keystore.properties`，填写本地密钥路径、别名和密码。
3. 执行 `gradlew.bat assembleRelease`（macOS/Linux 使用 `./gradlew assembleRelease`）。配置完整时，签名 APK 输出为 `app/build/outputs/apk/release/app-release.apk`。

未提供本地签名配置时，Release 构建仍会生成未签名安装包，不能直接作为公开安装包使用。`signing/` 和 `keystore.properties` 已被 Git 忽略；请独立备份签名文件及密码，后续更新应使用同一签名。不要把签名材料或真实密钥上传到仓库。

## 测试

- `RegressionTest` 覆盖草稿恢复、无密钥提示、密钥输入、返回导航、缓存清理、真实媒体时长、音色存储与列表更新、音频暂停及重播。
- 已实际验证：上传约 18.9 秒的官方示例 → 创建真实音色 → 审核 `OK` → 克隆音色中文合成 → 播放、暂停、继续及重播。
- 云端测试 `CloudSmokeTest` **默认跳过**。若要手动复现，先在 APP 中配置密钥，并上传克隆一个名为 `UploadTest1120` 的音色，等待审核通过；然后显式运行：

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.example.myapplication.CloudSmokeTest -e runCloudSmoke true com.example.myapplication.test/androidx.test.runner.AndroidJUnitRunner
```

该测试会发起真实合成并可能计费。Gradle 设备测试可能重新安装或卸载测试目标；如需保留已配置数据，建议构建测试 APK 后用 `adb install -r` 覆盖安装，并通过 `adb shell am instrument` 运行。普通回归测试也会清理应用缓存，请在专用测试设备上运行。

## 代码结构

```text
app/src/main/java/com/example/myapplication/
├── MainActivity.kt
├── data/       # 服务商、音色模型与 DataStore 持久化
├── service/    # CosyVoice HTTP 请求、样本校验、播放
└── ui/         # 克隆、合成、设置、服务商配置及主题
```

## 已知限制

- 尚未充分覆盖不同品牌真机、弱网、后台切换和系统终止进程等场景。表单恢复不等于云端任务在所有情况下都能继续执行。
- 待审核音色在 APP 运行时轮询，尚未实现后台任务调度；本地删除音色记录不会删除服务商云端音色。
- 尚无完整的合成历史管理、账号同步、在线更新或英文界面。检查更新入口会说明尚未接入在线更新服务。
- 密钥加密、备份策略及后续版本管理仍需完善；公开测试安装包使用独立发布签名，维护者应按上方说明备份签名材料。当前应用 ID 为 `com.example.myapplication`，版本为 `1.0`。
- 语言选择、预设音色与模型的全部组合尚未逐项验证。费用说明与服务能力应以服务商最新文档为准。

欢迎通过 [Issues](https://github.com/YingLi-kid/FL/issues)反馈问题。请附上设备型号、Android 版本、复现步骤与脱敏后的错误提示，不要附带 API Key 或未经授权的录音。

## 许可证

[GNU General Public License v3.0](LICENSE)。修改和分发时请遵守许可证。

Copyright © 2026 影篱 YingLi
