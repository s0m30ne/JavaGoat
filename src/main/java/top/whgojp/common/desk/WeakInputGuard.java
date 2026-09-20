package top.whgojp.common.desk;

/**
 * 业务侧输入检查。关键字匹配区分大小写，黑名单不覆盖常用绕过写法。
 */
public final class WeakInputGuard {

    private WeakInputGuard() {
    }

    public static String sql(String input) {
        if (isBlank(input)) {
            return null;
        }
        if (input.length() > 256) {
            return "查询条件过长";
        }
        if (contains(input, "union select", "sleep(", "benchmark(", "information_schema", "load_file(")) {
            return "查询包含非法关键字";
        }
        return null;
    }

    public static String xss(String input) {
        if (isBlank(input)) {
            return null;
        }
        if (input.length() > 20000) {
            return "内容过长";
        }
        if (contains(input, "<script", "</script>", "javascript:")) {
            return "内容包含非法脚本标记";
        }
        return null;
    }

    public static String cmd(String input) {
        if (isBlank(input)) {
            return null;
        }
        if (input.length() > 96) {
            return "目标地址过长";
        }
        if (contains(input, ";", "&&", "..")) {
            return "目标包含非法字符";
        }
        if (contains(input, "cat ", "whoami", "bash -i")) {
            return "目标包含非法命令";
        }
        return null;
    }

    public static String groovy(String input) {
        if (isBlank(input)) {
            return null;
        }
        if (input.length() > 4000) {
            return "脚本过长";
        }
        if (contains(input, "Runtime", "ProcessBuilder", "getRuntime")) {
            return "脚本包含危险调用";
        }
        return null;
    }

    public static String spel(String input) {
        if (isBlank(input)) {
            return null;
        }
        if (input.length() > 400) {
            return "表达式过长";
        }
        if (contains(input, "java.lang.Runtime", "getRuntime", "javax.script")) {
            return "表达式包含危险类型";
        }
        return null;
    }

    public static String viewName(String input) {
        if (isBlank(input)) {
            return null;
        }
        if (input.length() > 128) {
            return "模板名过长";
        }
        if (contains(input, "..", "WEB-INF", "\\")) {
            return "模板名非法";
        }
        return null;
    }

    public static String xxe(String input) {
        if (isBlank(input)) {
            return null;
        }
        if (contains(input, "php://", "expect://", "file:///etc/passwd")) {
            return "XML 包含非法外部实体";
        }
        return null;
    }

    public static String logExpr(String input) {
        if (isBlank(input)) {
            return null;
        }
        if (contains(input, "${jndi:ldap")) {
            return "标题包含非法占位符";
        }
        return null;
    }

    private static boolean contains(String input, String... needles) {
        for (String needle : needles) {
            if (input.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String input) {
        return input == null || input.isEmpty();
    }
}
