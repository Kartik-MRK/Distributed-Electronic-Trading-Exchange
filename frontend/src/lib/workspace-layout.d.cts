export type PanelId =
  | 'chart'
  | 'book'
  | 'simulation'
  | 'orderEntry'
  | 'activity'
  | 'balances';

export type ResizeAxis = 'width' | 'height' | 'both';

export interface PanelSize {
  columns: number;
  rows: number;
}

export type PanelLayout = Record<PanelId, PanelSize>;

export interface ResizeDrag {
  startX: number;
  startY: number;
  currentX: number;
  currentY: number;
  columnStep: number;
  rowStep: number;
}

export interface ResizeDelta {
  columns: number;
  rows: number;
}

export interface LayoutStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

export const DEFAULT_PANEL_LAYOUT: Readonly<PanelLayout>;
export const PANEL_LIMITS: Readonly<
  Record<PanelId, { minColumns: number; maxColumns: number; minRows: number; maxRows: number }>
>;
export function normalizePanelLayout(value: unknown): PanelLayout;
export function readPanelLayout(storage: LayoutStorage | undefined): PanelLayout;
export function savePanelLayout(storage: LayoutStorage | undefined, layout: unknown): void;
export function resizePanelLayout(
  layout: unknown,
  id: PanelId,
  delta: Partial<ResizeDelta> | null | undefined,
): PanelLayout;
export function resizePanelBoundaryLayout(
  layout: unknown,
  leftId: PanelId,
  rightId: PanelId,
  deltaColumns: number,
): PanelLayout;
export function getResizeDeltas(axis: ResizeAxis, drag: ResizeDrag): ResizeDelta;
