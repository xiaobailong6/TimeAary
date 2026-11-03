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

## 第七轮：分类、置顶与归档

- 版本码递增为 5，版本名仍为 `1.0.0-dev`；继续使用 `local.daybook` 与原固定签名证书，未新增权限、SDK 或远程 Secrets。
- Room schema 从 1 迁移到 2：新增 `categories` 表（`id` / `name` / `position`，名称唯一索引）与 `entries` 的 `categoryId` / `pinned` / `archivedAt` / `archiveReason` 列，并补充 `index_entries_kind_archivedAt`、`index_entries_categoryId`。迁移只执行 CREATE / ALTER / CREATE INDEX，不改写既有列，不删除任何记录，迁移后旧数据为未分类、未置顶、未归档。
- 活动列表 SQL 增加 `archivedAt IS NULL` 与分类条件，排序改为置顶优先、待办完成状态分组、所选排序、稳定 UUID 兜底；归档列表使用独立常量 `ARCHIVE_PAGE`，只读已归档行并按归档时间倒序，支持标题 / 备注搜索，不继承日期筛选。
- 统计口径与列表一致：`SUMMARY` 与 `DISTRIBUTION` 排除归档行，并新增 `archivedCount` 单独计数；统计页在存在归档时显示“不计入以上统计”的说明，没有伪造历史完成时刻或归档时间线。
- 分类操作在仓库事务内完成：新增校验名称非空、≤20 字、忽略大小写重名与最多 50 个；重命名同样校验；上下移动交换相邻 position；删除先 `clearCategory` 把记录迁回未分类，再删除分类行，事件本身不删除。
- 归档写入归档时间（注入 Clock，便于回归）与原因，重复归档被 `archivedAt IS NULL` 条件挡住；恢复清空归档字段。置顶与归档均 bump `revision`。编辑保存只更新标题 / 备注 / 日期 / 计数 / 分类，不覆盖置顶与归档状态。
- UI 增加：列表页归档筛选与归档空状态、分类筛选胶囊、分类管理弹窗（新增 / 重命名 / 上移下移 / 删除确认）、卡片菜单置顶与归档 / 恢复、详情页置顶与归档 / 恢复及分类显示、编辑器分类选择。归档视图下禁用“新建”，避免新建后落在另一视图造成误解。
- 兼容旧状态：`EntryOptions.decode` 接受 3 项或 4 项（新增分类），`EntryDraft.decode` 接受 7 项或 8 项（新增分类），旧版本的进程恢复数据不会失效。
- 新增生产 SQL 用例覆盖归档在活动 / 归档列表间移动、归档排除统计、重复归档幂等、归档列表搜索与排序、置顶排序优先级、分类筛选与搜索组合、分类删除迁移、分类列表排序；Python 测试 33 项全部通过。Kotlin 测试累计 42 项（新增归档筛选与分类查询、置顶 / 归档 / 恢复动作、删除分类重置筛选、分类校验错误、旧条件与旧草稿兼容），留待云端 release 单元测试执行。
- 本轮未执行本地 Android 构建、Room 编译、KSP、迁移仪器测试或真机验收；仓库没有 JDK / Gradle，只有 Python 可运行。因此 `MIGRATION_1_2` 与新增 DAO / Compose 代码尚未由编译器验证，不能声称本轮已编译成功。
- 到期自动归档仍待实施：需要设置持久化与调度配合，本轮只提供手动归档与恢复，恢复时明示“不会再次自动隐藏”。
- 只读复查修正（含既有代码的编译风险）：新增 `ReadState.valueOrNull()` 统一取值，替换 `AnalysisContent` 与既有测试里 `ReadState.Ready` 的裸泛型用法，避免标星投影取值退化成 `Any`；`EntryOptions.normalized` 在归档筛选下清空 `categoryId`，因为归档流不接分类条件，界面也不再显示点了没反应的分类胶囊；归档记录隐藏置顶入口（归档流按归档时间排序，置顶在归档列表无可见效果）；分类新增失败时保留已输入的名称，只有确认写入成功才清空输入框。

## 第八轮：UI 补齐（设置、图标颜色、侧栏与批量选择）

