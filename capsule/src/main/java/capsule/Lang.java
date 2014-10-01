package capsule;

import java.util.*;

/**
 * Created by circlespainter on 01/10/14.
 */
public class Lang {
    public static Map<String, String> stringMap(String... ss) {
        final Map<String, String> m = new HashMap<>();
        for (int i = 0; i < ss.length / 2; i++)
            m.put(ss[i * 2], ss[i * 2 + 1]);
        return Collections.unmodifiableMap(m);
    }

    public static <X> Set<X> set(X... xx) {
        final Set<X> s = new HashSet<>();
        for(X x : xx) s.add(x);
        return Collections.unmodifiableSet(s);
    }
}
