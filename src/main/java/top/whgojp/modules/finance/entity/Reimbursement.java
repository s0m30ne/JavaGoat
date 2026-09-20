package top.whgojp.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("reimbursement")
public class Reimbursement {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String applicant;
    private BigDecimal amount;
    private String status;
    private String remark;
    private Integer paid;
}
