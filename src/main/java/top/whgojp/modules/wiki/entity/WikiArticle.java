package top.whgojp.modules.wiki.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("wiki_article")
public class WikiArticle {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String title;
    private String body;
    private String templateName;
    private String createdBy;
}
