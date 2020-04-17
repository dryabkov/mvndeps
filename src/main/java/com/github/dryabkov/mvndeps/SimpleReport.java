package com.github.dryabkov.mvndeps;

import com.github.dryabkov.mvndeps.analyzer.Check;
import com.github.dryabkov.mvndeps.entities.ExceptionEnt;
import com.github.dryabkov.mvndeps.exceptions.ClassReadingException;
import com.github.dryabkov.mvndeps.exceptions.MavenStructureException;
import com.github.dryabkov.mvndeps.exceptions.ResultWritingException;
import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.Type;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Mojo(
        name = "deps",
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        aggregator = true,
        requiresDirectInvocation = true
)
public class SimpleReport extends AbstractMojo {

    private Log logger;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(property = "packagePrefixes", required = true)
    private List<String> packagePrefixes;

    @Parameter(property = "outputExceptionsFile", required = true)
    private File outputExceptionsFile;

    @Parameter(property = "outputClassesFile", required = true)
    private File outputClassesFile;

    @Parameter(property = "outputClassesInfoFile", required = true)
    private File outputClassesInfoFile;

    @Parameter(property = "outputPackagesDiagramFile", required = true)
    private File outputPackagesDiagramFile;


    private Map<String, AtomicInteger> containedPackages = new HashMap<>();
    private Map<Integer, Link<String, String>> packages = new HashMap<>();
    private Map<Integer, Link<String, String>> classes = new HashMap<>();
    private Map<Integer, Link<String, ExceptionEnt>> packageUsesException = new HashMap<>();
    private Map<String, Classinfo> classesInfo = new HashMap<>();

