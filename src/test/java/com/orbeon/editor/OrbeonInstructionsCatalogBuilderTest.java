package com.orbeon.editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.orbeon.editor.service.OrbeonInstructionsCatalogBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OrbeonInstructionsCatalogBuilderTest {

    @Autowired
    private OrbeonInstructionsCatalogBuilder catalogBuilder;

    @Test
    void generaReglasDesdeXmlGenerico() {
        String xml = """
                <html xmlns:xf="http://www.w3.org/2002/xforms" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
                  <xf:model>
                    <xf:instance id="fr-form-resources"><resources><resource xml:lang="es">
                      <declaracionesResponsables-textoEjemplo>
                        <text>Certificado de ejemplo para prueba de eliminacion en declaraciones</text>
                      </declaracionesResponsables-textoEjemplo>
                      <anexos-textoDocumentoPrueba>
                        <text>Documento acreditativo de prueba para anexo del formulario</text>
                      </anexos-textoDocumentoPrueba>
                    </resource></resources></xf:instance>
                  </xf:model>
                  <fr:view>
                    <fr:section id="declaraciones-section" bind="declaraciones-bind">
                      <fr:grid><fr:c><xf:select id="declaracionesResponsables-ejemplo-control"/></fr:c></fr:grid>
                    </fr:section>
                    <fr:section id="anexos-section" bind="anexos-bind">
                      <fr:grid><fr:c>
                        <xf:upload id="anexos-DocumentoPrueba-control"/>
                        <xf:output id="anexos-textoDocumentoPrueba-control"/>
                      </fr:c></fr:grid>
                    </fr:section>
                  </fr:view>
                </html>
                """;

        JsonNode catalogo = catalogBuilder.construirDesdeXml(xml);
        ArrayNode declaraciones = (ArrayNode) catalogo.get("reglasDeclaracion");
        ArrayNode anexos = (ArrayNode) catalogo.get("reglasAnexo");

        assertFalse(declaraciones.isEmpty(), "Debe detectar declaraciones");
        assertFalse(anexos.isEmpty(), "Debe detectar anexos");
        assertTrue(catalogo.path("origen").asText().contains("xml"));
    }
}
