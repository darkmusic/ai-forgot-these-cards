package com.darkmusic.aiforgotthesecards.business.entities;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Set;

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

    @Column(name="username", nullable = false)
    private String username;

    @Column(name="password_hash", nullable = false)
    private String password_hash;

    @Column(name="name",  nullable = false)
    private String name;

    @OneToMany(mappedBy = "user")
    @JsonManagedReference
    private Set<Deck> decks;

    @Column(name="is_admin", nullable = false, columnDefinition = "boolean default false")
    private boolean isAdmin;

    @Column(name="is_active", nullable = false, columnDefinition = "boolean default true")
    private boolean isActive;

    @Column(name="profile_pic_url", nullable = false,
            columnDefinition = "varchar(255) default '/vite.svg'") // Default profile picture URL
    private String profile_pic_url;

    @JoinColumn(name="theme_id")
    private Long themeId;
}
