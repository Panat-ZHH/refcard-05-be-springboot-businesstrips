package ch.bbw.trips.images;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/**
 * Zeigt unter /actuator/health, ob der Bilder-Storage (S3-Bucket) erreichbar ist und die IAM-Rechte reichen.
 * Bewusst NICHT Teil der readiness-Gruppe (ALB/ECS-Health-Check): ein S3-Rechteproblem soll die
 * Trips-API nicht lahmlegen, ist aber sofort im Health-Endpoint sichtbar.
 */
@Component("imageStorage")
public class ImageStorageHealthIndicator extends AbstractHealthIndicator {

	private final ImageStorage storage;

	public ImageStorageHealthIndicator(ImageStorage storage) {
		this.storage = storage;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) {
		builder.withDetail("storage", storage.describe());
		if (storage instanceof S3ImageStorage s3) builder.withDetail("accessScope", s3.accessScope());
		try {
			storage.verifyAccess();
			builder.up();
		} catch (StorageAccessDeniedException e) {
			builder.down().withDetail("error", "AccessDenied").withDetail("hint", e.getMessage());
		} catch (RuntimeException e) {
			builder.down().withDetail("error", e.getMessage());
		}
	}
}
