package ru.syntezis.cronctl.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Represents a registered {@code @Scheduled} task exposed via the cronctl API.
 *
 * <p>A task is a thin wrapper around {@link ScheduledMethod} that provides
 * a stable identity ({@link #getId()}) for use in HTTP requests.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    private ScheduledMethod method;

    public UUID getId() {
        return method.getId();
    }
}
