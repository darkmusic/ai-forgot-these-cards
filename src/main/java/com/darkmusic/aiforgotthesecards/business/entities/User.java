package com.darkmusic.aiforgotthesecards.business.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Set;
import static jakarta.persistence.CascadeType.ALL;

@Setter
@Getter
@Entity
@Table(name = "`user`", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username")
})
public class User {
    @Id
    @GeneratedValue
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name="username")
    private String username;

    @Column(name="password_hash")
    private String password_hash;

    @Column(name="name")
    private String name;

    @OneToMany(cascade = ALL, mappedBy = "user")
    private Set<Deck> decks;

    @Column(name="is_admin", nullable = false, columnDefinition = "boolean default false")
    private boolean isAdmin;

    @Column(name="is_active", nullable = false, columnDefinition = "boolean default true")
    private boolean isActive;
}
