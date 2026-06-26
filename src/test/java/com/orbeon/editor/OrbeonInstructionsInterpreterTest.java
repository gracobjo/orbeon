package com.orbeon.editor;

import com.orbeon.editor.dto.AnalisisInstruccionesResponse;
import com.orbeon.editor.service.OrbeonInstructionsInterpreterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OrbeonInstructionsInterpreterTest {

    @Autowired
    private OrbeonInstructionsInterpreterService interpreterService;

    @Test
    void extraeAnotacionesDelPdf684() throws Exception {
        Path pdf = Path.of("684 F1b Mixto - 480 Solicitud_Instrucciones.pdf");
        Path xml = Path.of("684_F1b_MIXTO_480_Solicitud_v39.txt");
        if (!Files.exists(pdf) || !Files.exists(xml)) {
            return;
        }

        AnalisisInstruccionesResponse resp = interpreterService.analizar(
                Files.readAllBytes(pdf),
                pdf.getFileName().toString(),
                Files.readString(xml, StandardCharsets.UTF_8),
                false
        );

        assertTrue(resp.getTotalAnotaciones() >= 15, "Anotaciones: " + resp.getTotalAnotaciones());
        assertFalse(resp.getPropuestas().isEmpty());

        List<String> tipos = resp.getCambiosAgregados().stream()
                .map(c -> (String) c.get("type"))
                .collect(Collectors.toList());
        assertTrue(tipos.contains("remove-field"), "Tipos: " + tipos);

        List<String> campos = resp.getCambiosAgregados().stream()
                .filter(c -> "remove-field".equals(c.get("type")))
                .map(c -> (String) c.get("fieldId"))
                .collect(Collectors.toList());

        assertTrue(campos.stream().anyMatch(f -> f.contains("inscritoROAC")),
                "Debe proponer eliminar ROAC. Campos: " + campos);
        assertTrue(campos.stream().anyMatch(f -> f.contains("permisosConsultaSufo-sufoid1")),
                "Debe proponer eliminar consulta SUFO. Campos: " + campos);
        assertTrue(campos.stream().anyMatch(f -> f.contains("anexos-certSegSocial")),
                "Debe proponer eliminar cert SS. Campos: " + campos);
        assertTrue(resp.getCambiosAgregados().size() < 25,
                "Demasiadas propuestas agregadas: " + resp.getCambiosAgregados().size());
    }

    @Test
    void aplicaCambiosPrincipalesSobreV39() throws Exception {
        Path pdf = Path.of("684 F1b Mixto - 480 Solicitud_Instrucciones.pdf");
        Path xml = Path.of("684_F1b_MIXTO_480_Solicitud_v39.txt");
        if (!Files.exists(pdf) || !Files.exists(xml)) {
            return;
        }

        AnalisisInstruccionesResponse resp = interpreterService.analizar(
                Files.readAllBytes(pdf),
                pdf.getFileName().toString(),
                Files.readString(xml, StandardCharsets.UTF_8),
                true
        );

        String resultado = resp.getXml();
        assertFalse(resultado.contains("declaracionesResponsables-inscritoROAC-control"));
        assertFalse(resultado.contains("permisosConsultaSufo-sufoid1-control"));
        assertTrue(resultado.contains("Documento de identidad del representante."));
        assertTrue(resultado.contains("beneficiaria de la subvención")
                || resultado.contains("beneficiaria de la subvencion"));
        assertTrue(resultado.contains("declaracionesResponsables-noDeudaImpagada-control"),
                "No debe eliminar declaraciones no indicadas en el PDF");
        List<String> campos = resp.getLogAplicados() != null ? resp.getLogAplicados() : List.of();
        assertTrue(campos.size() < 25, "Demasiados cambios aplicados: " + campos.size());
    }
}
