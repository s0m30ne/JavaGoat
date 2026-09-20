package top.whgojp.modules.iam.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import top.whgojp.common.annotation.AuthIgnore;
import top.whgojp.common.utils.R;
import top.whgojp.modules.system.service.UserService;
import top.whgojp.modules.xss.entity.Xss;
import top.whgojp.modules.xss.service.XssService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内网网关回源接口。供静态加速与服务间调用使用。
 */
@Controller
public class InternalGatewayController {

    @Autowired
    private UserService userService;

    @Autowired(required = false)
    private XssService xssService;

    @AuthIgnore
    @GetMapping("/public/status")
    @ResponseBody
    public R publicStatus() {
        Map<String, String> status = new LinkedHashMap<String, String>();
        status.put("status", "ok");
        status.put("app", "StackDesk");
        return R.ok().setData(status);
    }

    @GetMapping("/internal/staff-directory")
    @ResponseBody
    public R staffDirectory() {
        return R.ok().setData(userService.list());
    }

    @GetMapping("/internal/ticket-export")
    @ResponseBody
    public R ticketExport() {
        if (xssService == null) {
            return R.ok().setData("[]");
        }
        List<Xss> comments = xssService.list();
        return R.ok().setData(comments);
    }

    @GetMapping("/internal/config-dump")
    @ResponseBody
    public R configDump() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> dump = new LinkedHashMap<String, Object>();
        dump.put("gateway", "internal-static-cache");
        dump.put("principal", authentication == null ? null : authentication.getName());
        dump.put("authenticated", authentication != null && authentication.isAuthenticated());
        dump.put("staffDirectory", "/internal/staff-directory");
        dump.put("ticketExport", "/internal/ticket-export");
        return R.ok().setData(dump);
    }
}
