# Modular Machinery ドキュメント

Modular Machineryモジュールの技術ドキュメント一覧です。

## 📚 ドキュメント

### システム設計

#### [機械の稼働条件](./MACHINE_CONDITIONS.md)
構造定義に条件を書いて、レシピに関係なく機械そのものを止める仕組み。

**内容**:
- 書き方（配列 / 単体、式で機械の状態を見る）
- 稼働中に条件が崩れたとき（`pause` / `abort`）と、`abort` が材料を返さないこと

**対象者**: オリジナルマシンを作りたい人

---

#### [ポートの色分け](./PORT_COLORS.md)
機械のポートを色で塗り分け、同じ色のポートだけを 1 つのまとまりとして扱う仕組み。

**内容**:
- 塗り方
- レシピの選ばれ方・実行順

**対象者**: Player、Modpacker

---

## 機能ガイド

### 動的数量システム (Expression System)
レシピの入出力量を動的に変化させるための式システム。マシンの状態やワールド環境に応じた、柔軟なレシピ設計を可能にする。

**主な機能**:
- **マシン状態の参照**: エネルギー、流体、マナ、ガス、Tier、進捗など
- **ワールド環境の参照**: 時間、天候、月齢、バイオーム、経過日数など
- **数学関数**: 三角関数、対数、べき乗、乱数など
- **条件分岐**: 三項演算子、論理演算子による複雑な制御

**使用例**:
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

**関連ドキュメント**:
- [JSON フォーマット: 動的数量](../recipes/JSON_FORMAT.md#31-動的数量) - 基本的な使い方
- [式パーサー リファレンス](../recipes/EXPRESSION_REFERENCE.md) - 変数・関数のリスト
- [実用例集](../recipes/EXPRESSION_EXAMPLES.md) - パターン別の使用例

**対象者**: オリジナルマシンを作りたい人

---

## 🔗 関連ドキュメント

### Recipe System
- [JSON フォーマット](../recipes/JSON_FORMAT.md)
- [条件 (Conditions)](../recipes/CONDITIONS.md)
- [デコレータ (Decorators)](../recipes/DECORATORS.md)
- [式パーサー リファレンス](../recipes/EXPRESSION_REFERENCE.md)
- [実用例集](../recipes/EXPRESSION_EXAMPLES.md)

### Structure System
- [JSON フォーマット](../structures/JSON_FORMAT.md)

---

*このドキュメントは随時更新されます。*
