package com.sssxzxz.productkeyboard;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Reads only cell values from an XLSX file. Never executes formulas or external links. */
final class ModelWorkbook {
    static final class Column {
        final String label;
        final List<String> values;
        Column(String label, List<String> values) { this.label = label; this.values = values; }
    }
    static List<Column> read(InputStream input) throws Exception {
        Map<String,byte[]> entries = new LinkedHashMap<>();
        int total = 0, count = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > 2000) throw new IOException("表格文件过大");
                String name = entry.getName();
                boolean wanted = name.equals("xl/sharedStrings.xml") || name.equals("xl/styles.xml") || name.matches("xl/worksheets/sheet[0-9]+\\.xml");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                int size;
                while ((size = zip.read(buffer)) != -1) {
                    total += size;
                    if (total > 24 * 1024 * 1024) throw new IOException("请只保留型号列后再导入，表格解压后不能超过 24 MB");
                    if (wanted) bytes.write(buffer,0,size);
                }
                if (wanted) entries.put(name,bytes.toByteArray());
            }
        }
        List<String> strings = new ArrayList<>();
        if (entries.containsKey("xl/sharedStrings.xml")) {
            NodeList items = xml(entries.get("xl/sharedStrings.xml")).getElementsByTagName("si");
            for (int i=0;i<items.getLength();i++) strings.add(text((Element)items.item(i)));
        }
        List<Integer> zeroWidths = new ArrayList<>();
        if (entries.containsKey("xl/styles.xml")) {
            Document styles = xml(entries.get("xl/styles.xml"));
            Map<String,Integer> formats = new HashMap<>();
            NodeList formatsXml = styles.getElementsByTagName("numFmt");
            for (int i=0;i<formatsXml.getLength();i++) {
                Element format = (Element)formatsXml.item(i);
                String code = format.getAttribute("formatCode");
                if (code.matches("0{1,64}")) formats.put(format.getAttribute("numFmtId"),code.length());
            }
            NodeList groups = styles.getElementsByTagName("cellXfs");
            if (groups.getLength()>0) {
                NodeList items = ((Element)groups.item(0)).getElementsByTagName("xf");
                for(int i=0;i<items.getLength();i++) zeroWidths.add(formats.getOrDefault(((Element)items.item(i)).getAttribute("numFmtId"),0));
            }
        }
        List<Column> columns = new ArrayList<>();
        for (Map.Entry<String,byte[]> sheet : entries.entrySet()) {
            if (!sheet.getKey().startsWith("xl/worksheets/")) continue;
            Map<String,List<String>> values = new TreeMap<>();
            NodeList cells = xml(sheet.getValue()).getElementsByTagName("c");
            for(int i=0;i<cells.getLength();i++) {
                Element cell = (Element)cells.item(i);
                String column = cell.getAttribute("r").replaceAll("[0-9]", "");
                if (column.isEmpty()) continue;
                String type = cell.getAttribute("t"), value = "";
                NodeList v = cell.getElementsByTagName("v");
                if ("inlineStr".equals(type)) value = text(cell);
                else if (v.getLength()>0) {
                    value = v.item(0).getTextContent();
                    if ("s".equals(type)) value = strings.get(Integer.parseInt(value));
                    else if (type.isEmpty() || "n".equals(type)) {
                        try {
                            value = new BigDecimal(value).stripTrailingZeros().toPlainString();
                            String style = cell.getAttribute("s");
                            int index = style.isEmpty() ? 0 : Integer.parseInt(style);
                            int width = index < zeroWidths.size() ? zeroWidths.get(index) : 0;
                            if (value.matches("[0-9]+")) while (value.length()<width) value = "0"+value;
                        } catch (NumberFormatException ignored) { }
                    } else if ("e".equals(type) || "b".equals(type)) continue;
                }
                value = value.trim();
                if (!value.isEmpty()) values.computeIfAbsent(column,k -> new ArrayList<>()).add(value);
            }
            for (Map.Entry<String,List<String>> column : values.entrySet()) {
                List<String> items = column.getValue();
                String sample = String.join("、",items.subList(0,Math.min(4,items.size())));
                if (sample.length()>90) sample=sample.substring(0,90)+"…";
                columns.add(new Column(sheet.getKey().replace("xl/worksheets/", "")+" · "+column.getKey()+" 列\n"+sample, items));
            }
        }
        if (columns.isEmpty()) throw new IOException("未找到可读取的单元格，请选择未加密的 .xlsx 表格");
        return columns;
    }
    static List<String> models(List<String> values, boolean skipHeader) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (int i=skipHeader ? 1 : 0;i<values.size();i++) {
            String value=values.get(i).trim();
            // Input matching uses whitespace-delimited tokens. Reject unsupported values visibly in the preview.
            if (value.isEmpty() || value.length()>128 || value.matches("(?s).*\\s.*")) continue;
            result.add(value);
            if (result.size()>20000) throw new IllegalArgumentException("型号数量不能超过 20000 个");
        }
        return new ArrayList<>(result);
    }
    private static Document xml(byte[] bytes) throws Exception {
        String source = new String(bytes,StandardCharsets.UTF_8);
        if (source.contains("<!DOCTYPE") || source.indexOf('\0') >= 0) throw new IOException("不支持此表格的 XML 编码或外部文档定义");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId,systemId) -> { throw new org.xml.sax.SAXException("External entities are disabled"); });
        return builder.parse(new ByteArrayInputStream(bytes));
    }
    private static String text(Element item) {
        StringBuilder result = new StringBuilder();
        NodeList texts = item.getElementsByTagName("t");
        for(int i=0;i<texts.getLength();i++) result.append(texts.item(i).getTextContent());
        return result.toString();
    }
}
