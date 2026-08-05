# Recipe Decorators

Decorators add behaviour while a recipe runs or when it finishes.
The same three forms as conditions are available (`type` stated / the key names the type /
inferred from the properties).

## 📚 Related Documentation

- [JSON Format](./JSON_FORMAT.md)
- [Conditions](./CONDITIONS.md)
- [Expression Reference](./EXPRESSION_REFERENCE.md)

---

## 1. Kinds

| type name | Behaviour | Properties used for inference |
|---------|------|------------------------|
| `chance` | Controls the recipe's success chance (a loss produces nothing) | `chance` |
| `bonus` | Produces extra output by chance | `chance` + `outputs` |
| `weighted_random` | Picks an output from a weighted list | `outputs` (each with `weight`) / `pool` / `rolls` |
| `requirement` | Checks extra conditions and catalysts while running | `condition` / `requirements` |
| `harvest_block` | ⚠ **Not implemented** (see below) | `fortune` / `silkTouch` / `shear` / `harvestLevel` |
| `per_position_probability` | Swaps a block output per position, by chance | `chance` + `symbol` + `output` |
| `bonus_block_output` | Produces extra block output by chance | `chance` + `outputs` (first is `type: "block"`) |
| `random_block_output` | Draws a block output from candidates | `count` / `selections` |

## 2. Writing them

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

Omitting `rolls` on `weighted_random` draws once; omitting `weight` treats it as 1.

`chance` takes an expression as well as a number (`"chance": "0.1 + tier * 0.05"`).

## 3. How the draws work

A decorator draws **from the machine's evaluation seed**, not from a random number generator
carried around between calls, which gives the following.

| | |
|---|---|
| **Fixed within one run** | The same answer however many times it is asked, across a save and reload or a chunk unload |
| **Different between runs** | The next run gives a different result; the seed mixes in the tick the recipe started and how many recipes the machine has finished |
| **Different per machine** | The seed mixes in the position |
| **Different per position** | `per_position_probability` and `random_block_output` mix the block coordinates in as well |
| **Independent of each other** | Each kind draws from its own stream, so `bonus` landing does not mean `bonus_block_output` lands too |

The first one matters most in practice. The engine checks whether an output fits before
placing it, so an answer that moved between the two would mean **"said it would fit, then
placed nothing"**.

### Batches

A batch of n is n runs of the recipe folded into one, so **the draw happens n times**.

- `bonus` / `bonus_block_output` roll n times and produce output once per hit
- `weighted_random` picks `rolls × n` entries
- `per_position_probability` and `random_block_output` act on the structure's cells, so they
  **do not scale with the batch** - running them again would only rewrite the same blocks

A `chance` written as an expression is re-evaluated for each of the n draws, so a chance
containing `random()` varies between the runs inside one batch.

> [!WARNING]
> **Two decorators of the same kind on one recipe always give the same answer.**
> Streams are assigned per kind, not per instance. Two `bonus` entries will land together
> and miss together. For now, give them different `chance` values or fold them into one.

## 4. `harvest_block` does nothing

⚠ **It parses, but its effect never runs.** Writing it into a recipe logs a warning on load.

Decorator effects run from `produceExtraOutputs` on completion, and `harvest_block` has to
run **before** the blocks it collects are overwritten, so it does not fit that hook. It needs
a call site on the input side as well.

The same result is available by mining the blocks by hand with a `silk_touch` or `fortune`
tool.

## 5. The requirement decorator

Takes `condition` (an extra condition), `requirements` (catalysts), or both.

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

Each element of `requirements` takes the same form as an input and is **not consumed**.
They are checked on start and every tick, and the recipe stops if they run short.

> [!NOTE]
> **The same thing can be written directly as a non-consuming input.** The line below is
> equivalent to the `requirements` above.
> ```json
> "inputs": [ { "item": "minecraft:redstone", "amount": 10, "consume": false } ]
> ```
> The advantage of the decorator form is that a parent can hold catalyst requirements when
> recipes inherit through `parent`.

> [!IMPORTANT]
> Structure JSON also has a `requirements` key, and it is **a different thing**.
> That one specifies port counts.
