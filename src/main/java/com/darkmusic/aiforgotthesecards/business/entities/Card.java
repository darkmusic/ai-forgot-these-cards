package com.darkmusic.aiforgotthesecards.business.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "card")
public class Card {
    @Id
    @GeneratedValue
    @Column(name="id", nullable = false)
    private Long id;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name="front")
    private String front;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name="back")
    private String back;

    @JoinColumn(name = "deck_id", nullable = false)
    @ManyToOne
    @JsonBackReference
    private Deck deck;

    @ManyToMany(cascade = CascadeType.MERGE)
    @JoinTable(
            name="card_tag",
            joinColumns = @JoinColumn(name="card_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name="tag_id", referencedColumnName = "id")
    )
    private Set<Tag> tags;
}
