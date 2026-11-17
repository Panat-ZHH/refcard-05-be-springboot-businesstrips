package ch.bbw.trips.images;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Fehlerfaelle der S3-Anbindung ohne echten AWS-Zugriff (S3Client gemockt). */
class S3ImageStorageTest {

	S3Client s3 = mock(S3Client.class);
	S3ImageStorage storage = new S3ImageStorage(s3, "biztrips-images-test");

	static S3Exception s3Error(int status, String code) {
		return (S3Exception) S3Exception.builder()
				.statusCode(status)
				.awsErrorDetails(AwsErrorDetails.builder().errorCode(code).errorMessage(code).build())
				.message(code)
				.build();
	}

	@Test
	void accessDeniedIsTranslated() {
		when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(s3Error(403, "AccessDenied"));

		assertThatThrownBy(() -> storage.store("trips/1", new byte[]{1}, "image/jpeg"))
				.isInstanceOf(StorageAccessDeniedException.class)
				.hasMessageContaining("s3:PutObject")
				.hasMessageContaining("biztrips-images-test");
	}

	@Test
	void missingKeyIsEmpty() {
		when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().statusCode(404).build());
		assertThat(storage.load("trips/99")).isEmpty();
	}

	@Test
	void verifyAccessFailsWhenBucketMissing() {
		when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(s3Error(404, "NoSuchBucket"));
		assertThatThrownBy(storage::verifyAccess)
				.isInstanceOf(StorageUnavailableException.class)
				.hasMessageContaining("Bucket existiert nicht");
	}

	@Test
	void uploadUsesServerSideEncryption() {
		storage.store("trips/1", new byte[]{1}, "image/jpeg");
		verify(s3).putObject(argThat((PutObjectRequest r) ->
				r.serverSideEncryption() == ServerSideEncryption.AES256 && r.contentType().equals("image/jpeg")), any(RequestBody.class));
	}

	@Test
	void requiresBucket() {
		assertThatThrownBy(() -> new S3ImageStorage(s3, " ")).hasMessageContaining("S3_BUCKET");
	}
}
