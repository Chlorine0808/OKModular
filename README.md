# OK Modular

Modular multiblock machinery for Minecraft 1.7.10, split out of
[Omoshiroi Kamo](https://github.com/Shigure-Ruiseki/OmoshiroiKamo)
(the former `module/machinery`). Provides a flexible multiblock machine
system with JSON-based structure and recipe definitions.

## Required Dependencies:

*   [Omoshiroi Kamo](https://github.com/Shigure-Ruiseki/OmoshiroiKamo) (SMM-phase2 or later, machinery removed)
*   [GTNHLib (>= 0.11.19)](https://github.com/GTNewHorizons/GTNHLib)
*   [StructureLib (>= 1.4.39)](https://github.com/GTNewHorizons/StructureLib)
*   [ModularUI2 (>= 2.3.75)](https://github.com/GTNewHorizons/ModularUI2)

## Development setup

OmoshiroiKamo is consumed from the local Maven repository. Before building
this project, publish it once:

```
cd ../OmoshiroiKamo
./gradlew publishToMavenLocal
```

Then build this project as usual:

```
./gradlew build
```

Recipe/structure/tier JSON files are read from `config/okmodular/modular/`
(previously `config/omoshiroikamo/modular/`).

## Features:

*   Machine controller + casing multiblocks defined by JSON structures
*   JSON-defined recipes with item / fluid / energy / gas (Mekanism) /
    essentia & vis (Thaumcraft) inputs and outputs
*   Tiered machine components
*   NEI recipe display and structure previews (via BlockRenderer6343)
*   AE2 / ThaumicEnergistics / Botania integrations
