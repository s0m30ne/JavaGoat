package top.whgojp.modules.file.controller;

import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import top.whgojp.common.constant.SysConstant;
import top.whgojp.common.utils.R;
import top.whgojp.common.utils.UploadUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

@Controller
@RequestMapping("/files")
public class DeskFileController {

    @Autowired
    private UploadUtil uploadUtil;
    @Autowired
    private SysConstant sysConstant;

    @GetMapping("")
    public String page() {
        return "desk/files";
    }

    @RequestMapping("/upload")
    @ResponseBody
    public R upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws Exception {
        String suffix = FilenameUtils.getExtension(file.getOriginalFilename());
        String path = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + "/file/";
        String fileUrl = uploadUtil.uploadFileAndReturnUrl(file, suffix, path);
        return R.ok("上传成功").put("url", fileUrl);
    }

    @GetMapping("/read")
    @ResponseBody
    public String read(@RequestParam String fileName) throws Exception {
        return new String(Files.readAllBytes(Paths.get(fileName)));
    }

    @GetMapping("/download")
    public void download(@RequestParam String fileName, HttpServletResponse response) throws Exception {
        File file = new File(fileName);
        response.setHeader("Content-Disposition", "attachment; filename=" + file.getName());
        try (FileInputStream in = new FileInputStream(file); OutputStream out = response.getOutputStream()) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
    }

    @GetMapping("/delete")
    @ResponseBody
    public R delete(@RequestParam String fileName) {
        File file = new File(fileName);
        return file.delete() ? R.ok("已删除") : R.error("删除失败");
    }
}
