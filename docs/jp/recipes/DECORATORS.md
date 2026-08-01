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
| `harvest_block` | ブロック破壊時の採掘特性を変える | `fortune` / `silkTouch` / `shear` / `harvestLevel` |
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

## 3. requirement デコレータ

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
