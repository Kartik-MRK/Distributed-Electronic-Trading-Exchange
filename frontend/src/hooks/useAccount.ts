'use client';

import { useState, useEffect, useCallback } from 'react';
import { BalanceRecord, OrderRecord, TradeRecord, Instrument, OrderSide, OrderType } from '../types';
import { api } from '../lib/api';
import { getCurrentUser } from '../lib/auth';
import { useWebSocket } from './useWebSocket';

export function useAccount() {
  const { subscribe } = useWebSocket();
  const [balances, setBalances] = useState<BalanceRecord[]>([]);
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [orderHistory, setOrderHistory] = useState<OrderRecord[]>([]);
  const [tradeHistory, setTradeHistory] = useState<TradeRecord[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);

  // Load balances from API
  const refreshBalances = useCallback(async () => {
    try {
      const data = await api.account.getBalances();
      if (Array.isArray(data)) {
        setBalances(data);
      }
    } catch {
      // Fallback: keep current balances
    }
  }, []);

  // Load active orders from API
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

  // Load past order history from API
  const refreshOrderHistory = useCallback(async () => {
    try {
      const data = await api.orders.getOrderHistory();
      if (Array.isArray(data)) {
        setOrderHistory(data);
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
    refreshOrderHistory();
    refreshTrades();
  }, [refreshBalances, refreshOrders, refreshOrderHistory, refreshTrades]);

  useEffect(() => {
    const timer = setInterval(() => {
      void refreshBalances();
      void refreshOrders();
      void refreshOrderHistory();
      void refreshTrades();
    }, 3000);
    return () => clearInterval(timer);
  }, [refreshBalances, refreshOrders, refreshOrderHistory, refreshTrades]);

  // Subscribe to real-time order and balance updates via WebSocket for current account
  useEffect(() => {
    const user = getCurrentUser();
    const accountId = user?.userId;
    if (!accountId) return;

    const orderTopic = `/topic/orders.${accountId}`;
    const unsubscribeOrder = subscribe<Record<string, unknown>>(orderTopic, (incomingOrder) => {
      if (!incomingOrder) return;
      // Stream messages are deltas, not complete OrderRecord payloads.
      void refreshOrders();
      void refreshOrderHistory();
      void refreshBalances();
      void refreshTrades();
    });

    return () => {
      unsubscribeOrder();
    };
  }, [subscribe, refreshOrders, refreshOrderHistory, refreshBalances, refreshTrades]);

  // Cancel order
  const cancelOrder = useCallback(async (orderId: string) => {
    setIsLoading(true);
    try {
      await api.orders.cancelOrder(orderId);
      await Promise.all([refreshBalances(), refreshOrders(), refreshOrderHistory()]);
      return true;
    } finally {
      setIsLoading(false);
    }
  }, [refreshBalances, refreshOrders, refreshOrderHistory]);

  // Helper to place order and add to local state
  const placeOrder = useCallback(
    async (params: {
      instrument: Instrument;
      side: OrderSide;
      type: OrderType;
      price: string;
      quantity: string;
    }): Promise<OrderRecord> => {
      const order = await api.orders.placeOrder(params);
      setOrders((prev) => [order, ...prev]);
      await Promise.all([refreshBalances(), refreshOrders(), refreshOrderHistory(), refreshTrades()]);
      return order;
    },
    [refreshBalances, refreshOrders, refreshOrderHistory, refreshTrades]
  );

  return {
    balances,
    orders,
    orderHistory,
    tradeHistory,
    isLoading,
    refreshBalances,
    refreshOrders,
    refreshOrderHistory,
    refreshTrades,
    placeOrder,
    cancelOrder,
  };
}
