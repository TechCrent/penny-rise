package com.stash.platform.notification.domain;

import java.util.UUID;

/** Minimal view of a device token needed by the dispatch service. */
public interface DeviceTokenLike {
    UUID id();
    String expoPushToken();
}
