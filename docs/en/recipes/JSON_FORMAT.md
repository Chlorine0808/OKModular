# Recipe System: JSON Format Reference

Recipes are defined in `config/okmodular/recipes/*.json`.

## Related Documentation

- [Conditions](./CONDITIONS.md) - what decides whether a recipe runs or stops
- [Decorators](./DECORATORS.md) - chance, bonus output, catalysts
- [Expression Reference](./EXPRESSION_REFERENCE.md) - the list of variables and functions
- [Expression Examples](./EXPRESSION_EXAMPLES.md)

---

## 1. File layout

```json
{
  "group": "machine name",
  "recipes": [
    { ... recipe definition 1 ... },
    { ... recipe definition 2 ... }
  ]
}
```

## 2. Recipe properties

| Key | Type | Meaning |
|---|---|---|
| `group` / `machine` | string | Recipe group (which machine the recipe belongs to) |
| `duration` / `time` | number or expression | Work amount. **Not a time** (it is divided by the speed multiplier) |
| `inputs` / `input` | array | Inputs |
| `outputs` / `output` | array | Outputs |
| `conditions` / `condition` | array or object | Conditions → [CONDITIONS.md](./CONDITIONS.md) |
| `conditionPolicy` | string | What happens to this recipe when `conditions` stop holding mid-run (`pause` / `abort`, default: `pause`) |
| `decorators` | array | Extended behaviour → [DECORATORS.md](./DECORATORS.md) |
| `tier` / `tiers` | object | Required component tiers (e.g. `{"glass": 1, "casing": 3}`) |
| `priority` | number | Priority |
| `name` / `localizedName` | string | Display name |
| `registryName` | string | The name inheritance refers to → §10 |
| `parent` | string | The `registryName` to inherit from → §10 |
| `abstract` | boolean | Make this an inheritance-only recipe → §10 |

