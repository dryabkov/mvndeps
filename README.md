# MvnDeps
Build direct classes usage graph in multi-module maven project with Apache BCEL.

# Usage

## Add repo to your maven settings.xml

    <pluginRepositories>
        <pluginRepository>
            <id>central</id>
            <url>https://repo1.maven.org/maven2</url>
            <releases>
                <enabled>true</enabled>
            </releases>
        </pluginRepository>
        <pluginRepository>
            <id>github_dryabkov_mvndeps</id>
            <url>https://maven.pkg.github.com/dryabkov/mvndeps</url>
        </pluginRepository>
    </pluginRepositories>

## Collect data

mvn \
    clean compile com.github.dryabkov.mvndeps.reports:deps-maven-plugin:0.0.1:deps -Daggreagate=true \
    -DpackagePrefixes=<YOUR_ROOT_PACKAGE> \
    -DoutputClassesFile=${deps_report_root}/classes.txt \
    -DoutputClassesInfoFile=${deps_report_root}/classes-inf.txt \
    -DoutputExceptionsFile=${deps_report_root}/exc.txt \
    -DoutputPackagesDiagramFile=${deps_report_root}/mp.dot

## Generate image

dot -Tpng ${deps_report_root}/mp.dot > ${deps_report_root}/packages.png
