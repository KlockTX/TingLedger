# 亭宣记账 · Android 记账 App 设计方案

> 包名 `com.klocktx.moneyblocks` ／ 应用名「亭宣记账」
> 文档状态：已开发至 v1.25.0（versionCode 38）。最新几轮改动见 §47–§51，§1–§5 为最初评审时的需求与选型记录。

---

## 1. 我理解的需求

一个**纯本地、离线可用、打开就能记**的支出记账工具。核心不是报表有多炫，而是**把"记一笔"的成本压到两次点击以内**。

| 你的描述 | 我的落地理解 |
|---|---|
| 分层结构 | UI / Domain / Data 三层，依赖单向向内，Domain 层纯 Kotlin 可单测 |
| **内置计算功能** | **数字键盘即计算器**：支持 `23+15` `48.5-3` 这类表达式直接求值后入账（打车费过路费分开给、抹个零，都是真实场景）。不是只能输一个数 |
| 自定义账单（吃饭/打车/水电） | 账单可增删改、改名、换颜色换图标、拖拽排序、归档（不删历史记录） |
| 点击账单弹出数字键盘 | 自绘 Compose 键盘覆盖系统 IME，从底部弹层升起 |
| 提交后自动计入该账单总账 | 账单合计由 SQL 聚合实时算出，**不做内存累加**，保证任何时候刷新都对 |
| 每一次提交都保留条目 | 条目永久留存，可回看、可改金额、可改备注、可删 |
| 可以备注 | 每条支持一行备注文本，提交时可填、事后可补 |
| 计算总开支 | 全局总额 + 本月 / 今日 / 累计，多档口径 |
| 简单分析 | 账单占比、近 30 天趋势、Top 账单、月度环比、日均 |

**一个关键取舍先说在前面**：全程只做**支出**，不做收入账户。你的描述里没有收入，加上账户/转账会让数据模型复杂度翻倍（账户余额、科目对冲），而收益对你这个场景是零。以后要收入，加一个 `type` 字段即可，架构上不堵死。

---

## 2. 核心流程

```
              ┌─────────────────────────────────┐
              │  首页（账单网格）                │
              │  本月总开支 ¥1,284.50           │
              │  [吃饭 682] [打车 240] [水电 …]  │
              └───────────────┬─────────────────┘
                              │ 点账单
                              ▼
              ┌─────────────────────────────────┐
              │  账单底部弹层                    │
              │  ┌───────────────────────────┐  │
              │  │ 23+15           ¥38.00    │  │  ← 表达式实时求值
              │  │ 备注：打车去机场           │  │
              │  ├─────┬─────┬─────┬─────────┤  │
              │  │  C  │  ⌫  │  −  │   +     │  │
              │  │  7  │  8  │  9  │         │  │
              │  │  4  │  5  │  6  │  提交   │  │
              │  │  1  │  2  │  3  │         │  │
              │  │  0      │  .   │         │  │
              │  └─────┴─────┴─────┴─────────┘  │
              └───────────────┬─────────────────┘
                              │ 提交 → Room 插入条目 → Flow 自动回流
                              ▼
              ┌─────────────────────────────────┐
              │  账单详情页                      │
              │  合计 ¥682.00 · 共 34 笔        │
              │  今天  午饭·拉面        ¥32.00  │
              │  今天  早饭            ¥8.50    │
              │  昨天  超市            ¥126.30  │
              └─────────────────────────────────┘
```

提交后**不跳转**，键盘收起 + 顶部 SnackBar 提示「已记 ¥38.00 · 撤销」。留在原地以便连续记（这是记账 App 最关键的体验点——买完菜连着记三笔不能每次回到首页）。

「撤销」在 5 秒内直接删掉那条，比弹出确认框快。

---

## 3. 技术选型（**全部按你本机实测环境钉死，不猜版本**）

我先扫了你本机环境，发现 `Documents/AndroidProjects/CPAC/CPACmobile` 已经**成功构建出 debug + release APK**，所以直接复用那套已验证的工具链，避免任何试错：

| 项 | 值 | 来源 |
|---|---|---|
| Android SDK | `C:/Users/KlockTX/AppData/Local/Android/Sdk` | 现存 `local.properties` |
| compileSdk / targetSdk | **36** | 本机已装 `android-36` |
| minSdk | **26**（Android 8.0） | 沿用你现工程口径 |
| AGP | **8.13.0** | 本机 Gradle 缓存已存在，**你的工程注释明确写了"AGP 8.13 为本机上限"** |
| Gradle | **9.1.0** | 本机唯一完整解压的发行版（`~/.gradle/wrapper/dists`）。你工程 wrapper 虽写 9.5.1，但其 dist 实际不在机器上 |
| Kotlin | **2.4.0** | 你现工程在用 |
| JVM Target | **17** | 你现工程 `compileOptions` |
| Compose BOM | **2026.06.01** | 见下方「红线成因」 |
| Room | **2.8.5** | 阿里云镜像实测 200 可拉 |
| KSP | **2.3.12** | Maven Central 实测最新版 |

依赖仓库沿用你现工程的阿里云 + 腾讯镜像（`maven.aliyun.com` / `mirrors.cloud.tencent.com`），国内网络可直接拉取。

**构建结果（已实测通过，非计划）**：`assembleDebug` + `assembleRelease` + `testDebugUnitTest` 全绿，23 个单测 0 失败；debug APK 12 MB，release（开 R8 混淆资源收缩）约 1.6 MB。

### 实测暴露的红线成因（这条最值得记下来）

我最初选了本机已缓存的 Compose BOM `2026.08.00`，`checkDebugAarMetadata` 直接失败并给出 22 条 issue，核心两条：

- `androidx.compose.*:1.12.0` **要求 compileSdk ≥ 37**（本机只装了 `android-36`、`android-36.1`，没有 37 的 platform）
- 同一批 artifact **要求 AGP ≥ 9.1.0**（本机上限 8.13.0）

这就是你工程注释里 `compose-bom<=2026.06.01` 的真实由来 —— 不是保守，而是硬约束。改用 `2026.06.01` + `activity-compose 1.10.1` 后通过。

### 两条执行期风险的验证结论

1. **KSP 版本线：已验证可用。** KSP 从 2.x 起改用独立版本号（实测最新 `2.3.12`，不再有 `2.4.0-2.0.x` 那种跟随 Kotlin 的形式），与 Kotlin 2.4.0 搭配正常，Room 2.8.5 的 `@Database` 注解处理通过并导出了 `app/schemas/.../1.json`。预设的降级路径（Kotlin 2.2.21 + KSP 2.2.21-2.0.5）**没有动用**。
2. **命令行 JAVA_HOME 为空：已按预案绕过。** `PATH` 里的 java 是 JDK 25，AGP 8.13 官方支持到 17/21，构建全程显式使用 `C:/Program Files/Java/jdk-21.0.11`。Android Studio 里用它自带的 JBR 21 即可，无需改动。

### 一处选型的第一轮判断错了，已修正

我在 1.0 里以「`material-icons-extended` 版本线不同步、会白吃 8 MB」为由放弃了 Material 图标，改用 emoji。这个结论只对了一半：

- 实测 `1.7.8` 的 AAR 要求的是 compileSdk **34**，低于本机 36，与 `compose-bom 2026.06.01` 共存编译通过 —— 我担心的冲突并不存在。
- 体积上，**debug** APK 确实从 11.8 MB 涨到 18.4 MB（图标全量进包），但 **release 走 R8 后只从 1.60 MB 涨到 1.65 MB**，未用到的图标全部被 shrink 掉了。也就是说真实代价只发生在 debug 包上。

所以 1.1 起界面图标全部换成矢量图标：账单图标来自 `BillIcons`（25 个预设 Material 矢量图标，存 `iconKey` 到数据库，改图标不影响历史条目），界面功能图标直接用 Material 矢量（`ChevronLeft/Right`、`ArrowBack`、`Add`、`DeleteOutline`、`Backspace`、`CalendarMonth`、`KeyboardArrowUp/Down`、底部导航 `ReceiptLong/QueryStats/Widgets`）。

**教训记下来**：体积和兼容性这类判断，一次构建的实测远比推理可信；我这次是先给了结论、后被数据修正。

---

## 4. 分层架构

```
┌───────────────────────────────────────────────────────────┐
│  ui/  presentation                                        │
│  Compose Screens · ViewModels (StateFlow<UiState>)        │
│  自定义数字键盘 · Material 3 主题                          │
│         ↓ 只依赖 Domain 的接口和模型                       │
├───────────────────────────────────────────────────────────┤
│  domain/                                                  │
│  models (Bill, Entry, Money, BillSummary, StatSnapshot) │
│  repository 接口（ICatalogRepo, IEntryRepo, IStatsRepo）    │
│  usecase  (AddEntry, UpdateEntry, DeleteEntry,            │
│            CreateBill, CalcExpression, BuildStats)       │
│  ★ 表达式求值器：纯函数，零 Android 依赖，全覆盖单测        │
│         ↑ 实现 Domain 接口                                 │
├───────────────────────────────────────────────────────────┤
│  data/                                                    │
│  Room: entities · DAOs · AppDatabase                      │
│  repository 实现（Entity ⇄ Domain mapper）                 │
│  预置账单种子数据                                          │
├───────────────────────────────────────────────────────────┤
│  core/                                                    │
│  Money 格式化（分 ⇄ "¥1,284.50"）· 日期区间 · 依赖注入容器  │
└───────────────────────────────────────────────────────────┘
```

**分层落地方式：单 Gradle 模块 + 包级分层。**

理由：记账 App 体量小，包分层已经完全满足"依赖单向、Domain 可独立单测"的目的；拆成 5 个 Gradle module 会带来配置成本、增量构建变慢，而隔离能力对这个规模是过剩的。**如果你明确要 Gradle 多模块，说一声，我在 M0 之前改，成本很低（越晚改越贵）。**

DI 用**手写 `AppContainer`**（一个 lazy 组装图，挂在 `Application` 上），不引 Hilt。这个规模下 Hilt 的注解处理器成本和收益不成比例；将来模块拆多、需要 `@Inject` 到 Compose 里再换。

---

## 5. 数据模型

### 5.1 表结构（Room）

```kotlin
@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,          // "吃饭"
    val iconKey: String,       // "rice" / "taxi" / "bolt"… 映射到 Material 图标
    val colorKey: String,      // 8 色调色板枚举名
    val sortOrder: Int,        // 首页网格顺序
    val createdAt: Long,       // epoch millis
    val archived: Boolean = false,  // 归档=首页隐藏，历史条目与合计保留
)

@Entity(
    tableName = "entries",
    foreignKeys = [ForeignKey(
        entity = BillEntity::class,
        parentColumns = ["id"], childColumns = ["billId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("billId"), Index("occurredAt"), Index("billId", "occurredAt")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val amountCents: Long,     // ★ 一律存"分"，见 5.2
    val note: String,          // 可为空串
    val occurredAt: Long,      // 记账归属时间（支持补记昨天）
    val createdAt: Long,       // 实际录入时间，用于审计
)
```

### 5.2 金额精度：这是全案最容易埋雷的地方

**规则：`Long` 存分，全链路禁止 `Double` 参与金额运算。**

`0.1 + 0.2 == 0.30000000000000004` 在记账 App 里意味着用户加总会永久偏差几分钱，且随着条目变多误差不可预期。所以：

```kotlin
@JvmInline value class Cents(val value: Long) {
    fun toDisplayString(): String   // 128450L -> "¥1,284.50"
}
```

- 数字键盘输入 `48.5` → 立即规整为 `4850` 分；小数位**最多 2 位**，第 3 位拒绝输入（键盘上直接不给按）
- 表达式 `23+15.5` 在**分**的域内求值：`2300 + 1550 = 3850`
- 展示层唯一一处 `format` 函数，其余地方不做任何浮点转换
- 聚合全部交给 SQL `SUM(amountCents)`，返回 `Long`，不存在浮点累加

### 5.3 关键查询（响应式合计）

```sql
-- 账单卡片：一次查询拿到所有账单的合计与笔数，Flow 自动随写入刷新
SELECT b.id, b.name, b.iconKey, b.colorKey,
       IFNULL(SUM(e.amountCents), 0) AS totalCents,
       COUNT(e.id)                   AS entryCount
FROM bills b LEFT JOIN entries e ON e.billId = b.id
WHERE b.archived = 0
  AND (:monthStart IS NULL OR e.occurredAt BETWEEN :monthStart AND :monthEnd)
GROUP BY b.id
ORDER BY b.sortOrder, b.id;
```

账单合计、首页总额、分析页占比**共用同一套 DAO**，只是传入不同的时间区间参数，避免三处口径算出三个数。

---

## 6. 表达式计算器（"内置计算功能"）

Domain 层一个纯函数，不依赖 Android，100% 单测覆盖：

```kotlin
object ExpressionEvaluator {
    /** "23+15.5-3" -> Right(3850) ；非法输入 -> Left(ErrorReason) */
    fun evalCents(expr: String): Either<CalcError, Long>
}
```

**规则**（你已确认要四则运算，据此实现为**标准数学优先级**）
- 支持 `+ - × ÷` 与括号，**按数学优先级求值**（`9-2*3 = 3`，`100/(2+3) = 20`）。求值器用递归下降解析：`expr := term (('+'|'-') term)*`，`term := factor (('×'|'÷') factor)*`，`factor := number | '(' expr ')' | '-' factor`（一元负号支持「-3+10」这种冲抵写法）。
- **中间量用 `BigDecimal`，不用 `Double`**——除法必须精确（`0.1+0.2` 不能出错），除不尽时按 `scale=10, HALF_UP` 收敛，最终结果统一 `setScale(2, HALF_UP)` 落到分。例：`100÷3` → `¥33.33`。
- **除零**不报错崩溃，提示「不能除以 0」并把表达式停在原地。
- 只允许合法前缀：禁止连续运算符、禁止以运算符结尾（键盘层直接把非法按钮置灰，而不是等提交才报错）
- 单项上限 `999,999.99`，括号嵌套上限 8 层，结果溢出时拒绝提交并提示
- 实时求值：输入每变一次就刷新右侧 `¥38.00` 预览，**不等用户按提交**；表达式尚未完整（如刚按了 `×`）时预览显示上一次合法结果并标灰，不闪错

**键盘状态机**
```
idle ──digit──▶ entering ──op──▶ expecting_operand ──digit──▶ entering
                  │                    │
                  └── ⌫ / C ───────────┴──▶ (回退 / 清空)
expecting_operand 时按 ⌫ 会连带删掉运算符（避免留下悬空的 "+")
```

---

## 7. 页面清单

| 页面 | 内容 | 优先级 |
|---|---|---|
| **首页 · 账单网格** | 月份切换器（‹ 9月 ›）、总开支大字、`LazyVerticalGrid` 账单卡片（图标/名称/本月合计/笔数）、FAB 新建账单 | P0 |
| **记一笔弹层** | 表达式显示区 + 结果预览 + 备注输入 + 自定义数字键盘 + 提交 | P0 |
| **账单详情** | 该账单累计/本月合计、条目倒序列表（按日期分组）、点条目可改金额与备注、侧滑删除 | P0 |
| **分析** | ① 本月 vs 上月 + 日均；② 账单占比横条排行；③ 近 30 天柱状趋势；④ 最大单笔 | P0 |
| **账单管理** | 改名、换图标（12 个预设）、换色（8 色）、拖拽排序、归档/恢复 | P1 |
| **补记日期** | 弹层里长按日期可回选「今天/昨天/前天」或日期选择器 | P1 |
| **导出 / 备份** | 导出 CSV、恢复 | P2 |
| 主题跟随系统深色 | Material 3 dynamic color | P1 |

图表全部**手绘 Compose `Canvas`**（条形、占比条），不引第三方图表库——就这 3 种图，引库增加 APK 体积和 API 学习成本，不值。

---

## 8. 目录结构

> 下面是**最初评审时的规划快照**，文件名与包名都已变化（现包名 `com.klocktx.moneyblocks`，`ui/block` 现为 `ui/bill`，条目弹层现为 `ui/keypad/RecordSheet.kt`，并新增 `ui/day`、`ui/common`、`ui/search`、`ui/theme` 除外的多块）。以仓库实际目录为准，此处不作同步维护。

```
app/src/main/java/com/klocktx/moneybills/
├── core/           Cents.kt · DateRange.kt · AppContainer.kt
├── data/
│   ├── local/      AppDatabase.kt · BillEntity.kt · EntryEntity.kt
│   │               BillDao.kt · EntryDao.kt · SeedData.kt
│   └── repo/       BillRepositoryImpl.kt · EntryRepositoryImpl.kt
│                   StatsRepositoryImpl.kt · Mappers.kt
├── domain/
│   ├── model/      Bill.kt · Entry.kt · BillSummary.kt · StatSnapshot.kt
│   ├── repo/       BillRepository.kt · EntryRepository.kt · StatsRepository.kt
│   └── usecase/    AddEntryUseCase.kt · UpdateEntryUseCase.kt
│                   DeleteEntryUseCase.kt · BillManageUseCase.kt
│                   ExpressionEvaluator.kt · BuildStatsUseCase.kt
├── ui/
│   ├── home/       HomeScreen.kt · HomeViewModel.kt · BillCard.kt
│   ├── keypad/     MoneyKeypadSheet.kt · KeypadViewModel.kt
│   │               KeypadLayout.kt · KeypadState.kt
│   ├── block/      BillDetailScreen.kt · BillDetailViewModel.kt
│   ├── analytics/  AnalyticsScreen.kt · AnalyticsViewModel.kt
│   │               BarChart.kt · ShareBar.kt      (Canvas 手绘)
│   ├── manage/     BillManageScreen.kt · BillEditDialog.kt
│   └── theme/      Theme.kt · Color.kt · Type.kt
└── MainActivity.kt
app/src/test/java/… ExpressionEvaluatorTest.kt · CentsTest.kt · UseCaseTest.kt
```

---

## 9. 分析指标口径（避免"总开支对不上"）

| 指标 | 定义 |
|---|---|
| 今日/本月总开支 | `SUM(amountCents)` where `occurredAt ∈ [起, 止]` |
| 累计总开支 | 全表 `SUM`，**不受月份筛选影响**（卡片上明确区分标注） |
| 日均 | 本月总额 ÷ **本月已过天数**（不是自然月天数，否则月初看数字会假性偏低） |
| 环比 | (本月至今 − 上月同期天数) ÷ 上月同期天数，**同期对齐**，不然 9月1日 对比整个 8 月必然 -97% |
| 账单占比 | 账单本月合计 ÷ 本月总额，按占比降序 |
| 趋势 | 近 30 天按 `occurredAt` 逐日聚合，无支出的日期补 0（柱状图不能断档） |

---

## 10. 里程碑

| 阶段 | 交付 | 验收 |
|---|---|---|
| **M0 冒烟**（先做，最重要） | Gradle 骨架 + 空 Compose 页 + Room 依赖接入 | **`gradlew :app:assembleDebug` 真出 APK**。这一步专门验证 KSP×Kotlin 兼容性，不通立刻走 §3 降级路径，绝不带病往后写 |
| **M1 数据层** | Entity/DAO/DB + Repository 实现 + 种子账单 | Robolectric 跑通增删改查 + 聚合查询正确 |
| **M2 核心闭环** | 首页网格 → 点账单 → 键盘 → 提交 → 合计刷新 | 从冷启动到记完一笔 ≤ 2 次点击 |
| **M3 计算器 + 条目** | 表达式求值 + 单测全覆盖；账单详情列表、改、删、撤销 | 边界用例（连续运算符、3 位小数、溢出、`0.1+0.2`）全绿 |
| **M4 分析页** | 4 个指标 + Canvas 图表 | 手算三个样本日期与图表数值一致 |
| **M5 账单管理 + 打磨** | 增删改账单、图标配色、排序、归档、深色模式、空状态 | 真机走查 |

M0–M3 是"能用的记账"，M4–M5 是"好用"。我会一路做到 M5，每个里程碑结束后给你一次可装的 APK 与简短汇报。

---

## 11. 决策确认结果（已实现）

| 决策点 | 你的选择 | 实现落点 |
|---|---|---|
| 内置计算 | **要四则运算**（改了我原提案的"仅加减"） | `ExpressionEvaluator`：递归下降，`+ - × ÷` + 括号 + 数学优先级 + 一元负号，BigDecimal 中间量 |
| 分层方式 | 按默认（包级分层） | `core / data / domain / ui` 四层包，依赖单向向内，Domain 零 Android 依赖 |
| 收支范围 | 按默认（只做支出） | 金额必须 > 0，退款用「删除这笔 + 重记」处理 |
| 云同步 | 按默认（不做） | 纯本地 Room，`moneyblocks.db`，离线可用 |
| 命名 | 按默认 | 评审时叫「账单账本」/ `com.klocktx.moneybills`，v1.x 起改为「亭宣记账」/ `com.klocktx.moneyblocks` |

## 12. 尚未完成的一步

**没有做设备端运行验证**：这台机器当前既无连接设备（`adb devices` 为空），也没有任何 AVD。所以「能装、能点、显示正常」这两件事我还没有证据，只证明了「能编译、逻辑单测通过」。

要补的话有两条路：用 Android Studio 连真机跑一次；或者我下载系统镜像建一个 AVD（约 1 GB 下载 + 首次启动较慢）来自动跑通并截图。你定。

---

## 13. 构建与安装

```cmd
:: 项目根目录，脚本已固定 JAVA_HOME 指向本机 JDK 21（PATH 里是 JDK 25，AGP 8.13 不吃）
build.cmd :app:assembleDebug        :: 产出 app/build/outputs/apk/debug/app-debug.apk
build.cmd :app:testDebugUnitTest    :: 35 个单测
build.cmd :app:assembleRelease      :: R8 混淆，约 1.6 MB，未签名

:: 装到已连接的设备
"C:\Users\KlockTX\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

用 Android Studio 打开本目录也可以，Gradle JDK 选 Studio 自带的 JBR 21 或 `C:/Program Files/Java/jdk-21.0.11`，不要选 JDK 25。

**当前状态**：debug APK 18.4 MB、release 1.65 MB 均已成功产出，37 个单测全绿，Room schema 已导出到 `app/schemas/`。

---

## 14. v1.1 改动（按你四条反馈）

| 你的要求 | 做了什么 |
|---|---|
| ① 图标别用 emoji，换开源 SVG，建账单时能从预设图标库选 | 见上方「选型修正」。25 个 Material 矢量图标进 `BillIcons`，账单编辑弹窗里是**图标网格选择器**（选中项显示 ✓ 叠加），存的是 `iconKey` 而不是图片，换图标不影响历史条目 |
| ② 记新账时残留上一笔金额，还得手动删 | `KeypadViewModel.submit()` 成功分支现在会清掉表达式和备注。之前是提交后弹层关闭、ViewModel 按 `billId` 复用，状态就留到了下一次。现在归属日期**故意保留**——连续记同一天是常态 |
| ③「区块」改叫「账单」 | 界面文案、导航标签、类名（`Block*` → `Bill*`）、数据库表名（`blocks` → `bills`）、外键列（`blockId` → `billId`）一起改了，避免只剩一半 |
| ④ 设计语言太模板化，要更精致 | 见下 |

### 设计语言具体改了什么

不是换配色了事，而是把「默认组件堆砌」换成一套有约束的视觉体系：

- **自定义 `MoneyTypography`**：中文界面里过大的字距显得松散，标题整体收紧（`displaySmall` 34sp / -0.8 字距），正文行高压到 1.43 附近；金额单独一组带 `tnum` 等宽数字的样式（`MoneyDisplay` 40sp、`MoneyTitle` 21sp），逐行刷新时数字宽度不跳
- **`MoneyShapes` + `Spacing` token**：圆角按 8/12/18/24/30 分档，间距只走 4 的倍数，不再各处随手写 dp
- **卡片从「灰底阴影」改成「白底 + 0.5 透明度描边 + 零高度」**：线性、克制，靠 tonal surface 分 `surfaceContainerLow/High` 拉开层次，而不是靠投影堆
- **配色**：墨绿 `#0E3A2B` 为主、琥珀为辅，中性色偏一点暖灰绿，避免 Material 默认蓝的模板感；深色模式同调
- **首页**：顶部大数字 hero + 今日/日均/累计三列（用竖细线分隔而不是塞三张卡），账单改成单列横条卡——矢量图标圆底（账单主色 14% 透明）+ 名称 + **该账单占比细条** + 右侧金额与笔数，信息密度比原来的 2 列网格高，也不会让小屏文字挤压
- **键盘**：顶部功能区（AC／括号／退格）压到 42dp、数字键 54dp，视觉重心落在数字上；运算符键用当前账单主色的 12% 底；显示区做成独立浅容器，表达式小灰字在上、金额大字在下
- **账单行内的「明细」入口和「记一笔」仍然是分开的**，避免想看列表时误弹键盘

