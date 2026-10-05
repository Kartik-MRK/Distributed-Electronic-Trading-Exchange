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
import { INSTRUMENT_METAS } from '../../lib/api';
import { Maximize2, BarChart2 } from 'lucide-react';

interface PriceChartProps {
  instrument: Instrument;
}

type Interval = '1m' | '5m' | '15m' | '1h';

function generateCandles(instrument: Instrument, count = 120, intervalMinutes = 5): CandlestickData<Time>[] {
  const meta = INSTRUMENT_METAS[instrument];
  const mid = parseFloat(meta.lastPrice);
  const candles: CandlestickData<Time>[] = [];
  const nowSec = Math.floor(Date.now() / 1000);
  const stepSec = intervalMinutes * 60;
  let currentPrice = mid * (1 - (count * 0.0008));

  for (let i = count; i >= 0; i--) {
    const time = (nowSec - i * stepSec) as unknown as Time;
    const volatility = currentPrice * 0.0035;
    const open = currentPrice;
    const change = (Math.random() - 0.49) * volatility;
    const close = Math.max(1, open + change);
    const high = Math.max(open, close) + Math.random() * (volatility * 0.6);
    const low = Math.min(open, close) - Math.random() * (volatility * 0.6);

    candles.push({
      time,
      open: parseFloat(open.toFixed(meta.priceDecimals)),
      high: parseFloat(high.toFixed(meta.priceDecimals)),
      low: parseFloat(low.toFixed(meta.priceDecimals)),
      close: parseFloat(close.toFixed(meta.priceDecimals)),
    });

    currentPrice = close;
  }

  return candles;
}

export function PriceChart({ instrument }: PriceChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candleSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const [interval, setInterval] = useState<Interval>('5m');
  const [lastPrice, setLastPrice] = useState<string>(INSTRUMENT_METAS[instrument].lastPrice);
  const { subscribe } = useWebSocket();

  // Create chart instance
  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: '#0e1524' },
        textColor: '#94a3b8',
        fontSize: 11,
      },
      grid: {
        vertLines: { color: 'rgba(255, 255, 255, 0.03)' },
        horzLines: { color: 'rgba(255, 255, 255, 0.03)' },
      },
      crosshair: {
        vertLine: { color: 'rgba(0, 210, 255, 0.4)', width: 1, style: 3 },
        horzLine: { color: 'rgba(0, 210, 255, 0.4)', width: 1, style: 3 },
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
      upColor: '#00f5a0',
      downColor: '#ff3b69',
      borderVisible: false,
      wickUpColor: '#00f5a0',
      wickDownColor: '#ff3b69',
    });

    chartRef.current = chart;
    candleSeriesRef.current = candleSeries;

    // Load initial candles
    const intervalMin = interval === '1m' ? 1 : interval === '5m' ? 5 : interval === '15m' ? 15 : 60;
    const initialData = generateCandles(instrument, 100, intervalMin);
    candleSeries.setData(initialData);
    chart.timeScale().fitContent();

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

  // Subscribe to live candle ticks
  useEffect(() => {
    const topic = `/topic/candles.${instrument}.${interval}`;
    const unsubscribe = subscribe<{
      time: number;
      open: number;
      high: number;
      low: number;
      close: number;
    }>(topic, (candle) => {
      if (!candle || !candleSeriesRef.current) return;
      candleSeriesRef.current.update({
        time: candle.time as unknown as Time,
        open: candle.open,
        high: candle.high,
        low: candle.low,
        close: candle.close,
      });
      setLastPrice(candle.close.toFixed(2));
    });

    return () => {
      unsubscribe();
    };
  }, [instrument, interval, subscribe]);

  // Periodic subtle tick update to keep canvas reactive
  useEffect(() => {
    const intervalId = window.setInterval(() => {
      if (!candleSeriesRef.current) return;
      const meta = INSTRUMENT_METAS[instrument];
      const mid = parseFloat(meta.lastPrice);
      const delta = (Math.random() - 0.49) * (mid * 0.0004);
      const updatedClose = parseFloat((mid + delta).toFixed(meta.priceDecimals));
      const nowSec = (Math.floor(Date.now() / 60000) * 60) as unknown as Time;

      try {
        candleSeriesRef.current.update({
          time: nowSec,
          open: mid,
          high: Math.max(mid, updatedClose) + 0.5,
          low: Math.min(mid, updatedClose) - 0.5,
          close: updatedClose,
        });
        setLastPrice(updatedClose.toFixed(meta.priceDecimals));
      } catch {
        // Safe catch if timestamp ordering differs
      }
    }, 4000);

    return () => window.clearInterval(intervalId);
  }, [instrument]);

  return (
    <div
      className="glass-panel"
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        backgroundColor: 'var(--bg-panel)',
      }}
    >
      {/* Chart Top Bar */}
      <div
        style={{
          height: 38,
          borderBottom: '1px solid var(--border-subtle)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 12px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <BarChart2 size={16} color="var(--accent-cyan)" />
            <span style={{ fontWeight: 700, fontSize: '0.82rem', color: 'var(--text-primary)' }}>
              {instrument.replace('_', '/')}
            </span>
            <span
              className="font-mono"
              style={{
                fontSize: '0.8rem',
                fontWeight: 600,
                color: 'var(--bid-green)',
                marginLeft: 4,
              }}
            >
              ${lastPrice}
            </span>
          </div>

          {/* Timeframe Selectors */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              backgroundColor: 'var(--bg-input)',
              borderRadius: 4,
              padding: 2,
              gap: 2,
            }}
          >
            {(['1m', '5m', '15m', '1h'] as Interval[]).map((int) => (
              <button
                key={int}
                onClick={() => setInterval(int)}
                style={{
                  background: interval === int ? 'var(--bg-hover)' : 'transparent',
                  color: interval === int ? 'var(--accent-cyan)' : 'var(--text-muted)',
                  border: 'none',
                  borderRadius: 3,
                  padding: '2px 8px',
                  fontSize: '0.7rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
              >
                {int}
              </button>
            ))}
          </div>
        </div>

        <button
          onClick={() => {
            if (chartRef.current) {
              chartRef.current.timeScale().fitContent();
            }
          }}
          title="Reset Zoom"
          style={{
            background: 'none',
            border: 'none',
            color: 'var(--text-muted)',
            cursor: 'pointer',
            padding: 4,
            display: 'flex',
            alignItems: 'center',
          }}
        >
          <Maximize2 size={14} />
        </button>
      </div>

      {/* Chart Canvas Area */}
      <div
        ref={containerRef}
        style={{
          flex: 1,
          width: '100%',
          position: 'relative',
        }}
      />
    </div>
  );
}
