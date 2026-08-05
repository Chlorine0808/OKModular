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
| `bonus` | 確率で追加の出力を生成 | `chance` + `outputs` |
| `weighted_random` | 重み付きリストから出力を選択 | `outputs`（各要素に `weight`）／ `pool` ／ `rolls` |
| `requirement` | 実行中に追加の条件・触媒をチェック | `condition` / `requirements` |
| `harvest_block` | ⚠ **未実装**（下記） | `fortune` / `silkTouch` / `shear` / `harvestLevel` |
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
- `per_position_probability` / `random_block_output` … 構造体のマスに作用するので**バッチでは増えません**（同じブロックを上書きするだけになるため）

`chance` を式で書いた場合、n 回それぞれで評価し直されます。`random()` を含む確率は
バッチ内でも 1 回ごとに揺れます。

> [!WARNING]
> **同じ種類のデコレータを 1 つのレシピに 2 つ書くと、その 2 つは必ず同じ答えを出します。**
> 系統はデコレータの種類ごとに割り当てられており、インスタンスごとではありません。
> 例えば `bonus` を 2 つ並べると、片方が当たったときもう片方も必ず当たります。
> 独立させたい場合は、今のところ `chance` の値を変えるか 1 つにまとめてください。

## 4. `harvest_block` は動きません

⚠ **パースは通りますが、効果は一度も実行されません。** レシピに書くとロード時に警告が出ます。

デコレータの効果は完成時に `produceExtraOutputs` から実行されますが、`harvest_block` は
**ブロックが書き換わる前**に採掘する必要があるので、出力適用後に走るこのフックには乗りません。
入力側の口も別に要ります。

同じことは `silk_touch` / `fortune` を持つ通常のツールで手動採掘すれば得られます。

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
