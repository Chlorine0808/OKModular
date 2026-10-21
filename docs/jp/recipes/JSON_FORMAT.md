# レシピシステム: JSON フォーマットリファレンス

レシピは `config/okmodular/recipes/*.json` で定義されます。

## 関連ドキュメント

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
| `registryName` | 文字列 | 継承で参照される名前 → §10 |
| `parent` | 文字列 | 継承元の `registryName` → §10 |
| `abstract` | 真偽値 | 継承専用のレシピにする → §10 |

> [!NOTE]
> `speedMultiplier` / `energyMultiplier` / `batchMin` / `batchMax` / `durationPolicy` は構造 JSON 側のキーです。
> マシン単位の設定なので、レシピに書いても読まれません。
>
> `conditionPolicy` は構造 JSON とレシピの両方にあり、別々に設定します。
> 構造体側は[機械の稼働条件](../machinery/MACHINE_CONDITIONS.md)が崩れたとき、
> レシピ側はそのレシピ自身の `conditions` が崩れたときの挙動を決めます。

### 優先順位

レシピは次の順序で検索・実行されます。

1. 要求される最大 Tier: 最も高い Tier を要求するレシピ
2. 優先度 (`priority`): Tier 要求が同じ場合は、この数値の大きさ
3. 入力の種類数: より多くの種類の入力を持つレシピ
4. アイテム入力の総数: 要求するアイテムの合計数が多いレシピ

## 3. 入出力の指定

入出力の型は、オブジェクトにどのキーがあるかで判定されます。
`type` で明示することもできます。

| キー | 型 | 補足 |
|---|---|---|
| `item` | アイテム | `meta` でメタデータ指定 |
| `ore` | アイテム | 鉱石辞書名。input のみ |
| `fluid` | 液体 | `amount` は mB |
| `gas` | ガス | `amount` は mB |
| `energy` / `mana` | エネルギー / マナ | `perTick` が true なら毎 tick |
| `essentia` / `vis` | アスペクト | 値はアスペクト名 |
| `symbol` | ブロック | 構造体のシンボル位置を操作 → §6 |
| `structure` | ブロック | ブロックの配置全体を操作 → §7 |

```json
{ "item": "minecraft:coal", "amount": 64 }
{ "fluid": "water", "amount": 1000 }
{ "energy": 100, "perTick": true }
{ "essentia": "ignis", "amount": 10 }
```

`consume: false` を書いた入力は消費されません（触媒）。

### `perTick` で稼働中に引き続ける

`perTick` を指定した資源は、開始時に一度ではなく稼働中ずっと引かれます。
値には真偽値のほか数値も書け、数値は引く間隔になります。

```json
{ "energy": 100, "perTick": true }
{ "fluid": "water", "amount": 50, "perTick": 3 }
```

`pertick` と `perTick` のどちらの綴りでも読まれます。

> [!IMPORTANT]
> **単位は「実 tick」ではなく「作業量」です**。
> 速度倍率 1.5 の機械は 1 tick に 1.5 の作業を進めるので、`perTick` 資源も
> 1 tick あたり 1.5 個ぶん引きます（間隔 1 なら、実際には 1 個と 2 個が交互）。
>
> 単位が作業量なので、レシピ 1 回の総抽出回数は `ceil(duration / interval)` となり、
> 速度によらず一定です。そのため「このレシピは 10 万 EU」がレシピ自身の性質になり、
> どの機械で回したかで変わりません。
>
> その代わり、速い機械ほど 1 tick に引く量が大きくなります。100/tick のレシピを 5 倍速の機械で
> 回すと、瞬間的には 500/tick 引かれます。バッファは、この瞬間的な引き出しに応じられる必要があります。

