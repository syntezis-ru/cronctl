package ru.syntezis.cronctl.core.execution;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Provides the identifier attached to executions created by this application process. */
@Getter
@RequiredArgsConstructor
public class NodeIdProvider {

    private final String nodeId;

}
