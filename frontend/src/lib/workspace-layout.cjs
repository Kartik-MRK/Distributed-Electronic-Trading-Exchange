const STORAGE_KEY = 'dete-terminal-layout-v1';

const DEFAULT_PANEL_LAYOUT = Object.freeze({
  chart: Object.freeze({ columns: 6, rows: 4 }),
  book: Object.freeze({ columns: 3, rows: 4 }),
  simulation: Object.freeze({ columns: 3, rows: 1 }),
  orderEntry: Object.freeze({ columns: 3, rows: 3 }),
  activity: Object.freeze({ columns: 9, rows: 2 }),
  balances: Object.freeze({ columns: 3, rows: 2 }),
});

const PANEL_LIMITS = Object.freeze({
  chart: Object.freeze({ minColumns: 5, maxColumns: 12, minRows: 3, maxRows: 8 }),
  book: Object.freeze({ minColumns: 2, maxColumns: 6, minRows: 2, maxRows: 6 }),
  simulation: Object.freeze({ minColumns: 3, maxColumns: 6, minRows: 1, maxRows: 3 }),
  orderEntry: Object.freeze({ minColumns: 3, maxColumns: 6, minRows: 2, maxRows: 6 }),
  activity: Object.freeze({ minColumns: 6, maxColumns: 12, minRows: 2, maxRows: 6 }),
  balances: Object.freeze({ minColumns: 3, maxColumns: 6, minRows: 2, maxRows: 6 }),
});

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function clamp(value, minimum, maximum) {
  return Math.max(minimum, Math.min(maximum, Math.round(value)));
}

function normalizeDimension(value, fallback, minimum, maximum) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return fallback;
  return clamp(value, minimum, maximum);
}

function normalizePanelLayout(value) {
  const source = isRecord(value) ? value : {};
  const normalized = {};

  for (const [id, defaults] of Object.entries(DEFAULT_PANEL_LAYOUT)) {
    const limits = PANEL_LIMITS[id];
    const candidate = isRecord(source[id]) ? source[id] : {};
    normalized[id] = {
      columns: normalizeDimension(
        candidate.columns,
        defaults.columns,
        limits.minColumns,
        limits.maxColumns,
      ),
      rows: normalizeDimension(
        candidate.rows,
        defaults.rows,
        limits.minRows,
        limits.maxRows,
      ),
    };
  }

  return normalized;
}

function readPanelLayout(storage) {
  try {
    const serialized = storage?.getItem(STORAGE_KEY);
    if (!serialized) return normalizePanelLayout(DEFAULT_PANEL_LAYOUT);
    return normalizePanelLayout(JSON.parse(serialized));
  } catch {
    return normalizePanelLayout(DEFAULT_PANEL_LAYOUT);
  }
}

function savePanelLayout(storage, layout) {
  try {
    storage?.setItem(STORAGE_KEY, JSON.stringify(normalizePanelLayout(layout)));
  } catch {
    // A full or disabled browser storage area should not interrupt trading.
  }
}

function resizePanelLayout(layout, id, delta) {
  const normalized = normalizePanelLayout(layout);
  if (!Object.hasOwn(DEFAULT_PANEL_LAYOUT, id)) return normalized;

  const panel = normalized[id];
  return normalizePanelLayout({
    ...normalized,
    [id]: {
      columns: panel.columns + (Number.isFinite(delta?.columns) ? delta.columns : 0),
      rows: panel.rows + (Number.isFinite(delta?.rows) ? delta.rows : 0),
    },
  });
}

function getResizeDeltas(axis, drag) {
  const columnSteps = Math.round((drag.currentX - drag.startX) / drag.columnStep);
  const rowSteps = Math.round((drag.currentY - drag.startY) / drag.rowStep);

  return {
    columns: axis === 'width' || axis === 'both' ? columnSteps : 0,
    rows: axis === 'height' || axis === 'both' ? rowSteps : 0,
  };
}

module.exports = {
  DEFAULT_PANEL_LAYOUT,
  PANEL_LIMITS,
  getResizeDeltas,
  normalizePanelLayout,
  readPanelLayout,
  resizePanelLayout,
  savePanelLayout,
};
