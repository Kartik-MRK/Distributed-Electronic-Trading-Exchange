'use client';

import React, { useEffect, useRef, useState } from 'react';
import {
  createChart,
  IChartApi,
  ISeriesApi,
  CandlestickData,
  CandlestickSeries,
  Time,
  ColorType,
} from 'lightweight-charts';
import { Instrument } from '../../types';
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
  const [interval, setInterval] = useState<Interval>('5m');
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

    let active = true;
    setLastPrice('');
    void api.marketData.getCandles(instrument, interval).then((candles) => {
      if (!active) return;
      const history = candles.map((candle) => ({
        time: Math.floor(Number(candle.openTime) / 1000) as Time,
        open: Number(candle.open) / 100_000_000,
        high: Number(candle.high) / 100_000_000,
        low: Number(candle.low) / 100_000_000,
        close: Number(candle.close) / 100_000_000,
      }));
      const merged = new Map<number, CandlestickData<Time>>();
      history.forEach((candle) => merged.set(Number(candle.time), candle));
      // Preserve ticks that arrived while history was loading.
      candleSeries.data().forEach((candle) => {
        if ('open' in candle) merged.set(Number(candle.time), candle);
      });
      const data = [...merged.values()].sort((a, b) => Number(a.time) - Number(b.time));
      candleSeries.setData(data);
      if (data.length) setLastPrice(data[data.length - 1].close.toFixed(2));
      chart.timeScale().fitContent();
    }).catch(() => { /* Live ticks may still populate the chart. */ });

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
      active = false;
      resizeObserver.disconnect();
      chart.remove();
      chartRef.current = null;
      candleSeriesRef.current = null;
    };
  }, [instrument, interval]);

  // Subscribe to live candle ticks
  useEffect(() => {
    const backendInst = toBackendInstrument(instrument);
    const primaryTopic = `/topic/candles.${backendInst}.${interval}`;
    const altTopic = `/topic/candles.${instrument}.${interval}`;

    const handleIncomingCandle = (candle: Record<string, unknown>) => {
      if (!candle || !candleSeriesRef.current) return;
      const rawTime = candle.openTime ?? candle.time;
      const timeSec =
        typeof rawTime === 'number'
          ? (rawTime > 1_000_000_000_000 ? Math.floor(rawTime / 1000) : rawTime)
          : Math.floor(Date.now() / 1000);

      const o =
        Number(candle.open) / 100_000_000;
      const h =
        Number(candle.high) / 100_000_000;
      const l =
        Number(candle.low) / 100_000_000;
      const c =
        Number(candle.close) / 100_000_000;

      try {
        candleSeriesRef.current.update({
          time: timeSec as unknown as Time,
          open: o,
          high: h,
          low: l,
          close: c,
        });
        setLastPrice(c.toFixed(2));
      } catch {
        // Safe catch for timestamp ordering differences
      }
    };

    const unsubscribePrimary = subscribe<Record<string, unknown>>(
      primaryTopic,
      handleIncomingCandle
    );
    const unsubscribeAlt =
      primaryTopic !== altTopic
        ? subscribe<Record<string, unknown>>(altTopic, handleIncomingCandle)
        : () => {};

    return () => {
      unsubscribePrimary();
      unsubscribeAlt();
    };
  }, [instrument, interval, subscribe]);

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
        <span>Candlestick chart</span>
        <span>{connectionState === 'connected' ? 'Exchange history / Scroll to zoom' : 'Feed unavailable'}</span>
      </div>
    </div>
  );
}
