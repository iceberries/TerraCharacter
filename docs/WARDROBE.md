# 数据驱动的原版皮肤换装

换装只操作原版 64×64 RGBA 皮肤图集；基础层与外层沿用 Minecraft 的 UV，不添加模型、渲染层或第三层皮肤。
本次不制作部件美术。新人物默认使用当前 Minecraft 登录账号的皮肤，另保留 fire_berry 整体预设。其他部位的选择列表在安装资源包后出现。

## 导入自己的服装

通过客户端资源包导入；目前没有直接导入任意 PNG 并自动识别服装部位的按钮。最小资源包目录如下，`my_clothes` 是自定义命名空间：

```text
我的服装包/
  pack.mcmeta
  assets/my_clothes/wardrobe/parts/coat.json
  assets/my_clothes/textures/wardrobe/coat.png
  assets/my_clothes/wardrobe/presets/casual.json  （可选）
```

Minecraft 1.21.1 的 `pack.mcmeta`：

```json
{"pack":{"pack_format":34,"description":"我的 TerraCharacter 服装"}}
```

`coat.png` 必须是 **64×64 RGBA PNG**，按原版皮肤 UV 绘制。下面的 `coat.json` 示例仅替换躯干正面的基础层与外层；添加袖子、背面和侧面时，为各 UV 区域增加片段。`rect` 同时指定从 PNG 读取的位置与写入皮肤的位置，不能用裁剪后的衣服小图代替完整图集。

```json
{
  "name":"我的外衣",
  "slot":"outerwear",
  "fragments":[
    {"rect":[20,20,8,12],"texture":"my_clothes:textures/wardrobe/coat.png","tint":"my_clothes:cloth"},
    {"rect":[20,36,8,12],"texture":"my_clothes:textures/wardrobe/coat.png","tint":"my_clothes:cloth","blend":"replace"}
  ]
}
```

白色／灰色衣服像素适合通过 `tint` 快速染色。省略 `tint` 则保留 PNG 原色。外层的 `replace` 会清除该矩形内原有外层的透明对应位置；按需求改为 `over` 可保留原有像素。

如需在“整体预设”中一键应用搭配，额外添加 `casual.json`：

```json
{
  "name":"休闲搭配",
  "body":"terra_character:slim",
  "hair":"terra_character:account",
  "parts":{"outerwear":"my_clothes:coat"},
  "colors":{"my_clothes:cloth":"82AACC"}
}
```

`hair` 显式写 `terra_character:account` 使用当前登录账号皮肤。部件自身的 `fragments.rect` 就是覆盖像素位置信息；整体预设还可通过本文末尾的 `regions` 指定染色部位范围。

将文件夹放入游戏的 `resourcepacks` 目录，在“选项 → 资源包”启用，然后重新打开换装界面，在“外衣”分类中浏览“我的外衣”。当前汇流开发运行目录为 `ConfluenceOtherworld/run/resourcepacks`。也可打包成 ZIP，但 ZIP 根目录必须直接包含 `pack.mcmeta` 和 `assets`。修改文件后按 F3+T 重载资源；确认换装并保存人物后才保存选择。联机玩家需安装同一资源包。

## 资源路径与标识

- `assets/<namespace>/wardrobe/parts/<path>.json` → 部件 ID `<namespace>:<path>`。
- `assets/<namespace>/wardrobe/presets/<path>.json` → 预设 ID `<namespace>:<path>`。
- `name` 是翻译键，也可以直接写显示文字。相同 ID 遵循资源包优先级；F3+T 重载会更新定义并清理皮肤缓存。
- 资源仅由客户端加载。服务器保存部件 ID、颜色、手绘像素。联机双方需要相同资源包才能看到一致效果，不自动传输资源包。
- 缺失/无效资源不会删除人物中保存的选择；缺失部件退回基础皮肤，错误定义记录日志并跳过。部件 ID 与 slot 不符时跳过。

## 部位与组合服装

`skin`、`eyes`、`mouth`、`skin_detail`、`underwear`、`socks`、`pants`、`shoes`、`gloves`、`hair`、`outerwear`、`overalls`、`outfit`。

`outfit` 占用内衣、外衣、裤子、鞋子；`overalls` 占用内衣、外衣、裤子，保留鞋袜。选择某个分件会移除占用该部位的组合服装，选择组合服装会移除冲突分件。袜子和手套独立。冲突的整体预设被拒绝。
槽位是编辑分类，不是新增的渲染层。UV 范围决定实际修改哪些像素；资源作者应让片段落在所属部位。多个雀斑/红晕效果可放入一个 `skin_detail` 部件的多个片段。