### App 名顺带改了

launcher 名字原来叫「区块账本」，那个「区块」指的就是分类；分类既然改叫账单，原名就失去指代了，所以改成 **「记账本」**（`res/values/strings.xml` 的 `app_name`，一处即可改回）。包名 `com.klocktx.moneyblocks` 保持不变，改包名会牵动目录结构和已安装标识，不值得。

### ⚠️ 装 v1.1 之前要先卸载 v1.0

表名和列名都变了（`blocks`→`bills`、`emoji`→`iconKey`），Room 检测到 schema 指纹不匹配会直接抛异常，不是「数据保留升级」。我没有为这个未发布版本写迁移脚本——那东西之后就是死代码。你手机上那几笔测试数据需要重装后重新记。

---

## 15. v1.2 改动（你报的两个问题 + 黑白模式）

### ① 记账后提示条一直挂着不消失

根因不是我写的计时有问题，而是用错了组件：`SnackbarHostState.showSnackbar()` 是**挂起函数**，它的超时计时要等 Snackbar 真正进入可见状态才开始——而记账用的 `ModalBottomSheet` 是独立窗口，正好压在 Snackbar 上面，计时就可能起不来，于是提示常驻。

我没有去给别人的挂起语义打补丁，而是换成完全自控的 `ToastHost`（`ui/common/AppToast.kt`）：超时由外层一个 `LaunchedEffect(toast) { delay(2600); toast = null }` 控制，与可见性无关，一定会消失。视觉上用 `inverseSurface` 深底反白胶囊，底部滑入，可带一个「撤销」动作。

顺带一个收益：详情页删除的撤销逻辑变干净了。原来是 `if (showSnackbar(...) == ActionPerformed)` 这种挂起返回值判断，现在把 `onAction = { restore(entry) }` 当参数传进去，读起来直白得多。

### ② 明细入口发现不了

原来只是卡片右下角一枚裸文本「明细」，没有可点特征。改成：

- **带箭头图标的描边胶囊按钮**（`明细` + `ChevronRight`，`surfaceContainerLow` 底 + outline 描边），落在卡片行尾，一眼是个控件
- **长按卡片任意处也直接进明细**，作为熟练用户的快捷路径（`combinedClickable`）
- 单击卡片仍然是「记一笔」，你最初要的那个交互不变

### ③ 黑白模式切换

三档：**跟随系统 / 浅色 / 深色**，存在 DataStore（`settings.preferences_pb`，键 `theme_mode`），重启后保留。

- 入口放在「账单」页顶部一张「外观」卡里，用 M3 `SingleChoiceSegmentedButtonRow` 分段控件——这页实际充当设置入口，没必要为三档开关单开一页
- `ThemeMode` 是 domain 层枚举，`SettingsRepository` 接口在 domain、DataStore 实现在 data，依赖方向没有破
- 两处运行时细节：`MoneyBlocksTheme` 里用 `WindowCompat…isAppearanceLightStatusBars = !darkTheme`，让**状态栏图标跟 App 内选的模式走而不是系统的**（手动选深色、系统却是浅色时，不处理就会深色背景配深色图标）；`MainActivity` 调 `enableEdgeToEdge()`，并把详情页嵌套 `Scaffold` 的 `contentWindowInsets` 与内层 `TopAppBar` 的 `windowInsets` 都置 0——外层已经付过一次状态栏高度，再吃一次就是双倍顶部空白

### 安装提示

v1.2 **没有改数据库 schema**，从 v1.1 可以直接覆盖安装（versionCode 2→3），数据保留。但如果你手机上还是 v1.0，仍要先卸载——那之后的表名列名变动没写迁移。

---

## 16. v1.3 改动

### 交互修正

- **明细按钮回原位**：撤掉胶囊，改回笔数右侧的一枚**账单同色小色块**（26dp 圆角 + 账单主色 14% 底 + `›` 图标同色）。长按卡片进明细保留。
- **首页「新建」直接弹窗**：原来点了会跳到账单页再自己找按钮，多绕一步。现在首页「新建」和空状态按钮都直接弹新建弹窗。为此把账单编辑弹窗从管理页里抽成共享组件 `ui/bill/BillEditorDialog.kt`，两页共用一份——否则两处各一份迟早改成不一样。

### 性能与加载

- 首页从 `Column + verticalScroll` 换成 **`LazyColumn`**：账单和条目多了也只组合可见部分
- **加载骨架屏**：`HomeViewModel` 加了 `ready` 标志。之前冷启动会先闪一下「还没有账单」的空状态——那不是空，是还没查到。现在首次查询返回前显示 3 张 shimmer 占位卡
- 派生计算用 `remember(key)` 收住：账单 id→Bill 映射、最近条目切片、趋势峰值，避免每次滚动重算
- 列表项都带稳定 `key`，切月份时是复用而不是整列重建

### 动画

新增 `ui/theme/Motion.kt` 统一时长与曲线（`QUICK 140 / STANDARD 240 / SLOW 380` + M3 emphasized 曲线）。散落的 duration 会让动画节奏不一致，这本身就是一眼能看出的廉价感来源。落到四处：

- 页面切换：`NavHost` 淡入 + 1/20 屏宽横向位移，返回时反向
- 金额变化：首页 hero 总额与账单金额用 `AnimatedContent` 上滚淡入——记账时你想看到的正是「它跳上去了」
- 键盘按键：按下缩放至 0.94，去掉默认灰色 ripple，配合这套线性设计
- 预算条：设置/清除预算时 `expandVertically` 展开收起，不突兀

### 新增功能

| 功能 | 说明 |
|---|---|
| **最近条目** | 首页「最近」卡显示最近 4 笔（跨账单），点一下回到该账单明细。归属账单直接复用已加载的摘要 map，不额外查库 |
| **月度预算** | DataStore 存预算金额，首页 hero 下方一条进度：`剩 x` 或超支时整条转红显示 `超 x`。逻辑收在 `BudgetStatus` 值对象里（`fraction` / `isOver` / `isActive`），除零与负数判断不散在界面里 |
| **导出 CSV** | 走系统文件选择器（SAF），全量条目导出为「日期,时间,账单,金额,备注」。CSV 拼接是纯函数 `CsvExport.build()`，**7 个单测专门盯转义**：逗号包裹、内嵌引号双写、字段内换行、5 分必须输出 `0.05`、UTF-8 BOM（没 BOM 的话 Excel 打开中文账单名全是乱码） |

数据层相应新增 `ExportDao`（JOIN 查询直接产出导出行）、`ExportRepository`，`SettingsRepository` 扩了 `monthlyBudget`。

**测试从 37 个增加到 44 个，全部通过；编译 0 warning。**

---

## 17. v1.4 改动

### 定名「亭记账」

改的是 `res/values/strings.xml` 的 `app_name`（桌面图标下的名字）和 CSV 导出文件名前缀（`亭记账-2026-09-22.csv`）。包名 `com.klocktx.moneyblocks` 不动——改包名会牵动目录结构和已安装标识，换不了覆盖升级。APK 文件名用 ASCII（`TingLedger-v1.4.0-debug.apk`），因为 Git Bash 下中文路径在处理 `adb install` 时容易撞 GBK 编码问题。

### 页眉月份切换重做

原来是两个靠左的小箭头，既没有月份文字、也不能直接跳月，确实简陋。现在抽成共享组件 `ui/common/MonthHeader.kt`，首页和分析页都用它：

- **居中布局**：圆形描边箭头 + 中间月份胶囊，视觉重量平衡
- **胶囊上直接显示月份**——之前翻到几月完全看不出来
- **点胶囊打开月份面板**：先翻年（不允许翻进未来年份），再点 12 个月格子里任一格直接跳过去，未来月份置灰。底部一颗「回到本月」。之前只能一个月一个月按箭头，跳回三月要点七次
- 非本月时胶囊换成主色底，一眼看出「你在看历史月份」
- 换月时月份文字用 `Crossfade` 交叉淡入，不硬切

### 新增：长按「再记一笔」

详情页条目现在区分三种操作：**单击**改这笔、**长按**按同样的金额和备注新建一笔（归属日期是今天）、右侧图标删除。公交、早餐、房租这类周期性支出不用再重敲数字。`RecordTarget` 为此加了 `prefill` 字段，与 `editing` 分开——弹层标题会显示「吃饭 · 再记一笔」，副标题提示已带入上次金额。

### 修掉一个数据安全隐患

账单编辑弹窗里的「删除」以前是一点就删，而数据库外键是 `CASCADE`——也就是会连带删掉该账单下所有历史记录，没有确认也没有撤销。现在改成两步：第一次点「删除」出现红色警示块，写明「删除「吃饭」会连带删掉它名下的记录，共 N 笔，删除后无法恢复」，再点「确认删除」才真删。条目数取自 `observeBillSummaries` 的全周期口径，不额外查库。

### 一个值得记下的 Kotlin 中文陷阱

写月份面板时 `"$month月"` 编译报 `Unresolved reference 'month月'`——**中文的「月」属于字母类字符，会参与标识符解析**，所以 `$month月` 被当成一个整体变量名。必须写 `"${month}月"`。这是纯中文界面才会踩的坑。

**版本 1.4.0（versionCode 5）**，44 个单测全绿，编译 0 warning，schema 未变可直接覆盖安装。

---

## 18. 发行打包（v1.4.0 · 压缩 + 混淆 + 签名）

### 签名

密钥用本机 `keytool` 生成，**RSA-4096、有效期 10950 天（30 年）、SHA384withRSA**：

```
keystore/tjz-release.jks          密钥库（alias: tjz-release）
keystore/keystore.properties      口令与别名，构建脚本读取
```

两个文件都已在 `.gitignore` 里排除。`build.gradle.kts` 读不到 `keystore.properties` 时会退化为产出未签名包，所以没拿到密钥的机器仍然能构建，不会一上来就红。

签名方案是 **v2 + v3**，都验证通过。v1（JAR signing）显示 false——这不是漏配：AGP 对 minSdk 24+ 不产出 v1，而 v1 只服务 Android 7.0 以下；本工程 minSdk 26，最低就是 Android 8.0，系统安装器只校验 v2/v3。

**证书 SHA-256 指纹**（以后核对是否同一个密钥用）：
`fc86dddfac1fa60f3adbe4a1ef6c36de6d5f606f07d409d4331b958bff50906c`

> ⚠️ **必须备份 `keystore/` 那两个文件。** Android 靠签名证书识别同一个应用，密钥丢了就无法再给已安装用户推送升级，只能让用户卸载重装、丢掉本地数据。这份密钥没有第二处副本。

### 混淆与压缩

`isMinifyEnabled = true` + `isShrinkResources = true`，R8 full mode（AGP 8 默认）。

`proguard-rules.pro` 里两处值得说明的**主动保守取舍**：

- 保留了 `data.local.**` 与 `ExportRow` 的类名。理论上 Room 由 KSP 编译期生成直接构造代码、不需要 keep，但这个包**没有真机验证过运行时行为**，所以宁可让几十 KB 不被混淆，也不赌反射边缘路径不崩。
- 刻意**没有**写 `-assumenosideeffects android.util.Log`。这条很常见，能把库内部的日志一起静默掉，省几 KB——但同样因为没真机跑过，保留日志比省空间重要。

另外 `-keepattributes SourceFile,LineNumberTable` + `-renamesourcefileattribute SourceFile`，让崩溃栈保留行号，配合 `mapping.txt` 可反解。

### 实际效果（APK 内部构成，实测非估算）

| 指标 | 数值 |
|---|---|
| release 包体积 | **1.79 MB**（压缩内容 1.70 MB，未压缩 3.17 MB） |
| debug 包体积 | 19.25 MB |
| `classes.dex` | 1.34 MB，**单个 dex** |
| APK 条目数 | 111（material-icons-extended 未用到的图标全部被剔除） |
| R8 移除的成员数 | 68,237 行记录（`usage.txt`） |
| 资源映射 | `resources.txt` 102 KB（shrinkResources 生效） |

意外收获：AGP 自动把 **baseline profile** 打进了包（`assets/dexopt/baseline.prof`），系统首次安装后会按它预编译热路径，对启动速度有实际帮助——这一项不需要我们做任何事。

### 验证方式

不采信构建日志的"成功"，用 SDK 工具独立校验（脚本 `verify-release.cmd`，可重复执行）：

```cmd
build.cmd :app:assembleRelease
verify-release.cmd
```

它跑的是 `apksigner verify --verbose --print-certs` 和 `zipalign -c -v 4`。本次输出：`Verifies`、v2 true、v3 true、`Verification successful`。

### 发版工件（`dist/`）

```
TingLedger-v1.4.0-release-signed.apk   1.79 MB  已签名，可直接分发安装
TingLedger-v1.4.0-debug.apk            19.25 MB 未混淆，便于排查问题
mapping-v1.4.0-release.zip              3.09 MB mapping.txt + seeds + usage + configuration
```

`mapping.zip` 要跟着版本一起留存：release 包一旦崩溃，栈里的名字是混淆过的，只有对应版本的 `mapping.txt` 能翻译回来。

---

## 19. v1.5 改动

### 定名「亭宣记账」

`app_name` 与 CSV 导出文件名前缀同步改了。密钥别名 `tjz-release` 和包名保持不动——那些是内部标识，改了对用户没意义，改包名还会破坏覆盖升级。

### 新增：搜索

首页页眉左侧加了搜索入口（右侧放等宽占位，月份胶囊仍然居中）。可以搜备注内容、账单名，也可以**直接输金额**：`38.50` 会精确匹配金额为 ¥38.50 的记录。结果按日期分组，顶部给出命中笔数与合计，点任意一条跳到所属账单明细。

两个实现细节：

- 输入**停顿 220ms 才查库**。中文输入法联想会连续改字符串，逐字符查询既浪费又让结果抖动。
- LIKE 的 `%` 和 `_` 是元字符。用户搜「50%」如果直接拼进去会变成「50 开头的所有记录」。所以关键词先转义、再包通配符，SQL 侧用 `ESCAPE '\'`。这段转义逻辑我提到 `domain/usecase/SearchQuery.kt` 单独测了 7 条——它正是最容易写错又最难在界面上看出来的地方。

### 新增：月度对比

分析页加了近 12 个月的柱状对比，最高月归一化，当前查看的月份高亮为主色、其余半透明。**柱子可点**，点哪个月整页就切到那个月（复用已有的 `goToMonth`）。只能看不能点的图表价值有限。

数据侧新增 `StatsRepository.monthlySeries(12)`，按 `YearMonth` 分组、没支出的月份补 0，不会断柱。

### 新增：记错账单可以改归属

编辑弹层里现在能换账单——Header 下方出现「记入 · 吃饭 · 换账单」（仅在一个以上账单时显示），点开是带图标和选中标记的列表。`RecordEntryUseCase.edit()` 顺势把 `billId` 也写进去，所以记错的款项不用「删掉—重记一遍」，直接挪走。

### 版本

`versionCode 6 / versionName 1.5.0`，schema 未变，覆盖安装即可。
**51 个单测全绿**（新增 7 个搜索转义测试），编译 0 warning。
发行包签名复核通过：v2 + v3 scheme、zipalign `Verification successful`。

---

## 20. v1.6 改动（组件层收敛）

### 布局与组件的统一

前面几轮把描边卡片收口到了 `ui/common/Panels.kt`，但记账弹层和账单编辑器还各自留着本地的圆角、背景和选中态。这一轮把它们并进来：

- **半径收口**：`Panels` 新增 `dialogShape`(26) 与 `buttonShape`(16)，加上原有 `shape`(20)/`innerShape`(14)/`cornerSm`(10)。全库散落的 `RoundedCornerShape(12/13/14/15/16/26.dp)` 字面量改成引用令牌，弹窗、输入框、按键、色块从此同源。首页里 4/6/7/9dp 的微元素（色块提示、迷你柱）刻意保留，它们不是容器。
- **`Chip` 取代 `DayPill`**：记账弹层的「今天/昨天/前天」和搜索页、分析页的日期胶囊本是同一套样式，只是各写了一遍。删掉私有的 `DayPill`，统一走 `Chip`。
- **新增 `PickerCell`**：图标选择网格和月份选择网格是同一种「淡底 + 描边 + 44dp 高」的格子，之前两处独立维护。抽出 `PickerCell(selected, accent, onClick, enabled)`，`content` 用 `BoxScope` 接收，配套 `SelectedBadge` 角标。

### 修掉一个会误判的选中态

图标网格里选中某个图标时，原实现是**把图标本身换成一个对勾**——于是用户看不到自己究竟选了哪个图标，只能凭位置猜。改成图标常显，选中用「着色底 + 描边 + 右上角小角标」表达。

同一处的颜色选择器用文字 `✓` 当选中记号。文字符号受字体和环境影响，粗细、基线、可用性都不稳定，换成矢量 `Icons.Default.Check`，并把「选中即放大到 30dp」作为额外提示。

### 一次性恢复脚手架的清理

上一轮我为了削 unused import 写了个脚本，判断依据不足，结果把 47 个文件的 import 全删了。编译器把它们逐个报回来、我按报错补了回去，但补出来的块有重复行（`getValue`/`setValue` 出现两次）、混进空行、顺序也不对。

这一轮用一个只做「去重 + 按 ASCII 排序 + 去块内空行」的脚本把 42 个文件规范化，改完幂等（再跑 0 改动），全量重编译 `BUILD SUCCESSFUL`、51 测试全绿、无 `e:`/`w:` 输出。`tools/` 下的 5 个恢复脚本已完成使命，连同临时日志一起删除——它们是一次性脚手架，留在仓库里只会被误认为构建步骤。

**教训记在这里**：批量改源码的脚本，判据必须能验证（编译器 / 真实解析），不能用「看起来像没用」的字符串猜测。这次是编译器全量报错才救回来的，代价是整轮都在补 import。

### 版本

`versionCode 7 / versionName 1.6.0`，schema 未变，同签名证书（SHA-256 `fc86dddf…906c`），覆盖安装即可。
`dist/` 已换成 v1.6.0 三件套，v1.5.0 的旧包删除以免装错。
**本轮全是视觉层改动，只做到编译与单测层面验证，没有在真机上看过效果**——布局和选中态的实际观感仍需装机确认。

---

## 21. v1.7 改动（收支方向 + 首页分组）

### 记账不再只有支出

新增 `BillKind { EXPENSE, INCOME }`。**方向挂在账单上，不挂在每一笔上**：「吃饭」永远是支出、「工资」永远是收入，记一笔时不必再选一次方向。条目金额因此仍然恒为正数，符号只在展示和汇总时按账单方向推导——`Cents` 不用处理负数，计算器和四则求值器一行都不用改。

配套改动：

- 录入弹层：收入账单显示「记一笔收入」，预览金额带 `+`。
- 改归属的候选账单**限定同方向**。把一笔支出挪进收入账单本身就是记错了账，允许它只会制造说不通的数字。
- 编辑账单弹窗加「方向」分段选择，改方向不用删了重建。
- 首页有收入账单时才出现「收入」分组和「本月收入 / 结余」这一行；一个收入账单都没有时不摆 0.00 的噪音。
- 分析页概览加「本月收入 / 结余」，四个小节的标题改成**支出占比 / 近 30 天支出 / 月度支出对比 / 最大单笔支出**——有了收入之后，不写口径的图表会产生歧义。
- 搜索与「看某一天」的合计按方向拆开：同一天既有工资又有菜钱时，两边相减得到的数字谁都不代表。
- CSV 导出加「类型」列（支出/收入），金额列不再用 `Double` 格式化，改成整数分推导（顺带去掉一个不符合「Double 不参与记账」红线的实现）。

### 数据库：这一版写了真迁移

`bills` 加 `kind` 列，version 1 → 2。**这一版偏离了「未发布期不写迁移」的原定前提**，理由很具体：v1.5/v1.6 已经装在手机上、里面是真实流水，`fallbackToDestructiveMigration()` 会把账本清空。

迁移内容三句：

```sql
ALTER TABLE bills ADD COLUMN kind TEXT NOT NULL DEFAULT 'expense';
UPDATE bills SET iconKey = 'shopping_cart' WHERE iconKey = 'shopping_bag';
UPDATE bills SET iconKey = 'card_giftcard' WHERE iconKey = 'gift';
```

两个实测要点：

1. `@ColumnInfo(defaultValue = "expense")` 必须写。实体字段上的 Kotlin 默认值不生成 SQL 默认值，而 `ALTER TABLE ... DEFAULT` 会在表里留下默认值——两边不一致时 Room 升级阶段校验 schema 直接抛异常。
2. 用 `platform-tools/sqlite3.exe` 离线跑了一遍迁移（建 v1 表 → 插数据 → 执行三条语句），`PRAGMA table_info` 得到 `kind | TEXT | notnull=1 | dflt_value='expense'`，与 `app/schemas/.../2.json` 的期望逐字一致。没有用模拟器。

### 顺手挖出来两个真 bug

- **种子图标一直是错的**：`购物` 的 iconKey 写成 `shopping_bag`、`日常其他` 写成 `gift`，这两个 key 都不在预设图标库里，于是从第一个版本起它们一直显示兜底的 `category` 图标。改代码修不到已装设备（key 存在库里），所以纠正放在迁移里。
- **收入会稀释支出占比**：`shareSlices` 原本对全部账单求和当分母。加了收入之后，工资进去会把每个支出占比凭空压低。改成先在 `BuildStatsUseCase.shareSlices` 里过滤 EXPENSE，并有单测盯住。

### 首页账单分组

原来所有账单塞一张面板、行间一条细线，账单名和金额会连成一片。改成每条账单各自成卡、卡间 `Spacing.sm`，分组感来自间距而不是分隔线；支出与收入分两组。

### 分析页「最大单笔」挤压

`SectionHeader("最大单笔")` 前漏了其它每个小节都有的 `Spacer(Spacing.xl)`，所以文字直接压在月度对比卡片下沿上。补上，并把该页手写的 `HorizontalDivider(alpha = 0.45f)` 换成 `PanelDivider()`——那个 0.45 正是 `Panels.dividerAlpha`。

### 事故：一次脚本改动截断了 4 个源文件

给 import 块插入新行时我写错了切片长度，把 `BillDetailScreen / AnalyticsScreen / SearchScreen / DaySheet` 的整段文件体从中间截断了（编译器报的是「Expecting a top level declaration」一大片）。

恢复过程，记下来供以后照做：

1. Qoder 有 `~/.qoder-cn/file-history/<session-uuid>/<fileId>@vN` 的文件历史。用 `grep -l "fun AnalyticsScreen"` 按内容定位快照，再按 mtime 和 `wc -l` 挑出「最后一次能编译」的那份。
2. 用 `Panels.buttonShape` 这类本轮才引入的符号做标记位，确认快照的世代，避免恢复到旧版本。
3. 恢复后**先只编译、不叠加新改动**，确认 `BUILD SUCCESSFUL`，再逐个重放本轮的收入改动。
4. 4 个快照都括号配平，恢复后编译耗时 2s（Gradle 认定输入与上次成功编译一致），说明恢复是逐字一致的。

同一个脚本思路随后又差点出事：清理未用 import 时按「符号名有没有在正文出现」判断，把 `androidx.compose.runtime.getValue/setValue` 全删了——**`by` 委托是隐式调用这两个扩展，正文里永远不会出现字面量**。这一条判据不成立，已全部回补并重新编译通过。

结论：改源码的批量脚本，判据要么是编译器、要么是真正的语法解析；「看起来没用到」不成立，尤其是运算符和委托这类隐式引用。

### 版本与实测数字

`versionCode 8 / versionName 1.7.0`，schema 1 → 2（带迁移），签名证书不变（SHA-256 `fc86dddf…906c`），可直接覆盖 v1.6 安装、数据保留。

- 单测 51 → **56 全绿**（新增 CSV 收入行、无千分位、占比排除收入、结余符号、混合命中拆分 5 条），编译 0 error 0 warning。
- release 包 1,894,825 → **1,911,209 B**（+16.0 KB，主要是 v2 schema JSON 与方向逻辑）。
- apksigner：v2 + v3 true；zipalign `Verification successful`。
- `dist/` 已换成 v1.7.0 三件套。

**收支方向、迁移、首页分组都是会直接影响日常使用的改动，而我只做到编译 + 单测 + 离线 sqlite 迁移演练，没有在真机上跑过。** 覆盖安装后的数据保留情况、以及新增收入账单后的首页/分析页观感，需要你装机确认。

---

## 22. v1.8 改动（管理页分组 + 颜色选择挤压 + 收支双柱 + 启动图标）

### 账单管理页也分开成卡

