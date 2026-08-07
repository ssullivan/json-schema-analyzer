package io.github.ssullivan.types;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a field observed as more than one distinct type across merged samples — produced
 * only by {@link io.github.ssullivan.analyze.SchemaMerger}.
 */
public final class UnionType implements JsonType {
    private final Set<JsonType> members;

    public UnionType(Set<JsonType> members) {
        this.members = new LinkedHashSet<>(members);
    }

    public Set<JsonType> getMembers() {
        return members;
    }

    @Override
    public String toString() {
        return members.stream().map(String::valueOf).collect(Collectors.joining("|"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnionType unionType = (UnionType) o;
        return Objects.equals(members, unionType.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(members);
    }
}
