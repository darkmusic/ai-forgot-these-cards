package com.darkmusic.aiforgotthesecards.web.controller;

import com.darkmusic.aiforgotthesecards.business.entities.AiChat;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.AiChatDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import com.darkmusic.aiforgotthesecards.web.contracts.AiAskRequest;
import lombok.Getter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@RestController
public class AiController {

    private static final String FINAL_MESSAGE_MARKER = "<|channel|>final<|message|>";

    /**
     * SseEmitter lifetime. Set longer than the browser AbortController (12 min) so the
     * browser's timer always fires first and can display a clean user-visible timeout message.
     */
    private static final long SSE_EMITTER_TIMEOUT_MS = 15 * 60 * 1000L; // 15 min

    /**
     * Interval between SSE heartbeat comments. Must be shorter than every proxy/network
     * idle-timeout between the browser and this app (nginx proxy_read_timeout = 660 s).
     */
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L; // 30 s

    private final UserDAO userDAO;
    private final AiChatDAO aiChatDAO;
    private final ChatClient chatClient;

    public AiController(UserDAO userDAO, AiChatDAO aiChatDAO, ChatClient.Builder chatClientBuilder) {
        this.userDAO = userDAO;
        this.aiChatDAO = aiChatDAO;
        this.chatClient = chatClientBuilder.defaultOptions(new OpenAiChatOptions()).build();
    }

    /**
     * AI chat endpoint, streamed via Server-Sent Events (SSE).
     *
     * <h3>Why SSE instead of a plain JSON response?</h3>
     * <p>Long AI inference takes minutes. Without SSE the browser receives no HTTP bytes until the
     * entire response is ready. Browsers enforce an internal Time-To-First-Byte (TTFB) deadline
     * (~300 s in Firefox, shorter in some setups). When that fires the browser closes the
     * connection and raises {@code TypeError: NetworkError} — {@code signal.aborted} is
     * {@code false} because the shutdown came from the browser itself, not our
     * {@code AbortController}, so the real cause is hidden.</p>
     *
     * <h3>How this endpoint fixes the problem</h3>
     * <ol>
     *   <li>Starts the SSE response immediately so the browser gets its first bytes at once,
     *       satisfying any TTFB deadline.</li>
     *   <li>Sends a heartbeat comment every {@value #HEARTBEAT_INTERVAL_MS} ms, resetting every
     *       intermediate proxy idle-timer (e.g. nginx {@code proxy_read_timeout}).</li>
     *   <li>Sends a {@code done} event with the full answer when inference completes.</li>
     *   <li>Sends an {@code error} event if the AI or DB layer throws.</li>
     * </ol>
     *
     * <h3>Timeout cascade</h3>
     * <pre>
     * Spring AI → llama.cpp read timeout     : 10 min  (AI_CLIENT_READ_TIMEOUT)
     * Nginx proxy_read_timeout (per-heartbeat): 11 min
     * Browser AbortController                : 12 min  (always fires before emitter timeout)
     * SseEmitter timeout                     : 15 min  (safety valve)
     * </pre>
     */
    @PostMapping(value = "/api/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatWithAi(@RequestBody AiAskRequest request) {
        AtomicBoolean finished = new AtomicBoolean(false);
        SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT_MS);

        emitter.onTimeout(() -> {
            finished.set(true);
            emitter.complete();
        });
        emitter.onError(t -> finished.set(true));

        // All work runs on a virtual thread so no Tomcat worker thread is tied up.
        Thread.ofVirtual().name("ai-request-", 0L).start(() -> {

            // Send the very first event immediately. This is the first byte the browser
            // receives, which satisfies any TTFB deadline and prevents NetworkErrors caused
            // by the browser's internal response-start timeout.
            try {
                emitter.send(SseEmitter.event().comment("processing"));
            } catch (IOException e) {
                emitter.completeWithError(e);
                return;
            }

            // Heartbeat virtual thread: keeps the SSE connection (and every intermediate
            // proxy) alive by periodically sending an SSE comment.
            Thread.ofVirtual().name("ai-heartbeat-", 0L).start(() -> {
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
                        // Emitter closed (client disconnected or error); stop sending.
                        break;
                    }
                }
            });

            try {
                // Synchronous model call — blocks until the full response is available.
                String answer = chatClient.prompt()
                        .user(request.getQuestion())
                        .call()
                        .content();

                // Handle "thinking" models: keep only the text after the last marker.
                if (answer != null && !answer.isBlank()) {
                    int idx = answer.lastIndexOf(FINAL_MESSAGE_MARKER);
                    if (idx != -1) {
                        answer = answer.substring(idx + FINAL_MESSAGE_MARKER.length()).trim();
                    }
                }

                // Persist Q&A.
                var aiChat = new AiChat();
                aiChat.setQuestion(request.getQuestion());
                aiChat.setAnswer(answer);
                aiChat.setCreatedAt(System.currentTimeMillis());
                aiChat.setUser(userDAO.findById(request.getUserId()).orElseThrow());
                aiChatDAO.save(aiChat);

                // Signal completion to the client with the full answer.
                finished.set(true);
                emitter.send(SseEmitter.event().name("done").data(answer));
                emitter.complete();

            } catch (Exception e) {
                finished.set(true);
                String msg = e.getMessage() != null ? e.getMessage() : "Unknown error occurred";
                try {
                    emitter.send(SseEmitter.event().name("error").data(msg));
                } catch (IOException ignored) {
                    // Client already disconnected; error event cannot be delivered.
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
