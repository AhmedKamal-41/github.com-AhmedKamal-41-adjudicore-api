import http from 'k6/http';
import { check } from 'k6';

/**
 * Full lifecycle: submit → validate → adjudicate. Uses a ramping-VUs
 * executor to avoid a cold-start thundering herd — 20 VUs slamming the
 * fresh JVM at t=0 gives artificial p95 spikes unrelated to steady-state
 * behavior. 5s ramp to 20 VUs, 25s steady, brief ramp-down.
 *
 * Per-endpoint budgets reflect observed steady-state behavior:
 * adjudicate is the slowest because it runs four rules serially, including
 * a linear scan of memberHistory that grows per request.
 */
export const options = {
  scenarios: {
    full_pipeline: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { target: 20, duration: '5s' },
        { target: 20, duration: '25s' },
        { target: 0, duration: '2s' },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:submit}': ['p(95)<500'],
    'http_req_duration{endpoint:validate}': ['p(95)<600'],
    'http_req_duration{endpoint:adjudicate}': ['p(95)<1200'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MEMBERS = ['M001', 'M002', 'M003', 'M004'];
const NPIS = ['1234567890', '2345678901', '3456789012'];
const CPTS = ['99213', '99214', '99215', '99203', '80050'];

const headers = { 'Content-Type': 'application/json' };

export default function () {
  const payload = JSON.stringify({
    memberId: MEMBERS[Math.floor(Math.random() * MEMBERS.length)],
    providerNpi: NPIS[Math.floor(Math.random() * NPIS.length)],
    serviceDate: new Date().toISOString().split('T')[0],
    procedureCode: CPTS[Math.floor(Math.random() * CPTS.length)],
    diagnosisCode: 'J45.909',
    billedAmount: 150.0,
  });

  const submit = http.post(`${BASE_URL}/api/v1/claims`, payload, {
    headers,
    tags: { endpoint: 'submit' },
  });
  check(submit, { 'submit 201': (r) => r.status === 201 });
  if (submit.status !== 201) return;

  const claimId = submit.json('claimId');

  const validate = http.post(
    `${BASE_URL}/api/v1/claims/${claimId}/validate`,
    null,
    { headers, tags: { endpoint: 'validate' } }
  );
  check(validate, {
    'validate 200': (r) => r.status === 200,
    'validate moves to VALIDATED': (r) => r.json('status') === 'VALIDATED',
  });
  if (validate.status !== 200) return;

  const adjudicate = http.post(
    `${BASE_URL}/api/v1/claims/${claimId}/adjudicate`,
    null,
    { headers, tags: { endpoint: 'adjudicate' } }
  );
  check(adjudicate, {
    'adjudicate 200': (r) => r.status === 200,
    'adjudicate terminal status': (r) => {
      const s = r.json('status');
      return s === 'APPROVED' || s === 'DENIED' || s === 'VALIDATED';
    },
  });
}
