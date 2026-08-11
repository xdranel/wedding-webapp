# Phase 6A Wedding Media Implementation Plan

**Status (2026-08-11):** Tasks 1-8 implementation and automated verification are complete. Manual phone/laptop acceptance remains pending, so Phase 6A is not yet user-accepted.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an administrator-managed ten-photo optimized WebP gallery and one optional MP3 background track to the default public wedding invitation.

**Architecture:** Extend the existing `wedding` feature with one Flyway migration, JPA media state, synchronous Java ImageIO processing, filesystem storage below `MEDIA_DIRECTORY`, server-rendered admin/public views, and small dependency-free browser enhancements. Preserve active files on failed replacements, expose media only by database identifier, and keep guest/RSVP/check-in state independent.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC/Security/Data JPA/Validation, Thymeleaf, MySQL 8.4/Flyway, Java ImageIO, `com.github.usefulness:webp-imageio:0.10.0`, filesystem storage, vanilla JavaScript and CSS.

## Global Constraints

- Follow red-green-refactor: observe a focused failing test before every production behavior.
- Never edit Flyway V1-V10; add `V11__wedding_media.sql`.
- Accept JPG, PNG, and WebP up to 10 MiB; store only WebP main (longest side at most 1920 px) and thumbnail (at most 480 px), never upscale, preserve alpha.
- Read dimensions with `ImageReader` and reject more than 40,000,000 decoded pixels before raster allocation.
- Accept one non-empty MP3 up to 20 MB without transcoding.
- Keep at most ten gallery rows with contiguous zero-based positions.
- Alternative text is required; ID/EN captions are optional with the existing fallback.
- Gallery/audio enablement requires media; deleting the final media disables the feature.
- Mutations are administrator-only, CSRF-protected, validated, and version checked.
- Files use random application names below `gallery/` and `audio/`; never persist client filenames or absolute paths.
- Failure preserves active media and cleans newly generated files.
- Public endpoints resolve database identifiers only, never paths.
- Audio uses `preload="none"`; playback failure never blocks invitation opening.
- No object storage, CDN, external command, worker, antivirus, video, playlist, automatic carousel, or JavaScript library.
- Do not stage/delete user-owned `skills-lock.json` or generated `graphify-out/`.
- After code changes run `graphify update .` and leave generated graph files unstaged.

## File map

- Persistence: V11, `GalleryPhoto`, `GalleryPhotoRepository`, media fields on `WeddingSettings`.
- Storage: `GalleryImageStorage`, `StoredGalleryImage`, `WeddingAudioStorage`.
- Behavior: `WeddingMediaService`, `GalleryPhotoForm`, `WeddingMediaView`.
- Admin: `WeddingMediaAdminController`, `admin/wedding/media.html`.
- Delivery: identifier routes in `WeddingMediaController`.
- Guest: controller model additions, public/preview templates, `invitation-media.js`, and invitation CSS.
- Verification: migration/storage/service/MVC/security/rendering/journey tests and canonical docs.

---

### Task 1: Pin WebP support and add the relational model

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/db/migration/V11__wedding_media.sql`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/GalleryPhoto.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/GalleryPhotoRepository.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingSettingsRepository.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingSettings.java`
- Create: `src/test/java/myweddinginvitation/webapp/wedding/WeddingMediaMigrationTest.java`
- Create: `src/test/java/myweddinginvitation/webapp/wedding/WebpImageIoSmokeTest.java`

**Interfaces:**
- Produces `findAllByOrderByPositionAsc()`, `findByIdForUpdate(long)`, `WeddingSettingsRepository.findSingletonForUpdate()`, gallery entity mutation methods, wedding media getters/mutations, and registered ImageIO WebP reader/writer.

- [x] **Step 1: Write RED tests.** Assert V11 applies on MySQL, path/position uniqueness, fields/defaults/version, and wedding flags/audio path. Smoke-test a 2x2 ARGB WebP round trip:

```java
assertThat(ImageIO.write(image, "webp", output.toFile())).isTrue();
BufferedImage decoded = ImageIO.read(output.toFile());
assertThat(decoded.getWidth()).isEqualTo(2);
assertThat(decoded.getColorModel().hasAlpha()).isTrue();
```