和首页同一个问题：所有账单挤在一张面板里靠细线分隔，上下两条的「上移/下移/归档」按钮连成一片，点错行不容易一眼看出来。改成每条账单各自成卡、卡间 `Spacing.sm`；已归档列表同样处理。

### 修：新建账单时颜色显示不全

用户报「选择颜色的地方颜色显示不全、有挤压」。这是算得出的：色块 26dp × 8 个 + 间距 12dp × 7 段 = **292dp**，而 AlertDialog 内容区在 360dp 宽的屏上只有约 282dp，最后两个色块被挤掉；选中时还放大到 30dp，更挤。

改法：`Row` 里每格 `weight(1f)` 均分、色块**尺寸固定不再随选中变化**，选中态改用「外圈光环 + 白色对勾」表达。这样任何屏宽都不会溢出，也不会因为选中而抖动布局。

### 分析页：月度柱状图支持收支双柱

`monthlySeries(months, kind)` 本来就支持方向参数，于是再取一条收入序列，同一个格子里并排画支出/收入两根柱子，共用同一套归一化刻度，并加图例。**只在该区间真的有收入时才切成双柱**——否则多出来一列恒为 0 的柱子只是噪音。小节标题随之从「月度支出对比」变「月度收支对比」。

### 搜索：加方向筛选

结果列表顶部加「全部 / 支出 / 收入」胶囊（复用 `Chip`）。过滤放在 ViewModel 的 `combine` 里、内存中做：命中集合本来就已经取回来了，为这个再拼一次 SQL 只是多一趟查询。筛选导致零结果时空态文案会跟着变成「把筛选改回全部」，避免用户以为搜不到。

### 启动图标

原来是我早期放的占位：2×2 四个方块，颜色跟应用色板无关，也看不出这是记账。换成 **「亭顶 + 方孔铜钱」**：一道倒 V 屋脊压着一枚外圆内方的钱，取应用自己的松绿 `#4FD39C` 与琥珀 `#F3B84A`，底色是主色系的墨绿渐变。

做法上值得一提：**这台机器上没有可用的渲染途径**（in-app browser 没有可见 surface、没有 PIL/numpy），所以我写了一个只依赖标准库的小光栅器（多边形 + 圆盘 + 方孔 + 遮罩 + PNG 编码），把**同一组 path 坐标**直接画成 PNG 看了效果——先发现图形偏小、屋顶偏细，放大一档后才落成 `VectorDrawable`。桌面预览覆盖到 32px 仍然读得出来。

结构是标准三层自适应图标：

- `drawable/ic_launcher_background.xml`：108×108 墨绿斜向渐变（`aapt:attr` + `gradient`）。
- `drawable/ic_launcher_foreground.xml`：屋脊 + 铜钱，铜钱用 `fillType="evenOdd"` 挖出方孔。
- `drawable/ic_launcher_monochrome.xml`：同几何、纯白填充，供 Android 13+ 主题图标着色。

所有笔画都控制在自适应图标的安全圆（圆心 54,54、半径 33）内，所以无论启动器用圆形还是圆角方形遮罩都不会切到笔画。minSdk 26，因此不需要任何位图密度产物。

APK 侧实测：`aapt2 dump badging` 得到 `application: icon='res/BW.xml'`（资源名已被 shrink），`dump xmltree` 确认 background/foreground/monochrome 三层分别解析到 `@0x7f040007/08/09`——**混淆压缩之后三层都还在**，没有因为 monochrome 只被 adaptive-icon 引用而被 shrink 掉。

### 版本与实测数字

`versionCode 9 / versionName 1.8.0`，schema 仍是 2（本轮无数据库改动），签名证书不变（SHA-256 `fc86dddf…906c`），覆盖安装即可、数据保留。

- 单测 **56 全绿**，编译 0 error 0 warning。
- release 包 1,911,209 → **1,912,697 B**（+1.5 KB，全是图标矢量）。
- apksigner v2 + v3 true；zipalign `Verification successful`。
- `dist/` 已换成 v1.8.0 三件套。

**图标这一项我没法真机确认**：我只验证了「几何在安全圆内 + 三层资源都在包里 + 用同一组坐标自渲染的效果图」，具体遮罩形态、主题图标在你们系统上的着色、以及桌面 48px 以下的实际锐度，都要装机才看得出来。颜色选择器和双柱图同理，属于观感层面的改动，请你验。

---

## 23. v1.9 改动（拖拽排序 + 首页双指标 + 图标重做 + 本月节奏）

### 账单顺序改成拖动

管理页原来是每行两个「上移/下移」按钮，调一次顺序要点好几下。换成**按住把手拖动**：行首一个 `DragHandle` 把手，其余行自动让路，松手落位并整表重写 `sortOrder`。

实现放在 `ui/common/DragSort.kt`，刻意拆成两半：

- **纯数学**：`targetIndexFor(from, dragPx, stridePx, count)` 把手指位移换算成落点，`shiftFor(index, from, to, stridePx)` 算其它行让路多少。这两个函数单独测了 7 条——算错的表现为「顺序被悄悄改乱」，在界面上几乎发现不了。
- **交互壳**：把手用 `detectVerticalDragGestures` 并 `consume()` 掉事件，所以拖动过程中外层列表不会跟着滚；被拖那条跟手并带 `shadow` + 1.02 缩放，其余用 `offset` 让路。

两个踩过的点记在这里：

1. `DragSortState` **不持有回调**。调用点传进来的 lambda 每次重组都是新实例，一旦进了 `remember` 的 key，拖动过程中状态就会被重建、松手时什么也不会发生。改成 `finish(count)` 返回 `(from, to)`，由调用方去执行。
2. 无障碍没有因为换成拖动而丢掉：每行仍然通过 `semantics { customActions }` 暴露「上移 / 下移」，读屏用户走的还是同一条 `moveTo`。

`ManageBillsUseCase.moveTo(active, billId, to)` 取代原来的 `move(..., up: Boolean)`。

### 首页顶部：一个数字变两个

按要求改成「**本月变动**」（收入 − 支出）和「**本月支出**」两个数字。落地上是**上下两行而不是左右并排**：25sp 的金额并排放，一格只有约 160dp，五位数金额（`-¥12,345.67`）会被截断，所以改成各自占满一行、左对齐。为此加了 `MoneyPair`(25sp) 令牌——`MoneyDisplay`(40sp) 太大、`MoneyTitle`(21sp) 又撑不起首屏层级。

- 新增 `Cents.toNetYuanString()`：正数带 `+`，负数把负号放到货币符**前面**（`-¥380.00`），而不是复用 `toYuanWithSymbol()` 得到 `¥-380.00`。
- 变动额为负染 error 色、为正则主色，直观表达「这个月净流出/净流入」。
- v1.7 那条「本月收入 / 结余」小字随之下线，信息并进变动额；`HomeViewModel` 里已无人用的 `periodLabel`、`tracksIncome`、`isViewingCurrentMonth` 一并删除。
- 月份前缀跟着翻页走：本月 / 9月 / 2025年11月，两个数字共用。

**踩坑复现**：`"$prefix变动"` 又被 Kotlin 当成一个标识符解析了（中文字符参与标识符），必须写 `${prefix}变动`。这条在 §17 就记过一次，这次仍先编译报错才发现——凡是拼接中文的模板，一律用花括号。

### 启动图标重做（莫奈浅灰蓝）

上一版（墨绿底 + 屋脊铜钱）被否：配色不对、造型也不好看。这次按**浅灰蓝莫奈**重做，并且**先出图再看再定稿**——因为图标这种东西，坐标写得多正确都不如看一眼。

流程上先补了工具：原来的自写渲染器只认多边形和圆，画不了曲线，于是给它加上完整的 SVG path 解析（`M/L/H/V/C/Q/S/T/A/Z`，绝对+相对，贝塞尔与椭圆弧采样、even-odd 填充）。椭圆弧按 SVG spec F.6 做端点→圆心转换。第一次渲染直接报错，原因是命令字符类漏写了 `Aa`，弧命令被当成不存在而从 token 流里丢了。

出了两轮共 7 个候选：

- 第一轮：`①亭顶落日 ②三重拱 ③账本卡片`。②看着像 wifi，③像备忘录图标，都不行。
- 第二轮：`A 屋顶+水线+倒影 / B 屋顶+落日压在水线上 / C 折角账页 / D 屋顶+单点`。A 的倒影容易读成向下箭头，C 依旧像 PDF，D 太单薄。

**定稿 B**：一道曲线亭顶覆着一轮落日，日落的下沿被水线压住，下面再一道波纹。配色 `#BCCAD6→#93A8BC` 灰蓝渐变底，笔触用墨蓝 `#3D5164`、米白 `#F3F1EB`、淡赭 `#C89E77`、雾蓝 `#8DA6BA`。160/72/48/32px 和圆形、圆角两种遮罩都渲染看过，32px 仍能读出「顶 + 日 + 水线」；单色层作为剪影也成立。

三层资源与上一版同结构（背景渐变 / 前景 / monochrome），四条笔画全部落在自适应图标安全圆（圆心 54,54、半径 33）内。

### 分析页新增「本月节奏」

一笔小卡片：**笔数 / 平均单笔 / 最贵一天**。

- `BuildStatsUseCase.monthDigest(days, entryCount, total)` 是纯函数，测了 2 条：平均单笔按「笔」除而不是按「天」除（和日均是两个问题，别混成一个数）；整月没花钱时「最贵一天」必须是空，而不是报出某个 0.00 的日子。
- ViewModel 里这条流挂在 `monthSummaries` 下面而不是只跟月份走：否则记完一笔回来，笔数更新了而「最贵一天」还是切月时那份旧数据。

### 版本与实测数字

`versionCode 10 / versionName 1.9.0`，schema 仍为 2（本轮无库改动，`moveTo` 只重写已有的 `sortOrder` 列），证书不变（SHA-256 `fc86dddf…906c`），覆盖安装即可。

- 单测 56 → **66 全绿**（新增拖拽落点/让路 7 条、本月节奏 2 条、其余为回归），编译 0 error 0 warning，55 个 Kotlin 文件 import 审计 0 问题。
- release 包 1,912,697 → **1,912,801 B**。
- apksigner v2 + v3 true；zipalign `Verification successful`。
- `dist/` 已换成 v1.9.0 三件套。

**要装机确认的**：拖动的跟手感和落点是否准（数学有单测，手感没有）、首页两个数字在真实金额长度下是否好看、以及新图标在你手机启动器上的实际遮罩与着色。图标我只能保证「用同一组坐标自渲染出来的图」是这个样子。

---

## 24. v1.10 改动（拖动重写 + 首页并排 + 界面莫奈灰蓝）

### 拖动排序上一版为什么错乱

上一版用的是「让路式」：被拖行跟手，其它行按目标位置整体平移。它错了有三个原因，都在代码里：

1. 让路的位移基准是 `stridePx`（一行 + 间距），而它只在**第一次** `onSizeChanged` 时被赋值一次；中途任何重组或行高差异都会让基准和视觉对不上，整列就开始乱跳。
2. `pointerInput` 的 key 里含 `index`。而换一格 `index` 就变 → pointerInput 当场重启 → 手势被取消，表现就是拖到一半突然不听使唤。
3. 只有 40dp 的把手可拖，小面积拖动在可滚动列表里会和滚动抢手势，按下去常常先滚了半屏。

新版换成**「越过大半行就实时换位、位移回拨一行」**：

- 顺序由界面侧的 `SnapshotStateList` 持有，换一格就 swap；被拖行的位移永远只有自己那一段，错了也只错一行，行为可预测。
- 阈值取 **0.6 行**而不是 0.5，留回差，避免手指停在半行处来回抖。
- `pointerInput` 的 key 只含 `(id, count)`，实时下标改用 `currentIndex()` 查——就是为了让上面第 2 条不再发生。
- 触发方式改成**整卡长按**，把手只作为「这里能拖」的视觉提示；拖动结束才 `commitOrder(ids)` 一次写库，不是每换一格写一轮。
- 拖动期间不吃数据库回流：本地顺序先撑着，写完后 `activeIds` 变了再同步，否则手指底下的行会被旧顺序顶回去。

无障碍仍然保留：每行通过 `customActions` 暴露「上移 / 下移」，走同一条 `moveTo`。

### 首页两个数字改回一横排

上一版我把「本月变动 / 本月支出」做成了上下堆叠，理由是并排放不下五位数金额——**那是在替用户做取舍**。这次按并排实现，并且把"放不下"真正解决掉：用 `rememberTextMeasurer` 在可用宽度内从 25sp 逐档试到放得下，而不是全局缩小字号。金额正常时仍是 25sp，只有极端长度才自动降。

顺手把两个数字做成可点：**本月支出**点开当月的全部条目，分析页的**最贵一天**点开那一天的清单。看到「哪天最多」之后，下一个动作就是点开那天。

### 界面整体换成莫奈灰蓝

原来那套墨绿 + 琥珀只留在图标里不合理，这次界面跟上，并且直接取图标同源色：主色 `#3D5164`（屋顶墨蓝）、辅助 `#B08256`（落日淡赭）、中性阶改成带蓝的灰（画布 `#EDF1F4` / 卡片 `#F8FAFB` / 高容器 `#E3EAF0` / 描边 `#BCCAD6` / 正文 `#22303C` / 次要 `#546676`），深色对应 `#12171C / #192127 / #A8C6DE`。

标识符一并改名，不留说谎的名字：`Ink/InkLight/InkBright → SlateDeep/SlateMid/SlateBright`、`Amber/AmberBright → Ochre/OchreBright`、`OnInkDark/OnAmberDark → OnSlateDark/OnOchreDark`。8 个账单色压成莫奈灰调（苔绿 `#5E8C6D`、赭石 `#B0783F`、雾蓝、陶红、藕紫、池青、石墨、玫灰），**DB 里存的 key 一个都没动**，只换 label 和色值。

压灰之后辨识度是拿数字验的（WCAG 相对亮度比）：

| 组合 | 改前 | 改后 |
| --- | --- | --- |
| 次要文字 / 高容器（浅） | 4.21 ✗ | **4.66** ✓ |
| 苔绿 / 浅底 | 2.98 ✗ | **3.68** ✓ |
| 赭石 / 浅底 | 2.87 ✗ | **3.58** ✓ |
| 正文 / 卡片（浅、深） | — | 12.89 / 13.40 ✓ |
| 白字 / 主色按钮 | — | 8.21 ✓ |

描边对卡片底只有 1.60，但它是分隔线不是信息载体，刻意保持低对比。

### 版本与实测数字

`versionCode 11 / versionName 1.10.0`，schema 仍为 2，证书不变（SHA-256 `fc86dddf…906c`），覆盖安装即可。

- 单测 **64 全绿**（拖动的 `swapStepFor` 5 条 + `toNetYuanString` 符号位置 1 条，替换掉旧版 7 条让路式测试——那些函数已经不存在了）。
- release 包 1,912,801 → **1,912,805 B**；apksigner v2 + v3 true；zipalign `Verification successful`。
- `dist/` 已换成 v1.10.0 三件套。

**必须装机验的**：拖动手感（数学有单测，跟手与抖动没有）、灰蓝主题下 8 个账单色是否还分得开（我只验了对比度、没验辨识度）、以及新图标在你启动器上的实际遮罩与着色。

---

## 25. v1.11 改动（拖动换成库实现 + 首页数字修复）

### 修：首页两个数字不显示

用户报「本月变动和本月支出的数字不显示」。这是我上一轮引入的：为了并排放下长金额，我用 `rememberTextMeasurer` 在运行时从 25sp 往下试字号，量到可用宽度。但 `AnimatedContent` 会用**旧内容的尺寸约束**去测新内容，首帧旧内容还不存在，量到的宽度是 0，`while` 循环一路退到底、文字被压到不可见。

改成**确定性排版**：`MoneyPair` 从 25sp 降到 20sp 等宽数字。按 360dp 屏算，并排一格约 150dp，最长形态「¥1,234,567.89」在 20sp 下约 143dp，放得下，因此不需要运行时自适应；再加 `maxLines = 1` + `Ellipsis` 兜底。教训是：**展示层的自适应如果依赖测量时机，就必须证明那个时机一定拿得到非零约束**，否则宁可把尺寸定死。

### 拖动排序：不再自己写

上一版自己实现的拖动被明确否掉：一次只能挪一格、手感差、把手位置也不好。三个原因都在我那份代码里——落点基准只测一次、`pointerInput` 的 key 含 index 导致换格即断手势、40dp 小把手和外层滚动抢事件。

这次换成社区维护的 **`sh.calvin.reorderable:reorderable:3.1.0`**（阿里镜像和 Maven Central 都有），删掉自己那 170 行实现和它的 5 个单测。库提供的是连续移动 + 边缘自动滚动 + 抬起阴影，正是「一个一个挪、手感垃圾」的反面。

接入前先把 API 读准，没有靠记忆猜：`gradle dependencies` 只下载了 metadata，于是从镜像直接取 AAR 和 sources jar，用 `javap` + 读源码确认签名，结果发现两处和直觉相反：

- `ReorderableColumn` 的必填参数是 **`onSettle: (from, to) -> Unit`**（松手落定时给最终下标），而 `onMove: () -> Unit` 是无参通知。所以列表变更和落库都放在 `onSettle` 里，一次拖动只写一轮库。
- `Modifier.draggableHandle(dragGestureDetector = DragGestureDetector.Press)` **默认就是按下即拖**；长按版本叫 `longPressDraggableHandle`。

于是交互给了两条路：**整卡长按可拖**（点按仍然是进编辑，不冲突），**右侧 44dp 粗把手按下即拖**（不用先长按）。读屏仍暴露「上移 / 下移」自定义动作，并且走的是和拖动**同一个** `moveRow`，不会出现两套行为分叉。顺手把只有单步语义的 `ManageBillsUseCase.moveTo` 和 `ManageBillsViewModel.move` 删掉。

`Panel` 加了 `shadowElevation` 参数，让拖起的高度用现成卡片表达，不再出现第二种卡片写法。

### 又一次：脚本截断源文件（第三次）

改 `BillRow` 时我用 `s.index(...)` 定位函数结尾，遇到 `BillRow` 之后没有下一个 `@Composable` 的情况取了 `len(s)`，**把文件尾部整个删掉了**：`BudgetDialog`、`parseBudget`、`private val NoPadding` 三个定义一起没了，编译器报的是 `Unresolved reference 'BudgetDialog'`。

恢复与加固：

1. 还是靠 `~/.qoder-cn/file-history/<session>/…@vN`，按 `grep -l "fun BillManageScreen"` 找候选、用 mtime + 行数 + 本轮才有的标记挑出 07:21 那份（405 行，含 `BudgetDialog`），只取尾部拼回，然后核对函数清单齐全（`PreferencesPanel / BillRow / BudgetDialog / parseBudget`）。
2. 恢复时又踩一次：切片起点差一行，把上一个函数的收尾 `}` 也带了进来，表现为 `Expecting a top level declaration`。
3. **根因加固**：`NoPadding` 原来在两个文件里各写一份 `private val`，所以文件尾部被删时它跟着消失。现在提到 `ui/common/Panels.kt` 作为公共 `val NoPadding`，两处改为 import。写脚本改文件时，边界必须 `assert` 命中且断言结果（括号配平、函数清单一致），不能只看「跑完了没报错」。

### 版本与实测数字

`versionCode 12 / versionName 1.11.0`，schema 仍为 2，证书不变（SHA-256 `fc86dddf…906c`），覆盖安装即可。

- 单测 **59 全绿**（64 减掉随自研拖动一起删掉的 5 条；新增修复由编译与人工核对保证，展示层测量时机无法在 JVM 单测里复现）。
- release 包 1,912,805 → **1,933,285 B**（+20.4 KB，reorderable 库的 dex 与资源）。
- apksigner v2 + v3 true；zipalign `Verification successful`。
- `dist/` 已换成 v1.11.0 三件套。

**这轮全是交互与观感，必须装机验**：拖动的连续性和边缘滚动是否真的顺手、按下即拖的把手会不会误触编辑、以及首页两个数字在各种金额长度下是否显示完整。我只能保证编译通过、签名对齐、金额宽度按 20sp 等宽数字算过。

---

## 26. v1.11.1 修复：进「账单」页闪退

### 现象与根因

v1.11.0 装上后，点进账单页直接崩溃。根因在我对接 reorderable 的方式，不在库本身：

```kotlin
// 错：实例永远不变，只在原地增删
val rows = remember { mutableStateListOf<Bill>() }
LaunchedEffect(activeIds) { rows.clear(); rows.addAll(active) }
```

库内部是这么写的（读 sources jar 确认，不是猜）：

```kotlin
val reorderableListState = remember(list, spacing) {
    ReorderableListState(list.size, spacing, onMove, onSettle, ...)
}
// ReorderableListState 构造时一次性按 listSize 建好：
//   itemIntervals = MutableList(listSize) { ItemInterval() }
//   itemOffsets   = List(listSize) { Animatable(0f) }
//   draggableStates = List(listSize) { ... }
```

`remember(list, ...)` 认的是 **list 的实例**。我传的是一个永不换实例的 `mutableStateListOf`，
而首帧 `rows` 还是空的（要等 `LaunchedEffect` 才填），所以状态按 `listSize = 0` 建好后就再也不会重建；
`ReorderableItem` 去取 `itemIntervals[0]` → `IndexOutOfBoundsException` → 进页面即崩。

### 改法

换成**不可变列表 + 换实例**，让 `remember(list, ...)` 在内容变化时真的看到新对象：

```kotlin
var rows by remember { mutableStateOf<List<Bill>>(emptyList()) }
LaunchedEffect(activeIds) { rows = active }
val moveRow: (Int, Int) -> Unit = { from, to ->
    rows = rows.toMutableList().apply { add(to, removeAt(from)) }
    viewModel.commitOrder(rows.map { it.id })
}
```

### 这条教训的通用形式

给一个会 `remember(key)` 并按集合大小预分配状态的组件，传**原地可变的稳定实例**（`mutableStateListOf`、
`MutableList`）是错的：key 永远不变，预分配的大小就永远停在第一次的值。要么传不可变快照，
要么确认该组件的 key 用的是内容而不是实例。

### 我这次做得不对的地方

v1.11.0 出包前，我对这个页面的全部验证只有「编译通过 + 签名对齐」。拖动是运行时组合行为，
JVM 单测跑不了 Compose，而我当时没有补任何能在本机验证「进页面不崩」的手段，
把真机当成了第一次测试。连续两轮让你拿到崩的包，这是流程问题，不是运气问题。

下一步该补的是 **Robolectric + Compose UI 冒烟测试**：在 JVM 里真的组合一次 `BillManageScreen`，
断言不抛异常。它能在出包前就抓到这一类越界。

### 版本

`versionCode 13 / versionName 1.11.1`。59 单测全绿，apksigner v2+v3、zipalign 均通过。
`dist/TingLedger-v1.11.1-release-signed.apk`；崩过的 v1.11.0 包已从 dist 删除，别再装它。

---

## 27. v1.11.2：补上「发版前先把界面组合一次」这道闸

### 为什么前面会连发两个崩的包

v1.11.0 和 v1.11.1 我都只做了「编译通过 + 单测全绿 + 签名对齐」就出包。但这三个信号**没有一个会把界面真正组合出来**，而这次的崩溃恰好只发生在组合期。这是流程漏洞，不是运气差。

### 补的闸：Robolectric + Compose 冒烟测试

新增 `app/src/test/.../ui/manage/BillManageScreenSmokeTest.kt`：在 JVM 里用真实的 `AppContainer`（真 Room、真播种数据）把账单页组合一次，再 `waitUntil` 等账单节点出现。

一个坑值得记：一开始用 `waitForIdle()` + `assertIsDisplayed()`，两个用例都失败——因为账单是 Room 从后台线程用 Flow 推上来的，`waitForIdle` 不等它。那样测到的只是「空列表没崩」，正好是这次 bug 的盲区。改成 `waitUntil(10s)` 之后才真的走到有数据的那次组合。

**并且验证了这个测试有牙齿**：把 bug 原样装回去（`mutableStateListOf` + 原地 clear/addAll），两个用例立刻失败，异常就是

```
java.lang.IndexOutOfBoundsException: index: 0, size: 0
    at androidx.compose.runtime.snapshots.SnapshotStateList.get
    at sh.calvin.reorderable.ReorderableListScope$ReorderableItem$2$1.invoke(ReorderableList.kt:334)
```

——和真机闪退同一处。然后恢复正确实现，61 个测试全绿。一个从没在坏代码上跑过的测试，不能算测试。

### 顺手加的保险：release 的 keep 规则

