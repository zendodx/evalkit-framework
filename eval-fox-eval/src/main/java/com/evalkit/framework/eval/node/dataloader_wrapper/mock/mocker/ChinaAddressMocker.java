package com.evalkit.framework.eval.node.dataloader_wrapper.mock.mocker;

import com.evalkit.framework.common.utils.address.AddressUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中国地区Mocker,支持到省,市,区,街道级别,不支持村,香港澳门台湾不支持街道,村级别
 * <p>
 * {{province}}  返回一个省名称
 * {{city}}  返回一个城市名称
 * {{city 河北省}}  返回一个河北省的城市名称
 * {{area}} 返回一个区县名称
 * {{area 河北省}} 返回一个河北省的区县名称
 * {{area 河北省 石家庄市}} 返回一个河北省石家庄市的区县名称
 * {{street}} 返回一个街道名称
 * {{street 河北省}} 返回一个河北省的街道名称
 * {{street 河北省 石家庄市}} 返回一个河北省石家庄市的街道名称
 * {{street 河北省 石家庄市}} 返回一个河北省石家庄市的街道名称
 * {{street 河北省 石家庄市}} 返回一个河北省石家庄市的街道名称
 * {{street 河北省 石家庄市 高邑县}} 返回一个河北省石家庄市高邑县的街道名称
 */
public class ChinaAddressMocker implements Mocker {
    private static final Map<String, AddressStrategy> STRATEGY_POOL = new ConcurrentHashMap<>();

    static {
        STRATEGY_POOL.put("province", new ProvinceStrategy());
        STRATEGY_POOL.put("city", new CityStrategy());
        STRATEGY_POOL.put("area", new AreaStrategy());
        STRATEGY_POOL.put("street", new StreetStrategy());
    }

    @Override
    public boolean support(String ruleName, List<String> ruleParams) {
        return STRATEGY_POOL.containsKey(ruleName);
    }

    @Override
    public String mock(String ruleName, List<String> ruleParams) {
        AddressStrategy strategy = STRATEGY_POOL.get(StringUtils.lowerCase(ruleName));
        if (strategy == null) {
            return null;
        }
        AddressContext ctx = new AddressContext(ruleParams);
        return strategy.generate(ctx);
    }

    /**
     * 参数上下文
     */
    private static class AddressContext {
        private String province;
        private String city;
        private String area;

        public AddressContext(List<String> args) {
            if (CollectionUtils.isEmpty(args)) return;
            if (args.size() == 1) {
                province = args.get(0);
            } else if (args.size() == 2) {
                province = args.get(0);
                city = args.get(1);
            } else if (args.size() == 3) {
                province = args.get(0);
                city = args.get(1);
                area = args.get(2);
            } else {
                throw new IllegalArgumentException("Invalid number of arguments");
            }
        }
    }

    /**
     * 地址策略
     */
    private interface AddressStrategy {
        String generate(AddressContext ctx);
    }

    /**
     * mock省名称
     */
    private static class ProvinceStrategy implements AddressStrategy {
        @Override
        public String generate(AddressContext ctx) {
            return Mocker.randomChoose(AddressUtils.getProvinceNames());
        }
    }

    /**
     * mock市名称
     */
    private static class CityStrategy implements AddressStrategy {
        @Override
        public String generate(AddressContext ctx) {
            List<String> cityNames;
            if (StringUtils.isNotEmpty(ctx.province)) {
                cityNames = AddressUtils.getCityNamesByProvince(ctx.province);
            } else {
                cityNames = AddressUtils.getCityNames();
            }
            return Mocker.randomChoose(cityNames);
        }
    }

    /**
     * mock区县名称
     */
    private static class AreaStrategy implements AddressStrategy {

        @Override
        public String generate(AddressContext ctx) {
            List<String> areaNames;
            if (StringUtils.isNotEmpty(ctx.province) && StringUtils.isNotEmpty(ctx.city)) {
                areaNames = AddressUtils.getAreaNamesByProvinceAndCity(ctx.province, ctx.city);
            } else if (StringUtils.isNotEmpty(ctx.province)) {
                areaNames = AddressUtils.getAreaNamesByProvince(ctx.province);
            } else {
                areaNames = AddressUtils.getAreaNames();
            }
            return Mocker.randomChoose(areaNames);
        }
    }

    /**
     * mock街道名称
     */
    private static class StreetStrategy implements AddressStrategy {
        @Override
        public String generate(AddressContext ctx) {
            List<String> streetNames;
            if (StringUtils.isNotEmpty(ctx.province) && StringUtils.isNotEmpty(ctx.city) && StringUtils.isNotEmpty(ctx.area)) {
                streetNames = AddressUtils.getStreetNamesByProvinceAndCityAndArea(ctx.province, ctx.city, ctx.area);
            } else if (StringUtils.isNotEmpty(ctx.province) && StringUtils.isNotEmpty(ctx.city)) {
                streetNames = AddressUtils.getStreetNamesByProvinceAndCity(ctx.province, ctx.city);
            } else if (StringUtils.isNotEmpty(ctx.province)) {
                streetNames = AddressUtils.getStreetNamesByProvince(ctx.province);
            } else {
                streetNames = AddressUtils.getStreetNames();
            }
            return Mocker.randomChoose(streetNames);
        }
    }
}
