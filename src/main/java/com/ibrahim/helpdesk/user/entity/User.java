package com.ibrahim.helpdesk.user.entity;

import com.ibrahim.helpdesk.organization.entity.Organization;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;

    @JsonIgnore
    private String password;
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    @ManyToOne
    @JoinColumn(name="organization_id")
    private Organization organization;
    private Boolean active;

}
