package top.whgojp.modules.sso;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import top.whgojp.common.annotation.AuthIgnore;
import top.whgojp.common.desk.ClientRoles;
import top.whgojp.modules.system.entity.User;
import top.whgojp.modules.system.service.UserService;
import top.whgojp.security.detail.CustomUserDetailsService;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;

@Controller
@RequestMapping("/sso")
public class SsoController {

    @Autowired
    private UserService userService;
    @Autowired
    private CustomUserDetailsService customUserDetailsService;
    @Autowired
    private SsoCodeStore ssoCodeStore;

    @AuthIgnore
    @GetMapping("/authorize")
    public String authorize(@RequestParam(required = false) String redirect_uri,
                            @RequestParam(required = false) String state,
                            @RequestParam(required = false, defaultValue = "stackdesk") String client_id,
                            Model model) {
        model.addAttribute("redirectUri", redirect_uri == null ? "/sso/callback" : redirect_uri);
        model.addAttribute("state", state == null ? "" : state);
        model.addAttribute("clientId", client_id);
        return "desk/sso-authorize";
    }

    @AuthIgnore
    @PostMapping("/authorize")
    public void authorizeSubmit(@RequestParam String username,
                                @RequestParam String password,
                                @RequestParam String redirect_uri,
                                @RequestParam(required = false) String state,
                                HttpServletResponse response) throws IOException {
        User user = userService.userLogin(username, password);
        if (user == null) {
            response.sendRedirect("/sso/authorize?error=1&redirect_uri=" + urlEncode(redirect_uri)
                    + "&state=" + urlEncode(state));
            return;
        }
        if (!allowedRedirect(redirect_uri)) {
            response.sendRedirect("/sso/authorize?error=2&redirect_uri=" + urlEncode(redirect_uri)
                    + "&state=" + urlEncode(state));
            return;
        }
        String code = ssoCodeStore.issue(user.getUsername());
        response.sendRedirect(appendQuery(redirect_uri, code, state));
    }

    @AuthIgnore
    @GetMapping("/callback")
    public void callback(@RequestParam String code,
                         @RequestParam(required = false) String next,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        String username = ssoCodeStore.consume(code);
        if (username == null) {
            response.sendRedirect("/login?error=sso");
            return;
        }
        establishSession(request, response, username);
        if (next != null && !next.trim().isEmpty() && allowedRedirect(next)) {
            response.sendRedirect(next);
            return;
        }
        response.sendRedirect("/index");
    }

    /**
     * 回调地址须为本站相对路径或本机服务。startsWith("/") 会放过 //evil.com。
     */
    static boolean allowedRedirect(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return false;
        }
        String value = uri.trim();
        return value.startsWith("/")
                || value.startsWith("http://127.0.0.1")
                || value.startsWith("http://localhost");
    }

    private void establishSession(HttpServletRequest request, HttpServletResponse response, String username) {
        UserDetails details = customUserDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(details, details.getPassword(), details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        User user = userService.lambdaQuery().eq(User::getUsername, username).one();
        String role = (user != null && user.getRole() != null) ? user.getRole() : "employee";
        Cookie cookie = new Cookie(ClientRoles.COOKIE, role);
        cookie.setPath("/");
        response.addCookie(cookie);
    }

    private String appendQuery(String redirectUri, String code, String state) throws IOException {
        String sep = redirectUri.contains("?") ? "&" : "?";
        String url = redirectUri + sep + "code=" + urlEncode(code);
        if (state != null && !state.isEmpty()) {
            url += "&state=" + urlEncode(state);
        }
        return url;
    }

    private String urlEncode(String value) throws IOException {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, "UTF-8");
    }
}
