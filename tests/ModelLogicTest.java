package com.sssxzxz.productkeyboard;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Offline test: javac ModelMatcher.java ModelWorkbook.java tests/ModelLogicTest.java -d <temp>; java -cp <temp> com.sssxzxz.productkeyboard.ModelLogicTest */
public class ModelLogicTest {
    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static byte[] workbook(Map<String,String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String,String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        equal("", ModelMatcher.prefix(null)); equal("13",ModelMatcher.prefix("other\n13"));
        equal("",ModelMatcher.prefix("13 ")); equal("ZP-1",ModelMatcher.prefix("ZP-1"));
        equal("",ModelMatcher.prefix(String.join("",Collections.nCopies(256,"1"))));
        equal(2,ModelMatcher.suffixLength("01 other")); equal(-1,ModelMatcher.suffixLength(null));
        List<String> models=Arrays.asList("1301","1302","0130","413","c-1357","zp-1","ZP-2");
        equal(Arrays.asList("1301","1302","0130","413","c-1357"),ModelMatcher.match(models,"13"));
        equal(Arrays.asList("c-1357"),ModelMatcher.match(models,"-135"));
        equal(Collections.emptyList(),ModelMatcher.match(models,"31"));
        equal(Arrays.asList("zp-1","ZP-2"),ModelMatcher.match(models,"Zp"));
        equal(Collections.emptyList(),ModelMatcher.match(models,""));
        equal(Collections.emptyList(),ModelMatcher.match(models,"999"));
        Map<String,String> entries = new LinkedHashMap<>();
        entries.put("xl/sharedStrings.xml","<sst><si><t>型号</t></si><si><r><t>zp</t></r><r><t>-1</t></r></si></sst>");
        entries.put("xl/styles.xml","<styleSheet><numFmts><numFmt numFmtId='164' formatCode='00000'/></numFmts><cellXfs><xf numFmtId='0'/><xf numFmtId='164'/></cellXfs></styleSheet>");
        entries.put("xl/worksheets/sheet1.xml","<worksheet><sheetData><row r='1'><c r='A1' t='s'><v>0</v></c><c r='B1' t='inlineStr'><is><t>价格</t></is></c></row><row r='2'><c r='A2'><v>1301</v></c><c r='B2'><v>5000</v></c></row><row r='3'><c r='A3' t='s'><v>1</v></c></row><row r='4'><c r='A4' s='1'><v>13</v></c></row><row r='5'><c r='A5'><v>1.302E3</v></c></row><row r='6'><c r='A6' t='inlineStr'><is><t>1301</t></is></c></row></sheetData></worksheet>");
        List<ModelWorkbook.Column> columns = ModelWorkbook.read(new ByteArrayInputStream(workbook(entries)));
        equal(2,columns.size()); equal(Arrays.asList("1301","zp-1","00013","1302"),ModelWorkbook.models(columns.get(0).values,true));
        equal(Arrays.asList("1301","zp-1"),ModelWorkbook.models(Arrays.asList("1301","zp-1","a b","","1301"),false));
        entries.put("xl/worksheets/sheet1.xml","<!DOCTYPE worksheet [<!ENTITY x SYSTEM 'file:///private'>]><worksheet/>");
        try { ModelWorkbook.read(new ByteArrayInputStream(workbook(entries))); throw new AssertionError("DOCTYPE accepted"); } catch (IOException expected) { }
        System.out.println("PASS: matching, token boundaries, XLSX columns, rich strings, leading zeros, scientific notation, deduplication, unsafe XML rejection");
    }
}
