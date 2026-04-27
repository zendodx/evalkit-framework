package com.evalkit.framework.eval.mock.engine;

import com.evalkit.framework.eval.mock.mocker.NumberMocker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class SpelMockRuleEngineTest {
    private SpelMockRuleEngine engine;
    private SpelMockRuleEngine engineFillEmpty;

    @BeforeEach
    void setUp() {
        engine = new SpelMockRuleEngine();
        engineFillEmpty = new SpelMockRuleEngine(true);
    }

    @Test
    void testConstructorDefault() {
        SpelMockRuleEngine e = new SpelMockRuleEngine();
        assertNotNull(e.getMockers());
        assertFalse(e.getMockers().isEmpty());
        assertFalse(e.isFillEmptyStringOnMockFail());
    }

    @Test
    void testConstructorWithFillEmpty() {
        SpelMockRuleEngine e = new SpelMockRuleEngine(true);
        assertTrue(e.isFillEmptyStringOnMockFail());
    }

    @Test
    void testAddMocker() {
        SpelMockRuleEngine e = new SpelMockRuleEngine();
        int originalSize = e.getMockers().size();
        e.addMocker(new NumberMocker());
        assertEquals(originalSize + 1, e.getMockers().size());
    }

    @Test
    void testAddMockersSingleList() {
        SpelMockRuleEngine e = new SpelMockRuleEngine();
        int originalSize = e.getMockers().size();
        e.addMockers(Collections.singletonList(new NumberMocker()));
        assertEquals(originalSize + 1, e.getMockers().size());
    }

    @Test
    void testAddMockersVarargs() {
        SpelMockRuleEngine e = new SpelMockRuleEngine();
        int originalSize = e.getMockers().size();
        e.addMockers(new NumberMocker(), new NumberMocker());
        assertEquals(originalSize + 2, e.getMockers().size());
    }

    @Test
    void testMockIntegerWithSimpleRule() {
        String result = engine.mock("int");
        log.info("Mock result for 'int': {}", result);
        assertNotNull(result);
        try {
            Integer.parseInt(result);
        } catch (NumberFormatException e) {
            fail("Result should be a valid integer: " + result);
        }
    }

    @Test
    void testMockIntegerWithRange() {
        String result = engine.mock("int 10 20");
        log.info("Mock result for 'int 10 20': {}", result);
        assertNotNull(result);
        long value = Long.parseLong(result);
        assertTrue(value >= 10 && value <= 20, "Value should be between 10 and 20");
    }

    @Test
    void testMockFloatWithRange() {
        String result = engine.mock("float 5.5 15.5");
        log.info("Mock result for 'float 5.5 15.5': {}", result);
        assertNotNull(result);
        double value = Double.parseDouble(result);
        assertTrue(value >= 5.5 && value < 15.5, "Value should be between 5.5 and 15.5");
    }

    @Test
    void testMockDate() {
        String result = engine.mock("date");
        log.info("Mock result for 'date': {}", result);
        assertNotNull(result);
        assertTrue(result.contains("-") && result.contains(":"), "Should be a date with format yyyy-MM-dd HH:mm:ss");
    }

    @Test
    void testMockFutureDate() {
        String result = engine.mock("future_date 10");
        log.info("Mock result for 'future_date 10': {}", result);
        assertNotNull(result);
        assertTrue(result.contains("-") && result.contains(":"));
    }

    @Test
    void testMockPastDate() {
        String result = engine.mock("past_date 30");
        log.info("Mock result for 'past_date 30': {}", result);
        assertNotNull(result);
        assertTrue(result.contains("-") && result.contains(":"));
    }

    @Test
    void testMockFuzzyDate() {
        String result = engine.mock("fuzzy_date");
        log.info("Mock result for 'fuzzy_date': {}", result);
        assertNotNull(result);
        assertNotEquals("{{fuzzy_date}}", result);
        assertTrue(result.length() > 0);
    }

    @Test
    void testMockFuzzyDateDay() {
        String result = engine.mock("fuzzy_date day");
        log.info("Mock result for 'fuzzy_date day': {}", result);
        assertNotNull(result);
        // 未来日：即日/改日/他日/来日/当日；过去日：近日/近来/最近
        List<String> dayExpressions = Arrays.asList(
                "即日", "改日", "他日", "来日", "当日",
                "近日", "近来", "最近"
        );
        assertTrue(dayExpressions.contains(result), "Unexpected day expression: " + result);
    }

    @Test
    void testMockFuzzyDateWeek() {
        String result = engine.mock("fuzzy_date week");
        log.info("Mock result for 'fuzzy_date week': {}", result);
        assertNotNull(result);
        // 未来周：周末/本周/下周/未来一周/未来二周/未来三周；过去周：上周/上上周/大上周/过去一周/过去二周/过去三周
        List<String> weekExpressions = Arrays.asList(
                "本周", "下周", "周末", "未来一周", "未来二周", "未来三周",
                "上周", "上上周", "大上周", "过去一周", "过去二周", "过去三周"
        );
        assertTrue(weekExpressions.contains(result), "Unexpected week expression: " + result);
    }

    @Test
    void testMockFuzzyDateMonth() {
        String result = engine.mock("fuzzy_date month");
        log.info("Mock result for 'fuzzy_date month': {}", result);
        assertNotNull(result);
        // 月份表达包含未来（月初/月中/月末/月底/未来一月/未来二月/未来三月）和过去（上月/过去一月/过去二月/过去三月）两类
        List<String> monthExpressions = Arrays.asList(
                "月初", "月中", "月末", "月底", "未来一月", "未来二月", "未来三月",
                "上月", "过去一月", "过去二月", "过去三月"
        );
        assertTrue(monthExpressions.contains(result), "Unexpected month expression: " + result);
    }

    @Test
    void testMockFuzzyDateYear() {
        String result = engine.mock("fuzzy_date year");
        log.info("Mock result for 'fuzzy_date year': {}", result);
        assertNotNull(result);
        // 未来年：今年/明年/后年/年初/年中/年底/下半年/来年/未来一年/未来二年/未来三年
        // 过去年：去年/前年/往年/历年/上半年/过去一年/过去二年/过去三年
        List<String> yearExpressions = Arrays.asList(
                "今年", "明年", "后年", "年初", "年中", "年底", "下半年",
                "来年", "未来一年", "未来二年", "未来三年",
                "去年", "前年", "往年", "历年", "上半年",
                "过去一年", "过去二年", "过去三年"
        );
        assertTrue(yearExpressions.contains(result), "Unexpected year expression: " + result);
    }

    @Test
    void testMatchRulesEmpty() {
        List<String> rules = engine.matchRules("");
        assertNull(rules);
    }

    @Test
    void testMatchRulesNull() {
        List<String> rules = engine.matchRules(null);
        assertNull(rules);
    }

    @Test
    void testMatchRulesSingleRule() {
        List<String> rules = engine.matchRules("Hello {{int}}");
        assertNotNull(rules);
        assertEquals(1, rules.size());
        assertEquals("int", rules.get(0));
    }

    @Test
    void testMatchRulesMultipleRules() {
        List<String> rules = engine.matchRules("Hello {{int}}, age is {{int 18 65}}, date {{date}}");
        assertNotNull(rules);
        assertEquals(3, rules.size());
        assertEquals("int", rules.get(0));
        assertEquals("int 18 65", rules.get(1));
        assertEquals("date", rules.get(2));
    }

    @Test
    void testMatchRulesWithSpaces() {
        List<String> rules = engine.matchRules("Test {{ int }} and {{ float 1.5 2.5 }}");
        assertNotNull(rules);
        assertEquals(2, rules.size());
        assertEquals("int", rules.get(0));
        assertEquals("float 1.5 2.5", rules.get(1));
    }

    @Test
    void testMockWithPatternInText() {
        String text = "User age is {{int 18 65}} years old";
        String result = engine.mock("int 18 65");
        log.info("Mock result: {}", result);
        assertNotNull(result);
        long value = Long.parseLong(result);
        assertTrue(value >= 18 && value <= 65);
    }

    @Test
    void testMockUnsupportedRuleWithFillEmptyFalse() {
        String result = engine.mock("unsupported_rule");
        log.info("Mock result for unsupported rule: {}", result);
        assertEquals("{{unsupported_rule}}", result);
    }

    @Test
    void testMockUnsupportedRuleWithFillEmptyTrue() {
        String result = engineFillEmpty.mock("unsupported_rule");
        log.info("Mock result for unsupported rule with fillEmpty: {}", result);
        assertEquals("", result);
    }

    @Test
    void testMockWithRawRuleName() {
        String result = engine.mock("int 10 20", "int", Arrays.asList("10", "20"));
        log.info("Mock result with explicit params: {}", result);
        assertNotNull(result);
        long value = Long.parseLong(result);
        assertTrue(value >= 10 && value <= 20);
    }

    @RepeatedTest(10)
    void testMockChinaAddress() {
        String result = engine.mock("address");
        log.info("Mock result for 'address': {}", result);
        assertNotNull(result);
        // Address should contain Chinese characters or be a valid response
        assertTrue(result.length() > 0);
    }

    @RepeatedTest(10)
    void testMockChinaPoi() {
        String result = engine.mock("poi");
        log.info("Mock result for 'poi': {}", result);
        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    @Test
    void testMockComplexScenario() {
        String rawText = "用户年龄 {{int 18 65}}，注册日期 {{past_date 30}}，下一次登录 {{future_date 7}}，获得积分 {{int 100 1000}}";
        List<String> rules = engine.matchRules(rawText);
        log.info("Extracted rules: {}", rules);
        assertNotNull(rules);
        assertEquals(4, rules.size());

        // Mock each rule
        for (String rule : rules) {
            String mocked = engine.mock(rule);
            assertNotNull(mocked);
            assertNotEquals("{{" + rule + "}}", mocked);
            log.info("Rule: {} -> Mocked: {}", rule, mocked);
        }
    }

    @Test
    void testMockNestedBraces() {
        List<String> rules = engine.matchRules("Test {{int}} with {{float 1.5 2.5}}");
        assertEquals(2, rules.size());
    }

    @Test
    void testEmptyMockersList() {
        SpelMockRuleEngine e = new SpelMockRuleEngine(true);
        e.setMockers(Collections.emptyList());
        String result = e.mock("int");
        assertEquals("", result);
    }

    @Test
    void testAddMockersToNullList() {
        SpelMockRuleEngine e = new SpelMockRuleEngine();
        e.setMockers(null);
        e.addMocker(new NumberMocker());
        assertNotNull(e.getMockers());
        assertTrue(e.getMockers().size() > 0);
    }
}