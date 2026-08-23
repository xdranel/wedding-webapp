# Penerimaan Manual Phase 6D

Status: **diterima pada 2026-08-23**. Pemilik telah mengonfirmasi seluruh
pemeriksaan yang tersedia; kotak di bawah mencatat hasil nyata, bukan inferensi
dari pengujian otomatis. Bukti otomatis final: 2 pemeriksaan sintaks JavaScript
terlacak dan 78 suite / 431 test dengan 0 kegagalan, error, atau skip pada
MySQL/Flyway V1-V13.

## Persiapan

- [x] Siapkan server pusat, LAN venue, dua akun staf berbeda, tamu uji ID/EN
  termasuk `+1`, konten terbit, RSVP masa depan, dan kalender/media bila diuji.
  **Hasil yang diharapkan:** semua perangkat dapat mencapai server pusat lewat
  LAN dan akun staf dapat masuk setelah mengganti kata sandi sementara.
- [x] Siapkan CSV lengkap dan daftar cetak sebelum acara.
  **Hasil yang diharapkan:** keduanya tersedia sebagai fallback bila LAN/server
  tidak dapat dijangkau.

## Chrome/Chromium — perjalanan lengkap

- [x] Buka undangan ID dan EN, ganti bahasa, isi **Hadir** dengan PIN dan
  jumlah `+1`, lalu lihat/unduh QR.
  **Hasil yang diharapkan:** bahasa tetap benar, RSVP tersimpan, dan QR hanya
  tersedia setelah **Hadir**.
- [x] Buka kalender bila diaktifkan, kendalikan foto/audio bila tersedia, lalu
  ubah RSVP sebelum tenggat.
  **Hasil yang diharapkan:** kalender dapat diunduh, media tetap dapat dipakai,
  dan perubahan RSVP tersimpan sebelum tenggat.
- [x] Konfirmasi pengiriman undangan dan pengingat yang memenuhi syarat.
  **Hasil yang diharapkan:** membuka WhatsApp tidak mencatat pengiriman;
  **Confirm sent** yang mencatatnya.

## Firefox — smoke

- [x] Buka undangan, lihat RSVP/QR untuk tamu Hadir, dan buka halaman staf.
  **Hasil yang diharapkan:** halaman utama dan tindakan inti tampil tanpa
  kesalahan; tidak perlu mengulang seluruh perjalanan Chrome.

## iPhone Safari

- [x] Buka undangan, ganti bahasa, simpan RSVP, dan gunakan media bila ada.
  **Hasil yang diharapkan:** undangan dan kontrol media dapat digunakan pada
  Safari.
- [x] Unduh kalender dan mulai kamera pada halaman check-in HTTPS.
  **Hasil yang diharapkan:** kalender dapat diimpor dan kamera meminta izin
  serta dapat membaca QR; pada HTTP kamera gagal dengan fallback manual.

## Konflik check-in dua akun

- [x] Dengan dua perangkat/akun staf, pratinjau lalu konfirmasi tamu yang sama
  pada waktu hampir bersamaan.
  **Hasil yang diharapkan:** satu konfirmasi menang dan yang lain menunjukkan
  duplikat; hanya satu check-in pusat tercatat.
- [x] Koreksi dan batalkan check-in dari administrator dengan alasan.
  **Hasil yang diharapkan:** jumlah saat ini benar dan riwayat koreksi tidak
  hilang.

## WAN mati, LAN hidup

- [x] Putuskan WAN tanpa memutus LAN ke server pusat, lalu lakukan pencarian
  manual dan konfirmasi check-in.
  **Hasil yang diharapkan:** check-in tersimpan langsung di server pusat. Tidak
  ada antrean offline atau sinkronisasi belakangan.

## Tutup dan buka kembali

- [x] Simpan salinan pesan selesai, tutup acara, dan periksa halaman tamu ID/EN
  serta tindakan terblokir.
  **Hasil yang diharapkan:** halaman tamu netral; RSVP, QR/kalender, pengiriman,
  pengingat, dan check-in terblokir, sedangkan baca admin tetap tersedia.
- [x] Buka kembali acara dan periksa data yang sama.
  **Hasil yang diharapkan:** aturan normal kembali tanpa perubahan diam-diam
  pada token, RSVP, pengiriman, media, atau check-in.

## Laporan, CSV, dan cetak

- [x] Bandingkan total laporan dan kategori, filter kategori, lalu Print/Save
  as PDF dan ekspor CSV lengkap.
  **Hasil yang diharapkan:** angka keadaan saat ini konsisten; cetak tidak
  menampilkan data privat dan CSV lengkap dapat diunduh.

## Perbandingan integritas akhir

- [x] Bandingkan daftar tamu, RSVP, pengiriman/pengingat, check-in, laporan,
  CSV, dan riwayat koreksi setelah seluruh skenario.
  **Hasil yang diharapkan:** semua tampilan menunjukkan satu keadaan pusat yang
  konsisten; catat setiap selisih sebelum menerima Phase 6D.

## Perangkat keras yang ditunda

- [ ] Uji pemindai USB fisik secara terpisah.
  **Hasil yang diharapkan:** perangkat bertindak sebagai input keyboard,
  menghasilkan pratinjau, dan tetap memerlukan konfirmasi. Ini tetap pengingat
  Phase 5 yang ditunda dan tidak memblokir penerimaan Phase 6D.
