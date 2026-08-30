(() => {
    document.addEventListener('submit', event => {
        const message = event.target.dataset?.confirm;
        if (message && !globalThis.confirm(message)) event.preventDefault();
    });

    const navigation = document.querySelector('.app-navigation');
    if (!navigation) return;
    const desktop = globalThis.matchMedia('(min-width: 48.001rem)');
    const sync = () => navigation.open = desktop.matches;
    sync();
    desktop.addEventListener?.('change', sync);
})();
