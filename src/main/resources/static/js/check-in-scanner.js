import QrScanner from '/webjars/qr-scanner/1.4.2/qr-scanner.min.js';

const form = document.querySelector('#qr-preview-form');
const input = document.querySelector('#scanner-input');
const startButton = document.querySelector('#scanner-start');
const stopButton = document.querySelector('#scanner-stop');
const status = document.querySelector('#scanner-status');
const video = document.querySelector('#scanner-video');
let scanner;
let scanning = false;

if (!window.isSecureContext) {
  startButton.disabled = true;
  status.textContent = 'Camera scanning needs HTTPS. Use a USB scanner or manual search.';
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
    if (scanning) status.textContent = 'Camera scanning is active.';
  } catch (error) {
    fallback('Camera access is unavailable.');
  }
}

startButton.addEventListener('click', start);
stopButton.addEventListener('click', () => {
  stop();
  status.textContent = 'Camera stopped. Use a USB scanner or manual search.';
});
window.addEventListener('pagehide', stop);
