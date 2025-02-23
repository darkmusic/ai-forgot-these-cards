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

    @Column(name="question")
    private String question;

    @Column(name="answer")
    private String answer;

    @JoinColumn(name = "aimodel_id")
    @ManyToOne
    private AiModel aiModel;

    @JoinColumn(name = "user_id")
    @ManyToOne
    private User user;
}
