package net.explorviz.span.landscape;

import java.util.List;

public record K8sPod(
    String name,
    List<Application> applications
) {
}
