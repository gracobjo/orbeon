package com.orbeon.editor;

import com.orbeon.editor.service.OrbeonCalculatorService;
import com.orbeon.editor.service.OrbeonModificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OrbeonCalculatorServiceTest {

    @Autowired
    private OrbeonCalculatorService calculatorService;

    @Autowired
    private OrbeonModificationService modificationService;

    @Test
    void analizaCalculadorasEnPlantillaV39() throws Exception {
        String xml = Files.readString(Path.of("684_F1b_MIXTO_480_Solicitud_v39.txt"));
        var analisis = calculatorService.analizar(xml);

        assertTrue(analisis.getTotal() > 100);
        assertFalse(analisis.getElementos().isEmpty());
        assertFalse(analisis.getGlosarioFuentes().isEmpty());

        boolean tieneTipodoc = analisis.getElementos().stream()
                .anyMatch(c -> "documentoIdent-tipodoc-bind".equals(c.getBindId())
                        && c.getFuentesDatos().contains("documentoIdent-nifSol"));
        assertTrue(tieneTipodoc);

        boolean tieneApi = analisis.getElementos().stream()
                .anyMatch(c -> "autonomo-dCnae-bind".equals(c.getBindId())
                        && !c.getUrlsExternas().isEmpty());
        assertTrue(tieneApi);
    }

    @Test
    void updateCalculatorModificaYEliminaCalculate() throws Exception {
        String xml = Files.readString(Path.of("684_F1b_MIXTO_480_Solicitud_v39.txt"));
        var change = Map.<String, Object>of(
                "type", "update-calculator",
                "bindId", "provincializador-bind",
                "calculate", "$empresa-provincia"
        );
        var resp = modificationService.aplicarCambios(xml, List.of(change));
        assertTrue(resp.getXml().contains("id=\"provincializador-bind\""));
        assertTrue(resp.getXml().contains("calculate=\"$empresa-provincia\""));

        var remove = Map.<String, Object>of(
                "type", "update-calculator",
                "bindId", "provincializador-bind",
                "removeCalculate", true
        );
        var resp2 = modificationService.aplicarCambios(resp.getXml(), List.of(remove));
        var analisis = calculatorService.analizar(resp2.getXml());
        assertTrue(analisis.getElementos().stream()
                .noneMatch(c -> "provincializador-bind".equals(c.getBindId())));
    }
}
