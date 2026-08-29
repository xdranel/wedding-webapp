# Penerimaan Manual Phase 6D

Status fungsional Phase 6D: **diterima pada 2026-08-23**. Pemilik telah
mengonfirmasi seluruh pemeriksaan Phase 6D yang tersedia; kotak di bawah
mencatat hasil nyata, bukan inferensi dari pengujian otomatis. Bukti otomatis
final saat itu: 2 pemeriksaan sintaks JavaScript terlacak dan 78 suite / 431
test dengan 0 kegagalan, error, atau skip pada MySQL/Flyway V1-V13.

Status penerimaan visual redesign presentasi: **DITERIMA pada 2026-08-29**.
Pemilik menyelesaikan pemeriksaan desktop/mobile dan mengonfirmasi seluruh
retest temuan presentasi berjalan benar. Physical USB scanner tetap DEFERRED
karena perangkat belum tersedia dan tidak memblokir penerimaan.

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

- [ ] **DEFERRED — physical USB scanner:** uji pemindai USB fisik secara
  terpisah saat perangkat tersedia.
  **Hasil yang diharapkan:** perangkat bertindak sebagai input keyboard,
  menghasilkan pratinjau, dan tetap memerlukan konfirmasi. Ini tetap pengingat
  Phase 5 yang ditunda dan tidak memblokir penerimaan Phase 6D.

## Matriks penerimaan manual redesign presentasi

Daftar berikut adalah matriks yang digunakan selama acceptance. Status akhir
dan retest temuan dicatat pada bagian akhir dokumen; presentation refinement
telah diterima pemilik pada 2026-08-29.

### Guest

- [ ] **PENDING — cover/fallback:** unggah cover undangan khusus, ganti dan
  hapus cover, lalu verifikasi urutan fallback cover khusus → foto pasangan
  pertama → fallback terbuat tanpa memblokir isi undangan atau RSVP.
- [ ] **PENDING — initial reveal/navigation:** buka undangan dan Preview pada
  desktop; konten Welcome/Sapaan pertama harus terlihat tanpa lompatan ke
  Events dan fokus keyboard tetap dapat digunakan.
- [ ] **PENDING — desktop gallery controls:** gunakan mouse dan keyboard pada
  Previous, Next, dan Close; setiap tombol harus aktif tanpa dicegat gesture
  galeri.
- [ ] **PENDING — desktop RSVP centering:** periksa kartu RSVP pada viewport
  desktop lebar dan pastikan tetap berada di tengah.
- [ ] **PENDING — language disclosure:** tombol bahasa tunggal menampilkan
  `ID` atau `EN`, membuka pilihan Indonesia/English lewat mouse dan keyboard,
  serta mengidentifikasi bahasa aktif.
- [ ] **PENDING — hidden audio:** mulai audio lalu sembunyikan tab atau pindah
  aplikasi; audio harus berhenti dan tidak mulai kembali tanpa memilih Play.
- [ ] **PENDING — ID/EN:** jalankan perjalanan undangan dalam bahasa Indonesia
  dan Inggris, termasuk pergantian bahasa dan fallback teks.
- [ ] **PENDING — photo/no-photo:** bandingkan cover dengan foto dan fallback
  tanpa foto; pastikan teks, kontras, dan aksi buka tetap jelas.
- [ ] **PENDING — full/sparse sections:** periksa konten lengkap dan konfigurasi
  bagian opsional yang jarang; tidak boleh ada celah atau navigasi kosong.
- [ ] **PENDING — JavaScript disabled:** seluruh isi dan formulir tetap dapat
  dipahami dan digunakan; pesan `noscript` terlihat.
- [ ] **PENDING — reduced motion:** aktifkan preferensi reduced motion dan
  pastikan transisi/scroll non-esensial dinonaktifkan.
- [ ] **PENDING — RSVP pending/Hadir/Tidak Hadir:** periksa keadaan belum
  merespons, hadir, dan tidak hadir beserta validasi dan jumlah tamu.
- [ ] **PENDING — QR:** verifikasi QR hanya tersedia sesuai aturan `Hadir` dan
  alur PIN tetap dapat digunakan.
- [ ] **PENDING — Closed/unavailable:** periksa halaman Closed dan unavailable
  dalam ID/EN tanpa kebocoran identitas atau detail undangan.
- [ ] **PENDING — iPhone Safari:** jalankan smoke undangan, bahasa, RSVP, QR,
  galeri/audio bila tersedia, dan kontrol sentuh pada Safari iPhone.

