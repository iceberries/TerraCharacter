# 环境状态适配

环境扫描在模组与注册表加载完毕后、第一次界面初始化或服务器启动时执行，早于人物创建及载入。此时 NeoForge 主事件总线已启用，其他模组可以声明数据归属。人物创建时把当前环境清单写入人物包 `Environment`，记录模组版本、属性、附件、注册表条目及适配器归属。

## 人物状态

- `character_portal:attributes` 保存属性基础值和永久修饰符。新人物使用模组修改后的玩家默认属性，装备及药水临时修饰符由游戏重新计算。缺失属性或不再分配给玩家的属性会拒绝有损还原。
- 已由专用 `CharacterDataAdapter.legacyAttachmentIds()` 声明的附件优先使用专用适配器。
- 其余可序列化 NeoForge 附件自动适配，直接使用原模组的 `IAttachmentSerializer`，支持 Codec、`INBTSerializable` 及非 CompoundTag 数据。只捕获玩家已有的附件，不会遍历构造区块或世界专用附件。
- 未声明归属的附件默认按“人物＋世界”保存。新人物及首次进入其他世界时清除旧实例，由原模组按需提供默认值。死亡复制规则仍由 NeoForge 和原模组控制。
- `character_portal:persistent_data` 保存玩家 `NeoForgeData`，默认按“人物＋世界”隔离。
- 附件载入不依赖网络连接，连接建立后通过 NeoForge 同步。

模组可监听主事件总线上的 `DiscoverStateAdaptersEvent`，声明附件随人物跨世界移动：

```java
@SubscribeEvent
public static void ownership(DiscoverStateAdaptersEvent event) {
    event.attachmentScope(
        ResourceLocation.fromNamespaceAndPath("your_mod", "skills"),
        CharacterDataAdapter.Scope.CHARACTER);
}
```

特殊规则仍可注册专用适配器。`excludeAttachment(id)` 禁用某个自动适配器；排除后仍会执行原有的缺失归属检查，不会悄悄丢弃数据。自动适配器 ID 包含归属范围；修改已有数据的范围需要显式迁移。

## 世界状态与生成

世界创建界面加载数据包后扫描实际注册表，在点击创建时再次扫描，覆盖数据包切换及模组回调后的结果。`<world>/data/character_portal/environment.nbt` 的 `Creation` 保存：

- 当前环境及数据包注册表条目，包括生物群系、维度、噪声、结构和特征等。
- 用 `WorldGenSettings` Codec 编码的实际种子、生成选项、选定维度和生成器。
- 实际数据包、功能标记和游戏规则。

世界启动后的 `Runtime` 记录当前注册表、世界选项、规则及已加载维度的时间、天气和出生点。更新运行清单会保留最初的创建记录。

实际 `level.dat`、模组 SavedData 和区块仍由 Minecraft 与原模组读写；切换人物不会回滚世界进度或替换生成器。没有命名预设的自定义世界记为 `character_portal:custom`，并保留其完整生成 Codec。

自动适配覆盖标准属性、注册表、持久玩家 NBT、NeoForge 附件和生成 Codec。未提供序列化接口的私有字段、外部文件和自定义网络协议仍需专用适配器。环境清单不会把所有注册条目赋给玩家，也不会改写全局模组配置。

## 验证

```powershell
.\gradlew.bat :CharacterPortal:build --offline
.\gradlew.bat :CharacterPortal:runStateEnvironmentTest -PstateEnvironmentSmoke=true --offline
```

运行测试位于独立目录 `CharacterPortal/build/state-environment-smoke-run`，检查自动发现、专用适配器优先、Codec 还原、人物隔离、临时修饰符去重和世界生成 Codec。测试注册项不进入发行 JAR。
