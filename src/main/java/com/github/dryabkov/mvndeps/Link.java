package com.github.dryabkov.mvndeps;


import java.util.Objects;

public class Link<F, T> {

    private final F from;
    private final T to;
    private final RelationType relationType;
    private int count;

    Link(F from, T to, int count) {

        super();
        this.from = from;
        this.to = to;
        this.count = count;
        this.relationType = null;
    }

    Link(F from, T to, int count, RelationType relationType) {

        super();
        this.from = from;
        this.to = to;
        this.count = count;
        this.relationType = relationType;
    }

    public F getFrom() {

        return from;
    }

    public T getTo() {

        return to;
    }

    int getCount() {
        return count;
    }

    RelationType relationType() {
        return relationType;
    }

    void incCount() {
        count++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Link<?, ?> link = (Link<?, ?>) o;
        return Objects.equals(from, link.from) &&
                Objects.equals(to, link.to);
    }

    @Override
    public int hashCode() {

        return Objects.hash(from, to);
    }

    @Override
    public String toString() {
        return "Link{" +
                "from='" + from + '\'' +
                ", to='" + to + '\'' +
                '}';
    }
}
