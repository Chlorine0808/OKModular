# レシピの条件 (Conditions)

レシピを通すか止めるかの判定です。**稼働中は毎 tick チェックされます。**

## 📚 関連ドキュメント

- [JSON フォーマット](./JSON_FORMAT.md)
- [式パーサー 変数・関数リファレンス](./EXPRESSION_REFERENCE.md)
- [機械の稼働条件](../machinery/MACHINE_CONDITIONS.md)

---

## 1. 条件の種類

`type` を省略した場合、使われているキーの内容から型が推論されます。

| type | 判定するもの | ショートハンド |
|---|---|---|
| `block` | マシンの設置座標にあるブロック | `{ "block": "stone" }` |
| `block_below` | マシンの下 Y-1 のブロック | `{ "block_below": "stone" }` |
| `dimension` | 特定の次元にいるか | `{ "dimension": 0 }` |
| `biome` | バイオーム名 / タグ / 気温・湿度 | `{ "biome": "Plains" }` / `{ "tag": "FOREST" }` |
| `offset` | 相対座標 `(dx, dy, dz)` で任意の条件を判定 | — |
| `pattern` | クラフトレシピのような形式で周囲のバイオーム配置を判定 | — |
| `weather` | 現在の天候（`rain` / `thunder` / `clear`） | `{ "weather": "rain" }` |
| `comparison` | 二つの式を比較（`left`, `right`, `operator`） | — |
| `expression` | 文字列の式を直接記述 | `{ "expression": "day % 28 == 0" }` |

追加のプロパティ:

- `dimension` — `ids`: 数値の配列
- `biome` — `biomes`: 名前の配列 / `tags`: Forge BiomeDictionary タグの配列 /
  `minTemp` `maxTemp`: 気温の範囲 / `minHumid` `maxHumid`: 湿度の範囲
- `offset` — `dx` `dy` `dz`: 相対座標 / `condition`: 実行する条件オブジェクト
- `pattern` — `pattern`: 文字列の配列 / `keys`: パターン文字と条件オブジェクトのマッピング

```json
"conditions": [
  {
    "pattern": [ "FFF", "F#F", "FFF" ],
    "keys": {
      "#": { "biome": "Plains" },
      "F": { "tag": "FOREST" }
    }
  },
  { "weather": "rain" },
  { "expression": "day % 28 == 0" }
]
```

## 2. 論理演算

演算子名をそのままキーとして使えます。

| キー | 書き方 |
|---|---|
| `and` | `{ "and": [ { 条件1 }, { 条件2 } ] }` |
| `or` | `{ "or": [ ... ] }` |
| `not` | `{ "not": { 条件 } }` |

`xor` / `nand` / `nor` も同様に対応しています。

## 3. 条件の 3 つの書き方

どの条件も次の 3 通りで書けます。パーサはこの順に解釈します。

| # | 書き方 | 例 |
|---|--------|-----|
| 1 | `type` を明示 | `{ "type": "comparison", "left": 10, "operator": ">", "right": 5 }` |
| 2 | キーで型を名指し（値は 1 つのオブジェクト） | `{ "comparison": { "left": 10, "operator": ">", "right": 5 } }` |
| 3 | プロパティから推論 | `{ "biome": "Plains" }` |

## 4. NBT を条件にする

`expression` の中で `nbt(...)` を使います。

```json
{ "expression": "nbt('energy') >= 1000" }
{ "expression": "nbt('customData.heat') < 500" }
{ "expression": "nbt('S', 'stored_power') > 0" }
{ "expression": "nbt('mode') != 3" }
```

> [!IMPORTANT]
> キーの存在自体を条件にしたい場合は `has_nbt(...)` を併用してください。
> ```json
> { "expression": "has_nbt('heat') && nbt('heat') <= 100" }
> ```
> 引数の形は `nbt()` と同じです（`has_nbt('key')` / `has_nbt('S', 'key')`）。

## 5. Tips

エラーは `logs/latest.log` に出ます。

同じ判定は稼働中に毎 tick 走ります。
`count_blocks` のような重いクエリを条件に置くとそのぶん毎 tick のコストになります。
