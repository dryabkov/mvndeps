package com.github.dryabkov.mvndeps;

public class Classinfo {

    public final String name;

    public final boolean isInterface;

    public final boolean isEnum;

    public final boolean isUtility;

    public final String moduleName;

    public Classinfo(String moduleName, String name, boolean isInterface, boolean isEnum, boolean isUtility) {
        this.name = name;
        this.isInterface = isInterface;
        this.isEnum = isEnum;
        this.isUtility = isUtility;
        this.moduleName = moduleName;
    }
}
