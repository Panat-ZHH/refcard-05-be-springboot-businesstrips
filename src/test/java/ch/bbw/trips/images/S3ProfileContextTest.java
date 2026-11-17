package ch.bbw.trips.images;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressionstest: mit storage.type=s3 muss der Kontext starten und S3ImageStorage verwenden
 * (S3-Endpoint zeigt ins Leere; Seeding-Fehler duerfen den Start nicht verhindern).
 */
@SpringBootTest(properties = {
		"storage.type=s3",
		"storage.s3.bucket=test-bucket",
		"storage.s3.endpoint=http://127.0.0.1:1",
		"storage.s3.path-style=true",
		"storage.seed.enabled=false",
		"aws.accessKeyId=test",
		"aws.secretAccessKey=test"
})
class S3ProfileContextTest {

	@Autowired
	ImageStorage storage;

	@Test
	void usesS3StorageWhenConfigured() {
		assertThat(storage).isInstanceOf(S3ImageStorage.class);
		assertThat(storage.describe()).isEqualTo("s3://test-bucket");
		assertThat(((S3ImageStorage) storage).accessScope()).isEqualTo("task-role");
	}
}
