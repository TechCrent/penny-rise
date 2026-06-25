package com.stash.payments.shared.security;

import java.util.UUID;

/**
 * Carries the authenticated caller's identity through the request.
 * Stored as a request attribute by {@link CallerContextFilter}.
 *
 * <p>Two caller types:
 * <ul>
 *   <li>{@link #internal()} — authenticated via {@code X-Internal-Service-Token};
 *       may query any account without ownership checks.</li>
 *   <li>{@link #user(UUID)} — authenticated via JWT; subject to ownership
 *       enforcement (user may only read their own accounts).</li>
 * </ul>
 */
public record CallerContext(
        CallerType callerType,
        UUID       userId         // null for internal callers
) {
    public enum CallerType { USER, INTERNAL }

    public static CallerContext internal() {
        return new CallerContext(CallerType.INTERNAL, null);
    }

    public static CallerContext user(UUID userId) {
        return new CallerContext(CallerType.USER, userId);
    }

    public boolean isInternal()        { return callerType == CallerType.INTERNAL; }
    public boolean isUser()            { return callerType == CallerType.USER; }
}
