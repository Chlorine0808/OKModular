# Modular Machinery ドキュメント

Modular Machinery モジュールの技術ドキュメントの一覧です。
構造体・レシピ・機械の 3 つに分けて、各ドキュメントの内容と対象者を示します。

---

## 1. 構造体

#### [JSON フォーマット](../structures/JSON_FORMAT.md)
マルチブロック構造体を定義する JSON の形式

#### [構造 IO](../structures/STRUCTURE_IO.md)
ブロックの配置そのものを 1 つの入出力として扱う仕組み

## 2. レシピ

#### [JSON フォーマット](../recipes/JSON_FORMAT.md)
レシピを定義する JSON の形式

#### [条件 (Conditions)](../recipes/CONDITIONS.md)
レシピを動かすか止めるかの判定

#### [デコレータ (Decorators)](../recipes/DECORATORS.md)
確率・ボーナス出力・触媒

#### [式パーサー リファレンス](../recipes/EXPRESSION_REFERENCE.md)
式で使える変数・関数の一覧

#### [実用例集](../recipes/EXPRESSION_EXAMPLES.md)
式のパターン別の使用例

### 式システム

動的数量システム (Expression System) は、レシピの入出力量を式で動的に変える仕組みです。
マシンの状態やワールド環境に応じて、入出力量の変わるレシピを作れます。

主な機能は次の 4 つです。
- マシン状態の参照: エネルギー、流体、マナ、ガス、Tier、進捗など
- ワールド環境の参照: 時間、天候、月齢、バイオーム、経過日数など
- 数学関数: 三角関数、対数、べき乗、乱数など
- 条件分岐: 三項演算子、論理演算子

入出力量を式で書く例です。
```json
{
  "inputs": [
    { "item": "minecraft:coal", "amount": "tier * 10 + 5" }
  ],
  "outputs": [
    { "fluid": "steam", "amount": "energy_p * 1000" }
  ]
}
```

詳しくは次のドキュメントを参照してください。
- [JSON フォーマット: 動的数量](../recipes/JSON_FORMAT.md#4-動的数量) - 基本的な使い方
- [式パーサー リファレンス](../recipes/EXPRESSION_REFERENCE.md) - 変数・関数の一覧
- [実用例集](../recipes/EXPRESSION_EXAMPLES.md) - パターン別の使用例

対象者: オリジナルマシンを作りたい人

## 3. 機械

#### [機械の稼働条件](./MACHINE_CONDITIONS.md)
構造定義に条件を書いて、レシピに関係なく機械そのものを止める仕組み

内容:
- 書き方（配列 / 単体、式で機械の状態を見る）
- 稼働中に条件が崩れたときの扱い（`pause` / `abort`）と、`abort` が材料を返さないこと

対象者: オリジナルマシンを作りたい人

#### [ポートの色分け](./PORT_COLORS.md)
機械のポートを色で塗り分け、同じ色のポートだけを 1 つのまとまりとして扱う仕組み

内容:
- 塗り方
- グループの作られ方と見る順番
- レシピの選ばれ方

対象者: Player、Modpacker
