export type PanelId =
  | 'chart'
  | 'book'
  | 'simulation'
  | 'orderEntry'
  | 'activity'
  | 'balances';

export type ResizeAxis = 'width' | 'height' | 'both';
export type ColumnId = 'left' | 'middle' | 'right';

export interface PanelSize {
  columns: number;
  rows: number;
}

export type PanelLayout = Record<PanelId, PanelSize>;
export type ColumnLayout = Record<ColumnId, number>;

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
export const DEFAULT_COLUMN_LAYOUT: Readonly<ColumnLayout>;
export const COLUMN_LIMITS: Readonly<Record<ColumnId, { min: number; max: number }>>;
export const PANEL_LIMITS: Readonly<
  Record<PanelId, { minColumns: number; maxColumns: number; minRows: number; maxRows: number }>
>;
export function normalizePanelLayout(value: unknown): PanelLayout;
export function normalizeColumnLayout(value: unknown): ColumnLayout;
export function readPanelLayout(storage: LayoutStorage | undefined): PanelLayout;
export function readColumnLayout(
  storage: LayoutStorage | undefined,
  legacyPanelLayout?: unknown,
): ColumnLayout;
export function savePanelLayout(storage: LayoutStorage | undefined, layout: unknown): void;
export function saveColumnLayout(storage: LayoutStorage | undefined, layout: unknown): void;
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
export function resizeColumnBoundary(
  layout: unknown,
  leftId: ColumnId,
  rightId: ColumnId,
  delta: number,
): ColumnLayout;
export function getResizeDeltas(axis: ResizeAxis, drag: ResizeDrag): ResizeDelta;
