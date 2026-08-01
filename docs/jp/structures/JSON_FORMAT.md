# 構造体システム: JSON フォーマットリファレンス

このリファレンスでは、マルチブロック構造体を定義するための JSON 形式について説明します。ファイルは `config/okmodular/structures/` に配置してください。

## 1. ファイル構成
ファイルには単一のオブジェクト、またはオブジェクトの配列を含めることができます。
`default`という名前の特殊なオブジェクトを使用して、共通のマッピングを定義できます。

## 2. 主要なプロパティ

| プロパティ | 型 | 説明 |
| :--- | :--- | :--- |
| `name` | 文字列 | 識別子（必須、一意になるように設定） |
| `displayName` | 文字列 | 機械の表示名（任意） |
| `recipeGroup` | 文字列/配列 | この構造体が対応するレシピグループ |
| `mappings` | オブジェクト | 文字記号とブロックの対応 |
| `layers` | 配列 | 構造体の垂直方向のスライス（上から下へ） |
| `requirements` | 配列 | 構造認識要件（ポートなど） |
| `tintColor` | 文字列 | 機械の色（例: `#FF0000`） |
| `speedMultiplier` | Float / 式 | 処理速度（デフォルト: 1.0） |
| `energyMultiplier` | Float / 式 | エネルギー消費（デフォルト: 1.0） |
| `batchMin` | Integer / 式 | レシピの最小バッチサイズ（デフォルト: 1） |
| `batchMax` | Integer / 式 | レシピの最大バッチサイズ（デフォルト: 1） |
| `tier` | Integer | マシンのティア（デフォルト: 0） |
| `tierMap` | オブジェクト | 構造体の各パーツが提供する Tier の定義 |
| `defaultFacing` | 文字列 | 構造体のデフォルトの向き（`UP`, `DOWN`）。**コントローラの向きを指定する。**指定がない場合は横向き |
| `durationPolicy` | 文字列 | 式で書かれたレシピの `duration` をいつ評価するか（`onStart` / `perTick`、デフォルト: `onStart`） |
| `conditions` | 配列/オブジェクト | [機械の稼働条件](../machinery/MACHINE_CONDITIONS.md) |
| `conditionPolicy` | 文字列 | 稼働中に `conditions` が崩れたときの挙動（`pause` / `abort`、デフォルト: `pause`） |

### durationPolicy

レシピの `duration` が式で書かれている場合の評価タイミングを決めます。数値で書かれた `duration` には影響しません。

| 値 | 挙動 |
|----|------|
| `onStart`（デフォルト） | レシピ開始時に 1 回評価し、そのレシピ実行中は固定 |
| `perTick` | 毎 tick 評価し直す。天候や月齢など稼働中に変わるものを反映したいとき |

> [!CAUTION]
> `perTick` は進捗バーの分母を動かします。値が下がるとバーが飛び、
> すでに進んだ作業量を下回った瞬間にレシピが完成します。

`speedMultiplier` などを式で書いた場合に毎 tick 再評価される `dynamic` フラグとは別です。
`dynamic` は性能倍率、`durationPolicy` はレシピの作業量を対象とします。

### 性能係数を式で書く

`speedMultiplier` / `energyMultiplier` / `batchMin` / `batchMax` は数値の代わりに式を書けます。
式からは**機械自身の状態が見えます**（`tier`、`energy`、`item("...")` など。
使える名前は [式のリファレンス](../recipes/EXPRESSION_REFERENCE.md)）。

```json
"speedMultiplier": "1 + tier * 0.25",
"batchMax": "clamp(floor(energy / 10000), 1, 8)"
```

| フィールド | 評価タイミング |
|---|---|
| `speedMultiplier` | 稼働中は毎 tick |
| `batchMin` / `batchMax` | レシピ開始時（バッチサイズを決めるとき） |
| `energyMultiplier` | レシピ側が `energy_multi` を読んだとき |

> [!WARNING]
> **その係数自身を読むことはできません。** `speed_multi` は `speedMultiplier` の評価結果そのものなので、
> `"speedMultiplier": "speed_multi * 2"` は循環します。循環した係数は中立値（倍率 1.0 / バッチ 1）になり、
> `logs/latest.log` に 1 回だけエラーが出ます。
> **別の係数を読むのは問題ありません** — `"speedMultiplier": "energy_multi"` は動きます。

### 2.2 Tier Map の詳細
`tierMap` を使用すると、使用する材料（ブロック）に応じてマシンの一部に特定の Tier を割り当てることができます。
```json
"tierMap": {
  "glass": {
    "okmodular:glass:1": 1,
    "okmodular:glass:2": 2
  },
  "casing": {
    "okmodular:modularMachineCasing:0": 1,
    "okmodular:modularMachineCasing:1": 2,
    "okmodular:modularMachineCasing:2": 3
  }
}
```
レシピ側で `"tier": { "glass": 2 }` と指定されている場合、上記の設定では `glass:2` 以上のブロックを使用している構造体でのみそのレシピが有効になります。

## 3. マッピング (Mappings)
マッピングは、`layers` 内の文字をブロック ID にリンクします。

### 文字列形式
`"F": "okmodular:basaltStructure:*"` (メタデータにワイルドカード `*` が使用可能)

### オブジェクト形式 (一部実装予定)
```json
"S": {
  "block": "okmodular:modularMachineCasing:0",
  "max": 1
}
```

### 複数候補の指定
```json
"A": {
  "blocks": [
    "omoshiroikamo:modifierNull:0",
    "omoshiroikamo:modifierSpeed:0"
  ]
}
```

## 4. 要件 (Requirements)
要件は、マシンが備えていなければならない内部コンポーネント（ポート）を定義します。

利用可能なタイプ: `itemInput`, `itemOutput`, `fluidInput`, `fluidOutput`, `energyInput`, `energyOutput`, `manaInput`, `manaOutput`, `gasInput`, `gasOutput`, `essentiaInput`, `essentiaOutput`, `visInput`, `visOutput`

### 配列形式
```json
"requirements": [
    { "type": "energyInput", "min": 1 },
    { "type": "itemOutput", "min": 2 }
]
```

### オブジェクト形式
1.5.1.4以降、各タイプをキーとしたオブジェクト形式もサポートされています。
```json
"requirements": {
    "energyInput": { "min": 1 },
    "itemOutput": 1,
    "fluidInput": { "min": 1, "max": 4 }
}
```
※ 値が数値の場合は、`min` として扱われます。

## 5. 予約記号 (Reserved Symbols)

構造体システムでは、以下の記号が特殊な意味を持ちます。

### 5.1 システム予約記号 (必須)
**これらの記号は`mappings` で上書きすることはできません。**

| 記号 | 意味 | 説明 |
| :--- | :--- | :--- |
| `Q` | コントローラー | **構造体に必ず1つ必要です** |
| `_` | 空気 (Air) | 強制的な空気ブロックとして扱われます |
| (スペース) | 任意 (Any) | バリデーション対象外の空間です |

## 6. コマンド
- `/okmodular reload`: 構造体定義、レシピ定義、Tier定義をリロードします。

## 7. 関連

- [構造 IO](./STRUCTURE_IO.md) — ここと同じ `layers` / `mappings` の書き方で、
  ブロックの配置そのものをレシピの入出力にする仕組み。ファイルは `config/okmodular/structure_io/`。
  **予約記号の扱いが一部違います**（`Q` は固定ではなくアンカーの既定値）。
