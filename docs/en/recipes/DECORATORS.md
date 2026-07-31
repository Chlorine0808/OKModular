# Recipe Decorators

Decorators add behaviour while a recipe runs or when it finishes.
The same three forms as conditions are available (`type` stated / the key names the type /
inferred from the properties).

## 📚 Related Documentation

- [JSON Format](./JSON_FORMAT.md)
- [Conditions](./CONDITIONS.md)
- [Expression Reference](./EXPRESSION_REFERENCE.md)

---

## 1. Kinds

| type name | Behaviour | Properties used for inference |
|---------|------|------------------------|
| `chance` | Controls the recipe's success chance | `chance` |
| `bonus` | Produces extra output by chance | `chance` + `outputs` |
| `weighted_random` | Picks an output from a weighted list | `outputs` (each with `weight`) / `pool` / `rolls` |
| `requirement` | Checks extra conditions and catalysts while running | `condition` / `requirements` |
| `harvest_block` | Changes the mining characteristics used to break a block | `fortune` / `silkTouch` / `shear` / `harvestLevel` |
| `per_position_probability` | Swaps a block output per position, by chance | `chance` + `symbol` + `output` |
| `bonus_block_output` | Produces extra block output by chance | `chance` + `outputs` (first is `type: "block"`) |
| `random_block_output` | Draws a block output from candidates | `count` / `selections` |

## 2. Writing them

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

Omitting `rolls` on `weighted_random` draws once; omitting `weight` treats it as 1.

`chance` takes an expression as well as a number (`"chance": "0.1 + tier * 0.05"`).

## 3. The requirement decorator

Takes `condition` (an extra condition), `requirements` (catalysts), or both.

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

Each element of `requirements` takes the same form as an input and is **not consumed**.
They are checked on start and every tick, and the recipe stops if they run short.

> [!NOTE]
> **The same thing can be written directly as a non-consuming input.** The line below is
> equivalent to the `requirements` above.
> ```json
> "inputs": [ { "item": "minecraft:redstone", "amount": 10, "consume": false } ]
> ```
> The advantage of the decorator form is that a parent can hold catalyst requirements when
> recipes inherit through `parent`.

> [!IMPORTANT]
> Structure JSON also has a `requirements` key, and it is **a different thing**.
> That one specifies port counts.
