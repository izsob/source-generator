package org.sourcegenerator;

public class Util {

    public static String firstUpper(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public static String firstLower(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

}
