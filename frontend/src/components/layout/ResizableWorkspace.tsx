'use client';

import {
  CSSProperties,
  KeyboardEvent as ReactKeyboardEvent,
  PointerEvent as ReactPointerEvent,
  ReactNode,
  useEffect,
  useRef,
  useState,
} from 'react';
import {
  DEFAULT_COLUMN_LAYOUT,
  DEFAULT_PANEL_LAYOUT,
  MIN_PANEL_HEIGHTS,
  getResizeDeltas,
  normalizeColumnLayout,
  normalizePanelLayout,
  readColumnLayout,
  readPanelLayout,
  resizeColumnBoundary,
  saveColumnLayout,
  savePanelLayout,
  type ColumnId,
  type ColumnLayout,
  type PanelId,
  type PanelLayout,
  type ResizeAxis,
} from '@/lib/workspace-layout.cjs';

export interface WorkspacePanel {
  id: PanelId;
  label: string;
  className: string;
  content: ReactNode;
}

interface ColumnBoundary {
  leftId: ColumnId;
  rightId: ColumnId;
}

interface DragState {
  id: PanelId;
  axis: ResizeAxis;
  boundary: ColumnBoundary | null;
  pointerId: number;
  startX: number;
  startY: number;
  columnStep: number;
  rowStep: number;
  initialLayout: PanelLayout;
  initialColumns: ColumnLayout;
  heights: Record<PanelId, number>;
}

const DESKTOP_BREAKPOINT = 1181;
const ROW_UNIT = 100;
const ROW_GAP = 12;
// The outer bottom edges are fixed. Height handles move only internal dividers.
const HEIGHT_NEIGHBORS: Partial<Record<PanelId, PanelId>> = {
  chart: 'activity', simulation: 'orderEntry', orderEntry: 'balances',
};
const MIN_HEIGHTS = MIN_PANEL_HEIGHTS;
const PANEL_BOUNDARIES: Partial<Record<PanelId, ColumnBoundary>> = {
  chart: { leftId: 'left', rightId: 'middle' },
  book: { leftId: 'middle', rightId: 'right' },
  activity: { leftId: 'left', rightId: 'middle' },
};

