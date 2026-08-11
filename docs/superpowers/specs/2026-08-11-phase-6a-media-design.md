# Phase 6A Wedding Media Design

Date: 2026-08-11  
Status: approved in conversation; awaiting written-spec review

## Scope

Phase 6 is split into four sequential subphases:

1. Phase 6A: gallery, background audio, and media processing;
2. Phase 6B: manual reminders and calendar files;
3. Phase 6C: reports, exports, moderation, and system status;
4. Phase 6D: integration, acceptance, and documentation.

This document covers Phase 6A only. The repository receives a usable neutral
default design. A separate visual-design pass for the owner's wedding follows
after the functional phases are complete.

The physical USB scanner acceptance check from Phase 5 remains deferred until
hardware is available. It is recorded in `docs/installation/development.md`
and does not block Phase 6.

## Confirmed product behavior

The administrator can manage an optional gallery of at most ten photos and one
optional MP3 background track. Disabling either feature preserves its files and
configuration. Deleting the last gallery photo disables the gallery; deleting
the MP3 disables audio. A feature cannot be enabled without its required media.

Each gallery photo has:

- a required, language-neutral alternative text;
- optional Indonesian and English captions with the existing language fallback;
- an explicit display position;
- one optimized display image and one thumbnail.

The administrator can upload, replace, edit, reorder with Move up/Move down,
and delete gallery photos. Metadata edits do not require another upload. Audio
upload, replacement, deletion, enablement, and disablement are independent
operations. Staff accounts cannot manage wedding media.

## Selected technical approach

Media processing runs synchronously inside the Java application. It reuses the
configured filesystem `MEDIA_DIRECTORY`; gallery files live below `gallery/`
and audio below `audio/`. No object storage, CDN, external image command,
background worker, client-side image conversion, or asynchronous job system is
introduced.

One pinned Java-compatible WebP encoder is permitted because the JDK does not
provide the required WebP writer. The implementation plan must prove that the
chosen encoder can decode the accepted image formats and write WebP on Java 21
before production integration. No general media framework is required.

## Persistence

A new immutable Flyway migration after V10 introduces `gallery_photo` and the
minimum wedding-setting fields needed for gallery/audio visibility and the
current MP3 reference.

`gallery_photo` stores:

- identifier and optimistic-lock version;
- contiguous display position scoped to the single wedding;
- main WebP path and thumbnail WebP path;
- required alternative text;
- nullable Indonesian and English captions;
- creation and update timestamps.

The wedding settings store gallery visibility, audio enablement, and the
nullable MP3 path. The database stores relative application-owned paths, never
client filenames or absolute filesystem paths. The application enforces the
ten-photo limit under the same wedding-content serialization boundary used for
ordering changes.

## Image processing and storage lifecycle

Accepted uploads are JPG, PNG, and WebP up to 10 MiB each. The server decodes
the content rather than trusting the extension or request content type. Empty,
corrupt, unsupported, oversized, or dangerously large-dimension images are
rejected. The implementation plan defines a conservative decoded-pixel ceiling
and proves it with a regression test before the decoder allocates an unbounded
image.

The application removes metadata and stores only generated WebP output:

- main image: aspect ratio preserved, longest side at most 1920 pixels;
- thumbnail: aspect ratio preserved, longest side at most 480 pixels.

Smaller images are not enlarged. Transparency is preserved where present.
Original uploads are not retained. Application-generated random names prevent
guessing and collisions.

New output is processed into temporary files and moved into place only after
successful validation. A failed upload or replacement preserves the previously
active database row and files. After a successful database change, superseded
files are removed. Cleanup is idempotent: an already-missing obsolete file is
logged but does not make the administrator operation unusable. Temporary or
newly generated files are removed when persistence fails.

## Audio processing and lifecycle

The application accepts one non-empty MP3 of at most 20 MB. It validates an
MPEG audio signature instead of trusting the filename or request content type.
It stores the MP3 without transcoding under a random application-generated
name. Video, playlists, waveform generation, duration limits, normalization,
and format conversion are out of scope.

Replacement follows the same preserve-old-on-failure lifecycle as images.
Disabling audio keeps the file; explicit deletion removes it and disables the
feature.

## Routes and authorization

