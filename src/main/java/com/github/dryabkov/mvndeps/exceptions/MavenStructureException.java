package com.github.dryabkov.mvndeps.exceptions;

public class MavenStructureException extends RuntimeException {

    public MavenStructureException(Throwable e) {
        super(e);
    }

    public MavenStructureException(String message) {
        super(message);
    }
}
