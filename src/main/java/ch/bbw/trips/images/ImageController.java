package ch.bbw.trips.images;

import ch.bbw.trips.ex.TriptNotFoundException;
import ch.bbw.trips.repo.BusinessTripRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Bilder-Endpoint (aus RefCard04 uebernommen, jetzt storage-unabhaengig: lokal oder S3).
 * <pre>
 * GET  /v1/images                  -> Liste aller gespeicherten Keys
 * GET  /v1/trips/{id}/image        -> Bild eines Trips (404 wenn keines vorhanden)
 * PUT  /v1/trips/{id}/image        -> Bild hochladen/ersetzen (multipart, Feld "file")
 * </pre>
 */
@RestController
@RequestMapping("/v1")
public class ImageController {

	private final ImageStorage storage;
	private final BusinessTripRepository trips;
	private final StorageProperties props;

	public ImageController(ImageStorage storage, BusinessTripRepository trips, StorageProperties props) {
		this.storage = storage;
		this.trips = trips;
		this.props = props;
	}

	static String keyFor(Long tripId) {
		return "trips/" + tripId;
	}

	@GetMapping("/images")
	public Map<String, Object> list() {
		List<String> keys = storage.list();
		return Map.of("storage", storage.describe(), "count", keys.size(), "keys", keys);
	}

	@GetMapping("/trips/{id}/image")
	public ResponseEntity<byte[]> get(@PathVariable Long id) {
		return storage.load(keyFor(id))
				.map(img -> ResponseEntity.ok()
						.contentType(MediaType.parseMediaType(img.contentType()))
						.cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
						.body(img.data()))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PutMapping(path = "/trips/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Object>> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
		if (!trips.existsById(id)) throw new TriptNotFoundException(id);
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leere Datei");
		}
		if (file.getSize() > props.maxFileSize()) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Datei groesser als " + props.maxFileSize() + " Bytes");
		}
		byte[] data = file.getBytes();
		String type = ImageTypes.detect(data);
		if (type == null || !ImageTypes.ALLOWED.contains(type)) {
			throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Nur JPEG, PNG, GIF oder WebP erlaubt");
		}
		String key = keyFor(id);
		storage.store(key, data, type);
		return ResponseEntity.created(URI.create("/v1/trips/" + id + "/image"))
				.body(Map.of("key", key, "contentType", type, "size", data.length, "storage", storage.describe()));
	}
}