`proguard-rules.pro` 增加 `-keep class sh.calvin.reorderable.** { *; }`。理由和当初保 `data.local` 一样：这台机器没有设备可验证 release 包的运行时行为，而这个库一崩就是整页打不开。代价实测：release 包 1,933,285 → **1,982,437 B（+48 KB，约 2.5%）**。如果后面确认 R8 与此无关，这条可以撤。

### 仍然没解决的部分

用户反馈「依旧闪退」，但我这边：设备未连（`adb devices` 为空），拿不到崩溃栈；而 debug 组合测试是绿的。所以**还剩两种可能没被排除**：

1. 装的仍是 v1.11.0（唯一下载过的包），而 v1.11.1/1.11.2 其实已经修好；
2. 崩溃只在 release 出现（R8），debug 测不出来。

v1.11.2 同时针对这两点：代码修复 + keep 规则。要区分它们，需要真实堆栈。

### 版本

`versionCode 14 / versionName 1.11.2`，**61 单测全绿**（新增 2 条组合冒烟），apksigner v2+v3、zipalign 均通过。`dist/TingLedger-v1.11.2-release-signed.apk`，v1.11.0/1.11.1 已从 dist 删除。

---

## 28. v1.12.0 改动（去重新建入口 + 常用备注 + 全站组合冒烟）

### 首页新建入口：三个变两个

用户指出重复。查下来首页其实有**三个**新建入口：支出区标题右侧「新建」、列表末尾那张「新建账单」大卡片、收入区标题右侧「新建」。

删掉的是**列表末尾那张卡片**，保留两处标题右侧的动作。理由不只是"少一个"：

- 那张卡片永远创建**支出**账单。你在收入区往下看到它点下去，建出来的方向就是错的；
- 而两个区头部的「新建」各自带着上下文（`editorKind = EXPENSE / INCOME`），点开弹窗时方向已经预选好；
- 卡片混在一排账单里，看起来像一条账单却不是一个账单，是列表里的异物。

空状态里的「新建第一个账单」保留——那是没有账单时唯一的入口，不重复。

### 新功能：常用备注建议

记同类账时备注总是那几个字（地铁、午饭、房租），逐条重打很啰嗦。现在录入弹层里，备注框下方会给出**这条账单最近用过的备注**，点一下即填入。

- 取数：`EntryDao.recentNotes(billId, limit)` 按时间倒序取 30 条原始备注；
- 清洗：`NoteSuggestions.from()` 做 trim → 去空 → 去重 → 限 3 条。**去重放在 trim 之后**，否则「地铁」和「地铁 」会在建议里出现两次，看着像界面坏了（4 条单测盯住）；
- 刷新时机：跟着「选中的账单」和「刚记完一笔」两个事件走。记完一笔后刚用的那条应该排到最前，所以 `submit` 成功后 bump 一个 reload 计数；
- 只在**备注还空着时**显示。已经开始打字了再顶一排胶囊就是干扰。
- 建议条可能很长，用横向滚动而不是挤压。

### 把「组合一次」这道闸推广到全站

上一轮只给账单页补了冒烟测试，这次五块主要界面 + 录入弹层全覆盖（`ScreenSmokeTest`，6 条）：首页、分析、账单详情、搜索、账单管理、RecordSheet。

关键点是**等的是只有异步数据回来才会出现的节点**（"吃饭"、"支出 · 苔绿"、"这个月还没有支出记录"），而不是首帧就有的静态文案——否则测到的只是"空态没崩"，正好是上次那个 bug 的盲区。

一个实测数据：Robolectric 下详情页那条首查耗时 11.6s（首次建库 + Room 冷启动），所以超时从 15s 提到 30s，不然这台机器上会偶发假红。

### 版本与实测数字

`versionCode 15 / versionName 1.12.0`，schema 仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测 61 → **71 全绿**（新增 6 条全站组合冒烟 + 4 条常用备注清洗），编译 0 error 0 warning。
- release 包 **1,982,437 B**（与上一版同尺寸：新增的建议查询被删掉的入口卡片抵掉了）。
- apksigner v2 + v3 true；zipalign `Verification successful`。
- `dist/TingLedger-v1.12.0-release-signed.apk`，v1.11.2 已移除。

**仍需装机确认**：常用备注的胶囊位置是否顺手（我按"备注框正下方"排的，可能太靠下）、以及首页去掉末尾卡片后新建是否还够显眼。这两点是观感，冒烟测试只能保证不崩、节点存在。

## 29. v1.13.0 改动（换清新图标 + 长按快捷记账 + 把"长期红的闸"修绿）

### 启动图标：第三稿才敢叫"清新"

用户要"清新一点"。前两稿的问题不是画得丑，是**读错了**：

- 第一稿只有一道水面。屋脊的弧 + 檐下的圆点 + 下面一条线，在 48dp 的桌面上被读成**一只眼睛**（缩小后三个元素构成"眼白+瞳孔+眼线"的完形）。加了第二道水面破掉这个读法——两道平行弧线只能理解为水波，不会有人把它看成两只眼睛叠在一起。
- 第二稿笔画太粗、留白太少，压在小圆角遮罩里发闷，跟"清新"无关。

最终稿：一道亭檐压一轮落日，下面两道水面。

```
亭檐  M24,50C36,41 46,31 54,31C62,31 72,41 84,50L79,55C69,48 61,38 54,38C47,38 39,48 29,55Z   #4E7189
落日  圆心(54,49) 半径 7.5                                                                     #D88C62
水面  y=63.5 宽 56 高 3.4 / y=71 宽 38 高 3.0                                                   #FBFCFD
```

配色跟着界面走莫奈灰蓝：背景仍是 `#E6F0F6 → #C2D9E8` 的清晨淡雾渐变，檐是灰蓝、日是去饱和的暖橙，水面接近白。三层都在（background / foreground / monochrome），monochrome 用同一组几何全白填充，主题图标模式下不会退化成一坨。

**安全圆是硬约束，不是感觉**。自适应图标 108×108 视口，各家启动器遮罩内切圆半径 33（圆心 54,54）。逐个极值点算距心距离：

| 点 | 距 (54,54) |
| --- | --- |
| 水面右上角 (82, 66.9) | **30.83** ← 最紧的一处 |
| 亭檐端点 (24, 50) / (84, 50) | 30.26 |
| 第二道水面角 (73, 74) | 27.59 |
| 落日最外点 (54, 41.5) | 12.50 |

最大 30.83 < 33，四周一圈余量 ≥ 2.17，任何遮罩形状（圆、圆角方、水滴、圆形）都切不到笔画。中间有一稿亭檐端点落在 x=18/90，`|18−54| = 36` 已经大于 33，**不用管 y 就已经出界**——圆形启动器会把两个翘角削平。这一稿是先按安全圆把坐标往里收，不是画完再检查。

一个必须说清楚的限制：我只能保证"用同一组坐标自己渲染出来的图"是这个样子。各家启动器的遮罩、着色、动态主题我这儿没有设备可验。

### 新功能：长按图标快捷记账

桌面长按 App 图标直接给某个账单记一笔，省掉「打开 → 找账单 → 点开」三步。

- 选哪几个账单是一段有主张的逻辑，所以 `QuickShortcuts.selection()` 抽成纯函数单测（4 条）：**未归档 → 支出优先 → 按 sortOrder → 取 4**。收入占快捷位是浪费——工资一个月记一次，而"刚花了一笔"才是掏手机的理由。
- 发布时机：`AppScaffold` 观察 `observeBills()`，账单列表一变就 `setDynamicShortcuts`。快捷项跟着数据走，新建/归档/改名/排序都会立刻反映到长按菜单。
- 一个非显然的坑：`LaunchedEffect` 的 key 如果只用 `billId`，**连续两次点同一个快捷项不会重新触发**（第二次的 key 值没变）。所以 `MainActivity` 里维护 `billId to seq`，每次 `takeShortcut` 把 seq 加一再交给组合层，`LaunchedEffect(shortcut)` 拿到的元组必然变。`onNewIntent` 也走同一条路，热启动点快捷项才有效。

### 修闸：`test` 长期是红的，而长期红的闸等于没有闸

这次发版前跑全套（`test` = debug + release 两个变体），**release 变体 9 条组合冒烟全红**：

```
java.lang.RuntimeException: Unable to resolve activity for Intent { act=android.intent.action.MAIN ... }
```

原因跟界面无关：`createComposeRule()` 需要一个 manifest 里注册过的 `ComponentActivity`，它由 `ui-test-manifest` 提供，而这个依赖只能挂 `debugImplementation`——挂到 release 就会把测试 Activity 打进正式包。所以 release 单测跑组合测试必然失败，属于环境缺件。

三个选择里排掉了两个：把 `ui-test-manifest` 提到 release（污染正式包，绝对不行）；留着红（让人习惯红色，等于取消这道闸）。最后是在 `app/build.gradle.kts` 里给 `testReleaseUnitTest` 加 filter，**只排掉那两个组合冒烟类**，纯逻辑的 67 条两个变体都照跑；"把界面组合一次"这道检查由 `testDebugUnitTest` 把。

顺带用这次改动验了一件事：加完 filter 再跑 `assembleRelease`，AGP 报 `UP-TO-DATE`——**任务过滤确实碰不到 APK**，所以 dist 里那个包还是签名校验过的那份字节。

### 版本与实测数字

`versionCode 16 / versionName 1.13.0`，schema 仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **76 条全绿**；release 67 条全绿（9 条组合冒烟按上节排除）。
- release 包 **1,982,401 B**（v1.12.0 为 1,982,437 B，**−36 B**：快捷记账那段代码被 R8 压掉后比图标 XML 省下的还多一点）。debug 20,421,063 B，mapping 包 3,507,346 B。
- 发布件 `dist/`：`TingLedger-v1.13.0-release-signed.apk` / `-debug.apk` / `mapping-v1.13.0-release.zip`，v1.12.0 三件已删除。
- dist 里的 release 包与构建输出 **sha256 相同**（`91d65707…4a8fa37b`），不是"复制了一份大概是同一个"。
- apksigner v2 + v3 true；zipalign `Verification successful`；`aapt2 dump badging` 确认 `versionCode='16' versionName='1.13.0'`、`application-label:'亭宣记账'`、图标 `res/BW.xml` 仍在。
- 结构体检：61 个 Kotlin 文件，首行 `package` 与 import 块连续性 **0 处异常**（上一轮那次批量改 import 的事故留的尾巴，这轮复查干净）。
- 新鲜度：最新源码比 APK 早 120s；唯一比 APK 新的是 `app/build.gradle.kts`（上面那个 test filter），已由 `assembleRelease UP-TO-DATE` 证明不影响包体。

**仍需装机确认**：三点。一是新图标在他的启动器上到底是什么观感——尤其"清新"这个词，我只能保证坐标级不越界；二是长按图标能不能看到 4 个快捷项（各家 ROM 对 dynamic shortcuts 的支持不一，有些桌面要额外授权）；三是走一遍快捷项→弹层是否正确预选中那条账单、以及热启动路径。上一轮"依旧闪退"至今没有设备端确认，我这边只有 9 条 JVM 组合测试证明这些界面在 JVM 里组合不崩。

## 30. v1.14.0 改动（整库备份与恢复）

记账 App 到这一步，所有功能都建立在"数据还在"上面。之前唯一的出口是 CSV——它能看能用 Excel 加工，但**导不回来**，而且预算和黑白模式在 DataStore 里，CSV 完全不含这一半。这轮补的是真正的兜底。

### 为什么备份不是"把数据库文件拷出来"

三个理由，按分量排：

1. 预算、黑白模式存在 DataStore 的 XML 里，拷 `.db` 只带走一半状态；
2. 各家 ROM 的 WAL 落盘与文件锁差异让"边跑边拷文件"不可控，而我这儿没有设备能验（`adb devices` 至今为空）；
3. 文本格式能自带校验和，**截断和篡改当场就能发现**；二进制拷贝做不到——拷坏一半，只有恢复时才晓得。

所以备份是一份**逻辑快照**：账单、条目、偏好全列出来，末尾把自己的行数和金额和再抄一遍。

### 格式

```
FORMAT  tingxuan-backup/1      SCHEMA 2        APP 1.14.0(17)
CREATED <ms>                   BILLS 3         ENTRIES 4      SUM 898100
THEME   dark                   BUDGET 300000
B   id  名称  图标  颜色  方向  排序  归档  建档时间        （制表符分隔）
E   id  所属账单  金额  备注  归属时间  录入时间
#END  bills=3  entries=4  sum=898100
```

自由文本里的 `\` `换行` `回车` `制表符` 全部转义，反斜杠本身最先转义，所以 `午饭\n`（字面的反斜杠 n）不会被读成换行——这条单独有测试盯着，因为它是这类手写编解码最容易翻车的地方。

**拒收规则**（任何一条不过就整份拒绝，绝不"能恢复多少算多少"）：FORMAT 不匹配、缺 `#END`（= 被截断）、出现多个 `#END`（= 被拼接过）、BILLS/ENTRIES/SUM 与正文对不上、`#END` 里的金额和与正文对不上、id 重复、方向键不认识、金额为负、条目指向不存在的账单、SCHEMA 比本机新。

方向键**故意不做兜底**：`BillKind.fromKey` 平时遇到陌生键会退回"支出"，但恢复时这样干会把一笔工资静悄悄变成支出——那是把账本改错而不是改坏，比直接报错恶劣得多。所以这里遇到陌生方向就整份拒收。

### 恢复分三段，任何一段不过都不碰数据

1. 文件校验（纯函数，坏文件在这一层全挡掉）；
2. 先给**当前**库拍一份快照留档——它就是界面上「撤销这次恢复」的原料；
3. 整库替换，并且替换完在**同一个事务里**自己跟自己对账（行数、金额和），对不上就抛异常。

Room 的 `@Transaction` 在方法体抛异常时不会把事务标记为成功，于是全部回滚——所以"恢复失败"的结果是账本一点没动，而不是换出一半。这条我专门用一条测试钉住：绕过文件校验，直接塞给 DAO 一份外键悬空的数据，然后断言原来的 3 张账单 4 笔记录一笔没少。

另一个用测试钉住的事实：`INTEGER PRIMARY KEY AUTOINCREMENT` 插入显式 rowid 时，SQLite 会把 `sqlite_sequence` 推到 `max(旧值, 新 rowid)`。所以带着原 id 恢复回去之后，新记的账不会撞号——恢复完插入的第一笔拿到的是 14 而不是已存在的某个 id。这条不验就是玄学，验了才是事实。

### 顺带：一处只读文件才能发现的问题

恢复走 `ContentResolver` 读文件时用了 `InputStream.readAllBytes()`——那是 Java 9 的 API，Android 上要 **API 33**，而本项目 minSdk 26，编译能过、真机 Android 8/9 上直接 `NoSuchMethodError`。改用 Kotlin 标准库的 `readBytes()`。这类错编译器不管、R8 不管，只有把 minSdk 与 API 表对着看才发现，所以留了注释。

同时给读取加了 32 MB 上限：选文件用的是 SAF 过滤器（`text/plain` / `application/octet-stream`），但用户完全可以挑一个巨大的日志文件过来，不该把它整个读进内存。

### 冒烟测试抓到一次真回归（不是测试写错）

新加的备份面板放在偏好面板下面，于是账单列表被顶出首屏。`LazyColumn` **不组合视口外的内容**，三条老断言（等"吃饭"、等"支出 · 苔绿"）全部超时失败。

这不是测试的毛病，是界面的毛病：这一页叫「账单」，主角是列表，外观/导出/备份都是低频操作。所以顺序调成 **账单 → 已归档 → 偏好 → 备份**，把低频的沉到底部。备份区的断言则老老实实先 `swipeUp` 滚到底再等节点——视口外的东西本来就不该假装看得见。

顺带记一次我自己的误判：`#END` 金额篡改那条测试一开始报 Success（等于"篡改没被发现"），我以为格式有洞。写了个临时探针把篡改后的文本单独跑一遍，确认格式确实拒收（`Rejected(结尾行的金额和与正文不一致)`），才看出是**测试**把未篡改的原文也丢进取 `reason()` 的辅助函数里——原文当然解析成功，于是强转炸了。探针删掉，同时把"截断必被拒"改成常驻的四处裁剪（10%/30%/60%/90%）断言，不再只依赖某一句具体报错。教训是：断言失败先看断言怎么写的，别急着改被测代码。

### 版本与实测数字

`versionCode 17 / versionName 1.14.0`，**schema 仍是 2**（备份走逻辑快照，没动表结构），证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug 76 → **103 条全绿**（+20 格式与拒收、+6 真 Room 往返/回滚/自增/撤销/偏好、+1 备份区组合冒烟）；release 变体 93 条（10 条组合冒烟按 §29 那套排除）。
- release 包 **1,998,785 B**（上一版 1,982,401，**+16,384 B**：整条备份链路的代码压完约 16 KB）。debug 20,470,215 B，mapping 包 3,542,186 B。
- `dist/` 三件换成 v1.14.0，v1.13.0 已删除；两个 APK 与构建输出 **sha256 逐一相符**（release `679b947a…`、debug `23c8398a…`）。
- apksigner v2 + v3 true；zipalign `Verification successful`；`aapt2 dump badging` 两个包都是 `versionCode='17' versionName='1.14.0'`。
  这里也差点出错：`test assembleRelease` 不会重出 debug 包，我第一次复制过去的 debug APK 构建时间还是上一版的 15:32——靠"包体积和上一版一模一样"起疑才发现，重新 `assembleDebug` 后才对。以后发版把两个包的 mtime 一起看。
- 结构体检：68 个 Kotlin 文件，`package` 首行 / import 块连续 / import 块字典序 / 尾部无空行，**0 处异常**（这轮新文件里确实有一处 import 顺序不对，已修；修完重构建，release 包 sha256 不变，说明纯 import 调整动不到字节码）。

**仍需装机确认**：这轮的功能整条链路都得在你机器上验，JVM 测试替不了的部分很具体——① SAF 在你这台 ROM 上"备份账本"能不能真的写出文件、写到云盘目录会怎样；② 选文件时那份备份是否被 mime 过滤挡住看不见（真遇到就把过滤器放开到 `*/*`）；③ 恢复后首页/分析/搜索是否立刻刷新（Room 的 Flow 推新，理论上会）；④ 撤销按钮只在本次会话有效——杀掉 App 再进来就没有了，这个预期要先说清楚。另外「上次备份时间」现在只在被拒收或成功时才提示，没做过"提醒备份"的通知，那是另一件事。

## 31. v1.15.0 改动（「账单」页改成设置页 · 首页编辑态）

### 为什么改

第三个 tab 叫「账单」，里面却塞着：偏好 + 预算 + 导出 + 备份 + 账单列表 + 归档。而首页上"点账单记一笔"才是主路径，想改个名字、换个顺序、归档一条，得先跳到另一个页。名不副实，两头别扭。

拆完之后一句话能说清：**首页负责账单本身（含整理），设置页负责偏好与数据**。

### 设置页（原「账单」页）

四节，按"改得勤 → 改得少"排：

| 节 | 内容 |
|---|---|
| 偏好 | 黑白模式分段控件 · 月度预算 |
| 数据 | 导出 CSV · 备份账本 · 从备份恢复（刚恢复完还会多一行「撤销这次恢复」） |
| 已归档 | 每条归档账单：名字、方向、名下笔数、「恢复」；点进去可改名或删除 |
| 关于 | 版本 `1.15.0(18)` · "数据只存在这台手机上，不联网、不上传" · N 个账单 M 笔记录 |

「关于」那行的条目数是新加的响应式查询（`EntryDao.observeCount()` 返回 `Flow<Int>`），不是打开页面时抓一次快照——数字得跟着库走。tab 标签从「账单」改成「设置」，图标 widgets 换齿轮，路由 `manage` 一并改成 `settings`。

导出 CSV 与备份/恢复合进同一张「数据」面板，因为它们是同一件事：数据要离开手机、回到手机。`BackupSection` 因此改名 `BackupRows`，不再自带 Panel，由设置页把它接进面板里。

### 首页编辑态

- 右下角加号 FAB 从旧账单页搬过来负责新建（弹窗默认方向是支出，方向选择器就在名称下面一步）。
- 两个区头原来的「+ 新建」换成**「编辑 / 完成」**开关。
- 编辑态下每条账单行尾部从「合计 + 明细入口」换成**「归档 + 双横把手」**：点行弹编辑框（名称框就在最上面，顺手能改图标/颜色/方向，删除也在那儿，两下点击确认并告知会带走名下 N 笔），组内可拖动排序，松手才落库。
- **长按的归属必须唯一**：平时长按=看明细，编辑态长按=拖动。所以编辑态里 `combinedClickable` 的 `onLongClick` 传 null，不再挂"查看明细"。这条有测试盯着。
- 双横把手是自己画的两条圆角横（`ReorderHandle`），没用 Material 的 `drag_handle`——那是三条横，语义更像"菜单/更多"。形状不携带语义，所以读屏标签 `contentDescription = "拖动排序"` 显式给上，并保留「上移 / 下移」自定义无障碍动作，走的是和拖动完全同一条 `onMove`。

### 排序落库的坑

`sortOrder` 是**全局**字段（`applyOrder` 按传入列表下标 0..n-1 重编），而首页现在分成支出/收入两组、各一个 `ReorderableColumn`。如果拖收入组时只提交收入组的 id，支出组和收入组就会在编号上互相穿插，看起来顺序莫名其妙。所以每次落定都提交**两组拼起来的完整顺序**（支出在前、收入在后，组内用新顺序）。

另外每条 `ReorderableColumn` 的 `list` 仍然必须是"换实例"的列表，理由与 §26 那次闪退完全一样，注释也写在了代码里。

### 顺手一处性能修正

编辑弹窗要显示"名下几笔"，我一开始给首页加了第二条 `observeBillSummaries(全时段)`——可首页已有的 `summaries` 每条就带 `allTimeCount`。改成直接从已加载的摘要派生，省掉一条常驻的全表 GROUP BY。

### 改名要改彻底（这轮又踩一次）

包从 `ui.manage` 挪到 `ui.settings` 之后，`app/build.gradle.kts` 里 release 变体的测试排除名单**还写着已删除的旧类名**。不修的话新的 `SettingsScreenSmokeTest` 会在 release 变体真的跑起来，然后因为缺 `ui-test-manifest` 全红——一道莫名其妙的红。同理 `ScreenSmokeTest` 里根组件原来断言 tab 文字"账单"，现在改等"账本"。

新增的编辑态冒烟是双向断言的：先确认"查看明细"在、"拖动排序"不在 → 点「编辑」→ 反过来。只断言一边就拦不住"两种模式手势打架"。

### 版本与实测数字

`versionCode 18 / versionName 1.15.0`，schema 仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **104 条全绿**（设置页冒烟 3 条 + 首页编辑态 1 条，替换掉原管理页冒烟 3 条）；release 变体 93 条全绿（11 条组合冒烟按 §29/§30 排除）。
- release 包 **2,015,169 B**（v1.14.0 为 1,998,785，**+16,384 B**）。debug 20,486,658 B，mapping 包 3,554,041 B。
- apksigner `Verifies` + v2/v3 true；zipalign `Verification successful`；两个包 `badging` 均为 `versionCode='18' versionName='1.15.0'`；dist 与构建输出 sha256 一致（release `83f9de00…`、debug `cb5d8426…`）。
- 结构体检：68 个 Kotlin 文件，包名首行 / import 连续 / import 字典序 / 尾部无空行 **0 异常**，`ui.manage` 残留引用 **0**。这轮还清掉了自己引入的 3 处 import 乱序——修完重构建，release 包 sha256 **一字不差**，正好证明纯 import 调整动不到字节码。

**仍需装机确认**：四点，都是手感而不是功能。① 编辑态把手只有 40dp 宽，按得住吗、会不会误触旁边的「归档」；② 改名走的是整个编辑弹窗而不是内联输入框（为了不和长按拖动抢手势），代价是改一个字要多看两个选择器，这个取舍你未必认；③ 新建收入账单现在要在弹窗里切方向，少了原来区头那个直达入口，够不够用；④ 首页 FAB 与列表底部 120dp 内边距是否刚好不挡最后一行。

## 32. v1.15.1 改动（编辑态去掉说明书 · 尾部同骨架交叉淡变 · 两组独立）

§31 那版发出去后你给了三条反馈，都落在"我为了自解释多做了事，反而打断了视觉"。

### 1. 提示行删了

进编辑态时在区头下面塞了一行"点名字改名 · 长按或拖双横排顺序 · 归档只是从首页收起来"。删了。控件本来就该自己说清楚：区头的「编辑」就是开关，双横就是把手，归档那个色块就是归档——多一行说明书等于承认界面没做对。

### 2. 尾部互换改成同骨架交叉淡变

