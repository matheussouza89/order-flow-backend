package com.matheus.orderFlow.contracts.user;

import java.util.Map;

public final class UserFacts {
    public static final String REGISTERED_KEY = "user.registered";
    public static final String EMAIL_CHANGED_KEY = "user.email-changed";

    public static final String REGISTERED_V1 = "user.registered.v1";
    public static final String EMAIL_CHANGED_V1 = "user.email-changed.v1";

    public static final Map<String, Class<?>> TYPES = Map.of(
            REGISTERED_V1, UserRegisteredEvent.class,
            EMAIL_CHANGED_V1, UserEmailChangedEvent.class
    );

    private UserFacts() {
    }
}
