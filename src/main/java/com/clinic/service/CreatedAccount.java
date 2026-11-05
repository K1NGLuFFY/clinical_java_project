package com.clinic.service;

import com.clinic.model.User;

/**
 * Result of creating an account: the generated initial password must be shown once to the
 * receptionist because only its hash is stored.
 */
public record CreatedAccount(User user, String initialPassword) {
}
