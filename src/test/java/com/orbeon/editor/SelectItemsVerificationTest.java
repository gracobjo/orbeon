package com.orbeon.editor;

import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.service.OrbeonFormService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SelectItemsVerificationTest {

    @Autowired
    private OrbeonFormService orbeonFormService;

    private static final Set<String> TIPOS_SELECT = Set.of(
            "select", "select1", "databound-select1"
    );

    @Test
    void todosLosDesplegablesEstaticosTienenOpciones() throws Exception {
        Path xml = Path.of("684_F1b_MIXTO_480_Solicitud_PRE.txt");
        String contenido = Files.readString(xml);
        List<ComponenteFormulario> componentes = orbeonFormService.parsearEstructuraDesdeString(contenido);

        List<ComponenteFormulario> selects = componentes.stream()
                .filter(c -> TIPOS_SELECT.contains(c.getTipo()))
                .toList();

        System.out.println("=== DESPLEGABLES PRE.txt: " + selects.size() + " ===");
        int estaticosOk = 0;
        int dinamicos = 0;
        int sinOpciones = 0;

        for (ComponenteFormulario c : selects) {
            int n = c.getItems() != null ? c.getItems().size() : 0;
            boolean dinamico = "true".equals(c.getMetadatos().get("itemsetDinamico"))
                    || c.getMetadatos().containsKey("resourceUrl");
            String estado;
            if (n > 0) {
                estado = "OK (" + n + " opciones)";
                estaticosOk++;
            } else if (dinamico) {
                estado = "DINÁMICO (" + c.getMetadatos().getOrDefault("itemsetRef", "?") + ")";
                dinamicos++;
            } else {
                estado = "SIN OPCIONES";
                sinOpciones++;
            }
            System.out.printf("  %-45s %-20s %s%n", c.getId(), c.getTipo(), estado);
        }

        System.out.printf("Resumen: %d con opciones, %d dinámicos, %d sin opciones%n",
                estaticosOk, dinamicos, sinOpciones);

        assertTrue(selects.size() >= 20, "Debe detectar al menos 20 desplegables");
        assertTrue(sinOpciones == 0, "Hay desplegables estáticos sin opciones: " + sinOpciones);
    }

    @Test
    void todosLosDesplegablesEstaticosTienenOpciones_v39() throws Exception {
        Path xml = Path.of("684_F1b_MIXTO_480_Solicitud_v39.txt");
        if (!Files.exists(xml)) {
            return;
        }
        String contenido = Files.readString(xml);
        List<ComponenteFormulario> componentes = orbeonFormService.parsearEstructuraDesdeString(contenido);

        List<ComponenteFormulario> selects = componentes.stream()
                .filter(c -> TIPOS_SELECT.contains(c.getTipo()))
                .toList();

        System.out.println("=== DESPLEGABLES v39.txt: " + selects.size() + " ===");
        int sinOpciones = 0;
        for (ComponenteFormulario c : selects) {
            int n = c.getItems() != null ? c.getItems().size() : 0;
            boolean dinamico = "true".equals(c.getMetadatos().get("itemsetDinamico"))
                    || c.getMetadatos().containsKey("resourceUrl");
            if (n == 0 && !dinamico) {
                sinOpciones++;
                System.out.printf("  SIN OPCIONES: %s (%s)%n", c.getId(), c.getTipo());
            }
        }
        System.out.printf("Resumen v39: %d desplegables, %d sin opciones%n", selects.size(), sinOpciones);
        assertTrue(sinOpciones == 0, "Hay desplegables estáticos sin opciones en v39: " + sinOpciones);
    }
}
