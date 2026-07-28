package myweddinginvitation.webapp.guest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GuestCategoryForm {
	private Long version;

	@NotBlank
	@Size(max = 80)
	private String name;

	public Long getVersion() { return version; }
	public void setVersion(Long version) { this.version = version; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
}
