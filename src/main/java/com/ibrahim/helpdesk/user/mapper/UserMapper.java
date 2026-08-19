package com.ibrahim.helpdesk.user.mapper;

import com.ibrahim.helpdesk.organization.mapper.OrganizationMapper;
import com.ibrahim.helpdesk.user.dto.UserResponse;
import com.ibrahim.helpdesk.user.dto.UserSummaryResponse;
import com.ibrahim.helpdesk.user.entity.User;

/**
 * Explicit entity -> DTO mapping for users. Password is never copied into any
 * response type.
 */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getActive(),
                OrganizationMapper.toSummary(user.getOrganization())
        );
    }

    public static UserSummaryResponse toSummary(User user) {
        if (user == null) {
            return null;
        }
        return new UserSummaryResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
