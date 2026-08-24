(() => {
    const invitation = document.querySelector('#invitation');
    const open = document.querySelector('#open-invitation');
    const audio = document.querySelector('#background-audio');
    const audioToggle = document.querySelector('#audio-toggle');
    const reducedMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

    const updateAudioLabel = playing => {
        if (!audioToggle) return;
        audioToggle.textContent = playing ? audioToggle.dataset.pauseLabel : audioToggle.dataset.playLabel;
        audioToggle.setAttribute('aria-pressed', String(playing));
    };
    const playAudio = () => audio.play().catch(() => updateAudioLabel(false));

    if (invitation && open) {
        document.documentElement.classList.add('js');
        open.addEventListener('click', () => {
            invitation.classList.add('is-open');
            document.body?.classList.add('invitation-open');
            open.setAttribute('aria-expanded', 'true');
            invitation.focus();
            if (audio) playAudio();
        });
    }

    document.querySelectorAll('[data-scroll-target]').forEach(link => {
        link.addEventListener('click', () => {
            document.getElementById(link.dataset.scrollTarget)?.scrollIntoView({
                behavior: reducedMotion ? 'auto' : 'smooth'
            });
        });
    });

    if (audio && audioToggle) {
        updateAudioLabel(false);
        audioToggle.addEventListener('click', () => audio.paused ? playAudio() : audio.pause());
        audio.addEventListener('play', () => updateAudioLabel(true));
        audio.addEventListener('pause', () => updateAudioLabel(false));
        audio.addEventListener('ended', () => updateAudioLabel(false));
        audio.addEventListener('error', () => updateAudioLabel(false));
    }

    const dialog = document.querySelector('#gallery-dialog');
    const image = document.querySelector('#gallery-image');
    const caption = document.querySelector('#gallery-caption');
    const previous = document.querySelector('#gallery-previous');
    const next = document.querySelector('#gallery-next');
    const close = document.querySelector('#gallery-close');
    const thumbnails = [...document.querySelectorAll('[data-gallery-index]')];
    if (dialog && image && caption && previous && next && close && thumbnails.length) {
        let current = 0;
        let opener;
        let pointerStart;
        const show = index => {
            current = (index + thumbnails.length) % thumbnails.length;
            const thumbnail = thumbnails[current];
            image.setAttribute('src', thumbnail.dataset.imageUrl);
            image.setAttribute('alt', thumbnail.dataset.alt);
            caption.textContent = thumbnail.dataset.caption;
            caption.hidden = !thumbnail.dataset.caption;
        };
        thumbnails.forEach((thumbnail, index) => thumbnail.addEventListener('click', () => {
            opener = thumbnail;
            dialog.showModal();
            show(index);
            close.focus();
        }));
        previous.disabled = thumbnails.length < 2;
        next.disabled = thumbnails.length < 2;
        previous.addEventListener('click', () => show(current - 1));
        next.addEventListener('click', () => show(current + 1));
        close.addEventListener('click', () => dialog.close());
        dialog.addEventListener('pointerdown', event => {
            if (event.isPrimary === false) return;
            pointerStart = { id: event.pointerId, x: event.clientX, y: event.clientY };
            dialog.setPointerCapture?.(event.pointerId);
        });
        dialog.addEventListener('pointerup', event => {
            if (!pointerStart || event.pointerId !== pointerStart.id) return;
            const x = event.clientX - pointerStart.x;
            const y = event.clientY - pointerStart.y;
            pointerStart = undefined;
            dialog.releasePointerCapture?.(event.pointerId);
            if (Math.abs(x) > 48 && Math.abs(x) > Math.abs(y)) show(current + (x < 0 ? 1 : -1));
        });
        dialog.addEventListener('pointercancel', () => pointerStart = undefined);
        dialog.addEventListener('keydown', event => {
            if (event.key === 'ArrowLeft') {
                event.preventDefault();
                show(current - 1);
            } else if (event.key === 'ArrowRight') {
                event.preventDefault();
                show(current + 1);
            } else if (event.key === 'Escape') {
                event.preventDefault();
                dialog.close();
            }
        });
        dialog.addEventListener('close', () => {
            image.removeAttribute('src');
            opener.focus();
        });
    }

    const previewForm = document.querySelector('#preview-form');
    const language = document.querySelector('#language');
    if (previewForm && language) language.addEventListener('change', () => previewForm.requestSubmit());
})();