### Administrator

- [ ] **PENDING — Dashboard Search Guests:** tekan Enter dan gunakan tombol
  Search; keduanya harus membuka Guest list dengan filter `query` yang sama.
- [ ] **PENDING — accordion/active group:** hanya grup navigasi tujuan aktif
  yang terbuka pada desktop; tujuan aktif langsung terlihat setelah navigasi.
- [ ] **PENDING — mobile drawer state:** drawer Administrator mulai tertutup,
  lalu saat dibuka hanya grup aktif yang terbuka dan seluruh tujuan tetap dapat
  dicapai.
- [ ] **PENDING — filtered Guest list context:** buka Guest List, Invitations,
  RSVP, dan Check-ins; judul, penjelasan, badge filter, serta Clear filters /
  View all guests harus menjelaskan konteks setiap shortcut.
- [ ] **PENDING — full-width cards/tables:** kartu utama dan tabel desktop
  mengisi area konten yang tersedia; kartu tabel seluler tidak overflow.
- [ ] **PENDING — action styling:** tautan navigasi tetap berupa tautan,
  tindakan utama/pendukung memakai tombol yang konsisten, dan tindakan
  destruktif tetap jelas serta terkonfirmasi.
- [ ] **PENDING — Publication & Event Status:** tujuan permanen menampilkan
  status publikasi/acara, persyaratan, Publish/Return to Draft, Open/Close, dan
  salinan selesai ID/EN; panduan aksi terblokir mengarah ke tujuan ini.
- [ ] **PENDING — Wedding Settings redirect:** simpan Settings dan pastikan
  kembali ke Wedding Settings dengan pesan `Settings saved`.
- [ ] **PENDING — Preview controls:** verifikasi Back to Wedding Admin,
  sapaan/nama/bahasa saat ini, Open in new tab, dan pembukaan konten
  Welcome/Sapaan pertama.
- [ ] **PENDING — desktop/sidebar:** periksa seluruh grup navigasi, status aktif,
  tindakan utama, dan logout pada viewport desktop.
- [ ] **PENDING — mobile/drawer:** periksa drawer, urutan fokus, penutupan, dan
  akses ke seluruh tujuan pada viewport ponsel.
- [ ] **PENDING — keyboard focus:** gunakan keyboard saja dan pastikan urutan
  fokus logis serta indikator fokus selalu terlihat.
- [ ] **PENDING — tables/cards:** bandingkan tabel desktop dan kartu responsif
  tanpa kehilangan label, nilai, atau tindakan.
- [ ] **PENDING — filters:** terapkan, gabungkan, pertahankan, dan hapus filter
  pada Guests, Reminders, Greetings, dan Reports.
- [ ] **PENDING — destructive confirmations:** uji konfirmasi delete, regenerasi
  token, close/reopen, serta pengurangan allowance/count tanpa melakukan aksi
  pada data pemilik.
- [ ] **PENDING — preview:** pastikan preview memakai presentasi guest yang sama
  dan kontrol admin tetap terpisah.
- [ ] **PENDING — report print:** periksa Print/Save as PDF, tabel cetak, filter,
  dan tidak adanya navigasi atau data privat yang tidak disetujui.

### Staff

- [ ] **PENDING — role-aware password:** halaman penggantian kata sandi wajib
  menampilkan identitas Staff Check-in untuk staf dan Administrator untuk
  administrator tanpa mengubah form atau validasi.
- [ ] **PENDING — compact check-in controls:** pilihan jumlah hadir `1`/`2`
  tampil sebagai pilihan segmented ringkas dan konfirmasi perubahan RSVP
  sebagai checkbox normal dengan target label ramah sentuh.
- [ ] **PENDING — camera:** mulai/hentikan kamera, pindah ke Search, dan pastikan
  track kamera berhenti serta fallback manual tetap tersedia.
- [ ] **PENDING — pasted payload:** tempel payload scanner ke input native dan
  pastikan alurnya menuju preview yang sama.
- [ ] **PENDING — search:** cari nama/empat digit terakhir, pilih tamu, dan
  periksa preview sebelum konfirmasi.
- [ ] **PENDING — valid/duplicate/error:** periksa hasil valid, duplikat beserta
  waktu/staf pertama, dan error/fallback yang jelas secara tekstual.
- [ ] **PENDING — two accounts:** dua akun staf mencoba tamu yang sama; hanya satu
  check-in pusat tercatat dan hasil lain menunjukkan duplikat.
