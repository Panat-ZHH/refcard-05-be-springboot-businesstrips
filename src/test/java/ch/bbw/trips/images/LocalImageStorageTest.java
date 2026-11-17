package ch.bbw.trips.images;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImageStorageTest {

	static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

	@TempDir
	Path dir;

	LocalImageStorage storage() {
		return new LocalImageStorage(new StorageProperties("local", new StorageProperties.Local(dir.toString()), null, null, 0));
	}

	@Test
	void storeLoadAndList() {
		LocalImageStorage s = storage();
		s.store("trips/1", PNG, "image/png");

		assertThat(s.exists("trips/1")).isTrue();
		assertThat(s.exists("trips/2")).isFalse();
		assertThat(s.load("trips/1")).hasValueSatisfying(img -> {
			assertThat(img.data()).isEqualTo(PNG);
			assertThat(img.contentType()).isEqualTo("image/png");
		});
		assertThat(s.list()).containsExactly("trips/1");
		assertThat(s.describe()).startsWith("local:");
	}

	@Test
	void missingImageIsEmpty() {
		assertThat(storage().load("trips/42")).isEmpty();
	}

	@Test
	void rejectsPathTraversal() {
		assertThatThrownBy(() -> storage().store("../../etc/passwd", PNG, "image/png"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
