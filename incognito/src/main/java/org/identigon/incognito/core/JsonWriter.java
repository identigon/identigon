package org.identigon.incognito.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Minimal, dependency-free JSON serialiser for the DPIA artefact. Owns comma placement,
 * brace/bracket nesting, and string escaping, so the emitted JSON cannot be structurally malformed.
 * Produces compact single-line output with a readable {@code "key": value} / {@code , } token style.
 * Not a general-purpose library — it supports exactly the shapes {@link DpiaArtefactEmitter} needs.
 */
final class JsonWriter {

    private final StringBuilder sb = new StringBuilder();
    // One frame per open object/array; frame[0] = "this container already has a child" (needs a comma).
    private final Deque<boolean[]> stack = new ArrayDeque<>();
    // True immediately after name(): the next value/beginObject/beginArray is that name's value and
    // must NOT be preceded by a comma.
    private boolean expectingValue = false;

    /** Creates an empty writer positioned at the document root. */
    JsonWriter() {
        stack.push(new boolean[]{false}); // synthetic root frame
    }

    private void preItem() {
        if (expectingValue) {
            expectingValue = false;
            return;
        }
        boolean[] top = stack.peek();
        if (top[0]) {
            sb.append(", ");
        } else {
            top[0] = true;
        }
    }

    JsonWriter beginObject() {
        preItem();
        sb.append('{');
        stack.push(new boolean[]{false});
        return this;
    }

    JsonWriter endObject() {
        stack.pop();
        sb.append('}');
        return this;
    }

    JsonWriter beginArray() {
        preItem();
        sb.append('[');
        stack.push(new boolean[]{false});
        return this;
    }

    JsonWriter endArray() {
        stack.pop();
        sb.append(']');
        return this;
    }

    /** Writes {@code "key": } and expects a following value (object, array, or scalar). */
    JsonWriter name(String key) {
        preItem();
        sb.append(quote(key)).append(": ");
        expectingValue = true;
        return this;
    }

    JsonWriter field(String key, String value) {
        name(key);
        sb.append(value == null ? "null" : quote(value));
        expectingValue = false;
        return this;
    }

    JsonWriter field(String key, long value) {
        name(key);
        sb.append(value);
        expectingValue = false;
        return this;
    }

    JsonWriter field(String key, boolean value) {
        name(key);
        sb.append(value);
        expectingValue = false;
        return this;
    }

    /** Appends a string element inside an array. */
    JsonWriter value(String v) {
        preItem();
        sb.append(v == null ? "null" : quote(v));
        return this;
    }

    /** Returns the serialised JSON built so far. */
    String toJson() {
        return sb.toString();
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
