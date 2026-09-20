package top.whgojp.modules.iam.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import top.whgojp.common.desk.DeskUsers;
import top.whgojp.common.desk.WeakInputGuard;
import top.whgojp.modules.system.entity.User;
import top.whgojp.modules.system.service.UserService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    @Autowired
    private UserService userService;

    @GetMapping("/profile")
    public String profile(Model model) {
        model.addAttribute("user", DeskUsers.current(userService));
        return "desk/profile";
    }

    @PostMapping("/profile")
    public String saveProfile(@RequestParam(required = false) String displayName,
                              @RequestParam(required = false) String signature,
                              @RequestParam(required = false) String email,
                              @RequestParam(required = false) String department,
                              RedirectAttributes redirectAttributes) {
        String blocked = WeakInputGuard.xss(signature);
        if (blocked != null) {
            redirectAttributes.addFlashAttribute("avatarError", blocked);
            return "redirect:/settings/profile";
        }
        User user = DeskUsers.current(userService);
        if (user != null) {
            user.setDisplayName(displayName);
            user.setSignature(signature);
            user.setEmail(email);
            user.setDepartment(department);
            userService.updateById(user);
            redirectAttributes.addFlashAttribute("saved", true);
        }
        return "redirect:/settings/profile";
    }

    @PostMapping("/profile/avatar-from-url")
    public String avatarFromUrl(@RequestParam String url, RedirectAttributes redirectAttributes) {
        try {
            InputStream in = new URL(url).openStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            int total = 0;
            while ((n = in.read(buf)) != -1 && total < 4096) {
                out.write(buf, 0, n);
                total += n;
            }
            User user = DeskUsers.current(userService);
            if (user != null) {
                user.setAvatarUrl(url);
                userService.updateById(user);
            }
            redirectAttributes.addFlashAttribute("avatarOk", true);
            redirectAttributes.addFlashAttribute("avatarPreview", out.toString("UTF-8"));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("avatarError", e.getMessage());
        }
        return "redirect:/settings/profile";
    }

    @GetMapping("/password")
    public String password() {
        return "system/password";
    }
}
