package ch.bbw.trips.images;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration des Bilder-Storage. Alle Werte kommen aus application*.properties bzw. Umgebungsvariablen
 * (z. B. {@code S3_BUCKET}, {@code AWS_REGION}) - keine Zugangsdaten im Code. Auf ECS kommen die
 * S3-Credentials ausschliesslich aus der Task-Rolle (DefaultCredentialsProvider).
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(String type, Local local, S3 s3, Seed seed, long maxFileSize) {

	public StorageProperties {
		if (type == null || type.isBlank()) type = "local";
		if (local == null) local = new Local(null);
		if (s3 == null) s3 = new S3(null, null, null, false, null);
		if (seed == null) seed = new Seed(true);
		if (maxFileSize <= 0) maxFileSize = 5 * 1024 * 1024;
	}

	public record Local(String dir) {
		public Local {
			if (dir == null || dir.isBlank()) dir = "./data/images";
		}
	}

	/**
	 * @param bucket   Name des S3-Buckets (Pflicht bei storage.type=s3)
	 * @param region   AWS-Region, Default us-east-1 (Learner Lab)
	 * @param endpoint optionaler Endpoint-Override, z. B. http://minio:9000 fuer lokale Entwicklung
	 * @param pathStyle Path-Style-Zugriff (fuer MinIO noetig)
	 * @param scopedRoleArn optional: Rolle, die per STS AssumeRole mit einer Session Policy angenommen wird,
	 *                      die nur diesen Bucket erlaubt (Least Privilege im Learner Lab, siehe S3ImageStorage)
	 */
	public record S3(String bucket, String region, String endpoint, boolean pathStyle, String scopedRoleArn) {
		public S3 {
			if (region == null || region.isBlank()) region = "us-east-1";
		}
	}

	public record Seed(boolean enabled) {
	}
}
