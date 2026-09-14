package com.ibrahim.helpdesk.user.entity;

import com.ibrahim.helpdesk.organization.entity.Organization;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(
        name = "users",
        // Serves organization-scoped user lists, usually narrowed by role.
        indexes = @Index(name = "idx_users_organization_role", columnList = "organization_id, role")
)
@Getter @Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String name;

    /** Stored lower-case; unique across all organizations because it is the login name. */
    @Column(nullable = false, unique = true, length = 200)
    private String email;

    /** Always a {@code {bcrypt}...} hash, never plain text. */
    @JsonIgnore
    @Column(nullable = false)
    private String password;
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    /** Null only for SUPER_ADMIN, who belongs to no organization. */
    @ManyToOne
    @JoinColumn(name="organization_id")
    private Organization organization;

    @Column(nullable = false)
    private Boolean active;

}
