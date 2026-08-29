const assert = require('node:assert/strict');
const fs = require('node:fs');
const test = require('node:test');
const vm = require('node:vm');

const script = fs.readFileSync('src/main/resources/static/js/admin-navigation.js', 'utf8');

test('administrator navigation follows desktop state without overriding mobile interaction', () => {
    const navigation = { open: false };
    const media = {
        matches: true,
        addEventListener(type, listener) { this.listener = listener; }
    };
    const context = {
        document: { querySelector: () => navigation },
        matchMedia: () => media
    };
    context.globalThis = context;

    vm.runInNewContext(script, context);
    assert.equal(navigation.open, true);

    media.matches = false;
    media.listener();
    assert.equal(navigation.open, false);

    navigation.open = true;
    assert.equal(navigation.open, true);
});