Administrator wedding-media routes provide list/edit, upload, replace,
reorder, delete, and enable/disable operations. Every mutation requires the
`ADMIN` role, CSRF protection, server validation, and optimistic-lock/version
input where an existing record is changed. The page follows the current admin
views and supplies clear field and operation errors.

Application media endpoints serve only database-referenced gallery and audio
files. They accept an internal identifier rather than a filesystem path, do
not expose directory listings, and never concatenate untrusted path fragments.
Responses use fixed content types for their known media kind and appropriate
browser caching. Opaque media names and the token-protected invitation are
sufficient; per-file signed URLs are deliberately out of scope because media
contains no guest data.

## Public invitation behavior

The gallery is absent from rendered HTML when disabled or empty. When active,
the public invitation renders lazy-loaded thumbnails in a responsive grid.
Activating a thumbnail opens the full image in an accessible lightbox with
previous, next, and close controls, keyboard navigation, focus management, alt
text, and the caption for the current invitation language. The existing
language fallback applies when the selected caption is empty. No automatic
carousel or gallery dependency is used.

The audio element is absent when disabled or missing. It uses `preload="none"`
so MP3 download does not delay initial rendering. Pressing the existing Open
Invitation control attempts playback as part of that user interaction. A
browser rejection or media failure never prevents the invitation from opening;
the guest can use a persistent, labelled Play/Pause control in the lower corner.
No forced volume, progress bar, volume slider, visualizer, or playback
persistence across page visits is required.

The default implementation is mobile-first, supports current Chrome, Safari,
Edge, and Firefox, and respects reduced-motion preferences. Detailed artistic
layout, animation, typography, and owner-specific styling are deferred to the
separate final visual-design pass.

## Preview, publication, and consistency rules

The administrator preview uses the same gallery and audio rendering behavior
as the public invitation. Gallery visibility requires at least one photo;
audio enablement requires an MP3. Removing the final required media atomically
disables its feature so published invitations cannot retain a broken enabled
state.

Media operations do not change invitation tokens, RSVP state, check-in state,
or guest data. Existing published wedding edits remain live according to the
current publication model.

## Error handling and logging

Validation errors leave the edit page usable and identify the rejected field
or operation without echoing file bytes or server paths. Missing media returns
a neutral not-found response. Path traversal and unknown identifiers never
reach filesystem resolution.

Logs record the operation kind, internal media identifier, outcome, and safe
failure category. They do not record file contents, client filesystem names,
guest data, or secret paths. Antivirus scanning and automatic content
moderation are out of scope because upload access is limited to the trusted
administrator.

## Automated verification

Implementation follows test-driven development and covers:

- the new migration and relational constraints on MySQL 8.4;
- JPG, PNG, and WebP input producing bounded main and thumbnail WebP files;
- rejection of empty, corrupt, unsupported, oversized, and unsafe-dimension input;
- the ten-photo limit and serialized contiguous ordering;
- metadata edit, reorder, replacement, deletion, and optimistic-lock conflict;
- preservation of active files after failed replacement and cleanup after success;
- MP3 validation, upload, replacement, deletion, and enable/disable invariants;
- administrator role, staff denial, CSRF, unknown media, and path-traversal cases;
- bilingual captions, fallback, alt text, lazy loading, and visibility rendering;
- lightbox/audio JavaScript syntax and focused regression checks;
- an administrator-to-public media journey against MySQL and real filesystem files.

## Manual acceptance

Manual acceptance uses an available phone and laptop and verifies:

1. initial invitation rendering does not fetch the MP3;
2. responsive gallery and full-image loading on touch and mouse;
3. lightbox close/navigation by touch, mouse, and keyboard;
4. Open Invitation playback and usable fallback when playback is rejected;
5. persistent Play/Pause state and non-blocking media failure;
6. admin upload, reorder, replacement, disable/enable, and deletion behavior;
7. Chrome and Safari behavior on the currently available devices;
8. usable rendering under a throttled or slow connection.

The physical USB QR scanner remains a separate Phase 5 reminder and is not a
Phase 6A acceptance condition.

## Explicit deferrals

The following are not part of Phase 6A: reminders, calendar downloads,
reports, exports, additional greeting moderation, system status, deployment
packaging, backup/restore implementation, final visual redesign, video,
playlists, CDN/object storage, and automatic media moderation.
