# SUID 图标完整参考

> 包名: `@ead/suid-icons`（基于 `@ead/suid-icons-svg`）
> 导入: `import { X } from '@ead/suid-icons'`
> 禁止从 `@ead/suid` 或 `@ant-design/icons` 导入图标

> ⚠️ **严禁臆想图标名**：所有图标组件名必须来自本文件列举的实际导出列表（Outlined/Filled 分类），不得自行推断或拼造不存在的名称（如 `UploadFileOutlined`、`TableOutlined` 等未列出的名称）。使用前必须核对下方分类列表或通过 `iconsByTheme` 验证。

## 主题系统

图标分为两种主题：

| 主题 | 数量 | 命名规则 | 说明 |
|------|------|----------|------|
| **Outlined** | 369 个 | `{Name}Outlined` | 线条/描边风格，覆盖所有分类 |
| **Filled** | 60 个 | `{Name}Filled` | 实心/填充风格，常见于状态、品牌、方向图标 |

```tsx
import { HomeOutlined, HomeFilled, CheckCircleOutlined, CheckCircleFilled } from '@ead/suid-icons';

<HomeOutlined />    // 线条风格
<HomeFilled />      // 实心风格
```

> 填充图标集中在：文件类型（FileExcel/Word/Pdf/Image/Zip）、状态圆圈（Check/Close/Info/Warning Circle）、表情（Smile/Meh/Frown/Heart）、品牌（Apple/Android/Windows）、方向箭头等

## 关键 Props

| 属性 | 说明 | 类型 | 默认值 |
|------|------|------|--------|
| `spin` | 旋转动画 | `boolean` | `false` |
| `rotate` | 旋转角度（度） | `number` | - |
| `twoToneColor` | 双色图标颜色 | `string \| [string, string]` | — |
| `style` | 行内样式 | `CSSProperties` | — |
| `className` | CSS 类名 | `string` | — |
| `onClick` | 点击事件 | `function` | — |
| `aria-label` | 无障碍标签 | `string` | — |

> 所有图标组件均通过 `React.forwardRef` 转发 ref 到 `<span>` 元素

## 基础用法

```tsx
import {
  HomeOutlined,
  SearchOutlined,
  SettingOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  SyncOutlined,
  LoadingOutlined,
} from '@ead/suid-icons';

// 基本使用
<HomeOutlined />
<SearchOutlined />

// 旋转动画
<SyncOutlined spin />
<LoadingOutlined />  {/* Loading 图标自动启用 spin */}

// 旋转指定角度
<SyncOutlined rotate={180} />

// 样式控制
<SearchOutlined style={{ fontSize: 24, color: '#1890ff' }} />

// 事件处理（自动添加 tabIndex）
<DeleteOutlined onClick={() => handleDelete(id)} />
```

## 双色图标

Filled 图标支持双色模式，可分别设置主色和辅色。

```tsx
import { CheckCircleFilled, setTwoToneColor } from '@ead/suid-icons';

// 方式1：全局设置
setTwoToneColor('#1890ff');                    // 单色（自动计算辅色）
setTwoToneColor(['#1890ff', '#f5222d']);       // 显式主色 + 辅色

// 方式2：单个图标 prop 覆盖
<CheckCircleFilled twoToneColor="#eb2f96" />
<CheckCircleFilled twoToneColor={['#eb2f96', '#f5222d']} />

// 获取当前设置
import { getTwoToneColor } from '@ead/suid-icons';
getTwoToneColor(); // string | [string, string]
```

## iconfont 集成

使用 `createFromIconfontCN` 加载 iconfont.cn 上的 SVG 符号图标。

```tsx
import { createFromIconfontCN } from '@ead/suid-icons';

const IconFont = createFromIconfontCN({
  scriptUrl: '//at.alicdn.com/t/font_8d5l8fzk5b87iudi.js',
  // 也支持多 URL（按反序加载，首个 URL 的图标优先）
  // scriptUrl: ['url1.js', 'url2.js'],
  extraCommonProps: { style: { fontSize: 16 } }, // 传递给所有图标的额外 props
});

// 使用
<IconFont type="icon-tuichu" />
<IconFont type="icon-facebook" />
<IconFont type="icon-tuichu" spin />
```

