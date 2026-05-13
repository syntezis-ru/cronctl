package ru.syntezis.cronctl.util.condition;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Limits {

    UUID_LENGTH(36L),
    FIXED_RATE_MIN(1L),
    FIXED_RATE_MAX(99999L),
    RANDOM_STRING_LENGTH(8L),
    ;

    private final long value;

}
