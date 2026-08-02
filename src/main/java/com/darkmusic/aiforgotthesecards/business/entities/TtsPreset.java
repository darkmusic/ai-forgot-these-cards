package com.darkmusic.aiforgotthesecards.business.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Setter
@Getter
@Entity
@Table(name = "tts_preset")
public class TtsPreset {
    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "speaker")
    private String speaker;

    @Column(name = "language")
    private String language;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "caption")
    private String caption;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "advanced_config_json")
    private String advancedConfigJson;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @JoinColumn(name = "deck_id", nullable = false)
    @ManyToOne
    @JsonIgnore
    private Deck deck;
}