- [x] **Step 2: Run RED.** Run `./mvnw -q -Dtest=WeddingMediaMigrationTest,WebpImageIoSmokeTest test`. Expected: missing dependency/schema failures.
- [x] **Step 3: Implement minimum schema/domain.** Pin `com.github.usefulness:webp-imageio:0.10.0`. V11 adds `gallery_enabled`, `background_audio_enabled`, and nullable `background_audio_path` to settings and creates `gallery_photo(id, position, main_path, thumbnail_path, alt_text, caption_id, caption_en, version, created_at, updated_at)` with unique position and paths. Implement:

```java
void updateMetadata(String altText, String captionId, String captionEn)
void replacePaths(String mainPath, String thumbnailPath)
void moveTo(int position)
void setGalleryEnabled(boolean enabled, boolean hasPhotos)
void setBackgroundAudioEnabled(boolean enabled)
void replaceBackgroundAudio(String relativePath)
String removeBackgroundAudio()
```

Enable methods reject missing required media.
- [x] **Step 4: Run GREEN.** Run `./mvnw -q -Dtest=WeddingMediaMigrationTest,WebpImageIoSmokeTest,RsvpMigrationTest,WeddingContentMigrationTest test`; all pass and Flyway validates eleven migrations.
- [x] **Step 5: Refresh, review, commit.** Run `graphify update .`, `git diff --check`, stage only named files, commit `feat: add wedding media schema`.

### Task 2: Process bounded gallery images

**Files:**
- Create: `StoredGalleryImage.java`, `GalleryImageStorage.java`
- Create: `src/test/java/myweddinginvitation/webapp/wedding/GalleryImageStorageTest.java`

**Interfaces:**
- Produces:

```java
record StoredGalleryImage(String mainPath, String thumbnailPath) {}
StoredGalleryImage store(MultipartFile file)
Path resolve(String relativePath)
void delete(StoredGalleryImage image)
void deleteAfterCommit(StoredGalleryImage image)
```

- [x] **Step 1: Write RED tests with `@TempDir`.** Use real JPG/PNG/WebP fixtures. Assert output format/dimensions, no upscaling, alpha, random paths, no original, 10 MiB rejection before reads, corrupt/unsupported rejection, 40,000,001-pixel header rejection before raster read, traversal rejection, and cleanup when the second write fails.
- [x] **Step 2: Run RED.** `./mvnw -q -Dtest=GalleryImageStorageTest test`; expected missing types.
- [x] **Step 3: Implement minimal processor.** Select an `ImageReader`, inspect dimensions, apply the pixel ceiling, decode once, resize through `Graphics2D` bicubic interpolation to alpha-capable buffers, write two WebP temporary files, then move on the same filesystem with `ATOMIC_MOVE` and a non-atomic fallback only for unsupported atomic moves. Do not add Thumbnailator or a storage interface.
- [x] **Step 4: Run GREEN.** `./mvnw -q -Dtest=GalleryImageStorageTest,PartnerPhotoStorageTest test`.
- [x] **Step 5: Refresh graph and commit** `feat: optimize gallery images`.

### Task 3: Implement gallery lifecycle and ordering

**Files:**
- Create: `GalleryPhotoForm.java`, `WeddingMediaView.java`, `WeddingMediaService.java`
- Create: `WeddingMediaServiceTest.java`

**Interfaces:**
- Produces:

```java
record GalleryPhotoForm(
    @NotBlank @Size(max = 300) String altText,
    @Size(max = 500) String captionId,
    @Size(max = 500) String captionEn,
    long version) {}

record WeddingMediaView(
    boolean galleryEnabled,
    boolean audioEnabled,
    long weddingVersion,
    List<Photo> photos) {
    record Photo(long id, long version, String altText, String caption,
                 String thumbnailUrl, String imageUrl) {}
}

WeddingMediaView adminView()
WeddingMediaView publicView(String language)
long addPhoto(MultipartFile file, GalleryPhotoForm form)
void updatePhoto(long id, GalleryPhotoForm form)
void replacePhoto(long id, long version, MultipartFile file)
void movePhoto(long id, long version, int direction)
void deletePhoto(long id, long version)
void setGalleryEnabled(long weddingVersion, boolean enabled)
```

- [x] **Step 1: Write RED MySQL/filesystem tests.** Cover additions through ten, reject eleven without storing, stripped required alt, normalized captions, metadata/stale version, replacement cleanup after commit, failed replacement preservation, move boundaries and contiguous ordering, delete compaction, last-delete disabling, enable-without-photo, and two concurrent adds producing neither duplicate position nor eleven rows.
- [x] **Step 2: Run RED.** `./mvnw -q -Dtest=WeddingMediaServiceTest test`.
- [x] **Step 3: Implement minimum service.** Lock singleton wedding settings before count/order mutation. Store new files before DB mutation, register rollback cleanup for new paths and after-commit cleanup for obsolete paths. Accept move direction only `-1` or `1`. Apply EN→ID and ID→EN caption fallback in the view only.
- [x] **Step 4: Run GREEN.** `./mvnw -q -Dtest=WeddingMediaServiceTest,WeddingContentServiceTest test`.
- [x] **Step 5: Refresh graph and commit** `feat: manage wedding gallery lifecycle`.

