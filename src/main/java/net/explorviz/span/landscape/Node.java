package net.explorviz.span.landscape;

import java.util.List;

public record Node(
    String ipAddress,
    String hostName,
    List<Application> applications
) {

}
