(() => {
    const invitation = document.querySelector('#invitation');
    const open = document.querySelector('#open-invitation');
    const audio = document.querySelector('#background-audio');
    const audioToggle = document.querySelector('#audio-toggle');

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
            open.setAttribute('aria-expanded', 'true');
            invitation.focus();
            if (audio) playAudio();
        });
    }

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
