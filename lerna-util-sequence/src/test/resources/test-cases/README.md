# Test Cases

テストケースを洗い出すためのリソースです

## テストケースの見方

テストケースは [picts](./picts) ディレクトリ内の `*.pict` という拡張子のファイルに記述されています。

ファイル下部にある次の部分にテストケースが列挙されています。

```
##################### OUTPUT #####################
# 全組み合わせ網羅ケース:
# No  maxReservedValue    nextValue           after_nextValue     after_isStarving  after_freeAmount  @isOverflow  @isEmpty  @採番   @after_isOverflow  @after_isEmpty  @採番値予約  @採番値リセット
# 1   < maxSequenceValue  < maxReservedValue  < maxReservedValue  FALSE             = 0               FALSE        FALSE     する    FALSE              FALSE           しない       しない
...以降略...
```

テストケースの更新方法は以降のセクションを確認してください。

## テストケースの作成手順

テストケースを洗い出すのに `pict` というツールを利用します。

[microsoft/pict: Pairwise Independent Combinatorial Tool](https://github.com/microsoft/pict)

`pict` でテストケースを出力するためにはモデルファイルの作成が必要です。
既存のモデルファイルは  `*.pict` という拡張子で [picts](./picts) ディレクトリに格納されています。

モデルファイルの詳細な記法については次のページを参照してください：
https://github.com/Microsoft/pict/blob/main/doc/pict.md

モデルファイルを作成した後は `pict` をラップした [scripts/pict-coverall.sh](scripts/pict-coverall.sh) を使ってテストケースを出力できます。
このスクリプトを使うには Docker がインストールされている必要があります。

パラメータ名にプレフィックス `@` を付けると、このスクリプトではテストケースの結果（期待値）として特別扱いされます。
同じ条件で複数の結果が導かれるようになってしまっている場合は「条件重複ケース」として出力されます。
条件重複ケースがなくなるように結果の条件を調整してください。

モデルファイルを引数に指定してスクリプトを実行すると、生成されたテストケースがコンソールに出力されます。

```shell
cd test-cases
scripts/pict-coverall.sh picts/sequence_factory_worker_gen.pict
```

同時にモデルファイル内に、コンソールに出力された内容が追記されます。

```
##################### OUTPUT #####################
# 全組み合わせ網羅ケース:
# No  maxReservedValue    nextValue           after_nextValue     after_isStarving  after_freeAmount  @isOverflow  @isEmpty  @採番   @after_isOverflow  @after_isEmpty  @採番値予約  @採番値リセット
# 1   < maxSequenceValue  < maxReservedValue  < maxReservedValue  FALSE             = 0               FALSE        FALSE     する    FALSE              FALSE           しない       しない
...以降略...
```

モデルファイルの内容を変更した場合は、スクリプトを再実行することモデルファイル内に出力されたテストケースを更新できます。
