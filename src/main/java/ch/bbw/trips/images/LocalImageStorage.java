package ch.bbw.trips.images;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Bilder im lokalen Dateisystem bzw. in einem Docker-Volume (Stand RefCard04). */
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalImageStorage implements ImageStorage {

	private final Path root;

	public LocalImageStorage(StorageProperties props) {
		this.root = Path.of(props.local().dir()).toAbsolutePath().normalize();
	}

	@Override
	public void store(String key, byte[] data, String contentType) {
		Path target = resolve(key);
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, data);
		} catch (IOException e) {
			throw new StorageUnavailableException("Konnte Bild nicht speichern: " + key, e);
		}
	}

	@Override
	public Optional<StoredImage> load(String key) {
		Path p = resolve(key);
		if (!Files.isRegularFile(p)) return Optional.empty();
		try {
			byte[] data = Files.readAllBytes(p);
			String type = ImageTypes.detect(data);
			return Optional.of(new StoredImage(data, type != null ? type : "application/octet-stream"));
		} catch (IOException e) {
			throw new StorageUnavailableException("Konnte Bild nicht lesen: " + key, e);
		}
	}

	@Override
	public boolean exists(String key) {
		return Files.isRegularFile(resolve(key));
	}

	@Override
	public List<String> list() {
		if (!Files.isDirectory(root)) return List.of();
		try (Stream<Path> files = Files.walk(root)) {
			return files.filter(Files::isRegularFile)
					.map(p -> root.relativize(p).toString().replace('\\', '/'))
					.sorted()
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public String describe() {
		return "local:" + root;
	}

	@Override
	public void verifyAccess() {
		try {
			Files.createDirectories(root);
			if (!Files.isWritable(root)) throw new IOException("Verzeichnis nicht beschreibbar: " + root);
		} catch (IOException e) {
			throw new StorageUnavailableException(e.getMessage(), e);
		}
	}

	private Path resolve(String key) {
		Path p = root.resolve(key).normalize();
		if (!p.startsWith(root)) throw new IllegalArgumentException("Ungueltiger Key: " + key);
		return p;
	}
}
