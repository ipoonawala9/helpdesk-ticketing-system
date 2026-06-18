package com.ibrahim.helpdesk.user.service;

import com.ibrahim.helpdesk.exception.OrganizationNotFoundException;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.organization.repository.OrganizationRepository;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    public User createUser(User user) {
        if(user.getOrganization() != null) {
            Long organizationId = user.getOrganization().getId();

            Organization organization = organizationRepository.findById(organizationId)
                    .orElseThrow(()-> new OrganizationNotFoundException(organizationId));
            user.setOrganization(organization);
        }
        return userRepository.save(user);
    }
}
