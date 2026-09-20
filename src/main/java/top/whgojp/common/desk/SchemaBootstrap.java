package top.whgojp.common.desk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
@Component
public class SchemaBootstrap implements ApplicationRunner {

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            addColumnIfMissing(conn, stmt, "user", "id", "INT NOT NULL AUTO_INCREMENT UNIQUE FIRST");
            addColumnIfMissing(conn, stmt, "user", "role", "VARCHAR(32) DEFAULT 'employee'");
            addColumnIfMissing(conn, stmt, "user", "display_name", "VARCHAR(64) DEFAULT ''");
            addColumnIfMissing(conn, stmt, "user", "signature", "VARCHAR(512) DEFAULT ''");
            addColumnIfMissing(conn, stmt, "user", "avatar_url", "VARCHAR(512) DEFAULT ''");
            addColumnIfMissing(conn, stmt, "user", "email", "VARCHAR(128) DEFAULT ''");
            addColumnIfMissing(conn, stmt, "user", "department", "VARCHAR(64) DEFAULT ''");
            addColumnIfMissing(conn, stmt, "user", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
            stmt.executeUpdate("UPDATE user SET role='admin', display_name='系统管理员', department='信息技术部' WHERE username='admin'");
            stmt.executeUpdate("UPDATE user SET role='employee', display_name='测试员工', department='业务部' WHERE username='test'");
            stmt.executeUpdate("UPDATE user SET role='employee', display_name='编号用户', department='业务部' WHERE username='123'");
            stmt.executeUpdate("INSERT IGNORE INTO user (username, password, role, display_name, department, email) VALUES ('agent','agent','agent','IT处理人','信息技术部','agent@stackdesk.local')");

            stmt.execute("CREATE TABLE IF NOT EXISTS ticket (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "title VARCHAR(255) NOT NULL," +
                    "content TEXT," +
                    "status VARCHAR(32) DEFAULT 'open'," +
                    "priority VARCHAR(16) DEFAULT 'medium'," +
                    "category VARCHAR(64) DEFAULT 'general'," +
                    "reporter VARCHAR(64)," +
                    "assignee VARCHAR(64)," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE TABLE IF NOT EXISTS ticket_comment (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "ticket_id INT NOT NULL," +
                    "username VARCHAR(64)," +
                    "content TEXT," +
                    "user_agent VARCHAR(255)," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE TABLE IF NOT EXISTS wiki_article (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "title VARCHAR(255)," +
                    "body TEXT," +
                    "template_name VARCHAR(128) DEFAULT 'wiki'," +
                    "created_by VARCHAR(64)," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE TABLE IF NOT EXISTS asset_host (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "hostname VARCHAR(128)," +
                    "ip VARCHAR(64)," +
                    "owner VARCHAR(64)," +
                    "extra_xml TEXT" +
                    ")");
            stmt.execute("CREATE TABLE IF NOT EXISTS reimbursement (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "applicant VARCHAR(64)," +
                    "amount DECIMAL(12,2)," +
                    "status VARCHAR(32) DEFAULT 'submitted'," +
                    "remark VARCHAR(512)," +
                    "paid TINYINT DEFAULT 0" +
                    ")");
            stmt.execute("CREATE TABLE IF NOT EXISTS desk_message (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "username VARCHAR(64)," +
                    "content TEXT," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            seedIfEmpty(stmt, "ticket", "INSERT INTO ticket (title, content, status, priority, category, reporter, assignee) VALUES " +
                    "('邮箱无法登录','客户端提示认证失败，请协助排查','open','high','email','test','agent')," +
                    "('申请开通VPN','出差需要远程访问内网知识库','processing','medium','network','test','agent')," +
                    "('显示器闪烁','工位显示器间歇性闪烁','done','low','hardware','123','admin')");
            seedIfEmpty(stmt, "asset_host", "INSERT INTO asset_host (hostname, ip, owner, extra_xml) VALUES " +
                    "('mail-01','10.0.0.11','admin','<host>mail-01</host>')," +
                    "('vpn-gw','10.0.0.1','agent','<host>vpn-gw</host>')," +
                    "('pc-test','192.168.1.20','test','<host>pc-test</host>')");
            seedIfEmpty(stmt, "desk_message", "INSERT INTO desk_message (username, content) VALUES " +
                    "('agent','VPN 申请已收到，请补充出差时间和目的地。')," +
                    "('test','本周四到下周三，目的地杭州。')," +
                    "('admin','权限已开通，客户端配置见知识库。')");
            seedIfEmpty(stmt, "wiki_article", "INSERT INTO wiki_article (title, body, template_name, created_by) VALUES " +
                    "('VPN使用说明','连接前请确认已安装客户端。','wiki','admin')," +
                    "('邮箱故障排查','先检查密码策略与二次验证。','wiki','agent')");
            log.info("StackDesk schema ready");
        } catch (Exception e) {
            log.warn("Schema bootstrap failed: {}", e.getMessage());
        }
    }

    private void addColumnIfMissing(Connection conn, Statement stmt, String table, String column, String ddl) {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, table, column)) {
            if (!rs.next()) {
                stmt.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + ddl);
            }
        } catch (Exception e) {
            log.debug("skip alter {}.{}: {}", table, column, e.getMessage());
        }
    }

    private void seedIfEmpty(Statement stmt, String table, String insertSql) {
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.executeUpdate(insertSql);
            }
        } catch (Exception e) {
            log.debug("skip seed {}: {}", table, e.getMessage());
        }
    }
}
