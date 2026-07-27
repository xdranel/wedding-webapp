package myweddinginvitation.webapp.wedding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public class PartnerForm {
	private Long id;

	@NotBlank @Size(max = 160)
	private String fullName;

	@NotBlank @Size(max = 80)
	private String nickname;

	@NotBlank @Size(max = 120)
	private String childOfLabelId;

	@Size(max = 120)
	private String childOfLabelEn;

	@NotBlank @Size(max = 300)
	private String parentsNamesId;

	@Size(max = 300)
	private String parentsNamesEn;

	@Size(max = 500) @Pattern(regexp = "^$|https://[^\\s]+$", message = "Must use HTTPS")
	private String instagramUrl;

	private MultipartFile photo;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getFullName() { return fullName; }
	public void setFullName(String fullName) { this.fullName = fullName; }
	public String getNickname() { return nickname; }
	public void setNickname(String nickname) { this.nickname = nickname; }
	public String getChildOfLabelId() { return childOfLabelId; }
	public void setChildOfLabelId(String childOfLabelId) { this.childOfLabelId = childOfLabelId; }
	public String getChildOfLabelEn() { return childOfLabelEn; }
	public void setChildOfLabelEn(String childOfLabelEn) { this.childOfLabelEn = childOfLabelEn; }
	public String getParentsNamesId() { return parentsNamesId; }
	public void setParentsNamesId(String parentsNamesId) { this.parentsNamesId = parentsNamesId; }
	public String getParentsNamesEn() { return parentsNamesEn; }
	public void setParentsNamesEn(String parentsNamesEn) { this.parentsNamesEn = parentsNamesEn; }
	public String getInstagramUrl() { return instagramUrl; }
	public void setInstagramUrl(String instagramUrl) { this.instagramUrl = instagramUrl; }
	public MultipartFile getPhoto() { return photo; }
	public void setPhoto(MultipartFile photo) { this.photo = photo; }
}
