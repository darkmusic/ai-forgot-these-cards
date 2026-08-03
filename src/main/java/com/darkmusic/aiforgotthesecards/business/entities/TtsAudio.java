package com.darkmusic.aiforgotthesecards.business.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Setter
@Getter
@Entity
@Table(
        name = "tts_audio",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"card_id", "cache_key"})
        }
)
public class TtsAudio {
    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private Long id;

    @JoinColumn(name = "deck_id", nullable = false)
    @ManyToOne
    private Deck deck;

    @JoinColumn(name = "card_id", nullable = false)
    @ManyToOne
    private Card card;

    @Column(name = "cache_key", nullable = false)
    private String cacheKey;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "content_type", nullable = false)
    private String contentType = "audio/wav";

    @Column(name = "generated_at", nullable = false)
    private long generatedAt;

    @Column(name = "model_id", nullable = false)
    private String modelId;

    @Column(name = "target")
    private String target;

    @Column(name = "variant")
    private String variant;

    @Column(name = "language")
    private String language;

    @Column(name = "text_source")
    private String textSource;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "resolved_text")
    private String resolvedText;

    @Column(name = "voice")
    private String voice;

    @Column(name = "speed")
    private Double speed;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "config_json")
    private String configJson;
}
