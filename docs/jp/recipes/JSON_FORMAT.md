# レシピシステム: JSON フォーマットリファレンス

レシピは `config/okmodular/recipes/*.json` で定義されます。

## 📚 関連ドキュメント

- [条件 (Conditions)](./CONDITIONS.md) - レシピが動く / 止まる判定
- [デコレータ (Decorators)](./DECORATORS.md) - 確率・ボーナス出力・触媒
- [式パーサー 変数・関数リファレンス](./EXPRESSION_REFERENCE.md) - 変数・関数のリスト
- [式の実用例集](./EXPRESSION_EXAMPLES.md)

---

## 1. 基本の構成

```json
{
  "group": "マシン名",
  "recipes": [
    { ... レシピ定義 1 ... },
    { ... レシピ定義 2 ... }
  ]
}
```

## 2. レシピのプロパティ

| キー | 型 | 意味 |
|---|---|---|
| `group` / `machine` | 文字列 | レシピグループ（どのマシンのレシピか） |
| `duration` / `time` | 数値または式 | 作業量。**時間ではない**（速度倍率で割られる） |
| `inputs` / `input` | 配列 | 入力 |
| `outputs` / `output` | 配列 | 出力 |
| `conditions` / `condition` | 配列またはオブジェクト | 条件 → [CONDITIONS.md](./CONDITIONS.md) |
| `conditionPolicy` | 文字列 | 稼働中に `conditions` が崩れたときの挙動（`pause` / `abort`、デフォルト: `pause`） |
| `decorators` | 配列 | 挙動の拡張 → [DECORATORS.md](./DECORATORS.md) |
| `tier` / `tiers` | オブジェクト | 要求されるコンポーネントの Tier（例: `{"glass": 1, "casing": 3}`） |
| `priority` | 数値 | 優先度 |
| `name` / `localizedName` | 文字列 | 表示名 |
| `registryName` | 文字列 | 継承で参照される名前 → §6 |
| `parent` | 文字列 | 継承元の `registryName` → §6 |
| `abstract` | 真偽値 | 継承専用のレシピにする → §6 |

> [!NOTE]
> `speedMultiplier` / `energyMultiplier` / `batchMin` / `batchMax` / `durationPolicy` は構造 JSON 側のキーです。
> マシン単位の設定なので、レシピに書いても読まれません。
>
> `conditionPolicy` は**両方にあります**。構造体側は[機械の稼働条件](../machinery/MACHINE_CONDITIONS.md)が崩れたとき、
> レシピ側はそのレシピ自身の `conditions` が崩れたときの挙動で、**別々に設定します**。

## 2.1 レシピの優先順位

レシピは以下の順序で検索・実行します。

1. **要求される最大 Tier**: 最も高い Tier 要求を持つレシピ
2. **優先度 (`priority`)**: Tier 要求が同じ場合、この数値の大きさ
3. **入力の種類数**: より多くの種類の入力を持つレシピ
4. **アイテム入力の総数**: 合計アイテム要求数が多いレシピ

## 3. 入出力の指定

入出力は、オブジェクト内にどのキーが存在するかで型が判定されます。
`type` を明示することもできます。

| キー | 型 | 補足 |
|---|---|---|
| `item` | アイテム | `meta` でメタデータ指定 |
| `ore` | アイテム | 鉱石辞書名。input のみ |
| `fluid` | 液体 | `amount` は mB |
| `gas` | ガス | `amount` は mB |
| `energy` / `mana` | エネルギー / マナ | `perTick` が true なら毎 tick |
| `essentia` / `vis` | アスペクト | 値はアスペクト名 |
| `symbol` | ブロック | 構造体のシンボル位置を操作 → §3.2 |

```json
{ "item": "minecraft:coal", "amount": 64 }
{ "fluid": "water", "amount": 1000 }
{ "energy": 100, "perTick": true }
{ "essentia": "ignis", "amount": 10 }
```

`consume: false` を書くと消費しません（触媒）。

## 3.1 動的数量

`amount` には固定値の代わりに式を書けます。

```json
{
  "inputs":  [ { "item": "minecraft:iron_ingot", "amount": "tier * 10 + 5" } ],
  "outputs": [ { "fluid": "water", "amount": "energy_p * 1000" } ]
}
```

- 入力: Tier 1 なら 15 個、Tier 5 なら 55 個
- 出力: エネルギー充填率に応じて変化（満タンで 1000 mB、50% で 500 mB）

主なプロパティ:

| 変数 | 意味 |
|---|---|
| `tier` | マシンの現在の Tier |
| `energy_p` / `fluid_p` / `mana_p` | 各リソースの充填率 (0.0 - 1.0) |
| `progress` | レシピ進行度 (0.0 - 1.0) |
| `recipe_count` | 処理済みレシピ数 |
| `time` / `day` | ワールド時間と経過日数 |

