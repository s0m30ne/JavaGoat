package top.whgojp.common.desk;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

/**
 * 管理功能鉴权读取客户端提交的角色名（Header / Cookie / 参数），不查库、不读 Session。
 */
public final class ClientRoles {
    public static final String COOKIE = "role";
    public static final String HEADER = "X-Role";
    public static final String HEADER_ALT = "X-User-Role";

    private ClientRoles() {
    }

    public static String fromRequest(HttpServletRequest request) {
        String header = firstNonBlank(request.getHeader(HEADER), request.getHeader(HEADER_ALT));
        if (header != null) {
            return header.trim();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().trim().isEmpty()) {
                    return cookie.getValue().trim();
                }
            }
        }
        String param = request.getParameter(COOKIE);
        return param == null ? "" : param.trim();
    }

    public static boolean isAdmin(String role) {
        return "admin".equalsIgnoreCase(role);
    }

    public static boolean isAgent(String role) {
        return isAdmin(role) || "agent".equalsIgnoreCase(role);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a;
        }
        if (b != null && !b.trim().isEmpty()) {
            return b;
        }
        return null;
    }
}
