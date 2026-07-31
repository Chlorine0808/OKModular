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
| `speedMultiplier` | Float | Processing speed (default: 1.0) |
| `energyMultiplier` | Float | Energy consumption (default: 1.0) |
| `batchMin` | Integer | Minimum batch size for recipes (default: 1) |
| `batchMax` | Integer | Maximum batch size for recipes (default: 1) |
| `tier` | Integer | Machine tier (default: 0) |
| `tierMap` | Object | The tiers each part of the structure provides |
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

### 2.2 Tier Map Details
`tierMap` assigns a specific tier to part of the machine according to the material (block) used.
```json
"tierMap": {
  "glass": {
    "okmodular:glass:1": 1,
    "okmodular:glass:2": 2
  },
  "casing": {
    "okmodular:modularMachineCasing:0": 1,
    "okmodular:modularMachineCasing:1": 2,
    "okmodular:modularMachineCasing:2": 3
  }
}
```
When a recipe specifies `"tier": { "glass": 2 }`, with the settings above that recipe is only valid on structures built with `glass:2` or better.

## 3. Mappings
Mappings link the characters in `layers` to block IDs.

### String Format
`"F": "okmodular:basaltStructure:*"` (the wildcard `*` may be used for metadata)

### Object Format (partly planned)
```json
"S": {
  "block": "okmodular:modularMachineCasing:0",
  "max": 1
}
```

### Multiple Choices
```json
"A": {
  "blocks": [
    "omoshiroikamo:modifierNull:0",
    "omoshiroikamo:modifierSpeed:0"
  ]
}
```

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
