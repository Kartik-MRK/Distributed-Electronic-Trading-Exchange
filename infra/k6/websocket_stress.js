import ws from 'k6/ws';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const wsConnectionsActive = new Counter('ws_active_connections');
const wsMessagesReceived = new Counter('ws_messages_received_total');
const wsConnectionSuccess = new Rate('ws_connection_success_rate');
const wsConnectingDuration = new Trend('ws_connecting_duration', true);

// Target endpoint
const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws/websocket';
const INSTRUMENT = __ENV.INSTRUMENT || 'BTC_USD';

export const options = {
  scenarios: {
    websocket_connections: {
      executor: 'ramping-vus',
      startVUs: 100,
      stages: [
        { duration: '15s', target: 500 },
        { duration: '20s', target: 2000 },
        { duration: '30s', target: 2000 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    // Phase 16 Target: No drops, 100% stable connection rate
    ws_connection_success_rate: ['rate>0.99'],
    ws_messages_received_total: ['count>1000'],
  },
};

export default function () {
  const url = WS_URL;
  const startTime = new Date().getTime();

  const response = ws.connect(url, null, function (socket) {
    wsConnectingDuration.add(new Date().getTime() - startTime);
    wsConnectionsActive.add(1);
    wsConnectionSuccess.add(1);

    socket.on('open', function () {
      // Send STOMP CONNECT frame
      const connectFrame = 'CONNECT\naccept-version:1.1,1.2\nheart-beat:10000,10000\n\n\0';
      socket.send(connectFrame);

      // Subscribe to L2 orderbook topic
      const subscribeFrame = `SUBSCRIBE\nid:sub-0\ndestination:/topic/orderbook.${INSTRUMENT}\n\n\0`;
      socket.send(subscribeFrame);
    });

    socket.on('message', function (data) {
      wsMessagesReceived.add(1);
    });

    socket.on('error', function (e) {
      console.error(`WebSocket error encountered: ${e.error()}`);
      wsConnectionSuccess.add(0);
    });

    socket.on('close', function () {
      wsConnectionsActive.add(-1);
    });

    // Hold connection open for the duration of this iteration
    socket.setTimeout(function () {
      socket.close();
    }, 25000);
  });

  check(response, {
    'connected successfully': (r) => r && r.status === 101,
  });
}
