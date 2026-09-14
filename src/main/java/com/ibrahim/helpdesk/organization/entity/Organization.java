package com.ibrahim.helpdesk.organization.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name= "organizations")
@Getter
@Setter
@NoArgsConstructor
public class Organization {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank
    @Column(nullable = false, length = 150)
    private String name;

    @NotBlank
    @Email
    @Column(nullable = false, length = 200)
    private String companyEmail;

    @NotBlank
    @Column(nullable = false, length = 150)
    private String domain;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String industry;


}
