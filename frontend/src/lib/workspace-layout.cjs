const STORAGE_KEY = 'dete-terminal-layout-v1';
const COLUMN_STORAGE_KEY = 'dete-terminal-columns-v1';

const DEFAULT_COLUMN_LAYOUT = Object.freeze({
  left: 6,
  middle: 3,
  right: 3,
});

const COLUMN_LIMITS = Object.freeze({
  left: Object.freeze({ min: 4, max: 7.5 }),
  middle: Object.freeze({ min: 2, max: 6 }),
  right: Object.freeze({ min: 2.5, max: 6 }),
});

const DEFAULT_PANEL_LAYOUT = Object.freeze({
  chart: Object.freeze({ columns: 6, rows: 4 }),
  book: Object.freeze({ columns: 3, rows: 4 }),
  simulation: Object.freeze({ columns: 3, rows: 2 }),
  orderEntry: Object.freeze({ columns: 3, rows: 3 }),
  activity: Object.freeze({ columns: 9, rows: 2 }),
  balances: Object.freeze({ columns: 3, rows: 2 }),
});

const PANEL_LIMITS = Object.freeze({
  chart: Object.freeze({ minColumns: 5, maxColumns: 12, minRows: 3, maxRows: 8 }),
  book: Object.freeze({ minColumns: 2, maxColumns: 6, minRows: 2, maxRows: 6 }),
  simulation: Object.freeze({ minColumns: 3, maxColumns: 6, minRows: 2, maxRows: 3 }),
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

function normalizeColumnLayout(value) {
  const source = isRecord(value) ? value : DEFAULT_COLUMN_LAYOUT;
  const raw = {};
  for (const id of Object.keys(DEFAULT_COLUMN_LAYOUT)) {
    const candidate = source[id];
    raw[id] =
      typeof candidate === 'number' && Number.isFinite(candidate) && candidate > 0
        ? candidate
        : DEFAULT_COLUMN_LAYOUT[id];
  }

  const normalized = {};
  let remaining = 12;
  let pending = Object.keys(DEFAULT_COLUMN_LAYOUT);
  while (pending.length) {
    const pendingTotal = pending.reduce((sum, id) => sum + raw[id], 0);
    let constrained = false;
    for (const id of pending) {
      const projected = (remaining * raw[id]) / pendingTotal;
      const limits = COLUMN_LIMITS[id];
      if (projected < limits.min) {
        normalized[id] = limits.min;
        remaining -= limits.min;
        pending = pending.filter((other) => other !== id);
        constrained = true;
        break;
      }
      if (projected > limits.max) {
        normalized[id] = limits.max;
        remaining -= limits.max;
        pending = pending.filter((other) => other !== id);
        constrained = true;
        break;
      }
    }

    if (!constrained) {
      for (const id of pending) {
        normalized[id] = (remaining * raw[id]) / pendingTotal;
      }
      break;
    }
  }

  normalized.left = Number(normalized.left.toFixed(3));
  normalized.middle = Number(normalized.middle.toFixed(3));
  normalized.right = Number((12 - normalized.left - normalized.middle).toFixed(3));
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

function readColumnLayout(storage, legacyPanelLayout) {
  try {
    const serialized = storage?.getItem(COLUMN_STORAGE_KEY);
    if (serialized) return normalizeColumnLayout(JSON.parse(serialized));
  } catch {
    return normalizeColumnLayout(DEFAULT_COLUMN_LAYOUT);
  }

  const legacy = normalizePanelLayout(legacyPanelLayout);
  return normalizeColumnLayout({
    left: legacy.chart.columns,
    middle: legacy.book.columns,
    right: legacy.simulation.columns,
  });
}

function saveColumnLayout(storage, layout) {
  try {
    storage?.setItem(COLUMN_STORAGE_KEY, JSON.stringify(normalizeColumnLayout(layout)));
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

function resizePanelBoundaryLayout(layout, leftId, rightId, deltaColumns) {
  const normalized = normalizePanelLayout(layout);
  if (
    leftId === rightId ||
    !Object.hasOwn(DEFAULT_PANEL_LAYOUT, leftId) ||
    !Object.hasOwn(DEFAULT_PANEL_LAYOUT, rightId) ||
    !Number.isFinite(deltaColumns)
  ) {
    return normalized;
  }

  const left = normalized[leftId];
  const right = normalized[rightId];
  const leftLimits = PANEL_LIMITS[leftId];
  const rightLimits = PANEL_LIMITS[rightId];
  const requested = Math.round(deltaColumns);
  const maxIncrease = Math.min(
    leftLimits.maxColumns - left.columns,
    right.columns - rightLimits.minColumns,
  );
  const maxDecrease = Math.min(
    left.columns - leftLimits.minColumns,
    rightLimits.maxColumns - right.columns,
  );
  const applied =
    requested >= 0
      ? Math.min(requested, maxIncrease)
      : -Math.min(-requested, maxDecrease);

  return {
    ...normalized,
    [leftId]: { ...left, columns: left.columns + applied },
    [rightId]: { ...right, columns: right.columns - applied },
  };
}

function getResizeDeltas(axis, drag) {
  const columnSteps = Number(
    ((drag.currentX - drag.startX) / drag.columnStep).toFixed(3),
  );
  const rowSteps = Math.round((drag.currentY - drag.startY) / drag.rowStep);

  return {
    columns: axis === 'width' || axis === 'both' ? columnSteps : 0,
    rows: axis === 'height' || axis === 'both' ? rowSteps : 0,
  };
}

function resizeColumnBoundary(layout, leftId, rightId, delta) {
  const normalized = normalizeColumnLayout(layout);
  if (
    leftId === rightId ||
    !Object.hasOwn(DEFAULT_COLUMN_LAYOUT, leftId) ||
    !Object.hasOwn(DEFAULT_COLUMN_LAYOUT, rightId) ||
    !Number.isFinite(delta)
  ) {
    return normalized;
  }

  const leftLimits = COLUMN_LIMITS[leftId];
  const rightLimits = COLUMN_LIMITS[rightId];
  const maxIncrease = Math.min(
    leftLimits.max - normalized[leftId],
    normalized[rightId] - rightLimits.min,
  );
  const maxDecrease = Math.min(
    normalized[leftId] - leftLimits.min,
    rightLimits.max - normalized[rightId],
  );
  const applied =
    delta >= 0
      ? Math.min(delta, maxIncrease)
      : -Math.min(-delta, maxDecrease);

  return normalizeColumnLayout({
    ...normalized,
    [leftId]: normalized[leftId] + applied,
    [rightId]: normalized[rightId] - applied,
  });
}

module.exports = {
  COLUMN_LIMITS,
  DEFAULT_COLUMN_LAYOUT,
  DEFAULT_PANEL_LAYOUT,
  PANEL_LIMITS,
  getResizeDeltas,
  normalizeColumnLayout,
  normalizePanelLayout,
  readColumnLayout,
  readPanelLayout,
  resizeColumnBoundary,
  resizePanelBoundaryLayout,
  resizePanelLayout,
  saveColumnLayout,
  savePanelLayout,
};
