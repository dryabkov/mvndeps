package com.github.dryabkov.mvndeps.entities;

import java.util.Objects;

public class ExceptionEnt {

    private final String fullName;

    public ExceptionEnt(String fullName) {
        this.fullName = fullName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExceptionEnt that = (ExceptionEnt) o;
        return Objects.equals(fullName, that.fullName);
    }

    @Override
    public int hashCode() {

        return Objects.hash(fullName);
    }

    public String getFullName() {
        return fullName;
    }
}
