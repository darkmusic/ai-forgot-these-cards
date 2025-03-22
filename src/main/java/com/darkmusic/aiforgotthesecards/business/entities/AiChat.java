package com.darkmusic.aiforgotthesecards.business.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "ai_chat")
public class AiChat {
    @Id
    @GeneratedValue
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name="question", columnDefinition = "text", nullable = false)
    private String question;

    @Column(name="answer", columnDefinition = "text")
    private String answer;

    @JoinColumn(name = "aimodel_id", nullable = false)
    @ManyToOne
    private AiModel aiModel;

    @JoinColumn(name = "user_id", nullable = false)
    @ManyToOne
    private User user;

    @Column(name="created_at", nullable = false)
    private Long createdAt;
}
