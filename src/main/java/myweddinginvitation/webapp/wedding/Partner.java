package myweddinginvitation.webapp.wedding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "partner")
public class Partner {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "display_order", nullable = false, columnDefinition = "tinyint")
	private int displayOrder;

	@Column(name = "full_name", length = 160)
	private String fullName;

	@Column(length = 80)
	private String nickname;

	@Column(name = "photo_path", length = 500)
	private String photoPath;

	@Column(name = "child_of_label_id", length = 120)
	private String childOfLabelId;

	@Column(name = "child_of_label_en", length = 120)
	private String childOfLabelEn;

	@Column(name = "parents_names_id", length = 300)
	private String parentsNamesId;

	@Column(name = "parents_names_en", length = 300)
	private String parentsNamesEn;

	@Column(name = "instagram_url", length = 500)
	private String instagramUrl;

	protected Partner() {
	}

	private Partner(int displayOrder) {
		this.displayOrder = displayOrder;
	}

	static Partner empty(int displayOrder) {
		return new Partner(displayOrder);
	}

	public Long getId() {
		return id;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}

	public String getFullName() {
		return fullName;
	}

	public String getNickname() {
		return nickname;
	}

	public String getPhotoPath() {
		return photoPath;
	}

	public String getChildOfLabelId() {
		return childOfLabelId;
	}

	public String getChildOfLabelEn() {
		return childOfLabelEn;
	}

	public String getParentsNamesId() {
		return parentsNamesId;
	}

	public String getParentsNamesEn() {
		return parentsNamesEn;
	}

	public String getInstagramUrl() {
		return instagramUrl;
	}

	void update(PartnerForm form) {
		fullName = form.getFullName();
		nickname = form.getNickname();
		childOfLabelId = form.getChildOfLabelId();
		childOfLabelEn = form.getChildOfLabelEn();
		parentsNamesId = form.getParentsNamesId();
		parentsNamesEn = form.getParentsNamesEn();
		instagramUrl = form.getInstagramUrl();
	}

	void replacePhoto(String photoPath) {
		this.photoPath = photoPath;
	}

	void setDisplayOrder(int displayOrder) {
		this.displayOrder = displayOrder;
	}
}
