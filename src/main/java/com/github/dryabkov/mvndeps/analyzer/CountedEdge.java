package com.github.dryabkov.mvndeps.analyzer;

import org.jgrapht.graph.DefaultEdge;

public class CountedEdge extends DefaultEdge {

    private int count = 1;

    public void incCount() {
        count++;
    }

    public int getCount() {
        return count;
    }
}
