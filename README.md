# OK Modular

Modular multiblock for Minecraft 1.7.10.
Structures and recipes are defined in JSON

Split out of [OmoshiroiKamo](https://github.com/Shigure-Ruiseki/OmoshiroiKamo). 
**OK Modular is a standalone mod — does not require OmoshiroiKamo.**

> [!CAUTION]
> **Not a drop-in replacement for OmoshiroiKamo's machinery module.**
> Back up your world before updating.

## Required mods

| Mod | Minimum version |
|---|---|
| [OKCore](https://github.com/Shigure-Ruiseki/OKCore) | 26.07.10.0 |
| [GTNHLib](https://github.com/GTNewHorizons/GTNHLib) | 0.11.21 |
| [StructureLib](https://github.com/GTNewHorizons/StructureLib) | 1.4.39 |
| [ModularUI2](https://github.com/GTNewHorizons/ModularUI2) | 2.3.79-1.7.10 |

NEI, Waila, BlockRenderer6343, Thaumcraft, AE2, ThaumicEnergistics, Mekanism, EnderIO, IC2, CoFH and Botania are integrated when present.

## Configuration

```
config/okmodular/
  modular.cfg      worldgen.cfg      tiers.json
  recipes/         structures/       ← errors.txt is written here too
```

## Documentation

| | English |
|---|---|---|
| Structures | [docs/en/structures/](docs/en/structures/) |
| Recipes | [docs/en/recipes/](docs/en/recipes/) |
| Machines | [docs/en/machinery/](docs/en/machinery/) |

## Migration

- **From OmoshiroiKamo's machinery**: move `config/omoshiroikamo/modular/{structures,recipes}/` and `tiers.json` into `config/okmodular/`.
- Then re-domain block references inside them to `okmodular:`.
- NBT is now reached only through `nbt(...)` / `has_nbt(...)` — dot notation and the `tile_nbt` condition were removed. 
- See [EXPRESSION_REFERENCE](docs/en/recipes/EXPRESSION_REFERENCE.md).

## Development setup

```
./gradlew build
```

OKCore is resolved from JitPack, so no local publishing step is needed. Gradle wants a modern JDK
(`.java-version` asks for 25; 21 works too); the mod itself ships as Java 8 bytecode.

---

# OK Modular（日本語）

Minecraft 1.7.10 用のモジュラーマルチブロック機械 mod です。
機械・その構造・レシピを JSON で定義し、新しいマルチブロックを追加できます。

[OmoshiroiKamo](https://github.com/Shigure-Ruiseki/OmoshiroiKamo) からModularモジュールを分離したものです。
**OK Modular は単体で動作し、OmoshiroiKamo を前提としません。**

> [!CAUTION]
> **OmoshiroiKamo の machinery モジュールとの差し替えはできません。** 
> 更新前にワールドのバックアップを取ってください。

## 前提Mod

| Mod | 最低バージョン |
|---|---|
| [OKCore](https://github.com/Shigure-Ruiseki/OKCore) | 26.07.10.0 |
| [GTNHLib](https://github.com/GTNewHorizons/GTNHLib) | 0.11.21 |
| [StructureLib](https://github.com/GTNewHorizons/StructureLib) | 1.4.39 |
| [ModularUI2](https://github.com/GTNewHorizons/ModularUI2) | 2.3.79-1.7.10 |

NEI, Waila, BlockRenderer6343, Thaumcraft, AE2, ThaumicEnergistics, Mekanism, EnderIO, IC2, CoFH, Botania との連携があります

## Config

```
config/okmodular/
  modular.cfg      worldgen.cfg      tiers.json
  recipes/         structures/       ← errors.txt もここに書かれます
```

## ドキュメント

| | 日本語 |
|---|---|---|
| 構造体 |  [docs/jp/structures/](docs/jp/structures/) |
| レシピ |  [docs/jp/recipes/](docs/jp/recipes/) |
| 機械 |  [docs/jp/machinery/](docs/jp/machinery/) |

## 移行

- OmoshiroiKamo から: `config/omoshiroikamo/modular/{structures,recipes}/` と`tiers.json` を `config/okmodular/` へ移してください。
- そのうえで、中のブロック参照を `okmodular:` に書き換えます。
- NBT へのアクセスは `nbt(...)` / `has_nbt(...)` だけになり、ドット記法と `tile_nbt` 条件は削除されました。
- [EXPRESSION_REFERENCE](docs/jp/recipes/EXPRESSION_REFERENCE.md) を参照してください。

## 開発環境

```
./gradlew build
```

OKCore は JitPack から解決されるので、ローカルへの publish は不要です。Gradle には新しい JDK が必要です
（`.java-version` は 25 を要求しますが 21 でも動きます）。mod 自体は Java 8 バイトコードとして出力されます。
