package myweddinginvitation.webapp.guest;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class WhatsappNumberService {
	private static final String INVALID_NUMBER = "Enter a valid WhatsApp number.";
	private static final String INVALID_REGION = "Select a valid phone country.";
	private final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

	public record RegionOption(String code, String label) {
	}

	public List<RegionOption> supportedRegions() {
		return phoneUtil.getSupportedRegions().stream()
				.map(code -> new RegionOption(code, countryLabel(code)))
				.sorted(Comparator.comparing(RegionOption::label))
				.toList();
	}

	public String regionFor(String number, String fallbackRegion) {
		try {
			String region = phoneUtil.getRegionCodeForNumber(phoneUtil.parse(number, "ZZ"));
			return region != null && phoneUtil.getSupportedRegions().contains(region) ? region : fallbackRegion;
		} catch (NumberParseException exception) {
			return fallbackRegion;
		}
	}

	public String normalize(String raw, String region) {
		if (region == null || !phoneUtil.getSupportedRegions().contains(region)) {
			throw new IllegalArgumentException(INVALID_REGION);
		}
		try {
			PhoneNumber parsed = phoneUtil.parse(raw, region);
			if (!phoneUtil.isValidNumber(parsed)) {
				throw new IllegalArgumentException(INVALID_NUMBER);
			}
			return phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
		} catch (NumberParseException exception) {
			throw new IllegalArgumentException(INVALID_NUMBER, exception);
		}
	}

	private String countryLabel(String code) {
		String country = new Locale("", code).getDisplayCountry(Locale.ENGLISH);
		return country + " (+" + phoneUtil.getCountryCodeForRegion(code) + ")";
	}
}
