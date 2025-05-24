package com.darkmusic.aiforgotthesecards.web.controller;

import com.darkmusic.aiforgotthesecards.business.entities.AiChat;
import com.darkmusic.aiforgotthesecards.business.entities.AiModel;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.AiChatDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.AiModelDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import com.darkmusic.aiforgotthesecards.web.contracts.AiAskRequest;
import com.darkmusic.aiforgotthesecards.web.contracts.AiAskResponse;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.StreamSupport;

@RestController
public class AiController {
    private final OllamaApi ollamaApi;
    private final AiModelDAO aiModelDAO;
    private final UserDAO userDAO;
    private final AiChatDAO aiChatDAO;

    public AiController(OllamaApi ollamaApi, AiModelDAO aiModelDAO, UserDAO userDAO, AiChatDAO aiChatDAO) {
        this.ollamaApi = ollamaApi;
        this.aiModelDAO = aiModelDAO;
        this.userDAO = userDAO;
        this.aiChatDAO = aiChatDAO;
    }

    @GetMapping("/api/ai/models/sync")
    public void syncModels() {
        syncWithOllama(ollamaApi.listModels());
    }

    @GetMapping("/api/ai/models")
    public List<AiModel> getAiModels() {
        var models = ollamaApi.listModels();
        syncWithOllama(models);
        return models.models().stream().map(model -> new AiModel(model.hashCode(), model.name(), model.model())).toList();
    }

    void syncWithOllama(OllamaApi.ListModelResponse ollamaModels) {
        var aiModels = StreamSupport.stream(aiModelDAO.findAll().spliterator(), false).toList();
        for (var ollamaModel : ollamaModels.models()) {
            var aiModel = aiModels.stream().filter(model -> model.getName().equals(ollamaModel.name())).findFirst();
            if (aiModel.isEmpty()) {
                var newAiModel = new AiModel();
                newAiModel.setName(ollamaModel.name());
                newAiModel.setModel(ollamaModel.model());
                aiModelDAO.save(newAiModel);
            }
        }
    }

    @GetMapping("/api/ai/model/pull")
    public Flux<OllamaApi.ProgressResponse> pullModel(@RequestParam("modelName") String modelName) {
        var request = new OllamaApi.PullModelRequest(modelName, false, null, null, true);
        return ollamaApi.pullModel(request);
    }

    @PostMapping("/api/ai/ask")
    public AiAskResponse askAi(@RequestBody AiAskRequest request) {
        // Create a new chat request
        var ollamaRequest = new OllamaApi.ChatRequest(request.getModel(),
                List.of(new OllamaApi.Message(OllamaApi.Message.Role.USER, request.getQuestion(), null, null)),
                false, null, null, null, null);

        // Send the request to the AI model
        var response = new AiAskResponse();
        response.setAnswer(ollamaApi.chat(ollamaRequest).message().content().trim());

        // Save the chat to the database.
        // The intent for this is to let the user review their previous questions and answers.
        var aiChat = new AiChat();
        aiChat.setAiModel(aiModelDAO.findAiModelByModel(request.getModel()));
        aiChat.setQuestion(request.getQuestion());
        aiChat.setAnswer(response.getAnswer());
        aiChat.setCreatedAt(System.currentTimeMillis());
        aiChat.setUser(userDAO.findById(request.getUserId()).orElseThrow());
        aiChatDAO.save(aiChat);

        // Return the response
        return response;
    }
}
