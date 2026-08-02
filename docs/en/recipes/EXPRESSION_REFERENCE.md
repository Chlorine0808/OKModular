# Expression Parser Variable & Function Reference

A comprehensive reference for variables, functions, and advanced queries available in OmoshiroiKamo's `ExpressionParser`.
These can be used in the `amount` field of JSON recipes, the `expression` key in `Condition` objects, and machine property configurations.

## 📚 Related Documentation

- [JSON Format](./JSON_FORMAT.md) - Basic JSON syntax
- [Conditions](./CONDITIONS.md) - writing an `expression` condition
- [Decorators](./DECORATORS.md) - passing an expression to `chance` and the like
- [Practical Examples](./EXPRESSION_EXAMPLES.md) - Real-world recipe patterns

---

## 1. Basic Operations

Standard arithmetic and logical operations are supported.

- **Arithmetic**: `+`, `-`, `*`, `/`, `%` (modulo)
- **Comparison**: `==`, `!=`, `>`, `<`, `>=`, `<=`
- **Logical**: `&&` (AND), `||` (OR), `!` (NOT)
- **Grouping**: `()`, `{}`

> [!TIP]
> Conditional expressions (e.g., `1 == 1`) are treated as `1` (true) or `0` (false) in numerical calculations.
> Example: `10 + (day > 100) * 5` (15 after day 100, otherwise 10)

## 2. Built-in Variables

### World Variables
Retrieve information about the world where the machine is located.

#### Time & Calendar
- `day` / `total_days`: Cumulative elapsed days
- `time`: Current time of day (0 - 23999)
- `tick`: Total world time in raw ticks
- `moon` / `moon_phase`: Current moon phase (0 - 7; 0 = Full Moon, 4 = New Moon)

#### Coordinates & Dimension
- `x` / `y` / `z`: Coordinates of the machine controller
- `dimension`: Current dimension ID (Overworld = 0, Nether = -1, End = 1)

#### Weather & Environment
- `is_day`: Whether it is daytime (1 or 0)
- `is_night`: Whether it is nighttime (1 or 0)
- `is_raining`: Whether it is raining (1 or 0)
- `is_thundering`: Whether it is thundering (1 or 0)
- `temp`: Biome temperature at the controller's location (0.0 to 2.0)
- `humidity`: Biome humidity at the controller's location (0.0 to 1.0)

#### Light & Space
- `light`: Block light level at the controller (0 - 15; combined sky and block light)
- `light_block`: Raw block light level (0 - 15)
- `light_sky`: Raw sky light level (0 - 15)
- `can_see_sky`: Whether the controller can see the sky (1 or 0)
- `can_see_void`: Whether there's a void directly below the controller to Y=0 (1 or 0)

#### Recipe Progress
- `recipe_tick`: Elapsed time since the current recipe started (ticks)
- `progress_tick`: Raw tick progress value of the current recipe

#### Miscellaneous
- `redstone_signal`: Redstone signal strength received by the controller (0 - 15)
  - The bare name `redstone` is reserved for a future redstone port and is not a variable
- `random_seed`: Seed value for the recipe evaluation session (used for reproducible `random()` / `chance()`)
- `world_seed`: Seed of the world
- `facing`: Direction the machine is facing (0:Down, 1:Up, 2:North, 3:South, 4:West, 5:East)

### Constants
- `pi`: Pi (π ≒ 3.14159)
- `e`: Napier's constant (e ≒ 2.71828)

## 3. Machine Properties

Retrieve the current state of the machine.

### Every resource kind reads the same way

**All seven resource kinds share the same suffixes.** Read `K` below as the name
of a kind.

`K` = `item` / `fluid` / `gas` / `energy` / `mana` / `essentia` / `vis`

| Written as | Meaning |
|---|---|
| `K` / `K_stored` / `K_total` / `total_K` | How much is held |
| `K_max` / `K_capacity` / `total_K_max` / `total_K_capacity` | Capacity |
| `K_f` / `K_free` / `K_space` | Room left |
| `K_p` / `K_percent` | Fill ratio (0.0 - 1.0) |

That gives names like `gas_stored`, `total_vis` and `essentia_percent`.

Kinds whose input and output are **separate storage** (`item`, `fluid`, `gas`) also
have directional names.

| Written as | Meaning |
|---|---|
| `K_in` / `K_out` | Amount on the input / output side |
| `K_f_in` / `K_f_out` | Room left on the input / output side |

`energy`, `mana`, `essentia` and `vis` are single pools, so they have no
directional names - a direction would have nothing to select and the answer would
be the total.

### Asking about one specific resource

