package ch.bbw.trips.images;

/** Storage ist nicht erreichbar oder antwortet mit einem unerwarteten Fehler. */
public class StorageUnavailableException extends RuntimeException {
	public StorageUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
