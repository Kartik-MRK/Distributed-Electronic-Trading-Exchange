/* eslint-disable @typescript-eslint/no-require-imports -- Node's test runner uses CommonJS. */
const test = require('node:test');
const assert = require('node:assert/strict');

const {
  DEFAULT_PANEL_LAYOUT,
  getResizeDeltas,
  normalizePanelLayout,
  readPanelLayout,
  resizePanelBoundaryLayout,
  resizePanelLayout,
  savePanelLayout,
} = require('../src/lib/workspace-layout.cjs');

test('provides balanced default spans for all six workspace panels', () => {
  assert.deepEqual(DEFAULT_PANEL_LAYOUT, {
    chart: { columns: 6, rows: 4 },
    book: { columns: 3, rows: 4 },
    simulation: { columns: 3, rows: 1 },
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
