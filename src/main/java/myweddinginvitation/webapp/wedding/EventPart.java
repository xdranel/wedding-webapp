package myweddinginvitation.webapp.wedding;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "event_part")
public class EventPart {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 20)
	private EventType type;

	@Column(nullable = false)
	private boolean visible;

	@Column(name = "event_date")
	private LocalDate date;

	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	@Column(name = "venue_name", length = 200)
	private String venueName;

	@Column(name = "address_id", length = 1000)
	private String addressId;

	@Column(name = "address_en", length = 1000)
	private String addressEn;

	@Column(name = "map_url", length = 1000)
	private String mapUrl;

	protected EventPart() {
	}

	public Long getId() {
		return id;
	}

	public EventType getType() {
		return type;
	}

	public boolean isVisible() {
		return visible;
	}

	public LocalDate getDate() {
		return date;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public String getVenueName() {
		return venueName;
	}

	public String getAddressId() {
		return addressId;
	}

	public String getAddressEn() {
		return addressEn;
	}

	public String getMapUrl() {
		return mapUrl;
	}
}
