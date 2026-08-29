const assert = require('node:assert/strict');
const fs = require('node:fs');
const test = require('node:test');
const vm = require('node:vm');

const script = fs.readFileSync('src/main/resources/static/js/invitation-media.js', 'utf8');

class Element {
    constructor(dataset = {}) {
        this.attributes = new Map();
        this.classList = new Set();
        this.dataset = dataset;
        this.listeners = new Map();
        this.paused = true;
        this.textContent = '';
    }

    addEventListener(type, listener) {
        const listeners = this.listeners.get(type) || [];
        listeners.push(listener);
        this.listeners.set(type, listeners);
    }

    fire(type, values = {}) {
        const event = { key: undefined, preventDefault() { this.defaultPrevented = true; }, ...values };
        for (const listener of this.listeners.get(type) || []) listener(event);
        return event;
    }

    setAttribute(name, value) {
        if (this.beforeSetAttribute) this.beforeSetAttribute(name);
        this.attributes.set(name, String(value));
    }
    getAttribute(name) { return this.attributes.get(name); }
    removeAttribute(name) { this.attributes.delete(name); }
    focus(options) { this.focused = true; this.focusOptions = options; }
    scrollIntoView(options) { this.scrollOptions = options; }
    showModal() { this.open = true; }
    close() {
        this.open = false;
        this.fire('close');
    }
}

function fixture(overrides = {}) {
    const elements = {
        '#invitation': new Element(),
        '#open-invitation': new Element(),
        '#welcome': new Element(),
        '#background-audio': null,
        '#audio-toggle': null,
        '#gallery-dialog': null,
        '#gallery-image': null,
        '#gallery-caption': null,
        '#gallery-previous': null,
        '#gallery-next': null,
        '#gallery-close': null,
        '#preview-form': null,
        '#language': null,
        ...overrides.elements
    };
    const documentElement = { classList: new Set() };
    const document = new Element();
    Object.assign(document, {
        documentElement,
        querySelector: selector => elements[selector],
        querySelectorAll: selector => selector === '[data-gallery-index]' ? (overrides.thumbnails || []) : []
    });
    const context = { document, Promise };
    vm.runInNewContext(script, context);
    return { elements, document, documentElement };
}

test('opening remains usable when audio playback is rejected', async () => {
    const audio = new Element();
    let playCalls = 0;
    audio.play = () => {
        playCalls++;
        return Promise.reject(new Error('blocked'));
    };
    const toggle = new Element({ playLabel: 'Play music', pauseLabel: 'Pause music' });
    const { elements, documentElement } = fixture({
        elements: { '#background-audio': audio, '#audio-toggle': toggle }
    });

    elements['#open-invitation'].fire('click');
    await new Promise(resolve => setImmediate(resolve));

    assert.equal(playCalls, 1);
    assert.equal(elements['#invitation'].classList.has('is-open'), true);
    assert.equal(elements['#open-invitation'].getAttribute('aria-expanded'), 'true');
    assert.equal(elements['#invitation'].focused, undefined);
    assert.equal(elements['#welcome'].scrollOptions.behavior, 'smooth');
    assert.equal(elements['#welcome'].focusOptions.preventScroll, true);
	assert.equal(toggle.textContent, '');
	assert.equal(toggle.getAttribute('aria-label'), 'Play music');
    assert.equal(toggle.getAttribute('aria-pressed'), 'false');
    assert.equal(documentElement.classList.has('js'), true);

    audio.fire('play');
	assert.equal(toggle.textContent, '');
	assert.equal(toggle.getAttribute('aria-label'), 'Pause music');
    assert.equal(toggle.getAttribute('aria-pressed'), 'true');
    for (const event of ['pause', 'ended', 'error']) {
        audio.fire(event);
		assert.equal(toggle.textContent, '');
		assert.equal(toggle.getAttribute('aria-label'), 'Play music');
        assert.equal(toggle.getAttribute('aria-pressed'), 'false');
    }
});

