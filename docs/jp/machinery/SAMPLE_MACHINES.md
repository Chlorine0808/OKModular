# サンプル機械ガイド

Modular モジュールの機能を段階的に紹介する 5 つのサンプル機械です。  
ファイルは `config/omoshiroikamo/modular/` 以下に配置されています。

---

## 早見表

| # | 機械名 | グループ | 主なデモ内容 |
|---|--------|----------|-------------|
| 1 | Basic Crusher | `BasicCrusher` | 最小構成・複数アイテム出力 |
| 2 | Steam Boiler | `SteamBoiler` | 流体 I/O・`perTick`・`batchMax` |
| 3 | Alloy Forge | `AlloyForge` | `tierMap`・`requiredTier`・Expression 引数 |
| 4 | Lunar Furnace | `LunarFurnace` | `expression` 条件・月変数・amount 式 |
| 5 | Weather Harvester | `WeatherHarvester` | 天候条件・`chance`/`bonus` デコレータ |

---

## Sample 1: Basic Crusher（基本粉砕機）

**ファイル**: `sample_01_basic_crusher`  
**テーマ**: 最小限の動く機械。アイテム入力・アイテム出力・エネルギー入力のみ。

### 構造体のポイント

`requirements` でアイテム入力・出力・エネルギー入力が **最低 1 個ずつ** 必要と宣言しています。  
ポートを 1 個も置かない状態では構造体が完成扱いにならないので注意。

### レシピのポイント

| レシピ | 入力 | 出力 | ポイント |
|--------|------|------|----------|
| Stone to Gravel | 石 × 1 | 砂利 × 1 | 最も単純な 1:1 変換 |
| Cobblestone to Sand and Gravel | 丸石 × 1 | 砂 × 1 + 砂利 × 1 | **出力が 2 種類** |
| Disassemble Iron Block | 鉄ブロック × 1 | 鉄インゴット × 9 | 高コスト・高量産レシピ |

「Cobblestone to Sand and Gravel」は outputs に 2 アイテムを並べることで複数出力になる例です。

---

## Sample 2: Steam Boiler（蒸気ボイラー）

**ファイル**: `sample_02_steam_boiler`  
**テーマ**: 流体 I/O と `perTick` 消費/生産・`batchMax` による大量生産。

### 構造体のポイント
- `batchMax: 4` — 最大 4 バッチ同時処理。同じ構造体でも 4 倍の速度で蒸気を生産できます。
- `P` スロットに `modular_fluid_input` と `modular_fluid_output` が含まれており、流体タンクポートを設置可能。
- `requirements` に `fluidInput` と `fluidOutput` を指定しているため、流体ポートがないと構造体が完成しません。

### レシピのポイント

**Coal Boiler**  
```json
入力: coal × 1、water 10mB/t
出力: steam 80mB/t、duration: 200t
```
アイテム燃料（固体）と液体入力を組み合わせるパターン。  
石炭 1 個で 200 tick 動作し、合計 16,000mB の蒸気を生産します。

**Lava Boiler**  
```json
入力: lava 10mB/t、water 1,000mB/t
出力: steam 200mB/t、duration: 100t
```
入力が全て流体のパターン。石炭より効率が高い上位レシピ。

> **バッチについて**  
> `batchMax: 4` の構造体に 4 バッチ分のリソースがある場合、duration は変わらず入出力量が 4 倍になります。  
> `perTick` でも同様にバッチ分が自動でスケールされます。

---

## Sample 3: Alloy Forge（合金鍛錬炉）

**ファイル**: `sample_03_alloy_forge`  
**テーマ**: `tierMap` でケーシングの品質を Tier 化し、高 Tier レシピを解禁する。

### 構造体のポイント

**tierMap の設定**  
```json
"tierMap": {
  "grade": {
    "omoshiroikamo:casing_plain:0": 1,
    "omoshiroikamo:casing_plain:1": 2,
    "omoshiroikamo:casing_plain:2": 3,
    "omoshiroikamo:casing_plain:3": 4
  }
}
```
`F` スロットに置くケーシングのメタ値（0〜3）によって `grade` Tier が決まります。  
メタ 0 = Tier 1（最低）、メタ 3 = Tier 4（最高）。

**requirements の変化**  
`itemInput` の `min: 2` — 入力スロットが 2 個以上ないと稼働しません（合金 = 複数素材のイメージ）。

### レシピのポイント

**Expression 引数（`duration`・`energy`・`amount` すべてに式が使える）**

```json
"duration": 200
"energy":   "floor(500 * energy_multi)"
"amount":   "3 + tier"
```

- `energy_multi`: 構造体側で設定するエネルギー倍率（デフォルト 1.0）
- `tier`: マシンの現在の Tier（`tierMap` で決まる）

