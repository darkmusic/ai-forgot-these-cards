package com.darkmusic.aiforgotthesecards.web.controller;

import com.darkmusic.aiforgotthesecards.business.entities.AiChat;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.AiChatDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import com.darkmusic.aiforgotthesecards.web.contracts.AiAskRequest;
import com.darkmusic.aiforgotthesecards.web.contracts.AiAskResponse;
import lombok.Getter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.stream.StreamSupport;

@Getter
@RestController
public class AiController {
    private static final String FINAL_MESSAGE_MARKER = "<|channel|>final<|message|>";

    private final UserDAO userDAO;
    private final AiChatDAO aiChatDAO;
    private final ChatClient chatClient;

    public AiController(UserDAO userDAO, AiChatDAO aiChatDAO, ChatClient.Builder chatClientBuilder) {
        this.userDAO = userDAO;
        this.aiChatDAO = aiChatDAO;
        this.chatClient = chatClientBuilder.defaultOptions(new OpenAiChatOptions()).build();
    }

    @PostMapping("/api/ai/chat")
    public AiAskResponse chatWithAi(@RequestBody AiAskRequest request) {
        // Send the request to the AI model
        var response = new AiAskResponse();
        response.setAnswer(this.chatClient.prompt().user(request.getQuestion()).call().content());

        // Handle "thinking" models: if the response contains the marker, keep only the text after the last marker.
        String answer = response.getAnswer();
        if (answer != null && !answer.isBlank()) {
            int idx = answer.lastIndexOf(FINAL_MESSAGE_MARKER);
            if (idx != -1) {
                response.setAnswer(answer.substring(idx + FINAL_MESSAGE_MARKER.length()).trim());
            }
        }

        // Save the chat to the database.
        // The intent for this is to let the user review their previous questions and answers.
        var aiChat = new AiChat();
        aiChat.setQuestion(request.getQuestion());
        aiChat.setAnswer(response.getAnswer());
        aiChat.setCreatedAt(System.currentTimeMillis());
        aiChat.setUser(userDAO.findById(request.getUserId()).orElseThrow());
        aiChatDAO.save(aiChat);

        // Return the response
        return response;
    }
}
