package top.whgojp.modules.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("desk_message")
public class DeskMessage {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String username;
    private String content;
    private java.util.Date createdAt;
}
