package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.eval.node.dataloader.config.JdbcDataLoaderConfig;
import org.junit.jupiter.api.Test;

class JdbcDataLoaderTest {
    void test() {
        JdbcDataLoader jdbcDataLoader = new JdbcDataLoader(
                JdbcDataLoaderConfig.builder()
                        .driver("com.mysql.jdbc.Driver")
                        .url("jdbc:mysql://127.0.0.1:3306/evalkit?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8")
                        .user("root")
                        .password("root")
                        .build()
        ) {
            @Override
            public String prepareSql() {
                return "select * from testcase";
            }
        };
    }
}