# 内置汇流兼容

从此次合并起只分发 TerraCharacter 一个 JAR。未安装 Confluence 时提供独立人物与世界流程；安装 Confluence 及其正常前置后，自动启用原兼容模块的全部功能。升级时移除旧的 TerraCharacterConfluence JAR；元数据会拒绝旧兼容模组与新版核心同时加载，防止重复注册。

保留魔力、永久增益、扩展背包、饰品、猪猪储蓄罐、保险箱与渔夫任务的归属适配；世界选项、特殊种子、难度、掉钱、复活与生命 HUD 的兼容接点保持不变。八个适配器和世界选项仍使用 `terra_character_confluence:*` 数据标识，已有存档无需重写。此命名空间不再表示一个独立安装的模组。

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
# 相关单元测试与发行包
.\gradlew.bat :TerraCharacter:test --tests '*ConfluenceWorldOptionsTest' :TerraCharacter:jar --offline
```

`withConfluence` 只控制开发运行的模组组合，正式安装由 NeoForge 检测实际安装情况，无需配置。原 `characterSmoke`、`skinSmoke`、`menuSmoke` 和 `multiplayerSmoke` 专项仍可与该参数组合；所有隔离目录统一位于 TerraCharacter/build，检查类不打入发行包。双方联机仅需安装匹配的 TerraCharacter 和对应的汇流模组组合，客户端人物冲突优先规则不变。
