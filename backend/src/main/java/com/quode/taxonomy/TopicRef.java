package com.quode.taxonomy;

import java.util.UUID;

/** A topic as other modules see it. {@code path}: from the top level, e.g. "Languages › Java". */
public record TopicRef(UUID id, String name, String path) {}
