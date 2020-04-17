package com.github.dryabkov.mvndeps.analyzer;

import com.github.dryabkov.mvndeps.ClassBlock;
import com.github.dryabkov.mvndeps.ClassBlockType;
import com.github.dryabkov.mvndeps.Classinfo;
import com.github.dryabkov.mvndeps.Link;
import com.github.dryabkov.mvndeps.exceptions.CheckingInternalException;
import org.apache.maven.plugin.logging.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.KShortestSimplePaths;
import org.jgrapht.graph.DefaultDirectedGraph;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Check {

    @NonNull
    private final Log logger;

    @NonNull
    private final Map<String, Classinfo> classInfos;

    @NonNull
    private final Collection<Link<String, String>> classesRelations;

    public Check(@NonNull Map<String, Classinfo> classInfos,
                 @NonNull Collection<Link<String, String>> classesRelations, @NonNull Log logger) {

        this.classInfos = classInfos;
        this.classesRelations = classesRelations;
        this.logger = logger;
    }

    @NonNull
    private Classinfo getClassInfo(@NonNull String from) {
        if (!classInfos.containsKey(from)) {
            throw new CheckingInternalException("Unknown class " + from);
        } else {
            return classInfos.get(from);
        }
    }

    public void main(@NonNull Writer bufferedWriter) throws IOException {

        if (classesRelations.isEmpty() || classInfos.isEmpty()) {
            logger.error("Empty data");
            return;
        }

        Graph<String, CountedEdge> graph = new DefaultDirectedGraph<>(CountedEdge.class);
        Map<String, Set<ClassBlock>> clusters = new HashMap<>();
        fillGraph(graph, clusters);

        writeDotHeader(bufferedWriter);
        writeDotClusters(clusters, bufferedWriter);

        KShortestSimplePaths<String, CountedEdge> kShortestSimplePaths = new KShortestSimplePaths<>(graph);
        Set<String> processedEdges = new HashSet<>();

        for (Link<String, String> line : classesRelations) {

            Classinfo classInfoFrom = getClassInfo(line.getFrom());
            Classinfo classInfoTo = getClassInfo(line.getTo());

            String vertexFrom = format(classInfoFrom).getName();
            String vertexTo = format(classInfoTo).getName();
            String key = vertexFrom + "~" + vertexTo;

            if (!processedEdges.contains(key) && !classInfoFrom.moduleName.equals(classInfoTo.moduleName)) {
                boolean edgeWarn = false;
                if (!classInfoTo.isInterface && !classInfoTo.isEnum && !classInfoTo.isUtility) {
                    List<GraphPath<String, CountedEdge>> paths = kShortestSimplePaths.getPaths(
                            vertexFrom, vertexTo, 2);
                    if (paths.size() > 1) {
                        edgeWarn = true;
                        paths.forEach(path -> {
                            List<String> vertexList = path.getVertexList();
                            logger.info(vertexList.remove(0));
                            vertexList.forEach(vertex -> logger.info(" -> " + vertex));
                            logger.info("");
                            classesRelations.forEach(cr -> {
                                if (getPackage(cr.getFrom()).equals(getPackage(classInfoFrom.name)) &&
                                        getPackage(cr.getTo()).equals(getPackage(classInfoTo.name))) {
                                    Classinfo candidat = getClassInfo(cr.getTo());
                                    if (!candidat.isUtility && !candidat.isEnum && !candidat.isInterface) {
                                        logger.info("    " + cr.getFrom() + " -> " + cr.getTo());
                                    }
                                }
                            });
                        });
                    }
                }

                int count = graph.getEdge(vertexFrom, vertexTo).getCount();

                bufferedWriter.write(dotFormatEdge(count, classInfoFrom, classInfoTo, edgeWarn));
                processedEdges.add(key);
            }
        }

        writeDotTail(bufferedWriter);
    }

    private void fillGraph(Graph<String, CountedEdge> graph, Map<String, Set<ClassBlock>> clusters) {
        for (Link<String, String> line : classesRelations) {
            Classinfo classInfoFrom = getClassInfo(line.getFrom());
            Classinfo classInfoTo = getClassInfo(line.getTo());

            ClassBlock from = format(classInfoFrom);
            String vertexFrom = from.getName();
            ClassBlock to = format(classInfoTo);
            String vertexTo = to.getName();

            if (!classInfoFrom.moduleName.equals(classInfoTo.moduleName)) {
                if (graph.containsEdge(vertexFrom, vertexTo)) {
                    graph.getEdge(vertexFrom, vertexTo)
                            .incCount();
                } else {
                    graph.addVertex(vertexFrom);
                    graph.addVertex(vertexTo);
                    graph.addEdge(vertexFrom, vertexTo);

                    clusters.computeIfAbsent(classInfoFrom.moduleName, mn -> new HashSet<>())
                            .add(from);

                    clusters.computeIfAbsent(classInfoTo.moduleName, mn -> new HashSet<>())
                            .add(to);

                }
            }
        }
    }

    private String getPackage(@NonNull String className) {
        return className.substring(0, className.lastIndexOf('.'));
    }

    private void writeDotClusters(Map<String, Set<ClassBlock>> clusters, Writer bufferedWriter) throws IOException {
        for (Map.Entry<String, Set<ClassBlock>> entry : clusters.entrySet()) {
            bufferedWriter.write("subgraph cluster_" + entry.getKey()
                    .replaceAll("[:-]", "_")
                    .replace(" ", "_") + "{\n");
            bufferedWriter.write("label=\"" + entry.getKey() + "\";\n");
            for (ClassBlock node : entry.getValue()) {
                String color;
                switch (node.getType()) {
                    case ENUMS:
                        color = "green";
                        break;
                    case UTILITIES:
                        color = "yellow";
                        break;
                    case INTERFACES:
                        color = "blue";
                        break;
                    default:
                        color = "black";
                }
                String shortName = node.getName().substring(node.getName().indexOf(':') + 1);
                bufferedWriter.write(String.format("\"%s\"[color=%s,label=\"%s\"];\n", node.getName(), color, shortName));
            }
            bufferedWriter.write("}\n");
        }
    }


    private void writeDotHeader(@NonNull Writer bufferedWriter) throws IOException {
        bufferedWriter.write("digraph g {");
        bufferedWriter.write("node [shape=box];\n");
        bufferedWriter.write("rankdir = LR;\n");
        bufferedWriter.write("ranksep = 4;\n");
    }

    private void writeDotTail(@NonNull Writer bufferedWriter) throws IOException {
        bufferedWriter.write("}\n");
    }

    @NonNull
    private String dotFormatEdge(int count, @NonNull Classinfo from, @NonNull Classinfo to, boolean edgeWarn) {
        return "\"" + format(from).getName() + "\" -> \"" + format(to).getName() + "\"" +
                (edgeWarn ? " [color=red;" + "headlabel=\"" + count + "\";]" : "")
                + ";\n";
    }

    @NonNull
    private ClassBlock format(@NonNull Classinfo info) {
        String idx;
        ClassBlockType type;
        if (info.isEnum) {
            idx = "E";
            type = ClassBlockType.ENUMS;
        } else if (info.isInterface) {
            idx = "I";
            type = ClassBlockType.INTERFACES;
        } else if (info.isUtility) {
            idx = "U";
            type = ClassBlockType.UTILITIES;
        } else {
            idx = "C";
            type = ClassBlockType.CONCRETE_OR_ABSTRACT;
        }

        return new ClassBlock(info.moduleName + ":" + getPackage(info.name) + ".<" + idx + ">", type);
    }


}
