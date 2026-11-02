# 签名、安装与覆盖升级

## 先区分两种安装包

| 渠道 | Artifact | applicationId | 用途 |
| --- | --- | --- | --- |
| 固定签名开发版 | `app-signed-development` | `local.daybook` | 日常安装与连续覆盖升级 |
| 独立 debug | `app-debug-isolated` | `local.daybook.debug` | 临时调试，与日常包共存，不保证跨 runner 证书稳定 |

当前版本源为 `version.properties`，构建版本名统一增加 `-dev`。本轮版本码 `3`，签名文件名为 `app-1.0.0-dev-3-arm64-v8a.apk`。它仍不是全功能正式版。

**能够安装的 APK 已有签名，不等于它使用了固定发布密钥。** Android 默认 debug 签名在全新 GitHub runner 上可能重新生成。旧流程的 app-debug 与固定签名版同用 local.daybook，却证书不同，这是覆盖失败的一条明确风险路径。本轮将 debug 应用身份隔离，不能反向修复已经安装的旧证书包。

## 如何取得升级包

1. 用户手动 commit / push 代码；代理不执行提交或推送。
2. 默认分支 push 后，`Signed development APK` 自动执行；仍可在 Actions 手动 Run workflow。其他分支跳过签名 job，PR 不运行签名作业。
3. 作业使用 `signing` Environment。若配置了人工批准，每次必须批准后才运行，不能误把等待批准当成没有签名产物。
4. 作业运行脚本测试、Kotlin 单元测试和 release Lint，再解码 Secrets；以 `-PrequireSigning=true` 构建 release，凭据不完整直接失败，不回退为 debug / unsigned 包。
5. 通过真实 `apksigner verify`、固定证书指纹、包名、版本码、SDK 和 native ABI 检查后，上传 `app-signed-development`。只有成功作业的该 Artifact 才是日常升级渠道。
6. 解压下载，核对 `build-info.json` 的 commit / versionCode 和证书指纹，再安装。普通 `Android checks` 的 app-debug-isolated 不是升级包。

工作流不创建 Release、不打标签、不修改远端仓库。自动运行指用户推送触发，与代理提交无关。

## GitHub Secrets 配置

在仓库 Settings → Environments 中创建 `signing`，建议设置默认分支部署限制与人工批准者。在 Environment secrets 中配置四项；也可使用同名 Repository secrets，环境同名值优先。不要将签名权限授予 fork 或 PR 代码。

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 之前提供的 `ANDROID_KEYSTORE_BASE64.txt` 全文，不是文件路径 |
| `ANDROID_KEYSTORE_PASSWORD` | `ANDROID_KEYSTORE_PASSWORD.txt` 全文 |
| `ANDROID_KEY_ALIAS` | `ANDROID_KEY_ALIAS.txt` 全文，本次为 `app` |
| `ANDROID_KEY_PASSWORD` | `ANDROID_KEY_PASSWORD.txt` 全文 |

`ANDROID_KEYSTORE_PATH` 由流水线解码到 runner 临时目录后自动设置，无需用户配置。Base64 等同私钥材料，不是加密；不得写入源码、日志、截图、Issue 或 Artifact。

本轮没有重新生成密钥。继续使用原始 `app-signing.p12`（PKCS#12 / RSA-3072）及配套密码。证书 SHA-256 固定为：

```text
2b24963703f956cdfffe56d2b3ddd1b1f6c0b6ebe278f334d9e16407f37b6e56
```

公开指纹保存在 `.github/signing-certificate.sha256`，同时由 Gradle 注入 BuildConfig。设置页从 Android 系统读取当前应用实际签名，比对后显示结果；不会仅凭 buildType 显示“签名正确”。仅比较当前签名证书，不实现密钥轮换。

原签名材料在生成时放于项目外会话临时目录，必须自行复制到至少两个长期安全备份位置；不要假设临时目录一直存在。保管 keystore 与配套密码，丢失后不能通过重设 GitHub Secrets 恢复。

## 覆盖失败的处理

覆盖安装通常要求**同包名、同证书（本项目不做轮换）、版本码不降级**。同一版本重试不一定需要增加版本码，但向用户分发新更新时本项目要求严格递增。只改文件名或版本名不修复签名冲突。

| 现象 / 检查结果 | 处理 |
| --- | --- |
| 新旧均为固定签名 local.daybook | 核对双方证书、版本码，确认没有下载旧 run 的 Artifact |
| 旧包为先前的 app-debug（也叫 local.daybook） | 必须取得旧 APK / 原签名材料核对。没有旧私钥，不能给新代码签出相同证书；新固定证书不能直接覆盖旧证书 |
| 新包为 local.daybook.debug | 这是独立调试包，共存不是更新，数据也独立 |
| 签名工作流失败或等待 Environment 批准 | 查看该 job，确认四项 Secrets、环境名与批准状态；不要转而下载 debug 当升级包 |
| 证书一样但仍安装失败 | 提供系统具体错误或 adb install 的错误码，继续区分降级、空间、设备策略等原因 |

**旧应用有重要数据时不要先卸载。** 当前尚未实现备份恢复，卸载会删除本地数据。不同签名的同包名应用无法通过修改 Gradle、增加版本码、关闭签名校验等方式无损覆盖。可先保留旧应用，提供新旧 APK（无需密码 / 私钥）及完整错误；必要时再确认能否用旧 debug 包支持的调试方式导出数据，不承诺所有设备可用。

新版设置 → 安装与签名可复制公开诊断：实际包名、版本码、构建类型、证书 SHA-256、固定证书匹配结果。摘要不含任务、备注或密钥。旧版没有诊断入口时需直接检查旧 APK。

## 验证与分发边界

本地独立脚本测试使用合成 fixture 和模拟工具输出来覆盖失败分支，不等同真实 APK 验签。Android 编译、Lint、Kotlin 测试、真实 apksigner 和设备覆盖安装仍在云端 / 真机验证。

签名作业附件包含 APK、SHA256SUMS.txt、certificate-sha256.txt、build-info.json 和许可文件，不包含密钥、密码、Base64 或 R8 mapping。临时密钥在 always 清理步骤删除。ABI filter 与文件名不等同纯 JVM APK 的 ARM64 安装限制；正式发布仍需要完整功能、许可、安装与数据迁移验收。
