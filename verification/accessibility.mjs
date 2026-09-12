import fs from 'node:fs/promises';
import { createRequire } from 'node:module';
import { chromium } from 'playwright';

const require = createRequire(import.meta.url);
const required = name => process.env[name] || (() => { throw new Error(`${name} is required`); })();
if (required('VERIFY_FIXTURE_KIND') !== 'synthetic') throw new Error('Only a synthetic fixture is allowed');

const baseUrl = required('VERIFY_BASE_URL').replace(/\/$/, '');
const output = process.env.VERIFY_REPORT || 'verification-results/accessibility.json';
const browser = await chromium.launch({ headless: true });
const findings = [];

async function scan(page, name) {
  await page.addScriptTag({ path: require.resolve('axe-core/axe.min.js') });
  const result = await page.evaluate(() => globalThis.axe.run(document, {
    resultTypes: ['violations'],
    rules: {}
  }));
  findings.push({
    surface: name,
    violations: result.violations.map(({ id, impact, help, nodes }) => ({
      id, impact, help, nodes: nodes.map(({ target, failureSummary }) => ({ target, failureSummary }))
    }))
  });
}

async function login(page, username, password) {
  await page.goto(`${baseUrl}/login`);
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await Promise.all([page.waitForLoadState('networkidle'), page.locator('button[type=submit]').click()]);
}

try {
  const guest = await browser.newPage({ bypassCSP: true });
  await guest.goto(required('VERIFY_GUEST_URL'));
  await scan(guest, 'guest');

  const loginPage = await browser.newPage({ bypassCSP: true });
  await loginPage.goto(`${baseUrl}/login`);
  await scan(loginPage, 'login');

  const admin = await browser.newPage({ bypassCSP: true });
  await login(admin, required('VERIFY_ADMIN_USERNAME'), required('VERIFY_ADMIN_PASSWORD'));
  await admin.goto(`${baseUrl}/admin`);
  await scan(admin, 'admin');

  const staff = await browser.newPage({ bypassCSP: true });
  await login(staff, required('VERIFY_STAFF_USERNAME'), required('VERIFY_STAFF_PASSWORD'));
  await staff.goto(`${baseUrl}/check-in`);
  await scan(staff, 'check-in');
} finally {
  await browser.close();
}

const redact = value => JSON.stringify(value, null, 2).replaceAll(baseUrl, '[base-url]');
await fs.mkdir(new URL('.', `file://${process.cwd()}/${output}`).pathname, { recursive: true });
await fs.writeFile(output, redact(findings));
const blockers = findings.flatMap(result => result.violations)
  .filter(({ impact }) => impact === 'serious' || impact === 'critical');
if (blockers.length) throw new Error(`${blockers.length} serious/critical accessibility findings`);
