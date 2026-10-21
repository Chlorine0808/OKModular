# 構造体システム: JSON フォーマットリファレンス

このリファレンスでは、マルチブロック構造体を定義するための JSON 形式について説明します。ファイルは `config/okmodular/structures/` に配置してください。

## 関連ドキュメント

- [構造 IO](./STRUCTURE_IO.md)：この文書と同じ `layers` / `mappings` の書き方で、
  ブロックの配置そのものをレシピの入出力にする仕組みです。ファイルは `config/okmodular/structure_io/` に置きます。
  予約記号の扱いは一部違います（`Q` は固定ではなくアンカーの既定値）。
- [式のリファレンス](../recipes/EXPRESSION_REFERENCE.md)：性能係数の式で使える名前
- [機械の稼働条件](../machinery/MACHINE_CONDITIONS.md)：`conditions` の書き方

---

## 1. ファイル構成
ファイルには、オブジェクトを 1 つ、またはオブジェクトの配列を書けます。
`default` という名前の特別なオブジェクトには、共通のマッピングを定義できます。

## 2. 主要なプロパティ

| プロパティ | 型 | 説明 |
| :--- | :--- | :--- |
| `name` | 文字列 | 識別子（必須、一意になるように設定） |
| `displayName` | 文字列 | 機械の表示名（任意） |
| `recipeGroup` | 文字列/配列 | この構造体が対応するレシピグループ |
| `mappings` | オブジェクト | 文字記号とブロックの対応 |
| `layers` | 配列 | 構造体の垂直方向のスライス（上から下へ） |
| `requirements` | 配列 | 構造の認識に必要な要件（ポートなど） |
| `tintColor` | 文字列 | 機械の色（例: `#FF0000`） |
| `speedMultiplier` | Float / 式 | 処理速度（デフォルト: 1.0） |
| `energyMultiplier` | Float / 式 | エネルギー消費（デフォルト: 1.0） |
| `batchMin` | Integer / 式 | レシピの最小バッチサイズ（デフォルト: 1） |
| `batchMax` | Integer / 式 | レシピの最大バッチサイズ（デフォルト: 1） |
| `tier` | Integer | マシンのティア（デフォルト: 0） |
| `controllerOffset` | 配列 `[a, b, c]` | コントローラが構造体のどこに立つか。→ [§7](#controlleroffset--コントローラの立ち位置) |
| `externalPorts` | 配列（記号） | 外部と繋がってよいポート記号。→ [§7](#externalports--外から繋げるポート) |
| `inputOnly` / `outputOnly` / `bothOnly` | 配列（記号） | その記号の面 IO を固定する。→ [§7](#externalports--外から繋げるポート) |
| `tierStructures` | 配列 | 増設で Tier や個数が上がる副構造。→ [§7](#tierstructures--増設で-tier-が上がる) |
| `modVersion` | 文字列 | 書き出し時に自動で付く、移行用のバージョン。手では書かない |
| `defaultFacing` | 文字列 | 構造体のデフォルトの向き（`UP`, `DOWN`）。コントローラの向きを指定する。指定がない場合は横向き |
| `durationPolicy` | 文字列 | 式で書かれたレシピの `duration` をいつ評価するか（`onStart` / `perTick`、デフォルト: `onStart`） |
| `conditions` | 配列/オブジェクト | [機械の稼働条件](../machinery/MACHINE_CONDITIONS.md) |
| `conditionPolicy` | 文字列 | 稼働中に `conditions` が崩れたときの挙動（`pause` / `abort`、デフォルト: `pause`） |

## 3. 性能係数

### 式で書く

`speedMultiplier` / `energyMultiplier` / `batchMin` / `batchMax` は、数値の代わりに式で書けます。
式からは機械自身の状態（`tier`、`energy`、`item("...")` など）を読めます。
使える名前は [式のリファレンス](../recipes/EXPRESSION_REFERENCE.md) にあります。

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
> 別の係数を読むのは問題ありません。たとえば `"speedMultiplier": "energy_multi"` は動きます。

### durationPolicy

レシピの `duration` が式で書かれている場合の評価タイミングを決めます。数値で書かれた `duration` には影響しません。

| 値 | 挙動 |
|----|------|
| `onStart`（デフォルト） | レシピ開始時に 1 回評価し、そのレシピ実行中は固定 |
| `perTick` | 毎 tick 評価し直す。天候や月齢など稼働中に変わるものを反映したいとき |

> [!CAUTION]
> `perTick` では進捗バーの分母が変わります。値が下がるとバーが急に進み、
> すでに進んだ作業量を値が下回った時点でレシピが完成します。

`durationPolicy` は、`speedMultiplier` などを式で書いた場合に毎 tick 再評価される `dynamic` フラグとは別のものです。
`dynamic` は性能倍率を、`durationPolicy` はレシピの作業量を対象とします。

## 4. マッピング (Mappings)
マッピングは、`layers` 内の文字とブロック ID を対応づけます。

### 文字列形式
`"F": "okmodular:basaltStructure:*"`（メタデータにはワイルドカード `*` を使えます）

### オブジェクト形式 (一部実装予定)
```json
"S": {
  "block": "okmodular:casing_plain:0",
  "max": 1
}
```

### 複数候補の指定
```json
"A": {
  "blocks": [
    "okmodular:casing_plain:0",
    "okmodular:casing_reinforced:0"
  ]
}
```

`blocks` の要素はオブジェクトでも書けます。ブロック ID は `id` に入れます。

```json
"A": {
  "blocks": [
    { "id": "okmodular:casing_plain:0" },
    { "id": "okmodular:casing_reinforced:0" }
  ]
}
```

> [!WARNING]
> オブジェクト形式で `id` を書き忘れると、**その候補だけがエラーにならずに消えます**。
> 残りの候補で構造は成立してしまうので、症状は「なぜかこのブロックだけ受け付けない」という形で現れます。

### 部品ごとの Tier

使用した材料（ブロック）に応じて、マシンの一部に Tier を割り当てられます。
この設定はトップレベルの項目ではなく、マッピングの中に書きます。

```json
"mappings": {
  "G": {
    "component": "glass",
    "tiers": {
      "okmodular:glass:1": 1,
      "okmodular:glass:2": 2
    }
  },
  "C": {
    "component": "casing",
    "tiers": {
      "okmodular:casing_reinforced:0": 1,
      "okmodular:casing_reinforced:1": 2,
      "okmodular:casing_reinforced:2": 3
    }
  }
}
```

`component` が部品の名前、`tiers` が「そのブロックなら何 Tier か」の表です。
レシピ側で `"tier": { "glass": 2 }` と指定されていれば、
上の設定では `okmodular:glass:2` を使って組んだ構造体でのみそのレシピが有効になります。

> [!WARNING]
> この設定は以前、トップレベルの `tierMap` として説明されていました。しかし、そのような項目は存在せず、
> 読み込む処理もありません。書いても**エラーもログも出ずに無視される**ため、
> この誤りは「Tier を設定したのにレシピが通らない」という症状でしか現れませんでした。
> 正しくは、`mappings` の中に `component` と `tiers` を書きます。

## 5. 要件 (Requirements)
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
1.5.1.4 以降は、各タイプをキーにしたオブジェクト形式も使えます。
```json
"requirements": {
    "energyInput": { "min": 1 },
    "itemOutput": 1,
    "fluidInput": { "min": 1, "max": 4 }
}
```
※ 値が数値の場合は、`min` として扱われます。

## 6. 予約記号 (Reserved Symbols)

構造体システムでは、以下の記号が特殊な意味を持ちます。

### システム予約記号 (必須)
これらの記号は `mappings` で上書きできません。

| 記号 | 意味 | 説明 | レシピから指せるか |
| :--- | :--- | :--- | :--- |
| `Q` | コントローラ | 構造体に必ず1つ必要です | ✅ |
| `_` | 空気 (Air) | 強制的な空気ブロックとして扱われます | ✅ |
| (スペース) | 任意 (Any) | バリデーション対象外の空間です | ❌ ロードエラー |

### 「指せる」の意味

レシピが構造体のマスを扱う手段は `"symbol"` だけです
（`block` / `structure` の入出力、`per_position_probability`、`random_block_output`、
`harvest_block`、`nbt` 式）。そして `"symbol"` で指せるのは、位置が記録されている記号だけです。

スペースの位置は記録できません。スペースは判定の対象外なので、engine がそのマスを一度も訪れず、
記録しようがないからです。そのため、レシピでスペースを指すとロード時にエラーになります。
実行時に「対象 0 マスで正常終了」として何もしないまま終わるより、書いた時点で止まるほうがよいという判断です。

> [!WARNING]
> **`_` にブロックを置くと機械が解体されます。**
> `_` は「空気」という意味ではなく、「ここが空気でなければこの機械は成立しない」という意味です。
> そのため、`_` をブロック出力の対象にすると、次の構造判定で構造が崩れます。
> ただし、位置を取れること自体は正しい（パーティクルや判定には使える）ので、ロード時には止めません。
> 置いた結果は実機ですぐ分かります。

## 7. 配置と外部接続

### `controllerOffset` — コントローラの立ち位置

`layers` の中でコントローラ（`Q`）がどこに来るかを、アンカーからのズレで指定します。

```json
"controllerOffset": [2, 0, 2]
```

要素の順は `layers` と同じ `[a, b, c]`（横・高さ・奥行き）です。3 要素未満の指定は、エラーにならずに無視されます。
指定しない場合は、`Q` を書いた位置がそのまま使われます。

### `externalPorts` — 外から繋げるポート

既定では、構造体の中のポートは機械の内側だけを向きます。
外のパイプやケーブルを繋ぎたい記号は、ここに並べます。

```json
"externalPorts": ["E", "I"],
"inputOnly":  ["I"],
"outputOnly": ["O"]
```

| キー | 意味 |
|---|---|
| `externalPorts` | 外部接続を許す記号。面ごとの IO はプレイヤーがレンチで変えられる |
| `inputOnly` | その記号を搬入固定にする。レンチで変えられない |
| `outputOnly` | 同じく搬出固定 |
| `bothOnly` | 同じく双方向固定 |

値は文字列の配列で、先頭の 1 文字だけが記号として読まれます（`"EE"` は `E`）。
空文字は無視されます。

> [!NOTE]
> `inputOnly` などは `externalPorts` とは**別の一覧**です。
> 固定したい記号は両方に書いてください。

### `tierStructures` — 増設で Tier が上がる

`tierStructures` は、本体の周りに副構造を建てると部品の Tier や個数が上がる仕組みです。

```json
"tierStructures": [
  {
    "name": "coolant_tower",
    "tier": 2,
    "component": "cooling",
    "mode": "tier",
    "offsets": [
      [3, 0, 0],
      { "target": [-3, 0, 0], "anchor": [0, 1, 0] }
    ]
  }
]
```

| キー | 既定 | 意味 |
|---|---|---|
| `name` | 必須 | 副構造の名前。`name` の無い項目は、エラーにならずに読み飛ばされます |
| `tier` | `1` | 成立したときに与える Tier（`mode: "count"` なら数える単位） |
| `component` | `"structure"` | どの部品の Tier を上げるか |
| `mode` | `"tier"` | `"tier"` = Tier を上げる / `"count"` = 個数を数える |
| `offsets` | なし | 本体から見てどこに建てられるか。複数書けば複数箇所 |

`offsets` の要素は 2 通りの形で書けます。

- 配列 `[a, b, c]`：本体から見た副構造の位置
- オブジェクト `{ "target": [...], "anchor": [...] }`：副構造側のどの点を合わせるかまで指定

どちらの形でも、3 要素未満の項目はエラーにならずに、その項目だけが読み飛ばされます。

## 8. コマンド
- `/okmodular reload`: 構造体定義、レシピ定義、Tier定義をリロードします。
