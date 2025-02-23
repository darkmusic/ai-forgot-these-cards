package com.darkmusic.aiforgotthesecards.business.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "tag", uniqueConstraints = {
        @UniqueConstraint(columnNames = "name")
})
public class Tag {
    @Id
    @GeneratedValue
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name="name")
    private String name;

    @ManyToMany(mappedBy = "tags")
    private Set<Deck> decks;

    @ManyToMany(mappedBy = "tags")
    private Set<Card> cards;
}
