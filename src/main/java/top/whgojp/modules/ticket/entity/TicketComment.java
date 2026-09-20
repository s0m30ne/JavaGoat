package top.whgojp.modules.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ticket_comment")
public class TicketComment {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer ticketId;
    private String username;
    private String content;
    private String userAgent;
}