    /**
     * The Maven Session.
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    private void mkDirIfNotExists(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new ResultWritingException("Fail to create dir " + dir.getAbsolutePath());
        }
    }

    /**
     * @return true если не было и был создан
     */
    private boolean createNewFileIfNotExists(File file) {
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    throw new ResultWritingException("Fail to create file " + file.getAbsolutePath());
                }
            } catch (IOException e) {
                throw new ResultWritingException(e);
            }
            return true;
        }
        return false;
    }

    private Writer createFileWriter(File file, String header) throws IOException {
        mkDirIfNotExists(file.getParentFile());
        boolean writeHeader = createNewFileIfNotExists(file);
        Writer fileWriter = new BufferedWriter(new FileWriter(file, true));
        if (writeHeader && header != null) {
            fileWriter.write(header + "\n");
        }
        return fileWriter;
    }

    @Override
    public void execute() {

        logger = getLog();
        checkConfiguration();
        writeClassesInfo();
        writeClassRelations();
        writeExceptions();
        writePackageDiagram();
    }

    private void writePackageDiagram() {
        try (Writer out = createFileWriter(outputPackagesDiagramFile, null)) {
            new Check(classesInfo, classes.values(), logger)
                    .main(out);
        } catch (IOException e) {
            throw new ResultWritingException(e);
        }
    }

    private void writeExceptions() {
        try (Writer out = createFileWriter(outputExceptionsFile, "# package;class;count")) {
            for (Link<String, ExceptionEnt> link : packageUsesException.values()) {
                out.write(String.format("%s;%s;%d\n", link.getFrom(), link.getTo().getFullName(), link.getCount()));
            }
        } catch (IOException e) {
            throw new ResultWritingException(e);
        }
    }

    private void writeClassRelations() {
        try (Writer out = createFileWriter(outputClassesFile, "# class;class;count;reltype")) {

            for (Link<String, String> link : classes.values()) {
                String from = link.getFrom();
                String to = link.getTo();

                for (String packagePrefix : packagePrefixes) {
                    from = from.replace(packagePrefix, "");
                    to = to.replace(packagePrefix, "");
                }
                out.write(String.format("%s;%s;%d;%s\n", from, to, link.getCount(),
                        link.relationType() == null ? "" : link.relationType()));
            }
        } catch (IOException e) {
            throw new ResultWritingException(e);
        }
    }

    private void writeClassesInfo() {

        try {
            for (MavenProject module : project.getCollectedProjects()) {
                processProject(module);
            }
        } catch (DependencyResolutionRequiredException e) {
            throw new MavenStructureException(e);
        }

        try (Writer out = createFileWriter(outputClassesInfoFile,
                "# class;isInterface;module;isEnum;isUtility")) {

            for (Classinfo info : classesInfo.values()) {
                String to = info.name;
                for (String packagePrefix : packagePrefixes) {
                    to = to.replace(packagePrefix, "");
                }
                out.write(String.format("%s;%s;%s;%s;%s\n", to, info.isInterface, info.moduleName, info.isEnum, info.isUtility));
            }
        } catch (IOException e) {
            throw new ResultWritingException(e);
        }
    }

    private void checkConfiguration() {
        if (packagePrefixes == null || packagePrefixes.isEmpty()) {
            throw new IllegalArgumentException("Empty config package prefixes");
        } else {
            for (String packagePrefix : packagePrefixes) {
                if (packagePrefix == null || packagePrefix.trim().isEmpty()) {
                    throw new IllegalArgumentException("Empty config package prefix");
                }
            }
        }
    }

    private void processProject(MavenProject module) throws DependencyResolutionRequiredException {
        module.getCompileClasspathElements().forEach(cpe -> {
            if (!cpe.endsWith(".jar") && Paths.get(cpe).toFile().exists() &&
                    cpe.equals(module.getBasedir().getAbsolutePath() + File.separator + "target" + File.separator + "classes")) {
                try (Stream<Path> pathStream = Files.walk(Paths.get(cpe))) {
                    pathStream
                            .filter(path -> path.getFileName().toString().endsWith(".class"))
                            .forEach(clazz -> {
                                try {
                                    JavaClass javaClass = new ClassParser(clazz.toAbsolutePath().toString()).parse();
                                    proccessClass(module.getName(), javaClass);
                                } catch (ClassFormatException | IOException e) {
                                    throw new ClassReadingException(e);
                                }
                            });
                } catch (IOException e) {
                    throw new ClassReadingException(e);
                }
            }
        });
    }

    private void proccessClass(String moduleName, JavaClass javaClass) {

        String cn = javaClass.getClassName();
        String pn = javaClass.getPackageName();
        List<String> interfaceNames = Arrays.asList(javaClass.getInterfaceNames());

        boolean classIsUtility = isClassIsUtility(javaClass);

        classesInfo.put(cn, new Classinfo(moduleName, cn, javaClass.isInterface(), javaClass.isEnum(), classIsUtility));

        containedPackages.computeIfAbsent(pn, k -> new AtomicInteger(0)).incrementAndGet();

        ConstantPool constantPool = javaClass.getConstantPool();
        for (Constant c : constantPool.getConstantPool()) {
            if (c instanceof ConstantClass) {
                ConstantClass cc = (ConstantClass) c;
                String cName = cc.getBytes(constantPool);
                if (cName.startsWith("[L")) {
                    cName = cName.substring(2, cName.length() - 1);
                }

                String targetPn = cName;
                if (cName.lastIndexOf('/') >= 0) {
                    targetPn = cName.substring(0, cName.lastIndexOf('/')).replace("/", ".");
                }

                for (String packagePrefix : packagePrefixes) {
                    if (targetPn.startsWith(packagePrefix)) {

                        String tcn = cName.replace("/", ".");
                        if (!tcn.equals(cn)) {

                            RelationType rt = null;
                            if (interfaceNames.contains(tcn)) {
                                rt = RelationType.IMPLEMENTS;
                            }
                            Link<String, String> link = new Link<>(cn, tcn, 1, rt);

                            if (classes.containsValue(link)) {
                                classes.get(link.hashCode()).incCount();
                            } else {
                                classes.put(link.hashCode(), link);
                            }
                        }

                        if (!targetPn.equals(pn)) {
                            Link<String, String> link = new Link<>(pn, targetPn, 1);

                            if (!packages.containsValue(link)) {
                                packages.put(link.hashCode(), link);
                            } else {
                                packages.get(link.hashCode()).incCount();
                            }
                        }

                        if (tcn.endsWith("Exception")) {
                            Link<String, ExceptionEnt> excLink = new Link<>(pn, new ExceptionEnt(tcn), 0);
                            packageUsesException.computeIfAbsent(excLink.hashCode(), k -> excLink).incCount();
                        }
                    }
                }
            }
        }

    }

    private boolean isClassIsUtility(JavaClass javaClass) {
        boolean classIsUtility = false;
        if (javaClass.isFinal()) {

            List<Method> methodList = Arrays.asList(javaClass.getMethods());
            Optional<Method> constructor = methodList.stream()
                    .filter(method -> method.getName().equals("<init>"))
                    .findFirst();

            if (constructor.isPresent() && constructor.get().isPrivate()) {

                StringBuilder errors = new StringBuilder();

                boolean allMethodsAreStatic = true;
                for (Method method : javaClass.getMethods()) {
                    if (!method.getName().equals("<init>") && !method.isStatic()) {
                        allMethodsAreStatic = false;
                        errors.append("\t").append(method.getName()).append(" is not static\n");
                        break;
                    }
                }

                boolean allFieldsAreSimpleConstants = true;
                for (Field field : javaClass.getFields()) {
                    if (!fieldIsSimpleConstant(field)) {
                        allFieldsAreSimpleConstants = false;
                        errors.append("\t").append(field.getName()).append(" is not simple constant\n");
                        break;
                    }
                }

                if (errors.length() > 0) {
                    errors.insert(0, "\n");
                }

                classIsUtility = allMethodsAreStatic && allFieldsAreSimpleConstants;
                if (!classIsUtility) {
                    logger.info("Is final not utility: " + javaClass.getClassName() + " " + errors.toString());
                }
            }

        }
        return classIsUtility;
    }

    private boolean fieldIsSimpleConstant(Field field) {
        return field.isStatic()
                && field.isFinal()
                && (field.getType() instanceof BasicType
                || field.getType().equals(Type.STRING)
                || (field.getType() instanceof ObjectType
                && ((ObjectType) field.getType()).getClassName().equals("org.slf4j.Logger"))
        );
    }

}
