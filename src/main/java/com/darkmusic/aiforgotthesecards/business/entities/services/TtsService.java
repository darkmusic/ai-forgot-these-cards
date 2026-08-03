package com.darkmusic.aiforgotthesecards.business.entities.services;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.TtsAudio;
import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.CardDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.DeckDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.TtsAudioDAO;
import com.darkmusic.aiforgotthesecards.web.contracts.DeckTtsSettings;
import com.darkmusic.aiforgotthesecards.web.contracts.TtsAudioResponse;
import com.darkmusic.aiforgotthesecards.web.contracts.TtsPlaybackItem;
import com.darkmusic.aiforgotthesecards.web.contracts.TtsSynthesisRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TtsService {
    private static final Logger log = LoggerFactory.getLogger(TtsService.class);
    private static final String WAV_CONTENT_TYPE = "audio/wav";
    private static final String DEFAULT_TTS_CONFIG = """
            {
              "provider": "indic-parler-tts",
              "targets": {
                "word": { "enabled": true, "displaySide": "FRONT", "voices": ["hindi", "urdu"] },
                "example": { "enabled": true, "displaySide": "BACK", "voices": ["hindi", "urdu"] }
              },
              "variants": {
                "hindi": {
                  "language": "hi",
                  "textSource": "devanagari",
                  "caption": "",
                  "speaker": "",
                  "generationConfig": {}
                },
                "urdu": {
                  "language": "ur",
                  "textSource": "urduScript",
                  "caption": "",
                  "speaker": "",
                  "generationConfig": {}
                }
              },
              "defaultVariant": "hindi",
              "showVariantControls": "both"
            }
            """;
    private static final Pattern TTS_BLOCK_PATTERN = Pattern.compile("(?s):::\\s*tts\\s*\\R(.*?)\\R:::");
    private static final Pattern VOICES_PATTERN = Pattern.compile("(?m)^\\s*([A-Za-z0-9_-]+)\\s*:\\s*\\R\\s*voices\\s*:\\s*\\[([^]]*)]");
    private static final Pattern FIELD_PATTERN = Pattern.compile("(?i)^\\s*(?:[-*]\\s*)?([\\p{L}\\p{N} _.-]+)\\s*:\\s*(.+?)\\s*$");
    private static final Pattern FIELD_OR_SECTION_PATTERN = Pattern.compile("(?i)^\\s*(?:[-*]\\s*)?([\\p{L}\\p{N} _.-]+)\\s*:\\s*(.*?)\\s*$");

    private final DeckDAO deckDAO;
    private final CardDAO cardDAO;
    private final TtsAudioDAO ttsAudioDAO;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Path storageDir;
    private final URI synthesizeUri;
    private final Duration requestTimeout;

    public TtsService(
            DeckDAO deckDAO,
            CardDAO cardDAO,
            TtsAudioDAO ttsAudioDAO,
            @Value("${tts.storage-dir:${TTS_STORAGE_DIR:./data/tts}}") String storageDir,
            @Value("${tts.service-url:${TTS_SERVICE_URL:http://localhost:8091}}") String serviceUrl,
            @Value("${tts.request-timeout:${TTS_REQUEST_TIMEOUT:PT30M}}") Duration requestTimeout
    ) {
        this.deckDAO = deckDAO;
        this.cardDAO = cardDAO;
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
        return toSettings(findOwnedDeck(deckId, user));
    }

    public DeckTtsSettings saveDeckSettings(Long deckId, DeckTtsSettings settings, User user) {
        Deck deck = findOwnedDeck(deckId, user);
        deck.setTtsEnabled(settings.isTtsEnabled());
        deck.setTtsModelId(blankToNull(settings.getTtsModelId()));
        deck.setTtsConfigJson(normalizeConfig(settings.getTtsConfigJson(), "Deck TTS config"));
        deckDAO.save(deck);
        return toSettings(deck);
    }

    public List<TtsPlaybackItem> getPlaybackItems(Long cardId, User user) throws IOException {
        Card card = findOwnedCard(cardId, user);
        Deck deck = card.getDeck();
        if (!deck.isTtsEnabled()) {
            return List.of();
        }
        return resolvePlaybackItems(deck, card).stream()
                .map(item -> toPlaybackItem(item, findCachedAudio(card, item)))
                .toList();
    }

    public TtsAudioResponse generateForCard(Long cardId, String target, String variant, User user)
            throws IOException, InterruptedException {
        return generateForCard(cardId, target, variant, user, false, message -> {});
    }

    public TtsAudioResponse generateForCard(Long cardId, String target, String variant, User user, Consumer<String> status)
            throws IOException, InterruptedException {
        return generateForCard(cardId, target, variant, user, false, status);
    }

    public TtsAudioResponse generateForCard(Long cardId, String target, String variant, User user, boolean force,
                                            Consumer<String> status)
            throws IOException, InterruptedException {
        long startedAt = System.currentTimeMillis();
        status(status, "Resolving TTS text and voice settings");
        Card card = findOwnedCard(cardId, user);
        Deck deck = card.getDeck();
        log.info("TTS generate requested cardId={} deckId={} target={} variant={} force={} userId={}",
                cardId, deck == null ? null : deck.getId(), target, variant, force, user == null ? null : user.getId());
        if (!deck.isTtsEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TTS is not enabled for this deck");
        }
        if (isBlank(deck.getTtsModelId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No TTS model is configured for this deck");
        }

        ResolvedTtsItem item = resolveRequestedItem(deck, card, target, variant);
        status(status, "Resolved " + item.label() + " from " + item.textSource());
        String cacheKey = cacheKeyFor(item);
        var existing = ttsAudioDAO.findByCardAndCacheKey(card, cacheKey);
        if (!force && existing.isPresent() && Files.exists(Path.of(existing.get().getFilePath()))) {
            log.info("TTS cache hit cardId={} audioId={} cacheKey={}", cardId, existing.get().getId(), cacheKey);
            status(status, "Using cached audio");
            return toResponse(existing.get(), true);
        }

        status(status, "Preparing audio cache");
        Files.createDirectories(storageDir);
        Path audioPath = storageDir.resolve(force ? cacheKey + "-" + System.currentTimeMillis() + ".wav" : cacheKey + ".wav");
        if (!Files.exists(audioPath)) {
            status(status, force
                    ? "Regenerating TTS audio"
                    : "Calling TTS sidecar; first run may download/load the model");
            Files.write(audioPath, synthesize(item, status));
        } else {
            log.info("TTS audio file already exists for cacheKey={} path={}", cacheKey, audioPath);
            status(status, "Reusing generated audio file");
        }

        status(status, "Saving generated audio metadata");
        TtsAudio audio = existing.orElseGet(TtsAudio::new);
        audio.setDeck(deck);
        audio.setCard(card);
        audio.setCacheKey(cacheKey);
        audio.setFilePath(audioPath.toAbsolutePath().normalize().toString());
        audio.setContentType(WAV_CONTENT_TYPE);
        audio.setGeneratedAt(System.currentTimeMillis());
        audio.setModelId(item.modelId());
        audio.setTarget(item.target());
        audio.setVariant(item.variant());
        audio.setLanguage(blankToNull(item.language()));
        audio.setTextSource(item.textSource());
        audio.setResolvedText(item.text());
        audio.setVoice(item.voice());
        audio.setSpeed(item.speed());
        audio.setConfigJson(objectMapper.writeValueAsString(item.cacheInput()));
        TtsAudio saved = ttsAudioDAO.save(audio);
        log.info("TTS generate completed cardId={} audioId={} target={} variant={} durationMs={}",
                cardId, saved.getId(), item.target(), item.variant(), System.currentTimeMillis() - startedAt);
        status(status, "Audio ready");
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

    private ResolvedTtsItem resolveRequestedItem(Deck deck, Card card, String target, String variant) throws IOException {
        List<ResolvedTtsItem> items = resolvePlaybackItems(deck, card);
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No TTS playback items resolved for this card");
        }
        String requestedTarget = blankToNull(target);
        String requestedVariant = blankToNull(variant);
        if (requestedTarget == null && requestedVariant == null) {
            return items.getFirst();
        }
        return items.stream()
                .filter(item -> requestedTarget == null || item.target().equalsIgnoreCase(requestedTarget))
                .filter(item -> requestedVariant == null || item.variant().equalsIgnoreCase(requestedVariant))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unable to resolve TTS item for target/variant"));
    }

    private List<ResolvedTtsItem> resolvePlaybackItems(Deck deck, Card card) throws IOException {
        ObjectNode config = effectiveConfig(deck, card);
        JsonNode targets = config.path("targets");
        JsonNode variants = config.path("variants");
        if (!targets.isObject() || !variants.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "TTS config must define object values for targets and variants");
        }

        Map<String, String> sources = collectTextSources(card);
        Map<String, Set<String>> blockVoices = parseTtsBlockVoices((safe(card.getFront()) + "\n" + safe(card.getBack())));
        String modelId = blankToNull(deck.getTtsModelId());
        List<ResolvedTtsItem> items = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> targetFields = targets.fields();
        while (targetFields.hasNext()) {
            Map.Entry<String, JsonNode> targetEntry = targetFields.next();
            String targetName = targetEntry.getKey();
            JsonNode targetConfig = targetEntry.getValue();
            if (!targetConfig.path("enabled").asBoolean(true)) {
                continue;
            }

            List<String> targetVariants = variantsForTarget(targetName, targetConfig, config, variants, blockVoices);
            for (String variantName : targetVariants) {
                JsonNode variantConfig = variants.path(variantName);
                if (!variantConfig.isObject()) {
                    continue;
                }
                String textSource = resolvedTextSource(targetName, targetConfig, variantName, variantConfig, sources);
                String text = sourceValue(sources, textSource);
                if (isBlank(text)) {
                    continue;
                }

                ObjectNode cacheInput = buildCacheInput(modelId, targetName, targetConfig, variantName, variantConfig,
                        textSource, text);
                items.add(new ResolvedTtsItem(
                        targetName,
                        variantName,
                        labelFor(targetName, variantName, countEnabledTargets(targets)),
                        textValue(variantConfig.path("language")),
                        textSource,
                        text.trim(),
                        displaySide(targetConfig),
                        modelId,
                        textValue(variantConfig.path("speaker")),
                        doubleValue(variantConfig.path("speed")),
                        cacheInput
                ));
            }
        }
        return items;
    }

    private ObjectNode effectiveConfig(Deck deck, Card card) throws IOException {
        ObjectNode config = parseObjectConfig(deck.getTtsConfigJson(), "Deck TTS config");
        if (config == null) {
            config = parseObjectConfig(DEFAULT_TTS_CONFIG, "Default TTS config");
        }
        ObjectNode cardConfig = parseObjectConfig(card.getTtsConfigJson(), "Card TTS config");
        if (cardConfig != null) {
            merge(config, cardConfig);
        }
        applyTtsBlockOverrides(config, safe(card.getFront()) + "\n" + safe(card.getBack()));
        return config;
    }

    private void applyTtsBlockOverrides(ObjectNode config, String markdown) {
        Map<String, Set<String>> voicesByTarget = parseTtsBlockVoices(markdown);
        if (voicesByTarget.isEmpty()) {
            return;
        }
        ObjectNode targets = config.withObject("/targets");
        voicesByTarget.forEach((target, voices) -> {
            ObjectNode targetConfig = targets.withObject("/" + target);
            ArrayNode voicesNode = objectMapper.createArrayNode();
            voices.forEach(voicesNode::add);
            targetConfig.set("voices", voicesNode);
        });
    }

    private Map<String, Set<String>> parseTtsBlockVoices(String markdown) {
        Map<String, Set<String>> voicesByTarget = new LinkedHashMap<>();
        Matcher blockMatcher = TTS_BLOCK_PATTERN.matcher(markdown);
        while (blockMatcher.find()) {
            Matcher voicesMatcher = VOICES_PATTERN.matcher(blockMatcher.group(1));
            while (voicesMatcher.find()) {
                Set<String> voices = new HashSet<>();
                for (String rawVoice : voicesMatcher.group(2).split(",")) {
                    String voice = rawVoice.trim();
                    if (!voice.isEmpty()) {
                        voices.add(stripQuotes(voice));
                    }
                }
                if (!voices.isEmpty()) {
                    voicesByTarget.put(voicesMatcher.group(1).trim(), voices);
                }
            }
        }
        return voicesByTarget;
    }

    private Map<String, String> collectTextSources(Card card) {
        Map<String, String> sources = new LinkedHashMap<>();
        putIfNotBlank(sources, "front", stripTtsBlocks(card.getFront()));
        putIfNotBlank(sources, "back", stripTtsBlocks(card.getBack()));

        String combined = safe(stripTtsBlocks(card.getFront())) + "\n" + safe(stripTtsBlocks(card.getBack()));
        String prefix = "";
        for (String line : combined.split("\\R")) {
            Matcher sectionMatcher = FIELD_OR_SECTION_PATTERN.matcher(line);
            if (sectionMatcher.matches()) {
                String sectionKey = normalizeSemanticSourceName(sectionMatcher.group(1));
                if (sectionKey.contains("example")) {
                    prefix = "example.";
                }
            }

            Matcher matcher = FIELD_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String key = normalizeSemanticSourceName(matcher.group(1));
            String value = matcher.group(2).trim();
            if (key.contains("example")) {
                prefix = "example.";
            }
            putIfNotBlank(sources, key, value);
            if (!prefix.isEmpty() && !key.startsWith("example.")) {
                putIfNotBlank(sources, prefix + key, value);
            }
        }
        alias(sources, "devanagari", "hindi");
        alias(sources, "hindi", "devanagari");
        alias(sources, "urduScript", "urdu");
        alias(sources, "urdu", "urduScript");
        alias(sources, "romanization", "romanized");
        alias(sources, "romanized", "romanization");
        alias(sources, "example.devanagari", "example.hindi");
        alias(sources, "example.hindi", "example.devanagari");
        alias(sources, "example.urduScript", "example.urdu");
        alias(sources, "example.urdu", "example.urduScript");
        return sources;
    }

    private ObjectNode buildCacheInput(String modelId, String target, JsonNode targetConfig, String variant,
                                       JsonNode variantConfig, String textSource, String text) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("modelId", modelId);
        root.put("target", target);
        root.put("variant", variant);
        root.put("language", textValue(variantConfig.path("language")));
        root.put("textSource", textSource);
        root.put("text", text.trim());
        root.put("format", "wav");
        root.put("displaySide", displaySide(targetConfig));
        root.put("caption", textValue(variantConfig.path("caption")));
        root.put("speaker", textValue(variantConfig.path("speaker")));
        root.set("generationConfig", variantConfig.path("generationConfig").isMissingNode()
                ? objectMapper.createObjectNode()
                : variantConfig.path("generationConfig"));
        if (!variantConfig.path("speed").isMissingNode()) {
            root.set("speed", variantConfig.path("speed"));
        }
        return root;
    }

    private byte[] synthesize(ResolvedTtsItem item, Consumer<String> status) throws IOException, InterruptedException {
        TtsSynthesisRequest request = new TtsSynthesisRequest();
        request.setModelId(item.modelId());
        request.setText(item.text());
        request.setCaption(textValue(item.cacheInput().path("caption")));
        request.setSpeaker(textValue(item.cacheInput().path("speaker")));
        request.setLanguage(item.language());
        request.setGenerationConfig(item.cacheInput().path("generationConfig"));

        String payload = objectMapper.writeValueAsString(request);
        log.info("Calling TTS sidecar uri={} modelId={} target={} variant={} language={} textSource={} speaker={} captionSet={} textLength={} timeout={}",
                synthesizeUri, item.modelId(), item.target(), item.variant(), item.language(), item.textSource(),
                request.getSpeaker(), !isBlank(request.getCaption()), item.text() == null ? 0 : item.text().length(),
                requestTimeout);

        HttpRequest httpRequest = HttpRequest.newBuilder(synthesizeUri)
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        long startedAt = System.currentTimeMillis();
        status(status, "Waiting for TTS sidecar");
        HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        long durationMs = System.currentTimeMillis() - startedAt;
        log.info("TTS sidecar response status={} durationMs={} bytes={}",
                response.statusCode(), durationMs, response.body() == null ? 0 : response.body().length);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = new String(response.body(), StandardCharsets.UTF_8);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "TTS service failed: HTTP " + response.statusCode() + " " + body);
        }
        return response.body();
    }

    private void status(Consumer<String> status, String message) {
        log.info("TTS status: {}", message);
        status.accept(message);
    }

    private String cacheKeyFor(ResolvedTtsItem item) throws IOException {
        return sha256(objectMapper.writeValueAsString(item.cacheInput()));
    }

    private TtsAudio findCachedAudio(Card card, ResolvedTtsItem item) {
        try {
            return ttsAudioDAO.findByCardAndCacheKey(card, cacheKeyFor(item))
                    .filter(audio -> Files.exists(Path.of(audio.getFilePath())))
                    .orElse(null);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to build TTS cache key");
        }
    }

    private TtsPlaybackItem toPlaybackItem(ResolvedTtsItem item, TtsAudio cachedAudio) {
        TtsPlaybackItem response = new TtsPlaybackItem();
        response.setTarget(item.target());
        response.setVariant(item.variant());
        response.setLabel(item.label());
        response.setLanguage(item.language());
        response.setTextSource(item.textSource());
        response.setText(item.text());
        response.setDisplaySide(item.displaySide());
        response.setModelId(item.modelId());
        response.setCached(cachedAudio != null);
        if (cachedAudio != null) {
            response.setAudioId(cachedAudio.getId());
            response.setAudioUrl("/api/tts/audio/" + cachedAudio.getId());
        }
        return response;
    }

    private TtsAudioResponse toResponse(TtsAudio audio, boolean cached) {
        TtsAudioResponse response = new TtsAudioResponse();
        response.setId(audio.getId());
        response.setCardId(audio.getCard().getId());
        response.setDeckId(audio.getDeck().getId());
        response.setCacheKey(audio.getCacheKey());
        response.setContentType(audio.getContentType());
        response.setGeneratedAt(audio.getGeneratedAt());
        response.setModelId(audio.getModelId());
        response.setTarget(audio.getTarget());
        response.setVariant(audio.getVariant());
        response.setLanguage(audio.getLanguage());
        response.setTextSource(audio.getTextSource());
        response.setResolvedText(audio.getResolvedText());
        response.setVoice(audio.getVoice());
        response.setSpeed(audio.getSpeed());
        response.setAudioUrl("/api/tts/audio/" + audio.getId());
        response.setCached(cached);
        return response;
    }

    private DeckTtsSettings toSettings(Deck deck) {
        DeckTtsSettings settings = new DeckTtsSettings();
        settings.setTtsEnabled(deck.isTtsEnabled());
        settings.setTtsModelId(deck.getTtsModelId());
        settings.setTtsConfigJson(isBlank(deck.getTtsConfigJson()) ? DEFAULT_TTS_CONFIG : deck.getTtsConfigJson());
        return settings;
    }

    private List<String> variantsForTarget(String targetName, JsonNode targetConfig, JsonNode config,
                                           JsonNode variants, Map<String, Set<String>> blockVoices) {
        if (blockVoices.containsKey(targetName)) {
            return blockVoices.get(targetName).stream().toList();
        }
        if (targetConfig.path("voices").isArray()) {
            List<String> names = new ArrayList<>();
            targetConfig.path("voices").forEach(node -> {
                if (node.isTextual()) names.add(node.asText());
            });
            return names;
        }
        String defaultVariant = textValue(config.path("defaultVariant"));
        if (!isBlank(defaultVariant) && variants.has(defaultVariant)) {
            return List.of(defaultVariant);
        }
        Iterator<String> names = variants.fieldNames();
        return names.hasNext() ? List.of(names.next()) : List.of();
    }

    private int countEnabledTargets(JsonNode targets) {
        int count = 0;
        Iterator<JsonNode> values = targets.elements();
        while (values.hasNext()) {
            if (values.next().path("enabled").asBoolean(true)) count++;
        }
        return count;
    }

    private String labelFor(String target, String variant, int targetCount) {
        String variantLabel = title(variant);
        return targetCount <= 1 ? variantLabel : title(target) + " " + variantLabel;
    }

    private String displaySide(JsonNode targetConfig) {
        String side = textValue(targetConfig.path("displaySide"));
        return "FRONT".equalsIgnoreCase(side) ? "FRONT" : "BACK";
    }

    private String sourceValue(Map<String, String> sources, String source) {
        String value = sources.get(source);
        if (!isBlank(value)) return value;
        return sources.get(normalizeSourceName(source));
    }

    private String resolvedTextSource(String target, JsonNode targetConfig, String variant,
                                      JsonNode variantConfig, Map<String, String> sources) {
        String targetTextSource = textValue(targetConfig.path("textSource"));
        if (!isBlank(targetTextSource) && !"auto".equalsIgnoreCase(targetTextSource)) {
            return targetTextSource;
        }

        String textSource = textValue(variantConfig.path("textSource"));
        if (isBlank(textSource) || "auto".equalsIgnoreCase(textSource)) {
            return automaticTextSource(target, variant);
        }

        if (!"word".equalsIgnoreCase(target) && !textSource.contains(".")) {
            String prefixed = normalizeSourceName(target) + "." + textSource;
            if (!isBlank(sourceValue(sources, prefixed))) {
                return prefixed;
            }
        }

        return textSource;
    }

    private String automaticTextSource(String target, String variant) {
        String normalizedVariant = variant.toLowerCase(Locale.ROOT);
        if ("example".equalsIgnoreCase(target)) {
            if (normalizedVariant.contains("urdu")) return "example.urduScript";
            if (normalizedVariant.contains("hindi")) return "example.devanagari";
            return "example.devanagari";
        }
        if (normalizedVariant.contains("urdu")) return "urduScript";
        if (normalizedVariant.contains("hindi")) return "devanagari";
        return "devanagari";
    }

    private String normalizeConfig(String raw, String label) {
        if (isBlank(raw)) {
            return DEFAULT_TTS_CONFIG;
        }
        try {
            JsonNode parsed = objectMapper.readTree(raw);
            if (!parsed.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must be a JSON object");
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must be valid JSON");
        }
    }

    private ObjectNode parseObjectConfig(String raw, String label) throws IOException {
        if (isBlank(raw)) {
            return null;
        }
        JsonNode parsed = objectMapper.readTree(raw);
        if (!parsed.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must be a JSON object");
        }
        return (ObjectNode) parsed;
    }

    private void merge(ObjectNode base, ObjectNode overrides) {
        overrides.fields().forEachRemaining(entry -> {
            JsonNode existing = base.get(entry.getKey());
            JsonNode incoming = entry.getValue();
            if (existing instanceof ObjectNode existingObject && incoming instanceof ObjectNode incomingObject) {
                merge(existingObject, incomingObject);
            } else {
                base.set(entry.getKey(), incoming);
            }
        });
    }

    private Card findOwnedCard(Long cardId, User user) {
        Card card = cardDAO.findById(cardId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
        verifyCardOwner(card, user);
        return card;
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

    private static void putIfNotBlank(Map<String, String> map, String key, String value) {
        if (!isBlank(value)) {
            map.putIfAbsent(key, value.trim());
        }
    }

    private static void alias(Map<String, String> sources, String existingKey, String aliasKey) {
        if (sources.containsKey(existingKey)) {
            sources.putIfAbsent(aliasKey, sources.get(existingKey));
        }
    }

    private static String stripTtsBlocks(String markdown) {
        return TTS_BLOCK_PATTERN.matcher(safe(markdown)).replaceAll("").trim();
    }

    private static String normalizeSourceName(String value) {
        String trimmed = value.trim();
        String[] parts = trimmed.split("[^\\p{L}\\p{N}]+");
        StringBuilder cleaned = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (cleaned.isEmpty()) {
                cleaned.append(part.substring(0, 1).toLowerCase(Locale.ROOT)).append(part.substring(1));
            } else {
                cleaned.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
            }
        }
        return cleaned.isEmpty() ? trimmed : cleaned.toString();
    }

    private static String normalizeSemanticSourceName(String value) {
        String normalized = normalizeSourceName(value);
        if (normalized.length() > "example".length() && normalized.startsWith("example")) {
            String nested = normalized.substring("example".length());
            return "example." + nested.substring(0, 1).toLowerCase(Locale.ROOT) + nested.substring(1);
        }
        return normalized;
    }

    private static String stripQuotes(String value) {
        return value.replaceAll("^['\"]|['\"]$", "").trim();
    }

    private static String title(String value) {
        if (isBlank(value)) return "";
        String spaced = value.replace('-', ' ').replace('_', ' ');
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    private static String textValue(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static Double doubleValue(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asDouble();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
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

    private record ResolvedTtsItem(
            String target,
            String variant,
            String label,
            String language,
            String textSource,
            String text,
            String displaySide,
            String modelId,
            String voice,
            Double speed,
            ObjectNode cacheInput
    ) {
    }
}