### Task 4: Store and manage one MP3

**Files:**
- Create: `WeddingAudioStorage.java`, `WeddingAudioStorageTest.java`
- Modify: `WeddingMediaService.java`, `WeddingMediaView.java`, `WeddingMediaServiceTest.java`

**Interfaces:**
- Storage produces `String store(MultipartFile)`, `Path resolve(String)`, `delete`, and `deleteAfterCommit`.
- Service adds:

```java
void replaceAudio(MultipartFile file)
void deleteAudio(long weddingVersion)
void setAudioEnabled(long weddingVersion, boolean enabled)
```

- [x] **Step 1: Write RED tests.** Cover ID3-prefixed and MPEG frame-sync input; empty/corrupt/non-MP3/over-20-MB rejection; random `audio/*.mp3`; traversal; replacement preservation/cleanup; enable without file; disable preservation; delete disabling.
- [x] **Step 2: Run RED.** `./mvnw -q -Dtest=WeddingAudioStorageTest,WeddingMediaServiceTest test`.
- [x] **Step 3: Implement minimal storage/lifecycle.** Accept a leading MPEG frame sync, or parse the synchsafe ID3 size and require an MPEG frame sync immediately after the tag; an `ID3` marker alone is insufficient. Enforce size before copy, store unchanged, and reuse Task 3 transaction cleanup rules.
- [x] **Step 4: Run GREEN.** Same focused command must pass.
- [x] **Step 5: Refresh graph and commit** `feat: manage wedding background audio`.

### Task 5: Add administrator media workflow

**Files:**
- Create: `WeddingMediaAdminController.java`, `templates/admin/wedding/media.html`
- Modify: `templates/admin/wedding/overview.html`
- Create: `WeddingMediaAdminControllerTest.java`
- Modify: `SecurityRoutesTest.java`

**Interfaces:**
- Produces `GET /admin/wedding/media` and separate POST routes for photo add/update/replace/move/delete/toggle and audio upload/delete/toggle.

- [x] **Step 1: Write MVC/security RED tests.** Assert page/actions, multipart operations, validation preserving submitted data, move boundaries, delete confirmation, visibility toggles, PRG success, safe stale conflict, CSRF rejection, anonymous redirect, staff 403, upload limits, and no client paths.
- [x] **Step 2: Run RED.** `./mvnw -q -Dtest=WeddingMediaAdminControllerTest,SecurityRoutesTest test`; expect 404/template failures.
- [x] **Step 3: Implement thin controller/page.** One service call per POST and redirect to the media page. Validation returns HTTP 400 with `media` and submitted `photoForm`; optimistic conflict returns a safe page error. Add Media navigation. Use forms/buttons, not drag/drop.
- [x] **Step 4: Run GREEN.** `./mvnw -q -Dtest=WeddingMediaAdminControllerTest,SecurityRoutesTest,WeddingContentControllerTest test`.
- [x] **Step 5: Refresh graph and commit** `feat: add wedding media administration`.

### Task 6: Serve only referenced identifiers

**Files:**
- Modify: `WeddingMediaController.java`, `SecurityConfig.java`
- Modify: `templates/admin/wedding/preview.html`, `templates/guest/invitation.html`
- Create: `WeddingMediaControllerTest.java`

**Interfaces:**
- Produces `GET /media/gallery/{id}/thumbnail`, `/media/gallery/{id}/image`, and `/media/wedding/audio`.

- [x] **Step 1: Write endpoint RED tests.** Known DB records return exact bytes with fixed `image/webp` or `audio/mpeg`, `nosniff`, and `Cache-Control: no-cache`. Unknown/malformed IDs, missing files, disabled audio, escaping stored paths, and traversal-shaped URLs return 404. Anonymous reads work; listing does not.
- [x] **Step 2: Run RED.** `./mvnw -q -Dtest=WeddingMediaControllerTest test`.
- [x] **Step 3: Implement identifier lookup.** Resolve only paths obtained from repositories. Replace the generic partner filename route with `/media/partner/{id}` backed by `PartnerRepository`, update both templates, and permit only required `/media/**` reads. Return `CacheControl.noCache()` because replacement keeps stable identifiers.
- [x] **Step 4: Run GREEN.** `./mvnw -q -Dtest=WeddingMediaControllerTest,PartnerControllerTest,SecurityRoutesTest test`.
- [x] **Step 5: Refresh graph and commit** `feat: serve referenced wedding media`.

