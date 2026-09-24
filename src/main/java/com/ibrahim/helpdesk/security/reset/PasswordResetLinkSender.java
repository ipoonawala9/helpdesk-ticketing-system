package com.ibrahim.helpdesk.security.reset;

import com.ibrahim.helpdesk.user.entity.User;

/**
 * Delivers a password reset link to its owner. Implementations must not
 * disclose whether an address exists to anyone but that address.
 */
public interface PasswordResetLinkSender {

    void send(User user, String link);
}
