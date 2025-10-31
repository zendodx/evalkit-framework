package com.evalkit.framework.eval.node.reporter;

class JdbcReportTest {
    void test() {
        String driver = "com.mysql.cj.jdbc.Driver";
        String url = "jdbc:mysql://127.0.0.1:3306/evalkit?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";
        String username = "root";
        String password = "123456";
        JdbcReport jdbcReport = new JdbcReport(driver, url, username, password) {
            @Override
            public String prepareTableName() {
                return "";
            }
        };
    }
}