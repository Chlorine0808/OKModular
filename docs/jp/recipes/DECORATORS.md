# レシピのデコレータ (Decorators)

デコレータは、レシピの実行中や完成時の動きを足すものです。
条件と同じ 3 つの書き方（`type` 明示 / キーで型を名指し / プロパティから推論）が使えます。

## 関連ドキュメント

- [JSON フォーマット](./JSON_FORMAT.md)
- [条件 (Conditions)](./CONDITIONS.md)
- [式パーサー 変数・関数リファレンス](./EXPRESSION_REFERENCE.md)

---

## 1. 種類

| type 名 | 動作 | 推論に使われるプロパティ |
|---------|------|------------------------|
| `chance` | レシピの成功確率を決める（外れると出力なし） | `chance` |
| `bonus` | 確率で追加の出力を出す | `chance` + `outputs`（`modifierKey` は[廃止](#7-廃止-bonus-の-modifierkey)） |
| `weighted_random` | 重み付きリストから出力を選ぶ | `outputs`（各要素に `weight`）／ `pool` ／ `rolls` |
| `requirement` | 実行中に追加の条件・触媒を確かめる | `condition` / `requirements` |
| `harvest_block` | 消えるブロックを採掘して回収する（下記） | `fortune` / `silkTouch` / `shear` / `harvestLevel` |
| `per_position_probability` | 座標ごとに確率でブロック出力を差し替える | `chance` + `symbol` + `output` |
| `bonus_block_output` | 確率で追加のブロック出力を出す | `chance` + `outputs`（先頭が `type: "block"`） |
| `random_block_output` | 記号のマスから `count` マスを抽選してブロックを置く（下記） | `count` / `selections` |

## 2. 書き方

```json
"decorators": [
  {
    "chance": 0.5
  },
  {
    "bonus": {
      "chance": 0.1,
      "outputs": [{ "item": "minecraft:diamond", "amount": 1 }]
    }
  },
  {
    "type": "weighted_random",
    "outputs": [
      { "weight": 70, "item": "minecraft:flint",  "amount": 1 },
      { "weight": 30, "item": "minecraft:gravel", "amount": 1 }
    ]
  }
]
```

`weighted_random` の `rolls` を省略すると 1 回抽選、`weight` を省略すると 1 として扱われます。

`chance` には数値だけでなく式も書けます（`"chance": "0.1 + tier * 0.05"`）。
式が読む `tier` などの性能は、レシピを始めたときの値です。完成前に構造体を組み替えても確率は変わりません
（[式リファレンスの評価タイミング](./EXPRESSION_REFERENCE.md#5-式が書けるフィールドと評価タイミング)を参照）。

## 3. 抽選の仕組み

デコレータは、乱数オブジェクトを持ち回すのではなく、機械の評価シードから抽選します。
そのため、次の性質が保証されます。

| 性質 | 内容 |
|---|---|
| 同じ実行の中では必ず同じ | 何度評価しても同じ答え。セーブ・ロード・チャンク再読込をまたいでも変わらない |
| 実行ごとには変わる | 次に回したときは違う結果。シードに開始 tick と処理回数が混ざるため |
| 機械ごとに変わる | シードに座標が混ざるため |
| 位置ごとに変わる | `per_position_probability` と `random_block_output` はブロック座標もシードに混ぜる |
| デコレータどうしは連動しない | 種類ごとに別の系統から引く。`bonus` が当たったから `bonus_block_output` も当たる、ということは起きない |

実用上は 1 番目の性質がいちばん重要です。エンジンは出力が入るかを確かめてから入れるので、
同じ実行の中で答えが変わると「置けると答えたのに置かない」という食い違いが起きます。

> [!NOTE]
> レシピの `chance` は 1 回の実行につき 1 回だけ引き、その答えを記録します。
> 出力先が満杯で払い出せないとき、機械は空くまで毎 tick やり直しますが、
> **抽選はやり直しません**。セーブして入り直しても同じです。
>
> この記録が無かったころは、`"chance": "energy_p"` のように待っている間に変わる値を書いたレシピが、
> 詰まっている最中に外れに変わり、成果物ごと消えることがありました。
> 詰まった時間が長いほど当たりにくくなるという、誰も意図していない挙動です。

### バッチとの関係

バッチ n は「レシピを n 回動かした分を 1 回にまとめたもの」なので、**抽選も n 回行います**。

- `bonus` / `bonus_block_output` … n 回判定し、当たった回数だけ出力する
- `weighted_random` … `rolls × n` 個選ぶ
- `random_block_output` … `count × n` マス選ぶ
- `per_position_probability` … バッチでは増えない（下記）

`chance` や `count` を式で書いた場合、式は n 回それぞれで評価し直します。`random()` を含む式は
バッチ内でも 1 回ごとに値が変わるので、合計が n の倍数にならないことがあります。

> [!NOTE]
> `per_position_probability` だけが n 倍にならないのは、判定の単位が違うからです。
> `per_position_probability` はマスごとに「ここを置き換えるか」を判定するので、2 回判定しても同じブロックを
> 書き直すだけで何も増えません。一方、`random_block_output` は置くマス数を指定するので、
> 「5 マス置く」を n 回行えば 5n マスになります。
>
> マス数には限りがあるので、`count × n` が構造体のマス数を超えたら、置けるマスをすべて埋めた時点で止まります。
> エラーにはなりません。

> [!TIP]
> **`batch` を式に書かないでください**。バッチの分はエンジンが掛けるので、
> `"count": "5 * batch"` と書くと二重に掛かります（入出力の `amount` と同じ罠です）。

### 同じ種類を 2 つ書いたとき

> [!WARNING]
> **同じ種類のデコレータを 1 つのレシピに 2 つ書くと、その 2 つは必ず同じ答えを出します**。
> 抽選の系統はデコレータの種類ごとに割り当てられており、1 つずつに割り当てられているわけではありません。
> 例えば `bonus` を 2 つ並べると、片方が当たったときもう片方も必ず当たります。
> 独立させたい場合は、今のところ `chance` の値を変えるか 1 つにまとめてください。

## 4. `random_block_output`

`random_block_output` は、`selections` のすべてを順に適用します。どれか 1 つを選ぶのではありません。
各要素は「`symbol` のマスから `count` マスを選び、そこに `output` を置く」という指示です。

```json
{
  "type": "random_block_output",
  "count": 1,
  "selections": [
    { "symbol": "G", "output": { "symbol": "G", "block": "minecraft:grass" } },
    { "symbol": "M", "output": { "symbol": "M", "block": "minecraft:mycelium" } }
  ]
}
```

この例では、G のマスから 1 つを草にし、さらに M のマスから 1 つを菌糸にします。
選ぶのは `output` が置けるマスだけです。`replace` を書いた出力なら、そのブロックがあるマスだけが候補になります。
同じ記号に要素を 2 つ並べても、先の要素が置いたマスは `replace` に合わなくなるので、後の要素は別のマスを選びます。

## 5. `harvest_block`

`harvest_block` は、構造 IO で消えるブロックをただ消すのではなく、採掘品として回収します。
入力が消費するブロックと、出力が上書きするブロックの両方が対象です。

> [!NOTE]
> 採るのは記号の全マスではなく、その入出力が実際に消す・書くマス（`amount` 個まで）だけです。

```json
"decorators": [
  { "type": "harvest_block", "harvestLevel": 2, "fortune": 3 }
]
```

| キー | 既定 | 意味 |
|---|---|---|
| `harvestLevel` | `0` / 式 | これより硬いブロックがあるとレシピが始まりません（下記） |
| `fortune` | `0` | 幸運の値。ドロップ数に反映されます |
| `silkTouch` | `false` | シルクタッチ。ブロックそのものを回収します |
| `shear` | `false` | ハサミ。葉やツタがそのまま落ちます（`IShearable` のブロックのみ） |

### 処理が行われる 3 つの時点

`harvest_block` は他のデコレータと違い、出力を書いた後には動きません。
その時点では、採るはずのブロックがもう上書きされているからです。

| 時点 | 何をするか |
|---|---|
| レシピ開始前 | 硬すぎるブロックがあれば開始を断る |
| 入力を消す直前 | `consume: true` のブロック入力が持っていくブロックを採る |
| 出力を書く直前 | ブロック出力が上書きするブロックを採る |

**レシピを断れるのは開始前だけです**。消費の途中で断れば材料だけが取られます。
完成時に断れば材料を消費したうえで何も返さず、機械が待ち続けます。
そのため判定は開始時に 1 回だけ行い、入力と出力の両方の対象マスをまとめて調べます。

> [!WARNING]
> **`harvestLevel` の既定は 0 で、これは木のツルハシの値です**。
> 書き忘れると、構造の中に鉄鉱石（レベル 1）が 1 つあるだけでレシピが始まりません。
> 開始しないだけで例外もログも出ないので、「なぜか動かない機械」に見えます。
> 省略した場合は、ロード時に警告が出ます。
>
> 数値の代わりに式も書けます。`"harvestLevel": "tier"` とすれば、
> Tier の高い機械ほど硬いブロックを掘れます。式は開始時に 1 回だけ評価します。

`silkTouch` と `shear` を両方書いた場合は `silkTouch` が優先されます。

採掘品はアイテムポートに入ります。入らなかった分はその場にドロップします。

## 6. `requirement` デコレータ

`requirement` デコレータは、`condition`（追加の条件）と `requirements`（触媒）のどちらか、または両方を取ります。

```json
"decorators": [
  {
    "type": "requirement",
    "condition": "tier.glass >= 2",
    "requirements": [
      { "item": "minecraft:redstone", "amount": 10 },
      { "energy": 10000 }
    ]
  }
]
```

`requirements` の各要素は Input と同じ書式で、**消費されません**。
稼働開始時と稼働中の毎 tick に確かめ、足りなくなるとレシピを止めます。

> [!NOTE]
> 同じことは非消費入力で直接書けます。下の例は上の `requirements` と等価です。
> ```json
> "inputs": [ { "item": "minecraft:redstone", "amount": 10, "consume": false } ]
> ```
> デコレータ側で書く利点は、`parent` を使ったレシピ継承で、親のレシピに触媒の要件を持たせられることです。

> [!IMPORTANT]
> 構造 JSON 側にも `requirements` がありますが、**これとは別物**です。
> 構造 JSON の `requirements` はポート数を指定します。

## 7. 廃止: `bonus` の `modifierKey`

`bonus` の `modifierKey` は廃止しました。書いても無視され、ロード時に警告が出ます。

`modifierKey` は、廃止する前から確率に反映されていませんでした。値を読み込む処理はあったものの、
確率に掛ける処理が `TODO` のまま残っていたので、書いても書かなくても結果は同じでした。

`modifierKey` で実現したかったこと（機械の値で確率を変えること）は、`chance` が式を取るので既に書けます。

```json
{ "type": "bonus", "chance": "0.25 * speed_multi", "outputs": [...] }
{ "type": "bonus", "chance": "0.1 + tier * 0.05",  "outputs": [...] }
```

`modifierKey` で指定できたはずの値は、`speed_multi` / `energy_multi` / `batch` として
すべて式から参照できます。しかも式ならこれらを組み合わせられるので、キー 1 つで指定するより表現力があります。
`modifierKey` も実装すると、同じ指定に 2 通りの書き方ができ、表現力の低い `modifierKey` のほうが式と違う結果を返すことになります。
