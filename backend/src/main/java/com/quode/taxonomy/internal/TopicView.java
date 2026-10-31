package com.quode.taxonomy.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A topic as the API returns it. {@code questionCount}: distinct non-archived questions in the
 * topic and all its sub-topics.
 */
record TopicView(
    UUID id,
    @Schema(types = {"string", "null"}) UUID parentId,
    String name,
    long questionCount,
    boolean drift) {}
