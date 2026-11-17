package ch.bbw.trips.images;

/** Storage hat den Zugriff verweigert (z. B. fehlende IAM-Rechte der ECS-Task-Rolle auf den S3-Bucket). */
public class StorageAccessDeniedException extends RuntimeException {
	public StorageAccessDeniedException(String message, Throwable cause) {
		super(message, cause);
	}
}
