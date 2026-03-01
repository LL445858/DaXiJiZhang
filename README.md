## Daxijizhang[![zread](https://img.shields.io/badge/Ask_Zread-_.svg?style=flat&color=00b0aa&labelColor=000000&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB3aWR0aD0iMTYiIGhlaWdodD0iMTYiIHZpZXdCb3g9IjAgMCAxNiAxNiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTQuOTYxNTYgMS42MDAxSDIuMjQxNTZDMS44ODgxIDEuNjAwMSAxLjYwMTU2IDEuODg2NjQgMS42MDE1NiAyLjI0MDFWNC45NjAxQzEuNjAxNTYgNS4zMTM1NiAxLjg4ODEgNS42MDAxIDIuMjQxNTYgNS42MDAxSDQuOTYxNTZDNS4zMTUwMiA1LjYwMDEgNS42MDE1NiA1LjMxMzU2IDUuNjAxNTYgNC45NjAxVjIuMjQwMUM1LjYwMTU2IDEuODg2NjQgNS4zMTUwMiAxLjYwMDEgNC45NjE1NiAxLjYwMDFaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00Ljk2MTU2IDEwLjM5OTlIMi4yNDE1NkMxLjg4ODEgMTAuMzk5OSAxLjYwMTU2IDEwLjY4NjQgMS42MDE1NiAxMS4wMzk5VjEzLjc1OTlDMS42MDE1NiAxNC4xMTM0IDEuODg4MSAxNC4zOTk5IDIuMjQxNTYgMTQuMzk5OUg0Ljk2MTU2QzUuMzE1MDIgMTQuMzk5OSA1LjYwMTU2IDE0LjExMzQgNS42MDE1NiAxMy43NTk5VjExLjAzOTlDNS42MDE1NiAxMC42ODY0IDUuMzE1MDIgMTAuMzk5OSA0Ljk2MTU2IDEwLjM5OTlaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik0xMy43NTg0IDEuNjAwMUgxMS4wMzg0QzEwLjY4NSAxLjYwMDEgMTAuMzk4NCAxLjg4NjY0IDEwLjM5ODQgMi4yNDAxVjQuOTYwMUMxMC4zOTg0IDUuMzEzNTYgMTAuNjg1IDUuNjAwMSAxMS4wMzg0IDUuNjAwMUgxMy43NTg0QzE0LjExMTkgNS42MDAxIDE0LjM5ODQgNS4zMTM1NiAxNC4zOTg0IDQuOTYwMVYyLjI0MDFDMTQuMzk4NCAxLjg4NjY0IDE0LjExMTkgMS42MDAxIDEzLjc1ODQgMS42MDAxWiIgZmlsbD0iI2ZmZiIvPgo8cGF0aCBkPSJNNCAxMkwxMiA0TDQgMTJaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00IDEyTDEyIDQiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIxLjUiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIvPgo8L3N2Zz4K&logoColor=ffffff)](https://zread.ai/LL445858/DaXiJiZhang)

### 介绍
DaXiJiZhang 是一款功能全面的个人理财管理应用，专为 Android 7.0+ (API 24+) 设计，具备账单跟踪、支付记录管理、统计分析以及云端备份功能。该应用遵循现代 Android 开发实践，使用 Kotlin、Room 数据库和 Jetpack 组件构建。

该应用遵循 Model-View-ViewModel (MVVM) 架构模式，确保数据层、业务逻辑层和 UI 层之间的职责清晰分离。这种架构选择提升了可测试性、可维护性和可扩展性，同时通过使用 LiveData 和 Coroutines 进行响应式编程，提供流畅的用户体验。

