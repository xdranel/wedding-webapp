package myweddinginvitation.webapp.guest;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import org.springframework.stereotype.Service;

@Service
public class WhatsappNumberService {
	private static final String INVALID_NUMBER = "Enter a valid WhatsApp number.";
	private final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

	public String normalize(String raw, String region) {
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
}
