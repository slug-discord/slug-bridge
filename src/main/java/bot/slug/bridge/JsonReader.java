package bot.slug.bridge;

import java.util.LinkedHashMap;
import java.util.Map;

final class JsonReader {
    private JsonReader() {}

    static Map<String, String> readFlat(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null) return out;
        int i = 0;
        int n = json.length();
        while (i < n && json.charAt(i) != '{') i++;
        i++;
        while (i < n) {
            while (i < n && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= n || json.charAt(i) == '}') break;
            if (json.charAt(i) != '"') return out;
            StringBuilder key = new StringBuilder();
            i++;
            while (i < n && json.charAt(i) != '"') {
                if (json.charAt(i) == '\\' && i + 1 < n) {
                    key.append(unescape(json.charAt(++i)));
                } else {
                    key.append(json.charAt(i));
                }
                i++;
            }
            i++;
            while (i < n && json.charAt(i) != ':') i++;
            i++;
            while (i < n && Character.isWhitespace(json.charAt(i))) i++;
            String value;
            if (i < n && json.charAt(i) == '"') {
                StringBuilder v = new StringBuilder();
                i++;
                while (i < n && json.charAt(i) != '"') {
                    if (json.charAt(i) == '\\' && i + 1 < n) {
                        v.append(unescape(json.charAt(++i)));
                    } else {
                        v.append(json.charAt(i));
                    }
                    i++;
                }
                i++;
                value = v.toString();
            } else {
                StringBuilder v = new StringBuilder();
                while (i < n && json.charAt(i) != ',' && json.charAt(i) != '}') {
                    v.append(json.charAt(i));
                    i++;
                }
                value = v.toString().trim();
            }
            out.put(key.toString(), value);
            while (i < n && Character.isWhitespace(json.charAt(i))) i++;
            if (i < n && json.charAt(i) == ',') i++;
        }
        return out;
    }

    private static char unescape(char c) {
        switch (c) {
            case 'n': return '\n';
            case 'r': return '\r';
            case 't': return '\t';
            default:  return c;
        }
    }
}