> [!IMPORTANT]
> **`duration` に `speed_multi` を書かないでください。**
> `duration` は「作業量」であって時間ではありません。エンジンは 1 tick ごとに
> 速度倍率のぶんだけ作業を進めるので、実際の所要時間は `duration ÷ speed_multi` に既になっています。
> ここに `floor(200 / speed_multi)` と書くと倍率が **2 回** 掛かります（実効 `200 / speed_multi²`）。
>
> `duration` に式を書くのは、**マシンの性能とは無関係な外的要因**で作業量を変えたいときです
> （天候・月齢・TileEntity の NBT など）。エネルギー倍率は逆で、エンジン側で適用されないため
> `energy` に `energy_multi` を書くのが正しい書き方です。

**requiredTier によるレシピ解禁**

| レシピ | 必要 grade | 出力 |
|--------|-----------|------|
| Basic Alloy | 1（なんでも可） | 金ナゲット `3 + tier` 個 |
| Emerald Synthesis | 2 以上 | エメラルド `1 + floor(tier / 2)` 個 |
| Nether Star Fusion | 4（最高） | ネザースター × 1 |

Tier が高いほど出力量も増える設計例です。

---

## Sample 4: Lunar Furnace（月光鍛錬炉）

**ファイル**: `sample_04_lunar_furnace`  
**テーマ**: 夜・月齢という環境条件でレシピの有効/無効と出力量を切り替える。

### 構造体のポイント
- `defaultFacing: "UP"` — コントローラーの正面が上を向く（見た目の方向設定のみ、機能には影響なし）

### レシピのポイント

**expression 条件**  
```json
"conditions": [
  { "expression": "is_night == 1 && can_see_sky == 1" }
]
```
夜かつ空が見える場所に設置した場合のみ動作。昼間や地下では条件が満たされずレシピが非表示になります。

**月齢による出力変動（amount 式）**  
```json
"amount": "2 + (moon_phase == 0) * 6"
```
- 通常の夜: グロウストーンダスト × 2
- 満月（`moon_phase == 0`）: グロウストーンダスト × **8**

`moon_phase` は 0（満月）〜 7（満月の前日）の整数です。  
`(moon_phase == 0)` は真なら 1、偽なら 0 を返すので、満月時に +6 されます。

**Full Moon Fusion**  
```json
"conditions": [{ "expression": "moon_phase == 0 && is_night == 1 && can_see_sky == 1" }]
```
満月の夜だけ解禁されるレシピ。条件を絞り込むほど `expression` の AND 連結が便利です。

---

## Sample 5: Weather Harvester（気象収集機）

**ファイル**: `sample_05_weather_harvester`  
**テーマ**: 天候ごとに別レシピを持ち、`chance` と `bonus` デコレータで出力をランダム化する。

### 構造体のポイント
`defaultFacing: "UP"` — 空に向けた屋外設置を想定した向き設定。

### レシピのポイント

**天候条件の書き方（expression で統一）**

| レシピ | 条件 |
|--------|------|
| Solar Collection | `is_raining == 0 && is_thundering == 0 && is_day == 1 && can_see_sky == 1` |
| Rain Alchemy | `is_raining == 1 && is_thundering == 0 && can_see_sky == 1` |
| Thunder Strike | `is_thundering == 1 && can_see_sky == 1` |

`{ "weather": "rain" }` 形式でも書けますが、複数条件の AND を一行にまとめたいときは expression が便利です。

**Rain Alchemy の humidity 参照**  
```json
"amount": "1 + floor(humidity * 2)"
```
設置したバイオームの湿度（0.0〜1.0）に応じて出力量が変わります。  
砂漠（humidity ≈ 0.0）では 1 個、沼地（humidity ≈ 0.9）では 2 個。

**デコレータの重ね掛け（Thunder Strike）**  
```json
"decorators": [
  { "chance": 0.25 },
  {
    "bonus": {
      "chance": 0.05,
      "outputs": [{ "item": "minecraft:nether_star", "amount": 1 }]
    }
  }
]
```
- `chance: 0.25` — レシピ全体が 25% の確率でのみ成功する
- `bonus` — 成功した場合、さらに 5% でネザースターが追加ドロップ

デコレータは複数を `decorators` 配列に並べることで重ね掛けできます。

---

## カスタマイズのヒント

これらのサンプルはあくまでデモ用の素材です。実際の mod 追加アイテムに差し替えることを想定しています。

- アイテム ID を差し替えるだけで動作します（`minecraft:xxx` → `modid:xxx`）
- `tintColor` を変えると NEI のプレビューや構造体のガイドカラーが変わります
- `batchMax` を増やすと同一構造体でより大量処理できるようになります
- `tierMap` のブロック ID を差し替えれば、任意の素材で Tier を表現できます
