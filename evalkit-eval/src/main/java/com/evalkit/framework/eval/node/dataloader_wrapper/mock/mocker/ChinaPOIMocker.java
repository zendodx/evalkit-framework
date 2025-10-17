package com.evalkit.framework.eval.node.dataloader_wrapper.mock.mocker;

import com.evalkit.framework.common.utils.address.ScenicUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 兴趣点 mocker
 * <p>
 * 景区类POI
 * {{scenic}} -> 中国景区
 * {{scenic 河北省}} -> 河北省景区
 * {{scenic 河北省 廊坊市}} -> 河北省廊坊市景区
 */
public class ChinaPOIMocker implements Mocker {
    private final static Map<String, POIStrategy> STRATEGY_POOL = new ConcurrentHashMap<>();

    static {
        STRATEGY_POOL.put("scenic", new ScenicInterface());
    }

    @Override
    public boolean support(String ruleName, List<String> ruleParams) {
        return STRATEGY_POOL.containsKey(ruleName);
    }

    @Override
    public String mock(String ruleName, List<String> ruleParams) {
        POIStrategy strategy = STRATEGY_POOL.get(StringUtils.lowerCase(ruleName));
        if (strategy == null) return null;
        POIContext ctx = new POIContext(ruleParams);
        return strategy.generate(ctx);
    }

    /**
     * 景区上下文
     */
    private static class POIContext {
        private String province;
        private String city;

        public POIContext(List<String> args) {
            if (CollectionUtils.isEmpty(args)) {
                return;
            }
            if (args.size() == 1) {
                province = args.get(0);
            } else if (args.size() == 2) {
                province = args.get(0);
                city = args.get(1);
            } else {
                throw new IllegalArgumentException("Invalid number of arguments");
            }
        }
    }

    /**
     * 兴趣点策略
     */
    private interface POIStrategy {
        String generate(POIContext ctx);
    }

    /**
     * 景区兴趣点
     */
    private static class ScenicInterface implements POIStrategy {

        @Override
        public String generate(POIContext ctx) {
            List<String> scenics;
            if (StringUtils.isNotEmpty(ctx.province) && StringUtils.isNotEmpty(ctx.city)) {
                scenics = ScenicUtils.getScenariosByProvinceAndCity(ctx.province, ctx.city);
            } else if (StringUtils.isNotEmpty(ctx.province)) {
                scenics = ScenicUtils.getScenariosByProvince(ctx.province);
            } else {
                scenics = ScenicUtils.getScenics();
            }
            return Mocker.randomChoose(scenics);
        }
    }
}
