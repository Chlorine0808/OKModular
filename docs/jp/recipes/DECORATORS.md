# レシピのデコレータ (Decorators)

デコレータはレシピの実行中や終了時に追加の挙動を与えます。
条件と同じ 3 つの書き方（`type` 明示 / キーで型を名指し / プロパティから推論）が使えます。

## 📚 関連ドキュメント

- [JSON フォーマット](./JSON_FORMAT.md)
- [条件 (Conditions)](./CONDITIONS.md)
- [式パーサー 変数・関数リファレンス](./EXPRESSION_REFERENCE.md)

---

## 1. 種類

| type 名 | 動作 | 推論に使われるプロパティ |
|---------|------|------------------------|
| `chance` | レシピの成功確率を制御（外れると出力なし） | `chance` |
| `bonus` | 確率で追加の出力を生成 | `chance` + `outputs`（`modifierKey` は[廃止](#廃止-modifierkey)） |
| `weighted_random` | 重み付きリストから出力を選択 | `outputs`（各要素に `weight`）／ `pool` ／ `rolls` |
| `requirement` | 実行中に追加の条件・触媒をチェック | `condition` / `requirements` |
| `harvest_block` | 消えるブロックを採掘して回収（下記） | `fortune` / `silkTouch` / `shear` / `harvestLevel` |
| `per_position_probability` | 座標ごとに確率でブロック出力を差し替える | `chance` + `symbol` + `output` |
| `bonus_block_output` | 確率で追加のブロック出力を生成 | `chance` + `outputs`（先頭が `type: "block"`） |
| `random_block_output` | 候補からブロック出力を抽選 | `count` / `selections` |

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

## 3. 抽選の仕組み

デコレータの抽選は**機械の評価シードから引かれます**。乱数オブジェクトを持ち回しているわけではないので、
次の性質が保証されます。

| | |
|---|---|
| **同じ実行の中では必ず同じ** | 何度評価しても同じ答え。セーブ・ロード・チャンク再読込を跨いでも変わらない |
| **実行ごとには変わる** | 次に回したときは違う結果。シードに開始 tick と処理回数が混ざるため |
| **機械ごとに変わる** | シードに座標が混ざるため |
| **位置ごとに変わる** | `per_position_probability` と `random_block_output` はブロック座標もシードに混ぜる |
| **デコレータどうしは連動しない** | 種類ごとに別の系統から引く。`bonus` が当たったから `bonus_block_output` も当たる、にはならない |

1 番目が実用上いちばん効きます。エンジンは「出力が入るか確かめてから入れる」ので、
同じ実行内で答えが揺れると**「置けると答えたのに置かない」**が起きます。

### バッチとの関係

バッチ n は「レシピを n 回動かしたのを 1 回にまとめたもの」なので、**抽選も n 回行われます**。

- `bonus` / `bonus_block_output` … n 回判定し、当たった回数だけ出力する
- `weighted_random` … `rolls × n` 個選ぶ
- `random_block_output` … `count × n` マス選ぶ
- `per_position_probability` … **バッチでは増えません**（下記）

`chance` や `count` を式で書いた場合、n 回それぞれで評価し直されます。`random()` を含む式は
バッチ内でも 1 回ごとに揺れるので、合計が n の倍数にならないことがあります。

> [!NOTE]
> **`per_position_probability` だけ倍にならないのは、問いの立て方が違うからです。**
> あちらは**マスごとに**「ここを置き換えるか」を問うので、2 回問うても同じブロックを
> 書き直すだけで何も増えません。`random_block_output` は**マス数を名指しする**ので、
> 「5 マス置く」を n 回なら 5n マスになります。
>
> マス数は有限なので、`count × n` が構造体のマス数を超えたら**あるだけ埋めて止まります**。
> エラーにはなりません。

> [!TIP]
> **`batch` を式に書かないでください。** バッチはエンジンが掛けるので、
> `"count": "5 * batch"` は**二重に掛かります**（入出力の `amount` と同じ罠）。

> [!WARNING]
> **同じ種類のデコレータを 1 つのレシピに 2 つ書くと、その 2 つは必ず同じ答えを出します。**
> 系統はデコレータの種類ごとに割り当てられており、インスタンスごとではありません。
> 例えば `bonus` を 2 つ並べると、片方が当たったときもう片方も必ず当たります。
> 独立させたい場合は、今のところ `chance` の値を変えるか 1 つにまとめてください。

### 廃止: `modifierKey`

`bonus` の `modifierKey` は**廃止しました**。書いても無視され、ロード時に警告が出ます。

もともと**一度も効いていません**でした。読まれてフィールドに入るだけで、
確率に反映する所が `TODO` のまま残っていたので、書いても書かなくても同じ目が出ていました。

やりたかったこと（機械の値で確率を動かす）は **`chance` が式を取る**ので既に書けます。

```json
{ "type": "bonus", "chance": "0.25 * speed_multi", "outputs": [...] }
{ "type": "bonus", "chance": "0.1 + tier * 0.05",  "outputs": [...] }
```

`modifierKey` で名指しできたはずの値は `speed_multi` / `energy_multi` / `batch` として
すべて式から見えます。しかも式なら**組み合わせられる**ので、キー 1 つより強い。
片方だけ実装すると 1 つの考えに綴りが 2 つでき、弱い方が違う答えを返します。

## 4. `harvest_block` — 消えるブロックを採掘して回収する

構造 IO で消えるブロックを、**ただ消すのではなく採掘品として回収**します。
入力が消費するブロックと、出力が上書きするブロックの両方が対象です。

```json
"decorators": [
  { "type": "harvest_block", "harvestLevel": 2, "fortune": 3 }
]
```

| キー | 既定 | 意味 |
|---|---|---|
| `harvestLevel` | **`0`** / 式 | これより硬いブロックがあると**レシピが始まりません**（下記） |
| `fortune` | `0` | 幸運の値。ドロップ数に効きます |
| `silkTouch` | `false` | シルクタッチ。ブロックそのものを回収します |
| `shear` | `false` | ハサミ。葉やツタが**そのまま**落ちます（`IShearable` のブロックのみ） |

### 走る 3 つの瞬間

他のデコレータと違い、`produceExtraOutputs`（出力適用の**後**）には乗りません。
そこでは採るはずのブロックがもう上書きされているからです。

| 瞬間 | 何をするか |
|---|---|
| レシピ開始前 | 硬すぎるブロックがあれば**開始を断る** |
| 入力を消す直前 | `consume: true` の `BlockInput` が持っていくブロックを採る |
| 出力を書く直前 | `BlockOutput` が上書きするブロックを採る |

**断れるのは開始前だけ**です。消費の途中で断れば材料だけ取られ、
完成時に断れば材料を食ったうえで何も返さず機械が待ち続けます。
だから判定は開始時に 1 回、**両方の対象マスをまとめて**見ます。

> [!WARNING]
> **`harvestLevel` の既定は 0 で、これは木のツルハシの値です。**
> 書き忘れると、構造の中に鉄鉱石（レベル 1）が 1 つあるだけで**レシピが始まりません**。
> 開始しないだけで例外もログも出ないので、「なぜか動かない機械」に見えます。
> 省略した場合はロード時に警告を出します。
>
> **数値の代わりに式を書けます。** `"harvestLevel": "tier"` とすれば、
> Tier の高い機械ほど硬いブロックを掘れます。開始時に 1 回だけ評価されます。

`silkTouch` と `shear` を両方書いた場合は **`silkTouch` が勝ちます**。

採掘品はアイテムポートに入ります。入らなかった分はその場にドロップします。

## 5. requirement デコレータ

`condition`（追加の条件）と `requirements`（触媒）のどちらか、または両方を取ります。

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

`requirements` の各要素はInputと同じ書式で、**消費されません**。
稼働開始時と毎 tickチェックされ、足りなくなるとレシピが止まります。

> [!NOTE]
> **同じことは非消費入力で直接書けます。** 下は上の `requirements` と等価です。
> ```json
> "inputs": [ { "item": "minecraft:redstone", "amount": 10, "consume": false } ]
> ```
> デコレータ側で書く利点は、`parent` を使ったレシピ継承で**親が触媒要件を持てる**点です。

> [!IMPORTANT]
> 構造 JSON 側にも `requirements` がありますが**別物**です。
> あちらはポート数の指定です。
