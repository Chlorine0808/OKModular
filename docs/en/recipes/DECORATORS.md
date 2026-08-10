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
| `bonus` | Produces extra output by chance | `chance` + `outputs` (`modifierKey` is [gone](#removed-modifierkey)) |
| `weighted_random` | Picks an output from a weighted list | `outputs` (each with `weight`) / `pool` / `rolls` |
| `requirement` | Checks extra conditions and catalysts while running | `condition` / `requirements` |
| `harvest_block` | Mines the blocks that are about to disappear (see below) | `fortune` / `silkTouch` / `shear` / `harvestLevel` |
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

> [!NOTE]
> **A recipe's `chance` is drawn once per run and the answer is kept.**
> When the output ports are full the machine retries every tick until they clear, but it
> does not draw again - and neither does it after a save and reload.
>
> Without that, a recipe written as `"chance": "energy_p"` - anything that **moves while the
> machine waits** - could turn from a win into a loss mid-wait and take the whole payout with
> it. The longer the output stayed blocked, the less likely the recipe was to pay at all,
> which is not a rule anyone wrote.

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
- `random_block_output` picks `count × n` cells
- `per_position_probability` **does not scale with the batch** (see below)

A `chance` or a `count` written as an expression is re-evaluated for each of the n draws, so
one containing `random()` varies between the runs inside a batch and the total need not be a
multiple of n.

> [!NOTE]
> **`per_position_probability` is the one that does not scale, because it asks a different
> question.** It asks *each cell* whether to replace it, and asking twice only rewrites the
> same block. `random_block_output` names *a number of cells*, and n runs of "replace 5
> cells" is 5n cells.
>
> Cells are finite, so a `count × n` past the size of the structure **fills what is there and
> stops**. It is not an error.

> [!TIP]
> **Do not write `batch` into the expression.** The engine applies it, so `"count": "5 *
> batch"` applies it **twice** - the same trap as an input or output `amount`.

> [!WARNING]
> **Two decorators of the same kind on one recipe always give the same answer.**
> Streams are assigned per kind, not per instance. Two `bonus` entries will land together
> and miss together. For now, give them different `chance` values or fold them into one.

### Removed: `modifierKey`

`bonus` no longer takes `modifierKey`. Writing it is ignored, with a warning on load.

It never worked. The value was read into a field and the place that should have applied it
carried a `TODO`, so a recipe with it drew exactly the same odds as one without.

What it was for - letting something about the machine move the chance - is already how
`chance` works, because `chance` is an expression:

```json
{ "type": "bonus", "chance": "0.25 * speed_multi", "outputs": [...] }
{ "type": "bonus", "chance": "0.1 + tier * 0.05",  "outputs": [...] }
```

Every value `modifierKey` could have named is visible there as `speed_multi`,
`energy_multi` or `batch`, and an expression can **combine** them, which one key never
could. Implementing it would have left one idea with two spellings, the weaker one
answering differently.

## 4. `harvest_block` — mine the blocks that are about to disappear

Blocks a structure IO recipe removes come back as drops rather than simply vanishing. Both
the blocks a consuming input takes and the ones an output overwrites are collected.

```json
"decorators": [
  { "type": "harvest_block", "harvestLevel": 2, "fortune": 3 }
]
```

| Key | Default | Meaning |
|---|---|---|
| `harvestLevel` | **`0`** / expression | A block harder than this **stops the recipe from starting** (see below) |
| `fortune` | `0` | Fortune level, applied to the drops |
| `silkTouch` | `false` | Silk touch: collects the block itself |
| `shear` | `false` | Shears: leaves and vines come back as themselves (`IShearable` blocks only) |

### The three moments it runs at

Unlike the other decorators it does not fit `produceExtraOutputs`, which runs **after** the
outputs land - by then the block it was meant to collect has been overwritten.

| Moment | What happens |
|---|---|
| Before the recipe starts | Refuses if any block is too hard |
| Just before inputs are consumed | Collects what a `consume: true` `BlockInput` takes |
| Just before outputs are applied | Collects what a `BlockOutput` overwrites |

**Only the first moment may refuse.** Refusing mid-consumption would take the materials and
stop; refusing at completion would take them, do the work, and hand back nothing while the
machine waits forever. So the check happens once, at the start, across **both** sets of
positions.

> [!WARNING]
> **`harvestLevel` defaults to 0, which is what a wooden pick manages.**
> Leave it out and a single iron ore (level 1) anywhere in the structure means the recipe
> never starts. It simply does not begin - no exception, no log line - so it reads as "this
> machine does not work". Omitting it now logs a warning on load.
>
> **It takes an expression as well as a number.** `"harvestLevel": "tier"` lets a better
> machine mine harder blocks. It is evaluated once, when the recipe starts.

With both `silkTouch` and `shear` set, **silk touch wins**.

Drops go into the item ports. Anything that does not fit is dropped on the floor.

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
