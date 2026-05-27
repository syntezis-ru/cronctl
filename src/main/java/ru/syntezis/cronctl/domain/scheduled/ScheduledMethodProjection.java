package ru.syntezis.cronctl.domain.scheduled;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledMethodProjection {

    private ScheduledMethodDetails details;

}
