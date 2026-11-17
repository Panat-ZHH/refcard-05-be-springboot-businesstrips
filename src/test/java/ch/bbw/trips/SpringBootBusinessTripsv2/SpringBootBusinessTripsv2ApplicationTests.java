package ch.bbw.trips.SpringBootBusinessTripsv2;

import ch.bbw.trips.images.ImageStorage;
import ch.bbw.trips.images.LocalImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "storage.local.dir=target/test-images")
class SpringBootBusinessTripsv2ApplicationTests {

	@Autowired
	ImageStorage storage;

	@Test
	void contextLoads() {
		// Default-Profil: lokaler Storage, Demo-Bilder werden beim Start geseedet
		assertThat(storage).isInstanceOf(LocalImageStorage.class);
		assertThat(storage.list()).contains("trips/1", "trips/2", "trips/3");
	}

}