原来两态是两套结构（普通态 `Column(金额 + 明细色块)`，编辑态 `Row(归档按钮 + 40dp 把手)`），而且右内边距还不一样（md ↔ xs）。一点「编辑」，行高、右侧宽度、左边权重列的可用宽度全变，整列跟着跳。

现在两个模式共用**同一个形状**：左侧图标/名称/占比条完全不动，右侧固定两行（26dp + 3dp + 26dp），每行叠着两个互斥内容，只做淡入淡出：

| 行 | 普通态 | 编辑态 |
|---|---|---|
| 上 | 本期合计 | 归档色块 |
| 下 | 明细箭头 | 双横把手 |

中间换过一版用 `Crossfade`，尺寸恒定这点是对的，但它有个更糟的性质：**淡出的那份内容一直留在语义树里**——看不见的「查看明细」照样能被点、被读屏念到，等于每行挂着两个鬼控件。所以最终用两个互斥的 `AnimatedVisibility(fadeIn/fadeOut)`，外层框高度写死，淡变结束后淡出的那份真的从组合里消失。

### 3. 支出与收入的编辑各自独立

`editMode` 一个布尔换成 `editingExpense` / `editingIncome` 两个开关，拖动与落库逻辑本来就是按组走的，互不干涉。

两个连带细节：

- **点「编辑」不能顺手把另一组复位**。要是切换时写 `editingIncome = false`，"只编辑收入"这个状态就永远达不成——而且毛病藏在另一个按钮的行为里，出问题时最难想到是这儿。
- 首页只剩一个「+」，它得跟着开关走：只编辑收入时，新建默认方向就是收入，不然那个按钮还在暗示"新建支出"。

（这里原本我还想"顺手修一个 FAB 要按两下才开弹窗的 bug"，写文档时对着代码核了一遍——**并不存在**：`AlertDialog` 点外部确实会回调 `onDismissRequest`，标记被正常清掉，一次就开。撤掉了。记这一条是因为它的反面才是重点：改动越顺手的感觉越要回代码里核，别把想象写进发布说明。）

### 测试为了这三条改动学到的东西

断言"两组独立"必须先让两组**同时存在**。三连坑：

1. `LazyColumn` 视口外的项根本不组合，所以在顶部数不到收入那个区头；
2. 想用手势滑下去——编辑态里 `reorderable` 自己要吃纵向手势，`performGesture { swipeUp() }` 白滑，滚不动；
3. 改走语义的滚动动作 `performScrollToIndex(40)`，它**不是夹到末尾而是直接抛** `Can't scroll to index 40, it is out of bounds [0, 6)`。

最后给这条测试的 Robolectric 一个高屏幕：`@Config(qualifiers = "w411dp-h3200dp")`。两组一次全组合，断言就干净了——编辑按钮从 2 个变 1 个、支出组 5 个明细入口消失只剩收入那 1 个、`drag-handle-工资` 查不到而 `drag-handle-吃饭` 查得到。

还有一个语义树细节：整行是 `mergeDescendants` 节点，`testTag` 留在未合并的子节点上，`onAllNodesWithTag` 必须带 `useUnmergedTree = true` 才查得到。把手因此按账单名打标（`drag-handle-吃饭`），不然只能数"屏幕上一共有几个把手"，区分不了是哪一行。

### 版本与实测数字

`versionCode 19 / versionName 1.15.1`，schema 仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **104 条全绿**，release 变体 93 条全绿。首页编辑态那条现在是双向断言（进编辑态的组 + 没进编辑态的组各查一次）。
- release 包 **2,015,169 B**，debug 20,586,396 B，mapping 包 3,556,603 B。
- apksigner `Verifies` + v2/v3 true；zipalign `Verification successful`；`badging` 两个包都是 `versionCode='19'`；dist 与当前构建输出 sha256 相同（release `7cb78282…`）。
- 一条关于"哈希"的更正：只改注释重构建，release 包 sha256 也**会变**——APK 的 zip 条目带着时间戳。所以发版校验的正确说法是"dist 与本次构建输出一致"，而不是"同一份代码哈希跨构建稳定"。上一节里 import 排序后哈希未变，是因为那次 package 任务整个 UP-TO-DATE 没重跑。
- 结构体检：68 个 Kotlin 文件，包名首行 / import 连续与字典序 / 尾部无空行 **0 异常**。

**仍需装机确认**：淡变节奏（现在两个方向都是 `Motion.STANDARD` 时长）在你手上是否跟手；26dp 的归档色块和双横把手好不好按；以及只开收入编辑时，那个唯一的「+」按"新建收入"来默认对不对你的习惯。

## 33. v1.16.0 改动（归档与把手并排 · 莫奈主题系统）

### 并排，但行高不许动

上一条反馈说编辑态视觉割裂。根因是两态用了两套结构。这次按你说的把归档与双横改成**左右并排**，同时把尾部整块高度钉死成 55dp（正好等于原本"合计 26 + 间隔 3 + 明细 26"）：

| | 普通态 | 编辑态 |
|---|---|---|
| 尾部 | 上下两排：合计 / 明细箭头 | 左右并排：归档色块 · 双横把手 |
| 块高 | 55dp（写死） | 55dp（同一块） |

所以切换时只有淡入淡出，行高、左侧内容、卡片宽度全部不动。

踩到一个编译期才暴露的事实：`AnimatedVisibility` 只有 `ColumnScope` 和 `RowScope` 两个扩展，**`BoxScope` 没有**。我外层用 `Box` 包着，编译器就越过两层作用域去抓外层那行的 `RowScope` 版本，报"cannot be called with an implicit receiver"。换成固定高度的 `Column` 就好了。

### 主题系统：六套莫奈，四颗种子

设置页「偏好」里多了一排色板。六套主题：

| key | 名字 | 出处 |
|---|---|---|
| grey-blue | 灰蓝晨雾 | 鲁昂大教堂 · 清晨的石面（原默认，与启动图标同源） |
| waterlily | 睡莲水光 | 睡莲 · 池面的紫与绿 |
| sunrise | 日出赭橙 | 印象 · 日出 · 那轮橙红 |
| wheat | 麦田赭金 | 拾穗者 · 夏末的麦垛 |
| footbridge | 竹舟池绿 | 日本桥 · 柳荫下的水面 |
| rouen-dusk | 教堂黄昏 | 鲁昂大教堂 · 日落时的玫瑰灰 |

**每套只写四颗种子**（主色 hue / 中性倾向 mist / 强调 accent / 危险 danger），页面底、卡片、三档容器、描边、正文、次要文字、色块上的字**全部派生**。这么做不是为了省字，是因为我没有设备能看界面——六套手写颜色我调不动也验不了，一条派生规则改一次六个主题一起好。

新增 `Pigment`：纯 `Long`（0xAARRGGBB）算术，不引 Android/Compose 类型，所以 mix、相对亮度、对比度、颜色距离都能在普通 JVM 里算和断言。`MonetTheme.light()/dark()` 也只产 `Long`，最后一步在 `Theme.kt` 里翻成 `ColorScheme`。

派生里最关键的一处是 `pair(base, background)`：**色块和它上面的字要一起解**。先让色块在底上够分得开（≥3.0），再取黑/白里对比度更高的当字色，还不够就把色块往那一侧推。我最初写死"主色往白提 94% 当字色"，结果中间调主题（赭橙、赭金）刚好掉到 **4.38** 这种看着没事、实际读不清的值——是测试拦下来的。

### 判据本身也被写错过两次

1. **深色阶梯的顺序**：我按浅色的直觉把 `surface` 排在第一个去判单调，结果深色模式合理的关系是"页面底最暗、往上越来越亮"，判据假红。现在两套分别用各自的起算顺序。
2. **"撞色"用明度距离量不出来**：橙色和青色明度可以完全一样。改成 RGB 归一化的颜色距离 `Pigment.distance`，阈值 0.10。
3. 顺带一个真问题：最初的六颗种子确实太近了——灰蓝 / 睡莲 / 日出 / 教堂其实都是灰紫蓝，最窄一对距离只有 **0.045**，等于四个主题看着一样。重铺了色相，并且刻意都选"白字直接过 4.5"的深度，免得派生时被拉成同一个暗色。

还有一处分层修正：数据层原先想直接用 `MonetTheme.GREY_BLUE.key` 当默认值，那是 `data → ui` 的反向依赖；改成没设置过时存空串，由界面侧 `MonetTheme.fromKey` 兜底。

新增 `LocalDarkTheme`（compositionLocal）把"真正生效的明暗"暴露出来，色板预览才会在深色模式下显示深色那套而不是浅色那套。

### 测试又学到一条 shell 之外的东西

`pickingASwatchPersistsIt` 第一版红了 30 秒超时。原因不是功能坏，是**判据写法**：我把 `runBlocking { dataStore.first() }` 放进 `waitUntil` 的谓词里，而 DataStore 的写入正需要主线程推进——谓词阻塞主线程等于自己等自己。改成先等界面文字换掉（这本身已经是"点击 → 写 → Flow → 重绘"的完整回路），再在谓词外面直读一次，就绿了。

账单那 8 档分类色**不跟随主题**：它们是分类标识，跟着主题变就没法靠颜色认出"吃饭"了。启动图标也仍是灰蓝，那是包的固定身份。

### 版本与实测数字

`versionCode 20 / versionName 1.16.0`，schema 仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。主题选择存在 DataStore（`monet_theme` 键），**不需要迁移、也不影响备份格式**（备份里带的是 `THEME=` 黑白模式，主题色键没进备份——见下"待办"）。

- 单测：debug **104 → 115 条全绿**（+11：`MonetThemeTest` 9 条——8 条判据 + 1 条打印余量的 contrastReport；`SettingsScreenSmokeTest` +2 条——六块色板都在、点色板真的持久化），release 变体 102 条全绿。
- 12 组配色（6 主题 × 明暗）实测最差对比度：正文 **10.35–16.93**（阈值 7.0）、次要文字 **4.62–8.79**（4.5）、按钮文字 **5.70–10.61**（4.5）、色块在卡片上 **5.40–9.18**（3.0）。
- release 包 **2,015,169 B**（与 v1.15.1 同尺寸，哈希不同 `6d0de4c1…`），debug 20,519,426 B，mapping 包 3,569,332 B。
- apksigner `Verifies` + v2/v3 true；zipalign `Verification successful`；两个包 `badging` 均为 `versionCode='20'`；dist 与构建输出 sha256 一致（debug `45313229…`）。
- 结构体检：71 个 Kotlin 文件，包名首行 / import 连续 / import 字典序 / 尾部无空行 / 旧色值残留 **各 0 异常**。这轮自己引入的一处 import 乱序已修，修完重构建两个包哈希**逐字节不变**。

**留作下一步**：主题键目前不进备份文件（恢复后主题回到默认，其他都在）。要补就是备份格式加一行 `MONET=`，属小改，但不能偷偷改格式——得让老备份继续能读。

**仍需装机确认**：六套主题的**观感**我只能保证"读得清、分得开"，好不好看归你；色板格子只有 44dp 高，里面的三条预览带在窄屏上够不够显眼；以及并排后的归档/把手好不好按（各 26dp，间隔 8dp）。

## 34. v1.16.1 改动（色板重做：这次先画出来看，不再凭猜）

§33 那版色板被一句"太丑了"打回。回头看我错在哪：**我连它长什么样都不知道就发了包**。这台机器上没有设备可看，而我之前所有"视觉"判断都是从坐标和数值推的——坐标级的事（安全圆、字高、行高）推得准，观感推不准。

所以这次改了做法：把六套主题**真实派生出来的十六进制色**（用测试打印出来的，不是我记的）填进一个 HTML 小样，用浏览器渲成图，真的看一眼再决定。

### 三个候选，看了才敢选

| 方案 | 长相 | 判断 |
|---|---|---|
| A | 圆内沿对角分两色，主色 62% / 强调色 38% | **选中**。两个颜色都在，主色仍是主角，形状只有一种 |
| B | 主色实心圆 + 右下角一小块强调色 | 那个小点看着像**消息角标**，还会让圆显得没对准中心 |
| C | 主色实心圆 + 底部一条强调色带 | 像小旗子/半满的水杯，直边和圆打架 |

第一版还有两个具体毛病，图里一眼就看出来了：

1. **对半分（50/50）不对**——主色被吃掉一半，六颗看着一样重，认不出哪套是哪套；改成 62/38 后主色立住了。
2. **自然换行会排成 5+1**，第二行孤零零一颗。改成固定每行三颗（`FlowRow(maxItemsInEachRow = 3)` + `weight(1f)`），3×2 是稳的。

另外把"页面底 + 卡片 + 两条色带"那种缩微界面全部去掉：48dp 的格子里画缩略界面，页面底和卡片本来就几乎同色，结果就是六个空灰框。名字也从四字词缩成两字（晨雾/水光/赭橙/麦金/池绿/黄昏），`MonetTheme` 加了 `short` 字段，并用测试钉住六个短名不重——**重名就是界面 bug**，因为色板上只有这两个字可看。

### 顺带

- 选中环改成**常驻占位**，只在选中时画边框。之前是条件添加那个 44dp 的盒子，换主题时名字会上下跳一截——刚在 §32 被教育过"骨架不要变"，自己又犯了一遍。
- `FlowRowScope` 在这个版本（foundation 1.11.4）里直接继承 `RowScope`，用的是 `Modifier.weight(1f)`；`horizontalWeight` 还不存在，写上去是 unresolved reference。
- 设置页的冒烟测试也跟着换到高屏幕（`@Config(qualifiers = "w411dp-h3200dp")`）：色板让面板变高之后，靠手势滚到底的断言全超时了——这一页要判的是"节点在不在"，不该绑在滚动上。

### 版本与实测数字

`versionCode 21 / versionName 1.16.1`，schema 仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **115 条全绿**、release 变体 102 条全绿（条数与 §33 相同——短名/全名唯一性是并进 `keysAreUniqueAndUnknownKeyFallsBackToDefault` 的 3 条断言，没有新增测试方法）。
- release 包 **2,015,169 B**（与 v1.16.0 同尺寸，哈希 `1d5fd715…`），debug 20,519,422 B，mapping 包 3,585,162 B。
- apksigner `Verifies` + v2/v3 true；zipalign `Verification successful`；两个包 `badging` 均为 `versionCode='21'`；dist 与构建输出 sha256 一致；两个 APK 都比最新源码新。
- 结构体检：71 个 Kotlin 文件，包名首行 / import 连续与字典序 / 尾部无空行 **0 异常**，`MonetSwatch`、`horizontalWeight`、"目前记了" 等废弃引用 0 残留。

**这次仍然要装机看**：渲 HTML 小样只能确认"形状与配色关系对不对"，确认不了 Compose 实际渲染（抗锯齿、Dp 缩放、系统字体）和你那块屏上的观感。A/B/C 我是在网页里选的，最终效果还得你看真机。

## 35. v1.17.0 改动（配色回到莫奈 · 主题进备份 · 今天还没记）

### "还是太丑"的根因不在形状

§34 我改了色板的形状，但颜色本身还是错的：`#A94A1E`、`#3A6B28` 这种**高饱和实色**是企业配色，不是莫奈。莫奈的颜料全都夹在灰与雾之间，靠**并置的近似色**分层——画面里根本没有一块纯色。

所以这次把每套主题从"主色 + 补色"改成**五个色阶**，每级都取画面上真实存在的层次：

| 级 | 作用 | 例（晨雾） |
|---|---|---|
| `wash` 雾底 | 整页底色，就是那层空气 | `#EDF2F5` |
| `haze` 浅霭 | 卡片与凹陷面 | `#D8E3EA` |
| `tone` 中调 | 主色：按钮、选中态，夹灰的中间明度 | `#5E7488` |
| `light` 亮色 | 画里那一点温度 | `#A4886F` |
| `ink` 墨锚 | 正文与深色模式的骨架 | `#22323E` |

其余槽位仍从这五级派生。色板也从"半个圆两种颜色"换成**四栏漆卡**（wash→haze→tone→light 由淡到实并排），一眼能看出这套主题的雾有多重、亮点在哪。

**这次是先渲图看了才发的**：把测试打印出来的真实派生值喂给一个 HTML 小样，浏览器截图看。一看就发现两件事——① 六套整体成立；② 但**晨雾 / 水光 / 黄昏的页面底淡得几乎一样**，选主题时分不开。于是把三套冷调的 `wash`/`haze` 往各自色相推了一档（水光 `EDEFF0→E7EEF4`、日出 `F2EDEC→F1E9E4`、黄昏 `F1EBE9→F3E9E6`），再渲一次确认六套的页面温度各自立得住。上一版就是跳过了这一步才被打回。

### 判据又修了两处，都是自己写错的

1. **危险色在浅色里是文字**（首页"超支 剩/超多少"那行），我却让它走色块判据（只保 3.0），实测掉到 3.4。改成按文字判据保 4.5。
2. **"两个主题撞不撞色"不能把两栏雾色平均进去**——莫奈的画面底本来就该彼此相近，平均下来判据被稀释一半，`sunrise vs rouen-dusk` 报 0.035 其实是误判。改成只量色卡上那两栏**实色**（主色、亮色），下限 0.055。

12 组配色（6 主题 × 明暗）现在的实测余量：正文 **11.57–16.09**（阈值 7.0）、次要文字 **5.72–8.77**（4.5）、按钮文字 **4.84–10.08**（4.5）、色块在卡片上 **3.38–7.62**（3.0）。

### 两件人性化功能

**主题色进备份。** §33 起的遗留：恢复账本后主题会掉回默认。备份文件多一行 `MONET=<key>`，**解码时它是可选行**——v1.17 之前的备份没写过，必须照样能读、且不能顺手把用户的主题抹掉（解析成空串，恢复时空串就跳过不写）。两条测试分别钉住"老文件仍可读"和"新文件往返不丢"。

**首页"今天还没记"。** 一行小字，不加边框不加底色（是提醒不是按钮），点一下直接给**最近那笔所属的账单**记一笔；那账单归档了就退到第一个支出账单。出现条件三个都满足才算：正在看本月、本月确实记过、今天 0 笔。规则做成 `BuildStatsUseCase.shouldNudgeToday()` 纯函数单测，界面上也断言了"空白的第一个月不许催"。这里刻意用**笔数**而不是金额判断——有人今天记的是一笔 0 元转账，那也算记过。为此新增 `EntryDao.observeCountBetween()`（`Flow<Int>`，跟着库实时变）；中途我先写成按方向 GROUP BY 的查询，发现"记过没有"根本不分方向，换回单条计数更简单。

### 版本与实测数字

`versionCode 22 / versionName 1.17.0`，schema 仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。备份格式仍是 v1（只多一条可选行，老文件照读）。

- 单测：debug **115 → 119 条全绿**（+2 提醒规则、+2 主题进备份），release 变体 106 条全绿。
- release 包 **2,031,557 B**（v1.16.1 为 2,015,169，**+16,388 B**），debug 20,519,426 B，mapping 包 3,586,453 B。
- apksigner `Verifies` + v2/v3 true；zipalign `Verification successful`；两包 `badging` 均 `versionCode='22'`；dist 与构建输出 sha256 一致（release `b6a5cd3e…`、debug `5a1168c4…`）。
- 结构体检：71 个 Kotlin 文件，包名首行 / import 连续与字典序 / 尾部无空行 **0 异常**，废弃符号（`accentSeed`、`MonetSwatch`、`KindCountRow` 等）0 残留；两个 APK 都比最新源码新。

**这次看到的东西和上次不一样**：上一版我连渲染都没看就发了包。这次看过图，所以能负责地说"六套主题在页面上分得开、雾感在"；但 HTML 小样替代不了 Compose 的真实渲染（抗锯齿、Dp 缩放、系统字体、你那块屏的色准），**最终观感仍要你定**。另外两件事想听你的：一是六套里"晨雾"就是原来的默认色，要不要把它换成更有辨识度的；二是"今天还没记"这行会不会显得唠叨——它现在每天最多出现一次点击的距离，但要不要留着是你的判断。

## 36. v1.17.1 改动（顶栏不再随 tab 跳 · 偏好面板照小样排序）

### 顶栏为什么会跳

首页那一行是 `[搜索按钮 48dp] [月份胶囊] [48dp 占位]`，页面左右内边距 16dp、上 8dp；分析页只有一整行 `MonthHeader`，页面左右内边距 20dp、**上 0**。所以从首页切到分析，月份胶囊既横移（左右不对称的 48dp 槽没了）又上跳 8dp——就是"很难受"的那个感觉。

### 做法：一条共用的 `ScreenTopBar`

新增 `ui/common/ScreenTopBar`：固定高 56dp，左右各一个 **48dp 的常驻槽**（空着也占位），中间是月份切换器。首页把搜索按钮放进 `leading`，分析页两个槽都空着——**几何完全一致**，胶囊在两个页面落在同一个像素上。

同时把三页的页面内边距统一成左右 20dp（`Spacing.xl`）、上 8dp：首页、分析页、设置页现在卡片左边缘对齐，切 tab 不会整体左右挪。

这条几何用测试钉住了，防的是以后有人再各自摆一行：`assertTopBarAnchored()` 断言顶栏**中心 x = 窗口中心**、**宽度 = 窗口宽 − 2×20dp**，首页和分析页各跑一次。谁再把顶栏拆成页面私有布局，这两条立刻红。

### 偏好面板照小样排序

面板内改成 **主题色 → 黑白模式 → 月度预算**（小样就是这个顺序，也合理：主题色决定整页气质，黑白模式只是个开关）。

### 版本与实测数字

`versionCode 23 / versionName 1.17.1`，schema 仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **119 条全绿**（含两条新的顶栏几何断言）、release 变体 106 条全绿。
- release 包 **2,031,553 B**（v1.17.0 为 2,031,557，−4 B），debug 20,603,890 B，mapping 包 3,593,732 B。
- apksigner `Verifies` + v2/v3 true；zipalign `Verification successful`；两包 `badging` 均 `versionCode='23'`；dist 与构建输出 sha256 一致（release `b51a2c65…`）。
- 结构体检：71 个 Kotlin 文件，包名首行 / import 连续 / 字典序 / **无重复** / 尾部无空行 **0 异常**。这里额外补了"重复 import"和"未使用 import"两项检查——本轮就抓到 `SettingsScreen` 里两条重复（`clickable`、`PanelDivider`），是我前面用脚本重排导入时把去重和排序混出来留下的。

**仍需装机确认**：切 tab 时你的眼睛是否还觉得跳（我这边只能保证几何一致，动画是 Material 默认的淡入 + 轻微位移，如果残留感来自那个位移，可以把切页位移去掉）；以及主题色排在黑白模式上面是否顺手。

## 37. v1.18.0 改动（切页只淡入 · 默认主题有了来由 · 提示当天只提一次）

上一轮末尾留了两个问题给你，你说"都做"，于是这轮是三件小事实测收口，外加我自己复查出来的一个配色隐患。

### 1. 切 tab 去掉水平位移，只留淡入

Material 默认的页面转场是 **淡入 + 约 1/8 屏宽的水平位移**。§36 把顶栏几何对齐之后，位移反倒成了剩下唯一的"动"——顶栏不动、内容整块横着滑进来，看着像没对齐。

`MoneyBlocksRoot` 的 `NavHost` 转场改成四个纯淡变：进入 `fadeIn(tween(Motion.STANDARD, easing = Motion.Decelerate))`、退出 `fadeOut(tween(Motion.QUICK))`，pop 同理。位移是"你去了哪儿"的线索，但你要的正是"别动"，两相权衡留下不动的那个。两条 `slideIn/OutHorizontally` 导入随之删掉。

### 2. 默认主题「晨雾」重染：选它终于有理由

原来的「晨雾」是六套里最没脾气的一版——中调偏灰、亮色跟别的主题撞，当默认只是因为排在第一个。现在把中调锚在**启动图标屋顶那个蓝灰** `#4E7189` 上：

| 槽 | 值 | 是什么 |
| --- | --- | --- |
| wash 雾底 | `#E6EEF4` | 清晨那层空气 |
| haze 浅霭 | `#CFE0EB` | 水面上的薄霭 |
| tone 中调 | `#4E7189` | 与启动图标同色 |
| light 亮色 | `#D09A6A` | 琥珀，和蓝灰是画里那种互补的温度对照 |
| ink 墨锚 | `#1F303C` | 正文 |

说明文字改成"清晨的水面 · 与启动图标同色"——默认色和图标同源，这个事实以前在屏幕上看不见，现在能看见了。

### 3. 顺手修了色板里最糊的一对：日出 vs 黄昏

把六套的真实派生色填进 HTML 小样用浏览器渲出来看，发现「日出」和「黄昏」肉眼分不出来。回去量：色卡两栏实色的并置距离只有 **0.0577**，判据是 0.055——**刚好压线过**，测试绿着但界面是糊的。这就是"测试通过"和"能看出来"不是一回事的现场例子。

