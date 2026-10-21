# OK Modular

Modular multiblock machines for Minecraft 1.7.10.
Structures and recipes are defined in JSON.

Split out of [OmoshiroiKamo](https://github.com/Shigure-Ruiseki/OmoshiroiKamo).
**OK Modular is a standalone mod — it does not require OmoshiroiKamo.**

> [!CAUTION]
> **Not a drop-in replacement for OmoshiroiKamo's machinery module.**
> Block IDs moved from `omoshiroikamo:` to `okmodular:`, so machines in an existing world do not come back.
> Back up your world before updating.

## Required mods

| Mod | Minimum version |
|---|---|
| [OKCore](https://github.com/Shigure-Ruiseki/OKCore) | 26.10.10.0 |
| [GTNHLib](https://github.com/GTNewHorizons/GTNHLib) | 0.11.41 |
| [StructureLib](https://github.com/GTNewHorizons/StructureLib) | 1.4.39 |
| [ModularUI2](https://github.com/GTNewHorizons/ModularUI2) | 2.3.79-1.7.10 |

NEI, Waila, BlockRenderer6343, Thaumcraft, AE2, ThaumicEnergistics, Mekanism, EnderIO, IC2, CoFH and Botania are integrated when present.

## Configuration

```
config/okmodular/
  modular.cfg      worldgen.cfg      tiers.json
  recipes/         structures/       ← errors.txt is written here too
  structure_io/
```

### Example machines

On first launch, each of `structures/`, `recipes/` and `structure_io/` that does not exist yet is created and filled with six example machines: Assembler, Metal Former, Pressurized Crusher, Ore Transformer, Phased Alloy Forge and Spatial Shifter.
They are there to be read, changed or deleted.

- A directory that already exists is left alone, so a deleted example stays deleted. Delete the directory to get its examples back.
- The Assembler's own blocks (controller, plain casing, item ports, energy port) and the wrench are made at a crafting table. Every other casing and port is made in the Assembler.
- The machines run on RF from another mod; OK Modular adds no generator.

## Documentation

| Topic | English |
|---|---|
| Structures | [docs/en/structures/](docs/en/structures/) |
| Recipes | [docs/en/recipes/](docs/en/recipes/) |
| Machines | [docs/en/machinery/](docs/en/machinery/) |

## Migration

- **From OmoshiroiKamo's machinery**: move `config/omoshiroikamo/modular/{structures,recipes}/` and `tiers.json` into `config/okmodular/`.
- Then re-domain block references inside them to `okmodular:`.
- NBT is now reached only through `nbt(...)` / `has_nbt(...)` — dot notation and the `tile_nbt` condition were removed.
- The world property `redstone` was renamed to `redstone_signal`. A recipe that still uses `redstone` fails to load with `Unknown variable`.
- See [EXPRESSION_REFERENCE](docs/en/recipes/EXPRESSION_REFERENCE.md).

## Development setup

```
./gradlew build
```

OKCore is resolved from JitPack, so no local publishing step is needed. Gradle wants a modern JDK
(`.java-version` asks for 25; 21 works too); the mod itself ships as Java 8 bytecode.

---

# OK Modular（日本語）

Minecraft 1.7.10 用のモジュラーマルチブロック機械 mod である。
機械の構造とレシピを JSON で定義して、新しいマルチブロック機械を追加できる。

[OmoshiroiKamo](https://github.com/Shigure-Ruiseki/OmoshiroiKamo) の machinery モジュールを分離したものである。
**OK Modular は単体で動き、OmoshiroiKamo を必要としない。**

> [!CAUTION]
> **OmoshiroiKamo の machinery モジュールとそのまま入れ替えることはできない。**
> ブロック ID が `omoshiroikamo:` から `okmodular:` に変わったので、既存のワールドにある機械は戻らない。
> 更新する前に、ワールドのバックアップを取ること。

## 前提 mod

| Mod | 最低バージョン |
|---|---|
| [OKCore](https://github.com/Shigure-Ruiseki/OKCore) | 26.10.10.0 |
| [GTNHLib](https://github.com/GTNewHorizons/GTNHLib) | 0.11.41 |
| [StructureLib](https://github.com/GTNewHorizons/StructureLib) | 1.4.39 |
| [ModularUI2](https://github.com/GTNewHorizons/ModularUI2) | 2.3.79-1.7.10 |

NEI、Waila、BlockRenderer6343、Thaumcraft、AE2、ThaumicEnergistics、Mekanism、EnderIO、IC2、CoFH、Botania が入っていれば、それぞれと連携する。

## 設定ファイル

```
config/okmodular/
  modular.cfg      worldgen.cfg      tiers.json
  recipes/         structures/       ← errors.txt もここに出力される
  structure_io/
```

### お手本の機械

OK Modular は初回起動時に、`structures/`、`recipes/`、`structure_io/` のうちまだ無いディレクトリを作り、お手本の機械 6 台（Assembler、Metal Former、Pressurized Crusher、Ore Transformer、Phased Alloy Forge、Spatial Shifter）を書き出す。
お手本は読むためのもので、書き換えても消してもよい。

- 既にあるディレクトリには書き出さないので、消したお手本は戻らない。戻すには、そのディレクトリごと消す
- クラフト台で作れるのは、Assembler 自身の部品（コントローラ、Plain ケーシング、アイテムポート、エネルギーポート）と Wrench だけである。ほかのケーシングとポートは Assembler で作る
- 機械は他 mod の RF で動く。OK Modular は発電機を追加しない

## ドキュメント

| 対象 | 日本語 |
|---|---|
| 構造体 | [docs/jp/structures/](docs/jp/structures/) |
| レシピ | [docs/jp/recipes/](docs/jp/recipes/) |
| 機械 | [docs/jp/machinery/](docs/jp/machinery/) |

## 移行

- OmoshiroiKamo の machinery から移るときは、`config/omoshiroikamo/modular/{structures,recipes}/` と `tiers.json` を `config/okmodular/` へ移す。
- そのうえで、ファイルの中のブロック参照を `okmodular:` に書き換える。
- NBT には `nbt(...)` / `has_nbt(...)` でしかアクセスできなくなった。ドット記法と `tile_nbt` 条件は削除した。
- 世界プロパティの `redstone` は `redstone_signal` に改名した。`redstone` を書いたままのレシピは、`Unknown variable` で読み込みに失敗する。
- 詳しくは [EXPRESSION_REFERENCE](docs/jp/recipes/EXPRESSION_REFERENCE.md) を参照。

## 開発環境

```
./gradlew build
```

OKCore は JitPack から取得するので、ローカルへの publish は要らない。Gradle の実行には新しい JDK が要る
（`.java-version` は 25 を指定しているが、21 でも動く）。mod 自体は Java 8 のバイトコードとして出力される。
