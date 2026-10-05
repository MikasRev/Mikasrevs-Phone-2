package com.mikasrevs.newphone.integration.currency;

import java.nio.file.Path;

public final class PhoneBankScope {
    private PhoneBankScope() { }
    public static String local(Path directory) { return "local:" + directory.toAbsolutePath().normalize(); }
}
