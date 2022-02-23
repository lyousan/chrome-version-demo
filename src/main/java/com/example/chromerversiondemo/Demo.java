package com.example.chromerversiondemo;

import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @Author 有三
 * @Date 2022-02-23 13:54
 * @Description
 **/
public class Demo {
    /**
     * 用于在异常信息中匹配当前chrome浏览器版本的正则规则
     */
    private static final String PATTERN_STRING;
    private static Pattern pattern;
    /**
     * 存放资源的目录
     */
    private static File resourceDir;
    /**
     * 存在驱动的目录
     */
    private static File driverDir;
    /**
     * 记录本地上一次运行时使用的chromedriver版本
     */
    private static File recordVersionTxt;
    /**
     * 当前支持的chromedriver版本，从{@link #resourceDir}资源目录下读取
     * {
     * 92.0.4515.43,
     * 93.0.4577.63,
     * 94.0.4606.61,
     * ......
     * }
     */
    private static List<String> driverList;

    static {
        PATTERN_STRING = "Current browser version is (.*?) with binary path";
        pattern = Pattern.compile(PATTERN_STRING);
        resourceDir = new File("resources");
        driverDir = new File(resourceDir, "driver");
        recordVersionTxt = new File(resourceDir, "chrome_version.txt");
        // 从驱动目录下读取
        driverList = Arrays.stream(driverDir.listFiles())
                .map(file -> file.getName().replace(".exe", ""))
                .sorted()
                .collect(Collectors.toList());
        // 细节倒序，从最新版本开始遍历，通常情况下可以减少 selectVersion() 中对driver的创建
        Collections.reverse(driverList);
    }

    public static void main(String[] args) {
        WebDriver driver = createDriver();
        driver.get("http://www.baidu.com");
        System.out.println("成功正常打开浏览器");
        driver.quit();
        System.exit(0);
    }

    /**
     * 创建driver
     *
     * @return
     */
    public static WebDriver createDriver() {
        String driverVersion = selectVersion();
        if (driverVersion == null) {
            throw new RuntimeException("未匹配到相应的浏览器驱动程序，请联系管理人员");
        }
        WebDriver driver = doCreateDriver(driverDir.getAbsolutePath() + File.separator + driverVersion + ".exe");
        // ......
        return driver;
    }

    /**
     * 选择版本
     *
     * @return 版本号
     */
    private static String selectVersion() {
        WebDriver driver = null;
        // 先从txt文件中读取本地上一次运行时使用的版本，第一次运行时为null
        String lastRecordVersion;
        String retChromeVersion = lastRecordVersion = readFromTxt(recordVersionTxt);
        for (String driverVersion : driverList) {
            // 如果有记录上一次的版本并且该版本是被程序支持的，则将循环跳到上一次记录的版本开始执行
            if (lastRecordVersion != null && supportVersion(lastRecordVersion)
                    && !driverVersion.equals(lastRecordVersion)) {
                // 这里的处理是为了减少创建driver的次数
                continue;
            }
            try {
                // 创建driver，如果没有报错，正常创建了driver，就将当前的版本写入txt文件并跳出循环
                driver = doCreateDriver(driverDir.getAbsolutePath() + File.separator + driverVersion + ".exe");
                writeToTxt(recordVersionTxt, driverVersion);
                retChromeVersion = driverVersion;
                break;
            } catch (SessionNotCreatedException e) {
                // 没有正常创建driver，对异常信息进行正则匹配，得到当前浏览器的版本
                Matcher matcher = pattern.matcher(e.getMessage());
                if (matcher.find()) {
                    retChromeVersion = matcher.group(1);
                    System.out.println("当前浏览器版本为：" + retChromeVersion);
                    String versionPrefix = retChromeVersion.substring(0, 2);
                    // versionPrefix -> 92
                    // 判断是否支持当前的浏览器版本，不支持的话就直接抛出异常，终止运行
                    if (supportVersion(versionPrefix)) {
                        // 查询本地驱动中支持当前浏览器的完整版本号
                        retChromeVersion = lastRecordVersion = findSupportVersion(versionPrefix);
                        // retChromeVersion -> 92.0.4515.43
                        writeToTxt(recordVersionTxt, retChromeVersion);
                        break;
                    } else {
                        throw new RuntimeException("未匹配到相应的浏览器驱动程序，请联系管理人员");
                    }
                }
                continue;
            } finally {
                if (driver != null) {
                    driver.quit();
                }
            }
        }
        return retChromeVersion;
    }

    /**
     * 创建WebDriver对象
     *
     * @param driverPath 驱动文件所在的路径
     * @return
     */
    private static WebDriver doCreateDriver(String driverPath) {
        System.setProperty("webdriver.chrome.driver", driverPath);
        // ......
        WebDriver driver = new ChromeDriver();
        // ......
        return driver;
    }

    /**
     * 是否支持指定版本
     *
     * @param versionPrefix 版本前缀（大版本号）
     *                      92.0.4515.43 -> 92
     * @return
     */
    private static boolean supportVersion(String versionPrefix) {
        return driverList.stream().anyMatch(item -> item.startsWith(versionPrefix));
    }

    /**
     * 根据大版本查找完整版本号
     *
     * @param versionPrefix 版本前缀（大版本号）
     *                      92.0.4515.43 -> 92
     * @return 完整版本号
     */
    private static String findSupportVersion(String versionPrefix) {
        return driverList.stream().filter(item -> item.startsWith(versionPrefix)).findFirst().get();
    }

    /**
     * 读取
     *
     * @param txtFile
     * @return
     */
    private static String readFromTxt(File txtFile) {
        if (!txtFile.exists()) {
            return null;
        }
        String content = null;
        try (
                BufferedReader reader = new BufferedReader(new FileReader(txtFile));
        ) {
            content = reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content;
    }

    /**
     * 写入
     *
     * @param txtFile
     * @param content
     */
    private static void writeToTxt(File txtFile, String content) {
        try (
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(txtFile), "utf-8"));
        ) {
            if (!txtFile.exists()) {
                txtFile.createNewFile();
            }
            writer.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
