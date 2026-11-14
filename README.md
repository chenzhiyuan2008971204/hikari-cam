# 光 Hikari 相機

日系濾鏡相機 App（Android），為小米14 調校。

- 20 個濾鏡：空氣感 / 底片 / 電影・夜，拍照時即時預覽
- 9 個通過驗證的攝影師風格（日系名家：川內倫子、濱田英明、奧山由之、森山大道、植田正治；歐美名家：Saul Leiter、Eggleston、Herzog；電影・夜：何藩），都用攝影師真實作品校正並驗證，套用後畫面乾淨。點濾鏡右上角的 i 看對照圖，並可下載 Lightroom XMP 描述檔與 .cube。驗證方法見 `tools/looks/VERIFICATION.md`
- 濾鏡強度與 10 項微調，可存成「我的濾鏡」，可匯入 .cube LUT
- 手動模式：ISO、快門、對焦、白平衡、曝光補償；0.6x / 1x / 2x / 3.2x 鏡頭
- 編輯相簿舊照片（按住看原圖）、底片日期戳記

## 安裝
到本 repo 的 **Releases**，下載最新的 `HikariCam.apk`，在手機上開啟安裝
（小米需允許「安裝未知來源應用」）。

每次推送到 `main` 分支，GitHub Actions 會自動編譯新版本。