export function ResizableWorkspace({
  panels,
}: {
  panels: WorkspacePanel[];
}) {
  const gridRef = useRef<HTMLElement>(null);
  const dragRef = useRef<DragState | null>(null);
  const initialLayout = normalizePanelLayout(DEFAULT_PANEL_LAYOUT);
  const initialColumns = normalizeColumnLayout(DEFAULT_COLUMN_LAYOUT);
  const layoutRef = useRef<PanelLayout>(initialLayout);
  const columnsRef = useRef<ColumnLayout>(initialColumns);
  const [layout, setLayout] = useState<PanelLayout>(initialLayout);
  const [columns, setColumns] = useState<ColumnLayout>(initialColumns);

  useEffect(() => {
    const storage = getLocalStorage();
    const savedLayout = readPanelLayout(storage) as PanelLayout;
    const savedColumns = readColumnLayout(storage, savedLayout) as ColumnLayout;
    layoutRef.current = savedLayout;
    columnsRef.current = savedColumns;
    setLayout(savedLayout);
    setColumns(savedColumns);
  }, []);

  function beginResize(
    event: ReactPointerEvent<HTMLButtonElement>,
    id: PanelId,
    axis: ResizeAxis,
  ) {
    if (event.button !== 0) return;
    const grid = gridRef.current;
    if (!grid || window.innerWidth < DESKTOP_BREAKPOINT) return;

    const styles = getComputedStyle(grid);
    const gap = Number.parseFloat(styles.columnGap) || 0;
    const padding =
      (Number.parseFloat(styles.paddingLeft) || 0) +
      (Number.parseFloat(styles.paddingRight) || 0);
    const availableWidth = grid.clientWidth - padding - gap * 2;
    const columnStep = availableWidth / 12;
    const rowGap = Number.parseFloat(styles.rowGap) || 0;

    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = {
      id,
      axis,
      boundary: axis === 'height' ? null : PANEL_BOUNDARIES[id] ?? null,
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      columnStep,
      rowStep: ROW_UNIT + rowGap,
      initialLayout: layoutRef.current,
      initialColumns: columnsRef.current,
      heights: measureHeights(grid),
    };
  }

  function moveResize(event: ReactPointerEvent<HTMLButtonElement>) {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;

    const deltas = getResizeDeltas(drag.axis, {
      startX: drag.startX,
      startY: drag.startY,
      currentX: event.clientX,
      currentY: event.clientY,
      columnStep: drag.columnStep,
      rowStep: drag.rowStep,
    });
    const next = applyResize(
      drag.initialLayout,
      drag.initialColumns,
      drag.id,
      drag.boundary,
      deltas,
      drag.heights,
    );
    layoutRef.current = next.layout;
    columnsRef.current = next.columns;
    applyGridLayout(gridRef.current, next.layout, next.columns);
  }

  function finishResize(event: ReactPointerEvent<HTMLButtonElement>) {
    if (dragRef.current?.pointerId !== event.pointerId) return;
    moveResize(event);
    dragRef.current = null;
    setLayout(layoutRef.current);
    setColumns(columnsRef.current);
    const storage = getLocalStorage();
    savePanelLayout(storage, layoutRef.current);
    saveColumnLayout(storage, columnsRef.current);
  }

  function cancelResize(event: ReactPointerEvent<HTMLButtonElement>) {
    if (dragRef.current?.pointerId !== event.pointerId) return;
    dragRef.current = null;
    setLayout(layoutRef.current);
    setColumns(columnsRef.current);
    const storage = getLocalStorage();
    savePanelLayout(storage, layoutRef.current);
    saveColumnLayout(storage, columnsRef.current);
  }

  function resizeFromKeyboard(
    event: ReactKeyboardEvent<HTMLButtonElement>,
    id: PanelId,
    axis: ResizeAxis,
  ) {
    const deltas = { columns: 0, rows: 0 };
    if (axis !== 'height' && event.key === 'ArrowRight') deltas.columns = 0.25;
    if (axis !== 'height' && event.key === 'ArrowLeft') deltas.columns = -0.25;
    if (axis !== 'width' && event.key === 'ArrowDown') deltas.rows = 8 / (ROW_UNIT + ROW_GAP);
    if (axis !== 'width' && event.key === 'ArrowUp') deltas.rows = -8 / (ROW_UNIT + ROW_GAP);
    if (deltas.columns === 0 && deltas.rows === 0) return;

    event.preventDefault();
    const next = applyResize(
      layoutRef.current,
      columnsRef.current,
      id,
      axis === 'height' ? null : PANEL_BOUNDARIES[id] ?? null,
      deltas,
      measureHeights(gridRef.current),
    );
    layoutRef.current = next.layout;
    columnsRef.current = next.columns;
    setLayout(next.layout);
    setColumns(next.columns);
    const storage = getLocalStorage();
    savePanelLayout(storage, next.layout);
    saveColumnLayout(storage, next.columns);
  }

  function renderPanel(id: PanelId) {
    const panel = panels.find((candidate) => candidate.id === id);
    if (!panel) return null;
    const boundary = PANEL_BOUNDARIES[id];
    const heightResizable = Boolean(HEIGHT_NEIGHBORS[id]);
    const edges: ResizeAxis[] = [
      ...(boundary ? ['width' as const] : []),
      ...(heightResizable ? ['height' as const] : []),
      ...(boundary && heightResizable ? ['both' as const] : []),
    ];
    return (
      <section key={id} data-panel-id={id}
        className={`workspace-panel ${panel.className}`} aria-label={panel.label}
        style={{ '--panel-height': `${panelHeight(layout, id)}px`, '--panel-min-height': `${MIN_HEIGHTS[id]}px` } as CSSProperties}>
        {panel.content}
        <div className="workspace-resize-controls">
          {edges.map((edge) => <ResizeGrip key={edge} id={id} label={panel.label} edge={edge}
            onPointerDown={beginResize} onPointerMove={moveResize} onPointerUp={finishResize}
            onPointerCancel={cancelResize} onKeyDown={resizeFromKeyboard} />)}
        </div>
      </section>
    );
  }
  return (
    <main
      id="workspace"
      ref={gridRef}
      className="terminal-body terminal-body--resizable"
      aria-label="Trading workspace"
      style={
        {
          '--workspace-left': `${columns.left}fr`,
          '--workspace-middle': `${columns.middle}fr`,
          '--workspace-right': `${columns.right}fr`,
        } as CSSProperties
      }
    >
      <div className="workspace-left-stack">
        {renderPanel('chart')}
        {renderPanel('activity')}
      </div>
      <div className="workspace-right-group">
        {renderPanel('book')}
        <div className="workspace-controls-stack">
          {renderPanel('simulation')}
          {renderPanel('orderEntry')}
          {renderPanel('balances')}
        </div>
      </div>
    </main>
  );
}

