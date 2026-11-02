# 首个签名开发版构建

## 当前版本与边界

当前构建版本为 `1.0.0-dev`，版本码 `1`，applicationId 为 `local.daybook`。它是基础日期任务 / 待办开发版，不是全功能 `1.0.0` 正式版本。不会自动打标签、创建 Release 或修改仓库内容。

普通 `Android checks` 在 push / PR / 手动触发时运行，无需密钥，执行脚本测试、Kotlin 单元测试、Lint、debug 编译与 unsigned release 压缩编译；只上传 debug APK 和检查报告，不分发 unsigned APK。

`Signed development APK` 仅支持默认分支手动运行，使用 `signing` Environment：脚本测试、单元测试与 release Lint 通过后才解码密钥，再构建 R8 压缩的签名 APK，通过 `apksigner verify`、证书指纹、包名、版本、SDK 与 native ABI 检查后上传 Artifact。

## 手动配置 GitHub

1. 手动 commit / push 项目文件，不提交任何密钥文件，也不恢复原有 `gradlew` 状态来冒充本轮新文件。
2. 打开仓库 **Settings → Environments → New environment**，创建名称严格为 `signing` 的环境。允许时设置人工批准者和仅允许默认分支的部署规则。
3. 在该环境的 **Environment secrets** 中加入下表四项，也可使用同名 Repository secrets；同名 Environment secret 优先。
4. 每项 Secret 的 Value 是外部签名材料目录中对应 `.txt` 文件的**全部内容**，不是文件路径、文件名、引号或 JSON 对象。
5. 打开 **Actions → Signed development APK → Run workflow**，选择默认分支，按环境规则批准。
6. 成功后下载 `app-signed-development` Artifact 并解压，安装其中的 `app-1.0.0-dev-arm64-v8a.apk`。检查报告和生成的 Room schema 在 `signed-checks` 中。

| Secret 名称 | 取值 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `ANDROID_KEYSTORE_BASE64.txt` 全文，PKCS#12 二进制的 Base64 编码 |
| `ANDROID_KEYSTORE_PASSWORD` | `ANDROID_KEYSTORE_PASSWORD.txt` 全文 |
| `ANDROID_KEY_ALIAS` | `ANDROID_KEY_ALIAS.txt` 全文，本次为 `app` |
| `ANDROID_KEY_PASSWORD` | `ANDROID_KEY_PASSWORD.txt` 全文，本次与存储密码相同 |

不需要配置 `ANDROID_KEYSTORE_PATH`，它由流水线在临时 runner 中解码后自动设置。不要将 Secrets 写入 YAML、Gradle properties、Issue、截图或日志。Base64 不是加密，其保密级别与 keystore 相同。

## 本次签名身份

- 格式：PKCS#12，文件名 `app-signing.p12`。
- 别名：`app`。
- 算法：RSA-3072，证书 SHA256withRSA，自签名，无品牌 / 用户 / 仓库标识。
- 证书 SHA-256：`2b24963703f956cdfffe56d2b3ddd1b1f6c0b6ebe278f334d9e16407f37b6e56`。
- 证书 SHA-1：`39d3ca8533e94415efc77be7ac3ad5f3688d46b5`（仅识别用途，不用于安全校验）。
- CI 固定指纹在 `.github/signing-certificate.sha256`，这是公开证书信息，可以提交；不包含私钥。
- 实际证书起止日期与 keystore 文件校验和见外部 `certificate-info.json`。

本次生成材料位于项目之外的会话临时目录，具体路径由生成结果给出。请立即复制到至少两个安全备份位置；临时目录会被清理，不能作为长期保管位置。`github-secrets.json` 和四个 Secret 文本包含敏感数据，建议与密钥一起加密离线保管、不要使用聊天或公开网盘传播。

## 文件与验证

外部材料包括 `app-signing.p12`、四份 Secret 文本、`github-secrets.json`、公开证书 `signing-certificate.pem`、`certificate-info.json`。已经本地验证加密 PKCS#12 可读、别名、公私钥匹配、随机消息签名 / 验签、证书自签名与 Base64 往返。尚未使用 JDK keytool 或真实 APK 验证；Java 17 读取和 Android 签名验证由云端流水线执行。

云端分发内容包括 APK、`SHA256SUMS.txt`、`certificate-sha256.txt`、`build-info.json` 与许可文件；不会上传 keystore、密码、Base64、runner 临时文件或 R8 mapping。临时密钥在作业结束时清理。公开分发前仍须完成全功能、第三方许可、真机、安装及升级验收。

## 安装、升级与版本

- debug 包使用临时 debug 签名，不保证跨 runner 一致；不能直接覆盖安装本次签名包。若已装 debug 包，先明确处理数据再卸载；当前没有备份功能，卸载会删除数据。
- 后续签名开发版和正式版必须沿用相同 applicationId 与本次密钥。丢失密钥后无法正常覆盖升级现有安装，重新生成密码或 Secrets 不会恢复密钥。
- 每次向用户分发更新时递增 `version.properties` 中 versionCode，并按版本规则调整版本名；当前 `-dev` 在构建层统一添加，不在源码散落硬编码。
- 正式 `1.0.0` 发布前需要移除开发通道后缀、递增版本码、建立正式标签校验与发布流程；不得给当前基础开发版打 `v1.0.0` 标签。
- APK 中无 native 库时，ABI filter 不等于禁止在非 ARM64 设备安装；文件名只表达构建目标，不伪造安装限制承诺。

## 排错

- 缺少 Secrets：核对环境名称 `signing` 与四个 Secret 名称；没有密钥不会回退到 debug 签名。
- 签名不匹配：核对导入的是本次 keystore 及其配套密码 / 别名；不要为让流水线通过随意改指纹。
- 首轮构建失败：保留 Actions 具体失败步骤与 `signed-checks` / `checks` 报告，再按真实错误修复。当前尚无云端成功结果，不将静态检查当作编译成功。
