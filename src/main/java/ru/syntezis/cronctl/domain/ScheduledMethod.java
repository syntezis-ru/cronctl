package ru.syntezis.cronctl.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Combines the metadata ({@link ScheduledMethodDetails}) and the execution reference
 * ({@link ScheduledMethodReference}) of a single {@code @Scheduled} method.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledMethod {

    private ScheduledMethodDetails details;
    private ScheduledMethodReference reference;

    public UUID getId() {
        return details.getId();
    }
}
