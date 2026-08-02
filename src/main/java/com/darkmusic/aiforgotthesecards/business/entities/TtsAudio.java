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

    @JoinColumn(name = "preset_id")
    @ManyToOne
    private TtsPreset preset;

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

    @Column(name = "preset_name")
    private String presetName;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "preset_config_json")
    private String presetConfigJson;
}
