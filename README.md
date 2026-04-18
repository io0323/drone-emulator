# drone-emulator

Android 用の簡易ドローンエミュレータです。MAVLink v1 を UDP Socket で直接実装し、GCS に対して基本テレメトリを送信しつつ、受信した `COMMAND_LONG` / `MISSION_ITEM` / `RC_CHANNELS_OVERRIDE` を解析して UI に表示します。

## 実装内容

- MAVLink v1 パケット生成 (`HEARTBEAT`, `GLOBAL_POSITION_INT`, `BATTERY_STATUS`, `ATTITUDE`)
- CRC-16/MCRF4XX + `CRC_EXTRA`
- UDP 送受信
- 接続状態に応じた `ConnectionScreen` ↔ `MainScreen` 自動切替
- 高度・バッテリーのスライダー調整
- 受信コマンド表示
- ユニットテスト 9 本

## デフォルト接続設定

- Local Port: `14560`
- Remote Host: `192.168.3.11`
- Remote Port: `14550`

## テスト

```zsh
./gradlew test
```

## ビルド

```zsh
./gradlew assembleDebug
```

