package com.evalkit.framework.common.utils.file;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件工具类
 */
public class FileUtils {
    private FileUtils() {
    }

    /**
     * 获取resource,绝对路径文件的Path
     */
    public static Path getFilePath(String fileName) {
        if (fileName.startsWith("classpath:")) {
            String realFilePath = fileName.replace("classpath:", "");
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL resource = classLoader.getResource(realFilePath);
            if (resource == null) {
                throw new RuntimeException("Cannot find file " + realFilePath);
            }
            return Paths.get(resource.getPath());
        }
        return Paths.get(fileName);
    }

    public static InputStream openClasspath(String fileName) {
        if (!fileName.startsWith("classpath:")) {
            throw new IllegalArgumentException("only support classpath: prefix");
        }
        String real = fileName.substring("classpath:".length());
        InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(real);
        if (in == null) {
            throw new RuntimeException("classpath resource not found: " + real);
        }
        return in;
    }

    /**
     * 获取指定文件夹下指定名称的文件（不包含子文件夹）
     */
    public static File getFileByName(String dirPath, String fileName) {
        File dir = new File(dirPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().equals(fileName)) {
                        return file;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 获取指定文件夹及其所有子文件夹下指定名称的文件（精确匹配，返回所有同名文件）
     */
    public static List<File> getAllFilesByName(String dirPath, String fileName) {
        List<File> matchedFiles = new ArrayList<>();
        File dir = new File(dirPath);
        if (dir.exists() && dir.isDirectory()) {
            getAllFilesByNameRecursive(dir, fileName, matchedFiles);
        }
        return matchedFiles;
    }

    private static void getAllFilesByNameRecursive(File dir, String fileName, List<File> matchedFiles) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().equals(fileName)) {
                    matchedFiles.add(file);
                } else if (file.isDirectory()) {
                    getAllFilesByNameRecursive(file, fileName, matchedFiles);
                }
            }
        }
    }


    /**
     * 获取指定文件夹下所有文件（不包含子文件夹）
     */
    public static List<File> listFiles(String dirPath) {
        List<File> fileList = new ArrayList<>();
        File dir = new File(dirPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        fileList.add(file);
                    }
                }
            }
        }
        return fileList;
    }

    /**
     * 获取指定文件夹下所有文件（包含子文件夹中的文件）
     */
    public static List<File> listAllFiles(String dirPath) {
        List<File> fileList = new ArrayList<>();
        File dir = new File(dirPath);
        if (dir.exists() && dir.isDirectory()) {
            listAllFilesRecursive(dir, fileList);
        }
        return fileList;
    }

    private static void listAllFilesRecursive(File dir, List<File> fileList) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    fileList.add(file);
                } else if (file.isDirectory()) {
                    listAllFilesRecursive(file, fileList);
                }
            }
        }
    }

}
