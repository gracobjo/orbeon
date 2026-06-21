package com.orbeon.editor;

import com.orbeon.editor.dto.NaturalLanguageResponse;
import com.orbeon.editor.model.LogoEnFormulario;
import com.orbeon.editor.service.OrbeonLogoService;
import com.orbeon.editor.service.OrbeonNaturalLanguageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class NaturalLanguageServiceTest {

    @Autowired
    private OrbeonNaturalLanguageService naturalLanguageService;

    @Autowired
    private OrbeonLogoService logoService;

    @Test
    void detectaCantidadYPosicionDeLogos() throws Exception {
        String xml = Files.readString(Path.of("684_F1b_MIXTO_480_Solicitud_PRE.txt"));
        List<LogoEnFormulario> logos = logoService.analizarLogos(xml);

        assertTrue(logos.size() >= 1, "Debe detectar al menos un logo");
        LogoEnFormulario logo = logos.stream()
                .filter(l -> "iapa-img".equals(l.getTag()))
                .findFirst()
                .orElseThrow();
        assertEquals("iapa-img-control", logo.getControlId());
        assertTrue(logo.getPosicionGlobal() >= 1);
        assertTrue(logo.getPosicionEnSeccion() >= 1);
        assertFalse(logo.getSectionId().isBlank());
    }

    @Test
    void consultaCuantosLogosEnLenguajeNatural() throws Exception {
        String xml = Files.readString(Path.of("684_F1b_MIXTO_480_Solicitud_PRE.txt"));
        NaturalLanguageResponse resp = naturalLanguageService.procesar(
                xml, "¿Cuántos logos tiene el formulario?", false);

        assertFalse(resp.isEjecutado());
        assertTrue(resp.getRespuesta().contains("logo") || resp.getRespuesta().contains("imagen"),
                "Respuesta: " + resp.getRespuesta());
        assertTrue(resp.getLogos().size() >= 1);
    }

    @Test
    void listaOpcionesDesplegableTipoVia() throws Exception {
        String xml = Files.readString(Path.of("684_F1b_MIXTO_480_Solicitud_PRE.txt"));
        NaturalLanguageResponse resp = naturalLanguageService.procesar(
                xml, "listar opciones del desplegable tipo de vía", false);

        assertFalse(resp.isEjecutado());
        assertTrue(resp.getRespuesta().contains("29 opciones") || resp.getRespuesta().contains("opciones"),
                resp.getRespuesta());
    }

    @Test
    void anadirYEliminarOpcionDesplegable() throws Exception {
        String xml = Files.readString(Path.of("684_F1b_MIXTO_480_Solicitud_PRE.txt"));

        NaturalLanguageResponse add = naturalLanguageService.procesar(
                xml,
                "añadir opción PRUEBA_IA con valor PIA al desplegable tipo de vía",
                true);
        assertTrue(add.isEjecutado(), add.getRespuesta());

        NaturalLanguageResponse list = naturalLanguageService.procesar(
                add.getXml(),
                "listar opciones del desplegable tipo de vía",
                false);
        assertTrue(list.getRespuesta().contains("PIA") || list.getRespuesta().contains("PRUEBA_IA"));

        NaturalLanguageResponse del = naturalLanguageService.procesar(
                add.getXml(),
                "eliminar opción PIA del desplegable tipo de vía",
                true);
        assertTrue(del.isEjecutado());
    }
}