根因是「黄昏」的亮色我也给成了赭黄 `#C49A66`，跟「日出」的橙只差明度。而那幅《鲁昂大教堂》立面落日打上去是**玫瑰灰**（说明文字一直写着"日落时的玫瑰灰"，颜色却没照着来），改成 `#C5849A`。派生后实测：

- 日出 ↔ 黄昏：**0.0577 → 0.1045**（接近翻倍）
- 六套里最近的一对变成 水光 ↔ 池绿 **0.0855**，判据仍是 0.055，不再有需要贴着线过的组合

判据只量色卡后两栏（中调、亮色）是 §35 定的规矩：两栏雾底相近是应该的，莫奈画面的底本来就都是灰白雾，把它们算进平均值会把判据稀释一半。

### 4. 「今天还没记」当天点过就不再提

上一版的提示条只看"今天有没有笔数"，等于**只要今天没记账，每次回首页都要被提醒一遍**。你说怕唠叨，那就按"提醒一次"来做：

- `SettingsRepository` 加 `nudgeSeenDay`（DataStore key `nudge_seen_day`）。存的是**哪一天的 epochDay**，不是布尔值——跨了天它自然失效，不需要谁去重置，也不用写"每天凌晨清零"这种代码。
- `shouldNudgeToday()` 多一条 `nudgeSeenDay != todayDay`；点提示条时先 `markNudgeSeen()` 再弹记账面板。
- 测试 `nudgeStaysQuietForTheRestOfThatDay` 钉住这条。

一个有意的取舍：**点开又取消，当天也不再提**。这行的目的是提醒一次，不是催到记为止；真要催就成噪音了。

### 版本与实测数字

`versionCode 24 / versionName 1.18.0`，schema 仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **120 条全绿**（比上轮 +1，就是那条"当天不再提"）、release 变体 **107 条全绿**。
- 配色断言：六套 × 浅深两版的文字/次要字/色块上字/色块边界对比度全过，色卡层阶梯单调，最差的一对 0.0855 > 0.055。
- release 包 **2,031,557 B**（v1.17.1 为 2,031,553，+4 B，R8 对齐噪声级别），debug 20,624,963 B，mapping 包 3,008,079 B（`mapping.txt` 42,175,025 B）。
- apksigner `Verification successful`；zipalign `Verification successful`；`badging` 两包均 `versionCode='24'`；dist 与构建输出 md5 一致（release `08a49f98…`）。
- 结构体检：71 个 Kotlin 文件、**重复 import 0**、**未使用 import 从 5 条清到 0**（`HomeScreen` 的 `Balance`/`MoneyDisplay`、`SettingsScreen` 的 `background`、`BackupRows` 的 `dp`、`SettingsScreenSmokeTest` 的 `onAllNodesWithContentDescription`）。`getValue`/`setValue` 那批**不算未使用**——它们是 `by remember` 委托语法要的运算符扩展属性，源码里没有字面引用，删了直接编译不过。

**仍需装机确认**：① 位移去掉之后切 tab 眼睛还觉不觉得跳；② 六套配色在真机色域/亮度下的观感，尤其「黄昏」新换的玫灰；③ "当天点过就不再提"这个克制程度是不是你要的——如果你希望它连日提醒、或者反过来彻底关掉，都可以再调。

## 38. v1.19.0 改动（周期性账单 · 库结构 v3 · 备份格式 v2）

### 为什么是这个功能

账本里最烦的不是"记一笔"，而是**每月都要记同一笔**：房租、水电、工资。这类记录金额固定、日期可预测，
却和"今天午饭 32 块"走完全一样的手工流程。所以这轮把规则挂到账单上，到期自动落一笔。

### 规则模型

`Bill` 上加四个字段，全部落到 `bills` 表的独立列（都带 DEFAULT，老行升上来就是"不重复"）：

| 字段 | 含义 | 为什么单独存 |
| --- | --- | --- |
| `repeatKey` | `none` / `monthly` / `weekly` | 存 key 不存序号，手工修库看得懂 |
| `repeatAmountCents` | 每期金额（分） | 0 视为规则没设完，不生成 |
| `repeatAnchorDay` | 首次入账那天（epochDay） | 由用户挑，"从哪个月开始算"是显式的 |
| `repeatCursorDay` | 已经生成到哪一期 | 这一列决定"删掉的会不会回来" |

`Entry` 加一列可空 `autoDay`（属于哪一期），并给它建唯一索引 `(billId, autoDay)`。
手工条目 `autoDay` 是 NULL，而 **SQLite 的唯一索引不约束 NULL**，所以"同一天手工记两笔"照常。

排期函数不放 UI 层，放在 `domain/usecase/Recurring.kt` 的 `RepeatSchedule` 里，纯 `LocalDate` 运算、
不引 Android 类型，因此整套日期规则跑在普通单测里。**月度始终从首次那天加 n 个月**，
不是"上一期加一个月"——`plusMonths` 会把 1 月 31 日夹到 2 月 28 日，若从夹完的结果再往后加，
3 月就永远停在 28 日。用户设的是"每月 31 号"，就该是 1/31、2/28、3/31。

### 三条边界都是产品决定，不是实现细节

1. **不回填首次日之前**：9 月 10 日设的规则，不会冒出 7、8 月两笔房租。
2. **不重复已走过的期次**：游标之前的期次不再生成，所以**删掉一笔自动条目就是真想删掉它**，
   下次启动不会补回来。要恢复就手工补记。
3. **唯一索引兜底**：即使上面两条被绕过（比如把首次日期往回调），同一期也只能写进一条，
   `insertAuto` 撞索引返回 -1，不抛异常、不写重复。测试 `movingAnchorReplaysFromTheNewDate`
   就是专门把日期往回调来验这条。

生成时机是**进程启动时补一次**（`MoneyBlocksApp.onCreate` 里 `applicationScope.launch`），不养后台任务：
这类 App 被系统杀掉是常态，起一次补一次就够，省下一条常驻通知和一套 WorkManager 依赖。
失败只记一条 `Log.w`，不打扰用户——下一次启动自然会补。

### 迁移：必须让 Room 自己去跑，不能手工调

`SCHEMA_VERSION` 2 → 3，`MIGRATION_2_3` 加四列 + 一个可空列 + 唯一索引。索引名必须和 Room 的
生成规则逐字一致（`index_entries_billId_autoDay`），否则升级完校验直接抛。

新写的 `SchemaMigrationTest` 第一版**假失败**了一次，原因值得记下来：Room 导出的 `2.json` 里
`setupQueries` 不止有 `PRAGMA foreign_keys`，还包含建 `room_master_table` 并写入**该版本的 identity hash**。
我在测试里手工调 `MIGRATION_2_3.migrate(db)`，SQL 全对、列也都加上了，但 `room_master_table` 里那行
还是 v2 的哈希，Room 打开时报的是"Expected identity hash 49cd…、found 602d…"——看着像迁移没生效。
真机升级走的是 `RoomOpenHelper.onUpgrade`，Room 会顺手更新那行哈希。所以测试改成
**建 v2 库 → 交给 `Room.databaseBuilder().addMigrations()` 打开**，让 Room 自己跑升级，
这才是真机发生的事。第二个坑是库名只能按 name 传，绝对路径会让"迁移的文件"和"校验的文件"不是同一个。

### 备份格式 1 → 2，读旧写新

账单行从 8 个字段变 12 个（多四列规则），条目行从 6 个变 7 个（多 `autoDay`）。
**没有直接换成 v2 就拒收老文件**：`FORMAT` 头改成解析版本号，`MIN_FORMAT_VERSION = 1` 到当前都认，
v1 的行按 8/6 个字段解析、缺的项用默认值补。反过来 v2 文件遇到比它新的版本仍然整份拒收。
格式版本只看第一行——写出去的文件 FORMAT 永远在第一行，不在第一行就不是我们生成的文件。

`autoDay` 和 `repeatCursorDay` **必须进备份**：丢了期次，恢复之后那一期在库里变成手工条目，
唯一索引拦不住，下次生成会凭空多一笔房租。测试 `repeatRuleAndAutoPeriodSurviveRoundTrip` 盯着这条。
周期键也照 `kindKey` 的规矩做严格校验——认不出的键不能悄悄当成"不重复"，那等于把用户设好的规则弄丢。

### 界面

* 账单编辑弹窗加"自动记账"分组：`不重复 / 每月 / 每周` 分段控件 + 每期金额输入 + 首次入账日（日期选择器）。
  金额框只收数字和一位小数点，**不收表达式**——每期金额必须是定值。
* 没填金额时保存按钮是灰的，并给一行提示"填上每期金额才会自动入账"；填了之后提示变成
  "每月自动入账，下次 10月10日"，说清规则到底会长成什么样。
* 首页账单行的名字后面加一枚 13dp 的矢量 `Repeat` 标记（账单同色），具体规则放进 `contentDescription`。
  不额外占行、不改行高。
* 明细里自动生成的那笔在副标题末尾加"· 自动"两个字。
* ~~弹窗内容加了三项之后比屏高，但不能自己套 `verticalScroll`：Material3 的 `AlertDialog`
  已经会给 `text` 槽加滚动，两层嵌套会量测死循环。~~
  **这条是错的，见 §39**：1.4.0 的 `AlertDialog` 并不滚 `text` 槽（`javap` 查过签名，
  没有 `textScrollFlags` 那个参数），所以最底下的颜色行被整行裁掉了。

### 顺带修的两处（你没提，但改到这儿了）

1. `ManageBillsUseCase` 原来是 `create(4 个参数)` + `save(5 个参数)` 两份，加周期之后要涨到 7 个。
   合成一个 `save(bill: Bill)`，弹窗交回的是整份改好的 Bill。三处调用点跟着简化。
2. **游标只认库里那一版**。首页改名走的是 `BillSummary` 里的 Bill，那条路径根本没有游标，
   照抄调用方传回来的值会把 0 写进库，等于把已生成过的期次全部重新打开。
   现在 `save()` 先读库：日期没变就沿用库里的游标，只有改了首次入账日才把游标退回新起点之前。

### 测试环境上踩到的两个坑

* 测试数涨到 150 条之后，Robolectric 的测试 JVM **默认堆不够**了，表现很迷惑人：
  一半报 `OutOfMemoryError`，一半报 `Compose did not get idle after 74633 attempts`
  （内存不够 → 量测跑不完 → 看起来像死循环）。加了 `maxHeapSize = "3g"` 之后全绿。
* 弹窗测试当时躲开了自定义屏幕 qualifier（`h891dp` / `h3200dp` 都试过，一律"不空闲"）。
  **机制当时没查清，§39 查清了**：不是 qualifier 的锅，是 `usePlatformDefaultWidth = true`
  让弹窗宽度由内容反推，里面再放纵向滚动就是"宽度等内容、内容等宽度"的循环。
  顺带一个发现：`SemanticsNodeInteraction.assertExists` 在这个版本**不能显式 import**（成员函数），
  而 `SemanticsNodeInteractionCollection.fetchSemanticsNodes()` 才有无参重载。

### 版本与实测数字

`versionCode 25 / versionName 1.19.0`，**库结构 2 → 3**（显式迁移，不破坏性回退），
备份格式 1 → 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **152 条全绿**、release 变体 **136 条全绿**（三个组合冒烟类只在 debug 跑）。
  debug 从上一轮的 120 涨到 152，新增 32 条：`RecurringScheduleTest` 14、`RecurringDbTest` 10、
  `SchemaMigrationTest` 1、`BillEditorDialogSmokeTest` 3，另在 `BackupFormatTest` 里加 4 条格式用例。
- release 包 **2,031,557 B**——和 v1.18.0 一字节不差。R8 把新增的排期/规则代码压掉了，
  同时资源里多了图标和字符串，两边正好抵平。这个数不是凑出来的，是 `ls` 出来的。
- debug 20,552,194 B，mapping 包 3,026,289 B。
- apksigner `Verification successful`；zipalign `Verification successful`；
  `badging` 两包均 `versionCode='25'`；dist 与构建输出 md5 一致（release `e0cad029…`）。
- 结构体检：76 个 Kotlin 文件、**重复 import 0**、**未使用 import 从 18 条清到 0**。
  `getValue`/`setValue` 那 22 条**不算未使用**——它们是 `by remember` 委托要的运算符扩展，
  源码里没有字面引用，删了直接编译不过（清理脚本专门为此加了 `\bby\s` 的例外判断）。

**仍需装机确认**：① 老库升 v3 在你手机上顺不顺（这是本轮风险最高的一处，测试只能证明
Room 自己满意，不能证明各家 ROM 的 SQLite 行为一致）；② 设一条"每月 31 号"的规则，
看 2 月是不是落在 28 号、3 月又回到 31 号；③ 删掉某个月的自动条目，重启 App 确认它不回来；
④ 弹窗里"自动记账"这一组的位置和提示措辞是否顺手；⑤ 之前那份 v1 备份文件还能不能正常恢复。

## 39. v1.19.1 改动（新建账单弹窗：颜色行被裁掉）

你报的是"新建账单界面最下方选择颜色被挤压没了"。这条是我的错，而且错在一个**我没验证就写进注释里的事实**上。

### 根因

上一轮我给弹窗加了"自动记账"三项，内容因此比弹窗高。我当时在代码里写了这么一句注释就收工了：

> Material3 的 AlertDialog 已经会给 text 槽加滚动（textScrollFlags），这里再套一层就是死循环。

**这句话是错的。** 我把 material3 1.4.0 的 AAR 拆出来 `javap` 看过：`AlertDialogKt` 里
`AlertDialog` / `BasicAlertDialog` / `AlertDialogContent` 的签名**都没有 textScrollFlags 这个参数**
（那是更新版本才加的）。所以 `AlertDialog` 不滚 `text` 槽，内容超出高度时最底下那行颜色被整行裁掉——
正是你看到的现象。

顺带纠正另一条：§38 里我写"弹窗 + 自定义屏幕 qualifier 永远不空闲，机制没查清"。机制现在查清了——
不是 qualifier 的锅，是 **`usePlatformDefaultWidth = true`（默认值）会让弹窗宽度由内容反推**，
里面再放纵向滚动容器就成了"宽度等内容、内容等宽度"的循环。所以我当时"去掉自己的 verticalScroll"
只是绕开了症状，把裁切留了下来。

### 修法

壳子自己搭，不再用现成的 `AlertDialog`：

* `BasicAlertDialog(properties = DialogProperties(usePlatformDefaultWidth = false))`——宽度由窗口给，
  滚动容器才成立；
* `Surface(shape = Panels.dialogShape, tonalElevation = 6.dp)` + `fillMaxWidth()` +
  `padding(horizontal = lg, vertical = xl)` + `imePadding()`，键盘弹起来不会盖住金额框；
* **标题和按钮行留在滚动区外面**，中间那一大段（名称/方向/自动记账/图标/颜色）单独
  `weight(1f, fill = false) + verticalScroll`。滚到底再留 12dp 余量，最后一行色块不被按钮压住。

顺手把同类问题一起查了：全工程另外 4 个 `AlertDialog`（月份跳选、账单选择、备份确认、预算输入）
内容都很短，不存在同一个裁切，所以没动。

### 回归测试

新增一条 `colorRowIsReachable`：**先 `performScrollTo()` 再 `assertIsDisplayed()`**。
关键是后半句——节点"存在"不代表"没被裁掉"，Compose 对被裁剪的语义节点照样报存在，
上一轮就是这么漏的：那三个弹窗测试全绿，颜色行照样没了。

同时把弹窗测试类的屏幕改回真实尺寸 `w411dp-h891dp`（上一轮因为误判成"qualifier 有问题"而躲开），
这样"内容比弹窗高"会真的发生，这条断言才有意义。改完之后 4 条弹窗用例全绿，
也反过来证实了那个"不空闲"确实是宽度死锁而不是 qualifier。

### 版本与实测数字

`versionCode 26 / versionName 1.19.1`，schema 仍为 3、备份格式仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **153 条全绿**（+1 条颜色行可达性）、release 变体 **136 条全绿**。
- release 包 **2,031,553 B**（v1.19.0 为 2,031,557，−4 B），debug 20,552,190 B，mapping 包 3,028,015 B。
- apksigner 与 zipalign 均 `Verification successful`；`badging` 两包 `versionCode='26'`；
  dist 与构建输出 md5 一致（release `8ae0957d…`）。
- 结构体检：76 个 Kotlin 文件、重复 import 0、未使用 import 0。

**装机请确认**：① 新建账单时"颜色"那一行滚到底能看见、能点；② 键盘弹起时金额框没被盖住；
③ 深色模式下弹窗底色和分层看着对不对（这次是我自己给的 `Surface` + `tonalElevation = 6dp`，
不再是 `AlertDialog` 的默认容器色）。

## 40. v1.19.2 改动（记账计算器的颜色跟软件主题，不跟账单）

你报的是：记账时计算器的颜色跟着**该账单**的配色走，要它跟着**软件主题色**走。

### 原因

`RecordSheet` 开头一行就是全部问题：

```kotlin
val accent = target.palette.toComposeColor()   // 账单配色
```

这个 `accent` 一路传进表达式预览、「换账单」链接、日期行、数字键盘的运算符键，还有底部那枚
「记一笔」大按钮。于是同一个计算器在「吃饭」是绿的、在「打车」是蓝的，切账单时半屏控件一起换色——
看着像主题坏了。

改成：

```kotlin
val accent = MaterialTheme.colorScheme.primary  // 软件主题（莫奈色）
```

### 顺手修的一处对比度

「记一笔」按钮原来写死 `contentColor = colorScheme.surface`。容器色换成主题主色之后这就不对了：
`surface` 是最浅那层近白，压在中间调的主色上只有 3 出头。莫奈派生时 `primary` 和 `onPrimary`
是**配对着解**的（`pair()` 保证 `onPrimary` 对 `primary` ≥ 4.5），所以字色改用 `onPrimary`。

### 同类问题扫了一遍

账单配色不是不能用，是不能当**操作区的皮肤**。全工程 `toComposeColor()` 的调用点逐个看过，
剩下的都属于"这个颜色就是这条账单的身份"或"图里的数据编码"，保持原样：

| 位置 | 用途 | 判定 |
| --- | --- | --- |
| `Bits.kt` 账单图标徽章 | 账单身份 | 保留账单色 |
| `HomeScreen` 账单行的进度条 | 该账单占本月比重 | 保留 |
| `AnalyticsScreen` 占比扇区 | 数据编码 | 保留 |
| `BillEditorDialog` 颜色选择器 | 就是在选账单色 | 保留 |
| `RecordSheet` 键盘/按钮/预览 | 操作区皮肤 | **改主题色** |

记账弹层里唯一保留账单色的，是左上角那枚图标徽章——它负责告诉你"现在记进哪个账单"。

### 为什么没加测试

这条改动是"取色来源"换了一行，语义树里读不到颜色，`assertIsDisplayed` 之类也判不了色值，
写个"断言源码里不出现 `target.palette.toComposeColor()`"的测试属于给自己埋雷。
所以这处**靠装机眼睛验收**，我不假装它被测试钉住了。

### 版本与实测数字

`versionCode 27 / versionName 1.19.2`，schema 仍为 3、备份格式仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **153 条全绿**、release 变体 **136 条全绿**（没有新增用例，也没有删）。
- release 包 **2,031,553 B**（与 v1.19.1 同大小），debug 20,552,194 B，mapping 包 3,027,980 B。
- apksigner 与 zipalign 均 `Verification successful`；`badging` 两包 `versionCode='27'`；
  dist 与构建输出 md5 一致（release `757d2f9a…`）。
- 结构体检：76 个 Kotlin 文件、重复 import 0、未使用 import 0（本轮清掉 `RecordSheet` 里
  不再用到的 `toComposeColor` 一条）。

**装机确认**：① 在两个不同配色的账单之间切换记账，键盘和「记一笔」按钮颜色应该**不再变**；
② 换设置页的主题色，计算器要跟着变；③ 「记一笔」按钮上的字在新底色下读不读得清。

## 41. v1.19.3 改动（记账弹层不再顶到全屏）

你报的是：购物的「记一笔」界面全屏显示。

### 我判断的机制（没能真机复现，说明在这儿）

`RecordSheet` 是一个 `ModalBottomSheet`，里面那根 Column 带着 `.imePadding()` 和 `.verticalScroll()`。
键盘弹起时 `imePadding` 给内容**加**一截底部 padding，而 `verticalScroll` 会把"内容总高"照单全收
上报给弹层，于是弹层被撑到窗口上限——整屏都是它，键盘一挡，连数字键都要滚着才看得到。

为什么只有购物中招：这层里有一行**常用备注**胶囊，只有"这个账单以前记过带备注的账"时才出现
（`viewModel.noteSuggestions` 非空且备注还空着）。购物有这个历史，别的账单没有，它就比别的账单高出那一截；
再叠上键盘那截 padding，就翻过全屏这条线了。

**这条我只能算推断**：手上没有设备，Robolectric 里量不到真实 IME 高度。所以我按"最坏也不会更糟"来修。

### 修法

给弹层内容加一个高度上限：**窗口高度的 88%**，顶上永远留一条暗区。

* 新helper `Panels.kt` 里的 `rememberSheetMaxHeight(fraction = 0.88f)`，用 `LocalWindowInfo` 拿窗口高度、
  `LocalDensity` 换算成 dp。**拿不到高度时返回 `Dp.Infinity`**——弹层在独立窗口里，个别实现下
  `containerSize` 可能为 0，那种时候宁可不封顶，也不能让 `heightIn(max = 0)` 把整个弹层压没。
* `RecordSheet` 的内容 Column 加 `.heightIn(max = sheetMaxHeight)`。
  **顺序很关键**：`heightIn` 必须写在 `navigationBarsPadding()` / `imePadding()` **之前**。
  它约束的是"它后面所有节点合起来的高度"，写在 imePadding 后面就等于把键盘那截 padding 加在上限之外，
  白封一层——这正是这次的病根。

顺手把同类弹层查了一遍：`DaySheet`（看某一天）本来就有 `.heightIn(max = 420.dp)` 兜着，
不会顶到全屏，没动。

> **这一节的做法已被 §42 取代**：只封顶挡不住「内容变高后重新动画到新的展开位」，最终版是固定高度。留着这一节是为了把推断过程留在记录里。

### 为什么没加测试

这条断言的是"弹层高度 < 屏幕高度"，而 Robolectric 里 `ModalBottomSheet` 的窗口高度和真机不是一回事，
量出来的数没有意义；真要钉住得靠真机截图。所以这处仍然**以你装机眼睛为准**，我不假装它被测试覆盖了。

### 版本与实测数字

`versionCode 28 / versionName 1.19.3`，schema 仍为 3、备份格式仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **153 条全绿**、release 变体 **136 条全绿**（用例数没动）。
- release 包 **2,031,557 B**（v1.19.2 为 2,031,553，+4 B），debug 20,552,194 B，mapping 包 3,028,691 B。
- apksigner 与 zipalign 均 `Verification successful`；`badging` 两包 `versionCode='28'`；
  dist 与构建输出 md5 一致（release `109245bb…`）。
- 结构体检：76 个 Kotlin 文件、重复 import 0、未使用 import 0。

### 如果装机后还是觉得高

封顶只是把"最坏情况"截住，弹层本身内容就多（标题 / 表达式 / 记入 / 日期 / 备注 / 常用 / 键盘五行 / 提交，
粗算 670–700dp）。真要变矮就得减内容，三个候选按侵入性从小到大：

1. **常用备注那行不再单独占一行**，并进备注框下方当辅助文本；省 ~40dp。
2. **「记入」和「日期」合成一行**（左边账单、右边日期，都是单行元信息）；省 ~50dp。
3. **键盘从五行改四行**（把 `( )` 并进删除键那一格长按）；省 ~56dp，但改动手感，我不建议先动这个。

你说哪个，我就照哪个改。

## 42. v1.19.4 改动（记账弹层：高度钉死，不再往上爬）

> **这一节已被 §43 撤回**：固定高度不看内容多少，把每个账单都撑成快全屏。

你补的那句"最上面会**缓慢推进**到全屏"把病根定死了。§41 我只封顶、没钉高度，方向对了一半。

### 为什么"缓慢推进"

`ModalBottomSheet` 的展开位置是**按内容高度算**的。弹层先按当下内容开到一个高度，
随后异步数据到位——账单列表、常用备注那两批都是 Flow 后到的——内容变高，
它又重新算一次展开位并**动画过去**。视觉上就是"从半屏慢慢往上爬到全屏"。

所以只给 `heightIn(max = …)` 不够：上限只挡住"最高到哪"，挡不住"从当前高度动画到新高"。
要的是**高度从第一帧起就不变**。

### 改法

