package org.identigon.incognito.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Locks the {@link JsonWriter} contract (comma placement, nesting, escaping) independently of the
 * emitter.
 */
class JsonWriterTest {

  @Test
  void emptyObject() {
    assertEquals("{}", new JsonWriter().beginObject().endObject().toJson());
  }

  @Test
  void emptyArray() {
    assertEquals("[]", new JsonWriter().beginArray().endArray().toJson());
  }

  @Test
  void flatObjectUsesColonSpaceAndCommaSpace() {
    String json =
        new JsonWriter()
            .beginObject()
            .field("name", "Alice")
            .field("count", 3L)
            .field("ok", true)
            .endObject()
            .toJson();
    assertEquals("{\"name\": \"Alice\", \"count\": 3, \"ok\": true}", json);
  }

  @Test
  void nullStringFieldWritesNullLiteral() {
    assertEquals(
        "{\"x\": null}",
        new JsonWriter().beginObject().field("x", (String) null).endObject().toJson());
  }

  @Test
  void nestedArrayOfObjects() {
    JsonWriter jw = new JsonWriter().beginObject();
    jw.name("items").beginArray();
    jw.beginObject().field("id", 1L).endObject();
    jw.beginObject().field("id", 2L).endObject();
    jw.endArray().endObject();
    assertEquals("{\"items\": [{\"id\": 1}, {\"id\": 2}]}", jw.toJson());
  }

  @Test
  void stringArrayElements() {
    JsonWriter jw = new JsonWriter().beginObject();
    jw.name("cols").beginArray();
    jw.value("a");
    jw.value("b");
    jw.endArray().endObject();
    assertEquals("{\"cols\": [\"a\", \"b\"]}", jw.toJson());
  }

  @Test
  void escapesSpecialCharacters() {
    String json =
        new JsonWriter().beginObject().field("s", "say \"hi\"\n\ttab\\back").endObject().toJson();
    assertEquals("{\"s\": \"say \\\"hi\\\"\\n\\ttab\\\\back\"}", json);
  }

  @Test
  void escapesControlCharactersAsUnicode() {
    String json =
        new JsonWriter().beginObject().field("c", String.valueOf((char) 1)).endObject().toJson();
    assertEquals("{\"c\": \"\\u0001\"}", json);
  }
}
