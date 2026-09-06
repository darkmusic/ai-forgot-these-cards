package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.services.DeckAssistService;
import com.darkmusic.aiforgotthesecards.web.contracts.DeckAssistRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in inference smoke tests using synthetic cards; never accesses the application database. */
@EnabledIfSystemProperty(named = "deckAssist.smoke", matches = "true")
class DeckAssistLiveTests {
    @ParameterizedTest
    @ValueSource(strings = {"generate", "correct", "enhance", "duplicates", "tags", "gaps"})
    void configuredCompatibleEndpointProducesValidatedResults(String operation) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(operation.equals(System.getProperty("deckAssist.operation", operation)));
        var factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMinutes(2));
        var api = OpenAiApi.builder().baseUrl(System.getProperty("deckAssist.url", "http://localhost:8087"))
                .apiKey(System.getenv().getOrDefault("DECK_ASSIST_TEST_API_KEY", "not-set"))
                .restClientBuilder(RestClient.builder().requestFactory(factory)).build();
        var model = OpenAiChatModel.builder().openAiApi(api).defaultOptions(OpenAiChatOptions.builder()
                .model(System.getProperty("deckAssist.model", "muse-glimmer-30b-q3")).maxTokens(4096).build()).build();
        org.springframework.ai.chat.model.ChatModel recordingModel = new org.springframework.ai.chat.model.ChatModel() {
            @Override public org.springframework.ai.chat.prompt.ChatOptions getDefaultOptions() { return model.getDefaultOptions(); }
            @Override public org.springframework.ai.chat.model.ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
                var response = model.call(prompt);
                try { java.nio.file.Files.writeString(java.nio.file.Path.of("target", "deck-assist-live-" + operation + ".json"), response.getResult().getOutput().getText()); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                return response;
            }
        };
        var service = new DeckAssistService(recordingModel, new ObjectMapper());
        var request = new DeckAssistRequest(operation, "General knowledge", List.of(
                new DeckAssistRequest.DraftCard("1", "Capital of France?", "Paris", List.of("Geography")),
                new DeckAssistRequest.DraftCard("2", "French capital city?", "Paris", List.of()),
                new DeckAssistRequest.DraftCard("3", "Water formula?", "H20", List.of())),
                1, "beginner", List.of("Chemistry"), "Keep card answers brief. For topic suggestions, suggest at most three topics.");
        var result = service.assist(request);
        assertTrue(result.isObject());
        System.out.println("Live deck assist validated: " + operation);
    }
}
