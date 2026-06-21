package com.orbeon.editor.service;

import com.orbeon.editor.dto.ModificacionResponse;
import com.orbeon.editor.dto.NaturalLanguageResponse;
import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.model.ItemSelect;
import com.orbeon.editor.model.LogoEnFormulario;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta instrucciones en lenguaje natural (español) y las traduce a cambios XML
 * o consultas sobre logos y desplegables.
 */
@Service
public class OrbeonNaturalLanguageService {

    private static final Set<String> TIPOS_SELECT = Set.of("select", "select1");

    private final OrbeonFormService formService;
    private final OrbeonStructureService structureService;
    private final OrbeonLogoService logoService;
    private final OrbeonModificationService modificationService;

    public OrbeonNaturalLanguageService(OrbeonFormService formService,
                                         OrbeonStructureService structureService,
                                         OrbeonLogoService logoService,
                                         OrbeonModificationService modificationService) {
        this.formService = formService;
        this.structureService = structureService;
        this.logoService = logoService;
        this.modificationService = modificationService;
    }

    public NaturalLanguageResponse procesar(String xml, String instruccion, boolean aplicar) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("El XML es obligatorio");
        }
        if (instruccion == null || instruccion.isBlank()) {
            throw new IllegalArgumentException("La instrucción es obligatoria");
        }

        String texto = normalizar(instruccion.trim());
        List<ComponenteFormulario> componentes = formService.parsearEstructuraDesdeString(xml);

        NaturalLanguageResponse resp = new NaturalLanguageResponse();
        resp.setXml(xml);

        // --- Consultas logos ---
        if (coincide(texto, "cuantos logos", "cuantos logotipos", "numero de logos", "cuantas imagenes",
                "cuántos logos", "cuántas imágenes")) {
            return responderLogos(xml, resp, "consulta-cantidad-logos");
        }
        if (coincide(texto, "donde esta el logo", "donde estan los logos", "posicion del logo",
                "posición del logo", "ubicacion del logo", "ubicación del logo", "listar logos",
                "mostrar logos", "ver logos", "que logos tiene")) {
            return responderLogos(xml, resp, "consulta-posicion-logos");
        }

        String original = instruccion.trim();

        // --- Modificar logos ---
        Matcher sustituir = Pattern.compile(
                "(sustituir|cambiar|reemplazar|modificar)\\s+(el\\s+)?(logo|imagen)\\s+([\\w-]+)?\\s*(por|con)\\s+(.+)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(original);
        if (sustituir.find()) {
            return ejecutarCambioLogo(xml, componentes, resp, sustituir, aplicar);
        }

        Matcher sustituirSimple = Pattern.compile(
                "(sustituir|cambiar|reemplazar)\\s+(el\\s+)?logo\\s+(por|con)\\s+(.+)",
                Pattern.CASE_INSENSITIVE
        ).matcher(original);
        if (sustituirSimple.find()) {
            Matcher m = sustituirSimple;
            return ejecutarCambioLogoSimple(xml, componentes, resp, m.group(4).trim(), aplicar);
        }

        Matcher anadirLogo = Pattern.compile(
                "(?:añadir|anadir|agregar|crear)\\s+(?:un\\s+)?(?:nuevo\\s+)?(logo|imagen)\\s+([\\w-]+)?",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(original);
        if (anadirLogo.find()) {
            return ejecutarAnadirLogo(xml, resp, anadirLogo, instruccion, aplicar);
        }

        // --- Consultas desplegables ---
        Matcher listarOpciones = Pattern.compile(
                "(?:listar|mostrar|ver|cuales son|cuáles son)\\s+(?:las\\s+)?opciones\\s+(?:del\\s+)?desplegable\\s+(.+)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(original);
        if (listarOpciones.find()) {
            return responderOpcionesDesplegable(componentes, resp, listarOpciones.group(1).trim());
        }

        Matcher listarDesplegables = Pattern.compile(
                "(listar|mostrar|ver|cuantos)\\s+(los\\s+)?desplegables",
                Pattern.CASE_INSENSITIVE
        ).matcher(texto);
        if (listarDesplegables.find()) {
            return responderListaDesplegables(componentes, resp);
        }

        // --- CRUD desplegables ---
        Matcher anadirOpcion = Pattern.compile(
                "(?:añadir|anadir|agregar)\\s+(?:opción|opcion)\\s+(\\S+)\\s+con\\s+valor\\s+(\\S+)\\s+(?:al|del)\\s+desplegable\\s+(.+)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(original);
        if (anadirOpcion.find()) {
            String label = anadirOpcion.group(1).trim();
            String value = anadirOpcion.group(2).trim();
            String campoRef = anadirOpcion.group(3).trim();
            return ejecutarCrudSelect(xml, componentes, resp, "add-select-item", campoRef, label, value, null, aplicar);
        }

        Matcher anadirOpcionSimple = Pattern.compile(
                "(?:añadir|anadir|agregar)\\s+(?:opción|opcion)\\s+[\"']?([^\"']+)[\"']?\\s+(?:al|del)\\s+desplegable\\s+(.+)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(original);
        if (anadirOpcionSimple.find()) {
            String label = anadirOpcionSimple.group(1).trim();
            String value = slugify(label);
            String campoRef = anadirOpcionSimple.group(2).trim();
            return ejecutarCrudSelect(xml, componentes, resp, "add-select-item", campoRef, label, value, null, aplicar);
        }

        Matcher eliminarOpcion = Pattern.compile(
                "(eliminar|quitar|borrar)\\s+(?:opción|opcion)\\s+([\\w.-]+)\\s+(?:del|de el)\\s+desplegable\\s+(.+)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(original);
        if (eliminarOpcion.find()) {
            return ejecutarCrudSelect(xml, componentes, resp, "remove-select-item",
                    eliminarOpcion.group(3).trim(), null, eliminarOpcion.group(2).trim(), null, aplicar);
        }

        Matcher cambiarOpcion = Pattern.compile(
                "(cambiar|modificar)\\s+(?:opción|opcion)\\s+([\\w.-]+)\\s+(?:del|de el)\\s+desplegable\\s+(.+?)\\s+(?:a|por)\\s+[\"']?([^\"']+)[\"']?",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(original);
        if (cambiarOpcion.find()) {
            return ejecutarCrudSelect(xml, componentes, resp, "update-select-item",
                    cambiarOpcion.group(3).trim(), cambiarOpcion.group(4).trim(),
                    cambiarOpcion.group(2).trim(), null, aplicar);
        }

        // --- Labels / hints genéricos ---
        Matcher cambiarLabel = Pattern.compile(
                "(cambiar|modificar|actualizar)\\s+(el\\s+)?label\\s+(del\\s+)?campo\\s+(.+?)\\s+(?:a|por)\\s+[\"']?([^\"']+)[\"']?",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(original);
        if (cambiarLabel.find()) {
            ComponenteFormulario campo = resolverCampo(componentes, cambiarLabel.group(4).trim())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No encontré el campo: " + cambiarLabel.group(4)));
            String fieldId = campo.getMetadatos().getOrDefault("resourceKey", "");
            Map<String, Object> change = Map.of(
                    "type", "update-label",
                    "fieldId", fieldId,
                    "label", cambiarLabel.group(5).trim()
            );
            return aplicarOProponer(xml, resp, "update-label", List.of(change), aplicar,
                    "Label de '" + fieldId + "' actualizado.");
        }

        Matcher cambiarHint = Pattern.compile(
                "(cambiar|modificar|actualizar)\\s+(el\\s+)?hint\\s+(del\\s+)?campo\\s+(.+?)\\s+(?:a|por)\\s+[\"']?([^\"']+)[\"']?",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(original);
        if (cambiarHint.find()) {
            ComponenteFormulario campo = resolverCampo(componentes, cambiarHint.group(4).trim())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No encontré el campo: " + cambiarHint.group(4)));
            String fieldId = campo.getMetadatos().getOrDefault("resourceKey", "");
            Map<String, Object> change = Map.of(
                    "type", "update-hint",
                    "fieldId", fieldId,
                    "hint", cambiarHint.group(5).trim()
            );
            return aplicarOProponer(xml, resp, "update-hint", List.of(change), aplicar,
                    "Hint de '" + fieldId + "' actualizado.");
        }

        // Ayuda
        resp.setIntencion("ayuda");
        resp.setEjecutado(false);
        resp.setRespuesta(ayuda());
        return resp;
    }

    private NaturalLanguageResponse responderLogos(String xml, NaturalLanguageResponse resp, String intencion) {
        List<LogoEnFormulario> logos = logoService.analizarLogos(xml);
        resp.setIntencion(intencion);
        resp.setLogos(logos);
        resp.setEjecutado(false);
        resp.setRespuesta(logoService.describirLogos(xml));
        return resp;
    }

    private NaturalLanguageResponse ejecutarCambioLogo(String xml, List<ComponenteFormulario> componentes,
                                                        NaturalLanguageResponse resp, Matcher m, boolean aplicar) {
        String tag = m.group(4) != null ? m.group(4).trim() : null;
        String destino = m.group(6).trim();
        return construirCambioLogo(xml, componentes, resp, tag, destino, aplicar);
    }

    private NaturalLanguageResponse ejecutarCambioLogoSimple(String xml, List<ComponenteFormulario> componentes,
                                                                NaturalLanguageResponse resp, String destino,
                                                                boolean aplicar) {
        return construirCambioLogo(xml, componentes, resp, null, destino, aplicar);
    }

    private NaturalLanguageResponse construirCambioLogo(String xml, List<ComponenteFormulario> componentes,
                                                         NaturalLanguageResponse resp, String tagHint,
                                                         String destino, boolean aplicar) {
        List<LogoEnFormulario> logos = logoService.analizarLogos(xml);
        if (logos.isEmpty()) {
            throw new IllegalArgumentException("No hay logos en el formulario para sustituir.");
        }

        LogoEnFormulario logo = resolverLogo(logos, tagHint)
                .orElse(logos.get(0));

        String src = destino;
        String filename = destino;
        String mediatype = "image/png";
        if (destino.contains("|")) {
            String[] partes = destino.split("\\|");
            src = partes[0].trim();
            if (partes.length > 1) {
                filename = partes[1].trim();
            }
            if (partes.length > 2) {
                mediatype = partes[2].trim();
            }
        } else if (destino.startsWith("/") || destino.startsWith("http")) {
            filename = extraerNombreArchivo(destino);
        }

        Map<String, Object> change = new HashMap<>();
        change.put("type", "update-image");
        change.put("imageTag", logo.getTag());
        change.put("src", src);
        change.put("filename", filename);
        change.put("mediatype", mediatype);

        String msg = "Logo '" + logo.getTag() + "' (posición " + logo.getPosicionGlobal()
                + ", sección \"" + logo.getSectionTitulo() + "\") sustituido.";
        return aplicarOProponer(xml, resp, "sustituir-logo", List.of(change), aplicar, msg);
    }

    private NaturalLanguageResponse ejecutarAnadirLogo(String xml, NaturalLanguageResponse resp,
                                                        Matcher m, String instruccionOriginal, boolean aplicar) {
        String tag = m.group(2) != null ? m.group(2).trim() : null;
        if (tag == null || tag.isBlank()) {
            tag = "nuevo-logo-" + System.currentTimeMillis() % 10000;
        }

        String src = "";
        String sectionId = null;
        Matcher srcM = Pattern.compile("(?:src|url|ruta)\\s+(.+?)(?:\\s+en\\s+seccion|\\s+en\\s+sección|$)",
                Pattern.CASE_INSENSITIVE).matcher(instruccionOriginal);
        if (srcM.find()) {
            src = srcM.group(1).trim();
        }
        Matcher secM = Pattern.compile("(?:en|seccion|sección)\\s+([\\w-]+)", Pattern.CASE_INSENSITIVE)
                .matcher(instruccionOriginal);
        if (secM.find()) {
            sectionId = secM.group(1).trim();
            if (!sectionId.endsWith("-section")) {
                sectionId = sectionId + "-section";
            }
        }

        Map<String, Object> change = new HashMap<>();
        change.put("type", "add-image");
        change.put("imageTag", tag);
        change.put("src", src);
        change.put("filename", tag + ".png");
        change.put("mediatype", "image/png");
        if (sectionId != null) {
            change.put("sectionId", sectionId);
        }

        return aplicarOProponer(xml, resp, "anadir-logo", List.of(change), aplicar,
                "Logo nuevo '" + tag + "' añadido" + (sectionId != null ? " en " + sectionId : "") + ".");
    }

    private NaturalLanguageResponse responderOpcionesDesplegable(List<ComponenteFormulario> componentes,
                                                                  NaturalLanguageResponse resp, String ref) {
        ComponenteFormulario campo = resolverCampoDesplegable(componentes, ref)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No encontré desplegable: " + ref + ". Prueba con el label o id del campo."));
        if ("true".equals(campo.getMetadatos().get("itemsetDinamico"))) {
            resp.setIntencion("consulta-opciones-dinamicas");
            resp.setEjecutado(false);
            resp.setRespuesta("El desplegable '" + campo.getLabel() + "' (" + campo.getId()
                    + ") es dinámico. Las opciones se cargan desde: "
                    + campo.getMetadatos().getOrDefault("resourceUrl",
                            campo.getMetadatos().getOrDefault("itemsetRef", "?"))
                    + ". No se puede hacer CRUD sobre sus opciones en el XML.");
            return resp;
        }
        List<ItemSelect> items = campo.getItems();
        StringBuilder sb = new StringBuilder();
        sb.append("Desplegable \"").append(campo.getLabel()).append("\" (").append(campo.getId())
                .append(") tiene ").append(items.size()).append(" opciones:\n");
        int i = 1;
        for (ItemSelect it : items) {
            sb.append("  ").append(i++).append(". ").append(it.getLabel()).append(" → ").append(it.getValue()).append("\n");
        }
        resp.setIntencion("consulta-opciones-desplegable");
        resp.setEjecutado(false);
        resp.setRespuesta(sb.toString().trim());
        return resp;
    }

    private NaturalLanguageResponse responderListaDesplegables(List<ComponenteFormulario> componentes,
                                                                NaturalLanguageResponse resp) {
        List<ComponenteFormulario> selects = componentes.stream()
                .filter(c -> TIPOS_SELECT.contains(c.getTipo())
                        || "databound-select1".equals(c.getTipo()))
                .toList();
        StringBuilder sb = new StringBuilder();
        sb.append("Hay ").append(selects.size()).append(" desplegables:\n");
        for (ComponenteFormulario c : selects) {
            boolean din = "true".equals(c.getMetadatos().get("itemsetDinamico"));
            int n = c.getItems() != null ? c.getItems().size() : 0;
            sb.append("  • ").append(c.getLabel()).append(" (").append(c.getId()).append(") — ")
                    .append(din ? "dinámico" : n + " opciones").append("\n");
        }
        resp.setIntencion("consulta-desplegables");
        resp.setEjecutado(false);
        resp.setRespuesta(sb.toString().trim());
        return resp;
    }

    private NaturalLanguageResponse ejecutarCrudSelect(String xml, List<ComponenteFormulario> componentes,
                                                        NaturalLanguageResponse resp, String tipo,
                                                        String campoRef, String label, String value,
                                                        String newValue, boolean aplicar) {
        ComponenteFormulario campo = resolverCampoDesplegable(componentes, campoRef)
                .orElseThrow(() -> new IllegalArgumentException("No encontré desplegable: " + campoRef));
        if ("true".equals(campo.getMetadatos().get("itemsetDinamico"))) {
            throw new IllegalArgumentException("El desplegable '" + campo.getLabel()
                    + "' es dinámico; no admite CRUD de opciones en el XML.");
        }
        String fieldId = campo.getMetadatos().getOrDefault("resourceKey", "");
        Map<String, Object> change = new HashMap<>();
        change.put("type", tipo);
        change.put("fieldId", fieldId);
        if (label != null) {
            change.put("label", label);
        }
        if (value != null) {
            change.put("value", value);
        }
        if (newValue != null) {
            change.put("newValue", newValue);
        }
        String msg = switch (tipo) {
            case "add-select-item" -> "Opción '" + label + "' añadida al desplegable " + fieldId;
            case "remove-select-item" -> "Opción '" + value + "' eliminada de " + fieldId;
            case "update-select-item" -> "Opción '" + value + "' actualizada en " + fieldId;
            default -> "Cambio aplicado en " + fieldId;
        };
        return aplicarOProponer(xml, resp, tipo, List.of(change), aplicar, msg);
    }

    private NaturalLanguageResponse aplicarOProponer(String xml, NaturalLanguageResponse resp,
                                                      String intencion, List<Map<String, Object>> changes,
                                                      boolean aplicar, String mensajeExito) {
        resp.setIntencion(intencion);
        resp.setCambiosPropuestos(changes);
        if (!aplicar) {
            resp.setEjecutado(false);
            resp.setRespuesta("Cambios propuestos (no aplicados):\n" + changes);
            return resp;
        }
        ModificacionResponse mod = modificationService.aplicarCambios(xml, changes);
        resp.setXml(mod.getXml());
        resp.setLog(mod.getChangeLog());
        boolean ok = mod.getApplied().stream().noneMatch(a -> a.startsWith("✗"));
        resp.setEjecutado(ok);
        if (ok) {
            resp.setRespuesta(mensajeExito + "\n" + String.join("\n", mod.getApplied()));
            resp.setComponentes(formService.parsearEstructuraDesdeString(mod.getXml()));
            resp.setEstructura(structureService.parsearEstructuraCompleta(mod.getXml()));
        } else {
            resp.setRespuesta("Algunos cambios fallaron:\n" + String.join("\n", mod.getApplied()));
        }
        return resp;
    }

    private Optional<ComponenteFormulario> resolverCampoDesplegable(List<ComponenteFormulario> componentes, String ref) {
        String n = normalizar(ref);
        Optional<ComponenteFormulario> exacto = componentes.stream()
                .filter(c -> TIPOS_SELECT.contains(c.getTipo()))
                .filter(c -> normalizar(c.getLabel()).equals(n)
                        || normalizar(c.getMetadatos().getOrDefault("resourceKey", "")).equals(n))
                .findFirst();
        if (exacto.isPresent()) {
            return exacto;
        }
        return componentes.stream()
                .filter(c -> TIPOS_SELECT.contains(c.getTipo()))
                .filter(c -> normalizar(c.getId()).contains(n)
                        || normalizar(c.getLabel()).contains(n)
                        || normalizar(c.getMetadatos().getOrDefault("resourceKey", "")).contains(n))
                .findFirst();
    }

    private Optional<ComponenteFormulario> resolverCampo(List<ComponenteFormulario> componentes, String ref) {
        String n = normalizar(ref);
        return componentes.stream()
                .filter(c -> normalizar(c.getId()).contains(n)
                        || normalizar(c.getLabel()).contains(n)
                        || normalizar(c.getMetadatos().getOrDefault("resourceKey", "")).contains(n))
                .findFirst();
    }

    private Optional<LogoEnFormulario> resolverLogo(List<LogoEnFormulario> logos, String tagHint) {
        if (tagHint == null || tagHint.isBlank()) {
            return Optional.empty();
        }
        String n = normalizar(tagHint);
        return logos.stream()
                .filter(l -> normalizar(l.getTag()).contains(n)
                        || normalizar(l.getControlId()).contains(n))
                .findFirst();
    }

    private String extraerNombreArchivo(String ruta) {
        int slash = Math.max(ruta.lastIndexOf('/'), ruta.lastIndexOf('\\'));
        String nombre = slash >= 0 ? ruta.substring(slash + 1) : ruta;
        return nombre.contains(".") ? nombre : nombre + ".png";
    }

    private String slugify(String texto) {
        String n = normalizar(texto).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return n.isBlank() ? "valor" : n.toUpperCase(Locale.ROOT);
    }

    private boolean coincide(String texto, String... frases) {
        for (String f : frases) {
            if (texto.contains(normalizar(f))) {
                return true;
            }
        }
        return false;
    }

    private String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    private String ayuda() {
        return """
                Puedo interpretar instrucciones como:
                
                LOGOS:
                  • «¿Cuántos logos tiene?»
                  • «¿Dónde está el logo?» / «Listar logos»
                  • «Sustituir el logo iapa-img por /ruta/nuevo.png»
                  • «Añadir logo mi-logo src /ruta/logo.png en sección cabecera-section»
                
                DESPLEGABLES:
                  • «Listar desplegables»
                  • «Listar opciones del desplegable tipo de vía»
                  • «Añadir opción OTROS con valor OT al desplegable tipo de vía»
                  • «Eliminar opción CL del desplegable tipo de vía»
                  • «Cambiar opción CL del desplegable tipo de vía por CALLE PRINCIPAL»
                
                CAMPOS:
                  • «Cambiar el label del campo nombre a Nombre completo»
                  • «Cambiar el hint del campo nif a Introduzca DNI o NIE»
                """;
    }
}
