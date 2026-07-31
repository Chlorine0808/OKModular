# Recipe System: JSON Format Reference

Recipes are defined in `config/okmodular/recipes/*.json`.

## 📚 Related Documentation

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
| `registryName` | string | The name inheritance refers to → §6 |
| `parent` | string | The `registryName` to inherit from → §6 |
| `abstract` | boolean | Make this an inheritance-only recipe → §6 |

> [!NOTE]
> `speedMultiplier` / `energyMultiplier` / `batchMin` / `batchMax` / `durationPolicy` are structure JSON keys.
> They are per-machine settings, so writing them in a recipe has no effect.
>
> `conditionPolicy` exists in **both**. On a structure it governs the
> [machine's own conditions](../machinery/MACHINE_CONDITIONS.md); on a recipe it governs that
> recipe's own `conditions`. They are set separately.

## 2.1 Recipe priority

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
| `symbol` | block | Acts on a structure's symbol position → §3.2 |

```json
{ "item": "minecraft:coal", "amount": 64 }
{ "fluid": "water", "amount": 1000 }
{ "energy": 100, "perTick": true }
{ "essentia": "ignis", "amount": 10 }
```

Writing `consume: false` leaves the input unconsumed (a catalyst).

## 3.1 Dynamic amounts

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

## 3.2 Blocks (`symbol`)

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

## 4. Conditions and decorators

Expressive enough to warrant their own files.

- [Conditions](./CONDITIONS.md) — block, biome, weather, coordinate, NBT and expression checks
- [Decorators](./DECORATORS.md) — chance, bonus output, weighted draws, catalysts

## 5. Writing an expression as a JSON object

Some parameters take an expression object instead of a number.

| `type` | Behaviour |
|---|---|
| `constant` | Returns a fixed number |
| `nbt` | Reads a number at an NBT path on the machine's TileEntity |
| `map_range` | Maps one numeric range onto another by linear interpolation |
| `arithmetic` | Operates on two expressions (`left`, `right`, `operation`: `+` `-` `*` `/` `%`) |
| `world_property` | Reads world information (`time`, `day`, `moon_phase`) |

### The short string form

Skipping the JSON hierarchy, an expression can be written directly as a string. It holds
more than a single number - logical operations work too - which is why this system calls
it a **recipe script**.

```json
"condition": "nbt('S', 'energy') > 5000",
"chance": "{ nbt('energy') / 100000.0 } * 0.8"
```

## 6. Recipe inheritance

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
