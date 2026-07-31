# Machine Conditions

## 📚 Related Documentation

- [Modular Machinery Documentation](./INDEX.md)
- [Structure JSON Format](../structures/JSON_FORMAT.md)
- [Expression Reference](../recipes/EXPRESSION_REFERENCE.md)

---


## 1. Writing them

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

A single condition needs no array.

```json
"conditions": { "weather": "RAIN" }
```

The conditions available are the same ones a recipe's `conditions` accepts (weather, biome,
dimension, block, expressions, logical operators).
See the [Recipe JSON Format](../recipes/JSON_FORMAT.md) for details.

### The machine's own state counts too

An expression can ask about the machine itself.

```json
"conditions": [
  { "expression": "energy > 1000" }
]
```

For the names available see the [Expression Reference](../recipes/EXPRESSION_REFERENCE.md).

> [!IMPORTANT]
> **Conditions are joined with "AND".** One failure stops the machine.
> For "any one of these", wrap them in `or` explicitly.
>
> ```json
> "conditions": [ { "or": [ { "weather": "RAIN" }, { "weather": "THUNDER" } ] } ]
> ```

## 2. When they break mid-recipe — `conditionPolicy`

You can choose what happens when the conditions stop holding while a recipe is running.

| Value | Behaviour |
|---|---|
| `pause` (default) | Continues from where it stopped once the conditions hold again. Nothing already consumed is lost |
| `abort` | Throws the recipe away. **Inputs already consumed do not come back** |

```json
{
  "conditions": [ { "weather": "RAIN" } ],
  "conditionPolicy": "abort"
}
```


## 3. Tips

Errors are logged on `/okmodular reload`.

### The condition text is worded by the server

The condition shown in the GUI is built in the server's language before being sent to the
client. In multiplayer it may not match the client's language setting.
