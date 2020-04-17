package com.github.dryabkov.mvndeps;

import java.util.Objects;

public class ClassBlock {

    private final String name;

    private final ClassBlockType type;

    public ClassBlock(String name, ClassBlockType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public ClassBlockType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassBlock that = (ClassBlock) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
