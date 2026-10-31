'use client';

import React, { useEffect, useRef, useState } from 'react';
import {
  createChart,
  IChartApi,
  ISeriesApi,
  CandlestickSeries,
  Time,
  ColorType,
} from 'lightweight-charts';
import { useSimulationClock } from '../../hooks/useSimulationClock';
import { mergeCandles } from '../../lib/simulation-clock';
import { CandleData, Instrument } from '../../types';
import { useWebSocket } from '../../hooks/useWebSocket';
import { api } from '../../lib/api';
import { toBackendInstrument } from '../../lib/format';
import { Maximize2 } from 'lucide-react';

interface PriceChartProps {
  instrument: Instrument;
}

type Interval = '1m' | '5m' | '15m' | '1h';

export function PriceChart({ instrument }: PriceChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candleSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const [interval, setInterval] = useState<Interval>('1m');
  const clock = useSimulationClock();
  const epoch = clock.snapshot?.epochId;
  const status = clock.snapshot?.status;
  const recordsRef = useRef<{epoch: string | null; candles: CandleData[]}>({epoch: null, candles: []});
  const fittedRef = useRef(false);
  const [dataError, setDataError] = useState('');
  const [lastPrice, setLastPrice] = useState<string>('');
  const { subscribe, connectionState } = useWebSocket();

  // Create chart instance
  useEffect(() => {
    if (!containerRef.current) return;

    // Canvas cannot resolve CSS variables itself; share the data palette with
    // the order book and trade tables instead of muting the chart separately.
    const theme = getComputedStyle(containerRef.current);
    const upColor = theme.getPropertyValue('--bid-green').trim();
    const downColor = theme.getPropertyValue('--ask-red').trim();

    const chart = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: '#1c1c1e' },
        textColor: '#9b9b9f',
        fontSize: 11,
        fontFamily: theme.fontFamily,
        attributionLogo: false,
      },
      grid: {
        vertLines: { color: 'rgba(255, 255, 255, 0.03)' },
        horzLines: { color: 'rgba(255, 255, 255, 0.03)' },
      },
      crosshair: {
        vertLine: { color: 'rgba(210, 210, 215, 0.35)', width: 1, style: 3 },
        horzLine: { color: 'rgba(210, 210, 215, 0.35)', width: 1, style: 3 },
      },
      timeScale: {
        borderColor: 'rgba(255, 255, 255, 0.08)',
        timeVisible: true,
        secondsVisible: false,
      },
      rightPriceScale: {
        borderColor: 'rgba(255, 255, 255, 0.08)',
        autoScale: true,
      },
    });

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor,
      downColor,
      borderVisible: false,
      wickUpColor: upColor,
      wickDownColor: downColor,
    });

    chartRef.current = chart;
    candleSeriesRef.current = candleSeries;

    recordsRef.current = {epoch: null, candles: []};
    fittedRef.current = false;
    setLastPrice('');

    // Handle container resize
    const handleResize = () => {
      if (containerRef.current && chartRef.current) {
        chartRef.current.applyOptions({
          width: containerRef.current.clientWidth,
          height: containerRef.current.clientHeight,
        });
      }
    };

    const resizeObserver = new ResizeObserver(handleResize);
    resizeObserver.observe(containerRef.current);

    return () => {
      resizeObserver.disconnect();
      chart.remove();
      chartRef.current = null;
      candleSeriesRef.current = null;
    };
  }, [instrument, interval]);

  // History and streaming updates share the same revision-aware projection.
  useEffect(() => {
    const series = candleSeriesRef.current;
    const chart = chartRef.current;
    if (!epoch || !series || !chart) return;
    let active = true;
    if (recordsRef.current.epoch !== epoch) {
      recordsRef.current = {epoch, candles: []};
      series.setData([]);
      fittedRef.current = false;
      setLastPrice('');
    }
    const apply = (incoming: CandleData[], history: boolean) => {
      if (!active) return;
      try {
        const range = chart.timeScale().getVisibleLogicalRange();
        const previous = recordsRef.current.candles;
        const following = !range || range.to >= previous.length - 1;
        const merged = mergeCandles(previous, incoming, epoch);
        recordsRef.current.candles = merged;
        series.setData(merged.map(c => ({time: c.openTime / 1000 as Time,
          open: c.open / 100_000_000, high: c.high / 100_000_000,
          low: c.low / 100_000_000, close: c.close / 100_000_000})));
        if (merged.length) setLastPrice((merged[merged.length - 1].close / 100_000_000).toFixed(2));
        if (history && !fittedRef.current && merged.length) {
          chart.timeScale().fitContent(); fittedRef.current = true;
        } else if (range && !following) chart.timeScale().setVisibleLogicalRange(range);
        else if (following) chart.timeScale().scrollToRealTime();
        setDataError('');
      } catch (cause) { setDataError(cause instanceof Error ? cause.message : 'Unable to update chart.'); }
    };
    const unsubscribe = subscribe<CandleData>(`/topic/candles.${toBackendInstrument(instrument)}.${interval}`, candle => apply([candle], false));
    void api.marketData.getCandles(instrument, interval, 1000, epoch)
      .then(candles => apply(candles, true))
      .catch(() => { if (active) setDataError('History unavailable. Reconnecting will retry.'); });
    return () => { active = false; unsubscribe(); };
  }, [instrument, interval, epoch, status, connectionState, subscribe]);

  return (
    <div className="panel price-chart">
      <div className="chart-toolbar">
        <div>
          <h2>
            {instrument.replace('_', ' / ')}{' '}
            <span className="muted">/ Spot</span>
          </h2>
          <p className="chart-quote font-mono">
            {lastPrice ? Number(lastPrice).toLocaleString('en-US', {
              minimumFractionDigits: 2,
            }) : '?'}{' '}
            <span>USD</span>
          </p>
        </div>
        <div className="chart-actions">
          <div className="segmented" aria-label="Chart interval">
            {(['1m', '5m', '15m', '1h'] as Interval[]).map((value) => (
              <button
                key={value}
                aria-pressed={interval === value}
                onClick={() => setInterval(value)}
              >
                {value}
              </button>
            ))}
          </div>
          <button
            className="icon-button"
            aria-label="Reset chart zoom"
            title="Reset chart zoom"
            onClick={() => chartRef.current?.timeScale().fitContent()}
          >
            <Maximize2 size={15} />
          </button>
        </div>
      </div>
      <div className="chart-canvas" ref={containerRef} />
      <div className="chart-caption">
        <span>Simulation time ? 1m = one active minute</span>
        <span>{dataError || (connectionState === 'connected' ? 'Exchange history / Scroll to zoom' : 'Feed unavailable')}</span>
      </div>
    </div>
  );
}
