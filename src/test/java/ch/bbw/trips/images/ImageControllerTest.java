package ch.bbw.trips.images;

import ch.bbw.trips.ex.TripNotFoundAdvice;
import ch.bbw.trips.repo.BusinessTripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ImageControllerTest {

	static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};

	@TempDir
	Path dir;
	BusinessTripRepository trips = mock(BusinessTripRepository.class);
	ImageStorage storage;
	MockMvc mvc;

	@BeforeEach
	void setUp() {
		StorageProperties props = new StorageProperties("local", new StorageProperties.Local(dir.toString()), null, null, 1024);
		storage = spy(new LocalImageStorage(props));
		mvc = MockMvcBuilders.standaloneSetup(new ImageController(storage, trips, props))
				.setControllerAdvice(new StorageExceptionAdvice(), new TripNotFoundAdvice())
				.build();
		when(trips.existsById(1L)).thenReturn(true);
	}

	MockMultipartFile file(byte[] data) {
		return new MockMultipartFile("file", "x.jpg", "image/jpeg", data);
	}

	@Test
	void uploadThenDownload() throws Exception {
		mvc.perform(multipart("/v1/trips/1/image").file(file(JPEG)).with(r -> { r.setMethod("PUT"); return r; }))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.key").value("trips/1"))
				.andExpect(jsonPath("$.contentType").value("image/jpeg"));

		mvc.perform(get("/v1/trips/1/image"))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/jpeg"))
				.andExpect(content().bytes(JPEG));

		mvc.perform(get("/v1/images")).andExpect(jsonPath("$.count").value(1));
	}

	@Test
	void unknownImageIs404() throws Exception {
		mvc.perform(get("/v1/trips/7/image")).andExpect(status().isNotFound());
	}

	@Test
	void rejectsNonImages() throws Exception {
		mvc.perform(multipart("/v1/trips/1/image").file(file("not an image".getBytes())).with(r -> { r.setMethod("PUT"); return r; }))
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void rejectsUnknownTrip() throws Exception {
		mvc.perform(multipart("/v1/trips/5/image").file(file(JPEG)).with(r -> { r.setMethod("PUT"); return r; }))
				.andExpect(status().isNotFound());
	}

	@Test
	void storageAccessDeniedGives503() throws Exception {
		doThrow(new StorageAccessDeniedException("Zugriff verweigert: s3:PutObject", null))
				.when(storage).store(anyString(), any(), anyString());
		mvc.perform(multipart("/v1/trips/1/image").file(file(JPEG)).with(r -> { r.setMethod("PUT"); return r; }))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value("Bilder-Storage: Zugriff verweigert"));
	}
}
