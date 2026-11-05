package com.clinic.exception;

/** The account is locked after too many consecutive failed logins. */
public class AccountLockedException extends AuthenticationException {

    public AccountLockedException(String message) {
        super(message);
    }
}