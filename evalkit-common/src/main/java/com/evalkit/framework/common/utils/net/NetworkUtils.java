package com.evalkit.framework.common.utils.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

/**
 * 网络工具类
 */
public class NetworkUtils {
    private NetworkUtils() {
    }

    /**
     * 检查端口是否被占用或者无法绑定
     *
     * @param port 端口号
     * @return true表示端口被占用或者无法绑定，false表示端口可用
     */
    public static boolean isPortUsed(int port) {
        if (port < 0) return true;
        try (ServerSocketChannel ch = ServerSocketChannel.open()) {
            ch.bind(new InetSocketAddress(port));
            return false;
        } catch (IOException ignored) {
        }
        return true;
    }
}
