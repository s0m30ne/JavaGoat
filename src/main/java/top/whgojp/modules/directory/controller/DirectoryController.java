package top.whgojp.modules.directory.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/directory")
public class DirectoryController {

    @GetMapping("")
    public String query(@RequestParam(required = false) String name, Model model) {
        List<Map<String, String>> people = new ArrayList<Map<String, String>>();
        String error = null;
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ClassPathResource("data/directory.xml").getInputStream());
            XPath xpath = XPathFactory.newInstance().newXPath();
            String expression = "/directory/user[name='" + (name == null ? "" : name) + "']";
            if (name == null || name.isEmpty()) {
                expression = "/directory/user";
            }
            NodeList nodes = (NodeList) xpath.evaluate(expression, document, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                people.add(toRow(nodes.item(i)));
            }
        } catch (Exception e) {
            error = e.getMessage();
        }
        model.addAttribute("people", people);
        model.addAttribute("error", error);
        model.addAttribute("name", name);
        return "desk/directory";
    }

    private Map<String, String> toRow(Node node) {
        Map<String, String> row = new LinkedHashMap<String, String>();
        if (node instanceof Element && "user".equals(node.getNodeName())) {
            Element user = (Element) node;
            row.put("name", childText(user, "name"));
            row.put("title", childText(user, "title"));
            row.put("dept", childText(user, "dept"));
            row.put("secret", childText(user, "secret"));
            return row;
        }
        row.put("name", node.getNodeName());
        row.put("title", node.getTextContent() == null ? "" : node.getTextContent().trim());
        row.put("dept", "");
        row.put("secret", "");
        return row;
    }

    private String childText(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }
}
