# OK Modular

Modular multiblock machinery for Minecraft 1.7.10. Machines, their structures and their recipes are
defined in JSON, so a pack can add new multiblocks without writing code.

Split out of [Omoshiroi Kamo](https://github.com/Shigure-Ruiseki/OmoshiroiKamo), where it used to be
the `module/machinery` package. **OK Modular is a standalone mod — it does not require Omoshiroi Kamo.**

> [!CAUTION]
> **Not a drop-in replacement for Omoshiroi Kamo's machinery module.** Blocks register under `okmodular:`
> instead of `omoshiroikamo:`, so every `omoshiroikamo:modular_*` block, plus `vis_bridge` and
> `modular_machine_controller`, **disappears from existing worlds — installing OK Modular does not bring
> them back.** Back up your world before updating.

## Requirements

| Mod | Minimum version |
|---|---|
| [OKCore](https://github.com/Shigure-Ruiseki/OKCore) | 26.07.10.0 |
| [GTNHLib](https://github.com/GTNewHorizons/GTNHLib) | 0.11.21 |
| [StructureLib](https://github.com/GTNewHorizons/StructureLib) | 1.4.39 |
| [ModularUI2](https://github.com/GTNewHorizons/ModularUI2) | 2.3.79-1.7.10 |

The GTNHLib and ModularUI2 floors come from OKCore, not from this mod.

NEI, Waila, BlockRenderer6343, Thaumcraft, AE2, ThaumicEnergistics, Mekanism, EnderIO, IC2, CoFH and
Botania are integrated when present; none are required.

## Configuration

```
config/okmodular/
  modular.cfg      worldgen.cfg      tiers.json
  recipes/         structures/       ← errors.txt is written here too
```

> [!NOTE]
> **No structure definitions ship with the mod yet** — a fresh install has zero machines until you author
> `structures/*.json` yourself.

## Documentation

| | English | 日本語 |
|---|---|---|
| Structures | [docs/en/structures/](docs/en/structures/) | [docs/jp/structures/](docs/jp/structures/) |
| Recipes | [docs/en/recipes/](docs/en/recipes/) | [docs/jp/recipes/](docs/jp/recipes/) |
| Machines | [docs/en/machinery/](docs/en/machinery/) | [docs/jp/machinery/](docs/jp/machinery/) |

## Migration

- **From Omoshiroi Kamo's machinery**: move `config/omoshiroikamo/modular/{structures,recipes}/` and
  `tiers.json` into `config/okmodular/` (the `modular/` level is gone), then re-domain block references
  inside them to `okmodular:`.
- **From an earlier OK Modular build from source**: move the contents of `config/okmodular/modular/` up one
  level. NBT is now reached only through `nbt(...)` / `has_nbt(...)` — dot notation and the `tile_nbt`
  condition were removed. See [EXPRESSION_REFERENCE](docs/en/recipes/EXPRESSION_REFERENCE.md).

Old config left in place is silently ignored and defaults are regenerated, so it looks like your
structures and recipes vanished. There is no compatibility shim for any of the above.

## Development setup

```
./gradlew build
```

OKCore is resolved from JitPack, so no local publishing step is needed. Gradle wants a modern JDK
(`.java-version` asks for 25; 21 works too); the mod itself ships as Java 8 bytecode.
