package com.amcentral.tbd;

import org.junit.Test;

import static org.junit.Assert.*;

public class SomeJavaClassTest {

    @Test
    public void m1() {
        assertEquals( 4, SomeJavaClass.m1(4, null));
        assertEquals(11, SomeJavaClass.m1(4, "7 chars"));
    }

    @Test
    public void callingKotlin() {
        SomeJavaClass sjc = new SomeJavaClass();
        assertEquals( 37+5,  sjc.callingKotlin(37));
        assertEquals(-37+5, sjc.callingKotlin(-37));
    }
}