package com.github.ssullivan.analyze;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * The JsonPath is used to represent the path of field names as the {@link JsonSchemaAnalyzer}
 * traverses the JSON stream.
 */
public class JsonPath {
    private final Deque<String> paths = new ArrayDeque<>();


    JsonPath() {
    }

    public boolean isEmpty() {
        return paths.isEmpty();
    }

    public boolean nonEmpty() {
        return !isEmpty();
    }

    public void push(String path) {
        this.paths.push(path);
    }

    public void pop() {
        this.paths.pop();
    }

    @Override
    public String toString() {
        Iterator<String> itty = paths.descendingIterator();
        StringBuilder retval = new StringBuilder();
        while (itty.hasNext()) {
            retval.append(itty.next());
        }
        return retval.toString();
    }
}
