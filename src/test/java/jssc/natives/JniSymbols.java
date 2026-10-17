package jssc.natives;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Computes the JNI symbol names the JVM looks up for the {@code native} methods of a class.
 */
final class JniSymbols {

    static final String PREFIX = "Java_";

    private JniSymbols() {
    }

    /**
     * Inspects the class without initializing it: initializing {@code SerialNativeInterface}
     * would try to load the native library, which is impossible for foreign architectures.
     */
    static Set<String> expectedSymbols(String className) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(className, false, JniSymbols.class.getClassLoader());
        Map<String, Integer> overloads = new HashMap<String, Integer>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (Modifier.isNative(m.getModifiers())) {
                Integer n = overloads.get(m.getName());
                overloads.put(m.getName(), n == null ? 1 : n + 1);
            }
        }
        Set<String> symbols = new TreeSet<String>();
        String classPart = mangle(clazz.getName().replace('.', '/'));
        for (Method m : clazz.getDeclaredMethods()) {
            if (!Modifier.isNative(m.getModifiers())) {
                continue;
            }
            String symbol = PREFIX + classPart + "_" + mangle(m.getName());
            if (overloads.get(m.getName()) > 1) {
                StringBuilder sig = new StringBuilder();
                for (Class<?> p : m.getParameterTypes()) {
                    sig.append(descriptor(p));
                }
                symbol += "__" + mangle(sig.toString());
            }
            symbols.add(symbol);
        }
        return symbols;
    }

    /** Escaping rules from the JNI specification, "Resolving Native Method Names" */
    static String mangle(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '/') out.append('_');
            else if (c == '_') out.append("_1");
            else if (c == ';') out.append("_2");
            else if (c == '[') out.append("_3");
            else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) out.append(c);
            else out.append(String.format("_0%04x", (int) c));
        }
        return out.toString();
    }

    private static String descriptor(Class<?> c) {
        if (c.isArray()) return c.getName().replace('.', '/');
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == char.class) return "C";
        if (c == short.class) return "S";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        return "L" + c.getName().replace('.', '/') + ";";
    }
}