> [!NOTE]
> 機械によって総量を変えたいときは、量の式に機械プロパティを書きます。
> `perTick` の意味を機械ごとに切り替えるスイッチはありません。スイッチがあると同じ指定を
> 2 通りに書けてしまい、しかも式のほうが表せる範囲が広いためです。
>
> | やりたいこと | 書き方 |
> |---|---|
> | 速いほど総量が増える | `"energy": "100 * speed_multi"` |
> | 速いほど総量が減る | `"energy": "100 / speed_multi"` |
> | 速度と無関係（既定） | `"energy": 100` |
> | 構造体の `energyMultiplier` に従う | `"energy": "100 * energy_multi"` |
> | Tier で二乗に増える | `"energy": "100 * tier ^ 2"` |
>
> 構造体側の `energyMultiplier` は、レシピが `energy_multi` を読んだときにだけ反映されます。
> 構造体に書いただけでは何も起こりません。

## 4. 動的数量

`amount` には固定値の代わりに式を書けます。

```json
{
  "inputs":  [ { "item": "minecraft:iron_ingot", "amount": "tier * 10 + 5" } ],
  "outputs": [ { "fluid": "water", "amount": "energy_p * 1000" } ]
}
```

- 入力: Tier 1 なら 15 個、Tier 5 なら 55 個
- 出力: エネルギー充填率に応じて変化（満タンで 1000 mB、50% で 500 mB）

式で使える主な変数は次のとおりです。

| 変数 | 意味 |
|---|---|
| `tier` | マシンの現在の Tier |
| `energy_p` / `fluid_p` / `mana_p` | 各リソースの充填率 (0.0 - 1.0) |
| `progress` | レシピ進行度 (0.0 - 1.0) |
| `recipe_count` | 処理済みレシピ数 |
| `time` / `day` | ワールド時間と経過日数 |

完全なリストは [式パーサー リファレンス](./EXPRESSION_REFERENCE.md)、
書き方の例は [実用例集](./EXPRESSION_EXAMPLES.md) を参照してください。

### 注意事項

- 式の結果は整数に丸められます（小数点以下は切り捨て）
- 負の値は 0 として扱われます
- 三項演算子 `? :` と論理演算子 `&&` / `||` が使えます
- バッチサイズは量に自動で掛かります。式の中で `batch` を掛けると二重になります

## 5. アイテムの NBT を見る・書き換える

アイテムの入出力では、NBT の照合（入力）と書き換え（入出力どちらも）ができます。

> [!TIP]
> 手に持ったアイテムの ID・メタ値・NBT は `/okmodular hand` で確かめられます。
> `nbt` 式が何を書き込んだかを見るときに使ってください。

### `nbtmatch` による照合の厳しさ

| 値 | 意味 |
|---|---|
| `ignore`（既定） | NBT を一切見ない。エンチャント本もただの本と同じ扱い |
| `exact` | NBT が完全に一致したものだけ受け付ける |
| `none` | NBT を持っていないアイテムだけ受け付ける |

```json
{ "item": "minecraft:diamond_sword", "nbtmatch": "exact", "nbt": [ ... ] }
```

> [!WARNING]
> **既定は `ignore` です**。`nbtmatch` を書かなければ、NBT はまったく照合されません。
> 「エンチャント付きだけ受け付けたい」つもりで `nbt` を書いても、
> `nbtmatch` が無ければ素のアイテムも通ります。

### `nbtlist` でリスト型 NBT の中を見る・書き換える

`nbtlist` は、`ench`（エンチャント）や `Items`（インベントリ）のようなリスト型の NBT を扱います。

```json
"nbtlist": {
  "path": "ench",
  "ops": [
    { "id": 16, "lvl": ">=5" },
    { "id": 20, "lvl": 0 },
    { "id": 34, "lvl": "+1" }
  ]
}
```

| キー | 既定 | 意味 |
|---|---|---|
| `path` | `"Items"` | 対象のリストの NBT パス |
| `ops` | なし | リストの要素に対する操作。下の書式 |

`ops` の 1 要素は、リストの要素が満たすべき条件と、その要素の書き換え方を兼ねます。
各キーがどう動作するかは、値の書き方で決まります。

