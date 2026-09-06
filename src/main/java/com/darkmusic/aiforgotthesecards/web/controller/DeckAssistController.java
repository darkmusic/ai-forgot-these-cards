package com.darkmusic.aiforgotthesecards.web.controller;

import com.darkmusic.aiforgotthesecards.business.entities.services.DeckAssistService;
import com.darkmusic.aiforgotthesecards.web.contracts.DeckAssistRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
public class DeckAssistController {
    private final DeckAssistService service;

    public DeckAssistController(DeckAssistService service) { this.service = service; }

    @PostMapping(value = "/api/ai/deck-assist", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter assist(@RequestBody DeckAssistRequest request) {
        try { service.validateRequest(request); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }

        SseEmitter emitter = new SseEmitter(15 * 60 * 1000L);
        AtomicBoolean finished = new AtomicBoolean();
        emitter.onCompletion(() -> finished.set(true));
        emitter.onError(e -> finished.set(true));
        emitter.onTimeout(() -> { finished.set(true); emitter.complete(); });
        Thread.ofVirtual().name("deck-assist").start(() -> {
            Thread heartbeat = null;
            try {
                emitter.send(SseEmitter.event().comment("processing"));
                heartbeat = Thread.ofVirtual().name("deck-assist-heartbeat").start(() -> {
                    try {
                        while (!finished.get()) {
                            Thread.sleep(30_000);
                            if (!finished.get()) emitter.send(SseEmitter.event().comment("heartbeat"));
                        }
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    catch (IOException | IllegalStateException e) { finished.set(true); }
                });
                var result = service.assist(request);
                if (!finished.get()) emitter.send(SseEmitter.event().name("done").data(result, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                if (!finished.get()) {
                    try { emitter.send(SseEmitter.event().name("error").data(Map.of("message", errorMessage(e)), MediaType.APPLICATION_JSON)); }
                    catch (IOException | IllegalStateException ignored) { /* Client disconnected. */ }
                    emitter.complete();
                }
            } finally {
                finished.set(true);
                if (heartbeat != null) heartbeat.interrupt();
            }
        });
        return emitter;
    }

    private static String errorMessage(Exception e) {
        if (e instanceof JsonProcessingException) return "The AI returned malformed or incomplete JSON. No changes were applied; try fewer cards or retry.";
        if (e instanceof IllegalArgumentException) return e.getMessage();
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.SocketTimeoutException) return "The AI endpoint timed out. Try fewer cards or increase the configured AI timeout.";
        }
        // Do not expose provider bodies, credentials or full deck contents in errors.
        return "The AI endpoint request failed. Check its availability, credentials, model and context limit, then retry. No changes were applied.";
    }
}
