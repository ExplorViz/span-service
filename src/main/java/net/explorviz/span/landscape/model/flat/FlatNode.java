package net.explorviz.span.landscape.model.flat;

import java.util.Set;

public record FlatNode(
    String id,
    String ipAddress,
    String hostName,
    Set<String> applicationIds
) {

}
