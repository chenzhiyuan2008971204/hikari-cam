# 光 Hikari — Analog Film Camera

Android 原生底片相機 App：27 款原創濾鏡、自己的 GPU 底片引擎、完整解析度輸出、有量測依據的構圖分析與濾鏡推薦。

## 功能

- **Camera**：CameraX 即時預覽，經過與匯出相同的 GPU 底片管線。
  - 濾鏡列：收藏與最近使用排在前面。
  - 拍攝控制：調整面板、閃光燈、計時器（3 / 10 秒）、長寬比（3:4、2:3、9:16、1:1）、九宮格、雙指變焦、點按對焦與測光、曝光補償、前後鏡頭切換。
  - **只顯示這台相機真的有的功能**：沒有閃光燈、沒有前鏡頭、不支援曝光補償或 RAW 時，對應控制項會隱藏或改為說明文字。
  - 拍照流程：CameraX 以最高解析度拍 JPEG，引擎再以完整解析度、用和預覽相同的濾鏡與參數重新渲染。
  - 另外可保存：未套濾鏡的原圖 JPEG；相機支援 RAW + JPEG 時，再加一份 DNG。
- **Gallery**：顯示用 Hikari 拍的照片，也能匯入任何照片（JPEG / PNG / WebP / HEIC / DNG，依裝置解碼能力）。長按可多選刪除（Android 11 以上由系統確認）。
- **Editor**：非破壞性編輯。
  - 原圖永遠不會被寫入；配方（濾鏡 + 參數）存在 Room，匯出時另存新檔並複製 EXIF。
  - 支援還原 / 重做、左右分割比較、按住看原圖、收藏、自訂參數。
- **Filters**：12 款濾鏡（每款有自己的原創底片罐／隨身相機圖示）、色彩配方說明、收藏、最近使用、自訂參數。濾鏡列順序固定，選取後不會跳動。
- **Settings**：JPEG 品質、保存原圖、DNG、格線、預設長寬比，以及 **Filter Lab**。
  - Filter Lab 是品質工具：匯入任意照片、即時切換、比較、完整解析度匯出、預覽與匯出一致性檢查、效能數據。

## 智慧功能（v5）

- **濾鏡推薦**：相機即時顯示最適合目前畫面的三款；編輯器的「智慧」分頁列出前三名（真實預覽、理由、適合度、與原圖差異）。
- **構圖分析**：編輯器「智慧」分頁最多三個建議，每個都有依據、目前位置、調整方向、預期改善與信心值，並把量到的線、框、點畫在照片上。量不到依據時會直接說明，不硬湊。
- **真實濾鏡縮圖**：Filters 頁的縮圖由引擎在內建標準照片上即時算出，可切換預覽照片；濾鏡詳情顯示六個場景。
- **虛擬相機**：設定 → 智慧功能。用內建標準照片當相機畫面，預覽、濾鏡、構圖、推薦、拍照走同一條流程（測試用，也可在沒有相機的裝置上體驗）。

## 測試

```
scripts/run_all_tests.sh            # 本機可跑的全部測試（約 11 分鐘）
scripts/run_all_tests.sh --quick    # 略過 Golden Image（約 30 秒）
scripts/run_all_tests.sh --device   # 另外在已連接的模擬器／手機上跑 UI 自動化
```

需要：Python 3（numpy、Pillow）、Node.js + Playwright（Chromium）、Gradle + JDK 17 以上。結果寫在 `reports/test_summary.txt`。
CI（`.github/workflows/build.yml`）另外會建置 APK、跑 Android 單元測試，並在 Android 14 模擬器上跑 UI 自動化。

報告：`PROJECT_AUDIT.md`、`DEVELOPMENT_PLAN.md`、`TESTING_REPORT.md`、`IMAGE_QUALITY_REPORT.md`、`AI_COMPOSITION_REPORT.md`、`PERFORMANCE_REPORT.md`、`KNOWN_ISSUES.md`；每款濾鏡的預覽圖在 `reports/previews/`。

## 架構

