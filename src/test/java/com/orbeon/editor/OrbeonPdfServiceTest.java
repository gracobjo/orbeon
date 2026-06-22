package com.orbeon.editor;

import com.lowagie.text.pdf.PdfReader;
import com.orbeon.editor.model.ResultadoCumplimentacion;
import com.orbeon.editor.service.OrbeonInstanceService;
import com.orbeon.editor.service.OrbeonPdfService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OrbeonPdfServiceTest {

    @Autowired
    private OrbeonPdfService pdfService;

    @Autowired
    private OrbeonInstanceService instanceService;

    @Test
    void generaPdfFormalDesdePlantillaV39() throws Exception {
        Path plantilla = Path.of("684_F1b_MIXTO_480_Solicitud_v39.txt");
        String xml = Files.readString(plantilla, StandardCharsets.UTF_8);

        byte[] pdf = pdfService.generarPdf(xml, List.of());

        assertNotNull(pdf);
        assertTrue(pdf.length > 2_000, "PDF demasiado pequeño: " + pdf.length);
        assertTrue(pdf[0] == '%' && pdf[1] == 'P', "No es un PDF válido");
    }

    @Test
    void pdfCumplimentadoContieneDatosInstrucciones() throws Exception {
        Path plantilla = Path.of("684_F1b_MIXTO_480_Solicitud_v39.txt");
        String xml = Files.readString(plantilla, StandardCharsets.UTF_8);
        ResultadoCumplimentacion cumpl = instanceService.aplicarPreset(xml, "instrucciones-684");

        byte[] pdf = pdfService.generarPdf(cumpl.getXml(), List.of(), cumpl.getEtiquetas());

        assertTrue(pdf.length > 10_000, "PDF cumplimentado pequeño: " + pdf.length);
        PdfReader reader = new PdfReader(pdf);
        try {
            assertTrue(reader.getNumberOfPages() >= 4,
                    "PDF cumplimentado debe tener varias páginas, tiene: " + reader.getNumberOfPages());
        } finally {
            reader.close();
        }
    }
}
