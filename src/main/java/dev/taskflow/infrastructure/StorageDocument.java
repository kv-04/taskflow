package dev.taskflow.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.taskflow.domain.Priority;
import dev.taskflow.domain.Status;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * On-disk representation. Kept separate from the domain model so that the file format (versioned
 * via {@code schemaVersion}) can evolve independently of domain code, and so the domain stays free
 * of serialisation annotations.
 */
record StorageDocument(int schemaVersion, long nextId, List<StoredTask> tasks) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record StoredTask(
      long id,
      String title,
      Status status,
      Priority priority,
      LocalDate dueDate,
      List<String> tags,
      Instant createdAt,
      Instant completedAt) {}
}
