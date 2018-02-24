package com.alderprogs.amcentral;

public class SomeJavaClass {
    static public int m1(int p1, String p2) {
        int ret = p1 + (p2 == null ? 0 : p2.length());
        System.out.printf("I am SomeJavaClass::m1(%d, %s), and my return value is %d\n", p1, p2, ret);
        return ret;
    }

    public int callingKotlin(int p) {
        int ret = MainKt.callMeFromJavaForHighFive(p);
        System.out.printf("I am SomeJavaClass::callingKotlin(%d), and my return value is %d\n", p, ret);
        return ret;
    }
}