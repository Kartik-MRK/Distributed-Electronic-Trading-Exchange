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
  DEFAULT_PANEL_LAYOUT,
  getResizeDeltas,
  normalizePanelLayout,
  type PanelId,
  type PanelLayout,
  type ResizeAxis,
  readPanelLayout,
  resizePanelLayout,
  savePanelLayout,
} from '@/lib/workspace-layout.cjs';

export interface WorkspacePanel {
  id: PanelId;
  label: string;
  className: string;
  content: ReactNode;
}

interface DragState {
  id: PanelId;
  axis: ResizeAxis;
  pointerId: number;
  startX: number;
  startY: number;
  columnStep: number;
  rowStep: number;
  initialLayout: PanelLayout;
}

const GRID_COLUMNS = 12;
const DESKTOP_BREAKPOINT = 1181;
const ROW_UNIT = 100;

export function ResizableWorkspace({
  panels,
}: {
  panels: WorkspacePanel[];
}) {
  const gridRef = useRef<HTMLElement>(null);
  const dragRef = useRef<DragState | null>(null);
  const initialLayout = normalizePanelLayout(DEFAULT_PANEL_LAYOUT);
  const layoutRef = useRef<PanelLayout>(initialLayout);
  const [layout, setLayout] = useState<PanelLayout>(initialLayout);

  useEffect(() => {
    const saved = readPanelLayout(getLocalStorage()) as PanelLayout;
    layoutRef.current = saved;
    setLayout(saved);
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
    const availableWidth = grid.clientWidth - padding;
    const columnStep = (availableWidth - gap * (GRID_COLUMNS - 1)) / GRID_COLUMNS + gap;
    const rowGap = Number.parseFloat(styles.rowGap) || 0;

    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = {
      id,
      axis,
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      columnStep,
      rowStep: ROW_UNIT + rowGap,
      initialLayout: layoutRef.current,
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
    const next = resizePanelLayout(drag.initialLayout, drag.id, deltas) as PanelLayout;
    layoutRef.current = next;
    setLayout(next);
  }

  function finishResize(event: ReactPointerEvent<HTMLButtonElement>) {
    if (dragRef.current?.pointerId !== event.pointerId) return;
    moveResize(event);
    dragRef.current = null;
    savePanelLayout(getLocalStorage(), layoutRef.current);
  }

  function cancelResize(event: ReactPointerEvent<HTMLButtonElement>) {
    if (dragRef.current?.pointerId !== event.pointerId) return;
    dragRef.current = null;
    savePanelLayout(getLocalStorage(), layoutRef.current);
  }

  function resizeFromKeyboard(
    event: ReactKeyboardEvent<HTMLButtonElement>,
    id: PanelId,
    axis: ResizeAxis,
  ) {
    const deltas = { columns: 0, rows: 0 };
    if (axis !== 'height' && event.key === 'ArrowRight') deltas.columns = 1;
    if (axis !== 'height' && event.key === 'ArrowLeft') deltas.columns = -1;
    if (axis !== 'width' && event.key === 'ArrowDown') deltas.rows = 1;
    if (axis !== 'width' && event.key === 'ArrowUp') deltas.rows = -1;
    if (deltas.columns === 0 && deltas.rows === 0) return;

    event.preventDefault();
    const next = resizePanelLayout(layoutRef.current, id, deltas) as PanelLayout;
    layoutRef.current = next;
    setLayout(next);
    savePanelLayout(getLocalStorage(), next);
  }

  return (
    <main
      id="workspace"
      ref={gridRef}
      className="terminal-body terminal-body--resizable"
      aria-label="Trading workspace"
    >
      {panels.map((panel) => (
        <section
          key={panel.id}
          className={`workspace-panel ${panel.className}`}
          aria-label={panel.label}
          style={
            {
              '--panel-columns': layout[panel.id].columns,
              '--panel-rows': layout[panel.id].rows,
              gridColumn: `span ${layout[panel.id].columns}`,
              gridRow: `span ${layout[panel.id].rows}`,
            } as CSSProperties
          }
        >
          {panel.content}
          <div className="workspace-resize-controls">
            <ResizeGrip
              id={panel.id}
              label={panel.label}
              edge="width"
              onPointerDown={beginResize}
              onPointerMove={moveResize}
              onPointerUp={finishResize}
              onPointerCancel={cancelResize}
              onKeyDown={resizeFromKeyboard}
            />
            <ResizeGrip
              id={panel.id}
              label={panel.label}
              edge="height"
              onPointerDown={beginResize}
              onPointerMove={moveResize}
              onPointerUp={finishResize}
              onPointerCancel={cancelResize}
              onKeyDown={resizeFromKeyboard}
            />
            <ResizeGrip
              id={panel.id}
              label={panel.label}
              edge="both"
              onPointerDown={beginResize}
              onPointerMove={moveResize}
              onPointerUp={finishResize}
              onPointerCancel={cancelResize}
              onKeyDown={resizeFromKeyboard}
            />
          </div>
        </section>
      ))}
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
  const direction = edge === 'width' ? 'width' : edge === 'height' ? 'height' : 'width and height';
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
