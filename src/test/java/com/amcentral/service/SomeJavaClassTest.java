package com.amcentral.service;

import org.junit.jupiter.api.Test;

class SomeJavaClassTest {

    @Test
    void m1() {
        org.junit.jupiter.api.Assertions.assertEquals( 4, SomeJavaClass.m1(4, null));
        org.junit.jupiter.api.Assertions.assertEquals(11, SomeJavaClass.m1(4, "7 chars"));
    }

    @Test
    void callingKotlin() {
        SomeJavaClass sjc = new SomeJavaClass();
        org.junit.jupiter.api.Assertions.assertEquals( 37+5,  sjc.callingKotlin(37));
        org.junit.jupiter.api.Assertions.assertEquals(-37+5, sjc.callingKotlin(-37));
    }
}