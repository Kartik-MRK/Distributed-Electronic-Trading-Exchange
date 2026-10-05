'use client';

import { useState, useEffect, useCallback } from 'react';
import { BalanceRecord, OrderRecord, TradeRecord, Instrument, OrderSide, OrderType } from '../types';
import { api, INITIAL_DEMO_BALANCES } from '../lib/api';
import { getCurrentUser } from '../lib/auth';
import { useWebSocket } from './useWebSocket';

export function useAccount() {
  const { subscribe } = useWebSocket();
  const [balances, setBalances] = useState<BalanceRecord[]>(INITIAL_DEMO_BALANCES);
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [tradeHistory, setTradeHistory] = useState<TradeRecord[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);

  // Load balances from API
  const refreshBalances = useCallback(async () => {
    try {
      const data = await api.account.getBalances();
      if (Array.isArray(data) && data.length > 0) {
        setBalances(data);
      }
    } catch {
      // Fallback: keep current balances
    }
  }, []);

  // Load orders from API
  const refreshOrders = useCallback(async () => {
    try {
      const data = await api.orders.getOrders();
      if (Array.isArray(data)) {
        setOrders(data);
      }
    } catch {
      // Fallback
    }
  }, []);

  // Load personal trade fills
  const refreshTrades = useCallback(async () => {
    try {
      const data = await api.account.getTrades();
      if (Array.isArray(data)) {
        setTradeHistory(data);
      }
    } catch {
      // Fallback
    }
  }, []);

  // Initial load
  useEffect(() => {
    refreshBalances();
    refreshOrders();
    refreshTrades();
  }, [refreshBalances, refreshOrders, refreshTrades]);

  // Subscribe to real-time order and balance updates via WebSocket for current account
  useEffect(() => {
    const user = getCurrentUser();
    const accountId = user?.userId || 'default';

    const orderTopic = `/topic/orders.${accountId}`;
    const unsubscribeOrder = subscribe<OrderRecord>(orderTopic, (updatedOrder) => {
      if (!updatedOrder || !updatedOrder.orderId) return;

      setOrders((prev) => {
        const index = prev.findIndex((o) => o.orderId === updatedOrder.orderId);
        if (index >= 0) {
          const updated = [...prev];
          updated[index] = updatedOrder;
          return updated;
        }
        return [updatedOrder, ...prev];
      });

      // If order got filled or partially filled, refresh balances
      if (updatedOrder.status === 'FILLED' || updatedOrder.status === 'PARTIALLY_FILLED') {
        refreshBalances();
        refreshTrades();
      }
    });

    return () => {
      unsubscribeOrder();
    };
  }, [subscribe, refreshBalances, refreshTrades]);

  // Cancel order
  const cancelOrder = useCallback(async (orderId: string) => {
    setIsLoading(true);
    try {
      await api.orders.cancelOrder(orderId);
      // Optimistic status update
      setOrders((prev) =>
        prev.map((o) =>
          o.orderId === orderId ? { ...o, status: 'CANCELLED' } : o
        )
      );
      await refreshBalances();
      return true;
    } catch {
      // If server unreachable in standalone demo, optimistically mark cancelled
      setOrders((prev) =>
        prev.map((o) =>
          o.orderId === orderId ? { ...o, status: 'CANCELLED' } : o
        )
      );
      return true;
    } finally {
      setIsLoading(false);
    }
  }, [refreshBalances]);

  // Helper to place order and add to local state
  const placeOrder = useCallback(
    async (params: {
      instrument: Instrument;
      side: OrderSide;
      type: OrderType;
      price: string;
      quantity: string;
    }): Promise<OrderRecord> => {
      try {
        const order = await api.orders.placeOrder(params);
        setOrders((prev) => [order, ...prev]);
        await refreshBalances();
        return order;
      } catch (err) {
        // Fallback for standalone demo: simulate order lifecycle
        const simOrder: OrderRecord = {
          orderId: `ord-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
          accountId: getCurrentUser()?.userId || 'demo-user-id',
          instrument: params.instrument,
          side: params.side,
          type: params.type,
          price: params.price,
          quantity: params.quantity,
          filledQuantity: params.type === 'MARKET' ? params.quantity : '0.0000',
          remainingQuantity: params.type === 'MARKET' ? '0.0000' : params.quantity,
          status: params.type === 'MARKET' ? 'FILLED' : 'ACCEPTED',
          createdAt: new Date().toISOString(),
        };

        setOrders((prev) => [simOrder, ...prev]);

        // If market order, simulate immediate fill and update balances
        if (params.type === 'MARKET') {
          const cost = parseFloat(params.price) * parseFloat(params.quantity);
          const [base, quote] = params.instrument.split('_');

          setBalances((prev) =>
            prev.map((b) => {
              if (params.side === 'BUY') {
                if (b.asset === quote) {
                  const newAvail = Math.max(0, parseFloat(b.available) - cost).toFixed(2);
                  return { ...b, available: newAvail, total: (parseFloat(newAvail) + parseFloat(b.reserved)).toFixed(2) };
                }
                if (b.asset === base) {
                  const newAvail = (parseFloat(b.available) + parseFloat(params.quantity)).toFixed(4);
                  return { ...b, available: newAvail, total: (parseFloat(newAvail) + parseFloat(b.reserved)).toFixed(4) };
                }
              } else {
                if (b.asset === base) {
                  const newAvail = Math.max(0, parseFloat(b.available) - parseFloat(params.quantity)).toFixed(4);
                  return { ...b, available: newAvail, total: (parseFloat(newAvail) + parseFloat(b.reserved)).toFixed(4) };
                }
                if (b.asset === quote) {
                  const newAvail = (parseFloat(b.available) + cost).toFixed(2);
                  return { ...b, available: newAvail, total: (parseFloat(newAvail) + parseFloat(b.reserved)).toFixed(2) };
                }
              }
              return b;
            })
          );

          // Add to personal trade fills
          const simTrade: TradeRecord = {
            tradeId: `tr-${Date.now()}`,
            instrument: params.instrument,
            price: params.price,
            quantity: params.quantity,
            makerSide: params.side === 'BUY' ? 'SELL' : 'BUY',
            takerSide: params.side,
            executedAt: new Date().toISOString(),
          };
          setTradeHistory((prev) => [simTrade, ...prev]);
        }

        return simOrder;
      }
    },
    [refreshBalances]
  );

  return {
    balances,
    orders,
    tradeHistory,
    isLoading,
    refreshBalances,
    refreshOrders,
    refreshTrades,
    placeOrder,
    cancelOrder,
  };
}