- [ ] **PENDING — WAN off with LAN available:** putus WAN tetapi pertahankan LAN
  ke server, lalu scan/search dan konfirmasi tanpa antrean offline palsu.
- [ ] **DEFERRED — physical USB scanner:** uji perangkat fisik saat tersedia;
  scanner harus bertindak sebagai keyboard, membuka preview, dan tetap meminta
  konfirmasi. Penundaan Phase 5 ini tetap eksplisit dan tidak dianggap lulus.

### Tools

- [ ] **PENDING — Chromium Lighthouse accessibility/performance:** jalankan pada
  halaman guest default serta permukaan admin/staff representatif; simpan hanya
  ringkasan bebas data pribadi.
- [ ] **PENDING — default contrast:** periksa kontras teks, kontrol, status,
  focus ring, dan overlay foto dengan warna aksen default.
- [ ] **PENDING — configurable accent contrast:** ulangi pemeriksaan kontras
  dengan sedikitnya satu warna aksen yang dapat dikonfigurasi dan pastikan
  foreground terang/gelap tetap aman.

## Regresi otomatis refinement presentasi

Gate terakhir selesai pada 2026-08-29 dengan hanya satu proses Maven pada satu
waktu. Semua perintah Maven memakai lingkungan berikut agar Testcontainers
menggunakan Podman rootless dan tidak memulai Ryuk:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true ./mvnw ... test
```

Ledger dihitung ulang langsung dari `src/test/java/**/*Test.java` dan
`*Tests.java`: 79 kelas sumber dipetakan ke tujuh batch (4, 9, 8, 11, 10, 12,
25), tanpa nama sumber duplikat, penugasan duplikat, kelas hilang, atau kelas
tambahan. Daftar di kolom perintah adalah seluruh keanggotaan batch; setiap
kelas test tercakup tepat satu kali.

| Batch | Perintah `./mvnw` setelah dua environment variable di atas | Hasil |
|---|---|---|
| Root + acceptance + presentation | `-Dtest=DatabaseMigrationTest,MyweddinginvitationWebappApplicationTests,Phase6dIntegrationJourneyTest,PresentationStructureTest test` | 4 suite / 26 test |
| Account + config | `-Dtest=AccountSessionFilterTest,AdminBootstrapTest,SecurityRoutesTest,StaffAccountControllerTest,StaffAccountServiceTest,SystemStatusAdminControllerTest,SystemStatusServiceTest,UserAccountSecurityTest,WebErrorHandlerTest test` | 9 suite / 46 test |
| Check-in | `-Dtest=AdminCheckInControllerTest,AdminCheckInServiceTest,CheckInConcurrencyTest,CheckInControllerTest,CheckInJourneyTest,CheckInMigrationTest,CheckInSearchTest,CheckInServiceTest test` | 8 suite / 46 test |
| Guest | `-Dtest=GuestCategoryControllerTest,GuestControllerTest,GuestCsvControllerTest,GuestCsvServiceTest,GuestDeliveryJourneyTest,GuestDeliveryMigrationTest,GuestServiceTest,InvitationLinkSignerTest,Phase6dScaleTest,PublicInvitationControllerTest,WhatsappNumberServiceTest test` | 11 suite / 90 test |
| Messaging + reporting | `-Dtest=GuestDeliveryControllerTest,GuestDeliveryServiceTest,MessageTemplateControllerTest,MessageTemplateServiceTest,ReminderAdminControllerTest,ReminderCalendarJourneyTest,ReminderServiceTest,ReportAdminControllerTest,ReportServiceTest,ReportingStatusJourneyTest test` | 10 suite / 50 test |
| RSVP | `-Dtest=AdminRsvpControllerTest,AdminRsvpSummaryTest,CheckInQrSignerTest,GreetingModerationControllerTest,GuestPinServiceTest,GuestVerificationSessionTest,PublicQrControllerTest,PublicRsvpControllerTest,QrImageServiceTest,RsvpMigrationTest,RsvpQrJourneyTest,RsvpServiceTest test` | 12 suite / 65 test |
| Wedding | `-Dtest=AdminHomeControllerTest,CalendarControllerTest,CalendarServiceTest,EventPartControllerTest,EventStatusAdminControllerTest,EventStatusServiceTest,GalleryImageStorageTest,PartnerControllerTest,PartnerPhotoStorageTest,ReminderCalendarMigrationTest,ReportingEventStatusMigrationTest,StoryControllerTest,WebpImageIoSmokeTest,WeddingAudioStorageTest,WeddingContentControllerTest,WeddingContentJourneyTest,WeddingContentMigrationTest,WeddingContentServiceTest,WeddingMediaAdminControllerTest,WeddingMediaControllerTest,WeddingMediaJourneyTest,WeddingMediaMigrationTest,WeddingMediaRenderingTest,WeddingMediaServiceTest,WeddingPreviewTest test` | 25 suite / 154 test |

Setelah **setiap** eksekusi batch, cleanup dan audit berikut dijalankan sebelum
memulai Maven berikutnya:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
podman rm -f --filter label=org.testcontainers=true
ps -eo pid=,comm=,args= | \
awk '$2 == "java" && ($0 ~ /surefire|maven|Maven/) {print}; \
     $2 == "mysqld" {print}'
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
podman ps -a --filter label=org.testcontainers=true
```

