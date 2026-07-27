package myweddinginvitation.webapp.wedding;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "story_entry")
public class StoryEntry {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "story_date")
	private LocalDate date;

	@Column(name = "title_id", nullable = false, length = 200)
	private String titleId;

	@Column(name = "title_en", length = 200)
	private String titleEn;

	@Column(name = "body_id", nullable = false, length = 4000)
	private String bodyId;

	@Column(name = "body_en", length = 4000)
	private String bodyEn;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	protected StoryEntry() {
	}

	public Long getId() {
		return id;
	}

	public LocalDate getDate() {
		return date;
	}

	public String getTitleId() {
		return titleId;
	}

	public String getTitleEn() {
		return titleEn;
	}

	public String getBodyId() {
		return bodyId;
	}

	public String getBodyEn() {
		return bodyEn;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}
}
