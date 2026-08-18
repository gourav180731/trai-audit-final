package com.audit.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationParserTest {

    @Test
    void toSecondsParsesHourMinuteSecondFormats() {
        assertEquals(33463, DurationParser.toSeconds("9h 17m 43s"));
        assertEquals(73, DurationParser.toSeconds("1m 13s"));
        assertEquals(16, DurationParser.toSeconds("16s"));
    }

    @Test
    void toSecondsReturnsMinusOneForInvalidInput() {
        assertEquals(-1, DurationParser.toSeconds(null));
        assertEquals(-1, DurationParser.toSeconds("--"));
        assertEquals(-1, DurationParser.toSeconds("Awaited"));
        assertEquals(-1, DurationParser.toSeconds("not-a-duration"));
    }

    @Test
    void toHmsFormatsSecondsBackToReadableOutput() {
        assertEquals("--", DurationParser.toHms(-1));
        assertEquals("0s", DurationParser.toHms(0));
        assertEquals("1m 13s", DurationParser.toHms(73));
        assertEquals("9h 17m 43s", DurationParser.toHms(33463));
    }
}