**工作原理**：动态注入 `<script>` 标签加载 SVG symbol 定义，通过 `<use xlinkHref="#icon-name">` 引用。

## 自定义 SVG 图标

使用默认导出的 `Icon` 组件包裹自定义 SVG。

```tsx
import Icon from '@ead/suid-icons';

// 定义 SVG 组件
const HeartSvg = () => (
  <svg width="1em" height="1em" fill="currentColor" viewBox="0 0 1024 1024">
    <path d="M923 283.6a260.4 260.4 0 0 0-56.9-82.8 264.4 264.4 0 0 0-84-55.5A265.3 265.3 0 0 0 679.7 125c-49.3 0-97.4 13.5-139.2 39-10 6.1-19.5 12.8-28.5 20.1-9-7.3-18.5-14-28.5-20.1a265.3 265.3 0 0 0-334.4 62.1c-51 68-64.3 156-35.4 237.3 13.2 36 32.9 68.8 58.1 97.8 13.4 15.6 28.1 30 43.8 43.2l.2.2L512 864l345.1-352.4c15.7-13.2 30.4-27.6 43.8-43.2 25.2-29 44.9-61.8 58.1-97.8 28.9-81.3 15.6-169.3-35.4-237.3z" />
  </svg>
);

// 包装为图标组件
const HeartIcon = (props) => <Icon component={HeartSvg} {...props} />;

// 使用（自动获得 spin、rotate、事件等能力）
<HeartIcon style={{ color: 'hotpink', fontSize: 32 }} />
<HeartIcon spin />
```

## IconProvider 上下文

全局配置所有子图标的 CSS 前缀、类名、CSP 等选项。

```tsx
import { IconProvider, createFromIconfontCN } from '@ead/suid-icons';

const IconFont = createFromIconfontCN({ scriptUrl: '//...' });

<IconProvider value={{
  prefixCls: 'myicon',        // 自定义 CSS 前缀（默认 'suidicon'）
  rootClassName: 'custom-cls', // 额外根类名
  csp: { nonce: 'abc123' },    // CSP nonce
  layer: 'icons',              // CSS @layer 名称
}}>
  <HomeOutlined />
  <IconFont type="icon-tuichu" />
</IconProvider>
```

**IconContextProps**:

| 属性 | 说明 |
|------|------|
| `prefixCls` | CSS 类名前缀，默认 `'suidicon'` |
| `rootClassName` | 图标根元素额外类名 |
| `csp` | Content Security Policy nonce |
| `layer` | CSS @layer 名称 |

## 动态加载

通过通配符导入获取所有图标，实现动态访问。

```tsx
import * as SuidIcons from '@ead/suid-icons';

// 动态按名称访问
const iconName = 'HomeOutlined';
const IconComponent = SuidIcons[iconName];
if (IconComponent) {
  return <IconComponent />;
}

// 按主题过滤
const outlinedIcons = Object.keys(SuidIcons).filter(name => name.endsWith('Outlined'));
const filledIcons = Object.keys(SuidIcons).filter(name => name.endsWith('Filled'));

// 使用 iconsByTheme 获取分组
import { iconsByTheme } from '@ead/suid-icons';
// iconsByTheme.Outlined → string[] (369 个)
// iconsByTheme.Filled → string[] (60 个)
```

## 导出总览

```tsx
// 具体图标组件
import { HomeOutlined, CheckCircleFilled } from '@ead/suid-icons';

// 通用 Icon 组件（默认导出，用于自定义 SVG）
import Icon from '@ead/suid-icons';

// iconfont 工厂
import { createFromIconfontCN } from '@ead/suid-icons';

// 双色全局设置
import { setTwoToneColor, getTwoToneColor } from '@ead/suid-icons';

// 上下文
import { IconProvider } from '@ead/suid-icons';

// 主题分组
import { iconsByTheme } from '@ead/suid-icons';

// 通配符导入
import * as SuidIcons from '@ead/suid-icons';
```

