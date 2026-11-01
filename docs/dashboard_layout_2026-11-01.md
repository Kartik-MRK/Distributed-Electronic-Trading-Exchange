# Trading dashboard layout repair — 2026-11-01

The desktop workspace previously placed every card on shared 100px grid rows. The tallest top stack determined the Open Orders start row, leaving a gap below the chart and extending the layout beyond shorter windows. The simulation panel clipped its contents instead of giving them a scrollable area.

## Changes

- The chart and Open Orders now form an independent flex column. The chart retains a preferred height, constrained by the available viewport; Open Orders fills the remainder with a constant 12px gap.
- The book and trading controls occupy a separate, full-height right group. Its subgrid preserves the existing three shared horizontal column widths. The book, balances, and Open Orders bottom edges align.
- Internal horizontal dividers transfer height between adjacent panels, starting from their rendered dimensions. Fractional heights survive storage, so pointer motion no longer snaps to whole rows. Fixed outside edges no longer expose ineffective height grips; keyboard arrows move internal dividers by 8px.
- Simulation content and activity tables scroll within their assigned space. The simulation button has an explicit line height, balanced padding, and a non-collapsing minimum height. The shared-traders eyebrow has been removed.
- Tablet and mobile keep their responsive arrangement, fixed readable chart heights, and hidden resize grips. The existing 600px desktop minimum remains; windows below that height may scroll the page.

## Verification

- 27 frontend unit/contract tests passed, including a one-pixel vertical resize and persistence regression that failed before the fix.
- TypeScript, ESLint, production build, and `git diff --check` passed.
- Five Chromium tests run against the actual production page with intercepted exchange traffic. They cover desktop heights from 600–1050px, aligned bottoms, constant gaps, internal scrolling, button geometry, width/height/corner/keyboard resizing, persistence, extreme saved layouts, alternate activity tabs, and 390px mobile / 1000px tablet layouts.
- Inspected captured desktop screenshots at 600px and 900px heights. The browser tests are included in CI. Firefox and Safari were not exercised.

To repeat browser verification from `frontend`:

```sh
npm ci
npx playwright install chromium
npm run build
npm run test:layout
```

These changes affect frontend layout and its tests. They do not change market-clock, trading, account, or Kafka data behavior.
