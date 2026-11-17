package ch.bbw.trips.images;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Bilder-Storage auf Amazon S3.
 * <p>
 * Credentials: {@code DefaultCredentialsProvider} des AWS SDK - auf ECS die Task-Rolle
 * ({@code taskRoleArn}), lokal die Umgebungsvariablen fuer MinIO. Es stehen keine Zugangsdaten im Code.
 * <p>
 * <b>Least Privilege im AWS Academy Learner Lab:</b> Eigene IAM-Rollen ({@code iam:CreateRole}) sind dort
 * gesperrt, die Task-Rolle ist zwangsweise die breit berechtigte {@code LabRole}. Ist
 * {@code storage.s3.scoped-role-arn} gesetzt, nimmt das Backend diese Rolle per STS {@code AssumeRole} mit
 * einer <i>Session Policy</i> an ({@code iam/s3-least-privilege-policy.json}). Die effektiven Rechte der
 * Session sind die Schnittmenge aus Rollen- und Session-Policy - also ausschliesslich
 * {@code s3:ListBucket} auf den Bucket und {@code s3:GetObject/PutObject} auf {@code trips/*}.
 * In einem regulaeren AWS-Account wird dieselbe Policy einer eigenen Task-Rolle zugewiesen.
 * <p>
 * Fehlerfaelle: 403 (fehlende IAM-Rechte) wird als {@link StorageAccessDeniedException} gemeldet,
 * damit die API eine verstaendliche 503-Antwort liefert statt eines generischen 500.
 */
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3ImageStorage implements ImageStorage {

	private static final Logger log = LoggerFactory.getLogger(S3ImageStorage.class);

	static final String SESSION_NAME = "biztrips-backend-s3";

	private final S3Client s3;
	private final String bucket;
	private final String accessScope;

	@Autowired
	public S3ImageStorage(StorageProperties props) {
		this(buildClient(props.s3()), props.s3().bucket(), isScoped(props.s3()) ? "session-policy:bucket-only" : "task-role");
	}

	S3ImageStorage(S3Client s3, String bucket) {
		this(s3, bucket, "task-role");
	}

	private S3ImageStorage(S3Client s3, String bucket, String accessScope) {
		if (bucket == null || bucket.isBlank()) {
			throw new IllegalStateException("storage.type=s3, aber kein Bucket konfiguriert (Umgebungsvariable S3_BUCKET setzen)");
		}
		this.s3 = s3;
		this.bucket = bucket;
		this.accessScope = accessScope;
		log.info("Bilder-Storage: s3://{} (Zugriff: {})", bucket, accessScope);
	}

	private static boolean isScoped(StorageProperties.S3 cfg) {
		return cfg.scopedRoleArn() != null && !cfg.scopedRoleArn().isBlank();
	}

	/** Session Policy aus classpath:iam/s3-least-privilege-policy.json mit eingesetztem Bucket-Namen. */
	static String sessionPolicy(String bucket) {
		try (InputStream in = S3ImageStorage.class.getResourceAsStream("/iam/s3-least-privilege-policy.json")) {
			if (in == null) throw new IllegalStateException("iam/s3-least-privilege-policy.json fehlt");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("${BUCKET}", bucket);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static AwsCredentialsProvider credentials(StorageProperties.S3 cfg) {
		AwsCredentialsProvider base = DefaultCredentialsProvider.builder().build();
		if (!isScoped(cfg)) return base;
		StsClient sts = StsClient.builder().region(Region.of(cfg.region())).credentialsProvider(base).build();
		return StsAssumeRoleCredentialsProvider.builder()
				.stsClient(sts)
				.refreshRequest(AssumeRoleRequest.builder()
						.roleArn(cfg.scopedRoleArn())
						.roleSessionName(SESSION_NAME)
						.policy(sessionPolicy(cfg.bucket()))
						.durationSeconds(3600)
						.build())
				.build();
	}

	private static S3Client buildClient(StorageProperties.S3 cfg) {
		S3ClientBuilder b = S3Client.builder().region(Region.of(cfg.region())).credentialsProvider(credentials(cfg));
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

	public String accessScope() {
		return accessScope;
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