test('hidden page pauses playing audio without resuming it', () => {
    const audio = new Element();
    audio.paused = false;
    let pauseCalls = 0;
    audio.pause = () => { pauseCalls++; audio.paused = true; };
    const toggle = new Element({ playLabel: 'Play music', pauseLabel: 'Pause music' });
    const { document } = fixture({
        elements: { '#background-audio': audio, '#audio-toggle': toggle }
    });

    document.hidden = true;
    document.fire('visibilitychange');
    document.hidden = false;
    document.fire('visibilitychange');

    assert.equal(pauseCalls, 1);
});

test('gallery loads on open, supports keys, and restores thumbnail focus', () => {
    const dialog = new Element();
    const image = new Element();
    const caption = new Element();
    const previous = new Element();
    const next = new Element();
    const close = new Element();
    const first = new Element({ imageUrl: '/image/1', alt: 'First alt', caption: 'First caption' });
    const second = new Element({ imageUrl: '/image/2', alt: 'Second alt', caption: '' });
    image.beforeSetAttribute = name => {
        if (name === 'src') assert.equal(dialog.open, true, 'full image must load after dialog opens');
    };
    fixture({
        elements: {
            '#gallery-dialog': dialog,
            '#gallery-image': image,
            '#gallery-caption': caption,
            '#gallery-previous': previous,
            '#gallery-next': next,
            '#gallery-close': close
        },
        thumbnails: [first, second]
    });

    assert.equal(image.getAttribute('src'), undefined);
    first.fire('click');
    assert.equal(image.getAttribute('src'), '/image/1');
    assert.equal(image.getAttribute('alt'), 'First alt');
    assert.equal(caption.textContent, 'First caption');
    assert.equal(dialog.open, true);
    assert.equal(close.focused, true);

    next.fire('click');
    assert.equal(image.getAttribute('src'), '/image/2');
    previous.fire('click');
    assert.equal(image.getAttribute('src'), '/image/1');

    dialog.fire('keydown', { key: 'ArrowRight' });
    assert.equal(image.getAttribute('src'), '/image/2');
    assert.equal(caption.hidden, true);
    dialog.fire('keydown', { key: 'ArrowLeft' });
    assert.equal(image.getAttribute('src'), '/image/1');

    dialog.fire('pointerdown', {
        pointerId: 1, clientX: 100, clientY: 0,
        target: { closest: selector => selector === '.gallery-controls' ? close : null }
    });
    dialog.fire('pointerup', { pointerId: 1, clientX: 0, clientY: 0 });
    assert.equal(image.getAttribute('src'), '/image/1');

    dialog.fire('pointerdown', {
        pointerId: 2, clientX: 100, clientY: 0, target: { closest: () => null }
    });
    dialog.fire('pointerup', { pointerId: 2, clientX: 0, clientY: 0 });
    assert.equal(image.getAttribute('src'), '/image/2');

    const escape = dialog.fire('keydown', { key: 'Escape' });
    assert.equal(escape.defaultPrevented, true);
    assert.equal(dialog.open, false);
    assert.equal(image.getAttribute('src'), undefined);
    assert.equal(first.focused, true);
});

test('preview language change submits without browser storage', () => {
    const form = new Element();
    form.requestSubmit = () => { form.submitted = true; };
    const language = new Element();
    let storageRead = false;
    const elements = { '#preview-form': form, '#language': language };
    const document = new Element();
    Object.assign(document, {
        documentElement: { classList: new Set() },
        querySelector: selector => elements[selector] || null,
        querySelectorAll: () => []
    });
    const context = { document, Promise };
    Object.defineProperty(context, 'localStorage', {
        get() { storageRead = true; throw new Error('storage must not be used'); }
    });

    vm.runInNewContext(script, context);
    language.fire('change');

    assert.equal(form.submitted, true);
    assert.equal(storageRead, false);
});
