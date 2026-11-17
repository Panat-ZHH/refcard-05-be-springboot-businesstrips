package ch.bbw.trips.images;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Uebersetzt Storage-Fehler in verstaendliche HTTP-Antworten (RFC 9457 Problem Details). */
@RestControllerAdvice
public class StorageExceptionAdvice {

	@ExceptionHandler(StorageAccessDeniedException.class)
	ProblemDetail accessDenied(StorageAccessDeniedException e) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		pd.setTitle("Bilder-Storage: Zugriff verweigert");
		return pd;
	}

	@ExceptionHandler(StorageUnavailableException.class)
	ProblemDetail unavailable(StorageUnavailableException e) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		pd.setTitle("Bilder-Storage nicht verfuegbar");
		return pd;
	}
}
