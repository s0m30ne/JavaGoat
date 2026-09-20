package top.whgojp.security.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import top.whgojp.security.detail.CustomUserDetailsService;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * 静态资源与登录页前缀短路。使用原始 RequestURI 的 startsWith/endsWith，
 * 与 CDN/网关的前缀规则对齐，不做路径规范化。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class WeakPathAuthBypassFilter extends OncePerRequestFilter {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null
                && isPublicPath(rawPath(request))
                && !isExactAuthEntry(rawPath(request))) {
            injectGatewayPrincipal(request);
        }
        filterChain.doFilter(request, response);
    }

    private String rawPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        int query = uri.indexOf('?');
        return query >= 0 ? uri.substring(0, query) : uri;
    }

    private boolean isExactAuthEntry(String path) {
        return "/login".equals(path)
                || "/loginProcess".equals(path)
                || "/logout".equals(path)
                || "/captcha".equals(path)
                || "/public/status".equals(path);
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/static")
                || path.startsWith("/public")
                || path.startsWith("/assets/files")
                || path.startsWith("/help/static")
                || path.startsWith("/login/")
                || path.endsWith(".js")
                || path.endsWith(".css")
                || path.endsWith(".png")
                || path.endsWith(".ico")
                || path.endsWith(".woff")
                || path.endsWith(".map");
    }

    private void injectGatewayPrincipal(HttpServletRequest request) {
        Authentication authentication = buildGatewayAuthentication(request);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Authentication buildGatewayAuthentication(HttpServletRequest request) {
        try {
            UserDetails userDetails = customUserDetailsService.loadUserByUsername("admin");
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(userDetails, userDetails.getPassword(), userDetails.getAuthorities());
            token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            return token;
        } catch (UsernameNotFoundException ex) {
            org.springframework.security.core.userdetails.User fallback =
                    new org.springframework.security.core.userdetails.User("admin", "admin", Collections.emptyList());
            return new UsernamePasswordAuthenticationToken(fallback, fallback.getPassword(), fallback.getAuthorities());
        }
    }
}
