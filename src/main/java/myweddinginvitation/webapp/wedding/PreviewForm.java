package myweddinginvitation.webapp.wedding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PreviewForm {
    @NotBlank
    @Size(max = 120)
    private String salutation = "Bapak/Ibu";

    @NotBlank
    @Size(max = 160)
    private String guestName = "Nama Tamu";

    @Pattern(regexp = "ID|EN")
    private String language = "ID";

    public String getSalutation() {
        return salutation;
    }

    public void setSalutation(String salutation) {
        this.salutation = salutation;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
