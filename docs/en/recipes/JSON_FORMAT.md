# Recipe System: JSON Format Reference

Recipes are defined in `config/omoshiroikamo/modular/recipes/*.json`. 

## 1. File Structure
You can define a single recipe object or a collection of recipes.

### Multiple Recipes Configuration (Recommended)
```json
{
  "group": "MachineName",
  "recipes": [
    { ... Recipe Definition 1 ... },
    { ... Recipe Definition 2 ... }
  ]
}
```

## 2. Recipe Properties

| `decorators` | Array | Decorators to extend recipe behavior. |
| `requiredTier` | Object | Required component Tiers (e.g., `{"glass": 1, "casing": 3}`). |

## 2.1 Recipe Priority and Sorting
Recipes are evaluated and displayed in the following order (higher items take precedence):
1. **Max Required Tier**: Recipes requiring higher Tiers take the highest precedence.
2. **Priority (`priority`)**: If the max Tiers are equal, higher priority values take precedence.
3. **Input Type Count**: Recipes requiring more diverse resource types take precedence.
4. **Total Item Input Count**: Recipes requiring a larger total quantity of items take precedence.

## 3. Inputs and Outputs

The resource type is determined by the presence of a specific key within the object.

### Items
- `item`: Block/Item ID.
- `amount`: Quantity.
- `meta`: Metadata (optional).
- `ore`: Ore Dictionary name (input only, used instead of `item`).

```json
{ 
  "item": "minecraft:coal", 
  "amount": 64 
}
```

### Fluids
- `fluid`: Fluid ID.
- `amount`: Milli-buckets count.

```json
{ 
  "fluid": "water", 
  "amount": 1000 
}
```

### Energy & Mana
- `energy` / `mana`: Amount.
- `perTick` / `pertick`: If true, resource is consumed/produced every tick instead of as a lump sum.

```json
{ 
  "energy": 100, 
  "perTick": true 
}
```

### Other Resources
- `gas`: Gas ID.
- `essentia`: Aspect name.
- `vis`: Aspect name.

```json
{ 
  "essentia": "ignis", 
  "amount": 10 
}
```

## 3.1 Dynamic Amount

The `amount` field in inputs and outputs can accept **expressions** instead of fixed values.
Using expressions allows you to dynamically adjust quantities based on machine state or world environment.

### Basic Usage

```json
{
  "inputs": [
    {
      "item": "minecraft:iron_ingot",
      "amount": "tier * 10 + 5"
    }
  ],
  "outputs": [
    {
      "fluid": "water",
      "amount": "energy_p * 1000"
    }
  ]
}
```

In the above example:
- **Input**: Requires 15 items at Tier 1, 55 items at Tier 5
- **Output**: Output amount varies with energy fill percentage (1000mB at full, 500mB at 50%)

### Available Variables and Functions

For a complete list, see the [Expression Parser Variable & Function Reference](./EXPRESSION_REFERENCE.md).

**Main Properties**:
- `tier` - Current machine Tier (1-16)
- `energy_p` / `fluid_p` / `mana_p` - Resource fill percentages (0.0-1.0)
- `progress` - Recipe progress (0.0-1.0)
- `recipe_count` - Number of processed recipes
- `time` / `day` - World time and elapsed days

For practical examples, see the [Expression Examples](./EXPRESSION_EXAMPLES.md).

### Notes

- Expression results are rounded to integers (fractional parts are truncated)
- Negative values are treated as 0
- If an expression is invalid, the fixed `amount` value is used as a fallback
- Ternary operator `? :` can be used for conditional branching
- Logical operators `&&` (AND), `||` (OR) are available

---

### External Block NBT Check/Consume (Block Nbt Input)
Assess and consume NBT data from blocks within the structure at recipe start.

- `type`: `"block_nbt"`
- `symbol`: The target symbol.
- `key`: The NBT key to check.
- `operation`: (`"sub"` | `"set"` | `"add"`). `"sub"` prevents start if value is insufficient.
- `value`: Numeric constant or Expression.
- `consume`: If true (default), actually modifies NBT when recipe starts.
- `optional`: If true, allows recipe start even if the target block or NBT key is missing. If false (default), missing targets prevent the recipe from starting.

```json
"inputs": [{
  "type": "block_nbt",
  "symbol": "S",
  "key": "stored_energy",
  "operation": "sub",
  "value": 100
}]
```

### Blocks
Detect/manipulate blocks at specific symbol positions within the structure. This mod uses a unified naming convention: **`replace` (Before)** and **`block` (After)**.
(Note) Some TileEntities cause crashes when placed. (Confirmed with Beacon in Angelica + ETFuturm setup)
If you find a bug, please create an issue.

- `symbol`: The character symbol used in the structure definition.
- `replace`: (**Condition/Old block**) The block ID to target for manipulation.
- `block`: (**Result/New block**) The block ID that should finally be at the position.
- `consume`: (**Input only**) If true, automatically replaces the block with Air (clearing). No need to specify `block`.
- `optional`: If true, the recipe can start even if the target block is not found (executes if present).
- `amount`: The maximum number of blocks to target.
- `nbt`: (**Output only**) NBT data to apply to the placed block's TileEntity. Supports **Expression** values.

