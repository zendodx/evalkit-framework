package com.evalkit.framework.common.client.deepseek.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class UserBalanceResponse {
    @JsonProperty("is_available")
    private boolean isAvailable;        // 当前账户是否有余额可供 API 调用
    @JsonProperty("balance_infos")
    private List<BalanceInfo> balanceInfos;

    @Data
    static class BalanceInfo {
        private String currency;        // 货币，人民币或美元
        @JsonProperty("total_balance")
        private String totalBalance;    // 总的可用余额，包括赠金和充值余额
        @JsonProperty("granted_balance")
        private String grantedBalance;  // 未过期的赠金余额
        @JsonProperty("topped_up_balance")
        private String toppedUpBalance; // 充值余额
    }
}
