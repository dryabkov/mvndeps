package com.github.dryabkov.mvndeps.analyzer;

import com.github.dryabkov.mvndeps.Classinfo;
import com.github.dryabkov.mvndeps.Link;
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
    private final Map<String, Classinfo> classInfos;

    @NonNull
    private final Collection<Link<String, String>> classesRelations;

    public Check(@NonNull Map<String, Classinfo> classInfos,
                 @NonNull Collection<Link<String, String>> classesRelations) {

        this.classInfos = classInfos;
        this.classesRelations = classesRelations;
    }

    @NonNull
    private Classinfo getClassInfo(@NonNull String from) {
        if (!classInfos.containsKey(from)) {
            throw new RuntimeException("Unknown class " + from);
        } else {
            return classInfos.get(from);
        }
    }

    public void main(@NonNull Writer bufferedWriter) throws IOException {

        if (classesRelations.isEmpty() || classInfos.isEmpty()) {
            System.out.println("Empty data");
            return;
        }

        Graph<String, CountedEdge> graph = new DefaultDirectedGraph<>(CountedEdge.class);

        Map<String, Set<String>> clusters = new HashMap<>();

        for (Link<String, String> line : classesRelations) {
            Classinfo classInfoFrom = getClassInfo(line.getFrom());
            Classinfo classInfoTo = getClassInfo(line.getTo());

            String vertexFrom = format(classInfoFrom);
            String vertexTo = format(classInfoTo);

            if (!classInfoFrom.moduleName.equals(classInfoTo.moduleName)) {
                if (graph.containsEdge(vertexFrom, vertexTo)) {
                    graph.getEdge(vertexFrom, vertexTo)
                            .incCount();
                } else {
                    graph.addVertex(vertexFrom);
                    graph.addVertex(vertexTo);
                    graph.addEdge(vertexFrom, vertexTo);

                    clusters.computeIfAbsent(classInfoFrom.moduleName, mn -> new HashSet<>())
                            .add(format(classInfoFrom));

                    clusters.computeIfAbsent(classInfoTo.moduleName, mn -> new HashSet<>())
                            .add(format(classInfoTo));

                }
            }
        }

        writeDotHeader(bufferedWriter);
        writeDotClusters(clusters, bufferedWriter);

        KShortestSimplePaths<String, CountedEdge> kShortestSimplePaths = new KShortestSimplePaths<>(graph);
        Set<String> processedEdges = new HashSet<>();

        for (Link<String, String> line : classesRelations) {

            Classinfo classInfoFrom = getClassInfo(line.getFrom());
            Classinfo classInfoTo = getClassInfo(line.getTo());

            String vertexFrom = format(classInfoFrom);
            String vertexTo = format(classInfoTo);
            String key = vertexFrom + "~" + vertexTo;

            if (!processedEdges.contains(key) && !classInfoFrom.moduleName.equals(classInfoTo.moduleName)) {
                boolean edgeWarn = false;
                if (!classInfoTo.isInterfce && !classInfoTo.isEnum && !classInfoTo.isUtility) {
                    List<GraphPath<String, CountedEdge>> paths = kShortestSimplePaths.getPaths(
                            vertexFrom, vertexTo, 2);
                    if (paths.size() > 1) {
                        edgeWarn = true;
                        //TODO system.out to logger
                        paths.forEach(path -> {
                            List<String> vertexList = path.getVertexList();
                            System.out.print(vertexList.remove(0));
                            vertexList.forEach(vertex -> System.out.print(" -> " + vertex));
                            System.out.println();
                            classesRelations.forEach(cr -> {
                                if (getPackage(cr.getFrom()).equals(getPackage(classInfoFrom.name)) &&
                                        getPackage(cr.getTo()).equals(getPackage(classInfoTo.name))) {
                                    Classinfo candidat = getClassInfo(cr.getTo());
                                    if (!candidat.isUtility && !candidat.isEnum && !candidat.isInterfce) {
                                        System.out.println("    " + cr.getFrom() + " -> " + cr.getTo());
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

        bufferedWriter.write("}\n");
        bufferedWriter.close();

    }

    private String getPackage(@NonNull String className) {
        return className.substring(0, className.lastIndexOf("."));
    }

    private void writeDotClusters(Map<String, Set<String>> clusters, Writer bufferedWriter) throws IOException {
        for (Map.Entry<String, Set<String>> entry : clusters.entrySet()) {
            bufferedWriter.write("subgraph cluster_" + entry.getKey()
                    .replaceAll("[:-]", "_")
                    .replaceAll(" ", "_") + "{\n");
            bufferedWriter.write("label=\"" + entry.getKey() + "\";\n");
            for (String node : entry.getValue()) {
                //FIXME не парсить, а передавть типизировано
                String color = "black";
                if (node.endsWith("<I>")) {
                    color = "blue";
                }
                if (node.endsWith("<E>")) {
                    color = "green";
                }
                if (node.endsWith("<U>")) {
                    color = "yellow";
                }
                String shortName = node.substring(node.indexOf(':') + 1);
                bufferedWriter.write(String.format("\"%s\"[color=%s,label=\"%s\"];\n", node, color, shortName));
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

    @NonNull
    private String dotFormatEdge(int count, @NonNull Classinfo from, @NonNull Classinfo to, boolean edgeWarn) {
        return "\"" + format(from) + "\" -> \"" + format(to) + "\"" +
                (edgeWarn ? " [color=red;" + "headlabel=\"" + count + "\";]" : "")
                + ";\n";
    }

    @NonNull
    private String format(@NonNull Classinfo info) {
        String idx;
        if (info.isEnum) {
            idx = "E";
        } else if (info.isInterfce) {
            idx = "I";
        } else if (info.isUtility) {
            idx = "U";
        } else {
            idx = "C";
        }

        return info.moduleName + ":" + getPackage(info.name) + ".<" + idx + ">";
    }


}
