# Resizable Trading Workspace Panels

## Intent

Let each major trading workspace card be resized with the mouse so traders can give more room to the panels they use most. Changes are saved in the current browser and the existing responsive layout remains usable on smaller screens.

## Approved behavior

- On desktop, each of the six cards has a right-edge width grip, a bottom-edge height grip, and a bottom-right grip for resizing both dimensions.
- Dragging a grip changes that card's grid span. The dense CSS grid places the other cards into the available space while preserving their document order.
- Each card has a minimum and maximum span so controls remain usable and no card exceeds the workspace width.
- Card spans persist in browser `localStorage` and invalid saved data falls back to the defaults.
- Tablet and mobile layouts remain responsive and do not show resize grips.
- Resize grips are keyboard-focusable buttons. Arrow keys adjust the relevant dimension by one grid unit.

## Dashboard cards

The workspace contains six independently sized cards: price chart, order book/recent trades, simulation controls, order entry, account activity, and balances.

## Design calls

- Use a 12-column desktop CSS grid with 100px implicit row units and 12px gaps. `grid-auto-flow: dense` lets cards reflow after a span changes.
- Default spans preserve the current broad composition: chart 6×4, order book 3×4, simulation 3×1, order entry 3×3, activity 9×2, balances 3×2.
- Store only the span values under a versioned key; browser-specific storage keeps layouts separate between devices without an account setting or server change.
- Below 1181px, ignore stored spans and retain responsive card sizing. On mobile, preserve a single-column stack.
- Show grips on hover and keyboard focus with small neutral handles that do not cover chart or trading controls.

## Out of scope

- Moving cards to a user-selected order.
- Syncing layout settings between browsers or devices.
- Resizing panels on touch-sized layouts.
- Changing panel contents, trading behavior, or market data.

## Acceptance

- Each of the six cards can be resized in width, height, and both dimensions on desktop.
- Resizing one card causes the remaining cards to reflow without overlap or horizontal overflow.
- The chosen layout survives reload; malformed or out-of-range saved values do not break the page.
- Keyboard users can adjust dimensions with focused grips.
- Tablet and mobile layouts keep their current responsive behavior without resize controls.
