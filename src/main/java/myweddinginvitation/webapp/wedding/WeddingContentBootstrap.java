package myweddinginvitation.webapp.wedding;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class WeddingContentBootstrap implements ApplicationRunner {
    private final WeddingSettingsRepository settings;
    private final PartnerRepository partners;

    WeddingContentBootstrap(WeddingSettingsRepository settings, PartnerRepository partners) {
        this.settings = settings;
        this.partners = partners;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (settings.getSingleton().isEmpty()) {
            settings.save(WeddingSettings.initial());
        }
        List<Integer> displayOrders = partners.findAllByOrderByDisplayOrderAsc().stream()
                .map(Partner::getDisplayOrder)
                .toList();
        for (int displayOrder = 1; displayOrder <= 2; displayOrder++) {
            if (!displayOrders.contains(displayOrder)) {
                partners.save(Partner.empty(displayOrder));
            }
        }
    }
}
