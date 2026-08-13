package myweddinginvitation.webapp.config;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class CalendarMethodErrorHandler {
	private static final Pattern SIGNED_CALENDAR = Pattern.compile(
			"^/i/[^/]+/[^/]+/[^/]+/calendar/[^/]+\\.ics$");

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	Object methodNotSupported(HttpRequestMethodNotSupportedException exception, HttpServletRequest request,
			HttpServletResponse response) {
		if (!SIGNED_CALENDAR.matcher(request.getRequestURI()).matches()) {
			return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(exception.getHeaders()).build();
		}
		response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
		ModelAndView unavailable = new ModelAndView("guest/unavailable");
		unavailable.setStatus(HttpStatus.NOT_FOUND);
		return unavailable;
	}
}
