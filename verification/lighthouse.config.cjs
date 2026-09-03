module.exports = {
  extends: 'lighthouse:default',
  settings: {
    formFactor: 'mobile',
    throttlingMethod: 'simulate',
    throttling: { rttMs: 150, throughputKbps: 1638.4, cpuSlowdownMultiplier: 4 },
    screenEmulation: { mobile: true, width: 412, height: 823, deviceScaleFactor: 1.75 },
    blockedUrlPatterns: ['*.mp3', '*.m4a', '*.ogg', '*.wav'] // audio is outside the measured invitation journey
  },
  assertions: {
    'categories:performance': ['error', { minScore: 0.80 }],
    'categories:accessibility': ['error', { minScore: 0.90 }],
    'categories:best-practices': ['error', { minScore: 0.90 }]
  }
};