```
:engine   影像引擎（無 UI）
          GLES 3.0 shaders、FilmPipeline、OffscreenRenderer（分塊匯出）、
          CameraPreviewRenderer、FilterSpec / FilmParams、LUT
:data     Room：收藏、最近使用、自訂參數、非破壞性編輯、設定
:vision   場景分析、構圖幾何、濾鏡推薦（純 Kotlin，可在 JVM 測試）
:app      Compose UI、CameraX、MediaStore、ML Kit
tools/    濾鏡配方與 LUT 烘焙（Python）、WebGL 測試工具（與 App 用同一份 shader）、QA
```

- **預覽 = 匯出**：同一套 shader 與參數，只有輸出尺寸不同。
  - 光暈半徑、顆粒大小都以畫面短邊比例定義，所以不同解析度效果一致。
  - 預覽輸入一律是「原尺寸解碼再縮小」，避開解碼器縮圖造成的亮度差。
- **大照片**：
  - 原圖上傳 GPU 後立即釋放，輸出以 1024 px 分塊讀回，不會同時存在兩張完整大圖。
  - 解碼前依系統可用記憶體決定尺寸；不足時縮小，並在介面上明示。

## 影像管線（每像素，線性光）

1. 鏡頭色差
2. 白平衡 / 曝光
3. 柔焦 / 霧化
4. Halation 與 Bloom（保留色相）
5. 曝光不均 / 漏光
6. 暗角
7. 3D LUT 色彩配方
8. 使用者對比 / 飽和 / 褪色
9. 色彩漂移
10. 灰塵 / 刮痕
11. 多尺度顆粒（依亮度調整強度）
12. 三角抖動，避免色帶

### 色彩配方

每款濾鏡各有一份配方（`tools/looks/films.py`），在 OKLab 中處理，烘焙成 33³ LUT：

- filmic 影調曲線：黑位、高光肩部、18% 灰斜率
- 白平衡
- 整體 / 自然 / 高光飽和
- 8 色 HSL
- 分離色調
- 保護中性白灰與膚色色相
- 色域內壓縮

### 濾鏡強度

強度不是把全部效果線性一起降低：

- 色彩：線性混合
- 光暈：線性
- 鏡頭效果：s^1.2
- 顆粒：√s，弱強度時仍保留質感
- 瑕疵：s²，弱強度時幾乎消失

## 12 款濾鏡

| 系列 | 濾鏡 |
|---|---|
| Film | Mellow 400、Gold Memory、Cinema 400、Vivid 100 |
| Japanese | Tokyo Blue、Kyoto Soft、Summer Green |
| CCD | CCD 2000、Flash Diary、Compact Chrome |
| Special | Night Bloom、Dream 1998 |

名稱是原創的創作方向（避開實際底片商標），不是任何廠商的 LUT。

## 驗收與測試

| 測試 | 內容 |
|---|---|
| `./gradlew testDebugUnitTest` | 濾鏡解析、Kotlin 與測試工具參數一致、相機方向與裁切、設定 |
| CI `emulator` job（Android 14 模擬器） | 每款濾鏡預覽 vs 完整解析度匯出、輸出亮度正常、分塊接縫、解碼器差異，並實際操作四個分頁與 Filter Lab，存下截圖 |
| `tools/engine/engine_tests.py` | 尺寸一致性、顆粒相關長度與亮度響應、色帶、分塊 |
| `tools/engine/qa.py` | 18 張場景照片 × 12 款：高光爆白、暗部死黑、髒濁、膚色色相與飽和 |

最近一次 QA（12 款）：

- 高光爆白平均 ≤ 0.16%
- 暗部死黑 0%
- 膚色色相偏移 ≤ 1.8°
- 膚色飽和比 0.77–1.22

## 建置

推送後由 GitHub Actions 編譯。產出的 APK 是 Actions 的 artifact `HikariCam`；推送到 `main` 分支時也會發佈到 Releases。

## 已知限制

- 預覽每次渲染後讀回 CPU（小米 14 約 30–40 ms）。即時相機預覽直接畫到螢幕，不受影響。
- RAW 只在相機回報支援 RAW + JPEG 時提供。
- 模擬器的 GPU 是軟體模擬，效能數字不代表真機。
