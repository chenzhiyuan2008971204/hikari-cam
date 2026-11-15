# TESTING_REPORT — 測試報告

## 0. 一鍵測試

```
scripts/run_all_tests.sh            # 本機能跑的全部（約 11 分鐘）
scripts/run_all_tests.sh --quick    # 略過 Golden（約 30 秒）
scripts/run_all_tests.sh --device   # 另外在已連接的模擬器／手機跑 UI 自動化（需 Android SDK + adb）
```

結果寫在 `reports/test_summary.txt`，各階段日誌在 `reports/logs/`。無法在該機器執行的階段標為 **SKIPPED 並寫明原因，不算通過**；任何階段失敗則結束碼為 1。
CI（`.github/workflows/build.yml`）每次推送自動跑三個工作：`build`、`golden`、`emulator`。

## 1. 總表（最後一次完整執行）

| 層級 | 內容 | 在哪裡跑 | 結果 |
|---|---|---|---|
| 濾鏡色彩檢查 | 日光系列的天空／綠葉／膚色／白色需求（合成色塊 + 照片） | 本機 | 通過 |
| `:vision` 單元測試 | 26 項：合成幾何、14 張實拍標註、建議規則、推薦規則、偵測器輸出的反例 | 本機、CI build | 通過 |
| shader 引擎測試 | 解析度無關、顆粒、色帶、分塊 | 本機、CI golden | 通過 |
| Golden Image + 品質 | 27 款 × 14 張 = 378 張比對；偏色／死白／死黑／高光／膚色／色帶 | 本機、CI golden | 378 / 378 通過，品質檢查無例外 |
| Android 單元測試 + release 建置 | `EngineTest`（27 款濾鏡檔、LUT、81 組參數一致性）、APK | CI build | 通過 |
| 模擬器儀器測試 | 11 項（見下） | CI emulator（Android 14，軟體 GPU） | 10 項通過；1 項（編輯器畫面）在模擬器上略過，見第 4 節 |
| 實體手機 | — | — | **未執行**（沒有連接的裝置） |

## 2. 模擬器儀器測試（11 項）

量測機器：Google sdk_gphone64_x86_64，API 34，**模擬器**，GPU 為 SwiftShader（軟體）。未開模擬相機。

| 測試 | 驗證什麼 | 結果 |
|---|---|---|
| `EngineDeviceTest.looksRenderAndPreviewMatchesExport` | 27 款：1600×1200 預覽 vs 4000×3000 匯出一致；輸出不是全黑／壞圖 | 通過 |
| `EngineDeviceTest.tiledExportHasNoSeams` | 分塊匯出跨塊階差 | 通過（2 階） |
| `EngineDeviceTest.decoderDownscaleVsFullDecode` | 解碼縮放與完整解碼一致 | 通過 |
| `EngineDeviceTest.diagnoseSourcePaths` | 各種來源尺寸／快取路徑的輸出亮度與 GL 錯誤碼 | 通過 |
| `DaylightLabTest.daylightLab` | Daylight Lab：7 場景 × 8 款、比較畫面 | 通過 |
| `LabUiTest.filterLabFlow` | 四個分頁、Filter Lab：切換 4 款、配方、預覽＝匯出檢查、完整解析度匯出、效能、參數 | 通過 |
| `SmartDeviceTest.analysisOnEveryStandardPhoto` | 14 張照片以 ML Kit 實際偵測 → 場景、前三名、構圖建議；規則檢查；同輸入同輸出；記憶體 | 通過 |
| `EditorModelTest.openAnalyseApplyExport` | 編輯器邏輯（不含畫面）：開啟相簿照片、預覽、27 款縮圖、分析、套用第一名、全尺寸匯出 | 通過 |
| `SmartUiTest.switchingSourcesIsStable` | 實體來源 ↔ 虛擬相機來回切換 3 次不當機 | 通過 |
| `SmartUiTest.virtualCameraFlow` | 虛擬相機完整流程（見第 3 節） | 通過 |
| `SmartUiTest.zEditorSmartFlow` | 編輯器畫面：推薦卡片、構圖疊圖、套用、匯出 | **模擬器上略過**（第 4 節） |

