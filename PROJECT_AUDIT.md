# PROJECT_AUDIT — 升級前的專案檢查

檢查時間點：Git tag `checkpoint-before-pro-upgrade`（升級前的可還原點）。
檢查方式：實際閱讀原始碼、執行既有測試與 CI，不是憑印象。

## 1. 架構現況

| 模組 | 內容 | 規模 |
|---|---|---|
| `:engine` | GLES 3.0 底片引擎：`FilmPipeline`、`OffscreenRenderer`（分塊匯出）、`CameraPreviewRenderer`、`FilterSpec`／`FilmParams`、`CubeLut`、shader（`prefilter`／`blur`／`composite`） | Kotlin 約 1,150 行、GLSL 約 400 行 |
| `:data` | Room：收藏、最近使用、自訂參數、非破壞性編輯、設定 | 約 220 行 |
| `:app` | Compose UI、CameraX 1.5、MediaStore、ML Kit（物體／人臉） | 約 6,300 行 |
| `tools/looks` | Python／numpy：OKLab 配色引擎、33³ LUT 烘焙、色彩檢查 | — |
| `tools/engine` | 把 App 出貨的同一份 GLSL 放進 headless Chromium（WebGL2）執行的測試工具 | — |

結論：分層是健康的（引擎無 UI、資料層獨立），**不需要重寫**，在既有架構上擴充。

## 2. 影像管線（升級前）

順序：鏡頭取樣（色差）→ 曝光／白平衡（線性光）→ 對比 → 3D LUT（33³，OKLab 烘焙：影調曲線、RGB 曲線、分色 HSL、分離色調、色域壓縮）→ 飽和 → 褪色 → 光暈／柔焦 → 暗角 → 顆粒 → 抖色輸出。

- 優點：預覽與匯出共用同一條 `FilmPipeline`；匯出分塊、無接縫；LUT 在線性光／OKLab 下製作。
- **缺口**：使用者參數沒有 Highlights、Shadows、Whites、Blacks、Sharpness（總指令要求的 13 項參數缺 5 項）。
- **缺口**：沒有以「底片模擬」為定位的一組濾鏡（Provia／Velvia／Astia／Classic Chrome…那 12 種性格只覆蓋到一部分）。

## 3. 濾鏡（升級前 20 款）

- 都是真的濾鏡（LUT + 效果參數），不是色罩。每款由 `tools/looks` 產生並通過 `clean_check`。
- **問題**：濾鏡縮圖是程式畫的底片罐圖示（`FilmIcon`），不是濾鏡在照片上的真實效果 → 使用者無法從縮圖判斷效果。
- **問題**：沒有固定的標準測試照片；Daylight Lab 的照片未隨 App 出貨。

## 4. 智慧構圖（升級前）

- `app/ui/Composition.kt`：以 ML Kit 人臉／物體框 + 美感成本模型移動「完美構圖」目標點。
- **問題**：只處理「主體放哪裡」。沒有地平線、垂直線、引導線、對稱、留白的量測；沒有文字化的依據；沒有信心值；沒有標註答案的測試。
- **問題**：分析邏輯綁在 Android（Compose／ML Kit）裡，無法在沒有手機的環境做單元測試。

## 5. 濾鏡推薦

- 升級前**不存在**。

## 6. 測試（升級前）

- 有：`EngineTest`（JVM）、`EngineDeviceTest`／`LabUiTest`／`DaylightLabTest`／`GuidanceTest`（模擬器）、Python 色彩檢查、WebGL shader 測試。
- **問題**：CI 模擬器的模擬相機會讓整台模擬器當掉（與 App 無關，舊版 commit 重跑也會當）→ 相機流程（預覽、拍照、切換）在 CI 上**沒有任何自動化覆蓋**。
- **問題**：沒有 Golden Image 測試；沒有一鍵測試入口；沒有構圖／推薦的準確度測試。

## 7. 效能與穩定性

- 預覽即時（GPU）、匯出分塊，記憶體不足時會降階並告知。
- 風險：縮圖每次重算；分析若放在主執行緒會卡預覽（升級時要避開）。

## 8. 升級項目對照

| 總指令要求 | 升級前 | 處理 |
|---|---|---|
| 專業影像管線 + 13 項參數 | 缺 5 項 | 補齊，放在 LUT 之前的顯示亮度域 |
| 12 種底片模擬性格 | 部分 | 新增 Film Simulation 系列 7 款（原創配方、原創名稱） |
| 真實濾鏡縮圖 + 標準測試照片 | 無 | 內建 14 張合法來源照片，縮圖由引擎即時算 |
| 幾何依據的構圖分析 | 只有主體位置 | 新 `:vision` 模組 |
| 濾鏡推薦 | 無 | `FilterRecommender`，依量測到的濾鏡特徵排名 |
| 虛擬相機測試系統 | 無 | `VirtualCamera`，與實體相機共用同一條預覽／拍照管線 |
| Golden Image、一鍵測試 | 無 | `tools/golden`、`scripts/run_all_tests.sh` |

## 9. 不動的部分

相機控制（變焦、對焦、曝光、白平衡板、鏡頭選擇、長寬比、計時、閃光）、相簿、非破壞性編輯、Filter Lab、Daylight Lab、原有 20 款濾鏡——全部保留，沒有刪除任何功能。
