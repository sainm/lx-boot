# 心理测评 iPhone/iOS 原生端实施 Prompt

## 使用说明

将下面代码块中的内容完整复制给 Codex、GitHub Copilot 或其他代码代理，用于设计、实现和验证心理测评 iPhone/iOS 原生应用。该 Prompt 默认采用 Swift、SwiftUI 和现代 iOS 工程实践。

```text
请为当前心理测评系统设计并实现 iPhone/iOS 原生被测者端。

这不是 UI Demo。应用必须连接当前真实后端 API，并与 Web、Android 和微信小程序共享用户、任务、答卷、评分、报告、预约、通知和权限数据。

目标业务闭环：

启动与安全会话恢复 → 登录/SSO → 首页待办 → 查看测评任务 → 答题与保存 → 幂等提交 → 查看个人报告 → Push 通知和深链 → 咨询预约 → 个人资料与设备安全。

一、技术基线

优先使用：

- Swift
- SwiftUI
- async/await
- URLSession
- Codable
- NavigationStack
- Swift Charts（系统版本允许时）
- XCTest 和 XCUITest
- Keychain
- BackgroundTasks（确有需要时）
- UserNotifications

最低支持版本需要结合当前用户设备分布确定，并在实施前说明。不要无理由引入大型第三方网络、状态管理或 UI 框架。

工程结构至少包含：

- App
- Core
- Networking
- Authentication
- SecureStorage
- Features
- DesignSystem
- Localization
- Notifications
- Persistence
- Diagnostics
- UnitTests
- UITests

采用清晰、可测试的状态和依赖注入方式，避免所有业务都堆在单一 View 或单一 ObservableObject 中。

二、环境和配置

支持：

- Development
- Staging
- Production

不同环境配置：

- API Base URL
- Universal Link 域名
- 日志等级
- Push 环境
- 功能开关

要求：

- 密钥和服务端凭据不得写入源码或 Info.plist
- Release 默认只允许 HTTPS
- 禁止任意网络访问例外和不受控明文 HTTP
- 配置文件不能包含开发者个人路径或账号
- 建立可重复的本地、CI 和 Archive 构建流程

三、登录、Token 和设备安全

接入现有认证 API，支持后端当前实际启用的方式：

- 用户名密码
- OIDC/学校 SSO
- 其他后端已启用的登录方式

要求：

- Access Token 和 Refresh Token 存入 Keychain
- 禁止存入 UserDefaults、普通文件或日志
- 正确处理 Token 刷新并避免并发刷新风暴
- 刷新失败后清理会话并返回登录页
- 退出登录调用后端注销、清理 Keychain、缓存和用户草稿
- 登录时发送稳定设备 ID、设备类型、系统版本和 App 版本
- 获取 APNs Token 后调用设备注册接口
- APNs Token 变化时更新后端
- 支持服务端撤销设备和会话
- 切换账号后不能读取上一账号的草稿、报告或通知
- App 进入后台时根据隐私设置遮挡敏感页面快照
- 支持 Face ID/Touch ID 二次保护敏感报告（作为可配置增强，不替代服务端认证）

四、页面与导航

至少实现：

1. 启动和会话恢复
2. 登录/SSO 回调
3. 隐私政策、用户协议和知情同意
4. 首页
5. 我的任务
6. 答题
7. 提交确认与提交结果
8. 我的报告
9. 报告详情
10. 咨询师列表和时间选择
11. 我的预约
12. 通知中心
13. 个人资料
14. 语言、隐私和账号安全设置
15. 加载、空状态、错误、无权限和离线页面

首页优先展示：

- 当前最需要处理的任务
- 即将截止任务
- 最近报告
- 即将到来的预约
- 未读通知

使用 TabView 承载高频一级入口，使用 NavigationStack 管理业务详情。禁止在多个页面自行维护互相冲突的导航状态。

五、答题体验

答题页面需要：

- 一次聚焦一个问题
- 清晰、不过度施压的进度
- 上一题、下一题和题号导航
- 单选、多选、文本、滑块、矩阵、选项加文本题型
- 必答校验
- Dynamic Type 下保持可用
- VoiceOver 可识别题目、选项、必答状态和进度
- 支持键盘避让和滚动定位
- 显示保存中、已保存、未同步和保存失败状态
- App 切后台、被系统终止或崩溃后可恢复未提交内容

本地草稿要求：

- 仅保存恢复必需的数据
- 按 userId/安全账号标识、taskId 和量表版本隔离
- 敏感草稿使用 Data Protection 或加密存储
- 服务端草稿为跨设备权威来源
- 合并前校验 answerSheetId 和 versionNo
- 冲突时提示用户选择重新加载或保留本地未同步内容

提交要求：

- 由后端验证任务分配、时间、状态、量表版本和答案
- 同一逻辑提交使用稳定 Idempotency-Key
- 超时后先查询服务器结果，不生成新提交造成重复报告
- 提交时禁用重复按钮并显示明确状态
- 成功后删除对应本地草稿
- 已完成且不允许重测的任务不能进入空白答题页
- 匿名任务不得展示个人报告或个人风险结果

六、任务状态

正确展示并处理：

- 未开始
- 进行中
- 草稿
- 已完成
- 已逾期但允许提交
- 已逾期且禁止提交
- 已关闭
- 允许重测

客户端展示不能替代后端校验。App 时间可能不准确，关键状态以服务端返回为准。

七、报告与图表

个人报告至少包含：

- 量表名称和版本
- 测评时间
- 总分
- 维度分
- 风险等级
- 结果解释
- 建议
- 图表
- 免责声明和求助信息

要求：

- 图表之外必须提供文字结论
- 颜色之外使用文字和图标表达风险
- 支持 VoiceOver 描述图表摘要
- 支持 Dynamic Type
- 不显示内部预警或干预字段
- 报告访问必须由服务端校验本人权限
- 匿名任务不显示个人报告
- 截图、分享和导出前提示心理报告属于敏感信息
- 分享内容不得默认包含具体风险和得分

八、咨询预约

实现：

- 咨询师列表
- 可预约日期与时间
- 创建预约
- 我的预约
- 取消
- 改期（后端支持时）
- 已确认、已完成、已取消、已失约状态
- 日历提醒（用户明确授权后）

要求：

- 正确处理并发名额占用
- 创建后刷新服务端状态
- 日期时间按用户 locale 和时区显示
- 日历事件不写入敏感测评详情
- 预约变更通过站内通知和 Push 提醒

九、APNs、通知和深链

接入：

- APNs Token 注册
- Push 权限申请
- 前台通知展示
- 后台通知处理
- 点击回执
- 送达/接收回执（平台允许范围内）
- Universal Links
- Custom URL Scheme 作为兼容回退

通知类型至少覆盖：

- 任务分配
- 任务截止提醒
- 报告生成
- 预约创建和变更
- 必要的服务通知

要求：

- 在有上下文的业务时机申请通知权限
- 拒绝通知权限不影响基础业务
- Push Payload 不包含 Token、完整报告、具体风险详情或敏感答案
- 点击通知后进行登录和对象权限检查，再导航到目标页面
- 无效、过期或无权限深链进入安全错误页
- 上报通知 received/clicked 状态
- 深链路由有版本兼容和未知路径处理

十、中日英三语

支持：

- 简体中文：`zh-Hans`，与后端 `zh-CN` 映射
- 日语：`ja`
- 英语：`en`

要求：

- 使用 String Catalog（`.xcstrings`）或当前 Xcode 推荐方式
- 页面、按钮、错误、通知、隐私说明和报告完整国际化
- 禁止在 SwiftUI View 和业务代码中硬编码自然语言
- 三种语言 key 完全一致，并由测试或脚本检查
- 使用 FormatStyle 格式化日期、时间、数字和百分比
- 切换 App 语言不能丢失答题进度
- 日语和英语长文本不能造成截断、重叠或不可点击
- 支持 Dynamic Type 后仍保持三种语言基本可用
- 建立心理学术语表，并标记专业译文审核状态

十一、设计系统和可访问性

视觉方向：

- 专业、可信、温和、克制
- 低饱和蓝色或蓝绿色主色
- 浅色中性背景
- 风险颜色只用于必要区域
- 避免娱乐化、过度渐变和刺激性动画

建立统一：

- Color Token
- Typography
- Spacing
- Corner Radius
- Shadow
- Button
- Card
- Form Field
- Status Badge
- Empty/Error/Loading State

必须支持：

- Light Mode
- Dark Mode
- Dynamic Type
- VoiceOver
- Reduce Motion
- Increase Contrast
- Differentiate Without Color
- 触控区域尺寸
- 键盘和 Switch Control 的基本可操作性

十二、隐私和数据保护

- 使用 Keychain 保存凭据
- 使用 iOS Data Protection 保护敏感缓存
- 日志中脱敏 userId、报告、答案、Token 和联系方式
- 崩溃报告不得携带原始答题内容
- App 切后台时保护敏感页面快照
- 缓存设置过期和清理策略
- 删除账号或退出登录时清理本地个人数据
- 不使用广告 SDK 或未经批准的用户画像 SDK
- 分析埋点只记录业务事件，不记录答案内容和具体心理结果
- 隐私清单、权限用途说明和 App Store Privacy 信息保持一致

十三、网络和错误恢复

统一 API Client，处理：

- JSON 解码
- 认证 Header
- locale Header
- Token 刷新
- 超时
- 重试
- 后端业务错误
- 网络不可用
- 服务端维护
- 请求取消

重试要求：

- GET 可采用有限退避重试
- 非幂等 POST 不得自动盲目重试
- 提交答卷依赖 Idempotency-Key
- 预约创建依赖服务端幂等或显式确认
- 错误提示面向用户，不直接显示内部错误代码和堆栈
- 所有失败页面提供明确下一步

十四、性能和稳定性

- 控制启动时间
- 避免主线程执行网络和大 JSON 处理
- 列表和图表按需加载
- 图片缓存有边界
- 避免 SwiftUI 状态导致的重复请求和重复导航
- 检查内存泄漏
- App 进入后台时正确保存草稿状态
- 支持系统终止后的安全恢复
- 使用 MetricKit 或等效方式观察崩溃、卡顿和启动指标（不收集敏感数据）

十五、测试和验收

必须执行：

- Swift 编译和静态检查
- XCTest 单元测试
- API 契约测试
- Keychain 与会话测试
- 中日英资源一致性测试
- XCUITest
- 多尺寸 Simulator 测试
- 真机测试
- VoiceOver 和 Dynamic Type 检查
- 弱网、离线和后台恢复测试
- APNs 和 Universal Link 测试
- Archive 构建

至少验证：

1. 用户首次登录、Token 刷新、退出和会话撤销。
2. 中文、日语、英语分别完成登录、答题、报告和预约。
3. PHQ-9、GAD-7 和至少一种复杂题型量表完整作答。
4. App 进入后台或被终止后恢复未完成答题。
5. 多设备修改草稿时正确处理版本冲突。
6. 提交超时和重复点击只生成一个结果。
7. 任务开始前、截止后允许提交、截止后禁止提交边界正确。
8. 匿名任务不出现个人报告和风险信息。
9. APNs 通知点击和 Universal Link 跳转正确。
10. 无权限或跨租户对象 ID 被后端拒绝。
11. 切换账号后本地数据完全隔离。
12. VoiceOver 和超大字体下可完成主要答题流程。

十六、发布准备

提供：

- Bundle ID 与 Signing 配置说明
- Development/Staging/Production Scheme
- APNs 配置说明
- Associated Domains 配置
- Privacy Manifest
- 权限用途文案
- App Store 隐私信息清单
- TestFlight 流程
- Archive 和上传步骤
- 版本号和构建号策略
- 发布检查清单

不要提交真实证书、Provisioning Profile、私钥或服务端密钥。

十七、交付方式

先输出：

- 当前后端 API 与 iOS 需求映射表
- 可复用、需扩展和缺失的接口
- iOS 架构和模块设计
- 页面和导航清单
- 本地敏感数据清单
- APNs/Universal Link 配置清单
- 实施阶段和测试计划

然后直接实施。

完成后报告：

- 新增和修改文件
- 需要人工完成的 Apple Developer 配置，但不要包含真实密钥
- 已通过的业务 Case
- 单元测试、UI 测试、真机和 Archive 结果
- 未完成能力和剩余风险
- 是否达到可演示、内部测试、TestFlight、App Store 发布四个等级

只有真实登录、答题、保存、提交、报告、预约、Push、深链、隐私、三语和异常恢复全部接通，才算 iPhone 端业务闭环。
```