## 无障碍

所有图标内置无障碍支持：
- `<span role="img">` 包裹
- 自动设置 `aria-hidden="true"`（除非指定了 `aria-label`）
- 设置 `onClick` 时自动添加 `tabIndex={-1}`
- 通过 `aria-label` prop 支持屏幕阅读器

```tsx
<SearchOutlined aria-label="搜索" />
<CloseOutlined onClick={handleClose} />  {/* 自动 tabIndex */}
```

## 完整图标名称列表

> ⚠️ 使用图标前必须从此列表确认名称存在，**禁止使用未列出的名称**。

### Outlined（线条风格，369 个）

`AccountBookOutlined` `AimOutlined` `AlertOutlined` `AlignCenterOutlined` `AlignLeftOutlined`
`AlignRightOutlined` `AlipayCircleOutlined` `AlipayOutlined` `AndroidOutlined` `ApartmentOutlined`
`ApiOutlined` `AppOutlined` `AppleOutlined` `AppstoreAddOutlined` `AppstoreOutlined`
`AreaChartOutlined` `ArrowDownOutlined` `ArrowLeftOutlined` `ArrowRightOutlined` `ArrowUpOutlined`
`ArrowsAltOutlined` `AudioMutedOutlined` `AudioOutlined` `AuditOutlined` `BankOutlined`
`BarChartOutlined` `BarcodeOutlined` `BarsOutlined` `BellOutlined` `BgColorsOutlined`
`BlockOutlined` `BoldOutlined` `BookOutlined` `BorderBottomOutlined` `BorderLeftOutlined`
`BorderOutlined` `BorderRightOutlined` `BorderTopOutlined` `BorderlessTableOutlined` `BoxPlotOutlined`
`BranchesOutlined` `BugOutlined` `BuildOutlined` `BulbOutlined` `CalculatorOutlined`
`CalendarOutlined` `CameraOutlined` `CarOutlined` `CaretDownOutlined` `CaretLeftOutlined`
`CaretRightOutlined` `CaretUpOutlined` `CarryOutOutlined` `ChangeOutlined` `CheckCircleOutlined`
`CheckOutlined` `CheckSquareOutlined` `CiCircleOutlined` `ClearOutlined` `ClockCircleOutlined`
`CloseCircleOutlined` `CloseOutlined` `CloseSquareOutlined` `CloudDownloadOutlined` `CloudOutlined`
`CloudServerOutlined` `CloudSyncOutlined` `CloudUploadOutlined` `ClusterOutlined` `CodeOutlined`
`CoffeeOutlined` `ColumnHeightOutlined` `ColumnWidthOutlined` `CommentOutlined` `CompassOutlined`
`CompressOutlined` `ConsoleSqlOutlined` `ContactsOutlined` `ContainerOutlined` `ControlOutlined`
`CopyOutlined` `CopyrightOutlined` `CreditCardOutlined` `CrownOutlined` `CustomerServiceOutlined`
`DashOutlined` `DashboardOutlined` `DatabaseOutlined` `DeleteColumnOutlined` `DeleteOutlined`
`DeleteRowOutlined` `DeliveredProcedureOutlined` `DeploymentUnitOutlined` `DesktopOutlined` `DiffOutlined`
`DingdingOutlined` `DisconnectOutlined` `DislikeOutlined` `DollarCircleOutlined` `DotChartOutlined`
`DoubleLeftOutlined` `DoubleRightOutlined` `DownCircleOutlined` `DownOutlined` `DownloadOutlined`
`DragHandlerOutlined` `DragLineHandlerOutlined` `DragOutlined` `DribbbleOutlined` `EditOutlined`
`EllipsisOutlined` `EnterOutlined` `EnvironmentOutlined` `EuroCircleOutlined` `ExceptionOutlined`
`ExclamationCircleOutlined` `ExclamationOutlined` `ExpandOutlined` `ExperimentOutlined` `ExportOutlined`
`EyeInvisibleOutlined` `EyeOutlined` `FallOutlined` `FastBackwardOutlined` `FastForwardOutlined`
`FieldBinaryOutlined` `FieldNumberOutlined` `FieldStringOutlined` `FieldTimeOutlined` `FileAddOutlined`
`FileDoneOutlined` `FileExcelOutlined` `FileExclamationOutlined` `FileGifOutlined` `FileImageOutlined`
`FileJpgOutlined` `FileMarkdownOutlined` `FileOutlined` `FilePdfOutlined` `FilePptOutlined`
`FileProtectOutlined` `FileSearchOutlined` `FileSyncOutlined` `FileTextOutlined` `FileUnknownOutlined`
`FileWordOutlined` `FileZipOutlined` `FilterOutlined` `FireOutlined` `FixedLeftOutlined`
`FixedRightOutlined` `FlagOutlined` `FolderAddOutlined` `FolderOpenOutlined` `FolderOutlined`
`FolderViewOutlined` `FontColorsOutlined` `FontSizeOutlined` `ForkOutlined` `FormOutlined`
`FormatPainterOutlined` `FrownOutlined` `FullscreenExitOutlined` `FullscreenOutlined` `FunctionOutlined`
`FundOutlined` `FundProjectionScreenOutlined` `FundViewOutlined` `FunnelPlotOutlined` `GatewayOutlined`
`GifOutlined` `GiftOutlined` `GlobalOutlined` `GoldOutlined` `GroupOutlined`
`HddOutlined` `HeartOutlined` `HeatMapOutlined` `HighlightOutlined` `HistoryOutlined`
`HomeOutlined` `HourglassOutlined` `IdcardOutlined` `ImportOutlined` `InboxOutlined`
`InfoCircleOutlined` `InfoOutlined` `InsertRowAboveOutlined` `InsertRowBelowOutlined` `InsertRowLeftOutlined`
`InsertRowRightOutlined` `InsuranceOutlined` `InteractionOutlined` `IssuesCloseOutlined` `ItalicOutlined`
`KeyOutlined` `LaptopOutlined` `LayoutOutlined` `LeftCircleOutlined` `LeftOutlined`
`LikeOutlined` `LineChartOutlined` `LineHeightOutlined` `LinkOutlined` `Loading3QuartersOutlined`
`LoadingOutlined` `LockOutlined` `MacCommandOutlined` `MailOutlined` `ManOutlined`
`MaximizeOutlined` `MedicineBoxOutlined` `MehOutlined` `MenuFoldOutlined` `MenuOutlined`
`MenuUnfoldOutlined` `MergeCellsOutlined` `MessageOutlined` `MinimizeOutlined` `MinusCircleOutlined`
`MinusOutlined` `MinusSquareOutlined` `MobileOutlined` `MoneyCollectOutlined` `MonitorOutlined`
`MoonOutlined` `MoreOutlined` `NodeCollapseOutlined` `NodeExpandOutlined` `NodeIndexOutlined`
`NonFixedOutlined` `NotificationOutlined` `NumberOutlined` `OneToOneOutlined` `OrderedListOutlined`
`PaperClipOutlined` `PartitionOutlined` `PauseCircleOutlined` `PauseOutlined` `PayCircleOutlined`
`PercentageOutlined` `PhoneOutlined` `PicCenterOutlined` `PicLeftOutlined` `PicRightOutlined`
`PictureOutlined` `PieChartOutlined` `PlaySquareOutlined` `PlusCircleOutlined` `PlusOutlined`
`PlusSquareOutlined` `PoundOutlined` `PoweroffOutlined` `PrinterOutlined` `ProfileOutlined`
`ProjectOutlined` `PropertySafetyOutlined` `PullRequestOutlined` `PushpinOutlined` `QqOutlined`
`QrcodeOutlined` `QuestionCircleOutlined` `QuestionOutlined` `RadarChartOutlined` `RadiusBottomleftOutlined`
`RadiusBottomrightOutlined` `RadiusSettingOutlined` `RadiusUpleftOutlined` `RadiusUprightOutlined` `ReadOutlined`
`ReconciliationOutlined` `RedEnvelopeOutlined` `RedoOutlined` `ReloadOutlined` `RestOutlined`
`RightCircleOutlined` `RightOutlined` `RiseOutlined` `RobotOutlined` `RocketOutlined`
`RotateLeftOutlined` `RotateRightOutlined` `SafetyCertificateOutlined` `SafetyOutlined` `SaveOutlined`
`ScanOutlined` `ScheduleOutlined` `ScissorOutlined` `SearchOutlined` `SecurityScanOutlined`
`SelectOutlined` `SendOutlined` `SettingOutlined` `ShakeOutlined` `ShareAltOutlined`
`ShopOutlined` `ShoppingCartOutlined` `ShoppingOutlined` `ShrinkOutlined` `SisternodeOutlined`
`SkinOutlined` `SlidersOutlined` `SmileOutlined` `SnippetsOutlined` `SolutionOutlined`
`SortAscendingOutlined` `SortDescendingOutlined` `SortingDownOutlined` `SortingOutlined` `SortingUpOutlined`
`SoundOutlined` `SplitCellsOutlined` `StarOutlined` `StepBackwardOutlined` `StepForwardOutlined`
`StockOutlined` `StopOutlined` `StrikethroughOutlined` `SubnodeOutlined` `SunOutlined`
`SwapLeftOutlined` `SwapOutlined` `SwapRightOutlined` `SwitcherOutlined` `SyncOutlined`
`TableOutlined` `TabletOutlined` `TagOutlined` `TagsOutlined` `TeamOutlined`
`ThunderboltOutlined` `ToTopOutlined` `ToolOutlined` `TrademarkCircleOutlined` `TransactionOutlined`
`TranslationOutlined` `TrophyOutlined` `UnderlineOutlined` `UndoOutlined` `UngroupOutlined`
`UnlockOutlined` `UnorderedListOutlined` `UpCircleOutlined` `UpOutlined` `UploadOutlined`
`UsbOutlined` `UserAddOutlined` `UserDeleteOutlined` `UserOutlined` `UserSwitchOutlined`
`UsergroupAddOutlined` `UsergroupDeleteOutlined` `VerifiedOutlined` `VerticalAlignBottomOutlined` `VerticalAlignMiddleOutlined`
`VerticalAlignTopOutlined` `VerticalLeftOutlined` `VerticalRightOutlined` `VideoCameraAddOutlined` `VideoCameraOutlined`
`WalletOutlined` `WarningOutlined` `WechatOutlined` `WhatsAppOutlined` `WifiOutlined`
`WindowsOutlined` `WomanOutlined` `ZoomInOutlined` `ZoomOutOutlined`

