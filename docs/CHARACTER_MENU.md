# 人物菜单

人物卡片占据主要区域，每页一排，最多并排显示三个人物。卡片保持原三列布局约 7∶6 的宽高比，宽高等比例放大至可用空间内，居中排列；窄窗口减少列数，不单独拉伸宽度或高度。翻页按钮位于左右两侧。鼠标悬停人物区域时，滚轮翻页；也可使用 PageUp／PageDown。卡片继续支持双击确认、右键拖动旋转和信息框内的星标。

底部功能轮盘占两行按钮的高度。创建人物、开始游戏、回收站、删除人物和返回使用图标卡片表示，中间卡片放大；轮盘区域滚轮或聚焦后的方向键循环切换功能。点击侧边卡片只选中功能，点击中间卡片或按 Enter／空格才执行，滚动不会直接删除或进入游戏。名称和操作提示在悬停／聚焦时显示。未选人物时，开始游戏与删除人物不可执行。

存在中断会话时，额外提供恢复会话卡片；回收站使用同款轮盘提供恢复、永久删除与返回。删除、永久删除和会话恢复保留确认步骤。

轮盘默认提供代码绘制的像素图标，后续可用资源包覆盖 `assets/terra_character/textures/gui/sprites/actions/<名称>.png`，不需要修改 Java。名称为 `create`、`play`、`trash`、`delete`、`back`、`restore`、`recover`。PNG 可使用透明背景，图像由 GUI 精灵图集缩放到卡片图标区域，资源重载后生效。

菜单专项检查使用隔离目录，仅测试轮盘、翻页、人物预览和布局，结果与截图位于 `TerraCharacter/build/menu-smoke-run`：

```powershell
$env:TERRA_CHARACTER_CAROUSEL_ONLY = 'true'
.\gradlew.bat :TerraCharacter:runClient -PwithConfluence=true -PmenuSmoke=true --offline --no-daemon
Remove-Item Env:TERRA_CHARACTER_CAROUSEL_ONLY
```
