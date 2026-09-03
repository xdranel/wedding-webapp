import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const fixtures = JSON.parse(open(__ENV.STAFF_FIXTURES));
const guestSearchDuration = new Trend('guest_search_duration');
const confirmedCheckInDuration = new Trend('confirmed_check_in_duration');

export const options = {
  scenarios: { staff: { executor: 'per-vu-iterations', vus: 5, iterations: 1, maxDuration: '30s' } },
  thresholds: {
    http_req_failed: ['rate==0'],
    guest_search_duration: ['p(95)<=1000'],
    confirmed_check_in_duration: ['p(95)<=1000']
  }
};

export default function () {
  const fixture = fixtures[__VU - 1];
  if (!fixture) throw new Error(`Missing distinct fixture for VU ${__VU}`);
  const headers = { Cookie: fixture.cookie };

  const search = http.get(`${fixture.baseUrl}/check-in/search?q=${encodeURIComponent(fixture.search)}`, { headers });
  guestSearchDuration.add(search.timings.duration);
  check(search, { 'guest search is 200': result => result.status === 200 });

  const preview = http.get(`${fixture.baseUrl}/check-in/preview/guest/${fixture.guestId}`, {
    headers, tags: { journey: 'preview check-in' }
  });
  check(preview, { 'preview check-in is 200': result => result.status === 200 });

  const confirmed = http.post(`${fixture.baseUrl}/check-in/confirm/guest/${fixture.guestId}`, {
    _csrf: fixture.csrf,
    guestVersion: fixture.guestVersion,
    actualCount: fixture.actualCount || 1,
    acceptRsvpChange: true
  }, { headers, redirects: 0, tags: { journey: 'confirm check-in' } });
  confirmedCheckInDuration.add(confirmed.timings.duration);
  check(confirmed, { 'confirm check-in redirects': result => result.status === 302 });
}
