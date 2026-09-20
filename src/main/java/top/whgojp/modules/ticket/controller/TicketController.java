package top.whgojp.modules.ticket.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import top.whgojp.common.desk.DeskUsers;
import top.whgojp.common.desk.WeakInputGuard;
import top.whgojp.common.utils.R;
import top.whgojp.modules.ticket.entity.Ticket;
import top.whgojp.modules.ticket.entity.TicketComment;
import top.whgojp.modules.ticket.mapper.TicketCommentMapper;
import top.whgojp.modules.ticket.mapper.TicketMapper;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Controller
@RequestMapping("/tickets")
public class TicketController {
    private static final Logger log4j = LogManager.getLogger(TicketController.class);

    @Autowired
    private TicketMapper ticketMapper;
    @Autowired
    private TicketCommentMapper ticketCommentMapper;

    @GetMapping("")
    public String mine(Model model) {
        String username = DeskUsers.username();
        model.addAttribute("tickets", ticketMapper.searchMine(username));
        model.addAttribute("scope", "mine");
        return "desk/tickets";
    }

    @GetMapping("/all")
    public String all(Model model) {
        model.addAttribute("tickets", ticketMapper.selectList(null));
        model.addAttribute("scope", "all");
        return "desk/tickets";
    }

    @GetMapping("/new")
    public String createForm() {
        return "desk/ticket-new";
    }

    @PostMapping("/create")
    public String create(Ticket ticket, Model model) {
        String blocked = firstNonNull(WeakInputGuard.logExpr(ticket.getTitle()), WeakInputGuard.xss(ticket.getContent()));
        if (blocked != null) {
            model.addAttribute("error", blocked);
            return "desk/ticket-new";
        }
        ticket.setReporter(DeskUsers.username());
        if (ticket.getStatus() == null) {
            ticket.setStatus("open");
        }
        log4j.error(ticket.getTitle());
        ticketMapper.insert(ticket);
        return "redirect:/tickets/" + ticket.getId();
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(required = false) String orderBy,
                         Model model) {
        String blocked = firstNonNull(WeakInputGuard.sql(q), WeakInputGuard.sql(orderBy));
        if (blocked != null) {
            model.addAttribute("error", blocked);
            model.addAttribute("tickets", java.util.Collections.emptyList());
            model.addAttribute("q", q);
            model.addAttribute("orderBy", orderBy);
            model.addAttribute("scope", "search");
            return "desk/tickets";
        }
        List<Ticket> tickets = ticketMapper.searchVul(q, orderBy);
        model.addAttribute("tickets", tickets);
        model.addAttribute("q", q);
        model.addAttribute("orderBy", orderBy);
        model.addAttribute("scope", "search");
        return "desk/tickets";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        Ticket ticket = ticketMapper.selectById(id);
        List<TicketComment> comments = ticketCommentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TicketComment>().eq("ticket_id", id));
        model.addAttribute("ticket", ticket);
        model.addAttribute("comments", comments);
        return "desk/ticket-detail";
    }

    @PostMapping("/{id}/comments")
    public String comment(@PathVariable Integer id, @RequestParam String content, HttpServletRequest request, Model model) {
        String blocked = WeakInputGuard.xss(content);
        if (blocked != null) {
            Ticket ticket = ticketMapper.selectById(id);
            model.addAttribute("ticket", ticket);
            model.addAttribute("comments", ticketCommentMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TicketComment>().eq("ticket_id", id)));
            model.addAttribute("error", blocked);
            return "desk/ticket-detail";
        }
        TicketComment comment = new TicketComment();
        comment.setTicketId(id);
        comment.setUsername(DeskUsers.username());
        comment.setContent(content);
        comment.setUserAgent(request.getHeader("User-Agent"));
        ticketCommentMapper.insert(comment);
        return "redirect:/tickets/" + id;
    }

    private String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    @RequestMapping("/{id}/assign")
    @ResponseBody
    public R assign(@PathVariable Integer id, @RequestParam String assignee) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            return R.error("工单不存在");
        }
        ticket.setAssignee(assignee);
        ticket.setStatus("processing");
        ticketMapper.updateById(ticket);
        return R.ok("已转派给 " + assignee);
    }
}
