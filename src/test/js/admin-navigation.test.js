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
        document: { querySelector: () => navigation, addEventListener() {} },
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

test('data-confirm cancels submission when the operator declines', () => {
    const navigation = { open: false };
    const listeners = {};
    const context = {
        document: {
            querySelector: () => navigation,
            addEventListener: (type, listener) => listeners[type] = listener
        },
        matchMedia: () => ({ matches: false, addEventListener() {} }),
        confirm: () => false
    };
    context.globalThis = context;

    vm.runInNewContext(script, context);
    let prevented = false;
    listeners.submit({
        target: { dataset: { confirm: 'Delete this guest?' } },
        preventDefault: () => prevented = true
    });

    assert.equal(prevented, true);
});