`rememberSheetMaxHeight`（上限）换成 `rememberSheetHeight`（固定高度），
`RecordSheet` 的内容 Column 由 `.heightIn(max = …)` 改成 `.height(…)`。三条边界：

* **固定高度必须写在 `navigationBarsPadding()` / `imePadding()` 之前**，那两截 padding
  才是"从固定高度里扣"。写在后面就等于加在高度之外，键盘一弹起弹层又变高——
  这正好也是 §41 那个"键盘挡屏"的病根，一次修掉。
* **拿不到窗口高度时退回 620dp**，不是 0：`height(0.dp)` 会把整个弹层压没。
* **再钉一个 780dp 绝对上限**：固定高度是"真的占那么多"，不像上限可以被内容顶回来，
  所以大屏（以及测试用的 3200dp 合成屏幕）上 88% 会得到一个荒谬的值。

`DaySheet` 仍然不动：它里面那截列表本来就有 `heightIn(max = 420.dp)`，高度不随内容无限长。

### 一个已知代价

高度钉死后，内容比这截矮的账单会在「记一笔」按钮下面留一点空白（粗算几十 dp，
购物那种带常用备注的基本正好填满）。这是"稳定"换来的：不留白就要允许内容决定高度，
而允许内容决定高度就必然有爬升。我选稳定。如果你看着那片空白难受，
下一步就把「记入」和「日期」合成一行，把内容高度做到和这截一致。

### 为什么还是没加测试

`ModalBottomSheet` 在 Robolectric 里的窗口高度和真机不是一回事，量出来的数没有意义；
而"会不会爬"本质是动画时序，语义树断言抓不住。这处仍以你装机为准。

### 版本与实测数字

`versionCode 29 / versionName 1.19.4`，schema 仍为 3、备份格式仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **153 条全绿**、release 变体 **136 条全绿**（用例数没动）。
- release 包 **2,031,557 B**（与 v1.19.3 同大小），debug 20,552,194 B，mapping 包 3,028,920 B。
- apksigner 与 zipalign 均 `Verification successful`；`badging` 两包 `versionCode='29'`；
  dist 与构建输出 md5 一致（release `c9c13957…`）。
- 结构体检：76 个 Kotlin 文件、重复 import 0、未使用 import 0。

**装机确认**：① 打开购物的「记一笔」，弹层应当**一次到位**、不再往上爬；② 点备注框调出键盘，
弹层高度不变、只有内容区缩；③ 其他账单的记一笔弹层高度与购物一致（同一个固定值）。

## 43. v1.19.5 改动（撤回固定高度：记账弹层恢复贴内容）

你回了一句"改完之后每个账单都变成那样了"——**§42 那个固定高度是错的，已撤回**。

### 为什么错

固定高度不看内容多少，一律撑到窗口的 88%。内容本来只占半屏的账单，也被顶成"快全屏"，
于是问题从"购物一个账单"变成"每个账单都是那样"。这是我连着第二次只凭推断改这块
（第一次是 §41 的封顶，第二次是 §42 的钉高），先记在这儿。

### 现在回到哪

`rememberSheetHeight`（固定）换回 `rememberSheetMaxHeight`（上限），
`RecordSheet` 内容 Column 由 `.height(…)` 换回 `.heightIn(max = …)`：
平时**贴着内容长**，只有内容真的超过屏幕时才被上限截住并内部滚动。保留 §42 里两条确实有用的东西：

* 上限必须写在 `navigationBarsPadding()` / `imePadding()` **之前**，否则键盘那截 padding 加在上限之外；
* 拿不到窗口高度时返回 `Dp.Infinity`（不封顶），而不是 0（会把弹层压没）。
  另外加了一道 780dp 绝对上限，防大屏/测试合成屏幕上 88% 得到荒谬值。

`DaySheet` 仍未改动。

### 还没解决的那半：为什么会"爬"

"顶部慢慢往上推进"这个现象我还没有可靠解释，两个候选：

1. **内容后到撑高**：账单列表和常用备注都是 Flow 后到的，弹层先按当下内容开，
   数据到位后重新算展开位并动画过去。要治它得给那两行预留高度，代价是没备注历史的账单会多一块空洞。
2. **就是正常的打开动画**：`ModalBottomSheet` 本来就从屏幕外滑到位；如果内容本身就有 ~700dp，
   动画终点自然接近全屏。要治它得减内容，不是改动画。

这两个的修法相反，所以我不再猜第三次——需要一次真机观察来分（见下面的问题）。

### 版本与实测数字

`versionCode 30 / versionName 1.19.5`，schema 仍为 3、备份格式仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **153 条全绿**、release 变体 **136 条全绿**（用例数没动）。
- release 包 **2,031,557 B**（与 v1.19.4 同大小），debug 20,552,194 B，mapping 包 3,028,912 B。
- apksigner 与 zipalign 均 `Verification successful`；`badging` 两包 `versionCode='30'`；
  dist 与构建输出 md5 一致（release `53e8d47d…`）。
- 结构体检：76 个 Kotlin 文件、重复 import 0、未使用 import 0。

## 44. v1.19.6 改动（记账弹层：把后到的两行位置先占住）

> **这一节的封顶+内部滚动方案已被 §45 撤回**：它会造成纵向手势冲突（抽搐）。留下的是「永远占位、条件只决定行内内容」这条，它仍然成立。

你的观察是"**先开到正常高度，然后慢慢爬满**"——这就排除了"内容本来就高"和"只是入场动画"两种可能，
指向同一个原因：**有东西在首帧之后凭空长出来**。

### 查到的两处

`KeypadViewModel` 里两个状态都带初始值 `emptyList()`：

```kotlin
val bills = container.billRepository.observeBills()... stateIn(..., emptyList())
val noteSuggestions = ... stateIn(..., emptyList())
```

而 `RecordSheet` 里有两整块 UI 是按它们**条件渲染**的：

| 块 | 原条件 | 首帧 | 数据到位后 |
| --- | --- | --- | --- |
| 「记入 · 换账单」那一行 | `switchable.size > 1` | 不存在 | 出现，约 +52dp |
| 「常用」备注胶囊 | `suggestions.isNotEmpty() && note.isEmpty()` | 不存在 | 出现，约 +42dp |

合计约 **94dp 在弹层打开之后才长出来**。`ModalBottomSheet` 的展开位置是按内容高度算的，
内容变高它就重新算一次并动画过去——这就是"慢慢爬满"。购物恰好是常用备注最多的账单，所以最明显。

### 为什么不是 §41/§42 那两种改法

* §41 的 `heightIn(max = …)` 只挡"最高到哪"，挡不住"从当前高度动画到新高"。
* §42 的固定高度能挡住，但代价是**每个账单都被撑满**——你一句"每个账单都变成那样了"就把它否了。

正解是第三条：**只把会变的那两行的位置先占住**，整层仍然贴着内容长。

### 改法

* 「记入」行**永远渲染**，行高钉死 38dp。条件只决定行里的内容：`current != null` 才画徽章和账单名，
  `switchable.size > 1` 才给「换账单」入口和点击。只有一个账单的用户看到的是"记入 购物"，
  信息仍然成立，只是没得换。
* 「常用」行同样永远占位、行高钉死 38dp，胶囊按条件出现。顺带修掉另一个跳变：
  原来"开始打字"会让整行消失，弹层当场缩一次；现在缩的是胶囊，位置留着。

代价是没备注历史的账单会多一条 38dp 的空带——这是"不跳"换来的，我选不跳。
（如果这条空带你看著难受，说一声，我把「常用」并进备注框的辅助区，那里本来就有空位。）

### 这条有测试了

`recordSheetComposesWithATarget` 里加了一段：取 `record-bill-row` 的语义节点，
量它的实际高度，断言等于 38dp（按 Robolectric 配置的 density 换算）。
把 `.height(38.dp)` 去掉，这行高度会变成文字加内边距的约 30dp，断言立刻红。
这是这三轮里第一条真正能挡住回归的测试——前两版我都只能说"靠装机看"。

### 版本与实测数字

`versionCode 31 / versionName 1.19.6`，schema 仍为 3、备份格式仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **153 条全绿**、release 变体 **136 条全绿**（含新加的那段高度断言）。
- release 包 **2,031,557 B**（与 v1.19.5 同大小），debug 20,675,007 B，mapping 包 3,028,923 B。
- apksigner 与 zipalign 均 `Verification successful`；`badging` 两包 `versionCode='31'`；
  dist 与构建输出 md5 一致（release `38b89226…`）。
- 结构体检：76 个 Kotlin 文件、重复 import 0、未使用 import 0。

**装机确认**：① 打开购物的「记一笔」，顶部边缘应当**一次到位、不再爬**；② 开始打备注时弹层高度不变；
③ 没有备注历史的账单会多一条窄空带，确认你能接受（不接受就按上面说的并进备注框辅助区）。

## 45. v1.19.7 改动（记账弹层：先量出真实高度，再把内容做矮）

你这两句把我前四版的思路全否了：**"问题依旧"** + **"上下滑动一下还会开始抽搐"**。
抽搐是新出现的，而且是我造的，所以这次先把机制查清再动手。

### 抽搐是我引入的

拆 material3 1.4.0 的 `ModalBottomSheet` 字节码，三条事实：

* 展开位就是 **offset 0**，**弹层可见高度等于内容高度**；
* 它内部**没有滚动容器**，只有 `nestedScroll`（纵向手势归它管）；
* `ModalBottomSheetProperties` 在这个版本**没有** `isGestureEnabled`，关不掉弹层自己的拖动。

所以 §41/§43 那套"给内容封顶 + 让内容可滚"必然出事：同一个纵向手势，
子节点的 `verticalScroll` 要滚、父级弹层的 `nestedScroll` 要拖，两边互相拽——就是抽搐。
而且我用 `LocalWindowInfo` 取"窗口高度"来算上限，弹层本身就在独立窗口里，
窗口尺寸又跟着弹层状态变，等于给这个循环加了一条反馈边。**方向整个错了。**

### 正解：让内容比屏幕矮

内容装得下，就不需要封顶、不需要内部滚动、没有手势冲突，也不会有"顶到全屏"。
前提是要有**真实数字**——这里踩过一个坑：在小屏幕（`h740dp`）上量会被窗口裁掉，
删掉 46dp 前后都读出 692，看着像改动没生效。必须把窗口开大（`h1600dp`）才量得到真实高度。

新增 `RecordSheetHeightTest` 就是干这个的：在 1600dp 窗口上量弹层内容真实高度，
拿"一台 411×740dp 小手机的可用高度 ≈ 700dp"当判据。

### 减掉的两块（都是重复信息，不是砍功能）

| 改动 | 省 | 理由 |
| --- | --- | --- |
| 「记入 · 图标 · 名字 · 换账单」整行**并进标题行** | 46dp | 图标和账单名标题行刚出现过一次，那行是它的重复；`换账单` 挪到标题行尾部 |
| 「常用」胶囊移进备注框的 `supportingText` | 12dp | 这些胶囊本来就是"填这个框"的快捷方式，单占一行既白吃高度又是爬升源 |
| 两处 `Spacer(md)` → `sm` | 8dp | 弹层里不需要那么多呼吸 |

**754 → 708 → 696 → 688dp**（第一次量到的 754 就是"全屏"的来源：740dp 的屏幕装不下）。
现在 688 ≤ 700，还剩 12dp 余量。

爬升也顺手断了：标题行用 `target` 自带的账单名（一进来就有），不再等 `bills`；
备注框的辅助区**永远占位、高度钉死 30dp**，只有胶囊按条件出现。所以弹层从第一帧起就是最终高度。

### 诚实的边界

688dp 是**系统默认字号**下量的。用户把系统字号调大 20%，内容就 ~826dp，仍然会顶出屏幕——
而现在没有内部滚动可以兜底了（就是它造成抽搐）。真到那一步，正确做法是让弹层按字号缩放
（键盘按键高度和几处 padding 换成 `em` 式比例），而不是把封顶+滚动请回来。这条先记在这儿。

### 版本与实测数字

`versionCode 32 / versionName 1.19.7`，schema 仍为 3、备份格式仍为 2，证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **154 条全绿**（新增 `RecordSheetHeightTest` 1 条）、release 变体 **136 条全绿**。
  新测试类和另两个组合冒烟类一样要加进 release 变体的排除名单——`ui-test-manifest` 只能挂 debug，
  漏加就是 release 侧 `Unable to resolve activity`（这次就漏了一次，编译期没提示、跑起来才红）。
- release 包 **2,031,553 B**（v1.19.6 为 2,031,557，−4 B），debug 20,552,198 B，mapping 包 3,026,747 B。
- apksigner 与 zipalign 均 `Verification successful`；`badging` 两包 `versionCode='32'`；
  dist 与构建输出 md5 一致（release `cc34964c…`）。
- 结构体检：77 个 Kotlin 文件、重复 import 0、未使用 import 0（清掉 5 条：
  `Panels.kt` 的 `remember`/`LocalDensity`/`LocalWindowInfo`、`RecordSheet.kt` 的 `verticalScroll`、
  `ScreenSmokeTest.kt` 的 `abs`——都是这次删封顶留下的）。

**装机确认**：① 打开购物的「记一笔」，弹层应当**开到位就不动**，不再爬；② 上下滑动内容区不再抽搐；
③ 标题行右边有「换账单」，点了仍能切账单；④ 常用备注胶囊现在贴在备注框下面，点一下还能填进备注。

## 46. v1.20.0 改动（回收站：删除账单不再是不可逆的）

### 为什么挑这个

全 App 只有一个操作不可逆：删账单。`entries` 对 `bills` 是 `ON DELETE CASCADE`，
删一条账单会把它名下所有历史一起带走，界面上只有一句"删除后无法恢复"。
这个尖角我在早期章节就标过，一直没补。

### 设计：只打标记，条目不动

`bills` 加一列可空 `deletedAt`。删除 = 写进这一个时间戳；名下条目**一行都不动**。
恢复 = 把标记清掉。之所以不用"快照 + 重放"那套：标记方案下数据从来没离开过库，
不存在恢复时对不上账的可能。

真删只留两个入口：回收站里点「彻底删除」（要二次确认），以及**保留期 30 天到了自动清**。
清理挂在进程启动，和周期账单生成同一个时机——不养后台任务。

30 天的来由写进了代码注释：误删基本在当天或几天内发现，超过一个月才想起来更可能是"本来就该删";
再长回收站就变成第二个账本，每处统计都要多养一个过滤条件。

### 关键风险是"漏过滤"，不是"加列"

加一列很容易，难的是**每一处读账单的地方都要认它**。漏掉任何一处，表现都是
"账单删掉了但它的钱还在总数里"——这种错没人会逐条核对。这次改到的读路径：

| 查询 | 加了什么 |
| --- | --- |
| `BillDao.observeAll` / `findById` / `recurring` | `deletedAt IS NULL` |
| `observeSummaries`（首页与统计） | `b.deletedAt IS NULL` |
| `observeRecent` / `observeCount` / `observeCountBetween` | 原来不 JOIN，现在联到 bills 过滤 |
| `search` / `rowsInRange` / `rowsBetween` / `largestBetween` / 两个 `totalBetween` | `AND b.deletedAt IS NULL` |
| `ExportDao.exportRows` | 同上（CSV 里不该有回收站内容） |

`search` 那条有个 SQL 陷阱值得记：三个匹配条件是 `OR` 连起来的，而 **AND 比 OR 绑得紧**，
直接写成 `A OR B OR C AND deletedAt IS NULL` 会变成"A 命中就完全不看回收站"。
必须把 OR 整体括起来。这类错编译器不报，只有测试能抓——`TrashDbTest` 里就有这一条。

`maxSortOrder` 故意**不过滤**：排序号取全表最大值（含回收站），否则"删一个再建一个"会撞号。

### 顺手修了一个既有 bug

设置页那个"名下 N 笔"是拿 `observeBillSummaries` 拼的，而那条查询带 `WHERE archived = 0`——
于是**每个已归档账单都显示"名下 0 笔"**，偏偏这个数字是告诉用户"删掉会带走多少条"的依据。
改成新开一条 `observeCountsByBill`：直接按条目分组，归档和回收站都算得出来。
这条有独立的测试（`entryCountsCoverArchivedAndTrashedBills`）。

### 界面

* 设置页「已归档」下面多一节**回收站**：徽标 + 账单名 + "名下 N 笔 · 还剩 X 天"，
  右边「恢复」和「彻底删除」（后者第一次点只进入确认态）。
* 编辑弹窗里删除的后果说明改准了：原来写"删除后无法恢复"，
  现在写"移进回收站，30 天内可在设置页恢复；到期才彻底删除，那一步不可恢复"，
  并且名下 0 笔和有笔两种情况分开说。
* 归档那节的说明补了一句指向回收站。

### 迁移与备份

* **库结构 3 → 4**：`ALTER TABLE bills ADD COLUMN deletedAt INTEGER`，可空无默认——
  老行读出来就是 NULL（不在回收站），不需要回填。`SchemaMigrationTest` 现在是从真实 v2 库
  一路让 Room 升到 v4，顺带断言老行 `deletedAt IS NULL`。
* **备份格式 2 → 3**：账单行末尾加一项 `deletedAt`（空串 = 没被删）。
  仍然读旧写新：v1 的 8 字段、v2 的 12 字段、v3 的 13 字段各按各的长度解析，
  字段数由 `billFieldsFor(formatVersion)` 一处决定。回收站状态必须进备份，
  否则一份带着回收站的备份恢复出来会多出几个活账单。

### 测试

新增 `TrashDbTest` 7 条（真 Room）：软删后账单从首页/统计/搜索/导出/最近条目/按日查看
**每一处**都消失而条目一行不少；恢复后 id 与金额原样；只有彻底删除才级联；
过期清理只清过保留期的；回收站里的账单不再生成自动条目；`find` 拒绝返回回收站账单
（否则会出现"往已删账单里记一笔"）；归档与回收站的"名下几笔"都算得出。
备份那边加 2 条：v2 文件仍能读且 `deletedAt` 为 null、回收站标记往返一致。

### 版本与实测数字

`versionCode 33 / versionName 1.20.0`，**库结构 3 → 4**（显式迁移），备份格式 2 → 3，
证书不变（`fc86dddf…906c`），覆盖安装即可。

- 单测：debug **163 条全绿**、release 变体 **145 条全绿**（比上轮各 +9：回收站 7 条 + 备份 2 条）。
- release 包 **2,031,557 B**（与 v1.19.7 同大小），debug 20,659,251 B，mapping 包 3,038,231 B。
- apksigner 与 zipalign 均 `Verification successful`；`badging` 两包 `versionCode='33'`；
  dist 与构建输出 md5 一致（release `f0f8d51f…`）。
- 结构体检：78 个 Kotlin 文件、重复 import 0、未使用 import 0。
- Room 导出的 schema 目录现在有 `1.json`–`4.json`，迁移测试就是拿它们当"真机老库"的。

**装机确认**：① 删一个账单，首页和分析页立刻不该再有它和它的钱，但设置页回收站里能看到它；
② 从回收站点「恢复」，账单和名下条目原样回来（笔数、金额都对得上）；
③ 「彻底删除」要按两次，删掉后名下条目确实一起没了；
④ 导出 CSV 和「关于」里的笔数都不该算上回收站里的东西；
⑤ 老库升 v4 顺不顺（这轮风险最高的还是它，测试只能证明 Room 自己满意）。


---

## 47. v1.21.0 改动（消费日历 + 连续记账天数；搜索筛选下沉到 SQL）

两件一起发，因为它们共用同一条判断：**用户想问的是"哪几天"和"哪一类"，而这两个问题原来都问不出来**。

### 分析页：消费日历

一格一天，周一开头（和中文日历习惯一致），点任意一天打开当天的明细面板——复用的是趋势图那条
`onOpenDay(start, end)`，所以"格子"和"柱子"是同一个入口的两种画法，没有新造一套跳转。

四个决定：

- **对齐交给纯函数**。`MonthHeatmap.cells()` 返回的是"前导空格 + 每天一格"的一维列表，
  `HeatCell.date` 允许为 null 表示空格。这样 1 号落在第几列这件事在 JVM 里就能逐月核，
  不必装到手机上看——日历错一格没人会去逐格核，只会觉得"好像哪不对"。
- **深浅跟当月最贵那天比，不跟平均值比**。日历要回答"哪几天异常重"，和平均比会把所有日子挤成中间色。
  四档（>0.75 / >0.50 / >0.25 / 其余），压线归轻一档。
- **颜色跟主题主色，不跟账单配色**。和记账计算器（§42）同一条规矩：整页的分析视图不属于任何一个账单。
- **一天没记也要摆满整月**。格子数直接取 `lengthOfMonth()`，不依赖数据里有没有那天，
  否则空月会缩成几格，看着像坏了。

### 连续记账天数

`Streak.days(active, today)`：**今天还没记不算断**，从今天往前数（今天没记则从昨天起数），遇到第一个没记的日子停。
理由是激励口径而不是记账口径——晚上十点打开 App，"连续 12 天"比"连续 11 天，昨天断了"更可能让人继续记；
真要提醒没记，首页那条「今天还没记」（§40）已经在做，这里不该再吓一次。回看上限 400 天：
连续天数超过一年基本不会再涨，多取两周只为覆盖跨月跨年边界。

### 搜索：筛选从内存挪进 SQL

上一版的方向筛选是在 ViewModel 里对已取回的结果做内存过滤，金额和时间问法根本不存在。
现在**方向 / 金额下限 / 时间范围三个维度和关键词一起进 WHERE**，因为"500 以上的支出有哪些"
这类问法本来就没有关键词，取回全部再过滤等于把整张表读进内存。

- **哨兵值集中在一处**。`SearchQuery.params()` 一次生成九个绑定参数："不限"分别绑成
  `hasText = 0`、`kind = ''`、`minCents = 0`、`endExclusive = Long.MAX_VALUE`。
  时间不限用 `MAX_VALUE` 而不是 `-1`：前者是**结构上不可能被任何时刻排除**，后者要靠注释维持约定。
- **三个匹配条件必须整体括起来再 AND 回收站过滤**（§46 那条坑的续集）：SQL 里 AND 比 OR 绑得紧。
- **金额下限做成四个档**，不给自由输入——否则会出现"用户输了 0.001 然后奇怪为什么没结果"。
- **空关键词 + 任意一个筛选＝一次真实查询**；三维度全不限且没输入＝不查库（返回空列表，
  因为"给我前 200 条"不是一千种问法里的那一种）。

### 这一轮的两条实测事实（都是先按印象写、被编译器打回来）

1. **Compose UI test 1.11.4 没有 `Substring` 枚举**。`onAllNodesWithText` 的真实签名是
   `(text: String, substring: Boolean, ignoreCase: Boolean, useUnmergedTree: Boolean)`
   （`javap androidx.compose.ui.test.FindersKt`）。按"我记得有 `Substring.Contains`"写出来的断言直接编译不过。
2. **上一轮写下的 `SearchFilterTest.kt` 从来没编译过**：那轮最后只跑了 `compileDebugKotlin`（主源），
   测试源第一次进 `compileDebugUnitTestKotlin` 就报 5 处错。**只编译主源的"绿"不算过闸。**

### 验证

- 单测：debug **203 条**、release **183 条**（20 条 Robolectric 组合测试按例只在 debug 跑），0 失败。
- 本轮新增/修复：`HeatmapTest` 15 条（含 60 个月逐一核对起始列，7 种星期全覆盖）、
  `SearchFilterDbTest` 11 条（真 SQLite 跑哨兵与括号）、`SearchQueryTest` +3 条（哨兵约定单独钉）、
  `SearchFilterTest` 9 条（修好才跑起来）、`ScreenSmokeTest` +2 条（日历格子数＝模型给的格子数；搜索页三行筛选都在）。
- **红测核验**：把 `columnOf` 改成恒返回 0、把 streak 游标改成恒从"今天"起 → 恰好 4 条红，
  正是盯这两处的断言；说明它们不是恒真式。
- 产物：release **2,047,941 B（1,953 KB）**，debug 20,601,346 B；`aapt2 dump badging` 显示
  versionCode=34 / versionName=1.21.0 / min 26 / target 36；`apksigner verify` 通过，
  证书 `CN=TingLedger`，SHA-256 `fc86dddf…50906c`；`zipalign -c` 校验通过。
- 日历格子加了 `testTag("heat-cell")`：只为让冒烟测试数得出格子，不影响任何视觉。
- dist 里 **v1.20.0 那一套先留着**：历史崩溃日志要靠对应的 mapping 还原，删早了等于丢掉可符号化的过去。

---

## 48. v1.22.0 改动（分类预算：每个账单自己的月度上限）

