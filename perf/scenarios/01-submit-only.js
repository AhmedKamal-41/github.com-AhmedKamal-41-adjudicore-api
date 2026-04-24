import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    submit_sustained: {
      executor: 'constant-vus',
      vus: 50,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
// Members M001–M004 are active in the V5 seed; M005 is intentionally expired.
const MEMBERS = ['M001', 'M002', 'M003', 'M004'];
// NPIs present in V5 seed data.
const NPIS = ['1234567890', '2345678901', '3456789012'];
const CPTS = ['99213', '99214', '99215', '99203', '80050'];

export default function () {
  const payload = JSON.stringify({
    memberId: MEMBERS[Math.floor(Math.random() * MEMBERS.length)],
    providerNpi: NPIS[Math.floor(Math.random() * NPIS.length)],
    serviceDate: new Date().toISOString().split('T')[0],
    procedureCode: CPTS[Math.floor(Math.random() * CPTS.length)],
    diagnosisCode: 'J45.909',
    billedAmount: 150.0,
  });

  const res = http.post(`${BASE_URL}/api/v1/claims`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'status 201': (r) => r.status === 201,
    'has claimId': (r) => r.json('claimId') !== undefined,
  });

  sleep(0.1);
}
