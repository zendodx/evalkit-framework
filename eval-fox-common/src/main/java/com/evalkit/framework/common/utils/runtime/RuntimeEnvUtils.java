package com.evalkit.framework.common.utils.runtime;

import java.io.File;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class RuntimeEnvUtils {
    private RuntimeEnvUtils() {
    }

    /**
     * 是否是windows系统
     */
    public static boolean isWindows() {
        return osName().startsWith("windows");
    }

    /**
     * 是否是linux系统
     */
    public static boolean isLinux() {
        return osName().startsWith("linux");
    }

    /**
     * 是否是mac系统
     */
    public static boolean isMac() {
        return osName().startsWith("mac");
    }

    /**
     * 获取操作系统名称
     */
    public static String osName() {
        return System.getProperty("os.name").toLowerCase();
    }

    /**
     * 当前JDK版本
     */
    public static int jdkVersion() {
        String v = System.getProperty("java.version");
        if (v.startsWith("1.")) {
            return Integer.parseInt(v.substring(2, 3));
        }
        return Integer.parseInt(v.substring(0, v.indexOf('.')));
    }

    /**
     * 进程PID
     */
    public static long pid() {
        return Long.parseLong(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
    }

    /**
     * 主机hostname
     */
    public static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    /**
     * JVM 空闲内存（MB）
     */
    public static long freeMemoryMb() {
        return Runtime.getRuntime().freeMemory() / 1024 / 1024;
    }

    /**
     * JVM 最大可用内存（MB）
     */
    public static long maxMemoryMb() {
        return Runtime.getRuntime().maxMemory() / 1024 / 1024;
    }

    /**
     * CPU 核心数
     */
    public static int cpuCores() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * 操作系统环境变量
     */
    public static Map<String, String> env() {
        return System.getenv();
    }

    /**
     * 读取JVM系统属性,mvn的-D传参是系统属性
     */
    public static String getJVMProperty(String key) {
        return System.getProperty(key, null);
    }

    /**
     * 读取环境变量值
     */
    public static Long getJVMPropertyLong(String key, Long defaultValue) {
        String value = getJVMProperty(key);
        return value == null ? defaultValue : Long.valueOf(value);
    }

    public static Integer getJVMPropertyInt(String key, Integer defaultValue) {
        String value = getJVMProperty(key);
        return value == null ? defaultValue : Integer.valueOf(value);
    }

    public static Double getJVMPropertyDouble(String key, Double defaultValue) {
        String value = getJVMProperty(key);
        return value == null ? defaultValue : Double.valueOf(value);
    }

    public static Float getJVMPropertyFloat(String key, Float defaultValue) {
        String value = getJVMProperty(key);
        return value == null ? defaultValue : Float.valueOf(value);
    }

    public static String getJVMPropertyString(String key, String defaultValue) {
        String value = getJVMProperty(key);
        return value == null ? defaultValue : value;
    }

    /**
     * JVM 启动参数
     */
    public static List<String> jvmArgs() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments();
    }

    /**
     * 当前工作目录
     */
    public static String workDir() {
        return new File("").getAbsolutePath();
    }

    /**
     * 临时目录
     */
    public static String tmpDir() {
        return System.getProperty("java.io.tmpdir");
    }

    /**
     * OS 架构
     */
    public static String osArch() {
        return System.getProperty("os.arch");
    }

    /**
     * 获取spring profiles active名称
     */
    public static String springProfilesActive() {
        String active = System.getProperty("spring.profiles.active");
        if (active == null) {
            active = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        return active;
    }

    /**
     * 读取resource下的properties,获取执行的key
     */
    public static String getPropertyFromResource(String resourcePath, String key) {
        try (InputStream in = RuntimeEnvUtils.class.getClassLoader().getResourceAsStream(resourcePath)) {
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty(key);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取项目根目录
     */
    public static String getProjectRootDir() {
        return System.getProperty("user.dir");
    }
}
