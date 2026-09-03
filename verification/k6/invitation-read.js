import http from 'k6/http';
import { check } from 'k6';

const invitations = JSON.parse(open(__ENV.INVITATION_FIXTURES));

export const options = {
  scenarios: { readers: { executor: 'constant-vus', vus: 100, duration: '30s' } },
  thresholds: { http_req_failed: ['rate==0'], http_req_duration: ['p(95)<=2000'] }
};

export default function () {
  const url = invitations[(__VU - 1) % invitations.length];
  const response = http.get(url, { tags: { journey: 'GET invitation' } });
  check(response, { 'GET invitation is 200': result => result.status === 200 });
}
