package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrictJsonReaderTest {
    @Test
    void readsObjectsArraysNumbersAndUnicode() {
        Object parsed = StrictJsonReader.parse(
            "{\"text\":\"A\\uD83D\\uDE80\",\"values\":[0,-1.25e2,true,null]}");

        Map<?, ?> object = assertInstanceOf(Map.class, parsed);
        assertEquals("A🚀", object.get("text"));
        List<?> values = assertInstanceOf(List.class, object.get("values"));
        assertEquals(new BigDecimal("0"), values.get(0));
        assertEquals(new BigDecimal("-1.25e2"), values.get(1));
        assertEquals(Boolean.TRUE, values.get(2));
        assertEquals(null, values.get(3));
    }

    @Test
    void rejectsDuplicateFieldsAndTrailingTokens() {
        assertThrows(IllegalArgumentException.class,
            () -> StrictJsonReader.parse("{\"value\":1,\"value\":2}"));
        assertThrows(IllegalArgumentException.class,
            () -> StrictJsonReader.parse("{} []"));
    }

    @Test
    void rejectsInvalidNumbersAndUnicode() {
        assertThrows(IllegalArgumentException.class,
            () -> StrictJsonReader.parse("{\"value\":01}"));
        assertThrows(IllegalArgumentException.class,
            () -> StrictJsonReader.parse("{\"value\":1.}"));
        assertThrows(IllegalArgumentException.class,
            () -> StrictJsonReader.parse("{\"value\":\"\\uD83D\"}"));
    }
}
