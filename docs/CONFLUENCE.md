# 内置汇流兼容

从此次合并起只分发 TerraCharacter 一个 JAR。未安装 Confluence 时提供独立人物与世界流程；安装 Confluence 及其正常前置后，自动启用原兼容模块的全部功能。升级时移除旧的 TerraCharacterConfluence JAR；元数据会拒绝旧兼容模组与新版核心同时加载，防止重复注册。

保留魔力、永久增益、扩展背包、饰品、猪猪储蓄罐、保险箱与渔夫任务的归属适配；世界选项、特殊种子、难度、掉钱、复活与生命 HUD 的兼容接点保持不变。八个适配器和世界选项仍使用 `terra_character_confluence:*` 数据标识，已有存档无需重写。此命名空间不再表示一个独立安装的模组。

汇流 1.2.11 新增的灵魂、灵魂技能和虚空保险库也按人物保存，新增 ID 分别为 `terra_character_confluence:soul`、`terra_character_confluence:soulcerer_skill`、`terra_character_confluence:void_vault`，共十一项适配器。旧人物没有这些记录时使用全新默认数据，不继承登录账号或另一个人物的进度。灵魂附件会自动同步网络，因此登录前使用 NeoForge 的非同步反序列化路径安装，连接建立后再同步，避免加载阶段访问尚未创建的玩家连接。

单人内置服务器异常断开时返回人物选择页，保留原始断开详情；实际多人连接继续沿用服务器列表。若存档保护检查中止会话，最后有效人物存档保留，存在恢复标记时应从人物选择页恢复会话，不自动回滚正在使用的世界。

初始物品统一沿用主模组 `confluence:achievements/new_world` 的奖励入口，领取判定改为人物侧。`terra_character_confluence:quests` 的 `InitialItemsGranted` 随人物、客户端联机快照及死亡复制保存，适配器 ID 和版本保持不变。首次完成仍执行原版奖励与主模组成就事件；已领取的人物在其他世界完成该成就时，只更新当地成就显示，不再重复执行奖励或初始物品事件，不逐件检查、删除背包物品。其他成就进度仍按人物与世界保存。旧人物已有该适配器但缺少新字段时按已领取迁移，避免升级后补发；新人物首次保存明确写入 false，真正领取后改为 true。未启用独立人物会话时保持主模组原行为。

兼容代码的 Java 包为 `org.confluence.terra_character.compat.confluence`，其下包含 `client`、`integration`、`world` 和 `mixin`。Java 包名与持久化命名空间分离：旧适配器 ID、世界选项键、资源翻译键及旧兼容模组的冲突检查均保持原样。反射启动入口和 Mixin 配置指向新包，旧包不再包含实现类。

兼容入口在检测到 `confluence` 后才通过延迟加载调用。兼容类不再使用自动事件订阅注解；公共监听器由入口注册，客户端监听器和预览能力仅在物理客户端注册。Mixin 配置使用独立插件，在模组发现阶段依据 Confluence 的存在决定是否应用，缺少汇流时不解析其目标类。

运行依赖仍仅必需 Minecraft／NeoForge；构建时以 `compileOnly` 引用汇流及其前置，发行 JAR 不嵌入前置类或 JAR。Gradle 子项目 TerraCharacterConfluence 已移除，源码、资源和单元测试均位于 TerraCharacter 中。

```powershell
# 独立开发运行
.\gradlew.bat :TerraCharacter:runClient --offline --no-daemon
# 加载汇流，复用 ConfluenceOtherworld/run 目录
.\gradlew.bat :TerraCharacter:runClient -PwithConfluence=true --offline --no-daemon
# 仅检查条件加载，不进入世界，检查结束自动退出
.\gradlew.bat :TerraCharacter:runClient -PintegrationSmoke=true --offline --no-daemon
.\gradlew.bat :TerraCharacter:runClient -PwithConfluence=true -PintegrationSmoke=true --offline --no-daemon
# 仅验证单人进入、三项新增数据的保存恢复和断开返回，使用独立临时世界
.\gradlew.bat :TerraCharacter:runClient -PwithConfluence=true -PlocalSessionSmoke=true --offline --no-daemon
# 仅检查初始奖励：新人物、换世界、人物保存、旧存档迁移和死亡复制
$env:TERRA_CHARACTER_INITIAL_ITEMS_ONLY = 'true'
.\gradlew.bat :TerraCharacter:runClient -PwithConfluence=true -PlocalSessionSmoke=true --offline --no-daemon
Remove-Item Env:TERRA_CHARACTER_INITIAL_ITEMS_ONLY
# 相关单元测试与发行包
.\gradlew.bat :TerraCharacter:test --tests '*ConfluenceWorldOptionsTest' :TerraCharacter:jar --offline
```

`withConfluence` 只控制开发运行的模组组合，正式安装由 NeoForge 检测实际安装情况，无需配置。原 `characterSmoke`、`skinSmoke`、`menuSmoke` 和 `multiplayerSmoke` 专项仍可与该参数组合；所有隔离目录统一位于 TerraCharacter/build，检查类不打入发行包。双方联机仅需安装匹配的 TerraCharacter 和对应的汇流模组组合，客户端人物冲突优先规则不变。
