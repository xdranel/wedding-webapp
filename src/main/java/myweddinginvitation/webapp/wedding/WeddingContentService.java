package myweddinginvitation.webapp.wedding;

import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.web.multipart.MultipartFile;

import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeddingContentService {
    private final WeddingSettingsRepository settings;
    private final PartnerRepository partners;
    private final EventPartRepository events;
    private final StoryEntryRepository story;
    private final PartnerPhotoStorage photoStorage;

    public WeddingContentService(WeddingSettingsRepository settings, PartnerRepository partners,
                                 EventPartRepository events, StoryEntryRepository story, PartnerPhotoStorage photoStorage) {
        this.settings = settings;
        this.partners = partners;
        this.events = events;
        this.story = story;
        this.photoStorage = photoStorage;
    }

    @Transactional(readOnly = true)
    public WeddingOverview overview() {
        WeddingSettings weddingSettings = weddingSettings();
        List<Partner> orderedPartners = partners.findAllByOrderByDisplayOrderAsc();
        List<EventPart> orderedEvents = events.findAllByOrderByTypeAsc();
        List<StoryEntry> orderedStory = story.findAllByOrderByDisplayOrderAsc();
        return new WeddingOverview(weddingSettings.getVersion(), weddingSettings.getPublicationState(), settingsComplete(weddingSettings),
                orderedPartners.stream().map(this::isPartnerComplete).toList(),
                orderedEvents.stream().filter(this::isCompleteVisibleEvent).map(EventPart::getType).toList(),
                !orderedStory.isEmpty(), translationWarnings(weddingSettings, orderedPartners, orderedEvents, orderedStory));
    }

    @Transactional(readOnly = true)
    public WeddingSettingsForm settingsForm() {
        WeddingSettings source = weddingSettings();
        WeddingSettingsForm form = new WeddingSettingsForm();
        form.setVersion(source.getVersion());
        form.setCoupleTitle(source.getCoupleTitle());
        form.setOpeningTextId(source.getOpeningTextId());
        form.setOpeningTextEn(source.getOpeningTextEn());
        form.setClosingTextId(source.getClosingTextId());
        form.setClosingTextEn(source.getClosingTextEn());
        form.setTimeZone(source.getTimeZone());
        form.setRsvpDeadline(source.getRsvpDeadline());
        form.setDefaultPhoneCountry(source.getDefaultPhoneCountry());
        form.setAccentColor(source.getAccentColor());
        form.setFontPreset(source.getFontPreset());
        form.setGreetingsEnabled(source.isGreetingsEnabled());
        form.setPrivateOrganizerNoteEnabled(source.isPrivateOrganizerNoteEnabled());
        form.setCalendarDownloadsEnabled(source.isCalendarDownloadsEnabled());
        return form;
    }

    @Transactional
    public void saveSettings(WeddingSettingsForm form) {
        WeddingSettings weddingSettings = weddingSettings();
        if (!java.util.Objects.equals(weddingSettings.getVersion(), form.getVersion())) {
            throw new OptimisticLockingFailureException("Wedding settings have changed");
        }
        weddingSettings.update(form);
        settings.saveAndFlush(weddingSettings);
    }

    @Transactional(readOnly = true)
    public EventPartForm eventForm(EventType type) {
        EventPartForm form = new EventPartForm();
        events.findByType(type).ifPresent(event -> {
            form.setVersion(event.getVersion());
            form.setVisible(event.isVisible());
            form.setEventDate(event.getDate());
            form.setStartTime(event.getStartTime());
            form.setEndTime(event.getEndTime());
            form.setVenueName(event.getVenueName());
            form.setAddressId(event.getAddressId());
            form.setAddressEn(event.getAddressEn());
            form.setMapUrl(event.getMapUrl());
        });
        return form;
    }

    @Transactional
    public void saveEvent(EventType type, EventPartForm form) {
        EventPart event = events.findByType(type).orElse(null);
        if (event != null) {
            if (!java.util.Objects.equals(event.getVersion(), form.getVersion())) {
                throw new OptimisticLockingFailureException("Event has changed");
            }
            event.update(form);
            events.saveAndFlush(event);
            return;
        }
        try {
            event = EventPart.create(type);
            event.update(form);
            events.saveAndFlush(event);
        } catch (DataIntegrityViolationException exception) {
            throw new OptimisticLockingFailureException("Event has changed", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<PartnerForm> partnerForms() {
        return partners.findAllByOrderByDisplayOrderAsc().stream().map(partner -> {
            PartnerForm form = new PartnerForm();
            form.setId(partner.getId());
            form.setVersion(partner.getVersion());
            form.setFullName(partner.getFullName());
            form.setNickname(partner.getNickname());
            form.setChildOfLabelId(partner.getChildOfLabelId());
            form.setChildOfLabelEn(partner.getChildOfLabelEn());
            form.setParentsNamesId(partner.getParentsNamesId());
            form.setParentsNamesEn(partner.getParentsNamesEn());
            form.setInstagramUrl(partner.getInstagramUrl());
            return form;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<StoryEntryForm> storyForms() {
        return story.findAllByOrderByDisplayOrderAsc().stream().map(this::storyForm).toList();
    }

    @Transactional(readOnly = true)
    public void requireStory(long id) {
        if (!story.existsById(id)) throw new java.util.NoSuchElementException();
    }

    @Transactional
    public long addStory(StoryEntryForm form) {
        List<StoryEntry> orderedStory = story.findAllByOrderByDisplayOrderAscForUpdate();
        int displayOrder = orderedStory.stream().mapToInt(StoryEntry::getDisplayOrder).max().orElse(0) + 1;
        return story.saveAndFlush(StoryEntry.create(form, displayOrder)).getId();
    }

    @Transactional
    public void updateStory(long id, StoryEntryForm form) {
        StoryEntry entry = story.findById(id).orElseThrow();
        if (!java.util.Objects.equals(entry.getVersion(), form.getVersion())) {
            throw new OptimisticLockingFailureException("Story has changed");
        }
        entry.update(form);
        story.saveAndFlush(entry);
    }

    @Transactional
    public void deleteStory(long id) {
        List<StoryEntry> orderedStory = story.findAllByOrderByDisplayOrderAscForUpdate();
        StoryEntry deleted = orderedStory.stream().filter(entry -> entry.getId() == id).findFirst().orElseThrow();
        story.delete(deleted);
        story.flush();
        int displayOrder = 1;
        for (StoryEntry entry : orderedStory) {
            if (entry != deleted) entry.setDisplayOrder(displayOrder++);
        }
        story.flush();
    }

    @Transactional
    public void moveStoryUp(long id) {
        moveStory(id, -1);
    }

    @Transactional
    public void moveStoryDown(long id) {
        moveStory(id, 1);
    }

    @Transactional
    public void savePartner(long id, PartnerForm form, MultipartFile photo) {
        Partner partner = partners.findById(id).orElseThrow();
        if (!java.util.Objects.equals(partner.getVersion(), form.getVersion())) {
            throw new OptimisticLockingFailureException("Partner has changed");
        }
        String oldPath = partner.getPhotoPath();
        String newPath = photo != null && !photo.isEmpty() ? photoStorage.store(photo) : null;
        try {
            partner.update(form);
            if (newPath != null) partner.replacePhoto(newPath);
            partners.saveAndFlush(partner);
        } catch (RuntimeException exception) {
            if (newPath != null) photoStorage.delete(newPath);
            throw exception;
        }
        if (newPath != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (oldPath != null) photoStorage.deleteAfterCommit(oldPath);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) photoStorage.delete(newPath);
                }
            });
        }
    }

    @Transactional
    public void swapPartners() {
        List<Partner> orderedPartners = partners.findAllByOrderByDisplayOrderAsc();
        Partner first = orderedPartners.getFirst();
        Partner second = orderedPartners.get(1);
        first.setDisplayOrder(0);
        partners.saveAndFlush(first);
        second.setDisplayOrder(1);
        partners.saveAndFlush(second);
        first.setDisplayOrder(2);
        partners.saveAndFlush(first);
    }

    private void moveStory(long id, int direction) {
        List<StoryEntry> orderedStory = story.findAllByOrderByDisplayOrderAscForUpdate();
        int index = java.util.stream.IntStream.range(0, orderedStory.size())
                .filter(candidate -> orderedStory.get(candidate).getId() == id).findFirst().orElseThrow();
        int adjacent = index + direction;
        if (adjacent < 0 || adjacent >= orderedStory.size()) return;
        StoryEntry selected = orderedStory.get(index);
        StoryEntry neighbor = orderedStory.get(adjacent);
        int selectedOrder = selected.getDisplayOrder();
        int neighborOrder = neighbor.getDisplayOrder();
        selected.setDisplayOrder(0);
        story.saveAndFlush(selected);
        neighbor.setDisplayOrder(selectedOrder);
        story.saveAndFlush(neighbor);
        selected.setDisplayOrder(neighborOrder);
        story.saveAndFlush(selected);
    }

    @Transactional(readOnly = true)
    public PublicationCheck checkPublication() {
        WeddingSettings weddingSettings = weddingSettings();
        return new PublicationCheck(weddingSettings.getPublicationState() == PublicationState.PUBLISHED,
                publicationErrors(weddingSettings, partners.findAllByOrderByDisplayOrderAsc(), events.findAllByOrderByTypeAsc()));
    }

    @Transactional
    public PublicationCheck publish() {
        return publish(weddingSettings().getVersion());
    }

    @Transactional
    public PublicationCheck publish(long expectedVersion) {
        WeddingSettings weddingSettings = weddingSettings();
        requireVersion(weddingSettings, expectedVersion);
        List<String> errors = publicationErrors(weddingSettings, partners.findAllByOrderByDisplayOrderAsc(),
                events.findAllByOrderByTypeAsc());
        if (errors.isEmpty()) {
            weddingSettings.publish();
            settings.saveAndFlush(weddingSettings);
            return new PublicationCheck(true, errors);
        }
        return new PublicationCheck(false, errors);
    }

    @Transactional
    public void returnToDraft() {
        returnToDraft(weddingSettings().getVersion());
    }

    @Transactional
    public void returnToDraft(long expectedVersion) {
        WeddingSettings weddingSettings = weddingSettings();
        requireVersion(weddingSettings, expectedVersion);
        weddingSettings.returnToDraft();
        settings.saveAndFlush(weddingSettings);
    }

    @Transactional(readOnly = true)
    public WeddingPreview preview(String salutation, String guestName, String language) {
        WeddingSettings weddingSettings = weddingSettings();
        List<Partner> orderedPartners = partners.findAllByOrderByDisplayOrderAsc();
        List<EventPart> orderedEvents = events.findAllByOrderByTypeAsc();
        return new WeddingPreview(weddingSettings.getPublicationState(), coupleTitle(weddingSettings, orderedPartners), salutation,
                guestName, coverDate(orderedEvents), weddingSettings.getAccentColor(), weddingSettings.getFontPreset(),
                localized(weddingSettings.getOpeningTextId(), weddingSettings.getOpeningTextEn(), language),
                localized(weddingSettings.getClosingTextId(), weddingSettings.getClosingTextEn(), language),
                orderedPartners.stream().map(partner -> new WeddingPreview.PartnerView(partner.getId(), partner.getFullName(), partner.getNickname(),
                        partner.getPhotoPath(), localized(partner.getChildOfLabelId(), partner.getChildOfLabelEn(), language),
                        localized(partner.getParentsNamesId(), partner.getParentsNamesEn(), language), partner.getInstagramUrl())).toList(),
                orderedEvents.stream().filter(EventPart::isVisible).map(event -> new WeddingPreview.EventView(event.getType(), event.getDate(),
                        event.getStartTime(), event.getEndTime(), event.getVenueName(),
                        localized(event.getAddressId(), event.getAddressEn(), language), event.getMapUrl())).toList(),
                story.findAllByOrderByDisplayOrderAsc().stream().map(entry -> new WeddingPreview.StoryView(entry.getDate(),
                        localized(entry.getTitleId(), entry.getTitleEn(), language), localized(entry.getBodyId(), entry.getBodyEn(), language))).toList());
    }

    @Transactional(readOnly = true)
    public String staffLabel() {
        String label = coupleTitle(weddingSettings(), partners.findAllByOrderByDisplayOrderAsc());
        return hasText(label) ? label : null;
    }

    public static String localized(String indonesian, String english, String language) {
        return "EN".equals(language) && hasText(english) ? english : indonesian;
    }

    private WeddingSettings weddingSettings() {
        return settings.getSingleton().orElseThrow();
    }

    private void requireVersion(WeddingSettings weddingSettings, long expectedVersion) {
        if (weddingSettings.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Wedding settings have changed");
        }
    }

    private List<String> publicationErrors(WeddingSettings weddingSettings, List<Partner> orderedPartners, List<EventPart> orderedEvents) {
        List<String> errors = new ArrayList<>();
        for (int displayOrder = 1; displayOrder <= 2; displayOrder++) {
            int partnerOrder = displayOrder;
            Partner partner = orderedPartners.stream().filter(candidate -> candidate.getDisplayOrder() == partnerOrder).findFirst().orElse(null);
            if (partner == null) {
                errors.add("Partner " + displayOrder + ": profile is required");
            } else {
                partnerErrors(partner, errors);
            }
        }
        if (!isValidZone(weddingSettings.getTimeZone())) {
            errors.add("Time zone is invalid");
        }
        for (EventPart event : orderedEvents) {
            if (event.isVisible()) {
                eventErrors(event, errors);
            }
        }
        if (orderedEvents.stream().noneMatch(this::isCompleteVisibleEvent)) {
            errors.add("At least one complete event must be visible");
        }
        return errors;
    }

    private void partnerErrors(Partner partner, List<String> errors) {
        String label = "Partner " + partner.getDisplayOrder() + ": ";
        if (!hasText(partner.getFullName())) errors.add(label + "full name is required");
        if (!hasText(partner.getNickname())) errors.add(label + "nickname is required");
        if (!hasText(partner.getPhotoPath())) errors.add(label + "photo is required");
        if (!hasText(partner.getChildOfLabelId())) errors.add(label + "Indonesian child-of label is required");
        if (!hasText(partner.getParentsNamesId())) errors.add(label + "Indonesian parents' names are required");
    }

    private void eventErrors(EventPart event, List<String> errors) {
        String label = eventLabel(event) + ": ";
        if (event.getDate() == null) errors.add(label + "date is required");
        if (event.getStartTime() == null) errors.add(label + "start time is required");
        if (!hasText(event.getVenueName())) errors.add(label + "venue name is required");
        if (!hasText(event.getAddressId())) errors.add(label + "Indonesian address is required");
        if (!isHttpUrl(event.getMapUrl())) errors.add(label + "map URL must use HTTP or HTTPS");
        if (event.getStartTime() != null && event.getEndTime() != null && !event.getEndTime().isAfter(event.getStartTime())) {
            errors.add(label + "end time must be after start time");
        }
    }

    private boolean isPartnerComplete(Partner partner) {
        return hasText(partner.getFullName()) && hasText(partner.getNickname()) && hasText(partner.getPhotoPath())
                && hasText(partner.getChildOfLabelId()) && hasText(partner.getParentsNamesId());
    }

    private boolean isCompleteVisibleEvent(EventPart event) {
        return event.isVisible() && event.getDate() != null && event.getStartTime() != null && hasText(event.getVenueName())
                && hasText(event.getAddressId()) && isHttpUrl(event.getMapUrl())
                && (event.getEndTime() == null || event.getEndTime().isAfter(event.getStartTime()));
    }

    private boolean settingsComplete(WeddingSettings weddingSettings) {
        return hasText(weddingSettings.getOpeningTextId()) && hasText(weddingSettings.getClosingTextId())
                && isValidZone(weddingSettings.getTimeZone());
    }

    private List<String> translationWarnings(WeddingSettings weddingSettings, List<Partner> orderedPartners, List<EventPart> orderedEvents,
                                             List<StoryEntry> orderedStory) {
        List<String> warnings = new ArrayList<>();
        if (!hasText(weddingSettings.getOpeningTextEn())) warnings.add("Opening text: English translation is missing");
        if (!hasText(weddingSettings.getClosingTextEn())) warnings.add("Closing text: English translation is missing");
        for (Partner partner : orderedPartners) {
            String label = "Partner " + partner.getDisplayOrder() + ": ";
            if (!hasText(partner.getChildOfLabelEn())) warnings.add(label + "English child-of label is missing");
            if (!hasText(partner.getParentsNamesEn())) warnings.add(label + "English parents' names are missing");
        }
        for (EventPart event : orderedEvents) {
            if (event.isVisible() && !hasText(event.getAddressEn()))
                warnings.add(eventLabel(event) + ": English address is missing");
        }
        for (StoryEntry entry : orderedStory) {
            String label = "Story " + entry.getDisplayOrder() + ": ";
            if (!hasText(entry.getTitleEn())) warnings.add(label + "English title is missing");
            if (!hasText(entry.getBodyEn())) warnings.add(label + "English body is missing");
        }
        return warnings;
    }

    private StoryEntryForm storyForm(StoryEntry entry) {
        StoryEntryForm form = new StoryEntryForm();
        form.setId(entry.getId());
        form.setVersion(entry.getVersion());
        form.setDate(entry.getDate());
        form.setTitleId(entry.getTitleId());
        form.setTitleEn(entry.getTitleEn());
        form.setBodyId(entry.getBodyId());
        form.setBodyEn(entry.getBodyEn());
        return form;
    }

    private String coupleTitle(WeddingSettings weddingSettings, List<Partner> orderedPartners) {
        if (hasText(weddingSettings.getCoupleTitle())) return weddingSettings.getCoupleTitle();
        return orderedPartners.stream().map(Partner::getNickname).filter(WeddingContentService::hasText).collect(java.util.stream.Collectors.joining(" & "));
    }

    private LocalDate coverDate(List<EventPart> orderedEvents) {
        return orderedEvents.stream().filter(EventPart::isVisible).map(EventPart::getDate).filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
    }

    private boolean isValidZone(String value) {
        if (!hasText(value)) return false;
        try {
            ZoneId.of(value);
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }

    static boolean isHttpUrl(String value) {
        if (!hasText(value)) return false;
        try {
            URI uri = URI.create(value);
            return !uri.isOpaque() && hasText(uri.getHost())
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String eventLabel(EventPart event) {
        return event.getType().name().substring(0, 1) + event.getType().name().substring(1).toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
