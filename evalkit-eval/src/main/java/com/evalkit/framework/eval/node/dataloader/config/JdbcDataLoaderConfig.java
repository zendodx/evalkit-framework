package com.evalkit.framework.eval.node.dataloader.config;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class JdbcDataLoaderConfig extends DataLoaderConfig {
    /* 数据库连接驱动 */
    private String driver;
    /* 数据库连接url */
    private String url;
    /* 数据库连接用户名 */
    private String user;
    /* 数据库连接密码 */
    private String password;
    /* 最大链接池大小, 默认10 */
    @Builder.Default
    private int maximumPoolSize = 10;
    /* 最小idle,默认2 */
    @Builder.Default
    private int minimumIdle = 2;
    /* 链接超时时间, 默认5000毫秒 */
    @Builder.Default
    private long connectionTimeout = 5000;
}
