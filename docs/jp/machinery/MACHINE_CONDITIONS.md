# 機械の稼働条件

## 📚 関連ドキュメント

- [Modular Machinery ドキュメント](./INDEX.md)
- [構造体の JSON フォーマット](../structures/JSON_FORMAT.md)
- [式のリファレンス](../recipes/EXPRESSION_REFERENCE.md)

---


## 1. 書き方

構造定義のトップレベルに書きます。

```json
{
  "name": "rain_collector",
  "conditions": [
    { "weather": "RAIN" }
  ],
  "layers": [ "..." ]
}
```

1 つだけなら配列にしなくてよいです。

```json
"conditions": { "weather": "RAIN" }
```

書ける条件はレシピの `conditions` と同じです（天候・バイオーム・ディメンション・ブロック・式・論理演算子）。
詳しくは [レシピの JSON フォーマット](../recipes/JSON_FORMAT.md) を参照。

### 機械の状態も条件にできる

式を使うと、機械自身の状態を見られます。

```json
"conditions": [
  { "expression": "energy > 1000" }
]
```

使える名前は [式のリファレンス](../recipes/EXPRESSION_REFERENCE.md) を参照。

> [!IMPORTANT]
> **条件は "AND" で結ばれます。** 1 つでも満たさなければ止まります。
> 「どれか 1 つでよい」なら `or` で明示的に囲んでください。
>
> ```json
> "conditions": [ { "or": [ { "weather": "RAIN" }, { "weather": "THUNDER" } ] } ]
> ```

## 2. 稼働中に条件が崩れたとき — `conditionPolicy`

レシピの実行中に条件が崩れたとき、どうするかを選べます。

| 値 | 挙動 |
|---|---|
| `pause`（既定） | 条件が戻れば続きから再開する。消費済みの材料は失われない |
| `abort` | レシピを破棄する。**消費済みの材料は戻らない** |

```json
{
  "conditions": [ { "weather": "RAIN" } ],
  "conditionPolicy": "abort"
}
```


## 3. Tips

エラーがある場合は `/okmodular reload` の際にログがでます。

### 条件文の言語はサーバ側で決まる

GUI に出る条件の文はサーバ側の言語で作られてからクライアントに送られます。
マルチプレイでは、クライアントの言語設定と一致しないことがあります。
