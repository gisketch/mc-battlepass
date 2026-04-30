# Trading

Player trading is server-owned and temporary. No trade state is persisted.

## Flow

- Right-click another player to send a trade request.
- The requested player right-clicks the requester to accept.
- `/ck trade decline` or `/ck trade cancel` clears pending requests.
- Requests expire after 30 seconds.
- Moving too far away, logout, closing the trade screen, or pressing Cancel cancels the active trade and returns offered items.

## UI

The trade screen renders two chest-style panels side by side.

- Left panel: your 27 offer slots and your inventory.
- Right panel: other player offer slots and a dimmed locked preview of their inventory.
- Chowcoins can be offered with the bottom controls.
- Both players click Ready. Once both are ready, both click Confirm to commit.
- Any item or chowcoin change resets readiness.

## Debug

Use `/ck trade debug` as an operator to open a solo sandbox trade with `Debug Trader`.

The debug partner auto-confirms, offers sample items and chowcoins, and your offered items are returned on completion so the UI can be tested alone.
