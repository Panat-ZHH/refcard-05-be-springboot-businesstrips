package ch.bbw.trips.images;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Bilder-Storage auf Amazon S3.
 * <p>
 * Credentials: {@code DefaultCredentialsProvider} des AWS SDK - auf ECS die Task-Rolle
 * ({@code taskRoleArn}), lokal die Umgebungsvariablen fuer MinIO. Es stehen keine Zugangsdaten im Code.
 * <p>
 * Fehlerfaelle: 403 (fehlende IAM-Rechte) wird als {@link StorageAccessDeniedException} gemeldet,
 * damit die API eine verstaendliche 503-Antwort liefert statt eines generischen 500.
 */
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3ImageStorage implements ImageStorage {

	private static final Logger log = LoggerFactory.getLogger(S3ImageStorage.class);

	private final S3Client s3;
	private final String bucket;

	public S3ImageStorage(StorageProperties props) {
		this(buildClient(props.s3()), props.s3().bucket());
	}

	S3ImageStorage(S3Client s3, String bucket) {
		if (bucket == null || bucket.isBlank()) {
			throw new IllegalStateException("storage.type=s3, aber kein Bucket konfiguriert (Umgebungsvariable S3_BUCKET setzen)");
		}
		this.s3 = s3;
		this.bucket = bucket;
		log.info("Bilder-Storage: s3://{}", bucket);
	}

	private static S3Client buildClient(StorageProperties.S3 cfg) {
		S3ClientBuilder b = S3Client.builder().region(Region.of(cfg.region()));
		if (cfg.endpoint() != null && !cfg.endpoint().isBlank()) {
			b.endpointOverride(URI.create(cfg.endpoint()));
		}
		b.forcePathStyle(cfg.pathStyle());
		return b.build();
	}

	@Override
	public void store(String key, byte[] data, String contentType) {
		try {
			s3.putObject(PutObjectRequest.builder()
							.bucket(bucket).key(key).contentType(contentType)
							.serverSideEncryption(ServerSideEncryption.AES256)
							.build(),
					RequestBody.fromBytes(data));
		} catch (S3Exception e) {
			throw translate("PutObject", key, e);
		} catch (SdkClientException e) {
			throw new StorageUnavailableException("S3 nicht erreichbar: " + e.getMessage(), e);
		}
	}

	@Override
	public Optional<StoredImage> load(String key) {
		try {
			ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
			String type = bytes.response().contentType();
			return Optional.of(new StoredImage(bytes.asByteArray(), type != null ? type : "application/octet-stream"));
		} catch (NoSuchKeyException e) {
			return Optional.empty();
		} catch (S3Exception e) {
			if (e.statusCode() == 404) return Optional.empty();
			throw translate("GetObject", key, e);
		} catch (SdkClientException e) {
			throw new StorageUnavailableException("S3 nicht erreichbar: " + e.getMessage(), e);
		}
	}

	@Override
	public boolean exists(String key) {
		try {
			s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
			return true;
		} catch (NoSuchKeyException e) {
			return false;
		} catch (S3Exception e) {
			if (e.statusCode() == 404) return false;
			throw translate("HeadObject", key, e);
		}
	}

	@Override
	public List<String> list() {
		try {
			return s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).build())
					.contents().stream().map(S3Object::key).sorted().toList();
		} catch (S3Exception e) {
			throw translate("ListBucket", "*", e);
		}
	}

	@Override
	public String describe() {
		return "s3://" + bucket;
	}

	@Override
	public void verifyAccess() {
		try {
			// ListBucket mit max 1 Key: prueft Erreichbarkeit + Rechte, ohne Daten zu veraendern
			s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).maxKeys(1).build());
		} catch (S3Exception e) {
			throw translate("ListBucket", "*", e);
		} catch (SdkClientException e) {
			throw new StorageUnavailableException("S3 nicht erreichbar: " + e.getMessage(), e);
		}
	}

	private RuntimeException translate(String action, String key, S3Exception e) {
		if (e.statusCode() == 403) {
			String msg = "Zugriff verweigert: s3:" + action + " auf s3://" + bucket + "/" + key
					+ " - IAM-Policy der ECS-Task-Rolle bzw. Bucket-Policy pruefen (" + e.awsErrorDetails().errorCode() + ")";
			log.error(msg);
			return new StorageAccessDeniedException(msg, e);
		}
		if (e.statusCode() == 404 && "NoSuchBucket".equals(e.awsErrorDetails().errorCode())) {
			return new StorageUnavailableException("Bucket existiert nicht: " + bucket, e);
		}
		return new StorageUnavailableException("S3-Fehler bei " + action + " (" + e.statusCode() + "): " + e.getMessage(), e);
	}
}
