# 世界作弊与指令权限

创建人物世界时保留创建界面上的有效作弊选择。原版创造模式在玩家未明确设置开关时默认开启作弊，因此必须先读取 `isAllowCommands()`，再应用人物系统的非极限生存世界规则，最后还原该作弊选择。

权限仍由 Minecraft 服务端按当前世界、登录账号和 OP 设置计算。人物包不保存或携带作弊授权、OP 或权限等级。载入已有世界不重写 `level.dat` 的 `allowCommands`，不自动开启局域网作弊，也不调用全员授权。人物进入时的游戏模式规则不等同于指令授权。

已被旧版本错误保存为关闭的世界不会自动改为开启：现有记录不足以可靠判断当时的选择。需要修复时，应先备份，再由玩家明确修改指定世界。

专项检查只使用 `TerraCharacter/build/permission-smoke-run`。覆盖实际创建界面的显式开关与创造模式默认值、原生创建按钮、开启与关闭作弊世界的权限和指令树、保存重进、同一人物切换世界，以及非房主账号不继承房主权限。

```powershell
.\gradlew.bat :TerraCharacter:runClient -PpermissionSmoke=true -PwithConfluence=true --offline --no-daemon
```

结果写入隔离目录的 `permission-result.txt`。测试代码不打入发行 Jar。
