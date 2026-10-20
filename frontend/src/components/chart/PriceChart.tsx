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
import { Maximize2 } from 'lucide-react';

interface PriceChartProps {
  instrument: Instrument;
}

type Interval = '1m' | '5m' | '15m' | '1h';

function generateCandles(
  instrument: Instrument,
  count = 120,
  intervalMinutes = 5,
): CandlestickData<Time>[] {
  const meta = INSTRUMENT_METAS[instrument];
  const mid = parseFloat(meta.lastPrice);
  const candles: CandlestickData<Time>[] = [];
  const nowSec =
    Math.floor(Date.now() / (intervalMinutes * 60000)) * intervalMinutes * 60;
  const stepSec = intervalMinutes * 60;
  let currentPrice = mid * 0.995;

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
  const [lastPrice, setLastPrice] = useState<string>(
    INSTRUMENT_METAS[instrument].lastPrice,
  );
  const { subscribe, connectionState } = useWebSocket();

  // Create chart instance
  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: '#1c1c1e' },
        textColor: '#9b9b9f',
        fontSize: 11,
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
      upColor: '#9bafa2',
      downColor: '#b38c8c',
      borderVisible: false,
      wickUpColor: '#9bafa2',
      wickDownColor: '#b38c8c',
    });

    chartRef.current = chart;
    candleSeriesRef.current = candleSeries;

    // Load initial candles
    const intervalMin =
      interval === '1m'
        ? 1
        : interval === '5m'
          ? 5
          : interval === '15m'
            ? 15
            : 60;
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

  // Only animate sample ticks while the live feed is unavailable.
  useEffect(() => {
    if (connectionState === 'connected') return;
    const intervalId = window.setInterval(() => {
      if (!candleSeriesRef.current) return;
      const meta = INSTRUMENT_METAS[instrument];
      const mid = parseFloat(meta.lastPrice);
      const delta = (Math.random() - 0.49) * (mid * 0.0004);
      const updatedClose = parseFloat(
        (mid + delta).toFixed(meta.priceDecimals),
      );
      const step =
        (interval === '1m'
          ? 1
          : interval === '5m'
            ? 5
            : interval === '15m'
              ? 15
              : 60) * 60;
      const nowSec = (Math.floor(Date.now() / (step * 1000)) *
        step) as unknown as Time;

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
  }, [instrument, interval, connectionState]);

  return (
    <div className="panel price-chart">
      <div className="chart-toolbar">
        <div>
          <h2>
            {instrument.replace('_', ' / ')}{' '}
            <span className="muted">/ Spot</span>
          </h2>
          <p className="chart-quote font-mono">
            {Number(lastPrice).toLocaleString('en-US', {
              minimumFractionDigits: 2,
            })}{' '}
            <span>USDT</span>
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
        <span>Sample history / Scroll to zoom</span>
      </div>
    </div>
  );
}
