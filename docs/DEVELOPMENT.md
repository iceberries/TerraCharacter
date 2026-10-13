# 仓库与本地子模块

TerraCharacter 的源码由独立 Git 仓库维护，在汇流来世工作区内同时作为 Git 子模块和 Gradle 子项目使用。模组运行时不强制依赖汇流；构建时仍通过工作区中的其他子项目编译内置兼容代码。

## 工作区要求

- Java 21。
- 汇流来世工作区中包含 `ConfluenceOtherworld`、`Confluence-Magic-Lib`、`TerraCurio`、`TerraFurniture`，以及位于根目录的 `TerraCharacter`。
- 根 `settings.gradle` 包含 `TerraCharacter`，根构建配置提供 NeoForge ModDev 插件版本；Minecraft、NeoForge、Parchment、Curios 等版本沿用根 `gradle.properties`。

在尚未登记子模块的工作区，使用以下命令添加后，将 `TerraCharacter` 加入根 `settings.gradle` 的项目列表：

```powershell
git submodule add -b main git@github.com:iceberries/TerraCharacter.git TerraCharacter
```

若主仓库已包含该子模块记录，使用 `git submodule update --init TerraCharacter`。私有仓库需要当前 GitHub 账号有访问权限。

## 构建与运行

以下命令均在汇流来世工作区根目录执行，使用根目录的 Gradle Wrapper。本仓库没有独立构建所需的父项目配置，不应在单独克隆的目录直接运行 Gradle。

```powershell
.\gradlew.bat :TerraCharacter:build --no-daemon
.\gradlew.bat :TerraCharacter:runClient -PwithConfluence=false --no-daemon
.\gradlew.bat :TerraCharacter:runClient -PwithConfluence=true --no-daemon
```

产物位于 `TerraCharacter/build/libs`。正常发布只安装 TerraCharacter 的 JAR；汇流兼容已内置，不安装旧兼容模组。

## 提交与更新

在 `TerraCharacter` 中提交、推送源码；子模块工作区可切换到 `main` 开发。提交人物存档、账号皮肤缓存、运行目录、日志和构建产物没有必要，这些内容由本仓库自己的 `.gitignore` 排除。

主仓库记录的是子模块的提交 SHA。更新子模块源码后，主仓库会显示子模块指针变化；如需共享该变化，需要另行提交并推送主仓库。本次配置只在本地登记，不提交或推送汇流来世主仓库。

第三方界面素材来源见 [UI_STYLE.md](UI_STYLE.md)，保留皮肤的来源元数据。源码许可见仓库根目录 `LICENSE`。
