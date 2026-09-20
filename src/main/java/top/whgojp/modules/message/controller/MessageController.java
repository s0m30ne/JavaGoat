package top.whgojp.modules.message.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import top.whgojp.common.desk.DeskUsers;
import top.whgojp.common.desk.WeakInputGuard;
import top.whgojp.modules.message.entity.DeskMessage;
import top.whgojp.modules.message.mapper.DeskMessageMapper;

@Controller
@RequestMapping("/messages")
public class MessageController {

    @Autowired
    private DeskMessageMapper deskMessageMapper;

    @GetMapping("")
    public String list(Model model) {
        model.addAttribute("currentUser", DeskUsers.username());
        model.addAttribute("messages", deskMessageMapper.selectList(
                new QueryWrapper<DeskMessage>().orderByAsc("id")));
        return "desk/messages";
    }

    @PostMapping("/send")
    public String send(@RequestParam String content, Model model) {
        String blocked = WeakInputGuard.xss(content);
        if (blocked != null) {
            model.addAttribute("currentUser", DeskUsers.username());
            model.addAttribute("messages", deskMessageMapper.selectList(
                    new QueryWrapper<DeskMessage>().orderByAsc("id")));
            model.addAttribute("error", blocked);
            return "desk/messages";
        }
        DeskMessage message = new DeskMessage();
        message.setUsername(DeskUsers.username());
        message.setContent(content);
        deskMessageMapper.insert(message);
        return "redirect:/messages";
    }
}
