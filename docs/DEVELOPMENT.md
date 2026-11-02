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

## 第三轮：基础界面产品化

- 用户已反馈上一轮版本在 GitHub 编译成功；这仅确认上一轮构建，不代替本轮编译和真机验收。
- 界面统一为雾蓝 / 墨蓝的冷蓝时间刻度：集中深浅色、字体层级、等宽数字、静态水晶材质、边界与高光；不宣称真实实时背景折射。
- 手机使用图标加文字的悬浮胶囊底栏，720dp 起改为侧边导航；任务 / 待办分别保存滚动位置。主要页面可滚动，并限制宽屏内容宽度。
- 增加可翻周 / 回今天的周历。周历当前只改变明确标注的日数参考日期，仍展示全部记录，不冒充按 occurrence 筛选，也不写入历史操作。
- 日期卡突出标题、日期、模式和大日数；待办直接勾选完成 / 反选，编辑删除放入更多菜单。未接入的统计、备份、提醒、安全和外观设置仍明确说明，不添加假开关或虚构统计。
- 编辑页改为基本信息 / 计算方式 / 备注 / 预览分组，底部保存避开键盘；新增 Material 日期选择器，UTC 日历日编码、1900～2200 范围校验与默认回退。
- 新增 CalendarDates 与 5 个日期选择 / 周边界单元测试；当前共有 19 个 Kotlin 测试源码，留待云端执行。
- 版本码从 1 递增为 2，版本名仍为 1.0.0-dev。数据库 schema、应用身份、签名指纹及密钥不变，无需重新配置 Secrets。
- 待办完成筛选、月历、分类搜索和类型扩展仍待实现，不对分页后的已加载条目做伪全量筛选。
- 本地项目结构检查和现有 8 项 Python 签名脚本测试通过；只读复查指出的越界日期选择器问题已修复。未运行 Android 构建、Compose 仪器测试、截图或真机视觉验收；未 commit / push。

## 第四轮：签名渠道与覆盖升级

- 用户反馈覆盖安装失败；未提供旧 / 新 APK 或系统错误码，因此未断言设备实际失败的唯一原因。确认旧普通 CI 的 debug 包与固定签名版共用 local.daybook，debug 证书可能随全新 runner 改变，是确定的渠道设计风险。
- 固定签名包保留 local.daybook 与原始证书；debug 隔离为 local.daybook.debug、带调试标签和 -debug 版本后缀，Artifact 为 app-debug-isolated。两渠道数据互不迁移，不把共存称为覆盖升级。
- Signed development APK 支持默认分支 push 与手动触发，签名 job 保留默认分支限制、signing Environment 与只读权限，PR 不运行签名作业。-PrequireSigning=true 要求完整凭据；真实 APK 验签及固定指纹校验通过才上传。
- 新版设置从 Android 系统读取当前 APK 实际证书，显示包名、版本名 / 码、构建类型、证书 SHA-256 和固定证书匹配状态，可复制公开诊断。只读取当前应用，不申请查询其他包权限，不读取业务数据或密钥。
- 版本码递增为 3，签名产物命名 app-1.0.0-dev-3-arm64-v8a.apk。没有重新生成密钥、修改数据库 schema 或更新远程 Secrets。
- 增加 3 个安装身份 Kotlin 测试，累计 22 个，待云端执行。Python 签名脚本累计 10 项已通过，签名工具失败和版本不匹配阻止产物分发；项目静态结构检查与 git diff --check 通过。
- 设备旧包证书未知，不能保证覆盖已安装旧 debug 包；必须保留有重要数据的旧应用，核对 APK / 错误码后再处理。当前没有备份恢复功能，不能直接建议卸载或承诺无损迁移。
- 未执行本地 Android 构建、真实 APK 验签或设备覆盖安装，未 commit / push；后续由用户提交后取得云端结果。

## 第五轮：仅签名 APK 交付

