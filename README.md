# FeliCaEmulatorPoC

Android で FeliCa カードのエミュレートを行う実証コードです

## 概要

Android で FeliCa エミュレーションを行う PoC です。

NFC 対応 Android 端末を FeliCa カードとして動作させることができる場合があります。

FeliCa のエミュレーションは NFC Type F のリーダ機能を応用してハック的に実装されています。

## 使い方

Test3 以下をビルドします。

1. アプリを起動します
2. 任意の FeliCa カード（Lite-S を推奨）を Android 端末にかざします
3. その後端末をリーダにかざすと、Android 端末が FeliCa カードとして認識されることがあります

## 動作原理

TODO

## 動作確認環境

RC-S380 リーダと[nfcpy](https://github.com/nfcpy/nfcpy)の `examples/sense.py` で動作確認を行っています。

`python .\sense.py 212F -r`

- Google Pixel 8a (Android 16)
- OPPO Reno5 A (Android 11)

## 非動作確認環境

- Nothing Phone (2a) (Android 15)

動作する端末と動作しない端末がある理由は不明です。
NFC チップやその実装の違いが影響している可能性があります。

## 注意事項

- すべての Android 端末で動作するわけではありません
- すべての FeliCa リーダで動作するわけでもありません
- 実験的なコードであり、動作を保証するものではありません
- 使用に伴ういかなる損害についても責任を負いかねます
