package com.endangered.species.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** Reads the species workbook into plain {@code Map<String,String>} rows keyed by column header. */
public final class SpeciesWorkbookReader {

    private SpeciesWorkbookReader() {
    }

    public static Map<String, List<Map<String, String>>> readAll(InputStream input) throws IOException {
        DataFormatter formatter = new DataFormatter();
        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
        try (Workbook workbook = WorkbookFactory.create(input)) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                result.put(sheet.getSheetName().trim().toLowerCase(Locale.ROOT), readSheet(sheet, formatter));
            }
        }
        return result;
    }

    public static List<Map<String, String>> readFirstSheet(InputStream input) throws IOException {
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }
            return readSheet(workbook.getSheetAt(0), formatter);
        }
    }

    private static List<Map<String, String>> readSheet(Sheet sheet, DataFormatter formatter) {
        List<Map<String, String>> rows = new ArrayList<>();
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) {
            return rows;
        }

        Map<Integer, String> columns = new HashMap<>();
        for (Cell cell : header) {
            String name = formatter.formatCellValue(cell).trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) {
                columns.put(cell.getColumnIndex(), name);
            }
        }

        for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<Integer, String> column : columns.entrySet()) {
                Cell cell = row.getCell(column.getKey());
                String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                if (!value.isEmpty()) {
                    values.put(column.getValue(), value);
                }
            }
            if (!values.isEmpty()) {
                rows.add(values);
            }
        }
        return rows;
    }
}