- 版本码递增为 6，版本名仍为 `1.0.0-dev`；包名、签名证书、权限与第三方依赖均未变化。
- 设置持久化选择 SharedPreferences（`ui-settings`）而不是再建数据表：只有主题、字号、密度与入口顺序四项短字符串；`SettingsStore` 抽象出读写，`MemorySettingsStore` 供测试与默认构造使用，未知枚举名回退默认值。
- `AppTheme` 改为接收 `AppSettings`：主题三档（跟随系统 / 浅色 / 深色）决定色板，字号四档按系数缩放已定义的九个字号层级，系统字体缩放继续在 sp 之上叠加；紧凑密度只影响卡片备注行数，不改变数据。
- 底栏由 `NavEntry` 列表驱动，可排序与显隐；`AppSettings.normalized()` 保证设置入口常驻末尾且至少保留一个其他入口，`toggleEntry` / `moveEntry` 均为纯函数并有单元测试。
- 设置页重写：品牌与版本、外观三项、入口配置（隐藏 / 显示、上移 / 下移）、分类入口、安装身份核对、数据隐私说明；未实现能力仍明确说明，不添加占位开关。
- schema 从 2 迁移到 3：`entries` 增加可空 `icon` 与 `color` 两列，`MIGRATION_2_3` 只执行两条 ALTER TABLE；`AppActivity` 同时注册 1→2 与 2→3，升级链完整。图标与颜色以枚举名入库，未知名称读取时按未设置处理。
- 编辑器新增“外观”分组（图标网格 + 颜色圆点，再次点击清除），卡片与详情按所选色相显示图标与数字，统计长文说明未变化。
- 宽屏 ≥720dp 增加分类侧栏，与手机分类胶囊共用 `EntryOptions.categoryId`；归档流不接分类条件，归档视图显示“不按分类筛选”提示并隐藏胶囊。
- 批量选择：卡片长按进入选择模式（点击即勾选），操作条支持全选已加载、取消选择、批量移动到分类、批量删除（二次确认）；选择 ID 存 SavedStateHandle，进程恢复后仍可继续操作。
- 新增 DAO `deleteMany` / `assignCategoryMany`（`WHERE id IN (:ids)`），仓库在事务内按 400 条分批绑定；批量移动分类前校验目标分类存在，批量删除同时清理详情选中态。
- 测试：Python 生产 SQL 用例新增图标颜色保存与清空、批量删除只影响选中行、批量移动分类迁移校验，合计 36 项全部通过；Kotlin 新增 `AppSettingsTest`（默认值、显隐、排序、编解码、字号系数、外观枚举解析）、批量选择 / 批量删除 / 批量移动 / 选择恢复用例与草稿外观兼容用例，累计 57 项留待云端执行。
- 本轮仍未执行 Android 构建、Room 编译与迁移、Compose 预览或真机验收（本机没有 JDK / Gradle）；只完成 Python 回归、项目结构检查、括号与重复导入静态扫描和 `git diff --check`，不能声称构建 6 已编译成功。
- 未完成项：多选移入父任务、声音与振动开关、图片分享、月视图按日期任务标记。这些仍属正式版必做，本轮不标记完成。

## 第九轮：日历、统一组件与操作反馈

- 版本码递增为 7，版本名仍为 `1.0.0-dev`；包名、签名证书、权限与依赖不变，数据库 schema 仍为 3，本轮无迁移。
- `CalendarDates` 新增 `monthStart / monthGridStart / monthGrid / shiftMonth`：月视图固定 6 行 7 列（42 格），跨月日期由界面淡化显示；新增单元测试覆盖网格大小、周一开头、整月覆盖与跨月翻页。
- 新增 `CalendarPanel`（可折叠周 / 月历）替换旧的单行 `WeekCalendar` 并删除旧文件：周 / 月视图用统一分段控件切换，月视图翻月、今天高亮描边、跨月日期可点击；折叠状态、视图与月份锚点用 `rememberSaveable` 保留，选择日期后月份锚点跟随参考日。
- 新增 `GlassSegmentedControl` 统一分段控件：形状 + 颜色 + Tab 角色语义表达选中态，44dp 最小触控高度；统计页期间切换与日历视图切换已接入。
- 新增 `CreateTypePanel` 2 列新建类型面板（ModalBottomSheet）：日期记录与待办可创建，数值进度 / 计数 / 目标习惯 / 专注入口保留位置并标注“未实现”，点击无效；列表页“新建”按钮改为打开面板。
- 新增 `MessageHost` 统一消息入口：Popup 浮层不占布局高度，同文案去重、最多 3 条、4 秒自动消失或手动关闭，liveRegion 朗读；`AppViewModel.execute` 增加 `success` 参数，删除、完成、置顶、归档、恢复、分类增删与批量操作成功后提示，失败仍写 `error` 在页面内持久显示。
- 新增 `StatusCards` 统一状态卡：`GlassLoadingCard`、`ReadErrorCard`（迁移自 AnalysisContent）、`GlassStatusCard` 与 `EmptyRecordsCard`（区分没有记录 / 条件过窄 / 还没有归档）；列表加载与空状态改用统一组件。
- 测试：新增消息去重 / 上限 / 关闭、删除成功才提示与月历工具 3 项 Kotlin 用例，累计 65 项留待云端执行；Python 36 项不变全部通过。
- 本轮仍未执行 Android 构建、Compose 预览或真机验收（本机没有 JDK / Gradle）；完成 Python 回归、项目结构检查、括号与重复导入静态扫描、`git diff --check` 与签名 / 调用点逐项核对，不能声称构建 7 已编译成功。