### Filled（实心风格，60 个）

`AlipaySquareFilled` `AndroidFilled` `AppFilled` `AppleFilled` `CheckCircleFilled`
`CheckSquareFilled` `ClockCircleFilled` `CloseCircleFilled` `CloseSquareFilled` `DingtalkCircleFilled`
`DingtalkSquareFilled` `DislikeFilled` `DownCircleFilled` `DownSquareFilled` `DribbbleCircleFilled`
`DribbbleSquareFilled` `ExclamationCircleFilled` `FastBackwardFilled` `FastForwardFilled` `FileExcelFilled`
`FileFolderFilled` `FileImageFilled` `FileMp3Filled` `FileMp4Filled` `FileNnknownFilled`
`FilePdfFilled` `FilePptFilled` `FileTxtFilled` `FileWordFilled` `FileZipFilled`
`FilterFilled` `FolderFilled` `FrownFilled` `HeartFilled` `InfoCircleFilled`
`LeftCircleFilled` `LeftSquareFilled` `LikeFilled` `MehFilled` `MinusCircleFilled`
`MinusSquareFilled` `PauseCircleFilled` `PlayCircleFilled` `PlusCircleFilled` `PlusSquareFilled`
`QqCircleFilled` `QqSquareFilled` `QuestionCircleFilled` `RightCircleFilled` `RightSquareFilled`
`SendFilled` `SmileFilled` `StarFilled` `StepBackwardFilled` `StepForwardFilled`
`StopFilled` `UpCircleFilled` `UpSquareFilled` `WarningFilled` `WindowsFilled`
