package top.whgojp.security.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
import java.util.Locale;

/**
 * 内网网关信任接入：根据反向代理头判断请求是否已在网关完成认证。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 21)
public class TrustedHeaderAuthBypassFilter extends OncePerRequestFilter {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null && isTrustedGateway(request)) {
            SecurityContextHolder.getContext().setAuthentication(buildGatewayAuthentication(request));
        }
        filterChain.doFilter(request, response);
    }

    private boolean isTrustedGateway(HttpServletRequest request) {
        if (isTrustedIp(firstForwardedIp(request.getHeader("X-Forwarded-For")))) {
            return true;
        }
        if (isTrustedIp(request.getHeader("X-Real-IP"))
                || isTrustedIp(request.getHeader("True-Client-IP"))
                || isTrustedIp(request.getHeader("X-Client-IP"))
                || isTrustedIp(request.getHeader("X-Custom-IP-Authorization"))) {
            return true;
        }
        String internal = request.getHeader("X-Internal-Request");
        if (internal != null) {
            String value = internal.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(value) || "yes".equals(value) || "1".equals(value)) {
                return true;
            }
        }
        String gatewaySource = request.getHeader("X-Gateway-Source");
        if (gatewaySource != null) {
            String value = gatewaySource.trim().toLowerCase(Locale.ROOT);
            if ("intranet".equals(value) || "internal".equals(value)) {
                return true;
            }
        }
        return hasText(request.getHeader("X-Original-URL")) || hasText(request.getHeader("X-Rewrite-URL"));
    }

    private String firstForwardedIp(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.trim().isEmpty()) {
            return null;
        }
        int comma = forwardedFor.indexOf(',');
        return comma >= 0 ? forwardedFor.substring(0, comma).trim() : forwardedFor.trim();
    }

    private boolean isTrustedIp(String ip) {
        if (ip == null) {
            return false;
        }
        String value = ip.trim();
        return "127.0.0.1".equals(value)
                || "::1".equals(value)
                || "localhost".equalsIgnoreCase(value)
                || "10.0.0.1".equals(value)
                || value.startsWith("127.");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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
