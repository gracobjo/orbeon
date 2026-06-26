package com.orbeon.editor.service;

import com.lowagie.text.pdf.PdfArray;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfObject;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfString;
import com.orbeon.editor.model.AnotacionInstruccionPdf;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Extrae anotaciones de PDFs de instrucciones (comentarios en margen, tachados, texto libre).
 */
@Service
public class OrbeonPdfInstructionsService {

    public List<AnotacionInstruccionPdf> extraerAnotaciones(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("El PDF está vacío");
        }
        try {
            PdfReader reader = new PdfReader(pdfBytes);
            try {
                List<AnotacionInstruccionPdf> resultado = new ArrayList<>();
                for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                    PdfDictionary pageDict = reader.getPageN(page);
                    if (pageDict == null) {
                        continue;
                    }
                    PdfArray annots = pageDict.getAsArray(PdfName.ANNOTS);
                    if (annots == null) {
                        continue;
                    }
                    for (int i = 0; i < annots.size(); i++) {
                        PdfDictionary annot = annots.getAsDict(i);
                        if (annot == null) {
                            continue;
                        }
                        String contenido = extraerContenido(annot);
                        if (contenido == null || contenido.isBlank()) {
                            continue;
                        }
                        AnotacionInstruccionPdf item = new AnotacionInstruccionPdf();
                        item.setPagina(page);
                        PdfName subtype = annot.getAsName(PdfName.SUBTYPE);
                        item.setSubtipo(subtype != null ? subtype.toString().replace("/", "") : "Unknown");
                        item.setContenido(contenido.trim());
                        List<Float> rect = extraerRect(annot);
                        item.setRect(rect);
                        item.setPosicionVertical(rect.isEmpty() ? 0f : (rect.get(1) + rect.get(3)) / 2f);
                        resultado.add(item);
                    }
                }
                resultado.sort(Comparator
                        .comparingInt(AnotacionInstruccionPdf::getPagina)
                        .thenComparing(AnotacionInstruccionPdf::getPosicionVertical, Comparator.reverseOrder()));
                return resultado;
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el PDF de instrucciones: " + e.getMessage(), e);
        }
    }

    public int contarPaginas(byte[] pdfBytes) {
        try {
            PdfReader reader = new PdfReader(pdfBytes);
            try {
                return reader.getNumberOfPages();
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el PDF: " + e.getMessage(), e);
        }
    }

    private String extraerContenido(PdfDictionary annot) {
        PdfString contents = annot.getAsString(PdfName.CONTENTS);
        if (contents != null) {
            return contents.toUnicodeString();
        }
        PdfObject raw = annot.get(PdfName.CONTENTS);
        if (raw instanceof PdfString ps) {
            return ps.toUnicodeString();
        }
        return null;
    }

    private List<Float> extraerRect(PdfDictionary annot) {
        PdfArray rect = annot.getAsArray(PdfName.RECT);
        List<Float> valores = new ArrayList<>(4);
        if (rect == null) {
            return valores;
        }
        for (int i = 0; i < rect.size() && i < 4; i++) {
            PdfObject obj = rect.getPdfObject(i);
            if (obj instanceof com.lowagie.text.pdf.PdfNumber num) {
                valores.add(num.floatValue());
            }
        }
        return valores;
    }
}