> [!NOTE]
> `speedMultiplier` / `energyMultiplier` / `batchMin` / `batchMax` / `durationPolicy` are structure JSON keys.
> They are per-machine settings, so writing them in a recipe has no effect.
>
> `conditionPolicy` exists in **both**. On a structure it governs the
> [machine's own conditions](../machinery/MACHINE_CONDITIONS.md); on a recipe it governs that
> recipe's own `conditions`. They are set separately.

### Priority

Recipes are searched and run in this order.

1. **Highest required tier**: the recipe with the highest tier requirement
2. **Priority (`priority`)**: for equal tier requirements, the larger number
3. **Number of input kinds**: the recipe demanding more distinct kinds of input
4. **Total item inputs**: the recipe demanding more items in total

## 3. Inputs and outputs

The type is decided by which key is present in the object.
`type` may also be stated explicitly.

| Key | Type | Notes |
|---|---|---|
| `item` | item | `meta` for metadata |
| `ore` | item | Ore dictionary name. Input only |
| `fluid` | fluid | `amount` is in mB |
| `gas` | gas | `amount` is in mB |
| `energy` / `mana` | energy / mana | Per tick when `perTick` is true |
| `essentia` / `vis` | aspect | The value is an aspect name |
| `symbol` | block | Acts on a structure's symbol position → §6 |
| `structure` | block | Acts on a whole arrangement of blocks → §7 |

```json
{ "item": "minecraft:coal", "amount": 64 }
{ "fluid": "water", "amount": 1000 }
{ "energy": 100, "perTick": true }
{ "essentia": "ignis", "amount": 10 }
```

Writing `consume: false` leaves the input unconsumed (a catalyst).

### `perTick` - drawn for as long as the recipe runs

Drawn all the way through the recipe rather than once at the start. A boolean turns it on; a
number sets the interval.

```json
{ "energy": 100, "perTick": true }
{ "fluid": "water", "amount": 50, "perTick": 3 }
```

Both `pertick` and `perTick` are read.

> [!IMPORTANT]
> **The unit is work done, not ticks of the clock.**
> A machine at 1.5x speed does 1.5 work per tick, so it draws **1.5 units of a `perTick`
> resource per tick** as well - at interval 1 that is 1 and 2 alternating.
>
> What this buys is the total. **A recipe draws `ceil(duration / interval)` times whatever
> the speed**, so "this recipe costs 100k EU" is a property of the recipe rather than of the
> machine that happened to run it.
>
> The flip side is that **a single tick's draw comes in a lump**. A recipe drawing 100/tick
> on a 5x machine draws 500 on the ticks it draws at all, and the buffer has to take it.

> [!NOTE]
> **To let the machine change the total, write a machine property into the amount.**
> There is no switch that changes what `perTick` means per machine: it would give one idea
> two spellings, and an expression says more than a switch could.
>
> | To make it | Write |
> |---|---|
> | Cost more the faster it runs | `"energy": "100 * speed_multi"` |
> | Cost less the faster it runs | `"energy": "100 / speed_multi"` |
> | Independent of speed (default) | `"energy": 100` |
> | Follow the structure's `energyMultiplier` | `"energy": "100 * energy_multi"` |
> | Rise with the square of the tier | `"energy": "100 * tier ^ 2"` |
>
> A structure's `energyMultiplier` applies **only when a recipe reads `energy_multi`**.
> Setting it alone does nothing.

## 4. Dynamic amounts

`amount` takes an expression instead of a fixed value.

```json
{
  "inputs":  [ { "item": "minecraft:iron_ingot", "amount": "tier * 10 + 5" } ],
  "outputs": [ { "fluid": "water", "amount": "energy_p * 1000" } ]
}
```

- Input: 15 at tier 1, 55 at tier 5
- Output: varies with the energy fill ratio (1000 mB when full, 500 mB at 50%)

The main properties:

| Variable | Meaning |
|---|---|
| `tier` | The machine's current tier |
| `energy_p` / `fluid_p` / `mana_p` | Fill ratio of each resource (0.0 - 1.0) |
| `progress` | Recipe progress (0.0 - 1.0) |
| `recipe_count` | Recipes processed so far |
| `time` / `day` | World time and elapsed days |

For the full list see the [Expression Reference](./EXPRESSION_REFERENCE.md);
for worked examples see the [Expression Examples](./EXPRESSION_EXAMPLES.md).

### Notes

- The result is rounded down to an integer
- Negative values are treated as 0
- The ternary operator `? :` and the logical operators `&&` / `||` are available
- The batch size is applied to amounts automatically. Multiplying by `batch` inside the expression applies it twice

## 5. Matching and rewriting an item's NBT

An item input can **match** on NBT, and an item input or output can **rewrite** it.

> [!TIP]
> `/okmodular hand` prints the held item's id, meta and NBT. Use it to see what an `nbt`
> expression actually wrote.

### `nbtmatch` — how strict the match is

| Value | Meaning |
|---|---|
| `ignore` (**default**) | NBT is not looked at. An enchanted book is just a book |
| `exact` | Only an **exact** NBT match is accepted |
| `none` | Only items carrying **no** NBT are accepted |

```json
{ "item": "minecraft:diamond_sword", "nbtmatch": "exact", "nbt": [ ... ] }
```

> [!WARNING]
> **The default is `ignore`.** Leave it out and NBT is not consulted at all, so an input
> meant to accept only enchanted items **also accepts plain ones**, `nbt` or no `nbt`.

### `nbtlist` — reaching into a list-typed NBT

For lists such as `ench` (enchantments) or `Items` (an inventory).

```json
"nbtlist": {
  "path": "ench",
  "ops": [
    { "id": 16, "lvl": ">=5" },
    { "id": 20, "lvl": 0 },
    { "id": 34, "lvl": "+1" }
  ]
}
```

| Key | Default | Meaning |
|---|---|---|
| `path` | `"Items"` | NBT path of the list |
| `ops` | none | Operations on the list's entries, in the form below |

Each entry in `ops` is both the condition an element must meet and what to do with it.
**The way a value is written decides which.**

| Written as | Behaviour |
|---|---|
| `">=5"`, `"<=5"`, `">5"`, `"<5"`, `"==5"`, `"!=5"` | **Condition** - only matching elements are affected |
| `"+1"`, `"-1"` | **Relative** - added to or subtracted from the current value |
| `5` (a non-zero number) or any other string | **Absolute** - set to that value |
| `0` (the number zero) | **Remove** the element |

The example requires id 16 to be level 5 or better, removes id 20, and raises id 34 by one.

> [!WARNING]
> **A numeric `0` means remove, not "set to zero".**
> Unless the same `ops` block contains a relative change (`"+1"` and the like), in which case
> `0` is read as "set to zero" instead - `{"Slot": 0, "Count": "+1"}` makes no sense read as
> "delete slot 0". **Do not mix the two when you mean to delete.**

## 6. Blocks (`symbol`)

Detects and acts on the block at a symbol position inside the structure. The naming is
consistent: `replace` (before) and `block` (after).

| Key | Meaning |
|---|---|
| `symbol` | The target symbol (from the structure definition's mappings) |
| `replace` | The ID of the condition / old block |
| `block` | The ID of the result / new block |
| `consume` | true consumes the block (input only) |
| `optional` | true lets the recipe start and finish even when the target is missing |
| `amount` | Maximum number of blocks acted on |
| `nbt` | NBT written to the placed block's TileEntity (output only). Expressions allowed |

| # | Case | I/O | Example | Behaviour |
| :--- | :--- | :--- | :--- | :--- |
| 1 | Existence check | `inputs` | `"block": "stone"` | Check that stone is there |
| 2 | Required consume | `inputs` | `"block": "stone", "consume": true` | Remove the stone on start |
| 3 | Optional consume | `inputs` | `"consume": true, "optional": true` | Remove the block on start if present |
| 4 | Input replace | `inputs` | `"replace": "A", "block": "B"` | Turn A into B on start |
| 5 | Place new | `outputs`| `"block": "gold"` | Place gold on finish |
| 6 | Required replace | `outputs`| `"replace": "stone", "block": "gold"` | Replace stone with gold on finish |
| 7 | Optional replace | `outputs`| `"replace": "stone", "block": "gold", "optional": true` | Replace stone with gold on finish if present |

> [!NOTE]
> Some TileEntities cause a crash when placed (confirmed with the Beacon under Angelica + ET Futurum).
> Please open an issue if you find a bug.

### Rewriting a block's NBT

List assignment expressions in `nbt`. The left side is the destination (the placed block's
NBT), the right side is any expression.

```json
"outputs": [{
  "symbol": "D",
  "block": "modid:battery",
  "nbt": [
    "nbt('energy') = nbt('C', 'stored_power')",
    "nbt('tier') = tier.casing"
  ]
}]
```

Using a symbol on the right, as in `nbt('C', ...)`, reads from another block and writes the
value here. For addition and subtraction, either repeat the destination on the right or
use `+=`.

```json
"nbt": [ "nbt('stored_energy') += 1000" ]
```

## 7. Arrangements of blocks (`structure`)

Where `symbol` counts blocks one at a time, `structure` acts on a whole arrangement — a shape
written once as a named pattern and matched or placed as a unit.

```json
{ "type": "structure", "pattern": "altar_core", "symbol": "P", "consume": true }
```

| Key | Meaning |
|---|---|
| `pattern` | The pattern's name, from a file in `config/okmodular/structure_io/` |
| `symbol` | The structure symbol whose blocks the pattern is anchored to |
| `amount` | How many anchors must match / are written to. Default 1 |
| `consume` | Input only. Clears the whole matched arrangement on start |
| `optional` | Lets the recipe start and finish even when nothing matches |

An anchor counts only when every cell of the pattern matches, and the pattern turns with the
machine it sits in. The full format, the reserved characters and how the anchor is chosen are in
[Structure IO](../structures/STRUCTURE_IO.md).

## 8. Conditions and decorators

Expressive enough to warrant their own files.

- [Conditions](./CONDITIONS.md) — block, biome, weather, coordinate, NBT and expression checks
- [Decorators](./DECORATORS.md) — chance, bonus output, weighted draws, catalysts

## 9. Writing an expression as a JSON object

Some parameters take an expression object instead of a number.
For how to write one and the list of `type`s, see the
[structured form in the Expression Reference](./EXPRESSION_REFERENCE.md#6-structured-form--an-object-instead-of-a-string).
`arithmetic` accepts `+` `-` `*` `/` `%` as its `operation`, and `world_property` reads world
information (`time`, `day`, `moon_phase`).

### The short string form

Skipping the JSON hierarchy, an expression can be written directly as a string. It holds
more than a single number - logical operations work too - which is why this system calls
it a **recipe script**.

```json
"condition": "nbt('S', 'energy') > 5000",
"chance": "{ nbt('energy') / 100000.0 } * 0.8"
```

## 10. Recipe inheritance

Put shared properties on an `abstract` recipe and refer to it from others with `parent`.

```json
{
  "registryName": "base_miner",
  "abstract": true,
  "duration": 200,
  "inputs": [ ... ]
}
```

```json
{ "parent": "base_miner", "outputs": [ ... ] }
```
