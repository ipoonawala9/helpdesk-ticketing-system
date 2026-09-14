package com.ibrahim.helpdesk.organization.repository;
import com.ibrahim.helpdesk.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrganizationRepository
    extends JpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {

    }
