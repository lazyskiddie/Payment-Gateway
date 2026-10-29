// Load test for POST /api/v1/payments (idempotency now handled: duplicates must never 5xx)
// Run:  k6 run -e BASE_URL=http://localhost:8080 k6-payment-load.js
// Install k6: https://k6.io/docs/get-started/installation/
import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const accepted = new Rate('accepted_202');
const dupHandled = new Rate('duplicate_not_5xx');
const acceptLatency = new Trend('accept_latency_ms', true);

export const options = {
  scenarios: {
    // Unique payments: ramp the ARRIVAL RATE (req/s), not VUs, so you find the real ceiling.
    new_payments: {
      executor: 'ramping-arrival-rate',
      startRate: 50, timeUnit: '1s',
      preAllocatedVUs: 100, maxVUs: 500,
      stages: [
        { target: 100, duration: '30s' },   // warm-up
        { target: 300, duration: '1m' },
        { target: 600, duration: '1m' },
        { target: 600, duration: '2m' },    // steady state: report THIS window
        { target: 0,   duration: '15s' },
      ],
      exec: 'newPayment',
    },
    // Retry storm: same key hammered concurrently -> tests idempotency under load.
    duplicates: {
      executor: 'constant-arrival-rate',
      rate: 20, timeUnit: '1s', duration: '2m',
      preAllocatedVUs: 20, maxVUs: 50,
      exec: 'duplicatePayment',
      startTime: '30s',
    },
  },
  thresholds: {
    'http_req_duration{scenario:new_payments}': ['p(95)<200', 'p(99)<500'],
    accepted_202: ['rate>0.99'],
    duplicate_not_5xx: ['rate>0.99'],       // concurrent duplicates resolve to 202 replays
  },
};

const headers = (key) => ({ 'Content-Type': 'application/json', 'Idempotency-Key': key });
const body = () => JSON.stringify({
  sourceAccount: `ACC-${Math.floor(Math.random() * 1e6)}`,
  destinationAccount: `ACC-${Math.floor(Math.random() * 1e6)}`,
  amount: (Math.random() * 1000 + 1).toFixed(2),
});

export function newPayment() {
  const key = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const res = http.post(`${BASE_URL}/api/v1/payments`, body(), { headers: headers(key) });
  acceptLatency.add(res.timings.duration);
  accepted.add(res.status === 202);
  check(res, { 'status is 202': (r) => r.status === 202 });
}

export function duplicatePayment() {
  const key = `k6-dup-${Math.floor(Date.now() / 5000)}`;   // same key for 5s windows
  const res = http.post(`${BASE_URL}/api/v1/payments`, body(), { headers: headers(key) });
  dupHandled.add(res.status < 500);
}
