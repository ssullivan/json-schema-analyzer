package com.github.ssullivan.analyze;

import com.github.ssullivan.types.*;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Folds two independently-inferred {@link JsonType} schemas — typically the shapes of two
 * sample JSON records — into one combined shape: fields present in only one become optional,
 * and fields whose type differs between the two become a {@link UnionType}.
 * <p>
 * {@link ObjectType}s for the same field position are always structurally combined, since they
 * represent the same evolving record across samples. {@link ArrayType}s, by contrast, only have
 * their element-shape sets unioned — array elements may be intentionally heterogeneous (e.g. a
 * discriminated union of shapes), so they are never structurally merged against each other.
 */
public final class SchemaMerger {
    private SchemaMerger() {
    }

    public static JsonType merge(JsonType a, JsonType b) {
        if (a.equals(b)) {
            return a;
        }
        if (isNumberish(a) && isNumberish(b)) {
            return NumberType.instance();
        }
        if (a instanceof ObjectType oa && b instanceof ObjectType ob) {
            return mergeObjects(oa, ob);
        }
        if (a instanceof ArrayType aa && b instanceof ArrayType ab) {
            return mergeArrays(aa, ab);
        }
        return unionOf(a, b);
    }

    private static ObjectType mergeObjects(ObjectType oa, ObjectType ob) {
        ObjectType result = new ObjectType();

        Set<String> names = new LinkedHashSet<>(oa.getFields().keySet());
        names.addAll(ob.getFields().keySet());

        for (String name : names) {
            JsonType aType = oa.getFields().get(name);
            JsonType bType = ob.getFields().get(name);

            if (aType != null && bType != null) {
                result.addField(name, merge(aType, bType));
            } else {
                result.addField(name, aType != null ? aType : bType);
                result.markOptional(name);
            }

            if (oa.isOptional(name) || ob.isOptional(name)) {
                result.markOptional(name);
            }
        }

        return result;
    }

    private static ArrayType mergeArrays(ArrayType aa, ArrayType ab) {
        Set<JsonType> elements = new LinkedHashSet<>(aa.getFields());
        elements.addAll(ab.getFields());
        collapseNumbers(elements);

        ArrayType result = new ArrayType();
        elements.forEach(result::addField);
        return result;
    }

    private static JsonType unionOf(JsonType a, JsonType b) {
        Set<JsonType> members = new LinkedHashSet<>();
        addFlattened(members, a);
        addFlattened(members, b);
        collapseNumbers(members);

        if (members.size() == 1) {
            return members.iterator().next();
        }
        return new UnionType(members);
    }

    private static void addFlattened(Set<JsonType> members, JsonType type) {
        if (type instanceof UnionType unionType) {
            members.addAll(unionType.getMembers());
        } else {
            members.add(type);
        }
    }

    private static void collapseNumbers(Set<JsonType> types) {
        if (types.stream().filter(SchemaMerger::isNumberish).count() > 1) {
            types.removeIf(SchemaMerger::isNumberish);
            types.add(NumberType.instance());
        }
    }

    private static boolean isNumberish(JsonType type) {
        return type instanceof IntNumberType || type instanceof FloatNumberType || type instanceof NumberType;
    }
}
