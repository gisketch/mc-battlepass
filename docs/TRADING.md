# Trading

Player trading is server-owned and temporary. No trade state is persisted.

## Flow

- Right-click another player to send a trade request.
- The requested player right-clicks the requester to accept.
- `/ck trade decline` or `/ck trade cancel` clears pending requests.
- Requests expire after 30 seconds.
- Moving too far away, logout, closing the trade screen, or pressing Cancel cancels the active trade and returns offered items.

## UI

The trade screen renders two vanilla single-chest offer panels side by side with paperdoll identity cards outside them.

- Left paperdoll/panel: your nickname-aware name tag, player preview, 27 offer slots, and inventory.
- Right paperdoll/panel: other player's nickname-aware name tag, player preview when visible, 27 offer slots, and a dimmed locked inventory preview.
- Center title shows `TRADING WITH <OTHER PLAYER>` from the viewer's point of view.
- Panel headers show ready state with friend add/remove icons instead of player names.
- Chowcoins render with the shared `chowcoin.png` icon and CKDM-formatted white amount text.
- Type in the inline chowcoin input to update the offered amount.
- Both players click the gray Ready button. Once both are ready, it becomes a green Confirm button. Cancel uses the red 9-slice button.
- Any item or chowcoin change resets readiness.

## Debug

Use `/ck trade debug` as an operator to open a solo sandbox trade with `Debug Trader`.

The debug partner auto-confirms, offers sample items and chowcoins, and your offered items are returned on completion so the UI can be tested alone.
Debug chowcoins settle like a real trade: if you offer 500 and Debug Trader offers 1,000, your wallet gains 500 net.
