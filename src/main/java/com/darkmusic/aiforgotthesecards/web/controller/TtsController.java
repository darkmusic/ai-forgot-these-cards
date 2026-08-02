package com.darkmusic.aiforgotthesecards.web.controller;

import com.darkmusic.aiforgotthesecards.business.entities.TtsAudio;
import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import com.darkmusic.aiforgotthesecards.business.entities.services.TtsService;
import com.darkmusic.aiforgotthesecards.web.contracts.DeckTtsSettings;
import lombok.Getter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@RestController
public class TtsController {
    private static final long SSE_EMITTER_TIMEOUT_MS = 15 * 60 * 1000L;
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;

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

    @PostMapping(value = "/api/tts/card/{cardId}/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateCardAudio(Authentication authentication, @PathVariable long cardId) {
        User user = currentUser(authentication);
        AtomicBoolean finished = new AtomicBoolean(false);
        SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT_MS);

        emitter.onTimeout(() -> {
            finished.set(true);
            emitter.complete();
        });
        emitter.onError(t -> finished.set(true));

        Thread.ofVirtual().name("tts-request-", 0L).start(() -> {
            try {
                emitter.send(SseEmitter.event().comment("processing"));
            } catch (IOException e) {
                emitter.completeWithError(e);
                return;
            }

            Thread.ofVirtual().name("tts-heartbeat-", 0L).start(() -> {
                while (!finished.get()) {
                    try {
                        Thread.sleep(HEARTBEAT_INTERVAL_MS);
                        if (!finished.get()) {
                            emitter.send(SseEmitter.event().comment("heartbeat"));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        break;
                    }
                }
            });

            try {
                var response = ttsService.generateForCard(cardId, user);
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
