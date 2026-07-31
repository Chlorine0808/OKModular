# レシピ式の実用例集

式で書ける代表的なパターンです。
変数・関数のリストと、よくある間違いは [式パーサー リファレンス](./EXPRESSION_REFERENCE.md) にあります。

## 📚 関連ドキュメント

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

Tier 1 で鉄 8 個 → ダイヤ 1 個、Tier 8 で鉄 64 個 → ダイヤ 8 個。

飛躍的に増やしたいなら `pow(2, tier - 1)`。
ある Tier から挙動を変えたいなら`tier >= 5 ? tier * 2 : tier` のように書けます。

### 充填率でボーナスをつける

```json
{
  "inputs":  [ { "energy": 10000, "perTick": true },
               { "item": "minecraft:coal", "amount": 1 } ],
  "outputs": [ { "fluid": "steam", "amount": "floor(1000 * (1.0 + energy_p * 0.5))" } ],
  "duration": 100
}
```

エネルギー 0% で 1000 mB、50% で 1250 mB、100% で 1500 mB。

`energy_p` を入力側に置けば逆のペナルティになります
（`"amount": "energy_p < 0.2 ? 2 : 1"` = 残量 20% 未満なら原料 2 個）。

### 使えば使うほど良くなる

```json
{
  "outputs": [ { "item": "reward", "amount": "min(100, floor(sqrt(recipe_count) * 5))" } ]
}
```

初期は急に伸び、後半は緩やかになり、100 個で止まります。

> [!NOTE]
> **上限と下限は式の中で保証してください。** `min(...)` で天井を、`max(1, ...)` で床を作ります。
> 特に割り算やマイナス方向の補正が入る式は、極端な状態で 0 や巨大な値になりがちです。

## 2. ワールドの環境で変える

```json
{
  "inputs":  [ { "essentia": "luna", "amount": "8 - moon_phase" } ],
  "outputs": [ { "item": "moonstone", "amount": "moon_phase + 1" } ]
}
```

満月（`moon_phase` = 0）はエッセンシア 8 で出力 1 個、新月（7）はエッセンシア 8 で出力 64 個。

昼夜で切り替えるなら `time >= 0 && time < 12000 ? 5 : 1`。
周期的にするなら `1 + floor(sin(day * 0.1) * 3)` のように書けます。

天候そのものはレシピの `conditions` で判定する方が素直です
（→ [CONDITIONS.md](./CONDITIONS.md)）。

## 3. 確率を混ぜる

`chance(x)` は確率 `x` で 1、それ以外で 0 を返します。

```json
{
  "outputs": [ { "item": "rare_drop", "amount": "chance(0.1 + tier * 0.05) ? 1 : 0" } ]
}
```

Tier 1 で 15%、Tier 8 で 50% の確率で 1 個。

レシピ全体の成否や、追加出力そのものを確率で出したい場合はデコレータの `chance` / `bonus` の方が適しています（→ [DECORATORS.md](./DECORATORS.md)）。

## 4. duration を動的にする

```json
{ "duration": "max(20, floor(200 / (1.0 + tier * 0.2)))" }
```

Tier が高いほど作業量が減り、最低 20 で止まります。

式はレシピ開始時に 1 回だけ評価され、そのレシピ実行中は固定されます。
毎 tick 評価し直したい場合は構造体側で `"durationPolicy": "perTick"` を指定してください

> [!IMPORTANT]
> **`duration` に `speed_multi` を掛けたり割ったりしないでください。**
> 速度倍率はエンジンが毎 tick 適用しているので、書くと 2 回掛かります。`duration` は時間ではなく作業量です。
> エネルギーは逆で、`energy` に `energy_multi` を書くのが正しい経路です。

NEI はマシンを持たないため、マシン依存の式は評価できません。
その場合 NEI は秒数の代わりに式そのものを表示します（定数に畳める式なら従来どおり秒数が出ます）。

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

読む側は `nbt('C', 'stage')` でシンボルを指定します。
書く側にシンボルは指定できません（書き込み先はその式を書いた出力自身の NBT です）。
