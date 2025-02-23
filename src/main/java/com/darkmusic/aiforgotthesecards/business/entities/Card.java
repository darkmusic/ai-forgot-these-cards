package com.darkmusic.aiforgotthesecards.business.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
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

    @Column(name="front")
    private String front;

    @Column(name="back")
    private String back;

    @JoinColumn(name = "deck_id")
    @ManyToOne
    private Deck deck;

    @ManyToMany
    @JoinTable(
            name="card_tag",
            joinColumns = @JoinColumn(name="card_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name="tag_id", referencedColumnName = "id")
    )
    private Set<Tag> tags;
}
