(() => {
  const invitation = document.querySelector('#invitation');
  const open = document.querySelector('#open-invitation');
  if (invitation && open) {
    document.documentElement.classList.add('js');
    open.addEventListener('click', () => {
      invitation.classList.add('is-open');
      open.setAttribute('aria-expanded', 'true');
      invitation.focus();
    });
  }

  const form = document.querySelector('#preview-form');
  const language = document.querySelector('#language');
  if (!form || !language) return;
  language.addEventListener('change', () => {
    try {
      localStorage.setItem('wedding-preview-language', language.value);
    } catch (_) {
      // Browser storage is optional for preview.
    }
    form.requestSubmit();
  });

  let saved;
  try {
    saved = localStorage.getItem('wedding-preview-language');
  } catch (_) {
    // Browser storage is optional for preview.
  }
  if ((saved === 'ID' || saved === 'EN') && saved !== language.value) {
    language.value = saved;
    form.requestSubmit();
  }
})();
