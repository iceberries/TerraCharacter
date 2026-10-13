# 登录账号默认皮肤

创建人物默认使用当前 Minecraft 登录账号名，人物名字独立。通过游戏自带的 Mojang Authlib 查询正版资料并校验签名，仅从官方 textures.minecraft.net 下载皮肤；不使用第三方皮肤服务，也不发送登录令牌。

查询在后台线程执行，八秒超时。等待期间仍可填写人物名称与难度，底图就绪前暂停保存、换装和绘制入口。成功后识别 slim／wide 并更新预览；失败时使用本地缓存或原版按登录 UUID 选取的默认皮肤。用户已选择的体型不会被异步结果重置，已有角色不会自动换肤。

下载缓存为 `<gameDir>/terra_character/account_skins/<小写登录名>.nbt`，并输出同名 PNG；六小时内直接使用缓存。缓存不包含令牌或访问 URL。PNG 支持现代 64×64 和旧版 64×32，旧版按照原版规则补齐左右肢体；基础层不透明，现代外层保留原始透明度。

人物包 `Appearance.BaseSkin` 包含 `Slim` 和 4096 个 ARGB `Pixels`，底图与 `Paint` 手绘独立。模型尺寸改变时按原版 UV 映射，换装在底图上合成后应用手绘。清除手绘恢复对应原像素；显式擦除外层不会改动底图。底图随人物备份、跨世界、多人上传与外观同步携带，服务器与其他客户端无需联网重新下载。

账号皮肤没有统一的头发、衣物、眼睛像素位置，不会套用 fire_berry 的语义掩码。可以在绘制界面的部位范围工具中框选像素，再使用已有通道染色。fire_berry 仍作为明确命名的预设保留，旧人物默认外观不变。

网络协议更新为 7，客户端和服务端应同时升级。旧人物没有 BaseSkin 字段时沿用原有资源／生成皮肤。发行包不包含开发账号下载的皮肤，缓存位于各自游戏目录。

```powershell
# 仅检查这次涉及的数据与预设
.\gradlew.bat :TerraCharacter:test --tests '*SkinBaseTest' --tests '*WardrobeTest.bundledPresetsAreValidAndRequireNoNewArt' --offline
# 隔离客户端：实际查询登录账号，验证双层、体型、绘制与持久化；完成后自动退出
.\gradlew.bat :TerraCharacter:runClient -PaccountSkinSmoke=true --offline --no-daemon
```
