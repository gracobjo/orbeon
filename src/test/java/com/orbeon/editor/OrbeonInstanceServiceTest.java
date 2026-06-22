package com.orbeon.editor;

import com.orbeon.editor.model.ResultadoCumplimentacion;
import com.orbeon.editor.service.OrbeonInstanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OrbeonInstanceServiceTest {

    @Autowired
    private OrbeonInstanceService instanceService;

    @Test
    void presetInstrucciones684AplicaNifYCif() throws Exception {
        Path plantilla = Path.of("684_F1b_MIXTO_480_Solicitud_v39.txt");
        String xml = Files.readString(plantilla, StandardCharsets.UTF_8);

        ResultadoCumplimentacion resultado = instanceService.aplicarPreset(xml, "instrucciones-684");

        assertTrue(resultado.getCamposAplicados() > 20, "Debe aplicar muchos campos");
        assertTrue(resultado.getXml().contains("P0502100A"), "NIF del ayuntamiento");
        assertTrue(resultado.getXml().contains("documentoIdent-tipodoc>cif"),
                "Tipo documento CIF");
        assertTrue(resultado.getEtiquetas().containsKey("empresa-provincia"), "Etiquetas de provincia");
    }
}
