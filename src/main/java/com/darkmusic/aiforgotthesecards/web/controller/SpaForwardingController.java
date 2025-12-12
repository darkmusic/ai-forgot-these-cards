package com.darkmusic.aiforgotthesecards.web.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Serves the React SPA for deep links when running without Nginx.
 *
 * For non-API routes, we forward to index.html so the client-side router can handle it.
 */
@Controller
public class SpaForwardingController {

    @GetMapping({"/", "/login", "/logout", "/home"})
    public String forwardKnownSpaRoutes() {
        return "forward:/index.html";
    }

    // Forward any other non-API route (including nested routes).
    // Note: With Spring MVC's PathPatternParser, you cannot have more pattern data after a "**".
    // So we match ".../**" and then ensure (in code) we don't forward requests that look like static files.
    @GetMapping({
            "/{first:^(?!api$|assets$|actuator$|swagger-ui$|v3$)[^\\.]+$}/**"
    })
    public String forwardNestedSpaRoutes(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.contains(".")) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        return "forward:/index.html";
    }
}
