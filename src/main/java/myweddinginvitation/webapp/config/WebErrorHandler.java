package myweddinginvitation.webapp.config;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

@Controller
@ControllerAdvice
public class WebErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(WebErrorHandler.class);

    @GetMapping("/forbidden")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    String forbiddenPage() {
        log.warn("Access denied [reference={}]", reference());
        return "error/403";
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
