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
    private String name;

    @NotBlank
    @Email
    private String companyEmail;

    @NotBlank
    private String domain;

    @NotBlank
    private String industry;


}
