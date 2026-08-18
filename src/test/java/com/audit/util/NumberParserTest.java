package com.audit.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NumberParserTest {

    @Test
    void parseReturnsNullForEmptyOrAwaitedValues() {
        assertNull(NumberParser.parse(null).value);
        assertNull(NumberParser.parse(" ").value);
        assertNull(NumberParser.parse("--").value);
        assertNull(NumberParser.parse("Awaited").value);
    }

    @Test
    void parseHandlesCommasAndPreFetchMarker() {
        NumberParser.Result result = NumberParser.parse("1,15,476 **");
        assertEquals(115476L, result.value);
        assertTrue(result.preFetch);
    }

    @Test
    void parseIntReturnsParsedIntegerOrNull() {
        assertEquals(37988, NumberParser.parseInt("37,988"));
        assertNull(NumberParser.parseInt("abc"));
    }
}
