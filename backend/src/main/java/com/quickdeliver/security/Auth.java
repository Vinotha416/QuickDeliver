package com.quickdeliver.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires a valid JWT. Replaces the Express middleware pair {@code protect} / {@code authorize(...)}.
 * <ul>
 *   <li>{@code @Auth} - any logged-in user ({@code protect})</li>
 *   <li>{@code @Auth(roles = "admin")} - only these roles ({@code protect, authorize('admin')})</li>
 * </ul>
 * A method-level annotation overrides one on the class. Endpoints without it are public.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Auth {
    String[] roles() default {};
}
