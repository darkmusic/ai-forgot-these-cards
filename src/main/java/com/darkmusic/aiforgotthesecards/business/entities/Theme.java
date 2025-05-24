package com.darkmusic.aiforgotthesecards.business.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "theme")
public class Theme {
    @Id
    @GeneratedValue
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name="name", nullable = false)
    private String name;

    @Column(name="description")
    private String description;

    @Column(name="cssUrl")
    private String cssUrl;

    @Column(name="active")
    private boolean active;
}
