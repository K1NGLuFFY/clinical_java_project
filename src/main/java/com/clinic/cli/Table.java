package com.clinic.cli;

import java.util.ArrayList;
import java.util.List;

/** Small aligned text table used by the list and report commands. */
final class Table {

    private final String[] headers;
    private final boolean[] rightAligned;
    private final List<String[]> rows = new ArrayList<>();

    Table(String... headers) {
        this.headers = headers.clone();
        this.rightAligned = new boolean[headers.length];
    }

    /** Right-aligns the given zero-based columns (numbers read better that way). */
    Table rightAlign(int... columns) {
        for (int column : columns) {
            if (column >= 0 && column < rightAligned.length) {
                rightAligned[column] = true;
            }
        }
        return this;
    }

    void addRow(Object... cells) {
        String[] row = new String[headers.length];
        for (int i = 0; i < headers.length; i++) {
            Object cell = i < cells.length ? cells[i] : null;
            row[i] = cell == null ? "" : String.valueOf(cell);
        }
        rows.add(row);
    }

    void print() {
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }
        printRow(headers, widths);
        StringBuilder separator = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                separator.append("  ");
            }
            separator.append("-".repeat(widths[i]));
        }
        System.out.println(separator);
        for (String[] row : rows) {
            printRow(row, widths);
        }
    }

    private void printRow(String[] cells, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                line.append("  ");
            }
            String cell = cells[i];
            int padding = Math.max(0, widths[i] - cell.length());
            if (rightAligned[i]) {
                line.append(" ".repeat(padding)).append(cell);
            } else {
                line.append(cell).append(" ".repeat(padding));
            }
        }
        System.out.println(line);
    }
}
