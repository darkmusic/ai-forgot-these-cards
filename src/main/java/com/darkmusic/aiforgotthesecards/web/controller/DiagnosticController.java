package com.darkmusic.aiforgotthesecards.web.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.Map;

@RestController
public class DiagnosticController {

  @GetMapping("/api/session-info")
  public Map<String, Object> getSessionInfo(HttpSession session) {
    return Map.of(
        "maxInactiveInterval", session.getMaxInactiveInterval(),
        "maxInactiveIntervalMinutes", session.getMaxInactiveInterval() / 60,
        "creationTime", new Date(session.getCreationTime()),
        "lastAccessedTime", new Date(session.getLastAccessedTime()));
  }
}