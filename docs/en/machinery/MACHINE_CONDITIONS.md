# Machine Conditions

Put conditions in a structure definition and **that machine stops whenever they are not
met, whatever recipe it was about to run**. It sits in the same place as the redstone check.

## 📚 Related Documentation

- [Modular Machinery Documentation](./INDEX.md)
- [Structure JSON Format](../structures/JSON_FORMAT.md)
- [Expression Reference](../recipes/EXPRESSION_REFERENCE.md)

---

## 1. Not the same as a recipe's conditions

There are two places to write conditions, and they work independently.

| Written in | What it gates |
|---|---|
| A recipe's `conditions` | **That recipe only** - a recipe whose conditions fail is not chosen |
| **A structure's `conditions`** | **The machine itself** - it stops whatever it was going to run |

"This machine only works in the rain" belongs to the structure. "This recipe can only be
made in the rain" belongs to the recipe.

## 2. Writing them

At the top level of a structure definition.

```json
{
  "name": "rain_collector",
  "conditions": [
    { "weather": "RAIN" }
  ],
  "layers": [ "..." ]
}
```

**One condition needs no array.**

```json
"conditions": { "weather": "RAIN" }
```

The conditions available are **the same ones a recipe can use** - weather, biome,
dimension, block, expressions, logical operators. See the
[Recipe JSON Format](../recipes/JSON_FORMAT.md).

### The machine's own state counts too

An expression can ask about the machine.

```json
"conditions": [
  { "expression": "energy > 1000" }
]
```

The names available are in the [Expression Reference](../recipes/EXPRESSION_REFERENCE.md).

## 3. What happens when they are not met

**The machine does not advance a single tick.** The GUI says
`Conditions not met: <condition>`, naming **the first one that failed**.

> [!IMPORTANT]
> **Conditions are joined with "and".** One failure stops the machine.
> For "any one of these", say so with `or`.
>
> ```json
> "conditions": [ { "or": [ { "weather": "RAIN" }, { "weather": "THUNDER" } ] } ]
> ```

## 4. When they break mid-recipe - `conditionPolicy`

Conditions can stop holding part-way through a recipe: the rain stops, the power drops.

| Value | Behaviour |
|---|---|
| **`pause`** (default) | **Freeze** and wait. When the conditions hold again the run **continues from where it stopped**. **Nothing already consumed is lost** |
| `abort` | **Throw the recipe away**. **Inputs already consumed do not come back** |

```json
{
  "conditions": [ { "weather": "RAIN" } ],
  "conditionPolicy": "abort"
}
```

`pause` is the default because it is what a redstone signal already does and it loses
nothing. `abort` is for machines meant to feel fragile - where losing the weather part-way
through is meant to ruin the batch.

> [!WARNING]
> **`abort` does not give inputs back.** It is the only path in this mod that throws away a
> recipe which may already have consumed something. Not for a machine a player leaves
> running unattended.

## 5. Writing nothing changes nothing

Both keys are optional. A structure definition that says nothing behaves **exactly** as it
did before this existed, and a machine with no conditions allocates nothing to evaluate, so
there is no cost either.

## 6. Things to know

### A misspelled condition is ignored

A condition that cannot be read is **dropped at load time with a warning** naming the file.
That **loosens the gate rather than closing it** - the machine can run with fewer
conditions than its author wrote. Check the log after `/ok multiblock reload`.

A misspelled `conditionPolicy` warns as well and leaves the default `pause` in place.

### The condition text is worded by the server

The condition shown in the GUI is built on the **server's locale** and then sent to the
client, so in multiplayer it may not match the client's language setting. The existing
"Output Capacity Insufficient" line works the same way.