### Task 7: Render accessible gallery and audio

**Files:**
- Modify: `PublicInvitationController.java`, `WeddingContentController.java`
- Modify: `templates/guest/invitation.html`, `templates/admin/wedding/preview.html`
- Create: `static/js/invitation-media.js`
- Modify: `static/css/invitation.css`
- Create: `WeddingMediaRenderingTest.java`
- Modify: `WeddingPreviewTest.java`

**Interfaces:**
- Both controllers produce model attribute `media = weddingMedia.publicView(language)`.
- DOM IDs: `open-invitation`, `gallery-dialog`, `gallery-previous`, `gallery-next`, `gallery-close`, `background-audio`, `audio-toggle`.

- [x] **Step 1: Write rendering RED tests.** Disabled/empty media is absent; active gallery has lazy thumbnails, required alt, caption fallback, main-image IDs, accessible dialog controls, and no eager main image. Audio has `preload="none"`, labelled toggle, no autoplay. Preview/public share semantics and local script.
- [x] **Step 2: Run RED.** `./mvnw -q -Dtest=WeddingMediaRenderingTest,WeddingPreviewTest test`.
- [x] **Step 3: Implement minimal markup/CSS/JS.** Add the missing public Open Invitation button. On open, reveal invitation and call `audio.play().catch(() => updateAudioLabel(false))`. Load full image only when native `<dialog>` opens, support previous/next/close, Escape/arrows, restore focus, synchronize Play/Pause from audio events, and honor reduced motion. No local storage/library.
- [x] **Step 4: Run GREEN.** Run `node --check src/main/resources/static/js/invitation-media.js` and `./mvnw -q -Dtest=WeddingMediaRenderingTest,WeddingPreviewTest,PublicRsvpControllerTest test`.
- [x] **Step 5: Refresh graph and commit** `feat: render invitation gallery and audio`.

### Task 8: Prove the journey and update operational truth

**Files:**
- Create: `WeddingMediaJourneyTest.java`
- Modify: `README.md`, `docs/ARCHITECTURE.md`, `DESIGN.md`, `PRD.md`, `RULES.md`, `SCHEMA.md`, `docs/installation/development.md`, roadmap, and this plan.

**Interfaces:**
- Produces one admin-to-public executable regression and Phase 6A manual acceptance checklist.

- [x] **Step 1: Add journey test.** Admin configures/publishes; uploads two shapes and MP3; enables, reorders, edits; opens signed ID/EN invitations; verifies fallback/alt/lazy/audio and endpoint bytes; replaces/disables/re-enables/deletes; proves guest token/RSVP/check-in unchanged. Once green, invert one enablement assertion, observe RED at that boundary, then restore.
- [x] **Step 2: Run focused verification.** Run `node --check src/main/resources/static/js/invitation-media.js` and `./mvnw -q -Dtest='WeddingMedia*Test,WebpImageIoSmokeTest,WeddingPreviewTest,SecurityRoutesTest' test`; require zero failures/errors/skips.
- [x] **Step 3: Update docs.** Record V11, limits, directory layout, replacement guarantees, routes, backup inclusion, Phase 6A implemented, Phase 6B-6D pending, and manual checks for phone/laptop touch/mouse/keyboard, Chrome/Safari, no initial MP3 request, playback fallback, throttling, admin operations, plus the still-deferred Phase 5 physical USB scanner.
- [x] **Step 4: Run clean verification.** Run `git diff --check`, Node syntax, and `./mvnw -q clean test`; require exit 0, all Surefire reports zero failures/errors/skips, V1-V11 clean migration.
- [x] **Step 5: Refresh graph, inspect intended changes, commit.** Run `graphify update .`, inspect status/diff, stage only Task 8 files, commit `docs: complete phase 6a wedding media`.

## Final acceptance gate

Phase 6A is complete only when Tasks 1-8 are checked, the clean MySQL/Testcontainers suite and JavaScript syntax pass, documentation matches implemented schema/routes, and the user accepts the phone/laptop checklist. The physical USB scanner remains a visible Phase 5 deferral and does not block Phase 6B.
