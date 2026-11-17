package ch.bbw.trips.images;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Laedt beim Start die Demo-Bilder (classpath:seed-images/{tripId}.jpg) in den Storage, falls dort noch
 * keines liegt - analog zum Seeding der Demo-Daten. Fehler (z. B. fehlende S3-Rechte) werden geloggt,
 * verhindern aber den Start der Applikation nicht.
 */
@Component
@Order(100)
public class ImageSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ImageSeeder.class);

	private final ImageStorage storage;
	private final StorageProperties props;

	public ImageSeeder(ImageStorage storage, StorageProperties props) {
		this.storage = storage;
		this.props = props;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!props.seed().enabled()) return;
		try {
			Resource[] seeds = new PathMatchingResourcePatternResolver().getResources("classpath:seed-images/*.*");
			int uploaded = 0;
			for (Resource r : seeds) {
				String name = r.getFilename();
				if (name == null || !name.contains(".")) continue;
				String id = name.substring(0, name.indexOf('.'));
				String key = "trips/" + id;
				if (storage.exists(key)) continue;
				try (InputStream in = r.getInputStream()) {
					byte[] data = in.readAllBytes();
					String type = ImageTypes.detect(data);
					storage.store(key, data, type != null ? type : "application/octet-stream");
					uploaded++;
				}
			}
			log.info("Bilder-Seeding auf {}: {} neu hochgeladen ({} Seed-Bilder)", storage.describe(), uploaded, seeds.length);
		} catch (IOException | RuntimeException e) {
			log.warn("Bilder-Seeding auf {} fehlgeschlagen: {}", storage.describe(), e.getMessage());
		}
	}
}
