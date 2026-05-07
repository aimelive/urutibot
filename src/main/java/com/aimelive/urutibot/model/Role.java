package com.aimelive.urutibot.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, unique = true, length = 32)
    @Enumerated(EnumType.STRING)
    private RoleName name;

    @Column(length = 255)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @org.hibernate.annotations.BatchSize(size = 50)
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    public enum RoleName {
        USER,
        ADMIN
    }
}
