# mvndeps
Build direct classes usage graph in multi-module maven project with Apache BCEL.

# usage

mvn \
    clean compile com.github.dryabkov.mvndeps.reports:deps-maven-plugin:0.0.1:deps -Daggreagate=true \
    -DpackagePrefixes=<YOUR_ROOT_PACKAGE> \
    -DoutputClassesFile=${deps_report_root}/classes.txt \
    -DoutputClassesInfoFile=${deps_report_root}/classes-inf.txt \
    -DoutputExceptionsFile=${deps_report_root}/exc.txt \
    -DoutputPackagesDiagramFile=${deps_report_root}/mp.dot

dot -Tpng ${deps_report_root}/mp.dot > ${deps_report_root}/packages.png
