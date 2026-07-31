# Recipe Conditions

What decides whether a recipe runs or stops. **Checked every tick while running.**

## 📚 Related Documentation

- [JSON Format](./JSON_FORMAT.md)
- [Expression Reference](./EXPRESSION_REFERENCE.md)
- [Machine Conditions](../machinery/MACHINE_CONDITIONS.md)

---

## 1. Kinds of condition

With `type` omitted, the type is inferred from which keys are present.

| type | What it checks | Shorthand |
|---|---|---|
| `block` | The block at the machine's position | `{ "block": "stone" }` |
| `block_below` | The block under the machine at Y-1 | `{ "block_below": "stone" }` |
| `dimension` | Being in a particular dimension | `{ "dimension": 0 }` |
| `biome` | Biome name / tag / temperature and humidity | `{ "biome": "Plains" }` / `{ "tag": "FOREST" }` |
| `offset` | Any condition, evaluated at a relative position `(dx, dy, dz)` | — |
| `pattern` | Surrounding biome layout, written like a crafting recipe | — |
| `weather` | The current weather (`rain` / `thunder` / `clear`) | `{ "weather": "rain" }` |
| `comparison` | Compares two expressions (`left`, `right`, `operator`) | — |
| `expression` | A string expression written directly | `{ "expression": "day % 28 == 0" }` |

Additional properties:

- `dimension` — `ids`: an array of numbers
- `biome` — `biomes`: an array of names / `tags`: an array of Forge BiomeDictionary tags /
  `minTemp` `maxTemp`: temperature range / `minHumid` `maxHumid`: humidity range
- `offset` — `dx` `dy` `dz`: relative position / `condition`: the condition object to evaluate
- `pattern` — `pattern`: an array of strings / `keys`: a mapping from pattern character to condition object

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

## 2. Logical operations

The operator name is used directly as the key.

| Key | Form |
|---|---|
| `and` | `{ "and": [ { condition 1 }, { condition 2 } ] }` |
| `or` | `{ "or": [ ... ] }` |
| `not` | `{ "not": { condition } }` |

`xor` / `nand` / `nor` work the same way.

## 3. The three ways to write a condition

Every condition can be written in these three forms. The parser tries them in this order.

| # | Form | Example |
|---|--------|-----|
| 1 | `type` stated | `{ "type": "comparison", "left": 10, "operator": ">", "right": 5 }` |
| 2 | The key names the type (with a single object as its value) | `{ "comparison": { "left": 10, "operator": ">", "right": 5 } }` |
| 3 | Inferred from the properties | `{ "biome": "Plains" }` |

## 4. Conditions on NBT

Use `nbt(...)` inside an `expression`.

```json
{ "expression": "nbt('energy') >= 1000" }
{ "expression": "nbt('customData.heat') < 500" }
{ "expression": "nbt('S', 'stored_power') > 0" }
{ "expression": "nbt('mode') != 3" }
```

> [!IMPORTANT]
> To make the key's existence itself the condition, pair it with `has_nbt(...)`.
> ```json
> { "expression": "has_nbt('heat') && nbt('heat') <= 100" }
> ```
> The arguments take the same shape as `nbt()` (`has_nbt('key')` / `has_nbt('S', 'key')`).

## 5. Tips

Errors appear in `logs/latest.log`.

The same checks run every tick while the machine is working.
A heavy query such as `count_blocks` placed in a condition costs that much every tick.
