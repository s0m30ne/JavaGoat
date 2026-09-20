package top.whgojp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Data;

@TableName(value = "user")
@Data
public class User implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String username;

    private String password;

    private String role;

    private String displayName;

    private String signature;

    private String avatarUrl;

    private String email;

    private String department;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