## 像素部件

以下为接口示例，不作为正式美术资源内置：

```json
{
  "name": "example.part.eyes",
  "slot": "eyes",
  "fragments": [
    {"rect": [10, 12, 1, 1], "pixels": ["FFFFFFFF"], "tint": "terra_character:eyes"},
    {"rect": [13, 12, 1, 1], "pixels": ["FFFFFFFF"], "tint": "terra_character:eyes"}
  ]
}
```

每个片段必须有 `rect: [x,y,width,height]`，坐标使用原版皮肤图集，不能越过 64×64 边界。像素源二选一：

- `pixels`：按行排列的 `AARRGGBB` 十六进制字符串，数量等于 width×height。
- `texture`：例如 `example:textures/wardrobe/coat.png`，必须是 64×64 PNG。读取该 PNG 中与 rect 相同坐标的像素。未涉及的 UV 不修改。

可选字段：

- `body`：`any`（默认）、`slim`、`wide`。胳膊 UV 不同的资源提供两个对应片段；不做有损自动拉伸。
- `tint`：RGB 通道 ID。源 RGB 乘以通道 RGB，源 alpha 保留；白色像素可染色，灰色像素可保留明暗。未提供颜色的通道默认白色。
- `blend`：`over`（默认，alpha 合成）或 `replace`（覆盖）。`replace` 的全透明像素可清除原版外层的对应像素；基础层始终保持不透明，透明源保留原基础像素。

写入顺序固定为：基础皮肤 → skin → eyes → mouth → skin_detail → underwear → socks → pants → shoes → gloves → hair → outerwear → overalls → outfit → 手绘修改。
每个部件内部按 fragments 顺序执行。基础层、外层的位置直接通过原版 UV 识别，未使用的 UV 不绘制。手绘外层擦除标记保留原语义。
更换部件不会自动擦除手绘像素；若手绘遮住部件，可在画笔中重置对应区域。应用整体预设会清空手绘，界面明确提示。

### 基础皮肤快速染色

账号皮肤默认提供头部、躯干、手臂、腿部四个解剖范围通道，覆盖基础层和外层，保留原像素明暗及透明度。参考色从下载的原始皮肤计算；未调整或重置通道时保持原样，不将任意皮肤自动识别为衣服、眼睛等语义部位。精细染色可通过绘制界面的部位框选覆盖范围。装备对应的换装部件后使用部件的染色通道。

内置 fire_berry 现在提供皮肤、瞳孔、嘴巴、肤质、内衣、袜子、裤子、鞋子、手套、头发和外衣的独立像素通道。连体衣／背带裤分类汇总对应分件通道。创建人物可直接选择通道；换装页按分类筛选，调色区位于部件列表下方，右侧为独立模型预览。

`assets/<namespace>/wardrobe/dyes/<path>.json` 定义原皮肤像素蒙版，资源包可覆盖同路径：

```json
{"skin":"terra_character:fire_berry","channels":[
  {"id":"example:hair","slot":"hair","reference":"B8BBD4","pixels":[8,9,10]}
]}
```

`pixels` 为原始 slim 64×64 图集的线性索引 `y*64+x`，同时支持基础层和外层，通道之间不能重叠。wide 体型按基础皮肤相同 UV 转换映射。`reference` 为通道原色；未调色及恢复原色时完全保留原始像素。调色按参考色转换色相、饱和度、明度，保留各像素的明暗差异和 alpha。蒙版仅针对已有内置底图，不尝试自动识别任意 PNG 的语义部位。

基础通道使用 `terra_character:base_<slot>`，避免旧存档中原有六个颜色字段改变默认皮肤。装备对应部件后改用该部件自己的片段染色通道。顺序为基础底图染色 → 换装片段 → 手绘修改；手绘覆盖始终优先，恢复原色不清除手绘。只保存颜色通道值，沿用现有存档和联机协议，无新增模型层。

## 整体预设

```json
{
  "name": "example.preset.casual",
  "body": "terra_character:slim",
  "hair": "terra_character:fire_berry",
  "colors": {"terra_character:skin": "DFAF88", "terra_character:eyes": "3377AA"},
  "parts": {"eyes": "example:eyes", "outerwear": "example:coat"}
}
```