| 書き方 | 動作 |
|---|---|
| `">=5"` `"<=5"` `">5"` `"<5"` `"==5"` `"!=5"` | 条件。満たす要素だけが対象 |
| `"+1"` `"-1"` | 相対変更。今の値に足す / 引く |
| `5`（0 以外の数値）または他の文字列 | 絶対設定。その値にする |
| `0`（数値のゼロ） | 削除。その要素を消す |

上の例は、「id 16 のレベルが 5 以上」を条件として、id 20 を削除し、id 34 のレベルを 1 上げます。

> [!WARNING]
> **数値の `0` は「0 にする」ではなく「削除」です**。
> ただし同じ `ops` の中に相対変更（`"+1"` など）が 1 つでもあると、
> `0` は「削除」ではなく「0 を設定」として扱われます
> （`{"Slot": 0, "Count": "+1"}` を「スロット 0 を消す」と読むと意味が通らないため）。
> 明示的に消したいときは、相対変更と混ぜないでください。

## 6. ブロック（`symbol`）

`symbol` は、構造体内のシンボル位置にあるブロックを検知・操作します。
キー名は `replace`（操作前）と `block`（操作後）に統一しています。

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

`nbt` に代入式を並べます。左辺は書き込み先（設置するブロックの NBT）、右辺は任意の式です。

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

右辺で `nbt('C', ...)` のようにシンボルを指定すれば、別のブロックの値を読んで書き込めます。
加算・減算するには、右辺に書き込み先自身を書くか、`+=` を使います。

```json
"nbt": [ "nbt('stored_energy') += 1000" ]
```

## 7. ブロックの配置（`structure`）

`symbol` がブロックを 1 個ずつ数えるのに対し、`structure` は配置全体を扱います。
形は名前付きのパターンとして 1 度だけ書き、そのパターンで一致判定と設置の両方を行います。

```json
{ "type": "structure", "pattern": "altar_core", "symbol": "P", "consume": true }
```

| キー | 意味 |
|---|---|
| `pattern` | パターン名（`config/okmodular/structure_io/` のファイルで定義） |
| `symbol` | パターンの基準にするブロックの記号 |
| `amount` | 一致・書き込みを要求するアンカーの数。既定は 1 |
| `consume` | input のみ。一致した配置を丸ごと消す |
| `optional` | 一致しなくてもレシピを開始・完了できる |

アンカーが 1 つと数えられるのは、すべてのセルが一致したときだけです。
パターンは機械の向きに合わせて回転します。
フォーマットの詳細・予約文字・アンカーの決め方は [構造 IO](../structures/STRUCTURE_IO.md) を参照してください。

## 8. 条件とデコレータ

条件とデコレータは書ける内容が多いため、それぞれ別ファイルで説明しています。

- [条件 (Conditions)](./CONDITIONS.md) - ブロック・バイオーム・天候・座標・NBT・式による判定
- [デコレータ (Decorators)](./DECORATORS.md) - 確率、ボーナス出力、重み付き抽選、触媒

## 9. 式を JSON オブジェクトで書く

一部のパラメータは、数値の代わりに式オブジェクトを取れます。
書き方と `type` の一覧は [式パーサー リファレンスの構造化形式](./EXPRESSION_REFERENCE.md#6-構造化形式--文字列の代わりにオブジェクトで書く) を参照してください。
`arithmetic` の `operation` には `+` `-` `*` `/` `%` が書け、`world_property` ではワールドの情報（`time`, `day`, `moon_phase`）を取得できます。

### 文字列による簡易記述

式は、JSON の階層を作らずに文字列で直接書くこともできます。
数値だけでなく論理演算も書けるため、本システムではこの文字列の式を「レシピスクリプト」と呼んでいます。

```json
"condition": "nbt('S', 'energy') > 5000",
"chance": "{ nbt('energy') / 100000.0 } * 0.8"
```

## 10. レシピの継承

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

