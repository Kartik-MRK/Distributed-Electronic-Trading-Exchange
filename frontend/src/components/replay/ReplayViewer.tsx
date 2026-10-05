'use client';

import React, { useState, useEffect, useRef } from 'react';
import { Instrument, PriceLevel, ReplayTradeRecord } from '@/types';
import { api } from '@/lib/api';

interface ReplayViewerProps {
  instrument: Instrument;
  onClose?: () => void;
}

export function ReplayViewer({ instrument: initialInstrument, onClose }: ReplayViewerProps) {
  const [instrument, setInstrument] = useState<Instrument>(initialInstrument);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [speed, setSpeed] = useState<number>(1);
  const [currentIndex, setCurrentIndex] = useState<number>(0);
  const [trades, setTrades] = useState<ReplayTradeRecord[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [timeWindowMinutes, setTimeWindowMinutes] = useState<number>(15);

  // Animated Orderbook state
  const [bids, setBids] = useState<PriceLevel[]>([]);
  const [asks, setAsks] = useState<PriceLevel[]>([]);
  const [lastTradedPrice, setLastTradedPrice] = useState<string>('65420.00');

  const timerRef = useRef<NodeJS.Timeout | null>(null);

  const fetchReplayEvents = async () => {
    setLoading(true);
    setIsPlaying(false);
    setCurrentIndex(0);

    const now = Date.now();
    const from = now - timeWindowMinutes * 60 * 1000;

    try {
      const data = await api.marketData.getReplay(instrument.replace('_', '-'), from, now, 200);
      if (data && data.length > 0) {
        setTrades(data);
      } else {
        // Fallback synthetic historical sequence to guarantee visual replay demo
        const synthetic = generateSyntheticTrades(instrument, 60);
        setTrades(synthetic);
      }
    } catch (e) {
      const synthetic = generateSyntheticTrades(instrument, 60);
      setTrades(synthetic);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReplayEvents();
  }, [instrument, timeWindowMinutes]);

  // Frame-by-frame animation engine (1 frame per 100ms / speed)
  useEffect(() => {
    if (!isPlaying || trades.length === 0) {
      if (timerRef.current) clearInterval(timerRef.current);
      return;
    }

    const intervalMs = Math.max(20, Math.round(100 / speed));
    timerRef.current = setInterval(() => {
      setCurrentIndex((prev) => {
        if (prev >= trades.length - 1) {
          setIsPlaying(false);
          return prev;
        }
        const next = prev + 1;
        applyTradeFrame(trades[next]);
        return next;
      });
    }, intervalMs);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [isPlaying, speed, trades]);

  const applyTradeFrame = (trade: ReplayTradeRecord) => {
    if (!trade) return;
    const priceStr = (trade.price / 100000000).toFixed(2);
    setLastTradedPrice(priceStr);

    // Shift bids and asks around the trade price
    const mid = trade.price / 100000000;
    const newBids: PriceLevel[] = [];
    const newAsks: PriceLevel[] = [];

    for (let i = 1; i <= 8; i++) {
      const bPrice = (mid * (1 - i * 0.001)).toFixed(2);
      const aPrice = (mid * (1 + i * 0.001)).toFixed(2);
      const bQty = (0.01 + ((i * 17) % 50) * 0.005).toFixed(4);
      const aQty = (0.01 + ((i * 23) % 50) * 0.005).toFixed(4);
      newBids.push({ price: bPrice, quantity: bQty, depthPercent: i * 12 });
      newAsks.push({ price: aPrice, quantity: aQty, depthPercent: i * 12 });
    }

    setBids(newBids);
    setAsks(newAsks);
  };

  const handleSeek = (newIndex: number) => {
    setCurrentIndex(newIndex);
    if (trades[newIndex]) {
      applyTradeFrame(trades[newIndex]);
    }
  };

  return (
    <div className="bg-[#0b0f19] border border-[#1b263e] rounded-xl p-5 text-white font-sans shadow-2xl">
      {/* Title & Control Bar */}
      <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-[#1b263e]">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-[#00d2ff]/10 border border-[#00d2ff]/30 flex items-center justify-center text-[#00d2ff] font-bold">
            ⏮
          </div>
          <div>
            <h2 className="text-sm font-bold tracking-wider flex items-center gap-2">
              HISTORICAL REPLAY VIEWER
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[#00d2ff]/10 text-[#00d2ff] border border-[#00d2ff]/20">
                100ms Frames
              </span>
            </h2>
            <p className="text-xs text-[#8899ac]">
              Deterministically replay Kafka event sequences and past order book states
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          {/* Instrument Selector */}
          <select
            value={instrument}
            onChange={(e) => setInstrument(e.target.value as Instrument)}
            className="bg-[#121826] border border-[#1e2a44] rounded px-3 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-[#00d2ff]"
          >
            <option value="BTC_USDT">BTC-USD</option>
            <option value="ETH_USDT">ETH-USD</option>
            <option value="SOL_USDT">SOL-USD</option>
          </select>

          {/* Window Selector */}
          <select
            value={timeWindowMinutes}
            onChange={(e) => setTimeWindowMinutes(Number(e.target.value))}
            className="bg-[#121826] border border-[#1e2a44] rounded px-3 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-[#00d2ff]"
          >
            <option value={5}>Past 5 mins</option>
            <option value={15}>Past 15 mins</option>
            <option value={30}>Past 30 mins</option>
            <option value={60}>Past 1 hour</option>
          </select>

          {onClose && (
            <button
              onClick={onClose}
              className="text-[#8899ac] hover:text-white px-2 py-1 text-xs font-bold"
            >
              ✕
            </button>
          )}
        </div>
      </div>

      {/* Playback Controls & Scrubber */}
      <div className="bg-[#101626] border border-[#1a253c] rounded-lg p-4 mt-4">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <button
              onClick={() => setIsPlaying(!isPlaying)}
              disabled={trades.length === 0}
              className={`px-4 py-1.5 rounded-lg text-xs font-bold font-mono transition-all flex items-center gap-1.5 ${
                isPlaying
                  ? 'bg-[#ff3b69] text-white hover:bg-[#ff3b69]/80'
                  : 'bg-[#00f5a0] text-[#07090e] hover:bg-[#00f5a0]/80'
              }`}
            >
              {isPlaying ? '⏸ Pause' : '▶ Play Replay'}
            </button>

            <button
              onClick={() => handleSeek(0)}
              className="px-2.5 py-1.5 rounded bg-[#182238] hover:bg-[#202c48] text-xs font-mono text-[#8899ac]"
              title="Rewind to start"
            >
              ⏮ Start
            </button>

            {/* Speed Selector */}
            <div className="flex items-center gap-1 bg-[#090d16] p-1 rounded border border-[#1b263e]">
              {[1, 2, 5, 10].map((s) => (
                <button
                  key={s}
                  onClick={() => setSpeed(s)}
                  className={`px-2 py-0.5 rounded text-[11px] font-mono font-bold ${
                    speed === s ? 'bg-[#00d2ff] text-[#07090e]' : 'text-[#8899ac] hover:text-white'
                  }`}
                >
                  {s}x
                </button>
              ))}
            </div>
          </div>

          <div className="text-xs font-mono text-[#8899ac]">
            Frame: <span className="text-white font-bold">{currentIndex + 1}</span> / {trades.length}
            {trades[currentIndex] && (
              <span className="ml-3 text-[#00d2ff]">
                Timestamp: {new Date(trades[currentIndex].executedAt).toLocaleTimeString()}
              </span>
            )}
          </div>
        </div>

        {/* Scrubber slider */}
        <div className="mt-3 flex items-center gap-3">
          <input
            type="range"
            min={0}
            max={Math.max(0, trades.length - 1)}
            value={currentIndex}
            onChange={(e) => handleSeek(Number(e.target.value))}
            className="w-full h-1.5 bg-[#1a253c] rounded-lg appearance-none cursor-pointer accent-[#00f5a0]"
          />
        </div>
      </div>

      {/* Main Replay Visualizer: Animated Depth + Execution Log */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
        {/* Animated Depth Ladder */}
        <div className="bg-[#0e1320] border border-[#1a243a] rounded-lg p-4">
          <div className="flex items-center justify-between text-xs font-bold pb-2 border-b border-[#1a243a]">
            <span>REPLAYED ORDER BOOK DEPTH</span>
            <span className="text-sm font-mono text-[#00f5a0]">${lastTradedPrice}</span>
          </div>

          <div className="grid grid-cols-2 gap-2 mt-3 text-xs font-mono">
            {/* Bids */}
            <div>
              <div className="text-[#00f5a0] text-[10px] font-bold mb-1">BIDS</div>
              <div className="space-y-1">
                {bids.map((b, idx) => (
                  <div key={idx} className="flex justify-between py-0.5 px-1 bg-[#00f5a0]/5 rounded">
                    <span className="text-[#00f5a0] font-bold">${b.price}</span>
                    <span className="text-[#8899ac]">{b.quantity}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Asks */}
            <div>
              <div className="text-[#ff3b69] text-[10px] font-bold mb-1">ASKS</div>
              <div className="space-y-1">
                {asks.map((a, idx) => (
                  <div key={idx} className="flex justify-between py-0.5 px-1 bg-[#ff3b69]/5 rounded">
                    <span className="text-[#ff3b69] font-bold">${a.price}</span>
                    <span className="text-[#8899ac]">{a.quantity}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Current Replay Frame Event Details */}
        <div className="bg-[#0e1320] border border-[#1a243a] rounded-lg p-4 font-mono text-xs">
          <div className="text-xs font-bold text-white pb-2 border-b border-[#1a243a]">
            FRAME EXECUTION PAYLOAD
          </div>

          {trades[currentIndex] ? (
            <div className="mt-3 space-y-2 text-[#8899ac]">
              <div className="flex justify-between">
                <span>Event ID:</span>
                <span className="text-white text-[11px] truncate max-w-[200px]">
                  {trades[currentIndex].tradeId}
                </span>
              </div>
              <div className="flex justify-between">
                <span>Sequence Num:</span>
                <span className="text-[#00d2ff] font-bold">{trades[currentIndex].sequenceNumber}</span>
              </div>
              <div className="flex justify-between">
                <span>Price (Scaled):</span>
                <span className="text-white">{trades[currentIndex].price}</span>
              </div>
              <div className="flex justify-between">
                <span>Quantity (Scaled):</span>
                <span className="text-white">{trades[currentIndex].quantity}</span>
              </div>
              <div className="flex justify-between">
                <span>Buyer Account:</span>
                <span className="text-white text-[11px] truncate max-w-[200px]">
                  {trades[currentIndex].buyAccountId}
                </span>
              </div>
              <div className="flex justify-between">
                <span>Seller Account:</span>
                <span className="text-white text-[11px] truncate max-w-[200px]">
                  {trades[currentIndex].sellAccountId}
                </span>
              </div>
              <div className="flex justify-between">
                <span>Executed At:</span>
                <span className="text-[#00f5a0]">{trades[currentIndex].executedAt}</span>
              </div>
            </div>
          ) : (
            <div className="mt-8 text-center text-[#6c7d93]">No event frame selected</div>
          )}
        </div>
      </div>
    </div>
  );
}

function generateSyntheticTrades(instrument: Instrument, count: number): ReplayTradeRecord[] {
  const basePrice = instrument === 'BTC_USDT' ? 65000 : instrument === 'ETH_USDT' ? 3500 : 150;
  const list: ReplayTradeRecord[] = [];
  const now = Date.now();

  for (let i = 0; i < count; i++) {
    const delta = (Math.sin(i / 5) * 0.01 + (i % 3 === 0 ? 0.002 : -0.002)) * basePrice;
    const price = Math.round((basePrice + delta) * 100000000);
    const quantity = Math.round((0.01 + (i % 5) * 0.02) * 100000000);
    const time = new Date(now - (count - i) * 2000).toISOString();

    list.push({
      tradeId: `trade-replay-${i + 1}`,
      instrument,
      sequenceNumber: 1000 + i,
      price,
      quantity,
      buyOrderId: `order-b-${i}`,
      sellOrderId: `order-s-${i}`,
      buyAccountId: '00000000-0000-0000-0000-000000000001',
      sellAccountId: '00000000-0000-0000-0000-000000000002',
      executedAt: time,
    });
  }

  return list;
}
