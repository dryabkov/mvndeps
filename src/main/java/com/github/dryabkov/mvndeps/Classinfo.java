package com.github.dryabkov.mvndeps;

public class Classinfo {

    public final String name;

    public final boolean isInterfce;

    public final boolean isEnum;

    public final boolean isUtility;

    public final String moduleName;

    public Classinfo(String moduleName, String name, boolean isInterfce, boolean isEnum, boolean isUtility) {
        this.name = name;
        this.isInterfce = isInterfce;
        this.isEnum = isEnum;
        this.isUtility = isUtility;
        this.moduleName = moduleName;
    }
}
