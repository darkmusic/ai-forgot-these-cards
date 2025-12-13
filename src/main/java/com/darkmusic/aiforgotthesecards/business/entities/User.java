package com.darkmusic.aiforgotthesecards.business.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name="password_hash", nullable = false)
    private String password_hash;

    @Column(name="name",  nullable = false)
    private String name;

    @OneToMany(mappedBy = "user")
    @JsonManagedReference
    private Set<Deck> decks;

    @Column(name="is_admin", nullable = false)
    private boolean isAdmin;

    @Column(name="is_active", nullable = false)
    private boolean isActive = true;

    @Column(name="profile_pic_url", nullable = false)
    private String profile_pic_url = "/vite.svg";

    @JoinColumn(name="theme_id")
    private Long themeId;
}
