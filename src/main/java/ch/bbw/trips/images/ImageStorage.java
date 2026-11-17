package ch.bbw.trips.images;

import java.util.List;
import java.util.Optional;

/**
 * Abstraktion fuer den Bilder-Storage (RefCard04 -> RefCard05).
 * <ul>
 *   <li>{@link LocalImageStorage}: Dateisystem/Docker-Volume (Default, Tests, lokale Entwicklung ohne S3)</li>
 *   <li>{@link S3ImageStorage}: Amazon S3 (AWS) bzw. S3-kompatibel (S3Mock in podman-compose)</li>
 * </ul>
 * Umschalten ueber die Property {@code storage.type=local|s3} (Spring-Profil {@code s3}).
 */
public interface ImageStorage {

	void store(String key, byte[] data, String contentType);

	Optional<StoredImage> load(String key);

	boolean exists(String key);

	List<String> list();

	/** Kurzer Name fuer Logs/Health, z. B. {@code s3://bucket} oder {@code local:/data/images}. */
	String describe();

	/** Prueft, ob der Storage erreichbar ist und die Rechte reichen; wirft eine Exception, wenn nicht. */
	void verifyAccess();

	record StoredImage(byte[] data, String contentType) {
	}
}
