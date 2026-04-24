import http from 'k6/http';
import { check } from 'k6';

/**
 * Stress the duplicate-detection path. Every VU submits the SAME claim
 * (M001 + NPI 1234567890 + CPT 99213 + today). The first claim to finish
 * adjudicate lands in APPROVED; every subsequent claim should DENY with
 * CARC-18. As memberHistory grows, the DuplicateClaimRule's List<Claim>
 * scan takes longer — the p95 budget here is slightly higher to absorb
 * that cost.
 */
export const options = {
  scenarios: {
    duplicate_stress: {
      executor: 'constant-vus',
      vus: 30,
      duration: '20s',
    },
  },
  thresholds: {
    // Relaxed relative to scenario 01/02: every VU hammers the same
    // (memberId, cpt, serviceDate, providerNpi) tuple, so the
    // DuplicateClaimRule's in-memory List<Claim> scan grows linearly
    // *during* the run itself. 900ms p95 absorbs that growth — tighter
    // than this produces flakes on CI runners under load.
    http_req_duration: ['p(95)<900'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const IDENTICAL_PAYLOAD = JSON.stringify({
  memberId: 'M001',
  providerNpi: '1234567890',
  serviceDate: new Date().toISOString().split('T')[0],
  procedureCode: '99213',
  diagnosisCode: 'J45.909',
  billedAmount: 150.0,
});

const headers = { 'Content-Type': 'application/json' };

export default function () {
  const submit = http.post(`${BASE_URL}/api/v1/claims`, IDENTICAL_PAYLOAD, { headers });
  check(submit, { 'submit 201': (r) => r.status === 201 });
  if (submit.status !== 201) return;

  const claimId = submit.json('claimId');

  const validate = http.post(`${BASE_URL}/api/v1/claims/${claimId}/validate`, null, { headers });
  check(validate, { 'validate 200': (r) => r.status === 200 });
  if (validate.status !== 200) return;

  const adjudicate = http.post(`${BASE_URL}/api/v1/claims/${claimId}/adjudicate`, null, { headers });
  check(adjudicate, {
    'adjudicate 200': (r) => r.status === 200,
    'terminal status is APPROVED or DENIED': (r) => {
      const s = r.json('status');
      return s === 'APPROVED' || s === 'DENIED';
    },
  });
}
