# Recipe Expression Examples

The representative patterns that expressions cover.
The list of variables and functions, and the common mistakes, are in the
[Expression Reference](./EXPRESSION_REFERENCE.md).

## 📚 Related Documentation

- [JSON Format](./JSON_FORMAT.md) - Basic JSON syntax
- [Expression Reference](./EXPRESSION_REFERENCE.md) - Complete list of variables and functions

---

## 1. Scaling with the machine's state

### Growing with tier

```json
{
  "inputs":  [ { "item": "minecraft:iron_ingot", "amount": "tier * 8" } ],
  "outputs": [ { "item": "minecraft:diamond", "amount": "tier" } ],
  "duration": 200
}
```

8 iron → 1 diamond at tier 1; 64 iron → 8 diamonds at tier 8.

For steeper growth use `pow(2, tier - 1)`.
To change behaviour past a certain tier, write it as `tier >= 5 ? tier * 2 : tier`.

### A bonus from the fill ratio

```json
{
  "inputs":  [ { "energy": 10000, "perTick": true },
               { "item": "minecraft:coal", "amount": 1 } ],
  "outputs": [ { "fluid": "steam", "amount": "floor(1000 * (1.0 + energy_p * 0.5))" } ],
  "duration": 100
}
```

1000 mB at 0% energy, 1250 mB at 50%, 1500 mB at 100%.

Putting `energy_p` on the input side gives the opposite penalty
(`"amount": "energy_p < 0.2 ? 2 : 1"` = 2 units of ore below 20% charge).

### Getting better the more it is used

```json
{
  "outputs": [ { "item": "reward", "amount": "min(100, floor(sqrt(recipe_count) * 5))" } ]
}
```

Rises quickly at first, flattens later, and stops at 100.

> [!NOTE]
> **Guarantee the ceiling and the floor inside the expression.** `min(...)` gives a ceiling,
> `max(1, ...)` a floor. Expressions with division or a negative correction in them tend to
> reach 0 or something enormous at the extremes of a machine's state.

## 2. Varying with the world

```json
{
  "inputs":  [ { "essentia": "luna", "amount": "8 - moon_phase" } ],
  "outputs": [ { "item": "moonstone", "amount": "moon_phase + 1" } ]
}
```

A full moon (`moon_phase` = 0) takes 8 essentia for 1 output; a new moon (4) takes 4 for 5.

To switch on day and night, `time >= 0 && time < 12000 ? 5 : 1`.
For something cyclic, `1 + floor(sin(day * 0.1) * 3)`.

Weather itself is more naturally handled by a recipe's `conditions`
(→ [CONDITIONS.md](./CONDITIONS.md)).

## 3. Mixing in chance

`chance(x)` returns 1 with probability `x` and 0 otherwise.

```json
{
  "outputs": [ { "item": "rare_drop", "amount": "chance(0.1 + tier * 0.05) ? 1 : 0" } ]
}
```

A 15% chance of 1 at tier 1, 50% at tier 8.

For the success of the whole recipe, or for extra output as such, the `chance` / `bonus`
decorators suit better (→ [DECORATORS.md](./DECORATORS.md)).

## 4. A dynamic duration

```json
{ "duration": "max(20, floor(200 / (1.0 + tier * 0.2)))" }
```

The higher the tier the less work, bottoming out at 20.

The expression is evaluated **once at the start** of a recipe and stays fixed for that run.
To re-evaluate it every tick, set `"durationPolicy": "perTick"` on the structure.

> [!IMPORTANT]
> **Do not multiply or divide `duration` by `speed_multi`.** The engine applies the speed
> multiplier every tick, so writing it applies it twice. `duration` is a work amount, not a
> time. Energy is the other way round: writing `energy_multi` into `energy` is the correct
> route.

NEI has no machine, so it cannot evaluate a machine-dependent expression. In that case NEI
shows **the expression itself** instead of a number of seconds (an expression that folds to
a constant still shows seconds).

## 5. Advancing a block's NBT step by step

Assignment expressions in a `symbol` output's `nbt` advance a value every time the recipe runs.

```json
{
  "outputs": [
    { "symbol": "C", "block": "modid:altar", "nbt": [ "nbt('stage') += 1" ] },
    { "item": "stage_reward", "amount": "min(10, nbt('C', 'stage'))" }
  ]
}
```

The reading side names the symbol with `nbt('C', 'stage')`. The writing side cannot name a
symbol (the destination is the NBT of the output the expression is written on).
