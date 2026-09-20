package top.whgojp.modules.admin.controller;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.xstream.XStream;
import groovy.lang.GroovyShell;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.yaml.snakeyaml.Yaml;
import top.whgojp.common.desk.WeakInputGuard;
import top.whgojp.common.utils.R;
import top.whgojp.modules.system.service.UserService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.beans.XMLDecoder;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

@Controller
@RequestMapping("/admin")
public class AdminPortalController {
    private static final Logger log4j = LogManager.getLogger(AdminPortalController.class);

    @Autowired
    private UserService userService;

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userService.list());
        return "desk/admin-users";
    }

    @GetMapping("/integrations")
    public String integrations() {
        return "desk/admin-integrations";
    }

    @PostMapping("/integrations/webhook/test")
    @ResponseBody
    public R webhook(@RequestParam String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            return R.ok("status=" + connection.getResponseCode());
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @GetMapping("/config")
    public String config() {
        return "desk/admin-config";
    }

    @PostMapping("/config/json")
    @ResponseBody
    public R importJson(@RequestBody String body) {
        try {
            return R.ok(JSON.parseObject(body).toJSONString());
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @PostMapping("/config/jackson")
    @ResponseBody
    public R importJackson(@RequestBody String body) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enableDefaultTyping();
            return R.ok(String.valueOf(mapper.readValue(body, Object.class)));
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @PostMapping("/config/xml")
    @ResponseBody
    public R importXml(@RequestBody String body) {
        try {
            return R.ok(String.valueOf(new XStream().fromXML(body)));
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @PostMapping("/config/yaml")
    @ResponseBody
    public R importYaml(@RequestParam String payload) {
        try {
            return R.ok(String.valueOf(new Yaml().load(payload)));
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @PostMapping("/config/xml-decoder")
    @ResponseBody
    public R importXmlDecoder(@RequestParam String payload) {
        try {
            XMLDecoder decoder = new XMLDecoder(new ByteArrayInputStream(payload.getBytes("UTF-8")));
            Object result = decoder.readObject();
            decoder.close();
            return R.ok(String.valueOf(result));
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @PostMapping("/config/job")
    @ResponseBody
    public R importJob(@RequestParam String payload) {
        try {
            byte[] data = Base64.getDecoder().decode(payload);
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
            Object obj = ois.readObject();
            ois.close();
            return R.ok(String.valueOf(obj));
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @GetMapping("/notify-templates")
    public String notifyTemplates() {
        return "desk/notify-templates";
    }

    @GetMapping("/notify-templates/preview")
    public String previewNotifyTemplate(@RequestParam String name, Model model) {
        String blocked = WeakInputGuard.viewName(name);
        if (blocked != null) {
            model.addAttribute("error", blocked);
            return "desk/notify-templates";
        }
        return name;
    }

    @GetMapping("/reports")
    public String reports() {
        return "desk/admin-reports";
    }

    @GetMapping("/reports/filter")
    @ResponseBody
    public R reportFilter(@RequestParam String expr) {
        String blocked = WeakInputGuard.spel(expr);
        if (blocked != null) {
            return R.error(blocked);
        }
        try {
            Expression expression = new SpelExpressionParser().parseExpression(expr);
            Object value = expression.getValue(new StandardEvaluationContext());
            return R.ok(String.valueOf(value));
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @PostMapping("/notify-script")
    @ResponseBody
    public R notifyScript(@RequestParam String script) {
        String blocked = WeakInputGuard.groovy(script);
        if (blocked != null) {
            return R.error(blocked);
        }
        try {
            return R.ok(String.valueOf(new GroovyShell().evaluate(script)));
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @GetMapping("/sessions")
    public String sessions(HttpServletRequest request, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("principal", authentication == null ? "" : authentication.getName());
        model.addAttribute("sessionId", request.getSession().getId());
        model.addAttribute("remote", request.getRemoteAddr());
        return "desk/admin-sessions";
    }

    @GetMapping("/ops")
    public String ops(HttpServletRequest request, Model model) {
        String xff = request.getHeader("X-Forwarded-For");
        model.addAttribute("xff", xff);
        model.addAttribute("remote", request.getRemoteAddr());
        boolean trusted = xff != null && xff.contains("8.8.8.8");
        model.addAttribute("trusted", trusted);
        log4j.error(request.getHeader("User-Agent"));
        return "desk/admin-ops";
    }

    @GetMapping("/ip-policy")
    public String ipPolicy() {
        return "desk/admin-ops";
    }
}
