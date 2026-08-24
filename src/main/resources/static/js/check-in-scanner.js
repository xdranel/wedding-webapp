import QrScanner from '/webjars/qr-scanner/1.4.2/qr-scanner.min.js';

const form = document.querySelector('#qr-preview-form');
const input = document.querySelector('#scanner-input');
const startButton = document.querySelector('#scanner-start');
const stopButton = document.querySelector('#scanner-stop');
const status = document.querySelector('#scanner-status');
const video = document.querySelector('#scanner-video');
const tabs = [...document.querySelectorAll('[role="tab"][aria-controls]')];
let scanner;
let scanning = false;

function activateTab(activeTab, moveFocus = false) {
  tabs.forEach((tab) => {
    const selected = tab === activeTab;
    tab.setAttribute('aria-selected', selected.toString());
    tab.tabIndex = selected ? 0 : -1;
    document.querySelector(`#${tab.getAttribute('aria-controls')}`).hidden = !selected;
  });
  if (moveFocus) activeTab.focus();
}

const selectedTab = tabs.find((tab) => tab.getAttribute('aria-selected') === 'true') ?? tabs[0];
if (selectedTab) {
  activateTab(selectedTab);
  tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => activateTab(tab));
    tab.addEventListener('keydown', (event) => {
      let nextIndex;
      if (event.key === 'ArrowRight') nextIndex = (index + 1) % tabs.length;
      if (event.key === 'ArrowLeft') nextIndex = (index - 1 + tabs.length) % tabs.length;
      if (event.key === 'Home') nextIndex = 0;
      if (event.key === 'End') nextIndex = tabs.length - 1;
      if (nextIndex === undefined) return;
      event.preventDefault();
      activateTab(tabs[nextIndex], true);
    });
  });
}

if (!window.isSecureContext) {
  startButton.disabled = true;
  status.textContent = 'Camera scanning needs HTTPS. Use a USB scanner or manual search.';
  status.classList.add('status-error');
}

function stop() {
  scanning = false;
  scanner?.stop();
  video.srcObject?.getTracks().forEach((track) => track.stop());
  video.srcObject = null;
  video.hidden = true;
  startButton.disabled = false;
  stopButton.disabled = true;
}

function fallback(message) {
  stop();
  status.textContent = `${message} Use a USB scanner or manual search.`;
  status.classList.add('status-error');
  input.focus();
}

async function start() {
  if (!window.isSecureContext) {
    fallback('Camera scanning needs HTTPS.');
    startButton.disabled = true;
    return;
  }

  try {
    scanning = true;
    startButton.disabled = true;
    stopButton.disabled = false;
    video.hidden = false;
    scanner ??= new QrScanner(video, (result) => {
      if (!scanning) return;
      stop();
      input.value = result.data;
      status.textContent = 'Invitation scanned. Opening preview.';
      form.requestSubmit();
    }, { preferredCamera: 'environment' });
    await scanner.start();
    if (scanning) {
      status.classList.remove('status-error');
      status.textContent = 'Camera scanning is active.';
    }
  } catch (error) {
    fallback('Camera access is unavailable.');
  }
}

startButton.addEventListener('click', start);
stopButton.addEventListener('click', () => {
  stop();
  status.classList.remove('status-error');
  status.textContent = 'Camera stopped. Use a USB scanner or manual search.';
});
window.addEventListener('pagehide', stop);
