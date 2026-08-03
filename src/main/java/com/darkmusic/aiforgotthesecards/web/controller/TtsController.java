package com.darkmusic.aiforgotthesecards.web.controller;

import com.darkmusic.aiforgotthesecards.business.entities.TtsAudio;
import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import com.darkmusic.aiforgotthesecards.business.entities.services.TtsService;
import com.darkmusic.aiforgotthesecards.web.contracts.DeckTtsSettings;
import com.darkmusic.aiforgotthesecards.web.contracts.TtsPlaybackItem;
import lombok.Getter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@RestController
public class TtsController {
    private static final long SSE_EMITTER_TIMEOUT_MS = 60 * 60 * 1000L;
    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;

    private final TtsService ttsService;
    private final UserDAO userDAO;

    public TtsController(TtsService ttsService, UserDAO userDAO) {
        this.ttsService = ttsService;
        this.userDAO = userDAO;
    }

    @GetMapping("/api/deck/{id}/tts")
    public DeckTtsSettings getDeckTtsSettings(Authentication authentication, @PathVariable long id) {
        return ttsService.getDeckSettings(id, currentUser(authentication));
    }

    @PutMapping("/api/deck/{id}/tts")
    public DeckTtsSettings saveDeckTtsSettings(
            Authentication authentication,
            @PathVariable long id,
            @RequestBody DeckTtsSettings settings
    ) {
        return ttsService.saveDeckSettings(id, settings, currentUser(authentication));
    }

    @GetMapping("/api/tts/card/{cardId}/items")
    public List<TtsPlaybackItem> getCardTtsItems(Authentication authentication, @PathVariable long cardId)
            throws IOException {
        return ttsService.getPlaybackItems(cardId, currentUser(authentication));
    }

    @PostMapping(value = "/api/tts/card/{cardId}/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateCardAudio(
            Authentication authentication,
            @PathVariable long cardId,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String variant,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        User user = currentUser(authentication);
        AtomicBoolean finished = new AtomicBoolean(false);
        SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT_MS);

        emitter.onTimeout(() -> {
            finished.set(true);
            emitter.complete();
        });
        emitter.onError(t -> finished.set(true));

        Thread.ofVirtual().name("tts-request-", 0L).start(() -> {
            if (!sendStatus(emitter, finished, "Starting TTS request")) {
                return;
            }

            Thread.ofVirtual().name("tts-heartbeat-", 0L).start(() -> {
                while (!finished.get()) {
                    try {
                        Thread.sleep(HEARTBEAT_INTERVAL_MS);
                        if (!finished.get()) {
                            sendStatus(emitter, finished,
                                    "Still working; model load or generation may take several minutes");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });

            try {
                var response = ttsService.generateForCard(cardId, target, variant, user, force,
                        message -> sendStatus(emitter, finished, message));
                finished.set(true);
                emitter.send(SseEmitter.event().name("done").data(response));
                emitter.complete();
            } catch (Exception e) {
                finished.set(true);
                String msg = e.getMessage() != null ? e.getMessage() : "Unknown error occurred";
                try {
                    emitter.send(SseEmitter.event().name("error").data(msg));
                } catch (IOException ignored) {
                    // Client already disconnected.
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private boolean sendStatus(SseEmitter emitter, AtomicBoolean finished, String message) {
        if (finished.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name("status").data(message));
            return true;
        } catch (IOException e) {
            finished.set(true);
            emitter.completeWithError(e);
            return false;
        }
    }

    @GetMapping("/api/tts/audio/{audioId}")
    public ResponseEntity<Resource> getAudio(Authentication authentication, @PathVariable long audioId) {
        TtsAudio audio = ttsService.getOwnedAudio(audioId, currentUser(authentication));
        Resource resource = ttsService.audioResource(audio);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(audio.getContentType()))
                .body(resource);
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Login required");
        }
        return userDAO.findByUsername(authentication.getName()).orElseThrow(() ->
                new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Login required"));
    }
}
