package com.darkmusic.aiforgotthesecards.business.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Set;
import static jakarta.persistence.CascadeType.ALL;

@Setter
@Getter
@Entity
@Table(name = "deck")
public class Deck {
    @Id
    @GeneratedValue
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name="name")
    private String name;

    @Column(name="description")
    private String description;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(cascade = ALL, mappedBy = "deck")
    private Set<Card> cards;

    @ManyToMany
    @JoinTable(
            name="deck_tag",
            joinColumns = @JoinColumn(name="deck_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name="tag_id", referencedColumnName = "id")
    )
    private Set<Tag> tags;
}
