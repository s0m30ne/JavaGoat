package top.whgojp.security.handler;

import cn.hutool.core.date.DateUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import top.whgojp.common.desk.ClientRoles;
import top.whgojp.common.event.LoginLogEvent;
import top.whgojp.common.utils.IPUtil;
import top.whgojp.common.utils.SpringContextUtil;
import top.whgojp.modules.system.entity.Log;
import top.whgojp.modules.system.entity.User;
import top.whgojp.modules.system.service.UserService;

import javax.servlet.http.Cookie;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;

@Data
@Slf4j
public class CustomSavedRequestAwareAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private UserService userService;

//    private EmailPush emailPush;
//
//    private SmsPush smsPush;
//
//    private WechatPush wechatPush;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws ServletException, IOException {
        writeRoleCookie(response, authentication.getName());
        String redirect = request.getParameter("redirect");
        if (redirect != null && !redirect.trim().isEmpty()) {
            getRedirectStrategy().sendRedirect(request, response, redirect);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
        String username = authentication.getName();
        String loginIp = request.getRemoteHost();
        String loginDate = DateUtil.now();

        log.info("用户名:{},于{} 成功登录系统 IP:{} session:{}", username, loginDate, loginIp,authentication.getDetails());

        // 登录日志记录
        final Log loginLog = new Log();
        loginLog.setOptionip(IPUtil.getIpAddr(request));
        loginLog.setOptionname("用户登录成功");
        loginLog.setOptionterminal(request.getHeader("User-Agent"));
        loginLog.setUsername(username);
        loginLog.setOptiontime(new Date());
        SpringContextUtil.publishEvent(new LoginLogEvent(loginLog));

        try {
            // 发邮件
//            this.emailPush.send();

        } catch (Exception ex) {
            log.error(ex.getMessage(),ex);
        }
    }

    private void writeRoleCookie(HttpServletResponse response, String username) {
        String role = "employee";
        if (userService != null && username != null) {
            User user = userService.lambdaQuery().eq(User::getUsername, username).one();
            if (user != null && user.getRole() != null && !user.getRole().trim().isEmpty()) {
                role = user.getRole().trim();
            }
        }
        Cookie cookie = new Cookie(ClientRoles.COOKIE, role);
        cookie.setPath("/");
        response.addCookie(cookie);
    }

}
