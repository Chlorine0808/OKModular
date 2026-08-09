# Structure System: JSON Format Reference

This reference describes the JSON format used to define multiblock structures. Files should be placed in `config/okmodular/structures/`.

## 1. File Structure
A file can contain a single object or an array of objects.
A special object named `default` can be used to define shared mappings.

## 2. Main Properties

| Property | Type | Description |
| :--- | :--- | :--- |
| `name` | String | Identifier (required, must be unique) |
| `displayName` | String | The machine's display name (optional) |
| `recipeGroup` | String/Array | The recipe groups this structure serves |
| `mappings` | Object | Character-to-block associations |
| `layers` | Array | Vertical slices of the structure (top to bottom) |
| `requirements` | Array | Structure recognition requirements (ports and the like) |
| `tintColor` | String | The machine's colour (e.g. `#FF0000`) |
| `speedMultiplier` | Float / expression | Processing speed (default: 1.0) |
| `energyMultiplier` | Float / expression | Energy consumption (default: 1.0) |
| `batchMin` | Integer / expression | Minimum batch size for recipes (default: 1) |
| `batchMax` | Integer / expression | Maximum batch size for recipes (default: 1) |
| `tier` | Integer | Machine tier (default: 0) |
| `controllerOffset` | Array `[a, b, c]` | Where the controller stands in the structure. → [2.3](#23-controlleroffset--where-the-controller-stands) |
| `externalPorts` | Array of symbols | Which port symbols may connect to the outside. → [2.4](#24-externalports--ports-that-reach-outside) |
| `inputOnly` / `outputOnly` / `bothOnly` | Array of symbols | Fix a symbol's side IO. → [2.4](#24-externalports--ports-that-reach-outside) |
| `tierStructures` | Array | Sub-structures that raise a tier or a count when built. → [2.5](#25-tierstructures--building-more-raises-a-tier) |
| `modVersion` | String | Migration stamp, **written automatically**. Do not write it by hand |
| `defaultFacing` | String | The structure's default facing (`UP`, `DOWN`). **Specifies the controller's facing.** Horizontal when omitted |
| `durationPolicy` | String | When an expression-valued recipe `duration` is evaluated (`onStart` / `perTick`, default: `onStart`) |
| `conditions` | Array/Object | [Machine Conditions](../machinery/MACHINE_CONDITIONS.md) |
| `conditionPolicy` | String | What happens to a running recipe when `conditions` stop holding (`pause` / `abort`, default: `pause`) |

### durationPolicy

Decides when a recipe's `duration` is evaluated, for durations written as expressions. It has no effect on durations written as plain numbers.

| Value | Behaviour |
|----|------|
| `onStart` (default) | Evaluated once when the recipe starts and fixed for that run |
| `perTick` | Re-evaluated every tick, for reflecting things that change mid-run such as weather or moon phase |

> [!CAUTION]
> `perTick` moves the denominator of the progress bar. The bar jumps when the value drops,
> and the recipe completes the moment the duration falls below the work already done.

This is separate from the `dynamic` flag, which re-evaluates every tick when
`speedMultiplier` and the like are written as expressions. `dynamic` governs the
performance multipliers; `durationPolicy` governs the recipe's work amount.

### Performance modifiers as expressions

`speedMultiplier`, `energyMultiplier`, `batchMin` and `batchMax` accept an expression in
place of a number. Those expressions **can see the machine's own state** (`tier`, `energy`,
`item("...")` and so on - see the [Expression Reference](../recipes/EXPRESSION_REFERENCE.md)
for the available names).

```json
"speedMultiplier": "1 + tier * 0.25",
"batchMax": "clamp(floor(energy / 10000), 1, 8)"
```

| Field | Evaluated |
|---|---|
| `speedMultiplier` | Every tick while running |
| `batchMin` / `batchMax` | When a recipe starts, to pick the batch size |
| `energyMultiplier` | When a recipe reads `energy_multi` |

> [!WARNING]
> **A modifier cannot read itself.** `speed_multi` *is* the result of evaluating
> `speedMultiplier`, so `"speedMultiplier": "speed_multi * 2"` is a cycle. A modifier that
> cycles takes its neutral value (1.0 for a multiplier, 1 for a batch bound) and is reported
> once in `logs/latest.log`.
> **Reading a different modifier is fine** - `"speedMultiplier": "energy_multi"` works.

### 2.2 Per-part tiers

A part of the machine can carry a tier that depends on the material it was built from.
**This goes in the mapping**, not at the top level.

```json
"mappings": {
  "G": {
    "component": "glass",
    "tiers": {
      "okmodular:glass:1": 1,
      "okmodular:glass:2": 2
    }
  },
  "C": {
    "component": "casing",
    "tiers": {
      "okmodular:casing_reinforced:0": 1,
      "okmodular:casing_reinforced:1": 2,
      "okmodular:casing_reinforced:2": 3
    }
  }
}
```

`component` names the part and `tiers` says which tier each block counts as.
When a recipe asks for `"tier": { "glass": 2 }`, the settings above make it valid only on
structures built with `okmodular:glass:2`.

> [!WARNING]
> This used to be described as a top-level `tierMap`. No such field exists and nothing reads
> it, so writing one is **ignored without an error or a log line** - it shows up only as
> "I set the tier and the recipe still will not run". Put `component` and `tiers` inside
> `mappings`.

### 2.3 `controllerOffset` — where the controller stands

Says where the controller (`Q`) sits within `layers`, as an offset from the anchor.

```json
"controllerOffset": [2, 0, 2]
```

The order matches `layers`: `[a, b, c]` (across, height, depth). Fewer than three entries is
**ignored silently**. Left out, the position where `Q` was drawn is used as-is.

### 2.4 `externalPorts` — ports that reach outside

By default the ports inside a structure only face the machine. Symbols that outside pipes and
cables may connect to are listed here.

```json
"externalPorts": ["E", "I"],
"inputOnly":  ["I"],
"outputOnly": ["O"]
```

| Key | Meaning |
|---|---|
| `externalPorts` | Symbols **allowed** to connect outwards. Per-side IO stays wrench-adjustable |
| `inputOnly` | Pins that symbol to **input**. The wrench cannot change it |
| `outputOnly` | Pins it to **output** |
| `bothOnly` | Pins it to **both** |

Values are arrays of strings, and **only the first character** is read as the symbol (`"EE"`
means `E`). Empty strings are skipped.

> [!NOTE]
> `inputOnly` and its siblings are a **separate list** from `externalPorts`. A symbol that
> should be both reachable and pinned has to appear in both.

### 2.5 `tierStructures` — building more raises a tier

Sub-structures built around the machine raise a component's tier, or count towards one.

```json
"tierStructures": [
  {
    "name": "coolant_tower",
    "tier": 2,
    "component": "cooling",
    "mode": "tier",
    "offsets": [
      [3, 0, 0],
      { "target": [-3, 0, 0], "anchor": [0, 1, 0] }
    ]
  }
]
```

| Key | Default | Meaning |
|---|---|---|
| `name` | **required** | The sub-structure's name. Entries without one are skipped silently |
| `tier` | `1` | Tier granted when it stands (the unit counted when `mode` is `"count"`) |
| `component` | `"structure"` | Which component's tier this raises |
| `mode` | `"tier"` | `"tier"` raises a tier, `"count"` counts instances |
| `offsets` | none | Where it may be built, relative to the machine. Several entries mean several places |

An `offsets` entry may be written two ways.

- **Array** `[a, b, c]` — where the sub-structure sits relative to the machine
- **Object** `{ "target": [...], "anchor": [...] }` — also says which point of the
  sub-structure lines up

Either form with fewer than three entries is **skipped silently, that entry only**.

## 3. Mappings
Mappings link the characters in `layers` to block IDs.

### String Format
`"F": "okmodular:basaltStructure:*"` (the wildcard `*` may be used for metadata)

### Object Format (partly planned)
```json
"S": {
  "block": "okmodular:casing_plain:0",
  "max": 1
}
```

### Multiple Choices
```json
"A": {
  "blocks": [
    "okmodular:casing_plain:0",
    "okmodular:casing_reinforced:0"
  ]
}
```

A `blocks` entry may also be an object, with the block ID under `id`.

```json
"A": {
  "blocks": [
    { "id": "okmodular:casing_plain:0" },
    { "id": "okmodular:casing_reinforced:0" }
  ]
}
```

> [!WARNING]
> An object entry with no `id` **disappears silently**. The structure still forms on the
> remaining choices, so it shows up as "this one block is not accepted for some reason".

## 4. Requirements
Requirements define the internal components (ports) the machine must have.

Available types: `itemInput`, `itemOutput`, `fluidInput`, `fluidOutput`, `energyInput`, `energyOutput`, `manaInput`, `manaOutput`, `gasInput`, `gasOutput`, `essentiaInput`, `essentiaOutput`, `visInput`, `visOutput`

### Array Format
```json
"requirements": [
    { "type": "energyInput", "min": 1 },
    { "type": "itemOutput", "min": 2 }
]
```

### Object Format
Since 1.5.1.4, an object format using each type as a key is also supported.
```json
"requirements": {
    "energyInput": { "min": 1 },
    "itemOutput": 1,
    "fluidInput": { "min": 1, "max": 4 }
}
```
* If the value is a number, it is treated as `min`.

## 5. Reserved Symbols

The following symbols have special meanings in the structure system.

### 5.1 System Reserved Symbols (Mandatory)
**These symbols cannot be overridden in `mappings`.**

| Symbol | Meaning | Description |
| :--- | :--- | :--- |
| `Q` | Controller | **Exactly one is required per structure** |
| `_` | Air | Treated as a forced air block |
| (Space) | Any | Space excluded from validation |

## 6. Commands
- `/okmodular reload`: Reloads structure definitions, recipe definitions and tier definitions.

## 7. See Also

- [Structure IO](./STRUCTURE_IO.md) — the same `layers` / `mappings` vocabulary used to make a
  whole arrangement of blocks one recipe input or output. Files live in
  `config/okmodular/structure_io/`. **The reserved symbols differ slightly**: `Q` there is the
  default anchor rather than a fixed controller.
