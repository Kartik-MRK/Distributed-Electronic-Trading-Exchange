/* eslint-disable @typescript-eslint/no-require-imports -- Node's test runner uses CommonJS. */
const test = require('node:test');
const assert = require('node:assert/strict');

const {
  DEFAULT_PANEL_LAYOUT,
  DEFAULT_COLUMN_LAYOUT,
  getResizeDeltas,
  normalizeColumnLayout,
  normalizePanelLayout,
  readPanelLayout,
  readColumnLayout,
  resizePanelBoundaryLayout,
  resizeColumnBoundary,
  resizePanelLayout,
  savePanelLayout,
  saveColumnLayout,
} = require('../src/lib/workspace-layout.cjs');

test('provides balanced default spans for all six workspace panels', () => {
  assert.deepEqual(DEFAULT_PANEL_LAYOUT, {
    chart: { columns: 6, rows: 4 },
    book: { columns: 3, rows: 4 },
    simulation: { columns: 3, rows: 2 },
    orderEntry: { columns: 3, rows: 3 },
    activity: { columns: 9, rows: 2 },
    balances: { columns: 3, rows: 2 },
  });
});

test('round-trips valid panel spans through browser storage', () => {
  const values = new Map();
  const storage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  };
  const layout = normalizePanelLayout({
    ...DEFAULT_PANEL_LAYOUT,
    chart: { columns: 8, rows: 5 },
  });

  savePanelLayout(storage, layout);

  assert.deepEqual(readPanelLayout(storage), layout);
});

test('falls back safely when saved layout data is malformed or incomplete', () => {
  const storage = { getItem: () => '{bad json' };
  assert.deepEqual(readPanelLayout(storage), DEFAULT_PANEL_LAYOUT);
  assert.deepEqual(
    normalizePanelLayout({ chart: { columns: 'wide', rows: null } }),
    DEFAULT_PANEL_LAYOUT,
  );
  assert.deepEqual(
    normalizePanelLayout({ chart: { columns: 7, rows: 4 } }),
    { ...DEFAULT_PANEL_LAYOUT, chart: { columns: 7, rows: 4 } },
  );
});

test('clamps spans to each panel minimum and maximum', () => {
  const layout = normalizePanelLayout({
    ...DEFAULT_PANEL_LAYOUT,
    chart: { columns: 99, rows: 0 },
    balances: { columns: -4, rows: 99 },
  });

  assert.deepEqual(layout.chart, { columns: 12, rows: 3 });
  assert.deepEqual(layout.balances, { columns: 3, rows: 6 });
});

test('keeps the simulation controls tall enough for their content', () => {
  const layout = normalizePanelLayout({
    ...DEFAULT_PANEL_LAYOUT,
    simulation: { columns: 3, rows: 1 },
  });

  assert.equal(layout.simulation.rows, 2);
});

test('converts pointer movement into width, height, and corner grid deltas', () => {
  const drag = {
    startX: 10,
    startY: 20,
    currentX: 250,
    currentY: 244,
    columnStep: 120,
    rowStep: 112,
  };

  assert.deepEqual(getResizeDeltas('width', drag), { columns: 2, rows: 0 });
  assert.deepEqual(getResizeDeltas('height', drag), { columns: 0, rows: 2 });
  assert.deepEqual(getResizeDeltas('both', drag), { columns: 2, rows: 2 });
});

test('applies resize deltas without crossing per-panel span limits', () => {
  assert.deepEqual(
    resizePanelLayout(DEFAULT_PANEL_LAYOUT, 'chart', { columns: 20, rows: 20 }).chart,
    { columns: 12, rows: 8 },
  );
  assert.deepEqual(
    resizePanelLayout(DEFAULT_PANEL_LAYOUT, 'balances', { columns: -20, rows: -20 })
      .balances,
    { columns: 3, rows: 2 },
  );
});

test('moves width across a vertical boundary while preserving the pair width', () => {
  const next = resizePanelBoundaryLayout(
    DEFAULT_PANEL_LAYOUT,
    'chart',
    'book',
    1,
  );

  assert.equal(next.chart.columns, 7);
  assert.equal(next.book.columns, 2);
  assert.equal(next.chart.columns + next.book.columns, 9);

  const movedBack = resizePanelBoundaryLayout(next, 'chart', 'book', -1);
  assert.equal(movedBack.chart.columns, DEFAULT_PANEL_LAYOUT.chart.columns);
  assert.equal(movedBack.book.columns, DEFAULT_PANEL_LAYOUT.book.columns);
});

test('limits a boundary drag when either adjacent panel reaches its minimum', () => {
  const next = resizePanelBoundaryLayout(
    DEFAULT_PANEL_LAYOUT,
    'chart',
    'book',
    20,
  );

  assert.equal(next.chart.columns, 7);
  assert.equal(next.book.columns, 2);
  assert.equal(next.chart.columns + next.book.columns, 9);
});

test('preserves sub-track pointer movement for smooth width resizing', () => {
  assert.deepEqual(
    getResizeDeltas('both', {
      startX: 0,
      startY: 0,
      currentX: 1,
      currentY: 56,
      columnStep: 100,
      rowStep: 112,
    }),
    { columns: 0.01, rows: 1 },
  );
});

test('transfers fractional width between shared columns without changing total width', () => {
  const next = resizeColumnBoundary(
    DEFAULT_COLUMN_LAYOUT,
    'middle',
    'right',
    0.25,
  );

  assert.deepEqual(next, { left: 6, middle: 3.25, right: 2.75 });
  assert.equal(next.left + next.middle + next.right, 12);
});

test('clamps shared-column resizing at each column minimum and maximum', () => {
  const expandedMiddle = resizeColumnBoundary(
    DEFAULT_COLUMN_LAYOUT,
    'middle',
    'right',
    20,
  );
  assert.deepEqual(expandedMiddle, { left: 6, middle: 3.5, right: 2.5 });

  const expandedRight = resizeColumnBoundary(
    expandedMiddle,
    'middle',
    'right',
    -20,
  );
  assert.deepEqual(expandedRight, { left: 6, middle: 2, right: 4 });
});

test('normalizes saved column weights to a valid twelve-unit workspace', () => {
  const layout = normalizeColumnLayout({ left: 9, middle: 1, right: 2 });

  assert.equal(layout.left + layout.middle + layout.right, 12);
  assert.ok(layout.left >= 4 && layout.left <= 8);
  assert.ok(layout.middle >= 2 && layout.middle <= 6);
  assert.ok(layout.right >= 2 && layout.right <= 6);
});

test('round-trips fractional column widths through browser storage', () => {
  const values = new Map();
  const storage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  };
  const layout = { left: 6.125, middle: 3.375, right: 2.5 };

  saveColumnLayout(storage, layout);

  assert.deepEqual(readColumnLayout(storage), layout);
});

test('migrates saved panel widths to shared column widths', () => {
  const storage = { getItem: () => null };
  const legacy = {
    ...DEFAULT_PANEL_LAYOUT,
    chart: { columns: 5, rows: 4 },
    book: { columns: 3, rows: 4 },
    simulation: { columns: 4, rows: 1 },
  };

  assert.deepEqual(readColumnLayout(storage, legacy), {
    left: 5,
    middle: 3,
    right: 4,
  });
});
