# FRAME_STUDIO_REPORT — 專業邊框圖庫

> **更新（第二輪）**：依要求拿掉 6 款有齒孔的 35mm 類邊框（`film35`、`film35_vertical`、`film135_strip`、`contact_sheet`、`negative_sleeve`、`pano_film`），新增 10 款日系邊框（和紙、日曆、迷你相紙卡、俳句三行、原稿用紙、車票、相簿角貼、日期印、汽水薄荷、一點紅）。現在共 **91 款**：底片 9、極簡 15、日系 21、電影 11、拍立得 11、雜誌 11、創意 13。下方清單是第一輪的 87 款，已移除的 6 款不再存在。
> 邊框圖庫的示範照片改為從 `assets/japan/` 的日系照片池取用、每款一張不重複；**目前照片池只有 2 張**（合法來源的日本照片抓取被來源站限流），不足的部分仍用標準照片，所以圖庫裡照片仍會重複。放進更多照片後不需要改程式。

## 結果

- **87 款**邊框，7 個分類，全部是原創設計（沒有重現任何廠牌的相紙、標誌或商業外觀）。
- 自動化測試會檢查「沒有兩款邊框只差在顏色」：以留白比例 + 元素種類與數量當作結構指紋，87 款的指紋全部不同。這個測試第一次跑就抓到兩組只差顏色的（極簡白／黑、細線框／灰卡），已改成結構不同才通過。
- 每款的預覽都是真正把邊框合成到照片上的結果：`reports/frames/frames_<分類>.jpg`（電腦端繪圖後端），App 內的圖庫與編輯器縮圖則由 App 自己的繪圖後端即時算。

| 分類 | 款數 |
|---|---|
| 底片 Film & Analog | 15 |
| 極簡 Minimal | 15 |
| 日系 Japanese | 11 |
| 電影 Cinematic | 11 |
| 拍立得 Instant Camera | 11 |
| 雜誌 Editorial | 11 |
| 創意 Creative | 13 |

## 設計元素

不是只有矩形：不同比例與不對稱的留白、細線與多層框線、紙張顆粒、陰影、圓角、撕紙邊、紙膠帶、底片齒孔、片邊碼與條碼、裁切記號、場記板、字幕區、刻度、郵票齒孔、票根虛線、方格紙、色塊拼貼、模糊背景延伸、從照片本身取出的五色色票與漸層、七段顯示器日期、直書標題、落款印章、雜誌刊頭與圖說、檔案索引欄位。每款只用其中幾種。

## 怎麼運作

```
原始照片 → 基礎調整 + 濾鏡（GPU 引擎，完整解析度）→ 時間戳（畫在照片上）→ 邊框與版面 → JPEG
```

- 排版在純 Kotlin 模組 `:studio` 裡算出一份「繪圖清單」（填色、線、文字、多邊形、照片…），App 用 android.graphics 畫、測試用 java.awt 畫，兩邊吃同一份清單。
- **照片永遠 1:1 貼上**：來源矩形與目標矩形同尺寸、落在整數像素上，所以是複製而不是重新取樣；畫布是往外長大，不是把照片縮小。24 MP 的照片套拍立得框會輸出約 33 MP。
- 邊框單位是照片短邊的 1%，所以任何解析度、任何比例看起來都一樣。

## 比例與適配

| 功能 | 說明 |
|---|---|
| 自動適配 | 3:2、4:3、1:1、4:5、9:16、16:9 的照片都直接可用，邊框依短邊縮放 |
| 輸出比例 | 依照片／1:1／4:5／3:2／4:3／3:4／16:9／9:16 |
| Fit | 保留整張照片，不足的地方補留白；可調左右、上下位置 |
| Fill | 裁切照片使「照片＋邊框」剛好是指定比例 |
| Crop | 先把照片裁成指定比例再套邊框 |
| 厚度 | ×0.5 – ×2 |
| 顏色 | 11 個紙色，或保留該款原色；文字顏色會依紙色自動選深或淺 |
| 文字 | 標題、說明／手寫文字可自訂；日期、拍攝資訊可各自關閉 |

邊框上的日期、光圈快門 ISO、相機型號都來自照片的 EXIF。**沒有的資料就不印**，不會編造；測試有檢查「沒有日期時邊框上不出現任何年份」。

## 與時間戳整合

時間戳畫在照片上、邊框畫在外面，兩者可同時開。邊框自己的日期欄位與時間戳用同一個時間來源；時間戳關閉時，邊框只會用 EXIF 拍攝時間。

## 操作

