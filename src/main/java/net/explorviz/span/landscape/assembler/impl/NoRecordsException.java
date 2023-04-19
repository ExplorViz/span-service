package net.explorviz.span.landscape.assembler.impl;

import net.explorviz.span.landscape.assembler.LandscapeAssemblyException;

import java.io.Serial;

/**
 * Thrown if trying to generate a landscape out of 0 records.
 */
public class NoRecordsException extends LandscapeAssemblyException {
    @Serial
    private static final long serialVersionUID = 1L;

    public NoRecordsException() {
        this("At least one record must be given");
    }

    private NoRecordsException(final String message) {
        super(message);
    }
}
