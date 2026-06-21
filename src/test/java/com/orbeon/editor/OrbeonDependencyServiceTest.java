package com.orbeon.editor;

import com.orbeon.editor.service.OrbeonDependencyService;
import com.orbeon.editor.service.OrbeonModificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OrbeonDependencyServiceTest {

    @Autowired
    private OrbeonDependencyService dependencyService;

    @Autowired
    private OrbeonModificationService modificationService;

    @Test
    void analizaSeccionesCondicionalesEnPlantillaV39() throws Exception {
        String xml = Files.readString(Path.of("684_F1b_MIXTO_480_Solicitud_v39.txt"));
        var analisis = dependencyService.analizar(xml);

        assertFalse(analisis.getElementos().isEmpty());
        assertTrue(analisis.getTotalSecciones() > 20);

        boolean tieneEmpresa = analisis.getElementos().stream()
                .anyMatch(e -> "empresa-section".equals(e.getId())
                        && e.getExpresionRelevant() != null
                        && e.getExpresionRelevant().contains("cif"));
        assertTrue(tieneEmpresa);

        boolean tieneVinculadas = analisis.getElementos().stream()
                .anyMatch(e -> "vinculadas-section".equals(e.getId())
                        && e.getDependeDe().contains("otrosDatosEmpresa-vinculadas"));
        assertTrue(tieneVinculadas);
    }

    @Test
    void updateSectionRelevantModificaXml() throws Exception {
        String xml = Files.readString(Path.of("684_F1b_MIXTO_480_Solicitud_v39.txt"));
        var change = java.util.Map.<String, Object>of(
                "type", "update-section-relevant",
                "sectionId", "vinculadas-section",
                "relevant", "true()"
        );
        var resp = modificationService.aplicarCambios(xml, java.util.List.of(change));
        assertTrue(resp.getXml().contains("id=\"vinculadas-bind\""));
        assertTrue(resp.getXml().contains("relevant=\"true()\""));
    }
}
