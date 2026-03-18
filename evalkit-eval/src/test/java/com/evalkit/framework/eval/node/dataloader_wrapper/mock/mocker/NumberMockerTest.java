package com.evalkit.framework.eval.node.dataloader_wrapper.mock.mocker;

import com.evalkit.framework.eval.mock.mocker.NumberMocker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class NumberMockerTest {
    private final NumberMocker mocker = new NumberMocker();

    @Test
    void testSupportRuleName() {
        assertTrue(mocker.support("int", null));
        assertTrue(mocker.support("INT", null));
        assertTrue(mocker.support("float", null));
        assertTrue(mocker.support("FLOAT", null));
        assertFalse(mocker.support("string", null));
        assertFalse(mocker.support("date", null));
    }

    @Test
    void testIntegerDefaultRange() {
        String result = mocker.mock("int", Collections.emptyList());
        assertNotNull(result);
        int value = Integer.parseInt(result);
        assertTrue(value >= 0 && value <= 100, "Value should be between 0 and 100");
        log.info("Random int (default range): {}", result);
    }

    @RepeatedTest(50)
    void testIntegerWithMinValue() {
        String result = mocker.mock("int", Collections.singletonList("50"));
        assertNotNull(result);
        long value = Long.parseLong(result);
        assertTrue(value >= 50 && value <= 100, "Value should be between 50 and 100");
        log.info("Random int (min=50): {}", result);
    }

    @RepeatedTest(50)
    void testIntegerWithRange() {
        String result = mocker.mock("int", Arrays.asList("100", "200"));
        assertNotNull(result);
        long value = Long.parseLong(result);
        assertTrue(value >= 100 && value <= 200, "Value should be between 100 and 200");
        log.info("Random int (100-200): {}", result);
    }

    @Test
    void testFloatDefaultRange() {
        String result = mocker.mock("float", Collections.emptyList());
        assertNotNull(result);
        double value = Double.parseDouble(result);
        assertTrue(value >= 0.0 && value < 100.0, "Value should be between 0.0 and 100.0");
        log.info("Random float (default range): {}", result);
    }

    @RepeatedTest(50)
    void testFloatWithMinValue() {
        String result = mocker.mock("float", Collections.singletonList("10.5"));
        assertNotNull(result);
        double value = Double.parseDouble(result);
        assertTrue(value >= 10.5 && value < 100.0, "Value should be between 10.5 and 100.0");
        log.info("Random float (min=10.5): {}", result);
    }

    @RepeatedTest(50)
    void testFloatWithRange() {
        String result = mocker.mock("float", Arrays.asList("5.5", "15.5"));
        assertNotNull(result);
        double value = Double.parseDouble(result);
        assertTrue(value >= 5.5 && value < 15.5, "Value should be between 5.5 and 15.5");
        log.info("Random float (5.5-15.5): {}", result);
    }

    @RepeatedTest(50)
    void testNegativeIntegerRange() {
        String result = mocker.mock("int", Arrays.asList("-100", "-10"));
        assertNotNull(result);
        long value = Long.parseLong(result);
        assertTrue(value >= -100 && value <= -10, "Value should be between -100 and -10");
        log.info("Random int (negative range): {}", result);
    }

    @RepeatedTest(50)
    void testNegativeFloatRange() {
        String result = mocker.mock("float", Arrays.asList("-50.5", "-10.5"));
        assertNotNull(result);
        double value = Double.parseDouble(result);
        assertTrue(value >= -50.5 && value < -10.5, "Value should be between -50.5 and -10.5");
        log.info("Random float (negative range): {}", result);
    }

    @Test
    void testZeroValue() {
        String result = mocker.mock("int", Arrays.asList("0", "0"));
        assertNotNull(result);
        long value = Long.parseLong(result);
        assertEquals(0, value, "Value should be 0");
        log.info("Random int (0-0): {}", result);
    }

    @Test
    void testLargeIntegerValue() {
        String result = mocker.mock("int", Arrays.asList("1000000", "2000000"));
        assertNotNull(result);
        long value = Long.parseLong(result);
        assertTrue(value >= 1000000 && value <= 2000000, "Value should be in range");
        log.info("Random int (large range): {}", result);
    }

    @Test
    void testInvalidIntegerArgsThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mocker.mock("int", Collections.singletonList("abc")));
        log.info(ex.getMessage());
        assertTrue(ex.getMessage().contains("Error parsing args"));
    }

    @Test
    void testInvalidFloatArgsThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mocker.mock("float", Arrays.asList("10.5", "abc")));
        log.info(ex.getMessage());
        assertTrue(ex.getMessage().contains("Error parsing args"));
    }

    @Test
    void testTooManyArgsThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mocker.mock("int", Arrays.asList("10", "20", "30", "40")));
        log.info(ex.getMessage());
        assertTrue(ex.getMessage().contains("Error parsing args"));
    }

    @Test
    void testUnsupportedRuleReturnsNull() {
        assertNull(mocker.mock("unknown_rule", Collections.emptyList()));
    }

    @Test
    void testIntegerCaseInsensitive() {
        String result = mocker.mock("INT", Collections.emptyList());
        assertNotNull(result);
        long value = Long.parseLong(result);
        assertTrue(value >= 0 && value <= 100, "Value should be between 0 and 100");
        log.info("Random INT (case-insensitive): {}", result);
    }

    @Test
    void testFloatCaseInsensitive() {
        String result = mocker.mock("FLOAT", Collections.emptyList());
        assertNotNull(result);
        double value = Double.parseDouble(result);
        assertTrue(value >= 0.0 && value < 100.0, "Value should be between 0.0 and 100.0");
        log.info("Random FLOAT (case-insensitive): {}", result);
    }
}

