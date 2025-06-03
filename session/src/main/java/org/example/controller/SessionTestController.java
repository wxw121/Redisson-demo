package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/session")
public class SessionTestController {

    @PostMapping("/setAttribute")
    public Map<String, Object> setAttribute(
            @RequestParam String key,
            @RequestParam String value,
            HttpSession session) {
        log.info("Setting session attribute: {} = {}", key, value);
        session.setAttribute(key, value);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Session attribute set successfully");
        response.put("sessionId", session.getId());
        return response;
    }

    @GetMapping("/getAttribute")
    public Map<String, Object> getAttribute(
            @RequestParam String key,
            HttpSession session) {
        Object value = session.getAttribute(key);
        log.info("Getting session attribute: {} = {}", key, value);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("value", value);
        response.put("sessionId", session.getId());
        return response;
    }

    @GetMapping("/info")
    public Map<String, Object> getSessionInfo(HttpSession session) {
        Map<String, Object> info = new HashMap<>();
        info.put("sessionId", session.getId());
        info.put("creationTime", Instant.ofEpochMilli(session.getCreationTime()));
        info.put("lastAccessedTime", Instant.ofEpochMilli(session.getLastAccessedTime()));
        info.put("maxInactiveInterval", session.getMaxInactiveInterval());
        info.put("new", session.isNew());

        log.info("Session info: {}", info);
        return info;
    }

    @DeleteMapping("/invalidate")
    public Map<String, Object> invalidateSession(HttpSession session) {
        String sessionId = session.getId();
        session.invalidate();
        log.info("Session invalidated: {}", sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Session invalidated successfully");
        response.put("invalidatedSessionId", sessionId);
        return response;
    }
}