### 项目结构
```
DaXiJiZhang/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/daxijizhang/
│   │   │   ├── DaxiApplication.kt
│   │   │   ├── MainActivity.kt             # 带有 ViewPager2 导航的主 Activity
│   │   │   ├── data/                       # 数据层
│   │   │   │   ├── cache/                  # 内存缓存工具
│   │   │   │   ├── dao/                    # Room 数据库访问对象
│   │   │   │   ├── database/               # Room 数据库设置
│   │   │   │   ├── local/                  # 草稿管理
│   │   │   │   ├── model/                  # 数据实体
│   │   │   │   ├── notification/           # 数据变更通知
│   │   │   │   ├── repository/             # Repository 模式实现
│   │   │   │   └── util/                   # 数据层工具
│   │   │   ├── ui/                         # UI 层
│   │   │   │   ├── adapter/                # RecyclerView 适配器
│   │   │   │   ├── base/                   # 基础 Activity 类
│   │   │   │   ├── bill/                   # 账单管理 UI
│   │   │   │   ├── settings/               # 设置界面
│   │   │   │   ├── statistics/             # 统计可视化
│   │   │   │   ├── user/                   # 用户个人资料
│   │   │   │   └── view/                   # 自定义视图
│   │   │   └── util/                       # 工具类
│   │   └── res/                            # Android 资源
│   └── build.gradle.kts                    # 应用级构建配置
├── gradle/
│   └── libs.versions.toml                  # 版本目录
├── build.gradle.kts                        # 项目级构建配置
└── settings.gradle.kts                     # 项目设置
```
### 参数
|类别|技术|版本|用途|
|:--:|:--:|:--:|:--:|
|语言|	Kotlin	|2.0.21	|主要编程语言|
|构建系统|	Gradle with Kotlin DSL|	8.7.3 |AGP	构建配置和依赖管理|
|最低 SDK	|Android 7.0| (API 24)	|	最低支持的 Android 版本|
|目标 SDK|	Android 15| (API 35)	|	目标 Android 版本|
|架构|	MVVM + Jetpack|-	|	架构模式|
|数据库|	Room|	2.6.1	|本地数据持久化|
|协程	|Kotlinx Coroutines	|1.9.0|	异步编程|
|导航	|Navigation Component|	2.8.5	|应用内导航|
|网络	|OkHttp	|4.12.0|	用于 WebDAV 操作的 HTTP 客户端|
|PDF 生成|	iText PDF|	5.5.13.3	|PDF 导出功能|
|JSON 解析	|Gson|2.10.1	|JSON 序列化/反序列化|

### 关键目录说明
#### 数据层 (data/)
数据层负责管理所有与数据相关的操作，包括数据库交互、数据模型和业务逻辑存储库。该层确保 UI 与数据存储细节保持解耦。

- database/：包含 AppDatabase.kt，即 Room 数据库实例，它定义数据库配置并提供对 DAO 的访问
- dao/：数据访问对象，为每个实体定义 SQL 查询 - BillDao、BillItemDao、PaymentRecordDao 和 ProjectDictionaryDao
- model/：表示核心实体的数据模型类 - Bill、BillItem、BillWithItems、HeatmapData、PaymentRecord、PeriodType、ProjectDictionary 和 StatisticsData
- repository/：在数据源和应用程序其余部分之间进行中介的存储库类 - BillRepository、ProjectDictionaryRepository 和 StatisticsRepository
- cache/：缓存机制，包括用于优化数据访问的 DataCacheManager
- local/：本地存储工具，如用于管理未保存更改的 DraftManager
- notification/：DataChangeNotifier，用于向观察者广播数据更改
- util/：辅助类，如用于数据库类型转换的 DateConverter

#### UI 层 (ui/)
UI 层包含按功能区域组织的所有用户界面组件。该层使用 View Binding 进行类型安全的视图访问，并遵循 MVVM 模式，由 ViewModels 管理 UI 相关数据。

- bill/：账单管理功能，包括 BillsFragment、BillViewModel、AddBillActivity、BillDetailActivity、SearchActivity 及相应的适配器
- statistics/：统计和分析组件，包括 StatisticsFragment、StatisticsViewModel 和 StatisticsViewModelFactory
- user/：用户配置文件和设置管理，包括 UserFragment
- settings/：设置相关的 Activity - InfoSettingsActivity、DisplaySettingsActivity、DataMigrationActivity、MoreSettingsActivity、ProjectDictionaryActivity 和 PatternLockActivity
- adapter/：RecyclerView 适配器，包括用于 ViewPager2 导航的 MainFragmentAdapter
- base/：基类，如为所有 Activity 提供通用功能的 BaseActivity
- view/：自定义视图组件，包括 HeatmapView、YearlyHeatmapView、YearlyIncomeChartView、PatternLockView、ModernDatePickerDialog 和 CustomNumberPicker


#### 工具层 (util/)
工具层包含在应用程序中提供通用功能的辅助类。这些工具独立于特定功能，可以在整个代码库中重用。

- DateFormatter：处理日期格式和不同日期格式之间的转换
- ThemeManager：管理应用程序主题，包括亮/暗模式和自定义配色方案
- PatternLockManager：管理图案锁安全功能
- AutoBackupManager：处理自动数据备份功能
- WebDAVUtil：提供 WebDAV 集成以进行云备份
- BillExportUtil：处理账单数据导出为各种格式（PDF、Excel）
- ImagePickerUtil：管理从设备选择图像
- BlurUtil：为图像和背景提供模糊效果
- CrashHandler：处理应用程序崩溃和错误报告
- ActivityTransitionUtil：管理 Activity 转换动画
- StatisticsStateManager：管理统计视图状态
- ViewUtil：常见的视图相关工具
- PinyinUtil：提供中文拼音转换以进行搜索功能