function getLocalStorage(): Storage | undefined {
  try {
    return window.localStorage;
  } catch {
    return undefined;
  }
}

function applyResize(
  layout: PanelLayout,
  columns: ColumnLayout,
  id: PanelId,
  boundary: ColumnBoundary | null,
  deltas: { columns: number; rows: number },
  heights: Record<PanelId, number>,
): { layout: PanelLayout; columns: ColumnLayout } {
  let nextLayout = layout;
  const neighbor = HEIGHT_NEIGHBORS[id];
  if (neighbor && deltas.rows !== 0) {
    const pixels = Math.max(MIN_HEIGHTS[id] - heights[id],
      Math.min(deltas.rows * (ROW_UNIT + ROW_GAP), heights[neighbor] - MIN_HEIGHTS[neighbor]));
    // Start from rendered sizes, including any flex shrink after a window resize.
    // Holding the pair total constant makes the divider follow the pointer exactly.
    nextLayout = { ...layout };
    const stack: PanelId[] = id === 'chart' ? ['chart', 'activity'] : ['simulation', 'orderEntry', 'balances'];
    for (const panelId of stack) {
      const height = heights[panelId] + (panelId === id ? pixels : panelId === neighbor ? -pixels : 0);
      nextLayout[panelId] = { ...layout[panelId], rows: (height + ROW_GAP) / (ROW_UNIT + ROW_GAP) };
    }
  }
  const nextColumns =
    boundary && deltas.columns !== 0
      ? (resizeColumnBoundary(
          columns,
          boundary.leftId,
          boundary.rightId,
          deltas.columns,
        ) as ColumnLayout)
      : columns;

  return { layout: nextLayout, columns: nextColumns };
}

function panelHeight(layout: PanelLayout, id: PanelId): number {
  return layout[id].rows * (ROW_UNIT + ROW_GAP) - ROW_GAP;
}

function measureHeights(grid: HTMLElement | null): Record<PanelId, number> {
  const heights = { ...MIN_HEIGHTS };
  grid?.querySelectorAll<HTMLElement>('[data-panel-id]').forEach((panel) => {
    heights[panel.dataset.panelId as PanelId] = panel.getBoundingClientRect().height;
  });
  return heights;
}

function applyGridLayout(
  grid: HTMLElement | null,
  layout: PanelLayout,
  columns: ColumnLayout,
) {
  if (!grid) return;
  grid.style.setProperty('--workspace-left', `${columns.left}fr`);
  grid.style.setProperty('--workspace-middle', `${columns.middle}fr`);
  grid.style.setProperty('--workspace-right', `${columns.right}fr`);
  for (const panel of Array.from(
    grid.querySelectorAll<HTMLElement>('[data-panel-id]'),
  )) {
    const id = panel.dataset.panelId as PanelId | undefined;
    if (!id) continue;
    panel.style.setProperty('--panel-height', `${panelHeight(layout, id)}px`);
  }
}

function ResizeGrip({
  id,
  label,
  edge,
  onPointerDown,
  onPointerMove,
  onPointerUp,
  onPointerCancel,
  onKeyDown,
}: {
  id: PanelId;
  label: string;
  edge: ResizeAxis;
  onPointerDown: (
    event: ReactPointerEvent<HTMLButtonElement>,
    id: PanelId,
    axis: ResizeAxis,
  ) => void;
  onPointerMove: (event: ReactPointerEvent<HTMLButtonElement>) => void;
  onPointerUp: (event: ReactPointerEvent<HTMLButtonElement>) => void;
  onPointerCancel: (event: ReactPointerEvent<HTMLButtonElement>) => void;
  onKeyDown: (
    event: ReactKeyboardEvent<HTMLButtonElement>,
    id: PanelId,
    axis: ResizeAxis,
  ) => void;
}) {
  const direction =
    edge === 'width' ? 'width' : edge === 'height' ? 'height' : 'width and height';
  const ariaLabel = `Resize ${label} ${direction}`;

  return (
    <button
      type="button"
      className={`workspace-resize-grip workspace-resize-grip--${edge}`}
      aria-label={ariaLabel}
      title={ariaLabel}
      onPointerDown={(event) => onPointerDown(event, id, edge)}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerCancel}
      onKeyDown={(event) => onKeyDown(event, id, edge)}
    >
      <span aria-hidden="true" />
    </button>
  );
}
