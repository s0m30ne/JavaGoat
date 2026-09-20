package top.whgojp;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * WAR 部署入口。仅在 Docker 构建外置 Tomcat 镜像时拷入编译，不进入业务源码树。
 * 外置容器不会执行 {@link Application#main(String[])}，因此这里补上反序列化实验所需的 JVM 开关。
 */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        System.setProperty("org.apache.commons.collections.enableUnsafeSerialization", "true");
        return builder.sources(Application.class);
    }
}