To ask about a particular fluid, gas, aspect or item rather than the kind as a whole, pass one argument to the same name.
Exactly one argument is required; more or fewer is an error.

```
fluid("water")        item("minecraft:stone")     essentia("ignis")
fluid_in("water")     item_out("minecraft:iron_ingot")
fluid_f_out("lava")   item_f_in("minecraft:coal")
```

Which forms accept an argument depends on the kind, because naming a resource only
means something where the kind holds more than one:

| Kind | Argument forms available |
|---|---|
| `item` | `item`, `item_in`, `item_out`, `item_f`, `item_f_in`, `item_f_out` |
| `fluid` / `gas` | `K`, `K_in`, `K_out`, `K_f_in`, `K_f_out` |
| `essentia` / `vis` | `K` only |
| `energy` / `mana` | none - a single pool of one thing has nothing to name |

### Notes

- **Energy**: `power` and `power_p` are aliases of `energy` and `energy_p`.
  `energy_per_tick` is the energy drawn per tick (the total of the running recipe's `perTick` inputs), which is not an amount held and so is not in the table above.
- **Items**: capacity is expressed as slots * 64. `item_f` is not that capacity
  minus the count, though - it is **how many actually fit**, since stack limits
  differ per item and the subtraction would not match reality.
- **Item slots**: `item_slot()` total slots, `item_slot_in()` / `item_slot_out()` per direction, `item_slot_empty()` empty slots.

> [!NOTE]
> **Directional room is not the shared capacity minus what is held.**
> Fluids and gases keep separate input and output tanks, so `fluid_f_in` answers
> for the input tanks only. `fluid_f`, with no direction, is capacity minus held.

### Statistics & State
- `recipe_count`: Cumulative number of recipes processed by the machine
- `progress` / `progress_percent`: Current recipe progress (0.0 ~ 1.0)
- `tier`: Current machine Tier
- `is_running`: Whether the machine is running (1 or 0)
- `timeplaced`: Cumulative time since the machine was placed (ticks)
- `timecontinue`: Continuous uptime of the machine (ticks)

### Structural Properties
Performance multipliers provided by the structure definition.

- `batch`: Current batch size
- `speed_multi`: Speed multiplier
- `energy_multi`: Energy multiplier

> [!NOTE]
> **The batch size is applied to amounts for you.** An amount written as an expression, such as `"amount": "2 + tier"`, is tripled when the machine runs a batch of three. Do not multiply by `batch` inside the expression — that applies it twice.
> The `batch` variable is for when the batch size itself is what you want to reason about.
>
> **An output produced on completion draws once per item of the batch.** `"1 + floor(random() * 3)"`
> at a batch of three yields anything from 3 to 9, not only multiples of three: a batch of n
> is n runs of the recipe.
>
> **The `bonus` and `weighted_random` decorators follow the same rule** - a batch of three
> rolls three times. See [Decorators](./DECORATORS.md#batches).

---

## 4. Function Reference

### Math Functions
- `abs(x)`, `sqrt(x)`, `pow(base, exp)`
- `min(a, b...)`, `max(a, b...)`
- `sin(x)`, `cos(x)`, `tan(x)` (Input in **radians**)
- `asin(x)`, `acos(x)`, `atan(x)` (Output in radians)
- `rad(deg)`, `deg(rad)` (Convert degrees/radians)
- `floor(x)`, `ceil(x)`, `round(x)`
- `clamp(val, min, max)`
- `random()`: Random number between 0 and 1
- `chance(x)`: Returns 1 or 0 based on probability `x` (0.0 - 1.0)

> [!IMPORTANT]
> **These are not a stream that advances on each call.** They are computed from the machine's
> evaluation seed (`random_seed`), so **within one run they return the same value however
> many times they are evaluated**, including across a save and reload. A different run gives
> a different value.
>
> So `"1 + floor(random() * 3)"` evaluated every tick does not move. To make it move, mix
> `progress_tick` or `tick` into the expression.
>
> There are two exceptions, both for drawing n times inside one run: **batches** (the note
> above) and **decorators that draw per position**
> ([Decorators](./DECORATORS.md#3-how-the-draws-work)). Those shift the seed and draw again.

### Advanced Functions
- `can_see_sky(filter...)`: Check sky visibility. Specify IDs to treat blocks like glass as transparent
- `can_see_void(filter...)`: Check if there is void directly below

> [!NOTE]
> **Both also work without the brackets**, as the world properties listed above -
> `can_see_sky == 1`. The bare form is the plain test; reach for the function form only when
> you need to name blocks to see through.
> **Either spelling counts from the block above the controller** (below it, for
> `can_see_void`). The controller itself is a solid cube, so including its own position
> would make the answer always false.
- `count_blocks(distance, filter...)`: Count specific blocks within a range
    - Example: `count_blocks(1, "minecraft:iron_block")`
- `nbt('key')`: Retrieve NBT from the machine itself
- `nbt('symbol', 'key')`: Retrieve NBT from a block at a specific symbol position
- `has_nbt('key')` / `has_nbt('symbol', 'key')`: Whether the NBT key exists

### All NBT access goes through `nbt(...)`

**The target is always an argument.** Nested paths go inside the argument string,
separated by dots.

```
nbt('energy')                a top-level key on the machine itself
nbt('display.Name')          a nested path on the machine itself
nbt('S', 'stored_energy')    the TileEntity at structure symbol S
nbt('S', 'a.b.c')            a nested path on that TileEntity
```

#### Writing (assignment)

Put `nbt(...)` on the left of an assignment. Intermediate compounds are created
for you.

```json
"nbt": "nbt('customData.level') = 7"
"nbt": "nbt('temperature') += 50"
"nbt": "nbt('display.Name') = 'Excalibur'"
```

- Operators: `=` `+=` `-=` `*=` `/=`
- **You cannot assign through a symbol** (`nbt('S', 'x') = 1` is an error). Writes
  land on the NBT owned by the field the expression was written in — the output
  item, or the block being placed — whereas a symbol names a block to read from

#### Type suffixes

A suffix on a number pins the NBT tag type. Without one, the value is written as a
double.

| Suffix | Type | Example |
|---|---|---|
| `b` / `B` | byte | `127b` |
| `s` / `S` | short | `32767s` |
| `i` / `I` | int | `2147483647i` |
| `L` | long | `9223372036854775807L` |
| `f` / `F` | float | `3.14159f` |
| `d` / `D` | double | `2.718281828d` |

Long is uppercase only — a lowercase `l` reads too easily as a `1`.

---

## 5. Design Tips

### Where expressions are accepted

| Field | Evaluated |
|-------|-----------|
| `duration` | When the recipe starts (every tick while running if the structure sets `"durationPolicy": "perTick"`) |
| `amount` on an input | When it is consumed - every tick for `perTick`, otherwise at the start |
| **`amount` on an output produced on completion** | **When the recipe starts.** See the note below |
| `amount` on a `perTick` output | Every tick |
| Resource amounts such as `energy` and `mana` | As above, by whether they are input or output and `perTick` |
| A recipe's `chance` decorator | Once per run (settled on completion) |
| An output decorator's `chance` | When it is rolled |
| `condition` / `conditions` | When the recipe starts, and every tick while it runs |

> [!NOTE]
> **How much a recipe pays out is settled when it starts.** Change the machine's tier
> mid-run and the recipe still hands back what it was worth when it began. Its inputs were
> taken at the start, so anything else would let you begin at a low tier and finish at a
> high one.
>
> Only the amount is settled. An output item's `nbt` expressions and the decorators
> (`chance`, `bonus`) are still evaluated when the output is produced.
| The structure's `speedMultiplier` | Every tick while running |
| The structure's `batchMin` / `batchMax` | When a recipe starts |
| The structure's `energyMultiplier` | When a recipe reads `energy_multi` |

A structure's performance modifiers see the machine just as a recipe does, so
`"speedMultiplier": "1 + tier * 0.25"` works. **A modifier cannot read itself**, though —
see [the structure JSON format](../structures/JSON_FORMAT.md).

### Common Pitfalls
- **Quotes in JSON**: Expressions themselves must be strings in JSON, e.g., `"amount": "tier * 2"`.
- **Case Sensitivity**: Variable names are all **lowercase** (e.g., `tier`, not `Tier`).
- **Characters**: Do not use full-width or non-standard characters for operators or variable names.
- **Division by zero**: an expression dividing by a fill ratio or a stored amount divides by zero when empty.
  Check the denominator first, as in `energy_p > 0 ? floor(1000 / energy_p) : 0`.
- **Putting `speed_multi` in `duration`**: the engine applies the speed multiplier every
  tick, so writing it here applies it **twice**. A duration is a work amount, not a time.
  Energy is the reverse: writing `energy_multi` into `energy` is the correct route.

Errors from a failed expression parse appear in `logs/latest.log`.

### How this looks in NEI

NEI has no machine, so it cannot evaluate machine-dependent variables like `tier` or
`speed_multi`. When a `duration` is a machine-dependent expression, NEI shows **the
expression itself** instead of a time. Expressions written purely from constants are
folded to a number at load time and still show as seconds.

### Performance
Since expressions may be evaluated every tick, avoid extremely complex logic or excessive use of wide-range `count_blocks` queries.
