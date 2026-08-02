package com.darkmusic.aiforgotthesecards.business.entities.services;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.TtsAudio;
import com.darkmusic.aiforgotthesecards.business.entities.TtsPreset;
import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.CardDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.DeckDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.TtsAudioDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.TtsPresetDAO;
import com.darkmusic.aiforgotthesecards.web.contracts.DeckTtsSettings;
import com.darkmusic.aiforgotthesecards.web.contracts.TtsAudioResponse;
import com.darkmusic.aiforgotthesecards.web.contracts.TtsPresetInfo;
import com.darkmusic.aiforgotthesecards.web.contracts.TtsSynthesisRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TtsService {
    private static final String WAV_CONTENT_TYPE = "audio/wav";

    private final DeckDAO deckDAO;
    private final CardDAO cardDAO;
    private final TtsPresetDAO ttsPresetDAO;
    private final TtsAudioDAO ttsAudioDAO;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Path storageDir;
    private final URI synthesizeUri;
    private final Duration requestTimeout;

    public TtsService(
            DeckDAO deckDAO,
            CardDAO cardDAO,
            TtsPresetDAO ttsPresetDAO,
            TtsAudioDAO ttsAudioDAO,
            @Value("${tts.storage-dir:${TTS_STORAGE_DIR:./data/tts}}") String storageDir,
            @Value("${tts.service-url:${TTS_SERVICE_URL:http://localhost:8091}}") String serviceUrl,
            @Value("${tts.request-timeout:${TTS_REQUEST_TIMEOUT:PT10M}}") Duration requestTimeout
    ) {
        this.deckDAO = deckDAO;
        this.cardDAO = cardDAO;
        this.ttsPresetDAO = ttsPresetDAO;
        this.ttsAudioDAO = ttsAudioDAO;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.objectMapper = new ObjectMapper()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
        this.synthesizeUri = URI.create(serviceUrl.replaceAll("/+$", "") + "/synthesize");
        this.requestTimeout = requestTimeout;
    }

    public DeckTtsSettings getDeckSettings(Long deckId, User user) {
        Deck deck = findOwnedDeck(deckId, user);
        return toSettings(deck, ttsPresetDAO.findByDeckOrderBySortOrderAscIdAsc(deck));
    }

    public DeckTtsSettings saveDeckSettings(Long deckId, DeckTtsSettings settings, User user) {
        Deck deck = findOwnedDeck(deckId, user);
        deck.setTtsEnabled(settings.isTtsEnabled());
        deck.setTtsModelId(blankToNull(settings.getTtsModelId()));

        List<TtsPreset> existing = ttsPresetDAO.findByDeckOrderBySortOrderAscIdAsc(deck);
        Set<Long> seenIds = new HashSet<>();
        List<TtsPreset> saved = new ArrayList<>();
        int index = 0;

        for (TtsPresetInfo info : settings.getPresets() == null ? List.<TtsPresetInfo>of() : settings.getPresets()) {
            if (isBlank(info.getName())) continue;
            TtsPreset preset = null;
            if (info.getId() != null) {
                preset = existing.stream()
                        .filter(p -> info.getId().equals(p.getId()))
                        .findFirst()
                        .orElse(null);
            }
            if (preset == null) {
                preset = new TtsPreset();
                preset.setDeck(deck);
            }
            preset.setName(info.getName().trim());
            preset.setSpeaker(blankToNull(info.getSpeaker()));
            preset.setLanguage(blankToNull(info.getLanguage()));
            preset.setCaption(blankToNull(info.getCaption()));
            preset.setAdvancedConfigJson(normalizeAdvancedConfig(info.getAdvancedConfigJson()));
            preset.setSortOrder(index++);
            TtsPreset savedPreset = ttsPresetDAO.save(preset);
            saved.add(savedPreset);
            if (savedPreset.getId() != null) {
                seenIds.add(savedPreset.getId());
            }
        }

        for (TtsPreset preset : existing) {
            if (preset.getId() != null && !seenIds.contains(preset.getId())) {
                for (TtsAudio audio : ttsAudioDAO.findByPreset(preset)) {
                    audio.setPreset(null);
                    ttsAudioDAO.save(audio);
                }
                ttsPresetDAO.deleteById(preset.getId());
            }
        }

        Long requestedDefaultId = settings.getTtsDefaultPresetId();
        boolean defaultStillExists = requestedDefaultId != null && saved.stream()
                .anyMatch(p -> requestedDefaultId.equals(p.getId()));
        deck.setTtsDefaultPresetId(defaultStillExists
                ? requestedDefaultId
                : saved.stream().min(Comparator.comparingInt(TtsPreset::getSortOrder)).map(TtsPreset::getId).orElse(null));
        deckDAO.save(deck);

        return toSettings(deck, ttsPresetDAO.findByDeckOrderBySortOrderAscIdAsc(deck));
    }

    public TtsAudioResponse generateForCard(Long cardId, User user) throws IOException, InterruptedException {
        Card card = cardDAO.findById(cardId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
        verifyCardOwner(card, user);

        Deck deck = card.getDeck();
        if (!deck.isTtsEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TTS is not enabled for this deck");
        }
        if (isBlank(deck.getTtsModelId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No TTS model is configured for this deck");
        }
        if (isBlank(card.getTtsText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This card has no TTS text");
        }

        TtsPreset preset = resolvePreset(deck, card);
        ObjectNode cacheInput = buildCacheInput(deck, card, preset);
        String cacheKey = sha256(objectMapper.writeValueAsString(cacheInput));

        var existing = ttsAudioDAO.findByCardAndCacheKey(card, cacheKey);
        if (existing.isPresent() && Files.exists(Path.of(existing.get().getFilePath()))) {
            return toResponse(existing.get(), true);
        }

        Files.createDirectories(storageDir);
        Path audioPath = storageDir.resolve(cacheKey + ".wav");
        if (!Files.exists(audioPath)) {
            byte[] wav = synthesize(deck, card, preset);
            Files.write(audioPath, wav);
        }

        TtsAudio audio = existing.orElseGet(TtsAudio::new);
        audio.setDeck(deck);
        audio.setCard(card);
        audio.setPreset(preset);
        audio.setCacheKey(cacheKey);
        audio.setFilePath(audioPath.toAbsolutePath().normalize().toString());
        audio.setContentType(WAV_CONTENT_TYPE);
        audio.setGeneratedAt(System.currentTimeMillis());
        audio.setModelId(deck.getTtsModelId().trim());
        audio.setPresetName(preset == null ? null : preset.getName());
        audio.setPresetConfigJson(objectMapper.writeValueAsString(cacheInput.get("preset")));
        TtsAudio saved = ttsAudioDAO.save(audio);

        return toResponse(saved, false);
    }

    public TtsAudio getOwnedAudio(Long audioId, User user) {
        TtsAudio audio = ttsAudioDAO.findById(audioId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));
        if (audio.getDeck() == null || audio.getDeck().getUser() == null ||
                !audio.getDeck().getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found");
        }
        return audio;
    }

    public Resource audioResource(TtsAudio audio) {
        Path path = Path.of(audio.getFilePath()).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio file not found");
        }
        return new FileSystemResource(path);
    }

    private byte[] synthesize(Deck deck, Card card, TtsPreset preset) throws IOException, InterruptedException {
        TtsSynthesisRequest request = new TtsSynthesisRequest();
        request.setModelId(deck.getTtsModelId().trim());
        request.setText(card.getTtsText().trim());
        if (preset != null) {
            request.setCaption(preset.getCaption());
            request.setSpeaker(preset.getSpeaker());
            request.setLanguage(preset.getLanguage());
            request.setGenerationConfig(parseAdvancedConfig(preset.getAdvancedConfigJson()));
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(synthesizeUri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build();

        HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = new String(response.body(), StandardCharsets.UTF_8);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "TTS service failed: HTTP " + response.statusCode() + " " + body);
        }
        return response.body();
    }

    private TtsPreset resolvePreset(Deck deck, Card card) {
        Long presetId = card.getTtsPresetId() != null ? card.getTtsPresetId() : deck.getTtsDefaultPresetId();
        if (presetId == null) return null;
        TtsPreset preset = ttsPresetDAO.findById(presetId).orElse(null);
        if (preset == null || preset.getDeck() == null || !preset.getDeck().getId().equals(deck.getId())) {
            return null;
        }
        return preset;
    }

    private ObjectNode buildCacheInput(Deck deck, Card card, TtsPreset preset) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("modelId", deck.getTtsModelId().trim());
        root.put("text", card.getTtsText().trim());
        root.put("format", "wav");

        ObjectNode presetNode = objectMapper.createObjectNode();
        if (preset != null) {
            presetNode.put("id", preset.getId());
            presetNode.put("name", preset.getName());
            presetNode.put("speaker", preset.getSpeaker());
            presetNode.put("language", preset.getLanguage());
            presetNode.put("caption", preset.getCaption());
            presetNode.set("advancedConfig", parseAdvancedConfig(preset.getAdvancedConfigJson()));
        }
        root.set("preset", presetNode);
        return root;
    }

    private JsonNode parseAdvancedConfig(String raw) throws IOException {
        if (isBlank(raw)) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(raw);
    }

    private String normalizeAdvancedConfig(String raw) {
        if (isBlank(raw)) return null;
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(raw));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Advanced config must be valid JSON");
        }
    }

    private DeckTtsSettings toSettings(Deck deck, List<TtsPreset> presets) {
        DeckTtsSettings settings = new DeckTtsSettings();
        settings.setTtsEnabled(deck.isTtsEnabled());
        settings.setTtsModelId(deck.getTtsModelId());
        settings.setTtsDefaultPresetId(deck.getTtsDefaultPresetId());
        settings.setPresets(presets.stream().map(this::toPresetInfo).toList());
        return settings;
    }

    private TtsPresetInfo toPresetInfo(TtsPreset preset) {
        TtsPresetInfo info = new TtsPresetInfo();
        info.setId(preset.getId());
        info.setName(preset.getName());
        info.setSpeaker(preset.getSpeaker());
        info.setLanguage(preset.getLanguage());
        info.setCaption(preset.getCaption());
        info.setAdvancedConfigJson(preset.getAdvancedConfigJson());
        info.setSortOrder(preset.getSortOrder());
        return info;
    }

    private TtsAudioResponse toResponse(TtsAudio audio, boolean cached) {
        TtsAudioResponse response = new TtsAudioResponse();
        response.setId(audio.getId());
        response.setCardId(audio.getCard().getId());
        response.setDeckId(audio.getDeck().getId());
        response.setPresetId(audio.getPreset() == null ? null : audio.getPreset().getId());
        response.setCacheKey(audio.getCacheKey());
        response.setContentType(audio.getContentType());
        response.setGeneratedAt(audio.getGeneratedAt());
        response.setModelId(audio.getModelId());
        response.setPresetName(audio.getPresetName());
        response.setAudioUrl("/api/tts/audio/" + audio.getId());
        response.setCached(cached);
        return response;
    }

    private Deck findOwnedDeck(Long deckId, User user) {
        Deck deck = deckDAO.findById(deckId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));
        if (deck.getUser() == null || !deck.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found");
        }
        return deck;
    }

    private void verifyCardOwner(Card card, User user) {
        if (card.getDeck() == null || card.getDeck().getUser() == null ||
                !card.getDeck().getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute TTS cache key", e);
        }
    }
}
