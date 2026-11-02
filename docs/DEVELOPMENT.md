# 开发记录

## 执行约定

- 本地没有构建环境，Android 编译与测试仅在 GitHub Actions 执行。
- 源码、包路径、资源文件名不得包含品牌、GitHub 用户名、仓库名或远程地址；品牌仅放在展示资源值。
- 禁止建立 `main` 等默认占位式目录；通过 sourceSets 指定实际路径。
- 保持单个 app 模块，以真实职责分包，避免空模块与过度分层。
- 不覆盖用户暂存区；已有 gradlew 路径状态保持不变。

## 工程布局

```text
app/
  src/
    AndroidManifest.xml
    code/
      AppActivity.kt
      model/
      datetime/
      data/
      ui/
    resources/
  tests/
gradle/libs.versions.toml
version.properties
.github/workflows/ci.yml
```

## 首轮工具链决策

| 项目 | 固定值 |
| --- | --- |
| 应用身份 | local.daybook（正式发布前确认冻结） |
| 最低 / 编译 / 目标 API | 26 / 35 / 35 |
| JDK / Gradle | 17 / 8.11.1 |
| AGP / Kotlin | 8.9.1 / 2.1.20 |
| Compose BOM / KSP | 2025.04.01 / 2.1.20-1.0.32 |
| Room / Paging | 2.7.1 / 3.3.6 |

这些是固定的首轮兼容组合，不宣称是最新版本。官方 Maven 上已确认 AGP、Kotlin、Compose BOM、Room、KSP 与 Lifecycle 对应 POM 存在；尚未完成实际编译兼容验证。Gradle 分发包 SHA-256 从官方端点取得并固定到 CI。

初期使用显式 ViewModel Factory，顶层导航只保存四个页面索引；Hilt、Navigation Compose 与共享构建约定在功能规模扩大时接入，未将其标为已完成。标准玻璃材质使用稳定高不透明底色与描边，实时背景模糊和高档效果尚未接入。

## 已写入的首批实现

- 公历单次日期任务与待办基础 CRUD、完成 / 反选。
- java.time 日期服务，可注入 Clock；UI 前台跨午夜刷新。
- Room 表、复合索引、稳定 UUID、事务写入与 Paging 限量缓存。
- 可恢复草稿、输入校验、保存门控、持久错误、加载与重试、删除与退出确认。
- 统一深浅色主题、标准档玻璃卡与悬浮底栏。
- 原图生成五档传统图标和自适应图标，未提供 monochrome 素材。
- 已有 14 个 Kotlin 单元测试：6 个日期、4 个草稿、4 个 ViewModel 保存 / 恢复测试，等待 CI 执行。

## 云端操作

推送代码或创建 PR 后，Android checks 自动执行 Python 脚本测试、Kotlin 单元测试、Lint、debug 与 unsigned release 压缩编译；通过后上传 app-debug、检查报告和生成的 Room schema。首次成功后需将真实生成的 schema 回存源码，不手写虚构 schema。

已提供手动签名开发版工作流，使用 signing Environment 的四个 Secrets，通过测试与 Lint 后才解码密钥；构建后校验签名、固定证书指纹、应用身份、版本、SDK 与 native ABI，并上传签名 APK 和校验和。不会创建 Release 或执行 commit / push，操作说明见 [签名与云端打包](SIGNING.md)。实际版本统一添加 -dev 后缀，当前为 1.0.0-dev / 1；正式版本发布前必须递增版本码。ABI filter 不构成纯 JVM APK 的 ARM64 安装限制保证。

## 验证与后续

当前机器 Git 方式连接 GitHub 失败，API 可用于核对 Action 提交。尚未推送、触发 Actions 或取得 APK。没有执行本地 Gradle、JUnit、Lint 或真机测试。当前实现仅为开发起点，M0～M7 和原始全功能勾选保持未完成。

下一阶段：先取得云端编译与 Lint 结果、回存 schema，增加 Room CRUD / 进程恢复测试；再完成各任务类型独立详情表、农历适配与重复规则、首页分类搜索、数值任务与操作记录。后续继续专注、统计、备份、提醒、小组件与正式签名发布，完整清单不删减。

### 本轮已执行的静态检查

- 全部 Android XML 可解析，Version Catalog TOML 及版本引用检查通过。
- CI YAML 结构、只读权限、事件触发器、Action 40 位 SHA 格式检查通过；三个 Action 提交通过 GitHub API 确认存在。
- Gradle 8.11.1 分发校验和与官方 `.sha256` 端点一致。
- 五档图标尺寸符合 48 / 72 / 96 / 144 / 192 像素，品牌资源副本与根目录原图字节一致。
- 新建 app 路径无禁用目录名，Kotlin 源码未包含品牌名、GitHub 用户名或仓库地址。
- `git diff --check` 无报错；已有暂存新增 gradlew、工作区删除的状态保持不变。静态检查不等同 Android 编译、业务测试或真机验收。

## 第二轮：首个签名开发版准备

- 编辑表单拆为 EntryEditor，草稿由 ViewModel + SavedStateHandle 管理；新建时固定 UUID，输入校验或存储失败后保留草稿，保存不再调用旧 Activity 页面回调。
- 新建草稿日期使用可注入 Clock；界面显示 BuildConfig 的真实开发版本。
- 增加 release R8 配置、共享云端工具链 Action、手动签名打包、证书指纹固定、签名临时文件清理与密钥文件忽略规则。
- 在项目外生成 RSA-3072 / PKCS#12 签名密钥和四项 Secrets 文本；验证加密读取、公私钥、随机签名验签、证书自签名、Base64 往返。密钥未进入项目，用户必须及时离线备份会话临时材料。
- 本地 Python 签名脚本 8 项测试已通过，包含缺失 Secrets、非法 / 超长 Base64、准备 / 清理、包校验分支、错误证书 / 包名 / native ABI 拒绝。包验证测试使用合成 fixture 与工具输出模拟，不冒充真实 APK 验签。
- 只读复查发现的 Kotlin 伴生方法引用编译问题已改为显式调用；不再扩大审查范围。
- 未运行本地 Android 构建、Kotlin 测试或 Lint；用户手动 commit / push 后由 GitHub Actions 给出首个真实编译结果。当前不能声明已编译成功、已取得 APK 或已完成真机验收。
- 最终静态验证通过：两条 workflow 与 composite action 的 YAML 无重复键且步骤结构有效，Python 语法正确，工作区未发现生成的真实密码 / Base64，固定证书指纹与外部密钥一致。仅保留原有暂存 gradlew 状态，未执行 git add / commit / push。