完全なリストは [式パーサー リファレンス](./EXPRESSION_REFERENCE.md)、
書き方の例は [実用例集](./EXPRESSION_EXAMPLES.md) を参照。

### 注意事項

- 式の結果は整数に丸められます（小数点以下は切り捨て）
- 負の値は 0 として扱われます
- 三項演算子 `? :` と論理演算子 `&&` / `||` が使えます
- バッチサイズは量に自動で掛かります。式の中で `batch` を掛けると二重になります

## 3.2 ブロック（`symbol`）

構造体内のシンボル位置にあるブロックを検知・操作します。`replace`（操作前）と
`block`（操作後）という命名で統一されています。

| キー | 意味 |
|---|---|
| `symbol` | 対象の記号（構造体定義のマッピング） |
| `replace` | 条件 / 旧ブロックの ID |
| `block` | 結果 / 新ブロックの ID |
| `consume` | true でブロックを消費（input のみ） |
| `optional` | true なら対象が見つからなくてもレシピを開始・完了できる |
| `amount` | 操作する最大個数 |
| `nbt` | 設置するブロックの TileEntity に書き込む NBT（output のみ）。式が使える |

| # | ケース | 入出力 | 設定例 | 挙動 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | 存在確認 | `inputs` | `"block": "stone"` | 石があるか確認 |
| 2 | 必須消費 | `inputs` | `"block": "stone", "consume": true` | 開始時に石を消去 |
| 3 | 任意消費 | `inputs` | `"consume": true, "optional": true` | 開始時にブロックがあれば消去 |
| 4 | 入力置換 | `inputs` | `"replace": "A", "block": "B"` | 開始時に A を B に変換 |
| 5 | 新規設置 | `outputs`| `"block": "gold"` | 終了時に金を設置 |
| 6 | 必須置換 | `outputs`| `"replace": "stone", "block": "gold"` | 終了時に石を金に置換 |
| 7 | 任意置換 | `outputs`| `"replace": "stone", "block": "gold", "optional": true` | 終了時に石があれば金に置換 |

> [!NOTE]
> いくつかの TileEntity は設置時にクラッシュの原因になります（Angelica + ET Futurum 導入時の Beacon で確認）。
> バグを見つけたら issue の作成をお願いします。

### ブロックの NBT を書き換える

`nbt` に代入式を並べます。左辺が書き込み先（設置するブロックの NBT）、右辺は任意の式です。

```json
"outputs": [{
  "symbol": "D",
  "block": "modid:battery",
  "nbt": [
    "nbt('energy') = nbt('C', 'stored_power')",
    "nbt('tier') = tier.casing"
  ]
}]
```

右辺で `nbt('C', ...)` のようにシンボルを使えば、別のブロックから読んで書き込めます。
加算・減算は右辺に自分を書くか、`+=` を使います。

```json
"nbt": [ "nbt('stored_energy') += 1000" ]
```

## 4. 条件とデコレータ

表現力が高すぎるので、別ファイルを参照。

- [条件 (Conditions)](./CONDITIONS.md) — ブロック・バイオーム・天候・座標・NBT・式による判定
- [デコレータ (Decorators)](./DECORATORS.md) — 確率、ボーナス出力、重み付き抽選、触媒

## 5. 式を JSON オブジェクトで書く

一部のパラメータは、数値の代わりに式オブジェクトを取れます。

| `type` | 動作 |
|---|---|
| `constant` | 固定の数値を返す |
| `nbt` | マシンの TileEntity から NBT パスの数値を読む |
| `map_range` | ある範囲の数値を別の範囲へ線形補間でマッピング |
| `arithmetic` | 二つの式の間で演算（`left`, `right`, `operation`: `+` `-` `*` `/` `%`） |
| `world_property` | ワールドの情報（`time`, `day`, `moon_phase`）を取得 |

### 文字列による簡易記述

JSON の階層を避けて、文字列で直接書けます。単一の数値だけでなく論理演算も書けるため、
本システムでは**レシピスクリプト**と呼んでいます。

```json
"condition": "nbt('S', 'energy') > 5000",
"chance": "{ nbt('energy') / 100000.0 } * 0.8"
```

## 6. レシピの継承

`abstract` なレシピに共通のプロパティを置き、他のレシピから `parent` で参照します。

```json
{
  "registryName": "base_miner",
  "abstract": true,
  "duration": 200,
  "inputs": [ ... ]
}
```

```json
{ "parent": "base_miner", "outputs": [ ... ] }
```