- 按用户新约定，后续 Actions 仅构建固定密钥签名 release APK，取消全部 debug 和 unsigned release APK 打包任务。
- 普通 CI / PR 改为 testReleaseUnitTest + lintRelease，仅检查，不访问 Secrets，不打包，不上传任何 Artifact。签名流水线仍在默认分支 push / 手动触发，继续受 signing Environment 保护。
- 唯一上传步骤限定 app/build/distribution/*.apk；签名验证脚本只复制通过验签的单个 APK，不再生成分发用校验和、证书、构建 JSON、许可副本。公开校验值只记录在日志中。报告、Room schema、mapping 也不上传。
- 现有签名失败拒绝逻辑保持不变；测试严格断言分发目录只有一个 APK。10 项 Python 脚本测试、项目结构检查、严格 YAML / 步骤结构检查和 git diff --check 均通过。
- 结构校验确认所有工作流仅有一次 assembleRelease 且要求 requireSigning=true，合计一个 APK-only 上传步骤，无 debug 任务。22 个 Kotlin 测试转由 release 测试任务在云端执行，本地未运行 Android 构建。
- 本轮只修改构建交付策略与文档，不修改 APP 业务、版本码、签名密钥或 Secrets，不 commit / push。历史记录中旧的 debug / 报告上传约定由本节及开发要求第 0 节新约定覆盖；仓库源码和许可继续保留。

## 第六轮：基础管理闭环与真实统计

- 版本码递增为 4，继续使用 local.daybook 与原固定证书；保留上一轮仅签名 APK / 单一 APK 附件的 CI 改动，无 debug 或 unsigned APK 输出，未 commit / push。
- 搜索覆盖标题 / 备注，250ms 防抖后取消旧查询；中文子串、ASCII 大小写、字面 % / _ / 引号通过绑定参数处理。日期筛选全部 / 当天 / 未来 / 过去，待办筛选全部 / 未完成 / 已完成 / 当天 / 逾期未完成；参考日来自周历，待办状态仍为当前真实状态，不伪造历史快照。
- 日期升序 / 降序 / 标题排序，稳定 UUID 兜底；待办完成状态优先分组。选项分类型保存到 SavedStateHandle，支持进程恢复与重置；所有筛选在数据库执行，不限于已加载页。
- 点击卡片可进入实时观察数据库的详情，完整展示备注与日期规则，支持编辑返回、完成 / 反选、确认删除和系统文字分享；备注默认排除，用户确认后才包含。详情 ID 可恢复，记录不存在和读取失败有明确状态与重试。
- 统计页用 SQL 计算全局日期 / 待办 / 当前完成 / 逾期与今天及未来日期数量；周 / 月 / 年分布按原始日期和待办计划日期聚合，年图合并为 12 点，周 / 月补齐空白日，可点选读数量。口径明确，不宣称历史完成次数、专注时长或打卡趋势。
- 汇总失败不显示为零，统计结果带期间标签，防止快速切换画错期间；详情停止订阅后立即清空旧缓存，避免同 ID 重开短暂可操作旧完成状态，并补回归测试。
- Room 表与 schema 版本仍为 1，不改变已有事实字段；新增 DAO 查询、轻量聚合 DTO 和类型映射。当前子串与 CASE 排序尚未做十万级性能基准，不宣称满足最终性能预算。
- 新增生产 SQL 15 项测试，直接读取 Kotlin SQL 常量与 DAO 修改语句在内存 SQLite 验证；加上签名 10 项，25 项 Python 测试全部通过。Kotlin 测试共 34 项，覆盖查询选项、统计窗口、零数据、详情生命周期与读取重试，留待云端执行。
- 已完成一轮只读静态复查，指出的详情旧缓存问题已修复；项目结构、git diff --check 和仅签名 APK-only 策略检查通过。未执行本地 Android 构建、Room / Compose 仪器测试或设备覆盖验收，不能宣称本轮已编译成功。
- 新增 [开发构建验收清单](ACCEPTANCE.md)，记录具体操作与未验收项。农历 / 重复、分类 / 归档、数值任务、父子、专注、备份、提醒、小组件、图片分享、操作历史与完整设置仍为正式版必做，未标记完成。