总额预算从 v1.13 就在，但它只能回答"这个月还能花多少"。真正天天要问的是
**"吃饭这个月超了没"**——这一轮补的是那一句。

### 上限存在 DataStore，不进 bills 表

看起来"账单的一个属性"该是账单表的一列，这轮刻意没有：

- 加一列要再来一次 schema 迁移，而 **v3→v4 到现在都还没在真机上验过**（我这侧没有任何设备）。
  在一件未验的事上面再叠第二件未验的事，出问题时分不清是谁。
- 上限的语义本来就是偏好而不是账目：它不参与任何金额汇总、不进 CSV 的一行、不该被 CASCADE 牵连。
  和总预算、主题色住在一起才对。

存法是一条字符串偏好 `bill_caps = "id=cents;id=cents"`，编解码是纯函数 [BillCaps]：
`encode` 先过滤掉 id<=0 与 cents<=0（**上限为 0 等于没设，不该在界面上长出"上限 ¥0.00"**），
再按 id 排序，所以同一份内容永远写成同一个字符串——备份文件才能逐字节比对。
`decode`（读偏好）对认不出的片段**跳过**；`parseStrict`（读备份）**整条失败**。
两套是刻意的：偏好坏一片，丢那一片比丢全部上限好；备份被截断，必须整份拒收。

### 真删账单要把上限一起摘掉

SQLite 的 rowid 会被复用：彻底删除一个账单后，新建的账单可能拿到同一个 id，
于是莫名背上别人的上限。所以 `purgeTrash` 的返回从"几条"改成了**"哪几条"**
（`trashedIdsOlderThan` + `deleteByIds` 两步，不再用一条 `DELETE ... WHERE deletedAt < cutoff`），
`deleteForever` / `purgeTrash` 两条真删路径都跟着 `dropBillCap`。
备份侧同构：`CAPS` 行里出现文件中不存在的账单 id，整份拒收。

### 界面只加了两处

- **账单编辑器**多一个「月度上限（元）」输入框，**只对支出账单出现**（收入没有"超支"这回事），
  沿用每期金额那套 `trimToMoney` 规则。交回的是 `(Bill, Cents)` 两样：
  一次表单一次保存，不能出现"名字改了上限没改"的半提交。
  从支出改成收入时上限归零——判断放在计算 `capCents` 那一处，不放在点击回调里。
- **分析页「支出占比」卡片底部**按需追加几行 `本月 X / 上限 Y`，超额把进度与金额染成 `error` 色。
  **没设上限的人一行都看不到**，设了的人也不必跳到别的页面去对。占比那行本身一字未改——
  同一个卡片里不能今天说"占大头"明天改口说"占上限"。

### 这一轮被自己的测试抓到的真 bug

`CapStatus.overBy` 写成了 `(spent - cap).coerceAtLeast(0)`，没先看 `isActive`：
于是**没设上限的账单会算出"超出 ¥500"**。第一次跑 `testDebugUnitTest` 就红了
（`statusWithoutCapSaysNothing`）。同类问题在 `remaining` 上一并修掉，两处都改成先看 `isActive`。

变异核验（新写的纯测试必须是能红的）：把 `encode` 的过滤放宽成 `>= 0`、把 `progress` 的夹取去掉
→ 恰好 3 条红（`encodeSortsAndSkipsUnset`、`encodeOfNothingIsNothing`、
`ratioIsTheRealMultipleWhileProgressIsClamped`）。同时得到一个事实：
真 DataStore 那条"写 0 等于清空"在变异下仍然绿，因为 `decode` 自己也会丢掉 0——这条规则是双保险的。

### 备份格式 v3 → v4

多一行 `CAPS`。老文件没这行读成空表，**不拿本机现值当默认**（否则恢复一份旧备份会把后来的上限残在账本上）。
恢复走 `replaceBillCaps` 整表替换而不是逐条 set：恢复必须以文件为准。

### 验证

- 单测：debug **230 条**、release **206 条**（24 / 20 个类，0 失败）。差值 24 是四个 Compose 类
  按例只在 debug 跑（它们依赖 debugOnly 的 `ui-test-manifest`）。
- 本轮新增 **24 条**：`BillCapsTest` 13（纯逻辑，两侧都跑）、`BillCapStoreTest` 4（**真 DataStore** 往返：
  合并语义、写 0 即清除、drop/replace）、`BillEditorDialogSmokeTest` +4（收入不出现该字段、
  回填值不被悄悄改写、填进去的换成"分"交回、留空＝0）、`BackupFormatTest` +4（CAPS 往返、
  v3 老文件、CAPS 被篡改拒收、上限指向不存在的账单拒收）、`TrashDbTest` +2（两条真删路径的上限清理）。
- 顺带修掉一处测试自身的坏味道：`BillCapStoreTest` 曾按 `caps().size` 下断言，而偏好文件在同一进程里
  是跨测试复用的——换成只断言本用例独占的 id，否则换个执行顺序就假失败。
- 产物：release **2,047,941 B（1,953 KB）**、debug 20,617,730 B。变异重编译过主源，
  所以删掉产物**强制重新打包**再核验：`aapt2` 显示 versionCode=35 / versionName=1.22.0；
  `apksigner verify` 通过、证书仍是 `CN=TingLedger` / SHA-256 `fc86dddf…50906c`（与 v1.21.0 同一把钥匙）；
  `zipalign -c` 通过；dist 与 build 产物 md5 逐字节一致。
- import 审计删掉 5 条（都是把 `FakeSettings` 提成共享类之后剩下的未用 import），两侧测试源重新编译绿。

### 装机要看的是

① 支出账单编辑页有「月度上限」、收入账单没有；② 设了上限之后分析页占比卡片下面出现那几行、
超月的账单是警示色；③ 彻底删除一个账单（含回收站到期清理）之后，新建账单**不该**带着旧上限；
④ 导出的备份文件里有 `CAPS` 行，恢复之后上限回来；用 v1.21.0 导出的老备份恢复也**不该报错**（读成空表）；
⑤ 老库 v3→v4 的迁移仍未在真机验过，这是这套代码目前最高风险的未验项。

---

## 49. v1.23.0 改动（预算节奏：每天还能花多少、照这样下去月末会怎样）

上一版有了上限，但预算只会说"已经花了多少"。真正影响今天决策的是另外两句：
**还剩几天、每天还能花多少**，以及**照这个速度月末会到哪**。这轮补的就是那两句。

### 节奏的口径

`Pace(spent, budget, elapsedDays, daysInMonth)` 全整数、全纯函数：

- **日均 = 已花 ÷ 已过天数**，不是 ÷ 自然月天数。月初看"日均"才有意义，除错分母会让第一周的数一律虚高。
- **剩余日额度 = 剩下的钱 ÷ 剩下的天数（含今天）**，并且**向下取整**。向上取整会出现"照着建议花三天然后超支"，
  而这句建议的全部价值就是照着花不超。
- **超过预算就没有日均建议**（给 0），因为"你每天还能花 -50 元"不是一句可以显示的话。
- **正好花到预算不算超**，和 `CapStatus` 同一口径；但此时剩余日额度是 0，界面说"压线"。
- **`overrunDay` 是"累计严格超过预算的第一天"**：每天 100 元、预算 1000 元 → 第 10 天正好花到，
  答案必须是 11。这条是最容易写错成 `budget/perDay` 的地方，测试按边界钉住。

### 天数不够时，预测故意不说

`projectionIsReliable = elapsedDays >= 7`。月初第一天一笔房租，就能把"月末预计"推到实际值的三倍，
用户那一刻的判断不会是"这个月危险了"，而是"**这个 App 在瞎说**"。
所以第 1–6 天只说"日均可用"，那句预测换成一句实话：「才过 N 天，还看不出这个月的速度」。
阈值本身也进了测试（`elapsed=7` 可靠、`elapsed=6` 不可靠），免得有人把它悄悄改松。

同理 `CLOSE_WITHIN = 0.10`（时间进度领先花钱进度不到 10% 算"压线"）也按边界各钉一条：
差 10% 整算压线，差 10.1% 算宽裕。

### 界面只多两处，都不加控件

- **分析页**在总预算卡片下面多一块「预算节奏」，**只有设了总预算才出现**：
  进度条 + 「日均可用 ¥X · 剩 N 天」（已超则换成「已超 ¥Z」）+ 那一句预测。
  会超或已超时用 `error` 色，其余走中性色。
- **首页**预算那一行下面多一行小字「日均还能花 ¥X · 剩 N 天」。
  只在没超且真的还剩天数时出现。
- **往月不骗人**：`Pace.of` 只在看的确实是本月时用"今天"当已过天数；翻到往月按整月算，
  未来月份 `elapsedDays = 0`（于是速度、预测都自动不成立）。

### 装机验不到的那条：DataStore 要先读一次

「预算节奏」是条件出现的节点，正是 `ScreenSmokeTest` 存在的理由（组合期越界编译器看不见）。
第一次跑它 30 秒超时——写进偏好之后**立刻**进组合，界面那条 `monthlyBudget` 一直没等到 emission。
加上"写完先 `first()` 读回来"这一步（顺带断言值确实进去了）就稳定通过：
DataStore 的首次读才把它的后台作用域拉起来。这不是把测试改松，断言的是同一件事。

### 验证

- 变异核验：把 `CLOSE_WITHIN` 改成 0.50、`MIN_DAYS_FOR_PROJECTION` 改成 1、`overrunDay` 的 `+1` 去掉
  → **4 条红**，正是盯这三处规则的断言（`statesSplitAtThePaceLine`、`closeThresholdIsExactlyTenPercent`、
  `projectionRefusesToSpeakInTheFirstWeek`、`overrunDayIsTheFirstDayStrictlyOverBudget`）。
- 单测：debug **249 条 / 25 类**、release **224 条 / 21 类**，0 失败。本轮新增 19 条
  （`PaceTest` 18 + 分析页节奏块的组合用例 1）。
- 过程中改了两处自己的东西：`dailySoFar` 在 `elapsedDays = 0`（未来月份）时原本会等于"整笔已花"，
  改成给 0；测试里把分析页那句文案写成首页那句（两处文案本就不同，红得对）。
- 产物：release **2,047,937 B（1,953 KB）**、debug 20,634,114 B；`aapt2` 显示 versionCode=36 / 1.23.0；
  `apksigner verify`、`zipalign -c` 均通过，证书仍是 `CN=TingLedger`（`fc86dddf…50906c`）；
  dist 与 build 产物 md5 一致；import 审计 0 删除。
- 变异重编译过主源，所以删掉产物重新打包后才做上面这些核验。

### 装机要看的是

① 设了总预算之后分析页出现「预算节奏」，没设时整块不在；② 首页预算行下面那句"日均还能花"；
③ 今天是月底附近时"剩 N 天"要对得上日历；④ 月初（1–6 号）应该看到的是"还看不出速度"而不是一个吓人的预测；
⑤ 老库 v3→v4 迁移仍未在真机验过——这是目前最高风险的未验项。

---

## 50. v1.24.0 改动（样式体系收敛：圆角、尺寸阶、透明度各归一处）

这轮不动功能，只修"同一个意思在八个文件里各写一遍"这件事。开工前先量化，收工后用测试钉住。

### 开工时的账（判据：对 `ui/` 实际 grep 计数，非 theme 目录 = 21 个文件）

| 维度 | 扫描结果 |
|---|---|
| 字阶 | 非 theme 裸 `fontSize = N.sp` **0 处** — 已经是好的，没动 |
| 颜色 | 非 theme 裸 `Color(0x...)` **0 处**；但 `alpha`/`copy(alpha` 表达式 **28 处** |
| 间距 | `Spacing.*` 用了 **206 次**，同时裸 dp **175 处**，其中 **116 处不在 4 倍网格上** |
| 圆角 | 裸 `RoundedCornerShape(` **28 处**；`Panels` 里五个值；`MoneyShapes`(8/12/18/24/30) **被 MaterialTheme 接走但界面引用 0 次** |
| 动效 | `Motion` 已有，仍有裸 `tween(N)` **5 处** |

诊断一句话：**不是缺 token，是两套并行 + 一处死 token**。

### 分类规则（这轮最重要的决定）

那 116 个"不在网格上"的值**不都是一回事**，一律四舍五入会把每页都挪位且没人能判断好坏。分成三类：

1. **布局间距**（padding / Spacer / spacedBy）→ 回到 4 倍网格。这是唯一成批的可见变化，授权上限 **≤4dp**。
2. **固有尺寸**（图标字形、徽标盒子、进度条厚、图表几何、骨架灰块）→ 不该被间距网格管，
   给它们自己的阶：`IconSize`(14/16/18/20/26)、`Badge`(30/34/42/56 + dot)、`Bar`(1/2/3/4/6/1.6…)、
   `Chart`(间隙/柱高/图例)、`Skeleton`、`Rows`、`Col`、`Swatch`、`Toast`、`Sheet`、`Elev`、`FloatOffset`、`Drag`。
   这一类**取值不变**，只是从"某处随手写的数字"变成"有名字、有出处、可查"。
3. **几何补偿**（发丝线 1dp、格子间隙 1.5dp）→ 原值保留并命名（`Bar.hairline`、`Chart.cellGap`、`Radius.Hairline`）。

### 圆角只留一套

按选定的**锐利档**收敛：`Radius.Chip 8 / Field 12 / Card 16 / Overlay 20`，
外加按用途存在的 `Pill`、`BarTop`、`HeatCell`、`Tiny`、`Hairline`、`SheetTop`、
`RecordSheetTop 24`（原来随手 28，收到 24 是为了守住"位移 ≤4dp"）、`pillOf(height)`（高度决定半径的那些）。
`MoneyShapes` 删除，`MaterialTheme` 不再接 shapes——一套没人引用的平行尺度是这类问题最容易长回来的地方。

### 收尾时的账（同一套判据）

非 theme 目录：**裸 dp 0 处、裸 `RoundedCornerShape(` 0 处、`alpha = 0.Nf` 0 处、`tween(N)` 0 处**。
共 **79 类 / 88 处**替换。审计没写进注释里靠自觉，而是变成会红的测试：`StyleAuditTest`（5 条，
debug 与 release 两侧都跑）。找不到源码根目录就**硬失败**，不静默返回"没问题"。

### 两处我自己犯的错（写在这里，因为它们会重犯）

1. **批量替换的规则串本身有笔误**：三条规则我写成"旧串带右括号、新串不带"
   （`"vertical = 10.dp)"` → `"vertical = Spacing.md"`），把 `)` 换成了 `,`，弄坏了 3 处代码；
   分析页那个丢掉的括号也是同一类。**断言管的是出现次数，管不了这种笔误**——
   全靠 `compileDebugKotlin` 逐个文件抓出来。所以从现在起：**每迁移一个文件立刻编译**，不攒着。
2. **包级 `object Float` 会遮蔽 `kotlin.Float`**：本包内 `alpha: Float = 0.12f` 当场类型不匹配。
   已改名 `FloatOffset`，并把这条写进注释。

### 位移清单（第 1 类，其余都是零变化改名）

- 卡片圆角 20→16；内层面板/输入框 14→12；小胶囊 10→8（首页那两个 9→8）；对话框 26→20；
  按钮 16→12；记一笔弹层顶角 28→24。**这是这轮最主要的观感变化，全站一致。**
- 列表行纵向内边距 14→12（每行 −4）；筛选胶囊纵向 7→8、首页两处 `Spacer` 3→4；
- 首页行尾那一块 55→56：**必须跟着改**，它的注释写着 `26 + 3 + 26`，间隙抬到 4 之后总高就是 56，
  不一起改会裁掉内容——这类耦合是"随手数字"最怕的东西。
- 徽标盒子：38→42（设置页账单行 2 处）、32→34（详情页头、换账单行）；字形 13→14、15→16、17→16。
- 骨架灰块圆角改为按高度取半：13→6.5、9→4.5、18→9。
- 轻提示左右 14→16、内层胶囊 10/6→12/4；月份胶囊纵向 10→12。
- 透明度那 28 处**取值一个没动**（0.45/0.5/0.6/0.72/0.14/0.35…只是有了名字），
  动画时长同理（180/140/720 → `Motion.SOFT/QUICK/PULSE`）。

**要退回原来的圆角手感**：只改 `ui/theme/Radius.kt` 里 Chip/Field/Card/Overlay 四个数与
`RecordSheetTop`，其它都不用动——这正是收敛的意义。

### 验证

- 单测：debug **254 条 / 26 类**、release **229 条 / 22 类**，0 失败（新增 `StyleAuditTest` 5 条）。
- **守卫能红核验**：临时放一个违规文件（`13.dp` + `RoundedCornerShape(13.dp)` + `0.33f`）→
  `noRawDpOutsideTheme`、`noInlineCornerShapes` 两条红，删除后恢复绿。
  已知边界：透明度那条只认 `alpha =` / `tint(` 语境，换个写法（如 `Float` 变量传入）它看不见。
- 既有界面回归全过：`ScreenSmokeTest` 11、`SettingsScreenSmokeTest` 5、
  `BillEditorDialogSmokeTest` 8（含颜色行可达那条——圆角变了但可滚动性没变）、`RecordSheetHeightTest` 1。
- import 审计删 5 条未用 import（`PaddingValues` 与四处 `dp`），主源/两侧测试源重新编译绿；
  因为主源变过，产物**从最终源码重打一次**再核验。
- 产物：release **2,047,941 B（1,953 KB）**；versionCode **37** / versionName 1.24.0、min 26 / target 36；
  `apksigner verify` 对 **dist 里那份**通过、`zipalign -c` 通过、证书仍是同一把 `CN=TingLedger`
  （`fc86dddf…50906c`）；dist 与 build md5 一致。

### 装机要看的是

① 全站圆角是不是变利落了指望的方向（这轮唯一成批的观感变化）；② 首页/分析页文字有没有被新的
行高或行尾 56 裁到；③ 记一笔弹层顶角与键盘行；④ 骨架屏灰块；⑤ 明暗两套 + 4 套莫奈配色下的
卡片描边（透明度取值没动，但名字换了，值得眼过一遍）；⑥ 老库 v3→v4 迁移仍未在真机验过。

---

## 51. v1.25.0 改动（性能第一轮：先建测量台，再动刀）

性能这类改动最容易变成"我觉得会快一点"。所以这轮的顺序是**先有判据，再改**，
而且把判据留在仓库里（不是跑一次就丢的脚本）。方案与判据见 `性能优化方案.md`。

### 先修好"能测"这件事

`AnalyticsViewModel` 等 7 个 ViewModel 原来直接依赖具体类 `AppContainer`，
所以**没法塞假仓储去数查询次数**——这句话本身就只能靠读代码数，不能变成会红的测试。
这轮把它们改成注入实际用到的仓储/用例：

```kotlin
class AnalyticsViewModel(
    private val billRepository: BillRepository,
    private val buildStats: BuildStatsUseCase,
    private val settingsRepository: SettingsRepository,
    private val statsRepository: StatsRepository,
) : ViewModel()
```

于是有了 `AnalyticsQueryCountTest`（4 条）。它的价值全在**断言的是次数**，
界面上永远看不见"多扫了一遍整月流水"这件事。

### 基线 → 结果（数字来自 `dailySeries` 调用计数，不是估计）

| 场景 | 改前 | 改后 |
|---|---|---|
| 打开分析页 | 同一条整月区间被扫 **2** 次 | 每个区间各 1 次，重复 0 |
| 记一笔 | 额外 **+2** 次整月扫描 | **+1** |
| 翻月 | 额外 **+2** | **+1** |
| 改账单上限 | +0 | +0（本来就不该查） |

三处改动换来的：

1. **`digest` 复用 `monthDays`**。原来 `digest` 与 `monthDays` 各查一遍
   **完全相同**的 `dailySeries(本月, 支出)`；现在 `digest = combine(monthSummaries, monthDays)`，
   顺带让"最贵一天"和日历永远是同一时刻的同一份数据（以前两者可能来自不同时刻）。
2. **`stateIn` 的种子值会被下游当成一次真发射**。`monthSummaries` 种子是 `emptyList()`，
   数据库随后发一次真值 —— `flatMapLatest` 于是跑了两遍，这就是"打开时多一遍整月扫描"的真身。
   改法：种子改 `null`（类型变 `List<BillSummary>?`），下游统一 `filterNotNull()`。
3. **共享根用 `Eagerly` 而不是 `WhileSubscribed(5_000)`**。`monthSummaries` 是五条派生流共用的根，
   订阅陆续到齐期间它会被拆掉重启，重启 = 下游每一条带着自己那趟查询重跑。

### 顺带修掉的两处组合期开销

- `viewModel.dailyAverage()` / `balance()`（首页与分析页各一处）以前在**组合期**调用，
  内部读 `StateFlow.value`：每次重组重算，而且是非受跟踪读（理论上能读到旧值）。改成 VM 里的派生 `StateFlow`。
- `billCaps` 那个 `Map` 不再进组合：上限现在合进 `ShareSlice.cap`，界面拿到的是稳定 `List`。
  `Map` 在 Compose 里是不稳定类型，用到它的那段每次重组都跑。

### 一条被**证据否决**的优化（这轮最重要的记录）

试过把逐日聚合下推到 SQL（`date(occurredAt/1000,'unixepoch','localtime')`），
这样跨边界的行数从"条目数"降到"天数"。按规矩**先写时区对照测试**再切实现。
测试直接红了，而且红的正是我埋的那笔最坏情况：

```
2026-02-01 起 1 个月的活跃日集合应逐天一致
expected:<[2026-02-28]> but was:<[2026-03-01]>
```

种子是 `LocalDateTime.of(2026,2,28,23,59,59,999_000_000)`（America/New_York 本地时间），
本地日就该是 **02-28**；SQL 给的 `03-01` 是 **UTC 日** —— 也就是说 `'localtime'` 在这里
根本没跟 JVM 时区走（`TimeZone.setDefault` 没传到 native SQLite）。

真机上 SQLite 的 `'localtime'` 很可能确实跟设备时区一致，**但我这边没有设备，验不了**。
代价是"日历与连续天数在某些时刻整体错一天"——这种错没人会去逐格核。
所以：**这轮不采纳，SQL 路径与它的测试一起撤掉，公开实现保持 Kotlin 侧逐条换算**
（慢一点，但时区口径由 `millisToLocalDate` 一处决定，可测且已测）。
想再捡起来的话，前置条件是：真机跨时区（含 DST 那两天）逐日比对通过。

### 下一轮（不在本轮做，避免和测量混在一起）

- **P4 分析页/首页按 section 拆收集**：顶层一次收 14–17 条流，任意一条变就整片重组。
  这是界面结构改动，值得单独一轮 + 补节点级断言，不挤在性能轮里。
- 逐日/活跃日的 SQL 聚合（见上面被否决那条的前置条件）。
- `streakDays` 的回看窗口 400 天：目前每次触发一趟 400 天扫描（改后已是"一次"，但还是最重的一次）。

### 验证

- 单测：debug **258 条 / 27 类**、release **233 条 / 23 类**，0 失败（本轮新增计数测试 4 条）。
- 计数测试自己经过红→绿：先按目标值写好断言 → 跑出基线（3 次含 1 次重复 / +2 / +2）→ 改 → 全绿。
- 写测试台时踩到的两个真问题，都记进了记忆：
  `runTest` **不会**替换 `Dispatchers.Main`（不 `setMain` 时 `viewModelScope` 一条都不跑，
  所有计数停在 0，看起来像"代码没问题"）；`TimeZone.setDefault` 不会传进 native SQLite 的 `'localtime'`。
- 过程中我自己写坏过 3 处（批量替换的括号笔误、`plusMonths(Int)`、属性前向引用），
  每次都是**当场编译**抓出来的——这轮坚持"改一个文件编一次"，没有攒到最后。
- 产物：release **2,064,325 B（2,016 KB，比 v1.24.0 的 2,047,941 B 大 16 KB）**、debug 实测 20,634,114 B；
  versionCode **38** / 1.25.0；`apksigner verify`、`zipalign -c` 通过，证书同一把
  `CN=TingLedger`（`fc86dddf…50906c`）；dist 与 build md5 逐字节一致；import 审计 0 删除。
- 顺序：先 import 审计（删 0 条）→ 再 `test assembleRelease assembleDebug` → 才拷贝 dist，
  保证 APK 就是最终源码的产物。

### 装机要看的是

① 功能应与 v1.24.0 完全一致（这轮不改行为）；② 分析页翻月/记一笔之后数字是否仍然同步
（`digest` 现在复用日历那份序列，"最贵一天"与日历深浅不会再来自不同时刻）；
③ 首页"日均"和"净额"是否照常显示；④ 占比卡片里的上限行仍在上限改动后立刻更新；
⑤ 老库 v3→v4 迁移仍未真机验证 —— 这是目前唯一的高风险未验项。
