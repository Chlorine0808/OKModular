# Recipe Capture

Recipe capture builds a recipe JSON from the items and fluids already sitting in a machine's
ports, so you do not have to type ids by hand. You fill the ports, run a command, tweak the
draft in chat, and save.

## Flow

1. Build the machine and make sure it is **formed**.
2. Put the inputs in the input ports and the outputs in the output ports. A port may also hold
   fluids.
3. Look at the controller (within 5 blocks) and run
   `/okmodular recipe capture <duration> [group]`.
4. Read the draft with `/okmodular recipe show`. Fix amounts and the duration, remove
   entries you do not want, and set the energy. To add an entry, put it in a port and capture
   again.
5. Run `/okmodular recipe save`. The recipe is appended to a file and recipes are reloaded.

Ports are **not consumed**. Capture only reads them.

## Commands

All commands live under `/okmodular recipe` and need permission level 2.

| Command | What it does |
|---|---|
| `capture <duration> [group]` | Reads the ports of the controller you look at and starts a draft. `duration` is in ticks |
| `show` | Prints the draft. Each entry has `[amount]` and `[remove]` buttons |
| `amount <number> <amount>` | Sets the amount of entry `<number>` (items: count, fluids: mB) |
| `remove <number>` | Removes entry `<number>` |
| `duration <ticks>` | Sets the recipe duration |
| `energy <amount> [perTick]` | Sets the energy. `perTick` defaults to `true` (RF per tick); `false` means a total. `0` removes the energy |
| `save` | Appends the draft to the group's file and reloads recipes |
| `discard` | Throws the draft away |

Entries are numbered **inputs first, then outputs**. Energy has no number. Clicking `[amount]`
or `[remove]` in `show` only puts the command in the chat box. You press Enter yourself.

## What is captured

- Items merge by id and meta. Fluids merge by name.
- Item amounts may exceed 64. Fluid amounts are in mB.

## Where it is saved

`save` appends to `config/okmodular/recipes/captured/<group>.json`.

- `group` defaults to the controller's first recipe group, or `default` if it has none. It must
  match `[A-Za-z0-9_.-]`.
- If the file does not exist it is created with a `"group"` header.
- Files you wrote by hand are never touched.
- A captured file that is malformed is refused and left as it is.
- A recipe without a `registryName` is named `captured_<group>_<n>`, where `n` is the number of
  recipes in the file plus one (names already in the file are skipped). To rename it, edit the JSON.
- `save` clears the draft. Running `capture` again overwrites the existing draft.
- One draft exists per player. It is lost on logout and on server stop.

## What is not captured

Add these by hand in the JSON after saving. See [Recipe JSON Format](./JSON_FORMAT.md).

- NBT and NBT matching
- Ore dictionary entries
- Port index
- Other resource types (mana, gas, essentia, vis, blocks, structures)
- Chance and catalyst
