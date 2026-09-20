package top.whgojp.modules.finance.controller;

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
import top.whgojp.common.utils.R;
import top.whgojp.modules.finance.entity.Reimbursement;
import top.whgojp.modules.finance.mapper.ReimbursementMapper;

import java.math.BigDecimal;

@Controller
@RequestMapping("/reimbursements")
public class FinanceController {

    @Autowired
    private ReimbursementMapper reimbursementMapper;

    @GetMapping("")
    public String mine(Model model) {
        model.addAttribute("items", reimbursementMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Reimbursement>()
                        .eq("applicant", DeskUsers.username())));
        return "desk/reimbursements";
    }

    @GetMapping("/inbox")
    public String inbox(Model model) {
        model.addAttribute("items", reimbursementMapper.selectList(null));
        return "desk/reimbursements";
    }

    @PostMapping("/create")
    public String create(@RequestParam BigDecimal amount, @RequestParam(required = false) String remark) {
        Reimbursement item = new Reimbursement();
        item.setApplicant(DeskUsers.username());
        item.setAmount(amount);
        item.setRemark(remark);
        item.setStatus("submitted");
        item.setPaid(0);
        reimbursementMapper.insert(item);
        return "redirect:/reimbursements";
    }

    @RequestMapping("/{id}/pay")
    @ResponseBody
    public R pay(@PathVariable Integer id) {
        Reimbursement item = reimbursementMapper.selectById(id);
        if (item == null) {
            return R.error("申请不存在");
        }
        item.setPaid(1);
        item.setStatus("paid");
        reimbursementMapper.updateById(item);
        return R.ok("支付成功，金额 " + item.getAmount());
    }
}