Batch Wedding pertama dihentikan saat dua assertion riwayat migrasi lama
mengharapkan V1–V13, sedangkan Flyway secara sah menerapkan V14 Invitation
Cover. Hanya expected list pada `ReminderCalendarMigrationTest` dan
`ReportingEventStatusMigrationTest` yang diperbarui menjadi V1–V14; batch
Wedding kemudian diulang sendiri dan lulus. Batch 1–6 tidak diulang.

Setelah setiap eksekusi Maven—enam batch hijau awal, batch Wedding RED, dan
rerun Wedding hijau—cleanup hanya menarget container berlabel
`org.testcontainers=true`. Seluruh delapan audit kosong: tidak ada proses
Maven, Surefire, Testcontainers, atau `mysqld` yang tertinggal dan tidak ada
container berlabel Testcontainers. Compose MySQL tidak disentuh.

Agregasi dari XML Surefire segar tiap batch hijau menghasilkan **79 suite / 477
test, 0 kegagalan, 0 error, dan 0 skip**. JavaScript produksi yang tidak masuk
Maven juga diperiksa sekali dengan perintah berikut dan lulus 4/4:

```bash
node --test src/test/js/invitation-media.test.js
```

`graphify update .` dan `git diff --check` lulus setelah pembaruan dokumentasi.

Bukti otomatis ini melengkapi, tetapi tidak menggantikan hasil acceptance
manual yang dicatat di bawah.

## Retest presentation refinement 2026-08-29

Pemilik melaporkan seluruh perjalanan lain berjalan lancar dan menemukan lima
inkonsistensi presentasi: sidebar Administrator hilang pada desktop, ruang
putih setelah footer Preview, kartu QR tidak berada di tengah, kontrol musik
bertumpuk dengan tombol pembuka, dan Change Password Staff masih terlalu
generik. Perbaikannya menjaga sidebar terbuka pada desktop/tertutup pada
mobile, menyamakan background bawah Preview, memusatkan kartu dan isi QR,
menampilkan kontrol musik berikon hanya setelah invitation dibuka, serta
memberi Change Password identitas visual sesuai role.

Kelima poin tersebut tetap **PENDING retest** pada desktop dan mobile sebelum
branch diterima. Physical USB scanner tetap **DEFERRED** dan tidak memblokir.

Retest awal menemukan Change Password masih tampil native karena filter sesi
mengalihkan request stylesheet selama password wajib diganti. Aset statis kini
tetap dapat dimuat tanpa membuka route aplikasi lain. Emoji audio juga diganti
dengan ikon SVG outline monochrome yang mengikuti warna kontrol. Kedua poin
ini tetap **PENDING retest**.

Follow-up retest meminta ikon audio dipusatkan di dalam tombol serta jarak aman
antara Change Password dan Sign Out. Ikon kini memakai centering flex; Sign Out
menjadi aksi sekunder setelah pemisah dan jarak tersendiri. Pemilik kemudian
mengonfirmasi keduanya **PASS** pada 2026-08-29; penerimaan presentation
refinement selesai dengan USB scanner fisik tetap **DEFERRED**.

Setelah branch digabungkan ke `main`, regresi penuh final lulus pada 2026-08-29:
**79 suite / 485 test Java** terhadap MySQL/Flyway V1–V14 dan **5/5 test
JavaScript**, tanpa kegagalan, error, atau skip. Tidak ada proses Maven,
Surefire, `mysqld`, atau container Testcontainers yang tertinggal.
