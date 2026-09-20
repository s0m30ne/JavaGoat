package top.whgojp.modules.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ticket")
public class Ticket {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String title;
    private String content;
    private String status;
    private String priority;
    private String category;
    private String reporter;
    private String assignee;
}
