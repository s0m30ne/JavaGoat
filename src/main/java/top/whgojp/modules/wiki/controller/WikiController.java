package top.whgojp.modules.wiki.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import top.whgojp.common.desk.DeskUsers;
import top.whgojp.common.desk.WeakInputGuard;
import top.whgojp.modules.wiki.entity.WikiArticle;
import top.whgojp.modules.wiki.mapper.WikiArticleMapper;

import java.util.List;

@Controller
@RequestMapping("/wiki")
public class WikiController {

    @Autowired
    private WikiArticleMapper wikiArticleMapper;

    @GetMapping("")
    public String list(@RequestParam(required = false) String keyword, Model model) {
        String blocked = WeakInputGuard.xss(keyword);
        if (blocked != null) {
            model.addAttribute("error", blocked);
            keyword = "";
        }
        List<WikiArticle> articles = wikiArticleMapper.selectList(null);
        model.addAttribute("articles", articles);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        return "desk/wiki";
    }

    @GetMapping("/new")
    public String createForm() {
        return "desk/wiki-new";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        model.addAttribute("article", wikiArticleMapper.selectById(id));
        return "desk/wiki-detail";
    }

    @PostMapping("/create")
    public String create(WikiArticle article, Model model) {
        String blocked = WeakInputGuard.xss(article.getBody());
        if (blocked != null) {
            model.addAttribute("error", blocked);
            return "desk/wiki-new";
        }
        article.setCreatedBy(DeskUsers.username());
        if (article.getTemplateName() == null) {
            article.setTemplateName("wiki");
        }
        wikiArticleMapper.insert(article);
        return "redirect:/wiki/" + article.getId();
    }
}
