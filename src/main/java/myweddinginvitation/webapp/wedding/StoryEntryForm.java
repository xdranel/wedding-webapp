package myweddinginvitation.webapp.wedding;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

public class StoryEntryForm {
	private Long id;
	private Long version;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate date;

	@NotBlank(message = "Indonesian title is required")
	@Size(max = 200)
	private String titleId;

	@Size(max = 200)
	private String titleEn;

	@NotBlank(message = "Indonesian story is required")
	@Size(max = 4000)
	private String bodyId;

	@Size(max = 4000)
	private String bodyEn;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public Long getVersion() { return version; }
	public void setVersion(Long version) { this.version = version; }
	public LocalDate getDate() { return date; }
	public void setDate(LocalDate date) { this.date = date; }
	public String getTitleId() { return titleId; }
	public void setTitleId(String titleId) { this.titleId = titleId; }
	public String getTitleEn() { return titleEn; }
	public void setTitleEn(String titleEn) { this.titleEn = titleEn; }
	public String getBodyId() { return bodyId; }
	public void setBodyId(String bodyId) { this.bodyId = bodyId; }
	public String getBodyEn() { return bodyEn; }
	public void setBodyEn(String bodyEn) { this.bodyEn = bodyEn; }
}
