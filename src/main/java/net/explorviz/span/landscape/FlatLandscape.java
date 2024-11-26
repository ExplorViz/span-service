package net.explorviz.span.landscape;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.UUID;

public record FlatLandscape(
    UUID landscapeToken,
    List<Application> applications,
    List<Package> packages,
    List<Class> classes,
    List<Method> methods
) {

}
