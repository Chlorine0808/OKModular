# レシピ式の実用例集

式で書ける代表的なパターンです。
変数・関数のリストと、よくある間違いは [式パーサー リファレンス](./EXPRESSION_REFERENCE.md) にあります。

## 関連ドキュメント

- [JSON フォーマット](./JSON_FORMAT.md) - 基本的な JSON 構文
- [式パーサー 変数・関数リファレンス](./EXPRESSION_REFERENCE.md) - 変数・関数の完全なリスト

---

## 1. マシンの状態でスケールさせる

### Tier で増やす

```json
{
  "inputs":  [ { "item": "minecraft:iron_ingot", "amount": "tier * 8" } ],
  "outputs": [ { "item": "minecraft:diamond", "amount": "tier" } ],
  "duration": 200
}
```

Tier 1 なら鉄 8 個からダイヤ 1 個、Tier 8 なら鉄 64 個からダイヤ 8 個になります。

急激に増やしたいなら `pow(2, tier - 1)`、
ある Tier から挙動を変えたいなら `tier >= 5 ? tier * 2 : tier` のように書けます。

### 充填率でボーナスをつける

```json
{
  "inputs":  [ { "energy": 10000, "perTick": true },
               { "item": "minecraft:coal", "amount": 1 } ],
  "outputs": [ { "fluid": "steam", "amount": "floor(1000 * (1.0 + energy_p * 0.5))" } ],
  "duration": 100
}
```

エネルギーが 0% なら 1000 mB、50% なら 1250 mB、100% なら 1500 mB です。

`energy_p` を入力側に置けば、ボーナスとは逆のペナルティになります。
たとえば `"amount": "energy_p < 0.2 ? 2 : 1"` なら、残量 20% 未満のとき原料が 2 個になります。

### 使えば使うほど良くなる

```json
{
  "outputs": [ { "item": "reward", "amount": "min(100, floor(sqrt(recipe_count) * 5))" } ]
}
```

出力は、初めのうちは急に伸び、後半は緩やかになり、100 個で止まります。

> [!NOTE]
> **上限と下限は式の中で保証してください**。`min(...)` で上限を、`max(1, ...)` で下限を設けます。
> 特に割り算やマイナス方向の補正が入る式は、極端な状態で 0 や巨大な値になりがちです。

## 2. ワールドの環境で変える

```json
{
  "inputs":  [ { "essentia": "luna", "amount": "8 - moon_phase" } ],
  "outputs": [ { "item": "moonstone", "amount": "moon_phase + 1" } ]
}
```

満月（`moon_phase` = 0）はエッセンシア 8 で出力 1 個、新月（4）はエッセンシア 4 で出力 5 個になります。

昼夜で切り替えるなら `time >= 0 && time < 12000 ? 5 : 1`、
周期的に変えるなら `1 + floor(sin(day * 0.1) * 3)` のように書けます。

天候そのものは、レシピの `conditions` で判定するほうが素直です
（→ [CONDITIONS.md](./CONDITIONS.md)）。

## 3. 確率を混ぜる

`chance(x)` は確率 `x` で 1、それ以外で 0 を返します。

```json
{
  "outputs": [ { "item": "rare_drop", "amount": "chance(0.1 + tier * 0.05) ? 1 : 0" } ]
}
```

Tier 1 なら 15%、Tier 8 なら 50% の確率で 1 個出力されます。

レシピ全体の成否や、追加出力そのものを確率で出したい場合はデコレータの `chance` / `bonus` のほうが適しています（→ [DECORATORS.md](./DECORATORS.md)）。

## 4. duration を動的にする

```json
{ "duration": "max(20, floor(200 / (1.0 + tier * 0.2)))" }
```

Tier が高いほど作業量が減り、最低 20 で止まります。

`duration` の式はレシピ開始時に 1 回だけ評価され、そのレシピ実行中は固定されます。
毎 tick 評価し直したい場合は構造体側で `"durationPolicy": "perTick"` を指定してください。

> [!IMPORTANT]
> **`duration` に `speed_multi` を掛けたり割ったりしないでください**。
> 理由は [よくある間違い](./EXPRESSION_REFERENCE.md#よくある間違い) を参照してください。

マシン依存の式を NEI でどう表示するかは [NEI での見え方](./EXPRESSION_REFERENCE.md#nei-での見え方) を参照してください。

## 5. ブロックの NBT を段階的に進める

`symbol` 出力の `nbt` に代入式を書けば、実行するたびに値を進められます。

```json
{
  "outputs": [
    { "symbol": "C", "block": "modid:altar", "nbt": [ "nbt('stage') += 1" ] },
    { "item": "stage_reward", "amount": "min(10, nbt('C', 'stage'))" }
  ]
}
```

値を読む側は `nbt('C', 'stage')` のようにシンボルを指定します。
書く側にシンボルは指定できません（書き込み先はその式を書いた出力自身の NBT です）。
