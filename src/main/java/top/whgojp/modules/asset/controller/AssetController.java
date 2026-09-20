package top.whgojp.modules.asset.controller;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import top.whgojp.common.desk.WeakInputGuard;
import top.whgojp.common.utils.R;
import top.whgojp.modules.asset.entity.AssetHost;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

@Controller
@RequestMapping("/assets")
public class AssetController {

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping("")
    public String list(@RequestParam(required = false) String owner,
                       @RequestParam(required = false, defaultValue = "id") String orderBy,
                       Model model) {
        String jpql = "SELECT a FROM AssetHost a WHERE a.owner = '" + (owner == null ? "" : owner) + "' ORDER BY a." + orderBy;
        if (owner == null || owner.isEmpty()) {
            jpql = "SELECT a FROM AssetHost a ORDER BY a." + orderBy;
        }
        List<AssetHost> assets = entityManager.createQuery(jpql, AssetHost.class).getResultList();
        model.addAttribute("assets", assets);
        model.addAttribute("owner", owner);
        model.addAttribute("orderBy", orderBy);
        return "desk/assets";
    }

    @GetMapping("/probe")
    public String probePage() {
        return "desk/asset-probe";
    }

    @PostMapping("/probe")
    @ResponseBody
    public R probe(@RequestParam String ip) {
        String blocked = WeakInputGuard.cmd(ip);
        if (blocked != null) {
            return R.error(blocked);
        }
        try {
            Process process = new ProcessBuilder("sh", "-c", "ping -c 1 " + ip).redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
            return R.ok(output.toString());
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @GetMapping("/import")
    public String importPage() {
        return "desk/asset-import";
    }

    @PostMapping("/import")
    @Transactional
    public String importXml(@RequestParam("file") MultipartFile file, Model model) {
        if (file == null || file.isEmpty()) {
            model.addAttribute("error", "请选择要导入的 XML 文件");
            return "desk/asset-import";
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!original.toLowerCase().endsWith(".xml")) {
            model.addAttribute("error", "仅支持 .xml 文件");
            return "desk/asset-import";
        }
        try {
            byte[] xmlBytes = file.getBytes();
            String xmlText = new String(xmlBytes, "UTF-8");
            String blocked = WeakInputGuard.xxe(xmlText);
            if (blocked != null) {
                model.addAttribute("error", blocked);
                return "desk/asset-import";
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document document = builder.parse(new java.io.ByteArrayInputStream(xmlBytes));
            document.getDocumentElement().normalize();
            int saved = 0;
            NodeList hosts = document.getElementsByTagName("host");
            for (int i = 0; i < hosts.getLength(); i++) {
                if (!(hosts.item(i) instanceof Element)) {
                    continue;
                }
                Element host = (Element) hosts.item(i);
                AssetHost asset = new AssetHost();
                asset.setHostname(textOf(host, "hostname", host.getTextContent()));
                asset.setIp(textOf(host, "ip", ""));
                asset.setOwner(textOf(host, "owner", ""));
                asset.setExtraXml(host.getTextContent());
                entityManager.persist(asset);
                saved++;
            }
            model.addAttribute("saved", saved);
            model.addAttribute("parsed", document.getDocumentElement().getTextContent());
        } catch (Exception e) {
            model.addAttribute("error", e.toString());
        }
        return "desk/asset-import";
    }

    private String textOf(Element parent, String tag, String fallback) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return fallback == null ? "" : fallback.trim();
        }
        return nodes.item(0).getTextContent().trim();
    }
}
