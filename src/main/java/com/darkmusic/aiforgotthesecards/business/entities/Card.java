package com.darkmusic.aiforgotthesecards.business.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @Column(name="front", columnDefinition = "text")
    private String front;

    @Column(name="back", columnDefinition = "text")
    private String back;

    @JoinColumn(name = "deck_id", nullable = false)
    @ManyToOne
    @JsonBackReference
    private Deck deck;

    @ManyToMany
    @JoinTable(
            name="card_tag",
            joinColumns = @JoinColumn(name="card_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name="tag_id", referencedColumnName = "id")
    )
    @JsonIgnore
    private Set<Tag> tags;
}
