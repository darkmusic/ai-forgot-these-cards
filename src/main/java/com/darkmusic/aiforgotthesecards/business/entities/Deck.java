package com.darkmusic.aiforgotthesecards.business.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Set;

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
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;

    @OneToMany(mappedBy = "deck")
    @JsonManagedReference
    private Set<Card> cards;

    @ManyToMany(cascade = {CascadeType.MERGE})
    @JoinTable(
            name="deck_tag",
            joinColumns = @JoinColumn(name="deck_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name="tag_id", referencedColumnName = "id")
    )
    private Set<Tag> tags;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name="template_front")
    private String templateFront;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name="template_back")
    private String templateBack;
}