- 編輯器「邊框」分頁：分類（全部／收藏／最近＋7 類）、搜尋、縮圖列（用你正在編輯的那張照片即時合成）、即時預覽、收藏、上面表格的所有調整。
- Filters 頁右上「邊框圖庫」：全部邊框以適合它的示範照片呈現，可分類、搜尋、收藏、放大看。
- 設定跟著照片存（非破壞性）：重新開啟同一張照片會還原，選「無邊框」即移除；原始檔不會被改。

## 邊框清單

| # | ID | 名稱 | 中文 | 分類 |
|---|---|---|---|---|
| 1 | `film35` | 35mm Negative | 35mm 底片框 | 底片 |
| 2 | `film35_vertical` | 35mm Vertical Strip | 直式 35mm 片條 | 底片 |
| 3 | `film120` | 120 Medium Format | 120 中片幅 | 底片 |
| 4 | `film135_strip` | 135 Film Strip | 135 膠卷片條 | 底片 |
| 5 | `contact_sheet` | Contact Sheet | 接觸印樣 | 底片 |
| 6 | `analog_info` | Analog Data Back | 類比相機資訊框 | 底片 |
| 7 | `slide_mount` | Slide Mount | 幻燈片片框 | 底片 |
| 8 | `negative_sleeve` | Negative Sleeve | 底片保存頁 | 底片 |
| 9 | `film_leader` | Film Leader | 片頭漏光 | 底片 |
| 10 | `super8` | Super 8 Frame | Super 8 小畫幅 | 底片 |
| 11 | `retro_print` | Retro Lab Print | 復古沖印相紙 | 底片 |
| 12 | `print_round` | Rounded Lab Print | 圓角沖印相紙 | 底片 |
| 13 | `darkroom_proof` | Darkroom Proof | 暗房樣張 | 底片 |
| 14 | `edge_code` | Edge Code | 片邊碼 | 底片 |
| 15 | `pano_film` | Panorama Film | 寬景底片 | 底片 |
| 16 | `min_white` | Clean White | 極簡白框 | 極簡 |
| 17 | `min_black` | Clean Black | 極簡黑框 | 極簡 |
| 18 | `hairline` | Hairline | 超細線框 | 極簡 |
| 19 | `big_mat` | Gallery Mat | 大留白卡紙 | 極簡 |
| 20 | `asym_left` | Asymmetric Left | 不對稱留白 | 極簡 |
| 21 | `bottom_heavy` | Bottom Weighted | 下方留白 | 極簡 |
| 22 | `top_title` | Top Title | 上方標題留白 | 極簡 |
| 23 | `double_line` | Double Rule | 雙線框 | 極簡 |
| 24 | `corner_marks` | Corner Marks | 角落記號 | 極簡 |
| 25 | `soft_shadow` | Floating Print | 浮起陰影 | 極簡 |
| 26 | `inset_line` | Inset Keyline | 內嵌細線 | 極簡 |
| 27 | `float_caption` | Caption Rule | 圖說細線 | 極簡 |
| 28 | `side_stripe` | Side Margin | 側邊直排日期 | 極簡 |
| 29 | `grey_card` | Grey Card | 中性灰卡 | 極簡 |
| 30 | `wide_top_bottom` | Letter Margin | 上下大留白 | 極簡 |
| 31 | `jp_print` | Nichijou Print | 日系相紙框 | 日系 |
| 32 | `jp_retro_date` | Showa Date | 日系復古日期框 | 日系 |
| 33 | `jp_blank` | Yohaku | 日系留白（余白） | 日系 |
| 34 | `jp_cream` | Milk Cream | 清新奶油色框 | 日系 |
| 35 | `jp_magazine` | Kurashi Magazine | 日系雜誌排版 | 日系 |
| 36 | `jp_vertical` | Tategaki | 日系直書標題 | 日系 |
| 37 | `jp_seal` | Hanko Seal | 日系落款印章 | 日系 |
| 38 | `jp_note` | Film Note | 日系手寫便箋 | 日系 |
| 39 | `jp_diary` | Day Number | 日系日記大日期 | 日系 |
| 40 | `jp_blush` | Sakura Paper | 日系淡粉相紙 | 日系 |
| 41 | `jp_postcard` | Ehagaki Postcard | 日系明信片 | 日系 |
| 42 | `cine_bars` | Scope Letterbox | 電影寬黑框 | 電影 |
| 43 | `cine_subtitle` | Subtitle | 電影字幕區 | 電影 |
| 44 | `cine_still` | Production Still | 劇照資訊框 | 電影 |
| 45 | `cine_slate` | Clapper Slate | 場記板 | 電影 |
| 46 | `cine_timecode` | Timecode Dailies | 電影膠片資訊框 | 電影 |
| 47 | `cine_academy` | Academy Pillarbox | 學院比例直黑邊 | 電影 |
| 48 | `cine_poster` | One-Sheet Poster | 電影海報 | 電影 |
| 49 | `cine_white` | White Letterbox | 白色寬銀幕 | 電影 |
| 50 | `cine_viewfinder` | Camera Viewfinder | 攝影機觀景窗 | 電影 |
| 51 | `cine_ticks` | Scope Ruler | 寬銀幕刻度 | 電影 |
| 52 | `cine_lower_third` | Lower Third | 片名字卡 | 電影 |
| 53 | `instant_classic` | Instant Classic | 拍立得框 | 拍立得 |
| 54 | `instant_caption` | Instant Handwritten | 拍立得手寫 | 拍立得 |
| 55 | `instant_square` | Square Instant | 方形相紙 | 拍立得 |
| 56 | `instant_retro` | Rainbow Instant | 復古拍立得彩條 | 拍立得 |
| 57 | `instant_thick` | Thick Base | 厚底白框 | 拍立得 |
| 58 | `instant_shadow` | Print on Table | 相紙陰影 | 拍立得 |
| 59 | `instant_wide` | Wide Instant | 寬幅拍立得 | 拍立得 |
| 60 | `instant_mini` | Mini Instant | 迷你拍立得 | 拍立得 |
| 61 | `instant_black` | Black Instant | 黑色拍立得 | 拍立得 |
| 62 | `instant_tape` | Taped Instant | 膠帶拍立得 | 拍立得 |
| 63 | `instant_label` | Date Label | 拍立得日期標籤 | 拍立得 |
| 64 | `mag_cover` | Magazine Cover | 封面式排版 | 雜誌 |
| 65 | `mag_spread` | Feature Spread | 攝影雜誌跨頁 | 雜誌 |
| 66 | `fig_caption` | Figure Caption | 編輯圖說 | 雜誌 |
| 67 | `title_block` | Headline Block | 照片標題框 | 雜誌 |
| 68 | `portfolio` | Portfolio Plate | 作品集展示框 | 雜誌 |
| 69 | `gallery_label` | Museum Label | 展覽作品標籤 | 雜誌 |
| 70 | `index_card` | Archive Index | 檔案索引卡 | 雜誌 |
| 71 | `newspaper` | Broadsheet | 報紙頭版 | 雜誌 |
| 72 | `swiss_grid` | Swiss Grid | 瑞士風格網格 | 雜誌 |
| 73 | `color_story` | Colour Story | 色票故事 | 雜誌 |
| 74 | `exhibition` | Exhibition Poster | 展覽海報 | 雜誌 |
| 75 | `retro_color` | Retro Pop | 復古彩色框 | 創意 |
| 76 | `torn_paper` | Torn Paper | 撕紙邊框 | 創意 |
| 77 | `paper_texture` | Cotton Rag | 紙張紋理框 | 創意 |
| 78 | `journal` | Travel Journal | 手帳風格 | 創意 |
| 79 | `collage` | Paper Collage | 拼貼風格 | 創意 |
| 80 | `geometric` | Diagonal Bands | 幾何斜帶 | 創意 |
| 81 | `blur_bg` | Blurred Backdrop | 模糊背景延伸 | 創意 |
| 82 | `gradient_palette` | Palette Gradient | 照片配色漸層 | 創意 |
| 83 | `postage` | Postage Stamp | 郵票齒孔 | 創意 |
| 84 | `ticket` | Admit One | 票根 | 創意 |
| 85 | `graph_paper` | Graph Paper | 方格紙剪貼 | 創意 |
| 86 | `neon` | Neon Outline | 霓虹外框 | 創意 |
| 87 | `label_maker` | Label Maker | 打標機標籤 | 創意 |

## 測試

見 `IMAGE_EXPORT_TEST_REPORT.md`。重點：87 款 × 6 種照片比例 × 6 種輸出比例 × Fit/Fill × 3 種厚度 = 18,792 種排版全部檢查「照片不拉伸、不超出畫布、輸出比例正確、時間戳留在照片內」。

## 限制

1. 字體用系統內建字族（無襯線、襯線、等寬、窄體、手寫、隨筆）。手寫體在不同手機上外觀會不同；電腦端預覽圖的字體也與手機不同，版面位置相同。七段顯示器日期是自己畫的多邊形，各裝置一致。
2. 沒有做需要兩張以上照片的版型（半格、拼貼多圖）。
3. 邊框與時間戳的變更不在「還原／重做」的歷史裡（濾鏡與調整才有）；可以直接關閉或改回。
4. 50 MP 照片加大邊框時記憶體可能不足，此時會自動以 70% 尺寸輸出邊框版本並在訊息中說明。
5. App 內的畫面沒有在實機上看過（見 `KNOWN_ISSUES.md`）。
