# Resizable Trading Workspace Panels Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Allow traders to resize six workspace cards independently, persist each browser's layout, and reflow other cards around the change.

**Architecture:** A small layout utility owns default spans, input validation, clamping, and storage serialization. A client-side workspace component owns pointer and keyboard resizing and renders six semantic panel sections in a 12-column dense CSS grid. CSS keeps tablet and mobile behavior responsive without desktop resize grips.

**Tech Stack:** Next.js 16, React 19, TypeScript, CSS Grid, browser `localStorage`, Node's built-in test runner.

**Spec:** `docs/superpowers/specs/2026-10-25-resizable-terminal-panels-design.md`

## Global Constraints

- Desktop resize controls are enabled at widths of at least 1181px.
- The desktop grid uses 12 columns, 100px row units, and 12px gaps.
- The layout is stored per browser under a versioned key; no account or backend changes.
- Tablet and mobile retain natural responsive sizing and the mobile single-column stack.
- Do not add a UI dependency for resizing.

## Review Focus

- Invalid, stale, or out-of-range local storage values restore valid defaults — Task 1 tests parsing and clamping.
- Fast pointer movement cannot leave dimensions beyond card bounds — Task 1 tests min/max clamping.
- Resizing width, height, or both uses the correct drag axis — Task 2 exercises pointer delta conversion.
- Reflow does not create implicit columns wider than 12 — Task 2 uses maximum spans and an explicit 12-column grid.
- Resize handles do not alter tablet/mobile layout — Task 3 inspects media-query behavior and manually checks responsive widths.

---

### Task 1: Layout state and safe persistence

**Files:**
- Create: `frontend/src/lib/workspace-layout.cjs`
- Create: `frontend/tests/workspace-layout.test.cjs`
- Modify: `frontend/package.json`

**Interfaces:**
- Produces `DEFAULT_PANEL_LAYOUT`, `PANEL_LIMITS`, `normalizePanelLayout(value)`, `getStoredPanelLayout(storage)`, and `savePanelLayout(storage, layout)` for the workspace UI.
- Each panel size is `{ columns: number, rows: number }`; values are integers clamped to that panel's configured bounds.

- [ ] Write tests for default spans, valid serialization round-trip, malformed storage fallback, lower/upper clamping, and resize delta conversion.
- [ ] Run `node --test tests/workspace-layout.test.cjs` from `frontend`; confirm the new behavior fails before implementation.
- [ ] Implement the pure layout utility with browser storage passed in as an argument.
- [ ] Add the new file to the frontend `test` script; run `node --test tests/workspace-layout.test.cjs` and confirm all new assertions pass.

### Task 2: Workspace rendering and resize interaction

**Files:**
- Create: `frontend/src/components/layout/ResizableWorkspace.tsx`
- Modify: `frontend/src/app/(terminal)/terminal/page.tsx`
- Modify: `frontend/src/app/globals.css`
- Test: `frontend/tests/workspace-layout.test.cjs`

**Interfaces:**
- `ResizableWorkspace` accepts six `{ id, label, className, content }` panel records and exposes pointer edge/corner grips plus keyboard resizing.
- Width changes use measured grid-column units; height changes use the 100px grid-row unit plus the 12px grid gap.
- Pointer changes are saved after a drag ends; keyboard changes are saved after each key action.

- [ ] Add a test for applying width, height, and corner deltas and assert that dimensions remain clamped.
- [ ] Run the targeted workspace test and confirm it fails before implementing resize calculations.
- [ ] Render chart, book, simulation, order entry, activity, and balances as six `ResizableWorkspace` panels in their approved DOM order.
- [ ] Implement pointer capture, `pointermove`/`pointerup` handling, keyboard arrow increments, and persisted layout restore/save.
- [ ] Run the targeted workspace test, then inspect the resulting TypeScript and CSS changes.

### Task 3: Responsive layout and interaction finish

**Files:**
- Modify: `frontend/src/app/globals.css`
- Modify: `frontend/src/components/layout/ResizableWorkspace.tsx`
- Modify: `frontend/tests/workspace-layout.test.cjs`

- [ ] Add focused assertions for malformed panel IDs and missing dimensions being discarded safely.
- [ ] Run the targeted test and confirm it fails before tightening normalization.
- [ ] Implement the 12-column desktop grid, visible-on-hover/focus grips, focus-visible styling, and existing tablet/mobile stack behavior.
- [ ] Run the targeted workspace test and inspect the final diff; confirm the responsive CSS disables desktop spans and grips below 1181px.
- [ ] Commit the completed feature with a concise message.
