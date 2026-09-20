package top.whgojp.security.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import top.whgojp.common.desk.ClientRoles;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 管理类入口按请求中的角色字段鉴权，不读取服务端用户角色。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class ClientRoleAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isAuthenticated() || !requiresStaffRole(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String role = ClientRoles.fromRequest(request);
        boolean allowed = requiresAdmin(request) ? ClientRoles.isAdmin(role) : ClientRoles.isAgent(role);
        if (!allowed) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("权限不足，当前功能需要处理人或管理员角色。");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() != null
                && !"anonymousUser".equals(String.valueOf(authentication.getPrincipal()));
    }

    private boolean requiresAdmin(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/admin");
    }

    private boolean requiresStaffRole(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/admin")) {
            return true;
        }
        if ("/tickets/all".equals(path) || path.startsWith("/tickets/search")) {
            return true;
        }
        if ("/reimbursements/inbox".equals(path)) {
            return true;
        }
        return "/assets/probe".equals(path) || "/assets/import".equals(path);
    }
}
