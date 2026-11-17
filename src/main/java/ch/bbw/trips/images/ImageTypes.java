package ch.bbw.trips.images;

import java.util.Set;

/** Erlaubte Bildformate + Erkennung anhand der Magic Bytes (Content-Type des Clients wird nicht blind vertraut). */
final class ImageTypes {

	static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

	private ImageTypes() {
	}

	static String detect(byte[] d) {
		if (d == null || d.length < 12) return null;
		if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "image/jpeg";
		if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
		if (d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8') return "image/gif";
		if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P')
			return "image/webp";
		return null;
	}
}
