package jwp.fuzztest;

// These are in a separate package so the branches are not culled
public class TestMethods {

    public static String simpleMethod(int foo, boolean bar) {
        if (foo == 2) return "two";
        if (foo >= 5 && foo <= 7 && bar) return "five to seven and bar";
        if (foo > 20 && !bar) return "over twenty and not bar";
        return "something else";
    }
}