#### 7 Key Use Cases

| # | Case | I/O | Example Config | Behavior |
| :--- | :--- | :--- | :--- | :--- |
| 1 | **Exist Check** | `inputs` | `"block": "stone"` | Checks for Stone (not consumed). |
| 2 | **Mandatory Consume**| `inputs` | `"block": "stone", "consume": true` | Clears Stone (at start). |
| 3 | **Optional Consume** | `inputs` | `"consume": true, "optional": true` | Clears if present (at start). |
| 4 | **Input Replace** | `inputs` | `"replace": "A", "block": "B"` | Transforms A to B (at start). |
| 5 | **Output Placement** | `outputs`| `"block": "gold"` | Places Gold in air (at end). |
| 6 | **Output Replace** | `outputs`| `"replace": "stone", "block": "gold"` | Replaces Stone with Gold (at end). |
| 7 | **Optional Replace** | `outputs`| `"replace": "stone", "block": "gold", "optional": true`| Replaces if Stone exists (at end). |

#### Dynamic NBT Example
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

The left-hand side is the destination — the NBT of the block being placed — and the
right-hand side is any expression. Using a symbol on the right, as in
`nbt('C', ...)`, lets you **read from another block and write the result here**.

> [!NOTE]
> The older object form (`{"energy": {"type":"nbt","path":"..."}}`) is legacy and
> **does not work**: it uses a `path` key, while the implementation reads `key`. Use
> the array form above.

### External Block NBT Manipulation (Block Nbt Output)
Manipulate NBT data of any TileEntity within the structure. Unlike `block` replacement, this modifies internal data numerically without changing the block itself.

- `type`: Specify `"block_nbt"`.
- `symbol`: The symbol target.
- `key`: The target NBT key.
- `operation`: The operation type (`"set"`, `"add"`, `"sub"`)。
- `value`: The numeric value or Expression for the operation.
- `optional`: If true, failure to find the target block or NBT key will not block recipe completion. If false (default), missing targets will block the recipe (treated as insufficient capacity).

```json
"outputs": [{
  "type": "block_nbt",
  "symbol": "S",
  "key": "stored_energy",
  "operation": "add",
  "value": 1000
}]
```

## 4. Conditions
Conditions are checked every tick or at the start of the process. Logical operators (CoR Pattern) can be used to construct complex conditions.

Available types:
(Note: If the `type` property is omitted, the type is automatically inferred from the keys used.)

- **`block` (Recommended)**: Checks for a block at the machine's current position. (`block`: string ID or object)
- `dimension`: Checks if the machine is in a specific dimension.
    - **Shorthand**: `{ "dimension": 0 }`
    - `ids`: Array of numeric IDs (Legacy format).
- `biome`: Assesses biome names, tags, or environmental values.
    - **Shorthand**: `{ "biome": "Plains" }` / `{ "tag": "FOREST" }`
    - `biomes`: Array of biome names.
    - `tags`: Array of Forge BiomeDictionary tags.
    - `minTemp` / `maxTemp`: Temperature range check (optional).
    - `minHumid` / `maxHumid`: Humidity range check (optional).
- `offset`: Wraps another condition to be checked at a relative offset `(dx, dy, dz)`.
    - `dx`, `dy`, `dz`: Relative coordinates.
    - `condition`: The condition object to execute.
- `pattern`: Checks biome layout using a grid pattern.
    - `pattern`: Array of strings.
    - `keys`: Mapping of pattern characters to condition objects.
- `block_below`: Checks for a specific block below the machine (Y-1). Using `offset` + `block` is now recommended instead.
- ~~`tile_nbt`~~: **Removed.** Write `{ "expression": "nbt('energy') >= 1000" }` instead (below).
- `weather`: Checks current weather. (`rain`, `thunder`, `clear`)
- `comparison`: Compares two expressions (`left`, `right`, `operator`).
- `expression`: Direct mathematical string expression.

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

### Supported Logical Operators (Shortcuts)
You can use the operator name directly as the key.
- **`and`**: `{ "and": [ { condition1 }, { condition2 } ] }`
- **`or`**: `{ "or": [ ... ] }`
- **`not`**: `{ "not": { condition } }`
- `xor`, `nand`, `nor` are also supported.

### The three ways to write a condition

Every condition accepts all three, and the parser tries them in this order.

| # | Form | Example |
|---|------|---------|
| 1 | Explicit `type` | `{ "type": "comparison", "left": 10, "operator": ">", "right": 5 }` |
| 2 | **Type named by the key** (value is one object) | `{ "comparison": { "left": 10, "operator": ">", "right": 5 } }` |
| 3 | Inferred from properties | `{ "biome": "Plains" }` |

