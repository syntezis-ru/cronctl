package ru.syntezis.cronctl.domain;

import lombok.*;

import java.util.UUID;

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
