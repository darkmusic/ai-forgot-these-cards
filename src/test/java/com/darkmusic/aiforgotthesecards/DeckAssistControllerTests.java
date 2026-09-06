package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.services.DeckAssistService;
import com.darkmusic.aiforgotthesecards.web.contracts.DeckAssistRequest;
import com.darkmusic.aiforgotthesecards.web.controller.DeckAssistController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeckAssistControllerTests {
    private final DeckAssistService service = mock(DeckAssistService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(new DeckAssistController(service)).build();
    private static final String REQUEST = "{\"operation\":\"generate\",\"deckName\":\"Biology\",\"cards\":[],\"count\":1}";

    @Test void streamsProcessingAndValidatedDone() throws Exception {
        when(service.assist(any())).thenReturn(new ObjectMapper().readTree("{\"cards\":[{\"front\":\"A\",\"back\":\"B\"}]}"));
        var result = mvc.perform(post("/api/ai/deck-assist").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(request().asyncStarted()).andReturn();
        result.getAsyncResult(5000);
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().string(containsString(":processing")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(containsString("\"cards\"")));
        verify(service, times(1)).assist(any());
    }

    @Test void streamsActionableErrorWithoutProviderSecrets() throws Exception {
        when(service.assist(any())).thenThrow(new RuntimeException("secret provider body"));
        var result = mvc.perform(post("/api/ai/deck-assist").contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andReturn();
        result.getAsyncResult(5000);
        mvc.perform(asyncDispatch(result)).andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(containsString("No changes were applied")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("secret provider body"))));
    }

    @Test void rejectsBadRequestBeforeOpeningStream() throws Exception {
        doThrow(new IllegalArgumentException("Invalid count")).when(service).validateRequest(any(DeckAssistRequest.class));
        mvc.perform(post("/api/ai/deck-assist").contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isBadRequest());
        verify(service, never()).assist(any());
    }
}
