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
| `mappings` | Object | Character → block ID. Block IDs only; the object and list forms a structure accepts are not available here |
| `anchor` | String | Single character marking the cell that lands on the block the recipe names. Defaults to `Q` |

`layers` takes either a bare array of row strings or `{ "rows": [...] }`, the same two forms a
structure definition takes.

### Reserved characters

| Symbol | Meaning |
| :--- | :--- |
| (Space) | Not part of the pattern. Neither checked nor placed |
| `_` | Air. Checked as air on input, set to air on output |
| the anchor | If it has no entry in `mappings`, it is only a marker and its cell is left alone |

Any other character **must** have an entry in `mappings`. One that does not stops the file from
loading and is reported in `structures/errors.txt`, because a silently dropped character would
make the pattern check fewer blocks than it looks like it checks.

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

The capacity check asks whether enough anchors exist. A recipe whose anchor symbol is missing
therefore refuses to start rather than consuming its inputs and placing nothing.

## 4. Schema Versioning

Pattern files run through the structure migration registry on load, so the format can be changed
later and existing files fixed automatically. A migrated file is rewritten, and the original is
kept alongside it as `.bak`.

This is the reason patterns are their own files rather than inline recipe JSON: recipe JSON has no
schema versioning, so anything written there is frozen for good.

## 5. Commands

- `/okmodular reload`: reloads structures, patterns, tiers and recipes. Patterns are reloaded
  before recipes, because a recipe resolves its pattern by name.
