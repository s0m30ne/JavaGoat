package top.whgojp.common.desk;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import top.whgojp.modules.system.entity.User;
import top.whgojp.modules.system.service.UserService;

public final class DeskUsers {
    private DeskUsers() {
    }

    public static String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    public static User current(UserService userService) {
        String username = username();
        if (username == null || userService == null) {
            return null;
        }
        return userService.lambdaQuery().eq(User::getUsername, username).one();
    }

    public static boolean isAdmin(User user) {
        return user != null && "admin".equalsIgnoreCase(user.getRole());
    }

    public static boolean isAgent(User user) {
        return user != null && ("agent".equalsIgnoreCase(user.getRole()) || "admin".equalsIgnoreCase(user.getRole()));
    }
}
