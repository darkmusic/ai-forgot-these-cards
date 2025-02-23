package com.darkmusic.aiforgotthesecards.business.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

import static jakarta.persistence.CascadeType.ALL;

@Setter
@Getter
@Entity
@Table(name = "ai_model")
public class AiModel {
    @Id
    @GeneratedValue
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name="name")
    private String name;

    @Column(name="model")
    private String model;

    @OneToMany(cascade = ALL, mappedBy = "aiModel")
    private Set<AiChat> chats;

    public AiModel(int hashCode, String name, String model) {
        this.id = (long) hashCode;
        this.name = name;
        this.model = model;
    }

    public AiModel() {
    }

    public String toString() {
        return "AiModel(id=" + this.getId() + ", name=" + this.getName() + ", description=" + this.getModel() + ")";
    }
}
