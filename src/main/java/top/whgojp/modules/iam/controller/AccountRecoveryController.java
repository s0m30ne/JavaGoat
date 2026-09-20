package top.whgojp.modules.iam.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import top.whgojp.common.annotation.AuthIgnore;
import top.whgojp.common.utils.JwtUtil;
import top.whgojp.common.utils.R;
import top.whgojp.modules.system.entity.User;
import top.whgojp.modules.system.service.UserService;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Controller
@RequestMapping("/account")
public class AccountRecoveryController {

    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;

    @AuthIgnore
    @GetMapping("/reset")
    public String resetPage() {
        return "desk/account-reset";
    }

    @AuthIgnore
    @PostMapping("/reset/step1")
    @ResponseBody
    public R step1(@RequestParam String username, HttpSession session) {
        session.setAttribute("resetUser", username);
        return R.ok("用户名已确认");
    }

    @AuthIgnore
    @PostMapping("/reset/step2")
    @ResponseBody
    public R step2(@RequestParam String oldPassword, HttpSession session) {
        session.setAttribute("resetChecked", true);
        return R.ok("身份校验完成");
    }

    @AuthIgnore
    @PostMapping("/reset/step3")
    @ResponseBody
    public R step3(@RequestParam String newPassword, HttpSession session) {
        String username = String.valueOf(session.getAttribute("resetUser"));
        userService.changePassword(username, newPassword);
        return R.ok("密码已更新");
    }

    @AuthIgnore
    @GetMapping("/sms")
    @ResponseBody
    public R sms(@RequestParam String phone, HttpSession session) {
        String code = String.format("%06d", new Random().nextInt(1000000));
        session.setAttribute("smsCode", code);
        return R.ok("验证码已发送").put("code", code);
    }

    @AuthIgnore
    @PostMapping("/sms/verify")
    @ResponseBody
    public R smsVerify(@RequestParam String code,
                       @RequestParam(required = false) String code_verify,
                       HttpSession session) {
        if ("true".equals(code_verify)) {
            return R.ok("验证通过");
        }
        Object saved = session.getAttribute("smsCode");
        if (saved != null && saved.toString().equals(code)) {
            return R.ok("验证通过");
        }
        return R.error("验证码不正确");
    }

    @AuthIgnore
    @GetMapping("/api/me")
    @ResponseBody
    public R me(@RequestParam(required = false) String token) {
        try {
            String username = jwtUtil.extractUsername(token);
            Map<String, String> data = new HashMap<String, String>();
            data.put("username", username);
            data.put("token", token);
            return R.ok().setData(data);
        } catch (Exception e) {
            return R.error("令牌无效");
        }
    }
}
