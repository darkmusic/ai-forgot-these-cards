package com.darkmusic.aiforgotthesecards.business.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Setter
@Getter
@Entity
@Table(name = "ai_chat")
public class AiChat {
    @Id
    @GeneratedValue
    @Column(name="id", nullable = false)
    private Long id;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name="question", nullable = false)
    private String question;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name="answer")
    private String answer;

    @JoinColumn(name = "user_id", nullable = false)
    @ManyToOne
    private User user;

    @Column(name="created_at", nullable = false)
    private Long createdAt;
}
