package com.darkmusic.aiforgotthesecards.web.controller;

import com.darkmusic.aiforgotthesecards.business.entities.AiModel;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.AiModelDAO;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.StreamSupport;

@RestController
public class AiController {
    private final OllamaApi ollamaApi;
    private final AiModelDAO aiModelDAO;

    @Autowired
    public AiController(OllamaApi ollamaApi, AiModelDAO aiModelDAO) {
        this.ollamaApi = ollamaApi;
        this.aiModelDAO = aiModelDAO;
    }

    @GetMapping("/ai/models")
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

    @GetMapping("/ai/model/pull")
    public Flux<OllamaApi.ProgressResponse> pullModel(@RequestParam("modelName") String modelName) {
        var request = new OllamaApi.PullModelRequest(modelName, false, null, null, true);
        return ollamaApi.pullModel(request);
    }
}
