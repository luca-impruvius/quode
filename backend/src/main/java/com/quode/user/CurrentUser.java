package com.quode.user;

import java.util.UUID;

/**
 * The signed-in user, for other modules: every owned query is scoped by {@link #id()}. Throws a 401
 * when the request has no signed-in user with an app_user row.
 */
public interface CurrentUser {

  UUID id();
}