`CameraUiTest`（模擬相機／實體相機）保留但 CI 不執行：模擬器的模擬相機會讓模擬器當機，與 App 無關。

## 3. 虛擬相機流程（`virtualCameraFlow`，模擬器）

虛擬相機把內建標準照片當作相機畫面，交給**同一個預覽渲染器、同一條濾鏡管線、同一組偵測器與推薦、同一條拍照→全解析度渲染→存檔路徑**。

| 步驟 | 結果 |
|---|---|
| App 啟動到虛擬相機預覽出現 | 5.6 秒（含 App 啟動；模擬器） |
| 即時推薦 | 出現 3 個推薦標籤 |
| 點推薦標籤切換濾鏡 | 完成 |
| 即時智慧構圖開啟／關閉 | 完成 |
| 拍照 | 成功，3.4 秒存檔（模擬器），相簿新增濾鏡版與原圖 |
| 切換鏡頭鍵 → 下一張標準照片（兩次） | 完成 |

失敗情況也有測：沒有相機時 App 顯示說明並提供「改用虛擬相機」而不是當機；虛擬相機啟動失敗時顯示原因；來源切換中的取消不被當成錯誤。

## 4. 沒有通過／沒有驗證的部分（誠實列出）

1. **編輯器畫面的 UI 自動化在 CI 模擬器上無法執行。** 一開編輯器畫面，模擬器行程就消失（App 無例外），**原因沒有查出來**；六次嘗試、四種排除作法都一樣（細節見 `KNOWN_ISSUES.md` B-5a）。不帶畫面的 `EditorModelTest` 在同一台模擬器每次都通過。因此「智慧」分頁的畫面（推薦卡片、構圖疊圖、裁切框）**沒有自動化截圖驗證**，需要在手機上看。
1a. **模擬器偶發當機。** 虛擬相機流程在最後六輪中通過五次、一次因模擬器消失而失敗（原因不明）。
2. **沒有任何實機測試。** 預覽 FPS、發熱、50 MP 匯出、長焦鏡頭、RAW、觸控手感都未驗證。
3. **模擬器截圖這次沒有取回。** 截圖存在模擬器裡，早先幾輪因模擬器當機而拉不回來；最後一輪的截圖在 CI 產物 `lab-emulator` 中。
4. 構圖／推薦的準確度只在 14 張照片上量過，見 `AI_COMPOSITION_REPORT.md`。

## 5. 這一輪測試抓到並修掉的問題

共 13 項，逐項列在 `KNOWN_ISSUES.md` 的 C 節（夜景判斷、屋頂斜邊誤判、黑白配方說明、推薦評分、虛擬相機啟動與當機、裝置端偵測器輸出造成的四種構圖誤判等）。沒有為了通過而刪除或放寬任何測試；Golden 品質門檻沒有任何濾鏡例外。唯一的略過是上面第 4-1 點，且已用不帶畫面的測試補上同樣的步驟。

## 6. 測試資料

14 張內建標準照片（`app/src/main/assets/samples/`，授權清單在 `samples.json` 與設定頁）：晴天藍天、陰天街景、綠色植物、城市建築、人像、逆光、高反差、白色建築、海與雲、深膚色人像、水泥建築、海灘人物、夜景燈光、夜間街道。來源為 Kodak 無損測試影像集與 Wikimedia Commons CC0。解析度 512×768 到 1024×771，橫直皆有。
仍缺：室內混合光、日落（找不到授權明確的來源，沒有硬塞）。

## 7. 紀錄位置

| 檔案 | 內容 |
|---|---|
| `reports/test_summary.txt` | 一鍵測試各階段結果 |
| `reports/golden/golden_report.txt`、`.json` | Golden 與品質數字 |
| `reports/vision_metrics.txt` | 構圖／推薦所有案例的量測值 |
| `reports/composition/*.jpg` | 14 張照片的構圖證據疊圖 |
| `reports/previews/*.jpg` | 27 款濾鏡在 14 張照片上的真實輸出 |
| CI 產物 `lab-emulator` | 模擬器報告、截圖、逐測試 logcat |