Form 2 only counts as a type declaration when the inner object holds up as that
type on its own. So `{ "chance": { "type": "map_range", … } }`, where the value is
an expression object, is read as form 3 and the expression is not lost.

### Conditions on NBT

Use `nbt(...)` inside an `expression`. **`tile_nbt` has been removed.**

```json
{ "expression": "nbt('energy') >= 1000" }
{ "expression": "nbt('customData.heat') < 500" }
{ "expression": "nbt('S', 'stored_power') > 0" }
{ "expression": "nbt('mode') != 3" }
```

`tile_nbt` had its own comparison parser and could express **none** of the above
beyond the first line — no nested paths, no other blocks, no `!=`. `nbt()` covers
all of them.

> [!IMPORTANT]
> **They differ on a key that is not there.** `tile_nbt` treated an absent key as
> failing the condition; `nbt()` answers **0**. For `>=` comparisons the outcome is
> the same, but for `<=` and `<` it is **reversed** — 0 satisfies them.
>
> When the key's presence is itself part of the condition, pair it with `has_nbt(...)`:
> ```json
> { "expression": "has_nbt('heat') && nbt('heat') <= 100" }
> ```
> Its arguments take the same form as `nbt()` (`has_nbt('key')` / `has_nbt('S', 'key')`).

## 5. Decorators
Decorators provide additional behavior during or after recipe execution. They
accept the same three forms as conditions: explicit `type`, type named by the key,
or inferred from the properties present.

| type | Behaviour | Properties used for inference |
|------|-----------|-------------------------------|
| `chance` | Controls the success probability of the recipe | `chance` |
| `bonus` | Gives a chance to produce extra outputs | `chance` + `outputs` |
| `weighted_random` | Selects an output from a weighted pool | `outputs` (entries carrying `weight`), `pool`, `rolls` |
| `requirement` | Checks an additional condition or catalyst during execution | `condition` / `requirements` |
| `harvest_block` | Changes how broken blocks are harvested | `fortune` / `silkTouch` / `shear` / `harvestLevel` |
| `per_position_probability` | Swaps a block output per position, by chance | `chance` + `symbol` + `output` |
| `bonus_block_output` | Gives a chance to produce extra block outputs | `chance` + `outputs` (first is `type: "block"`) |
| `random_block_output` | Draws block outputs from a set of candidates | `count` / `selections` |

> [!NOTE]
> `harvest_block`, `per_position_probability`, `bonus_block_output` and
> `random_block_output` were previously registered in camelCase (`harvest`,
> `perPositionProbability`, `bonusBlockOutput`, `randomBlockOutput`). Those names
> remain as aliases, so existing recipe packs keep working.

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

Omitting `rolls` draws once; omitting a `weight` treats it as 1.

### The requirement decorator

Takes a `condition`, a list of `requirements`, or both.

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

Each entry in `requirements` is written **like an input** and is **not consumed** — it
is a catalyst. It is checked when the recipe starts and again every tick, so the
recipe stops if it goes missing. Writing `consume: true` there is ignored: if you
want an extra input that is consumed, put it in `inputs`, where it reads as one.

> [!NOTE]
> **The same thing can be written as a non-consuming input.** This is equivalent to
> the `requirements` entry above:
> ```json
> "inputs": [ { "item": "minecraft:redstone", "amount": 10, "consume": false } ]
> ```
> Declaring it on the decorator is useful with recipe inheritance, where a `parent`
> can hand a catalyst requirement to everything inheriting from it without touching
> their inputs.
>
> Structure definitions also have a `requirements` field, but it is **unrelated**:
> that one declares **how many ports** a machine needs, e.g. at least one item input.

## 6. Expressions (IExpression)
Some parameters (like decorator chances) can use `IExpression` to calculate values dynamically. Instead of a direct numeric constant, you can use the following object format:

- `constant`: Returns a fixed numeric value.
- `nbt`: Reads a value from the machine's TileEntity NBT path (e.g., `energyStored`).
- `map_range`: Maps a value from one range to another using linear interpolation.
- `arithmetic`: Performs operations between two expressions (`left`, `right`, `operation`: `+`, `-`, `*`, `/`, `%`).
- `world_property`: Retrieves world info (`time`, `day`, `moon_phase`).

### Expression String (Recipe Script)
Instead of deep JSON objects, you can write mathematical/logical expressions directly as strings. This supports complex logic and is referred to as **"Recipe Script"**.

```json
"condition": "nbt('S', 'energy') > 5000",
"chance": "{ nbt('energyStored') / 100000.0 } * 0.8"
```

For a full reference of available variables, functions, and operators, see the [Expression Parser Variable & Function Reference](./EXPRESSION_REFERENCE.md).

## 7. Inheritance
You can use an `abstract` recipe to share common properties.

```json
{
  "registryName": "base_miner",
  "isAbstract": true,
  "time": 200,
  "inputs": [...]
}
```
Recipes can then use `"parent": "base_miner"` to inherit those values.
