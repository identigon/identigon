package org.identigon.alterego;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hand-rolled JSON reader/writer for the conformance vector fixtures. No JSON library is a
 * test dependency, and the vector schemas are simple enough (objects, arrays, strings, longs,
 * booleans) that this is the pragmatic choice over adding one.
 */
final class MinimalJson {

  private MinimalJson() {}

  static String write(Object value) {
    StringBuilder sb = new StringBuilder();
    writeValue(value, sb);
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  private static void writeValue(Object value, StringBuilder sb) {
    switch (value) {
      case null -> sb.append("null");
      case String s -> writeString(s, sb);
      case Boolean b -> sb.append(b);
      case Number n -> sb.append(n);
      case Map<?, ?> map -> writeObject((Map<String, Object>) map, sb);
      case List<?> list -> writeArray(list, sb);
      default -> throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
    }
  }

  private static void writeObject(Map<String, Object> map, StringBuilder sb) {
    sb.append('{');
    boolean first = true;
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      writeString(entry.getKey(), sb);
      sb.append(':');
      writeValue(entry.getValue(), sb);
    }
    sb.append('}');
  }

  private static void writeArray(List<?> list, StringBuilder sb) {
    sb.append('[');
    boolean first = true;
    for (Object element : list) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      writeValue(element, sb);
    }
    sb.append(']');
  }

  private static void writeString(String s, StringBuilder sb) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
  }

  static Object parse(String json) {
    Parser parser = new Parser(json);
    Object value = parser.parseValue();
    parser.skipWhitespace();
    if (!parser.atEnd()) {
      throw new IllegalArgumentException("Trailing content at position " + parser.pos);
    }
    return value;
  }

  private static final class Parser {
    private final String json;
    private int pos;

    Parser(String json) {
      this.json = json;
    }

    boolean atEnd() {
      return pos >= json.length();
    }

    void skipWhitespace() {
      while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
        pos++;
      }
    }

    Object parseValue() {
      skipWhitespace();
      char c = json.charAt(pos);
      return switch (c) {
        case '{' -> parseObject();
        case '[' -> parseArray();
        case '"' -> parseString();
        case 't' -> parseLiteral("true", Boolean.TRUE);
        case 'f' -> parseLiteral("false", Boolean.FALSE);
        case 'n' -> parseLiteral("null", null);
        default -> parseNumber();
      };
    }

    Map<String, Object> parseObject() {
      Map<String, Object> map = new LinkedHashMap<>();
      expect('{');
      skipWhitespace();
      if (peek() == '}') {
        pos++;
        return map;
      }
      while (true) {
        skipWhitespace();
        String key = parseString();
        skipWhitespace();
        expect(':');
        Object value = parseValue();
        map.put(key, value);
        skipWhitespace();
        char c = json.charAt(pos);
        if (c == ',') {
          pos++;
        } else if (c == '}') {
          pos++;
          break;
        } else {
          throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
        }
      }
      return map;
    }

    List<Object> parseArray() {
      List<Object> list = new ArrayList<>();
      expect('[');
      skipWhitespace();
      if (peek() == ']') {
        pos++;
        return list;
      }
      while (true) {
        list.add(parseValue());
        skipWhitespace();
        char c = json.charAt(pos);
        if (c == ',') {
          pos++;
        } else if (c == ']') {
          pos++;
          break;
        } else {
          throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
        }
      }
      return list;
    }

    String parseString() {
      expect('"');
      StringBuilder sb = new StringBuilder();
      while (true) {
        char c = json.charAt(pos++);
        if (c == '"') {
          break;
        }
        if (c == '\\') {
          char escape = json.charAt(pos++);
          switch (escape) {
            case '"' -> sb.append('"');
            case '\\' -> sb.append('\\');
            case '/' -> sb.append('/');
            case 'n' -> sb.append('\n');
            case 'r' -> sb.append('\r');
            case 't' -> sb.append('\t');
            case 'u' -> {
              String hex = json.substring(pos, pos + 4);
              pos += 4;
              sb.append((char) Integer.parseInt(hex, 16));
            }
            default -> throw new IllegalArgumentException("Unknown escape: \\" + escape);
          }
        } else {
          sb.append(c);
        }
      }
      return sb.toString();
    }

    Long parseNumber() {
      int start = pos;
      if (peek() == '-') {
        pos++;
      }
      while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
        pos++;
      }
      return Long.parseLong(json.substring(start, pos));
    }

    Object parseLiteral(String literal, Object value) {
      if (!json.startsWith(literal, pos)) {
        throw new IllegalArgumentException("Expected '" + literal + "' at position " + pos);
      }
      pos += literal.length();
      return value;
    }

    char peek() {
      return json.charAt(pos);
    }

    void expect(char c) {
      if (json.charAt(pos) != c) {
        throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
      }
      pos++;
    }
  }
}
