package com.quode.taxonomy;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Finds topics by path for other modules, creating the missing ones (bulk import, M2). */
public interface TopicPaths {

  /** The separator between topic names in a path, as shown in the app: "Cloud › GCP". */
  String SEPARATOR = "›";

  /**
   * Resolves each path ("Cloud › GCP › IAM") to the owner's topic, creating missing topics along
   * the way in the caller's transaction. Names match siblings ignoring case and surrounding spaces,
   * like the topic rules. A path with an empty or too-long name is reported, not created.
   *
   * @param paths the paths as the caller received them; they are also the keys of the result
   */
  Resolved resolve(UUID ownerId, Collection<String> paths);

  /**
   * {@code ids}: topic id per valid path. {@code problems}: reason per invalid path. {@code
   * created}: how many topics were created.
   */
  record Resolved(Map<String, UUID> ids, Map<String, String> problems, int created) {}
}
