package com.quode.taxonomy.internal;

import java.util.UUID;

/**
 * A topic as the API returns it. {@code questionCount}: distinct non-archived questions in the
 * topic and all its sub-topics.
 */
record TopicView(UUID id, UUID parentId, String name, long questionCount) {}
