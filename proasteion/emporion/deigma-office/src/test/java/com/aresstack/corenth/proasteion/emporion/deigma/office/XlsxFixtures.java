package com.aresstack.corenth.proasteion.emporion.deigma.office;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

/**
 * Builds small XLSX fixtures in memory with the Office library itself.
 */
final class XlsxFixtures {

    private XlsxFixtures() {
    }

    /**
     * Three sheets: "Zölle" with typed cells, gaps and formulas, an empty "Leer" sheet and a
     * hidden "Versteckt" sheet. The formula input is changed after evaluation, so a cached
     * result that differs from a recalculation proves that nothing is recomputed.
     */
    static byte[] workbook() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            workbook.getProperties().getCoreProperties().setTitle("Zollliste");
            workbook.getProperties().getCoreProperties().setCreator("Corenth Tests");

            XSSFSheet duties = workbook.createSheet("Zölle");
            CellStyle date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            CellStyle amount = workbook.createCellStyle();
            amount.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            text(duties.createRow(0), "Ware", "Zoll", "Datum");
            Row bronze = duties.createRow(1);
            bronze.createCell(0).setCellValue("Bronze");
            bronze.createCell(1).setCellValue(5);
            bronze.createCell(2).setCellValue(LocalDate.of(2026, 10, 7));
            bronze.getCell(2).setCellStyle(date);
            Row blank = duties.createRow(2);
            blank.createCell(0).setCellValue("   ");
            blank.createCell(1);
            text(duties.createRow(3), "Wein");
            text(duties.createRow(4), "", "Mitte");
            Row sum = duties.createRow(5);
            sum.createCell(0).setCellValue("Summe");
            sum.createCell(1).setCellFormula("B2*2");
            Row money = duties.createRow(6);
            money.createCell(0).setCellValue("Betrag");
            money.createCell(1).setCellValue(1234.5);
            money.getCell(1).setCellStyle(amount);
            Row joined = duties.createRow(7);
            joined.createCell(0).setCellValue("Text");
            joined.createCell(1).setCellFormula("CONCATENATE(\"Ha\",\"fen\")");
            Row truth = duties.createRow(8);
            truth.createCell(0).setCellValue("Wahr");
            truth.createCell(1).setCellFormula("1=1");
            Row error = duties.createRow(9);
            error.createCell(0).setCellValue("Fehler");
            error.createCell(1).setCellFormula("1/0");

            workbook.createSheet("Leer");
            XSSFSheet hidden = workbook.createSheet("Versteckt");
            text(hidden.createRow(0), "geheim");
            workbook.setSheetHidden(2, true);

            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            bronze.getCell(1).setCellValue(7);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                workbook.close();
            } catch (IOException ignored) {
                // test fixture
            }
        }
    }

    /** A workbook whose only sheet has no cell values. */
    static byte[] emptyWorkbook() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            workbook.createSheet("Leer");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                workbook.close();
            } catch (IOException ignored) {
                // test fixture
            }
        }
    }

    private static void text(Row row, String... values) {
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }
}
