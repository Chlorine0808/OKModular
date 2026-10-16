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
| `layers` | Array | The shape, written exactly as in a structure definition (**exclusive** with `shape`; one of them is required) |
| `shape` | Object | **Generates** the shape instead of `layers`. [See below](#generating-a-shape-instead-of-drawing-it) |
| `mappings` | Object | Character → block ID, or [an object describing the cell](#writing-a-cell---chance-and-weights). The list form a structure accepts is not available here |
| `anchor` | String | Single character marking the cell that lands on the block the recipe names. Defaults to `Q` |

`layers` takes either a bare array of row strings or `{ "rows": [...] }`, the same two forms a
structure definition takes.

A shape as simple as a sphere need not be drawn at all - see
[`shape`](#generating-a-shape-instead-of-drawing-it).

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

**A `chance` or a weight written as a plain number is evaluated once per placement.** The tier,
how full a tank is, the weather - all the same at every cell, so evaluating per cell would give
the same answer two thousand times over for a sphere. **Scattering across cells is the
coordinates' job**, not the expression's.

**An expression is evaluated per cell**, because it can read `cell_x` / `cell_y` / `cell_z`
below and so its value is not knowable at placement time. An expression that only reads the
machine is carried along with them; that is a deliberate trade, keeping one test for "can this
be settled early" in exchange for a few thousand cheap evaluations.

#### Reading where the cell is

| Variable | What it is | Direction |
| :--- | :--- | :--- |
| `x` / `y` / `z` | **the cell's own world position** | as Minecraft's |
| `cell_x` / `cell_y` / `cell_z` | **how far the cell sits from the anchor** | the pattern's axes; `cell_y` is up as drawn; turns with the machine |

On a machine lying down, `y = the anchor's world y + cell_y`. On an upright machine it does not hold, because the pattern's up points sideways.

```json
"mappings": {
  "X": { "block": "minecraft:iron_ore",
         "chance": "1 - sqrt(cell_x^2 + cell_y^2 + cell_z^2) / 8" }
}
```

A blob of radius eight centred on the anchor, **thinning toward the edge**. Draw one solid cube
of `X` and the expression makes the gradient; there is no need to split the shells across
several characters and draw each layer by hand.

| What you want | Written as |
| :--- | :--- |
| A softened outline | `"chance": "1 - sqrt(cell_x^2 + cell_y^2 + cell_z^2) / 8"` |
| Thinner above the anchor | `"chance": "1 - cell_y * 0.2"` (`cell_y` is up) |
| Better ore toward the middle | weights written in terms of the distance |
| Only below a depth | `"chance": "y < 40 ? 1 : 0"` |
| Stripes, chequerboard | `"chance": "cell_x % 2"` |

> [!NOTE]
> **`cell_*` is measured on the pattern's axes, so it turns with the shape and the machine.**
> An upright machine gets the shape and gradient as drawn. On a north-facing machine lying down
> the values equal world offsets. For something that should stay level with the world, such as
> strata, write it with the world `y` rather than `cell_y`.

> [!NOTE]
> **`cell_*` only means anything inside a pattern's `mappings`.** Written in a recipe's `amount`
> or `duration` it answers zero, the way `tier` answers zero when no machine is connected.

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

#### Making it clump - `noise(scale)`

A `chance` is drawn **independently at every cell**, so a density that falls off by distance still
leaves the inside evenly scattered. `noise(scale)` is a field where **near positions answer alike**.

> [!IMPORTANT]
> **Threshold the noise; do not hand it over as a probability.**
>
> ```json
> "chance": "noise(8)"        ← does not clump
> "chance": "noise(8) > 0.5"  ← clumps
> ```
>
> A smooth `chance` still leaves **the placement itself an independent draw per cell**. Where the
> field sits near 0.5 you get the same 50/50 scatter as before, so what you have made is an even
> scatter whose density drifts smoothly.
>
> A comparison answers 1 or 0, which **skips the draw**, and the shape of the field comes through
> as it is. Raise the threshold for smaller blobs, lower it for bigger ones.

```json
"mappings": {
  "X": { "block": "minecraft:iron_ore",
         "chance": "noise(6) > 0.45 ? 1 - sqrt(cell_x^2 + cell_y^2 + cell_z^2) / 8 : 0" }
}
```

**Ore only inside blobs six blocks across, thinning toward the sphere's edge.** That is what a vein
looks like; the distance draw inside each blob is what leaves its rim ragged. And with `shape`
generating the sphere, without drawing a character of `layers`.

`scale` is how many blocks across one blob is. Larger is smoother and broader; **taken down to 1 it
is evenly scattered again**, because a lattice spacing of one block leaves nothing to interpolate.
A literal of 0 or less is a load error; an expression that falls there at run time is treated as 1.

> [!WARNING]
> **`noise()` is not uniformly distributed.** Interpolating between lattice corners piles values up
> around 0.5. `"chance": "noise(8)"` fills roughly half the cells, but **`noise(8) < 0.2` passes a
> few percent rather than twenty.** If you are aiming at a fraction, check it in game.

> [!NOTE]
> **Nothing guarantees one connected blob.** A threshold of the field may come out split, or with
> holes. What `noise()` promises is that it looks like clumps.

> [!NOTE]
> **Within one run it is a single continuous field.** The field is a function of world position, so
> two anchors produce arrangements that **join up** rather than two independent ones - unlike a
> `chance`'s scatter, which differs per anchor. The next run is a different field.

> [!NOTE]
> **`cell_*` can only take away.** A cell that was not drawn does not appear. Getting something
> to carve without drawing it is what `shape` in the next section is for.

### Generating a shape instead of drawing it

Write `shape` in place of `layers` and the cells are generated.

```json
{
  "name": "vein",
  "shape": { "type": "sphere", "radius": 8, "symbol": "X" },
  "mappings": {
    "X": { "block": "minecraft:iron_ore",
           "chance": "1 - sqrt(cell_x^2 + cell_y^2 + cell_z^2) / 8" }
  }
}
```

A sphere of radius eight - **2109 cells** - without writing 17×17×17 = 4913 characters. With the
`chance` above it is a vein with a softened outline.

| Key | Type | Meaning |
| :--- | :--- | :--- |
| `type` | String | Which shape. `sphere` is the only one so far |
| `radius` | Number | **Not an expression** (see below) |
| `symbol` | String | The single character every cell gets; needs an entry in `mappings` |
| `offset` | `[x, y, z]` | Where the shape's centre sits from the anchor, on the cell_* axes. Optional; the centre is on the anchor without it |

Membership is the integer test `dx² + dy² + dz² <= radius²`, so **the same radius always generates
the same cells**. Radius 0 is 1 cell, 1 is 7, 2 is 33, 4 is 257, 8 is 2109, 16 is 17077.

- **Writing `layers` as well is a load error** - that says what the cells are twice
- **The anchor is the centre**, or with `offset`, the cell that puts the centre at that offset.
  Writing `anchor` as well is a load error: every cell holds the same character, so there is
  nothing for it to pick out
- **The shape is solid.** For a shell, carve it with a `chance` -
  `"chance": "sqrt(cell_x^2 + cell_y^2 + cell_z^2) > 7"` leaves the outer layer
- **20000 cells per pattern** is the limit, because every cell is written in one go. Radius 16
  fits; 17 does not

> [!IMPORTANT]
> **A shape used as an output needs an `offset`.** Without one its centre sits on the block the
> recipe names, which is part of the machine, and an output never writes over the machine - so
> no anchor qualifies and the recipe does not start. Move the shape clear of the machine:
>
> ```json
> "shape": { "type": "sphere", "radius": 3, "symbol": "X", "offset": [0, 5, 0] },
> "mappings": { "X": { "block": "minecraft:iron_ore",
>                      "chance": "1 - sqrt(cell_x^2 + (cell_y - 5)^2 + cell_z^2) / 3" } }
> ```
>
> `cell_*` still measure from the anchor, so a distance from the centre subtracts the offset.

> [!WARNING]
> **`radius` cannot be an expression.** The shape is settled when the file is read and cached per
> machine facing from then on - `cellsFor` is handed a facing and nothing else - so there is
> nowhere for one to be evaluated.
>
> To have the tier decide the size, **generate the larger shape and carve it**:
> `"chance": "sqrt(cell_x^2 + cell_y^2 + cell_z^2) <= tier"` makes the tier the radius.

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