body/hair 未指定时沿用默认 slim/fire_berry；colors 是 RRGGBB，parts 选项可省略。预设替换整个外观草稿，保留人物名称和难度，确认换装后才回传父界面。取消不保存。自定义通道应使用自己的 namespace。
存档 Appearance 新增可选 Parts Compound；旧存档缺少该字段时为空选项。未知资源 ID 保留，未知槽位/非法冲突拒绝。联机协议升级到 5，避免旧客户端静默丢弃新外观。

## Java 接口

`Wardrobe.equip(slot, id)` 返回不可变新选择并处理占位；id 为 null 表示取消。`CharacterAppearance.withWardrobe` / `withPaint` 保留其余外观数据。
`WardrobeDefinitions` 负责 JSON 结构校验；`WardrobeResources` 负责资源包加载；`WardrobePixels` 将片段写入已有皮肤；`CharacterSkins.image` 是预览、游戏内皮肤和画笔共用的合成入口。

## 选择界面

捏人主界面使用分档难度滑块和体型下拉框，不再提供旧的基础发型栏。换装页面左侧为部位分类，中间为可滚动的部件／整体预设列表，右侧保留模型与内嵌调色区。RGB 三通道滑块、饱和度／明度取色板、色相条和十六进制代码由同一组件提供，绘制皮肤也直接内嵌该组件。显示内置基础皮肤及当前体型／已装备部件实际支持的通道；未调整的通道保留原色。换装界面按当前分类筛选通道，创建人物界面提供全部可用通道。

模型右键拖动后保持手动方向，左键恢复平滑鼠标跟随，双击按当前角度识别部位并切换对应分类／绘制面。分类对应像素使用 20%～100% 正弦透明度提示；提示不写入皮肤。绘制时仅以当前层为主：二维画布中的透明外层显示独立棋盘格，不叠加基础层；三维预览将非编辑层降为灰色低透明度参照，外层编辑时保持外层显示。

## 人物自定义染色范围

绘制皮肤的工具下拉框选择“部位框选”，再选择像素所属部位。在当前身体面与编辑层上，左键拖框添加像素，右键拖框移除像素；重复框选可组成不连续区域。已有范围使用青色覆盖与金色描边，拖框预览添加为金色、移除为红色。支持单格、当前面清空、恢复整个部位的默认范围，以及独立的 32 步撤销/重做。显示全部时仍操作提示所示的编辑层。

自定义范围以 slim 规范 UV 存入 Appearance.DyeMasks，wide 体型映射同一规范坐标。新增像素优先归属于新部位，避免其他部位重复染色；空数组明确表示关闭该部位的范围，缺少字段表示沿用资源默认范围。组合衣物请分别编辑组成部位。

应用绘制回传完整外观草稿，保存人物才落盘；取消保留原外观。自定义染色作用于最终合成像素，因此手绘的眼睛也可快速调色。旧存档默认无覆盖，联机协议升为 6，避免旧端丢弃范围数据。`SKIN_DETAIL` 存档标识保留，显示名称改为“面部”。

## 预设图库与像素范围

点击合并后的“部位名：预设部件名”入口（含“无／使用基础皮肤”）会打开 3D 图库：整体分类列出所有整体预设，部位分类列出基础外观及该部位的全部资源。单击选中、双击确认，底部也可应用或取消；模型支持右键拖动，分页只创建当前页纹理。确认图库仍只修改外观草稿，人物保存规则不变。图库按资源定义生成，F3+T 后自动重新读取列表与模型。

整体预设新增 `regions` 字段，键为部位名，值为原版 slim 64×64 图集像素索引数组 `y*64+x`，基础层与外层都可指定。例如：

```json
{"name":"example.preset.casual","body":"terra_character:slim","hair":"terra_character:fire_berry",
 "colors":{},"parts":{},"regions":{"eyes":[778,781],"mouth":[907,908]}}
```

应用整体预设时像素范围与外观一起替换。内置默认预设已包含所有单件部位的范围；资源包作者应为新预设提供准确范围，旧预设省略时继续兼容资源默认范围。非法、越界或重叠范围会拒绝加载。部件已有 fragments.rect 像素范围；图库不引入额外模型层。

创建人物和换装染色只修改当前通道，取消“全选”。“随机”仅随机当前通道。整体预设与部位预设均有 20%～100% 的正弦透明度闪烁，闪烁不会写入保存的皮肤。图库按实际部件 UV 范围计算模型高度并分配不同大小的矩形展示框，局部部件放大显示；右键拖动旋转，左键恢复鼠标跟随与平滑复位。
