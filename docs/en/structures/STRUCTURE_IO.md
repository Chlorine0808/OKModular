# Structure IO: Using an Arrangement of Blocks as One Input or Output

A recipe can require, or produce, a whole *arrangement* of blocks instead of a single one. The
arrangement is written once as a **pattern** and referred to by name from as many recipes as you
like.

Pattern files go in `config/okmodular/structure_io/`, beside `recipes/` and `structures/`. The
directory name is a config value (`structureIoDirectory` in `modular.cfg`).

> [!NOTE]
> The blocks written as `symbol` inputs and outputs ([JSON Format §3.2](../recipes/JSON_FORMAT.md#32-blocks-symbol))
> are counted individually — "three of these somewhere under that symbol". A pattern is the other
> question: "this exact shape, in this orientation, at this block".

## 1. File Structure

A file may hold a single pattern object, an array of them, or an object with a `patterns` array.

```json
{
  "patterns": [
    {
      "name": "altar_core",
      "anchor": "Q",
      "mappings": {
        "S": "minecraft:stone",
        "G": "minecraft:gold_block:0"
      },
      "layers": [
        [ "SSS",
          "SQS",
          "SSS" ],
        [ "G G",
          "   ",
          "G G" ]
      ]
    }
  ]
}
```

## 2. Properties

| Property | Type | Description |
| :--- | :--- | :--- |
| `name` | String | Identifier a recipe refers to (required, must be unique) |
| `layers` | Array | The shape, written exactly as in a structure definition (required) |
| `mappings` | Object | Character → block ID, or [an object describing the cell](#writing-a-cell---chance-and-weights). The list form a structure accepts is not available here |
| `anchor` | String | Single character marking the cell that lands on the block the recipe names. Defaults to `Q` |

`layers` takes either a bare array of row strings or `{ "rows": [...] }`, the same two forms a
structure definition takes.

> [!WARNING]
> **Give every layer the same number of rows.** Row order is reversed within each layer, using
> that layer's own row count, so a layer with fewer rows than the others is shifted against them.
> Pad with rows of spaces rather than leaving them out.

### Reserved characters

| Symbol | Meaning |
| :--- | :--- |
| (Space) | Not part of the pattern. Neither checked nor placed |
| `_` | Air. Checked as air on input, set to air on output |
| the anchor | If it has no entry in `mappings`, it is only a marker and its cell is left alone |

Any other character **must** have an entry in `mappings`. One that does not stops the file from
loading and is reported in `structures/errors.txt`, because a silently dropped character would
make the pattern check fewer blocks than it looks like it checks.

### Writing a cell - chance and weights

An entry in `mappings` takes an object as well as a block ID string.

| Key | Decides | Default |
| :--- | :--- | :--- |
| `block` | The block that goes in the cell | — |
| `weights` | **What goes in, if anything does.** Block ID → how often | — |
| `chance` | **Whether anything goes in at all** (0.0 to 1.0) | `1.0` |

```json
"mappings": {
  "A": "minecraft:stone",
  "B": { "block": "minecraft:stone", "chance": 0.7 },
  "C": { "weights": { "minecraft:iron_ore": 8, "minecraft:gold_ore": 2 } },
  "D": { "chance": 0.7, "weights": { "minecraft:iron_ore": 8, "minecraft:gold_ore": 2 } }
}
```

- **B** - stone in 70% of the cells; the rest are **left alone**
- **C** - every cell filled, split 8:2
- **D** - 70% of the cells filled, and those are split 8:2

`chance` and `weights` are **two independent axes**. Write either or both.

**`block` is the one-entry form of `weights`**, so writing both is a load error: it says what
goes in the same cell twice, and the engine would have to pick one silently.

#### Keys of `weights`

Block IDs, plus the same two reserved characters `layers` uses.

| Key | Meaning |
| :--- | :--- |
| `_` | Air - **clears the cell** |
| `" "` (space) | **Leave the cell alone** |
| `modid:name:*` | ❌ **load error.** A wildcard matches blocks; it does not name one to place |

"Sometimes clear it" and "sometimes skip it" are different things, so both can be written.

#### Writing them as expressions

`chance` and each weight take an [expression](../recipes/EXPRESSION_REFERENCE.md) as readily
as a number.

```json
"mappings": {
  "X": { "chance": "tier * 0.2", "weights": { "minecraft:iron_ore": "tier", "minecraft:gold_ore": 1 } }
}
```

A vein that grows **denser** with the machine, and richer in iron as it does.

**They are evaluated once per placement.** What an expression reads - the tier, how full a
tank is, the weather - is the same at every cell, so evaluating per cell would give the same
answer two thousand times over for a sphere. **Scattering across cells is the coordinates'
job**, not the expression's.

> [!NOTE]
> **An expression-valued `chance` counts as undecided even if it would evaluate to 1.0.**
> What it comes to is not knowable when the file is read. For a pattern a `structure` input
> is meant to use, leave `chance` out or write the constant `1.0`.

> [!IMPORTANT]
> **A cell that loses its `chance` is left alone, not emptied.** Whatever stood there stays.
> To clear it instead, give `weights` an entry for `"_"`.

> [!WARNING]
> **A pattern with a chance or a weight table cannot be used as a `structure` input**, and
> saying so is a load error. Coming out differently every time is the point of it, which
> leaves an input with no arrangement to match against. Left to run, the recipe would simply
> never fire, which reads as a structure that will not detect.

#### How it scatters

The draw mixes in the cell's **world coordinates**, so:

- neighbouring cells get **different answers** - it does not collapse into all-or-nothing
- one recipe writing at **two anchors** gets **two different arrangements**
- within one run the answer never moves, across a save and reload included
- the next run lays it out differently

> [!NOTE]
> **This alone does not make a vein.** Cells draw independently of each other, so the mix comes
> out **evenly scattered** - salt and pepper. Clumping needs noise with spatial correlation.

### The anchor

The recipe names a symbol from the machine's structure; each block recorded for that symbol is a
candidate anchor, and the pattern is laid over it so that the anchor cell sits on that block.

Drawing the anchor inside the pattern is what lets a 3×3×3 arrangement be centred on an altar
rather than hung off a corner. Omit both `anchor` and `Q`, and the pattern's first cell becomes
the reference instead.

If you write an `anchor` that never appears in `layers`, the file is rejected rather than
falling back to the corner.

### Orientation

**A pattern has no `defaultFacing` of its own.** It is transformed with the facing of the machine
it is used in, so it turns with that machine and can never end up rotated against it.

This also means the same file behaves differently in an upright machine than in a horizontal one:
in an upright machine, layers become rows. Write the pattern the way you would write that
machine's own structure.

### Reaching outside the machine

A pattern is not limited to blocks the formation check covers. Cells that fall outside the
machine's own shape resolve normally, so an IO region can sit beside or below the machine.

## 3. Referring to a Pattern from a Recipe

```json
{
  "inputs": [
    { "type": "structure", "pattern": "altar_core", "symbol": "P", "consume": true }
  ],
  "outputs": [
    { "type": "structure", "pattern": "altar_spent", "symbol": "P" }
  ]
}
```

| Key | Meaning |
| :--- | :--- |
| `pattern` | The pattern's `name` |
| `symbol` | The structure symbol whose blocks anchor the pattern |
| `amount` | How many anchors must match / are written to. Default 1. Expressions allowed |
| `consume` | Input only. Clears the whole matched arrangement on start |
| `optional` | Lets the recipe start and finish even when nothing matches |
| `index` | Restricts to ports with this assigned index |
| `pertick` | Repeats the check or the write while the recipe runs |

### As an input

An anchor counts **only when every cell matches**. Partial matches are not partial credit — that
is the difference between a pattern and a handful of block inputs.

`consume` clears the entire arrangement, not a count of blocks: half an arrangement is not a
smaller arrangement, it is a broken one.

### As an output

Every cell is written, air cells included, so the same file both builds an arrangement and — drawn
as air — clears one.

**It overwrites.** There is no per-cell "only if replaceable" filter: a pattern is placed as a
unit, and skipping the occupied cells would leave the machine having produced something that is
not the arrangement the recipe promised. Put a `structure` input in front of it when the recipe
should only run against particular ground.

It will not overwrite the machine, though. An anchor whose cells touch any block the formation
check recorded is skipped, so a pattern anchored near an edge cannot reach through the wall and
replace the structure that is running the recipe.

The capacity check asks whether enough usable anchors exist. A recipe with nowhere to put its
output therefore refuses to start rather than consuming its inputs and placing nothing.

### Choosing where it lands

A symbol usually names many blocks — a floor is one symbol repeated — and **the output picks the
anchor, not you**. With several usable anchors, which one gets written is not something the recipe
controls.

The way to make that decision for it is the pattern's own footprint: **draw it wide enough that
only one anchor can hold it.** A pattern spanning the full width of a chamber fits over exactly
the centre block of the floor, because every other anchor would push it into the wall. An input
and an output drawn to the same footprint therefore always act on the same spot, which is what
makes "transform this arrangement in place" work.

## 4. Schema Versioning

Pattern files run through the structure migration registry on load, so the format can be changed
later and existing files fixed automatically. A migrated file is rewritten, and the original is
kept alongside it as `.bak`.

This is the reason patterns are their own files rather than inline recipe JSON: recipe JSON has no
schema versioning, so anything written there is frozen for good.

## 5. Commands

- `/okmodular reload`: reloads structures, patterns, tiers and recipes. Patterns are reloaded
  before recipes, because a recipe resolves its pattern by name.
