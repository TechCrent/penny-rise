package com.stash.platform.user.event;

import org.springframework.context.ApplicationEvent;

/**
 * Published within the signup transaction; {@link SignupVerificationEmailPublisher}
 * sends the actual email AFTER the transaction commits — see that class for why.
 */
public class SignupVerificationEmailRequestedEvent extends ApplicationEvent {

    private final String email;
    private final String displayName;
    private final String verificationUrl;

    public SignupVerificationEmailRequestedEvent(Object source,
                                                  String email, String displayName, String verificationUrl) {
        super(source);
        this.email           = email;
        this.displayName     = displayName;
        this.verificationUrl = verificationUrl;
    }

    public String getEmail()           { return email; }
    public String getDisplayName()     { return displayName; }
    public String getVerificationUrl() { return verificationUrl; }
}
