# Penerimaan Manual Phase 6D

Status: **belum selesai**. Pemilik menjalankan setiap pemeriksaan dan mengisi
hasil nyata; jangan menandai kotak berdasarkan pengujian otomatis.

## Persiapan

- [ ] Siapkan server pusat, LAN venue, dua akun staf berbeda, tamu uji ID/EN
  termasuk `+1`, konten terbit, RSVP masa depan, dan kalender/media bila diuji.
  **Hasil yang diharapkan:** semua perangkat dapat mencapai server pusat lewat
  LAN dan akun staf dapat masuk setelah mengganti kata sandi sementara.
- [ ] Siapkan CSV lengkap dan daftar cetak sebelum acara.
  **Hasil yang diharapkan:** keduanya tersedia sebagai fallback bila LAN/server
  tidak dapat dijangkau.

## Chrome/Chromium — perjalanan lengkap

- [ ] Buka undangan ID dan EN, ganti bahasa, isi **Hadir** dengan PIN dan
  jumlah `+1`, lalu lihat/unduh QR.
  **Hasil yang diharapkan:** bahasa tetap benar, RSVP tersimpan, dan QR hanya
  tersedia setelah **Hadir**.
- [ ] Buka kalender bila diaktifkan, kendalikan foto/audio bila tersedia, lalu
  ubah RSVP sebelum tenggat.
  **Hasil yang diharapkan:** kalender dapat diunduh, media tetap dapat dipakai,
  dan perubahan RSVP tersimpan sebelum tenggat.
- [ ] Konfirmasi pengiriman undangan dan pengingat yang memenuhi syarat.
  **Hasil yang diharapkan:** membuka WhatsApp tidak mencatat pengiriman;
  **Confirm sent** yang mencatatnya.

## Firefox — smoke

- [ ] Buka undangan, lihat RSVP/QR untuk tamu Hadir, dan buka halaman staf.
  **Hasil yang diharapkan:** halaman utama dan tindakan inti tampil tanpa
  kesalahan; tidak perlu mengulang seluruh perjalanan Chrome.

## iPhone Safari

- [ ] Buka undangan, ganti bahasa, simpan RSVP, dan gunakan media bila ada.
  **Hasil yang diharapkan:** undangan dan kontrol media dapat digunakan pada
  Safari.
- [ ] Unduh kalender dan mulai kamera pada halaman check-in HTTPS.
  **Hasil yang diharapkan:** kalender dapat diimpor dan kamera meminta izin
  serta dapat membaca QR; pada HTTP kamera gagal dengan fallback manual.

## Konflik check-in dua akun

- [ ] Dengan dua perangkat/akun staf, pratinjau lalu konfirmasi tamu yang sama
  pada waktu hampir bersamaan.
  **Hasil yang diharapkan:** satu konfirmasi menang dan yang lain menunjukkan
  duplikat; hanya satu check-in pusat tercatat.
- [ ] Koreksi dan batalkan check-in dari administrator dengan alasan.
  **Hasil yang diharapkan:** jumlah saat ini benar dan riwayat koreksi tidak
  hilang.

## WAN mati, LAN hidup

- [ ] Putuskan WAN tanpa memutus LAN ke server pusat, lalu lakukan pencarian
  manual dan konfirmasi check-in.
  **Hasil yang diharapkan:** check-in tersimpan langsung di server pusat. Tidak
  ada antrean offline atau sinkronisasi belakangan.

## Tutup dan buka kembali

- [ ] Simpan salinan pesan selesai, tutup acara, dan periksa halaman tamu ID/EN
  serta tindakan terblokir.
  **Hasil yang diharapkan:** halaman tamu netral; RSVP, QR/kalender, pengiriman,
  pengingat, dan check-in terblokir, sedangkan baca admin tetap tersedia.
- [ ] Buka kembali acara dan periksa data yang sama.
  **Hasil yang diharapkan:** aturan normal kembali tanpa perubahan diam-diam
  pada token, RSVP, pengiriman, media, atau check-in.

## Laporan, CSV, dan cetak

- [ ] Bandingkan total laporan dan kategori, filter kategori, lalu Print/Save
  as PDF dan ekspor CSV lengkap.
  **Hasil yang diharapkan:** angka keadaan saat ini konsisten; cetak tidak
  menampilkan data privat dan CSV lengkap dapat diunduh.

## Perbandingan integritas akhir

- [ ] Bandingkan daftar tamu, RSVP, pengiriman/pengingat, check-in, laporan,
  CSV, dan riwayat koreksi setelah seluruh skenario.
  **Hasil yang diharapkan:** semua tampilan menunjukkan satu keadaan pusat yang
  konsisten; catat setiap selisih sebelum menerima Phase 6D.

## Perangkat keras yang ditunda

- [ ] Uji pemindai USB fisik secara terpisah.
  **Hasil yang diharapkan:** perangkat bertindak sebagai input keyboard,
  menghasilkan pratinjau, dan tetap memerlukan konfirmasi. Ini tetap pengingat
  Phase 5 yang tidak memblokir penerimaan manual Phase 6D.
