// A cold ChromeHeadless during a full parallel build (iOS framework linking, production webpack)
// misses the Karma and Mocha defaults: the browser is not captured within 60 s, or the first
// test in the bundle overruns Mocha's 2 s. Both are start-up delays, not slow tests; 10 s per
// test still fails a test that genuinely hangs.
config.captureTimeout = 180000;
config.browserNoActivityTimeout = 180000;
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 10000 });
