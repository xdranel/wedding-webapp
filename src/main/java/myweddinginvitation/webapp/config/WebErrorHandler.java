package myweddinginvitation.webapp.config;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class WebErrorHandler implements AccessDeniedHandler {
	private static final Logger log = LoggerFactory.getLogger(WebErrorHandler.class);

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		log.warn("Access denied [reference={}]", reference());
		response.sendError(HttpServletResponse.SC_FORBIDDEN);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ModelAndView forbidden() {
		return error("error/403", HttpStatus.FORBIDDEN);
	}

	@ExceptionHandler(Exception.class)
	ModelAndView internalError() {
		String reference = reference();
		log.error("Request failed [reference={}]", reference);
		ModelAndView error = error("error/500", HttpStatus.INTERNAL_SERVER_ERROR);
		error.addObject("reference", reference);
		return error;
	}

	private ModelAndView error(String view, HttpStatus status) {
		ModelAndView error = new ModelAndView(view);
		error.setStatus(status);
		return error;
	}

	private String reference() {
		return UUID.randomUUID().toString();
	}
}
