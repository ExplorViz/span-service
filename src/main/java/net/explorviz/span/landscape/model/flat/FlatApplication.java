package net.explorviz.span.landscape.model.flat;

import java.util.Set;

public record FlatApplication(
    String id,
    String name,
    String language,
    int instanceId, // TODO: Deviation from frontend, expects `String instanceId`
    Set<String> packagIds
) {

}
