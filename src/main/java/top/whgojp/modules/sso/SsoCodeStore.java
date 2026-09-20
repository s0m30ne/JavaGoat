package top.whgojp.modules.sso;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SsoCodeStore {

    private static final long TTL_MS = 5 * 60 * 1000L;

    private final Map<String, Entry> codes = new ConcurrentHashMap<String, Entry>();

    public String issue(String username) {
        prune();
        String code = UUID.randomUUID().toString().replace("-", "");
        codes.put(code, new Entry(username, System.currentTimeMillis() + TTL_MS));
        return code;
    }

    public String consume(String code) {
        prune();
        if (code == null) {
            return null;
        }
        Entry entry = codes.remove(code);
        if (entry == null || entry.expireAt < System.currentTimeMillis()) {
            return null;
        }
        return entry.username;
    }

    private void prune() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Entry> e : codes.entrySet()) {
            if (e.getValue().expireAt < now) {
                codes.remove(e.getKey());
            }
        }
    }

    private static final class Entry {
        private final String username;
        private final long expireAt;

        private Entry(String username, long expireAt) {
            this.username = username;
            this.expireAt = expireAt;
        }
    }
}
