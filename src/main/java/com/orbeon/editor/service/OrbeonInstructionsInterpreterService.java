package com.orbeon.editor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbeon.editor.dto.AnalisisInstruccionesResponse;
import com.orbeon.editor.dto.ModificacionResponse;
import com.orbeon.editor.model.AnotacionInstruccionPdf;
import com.orbeon.editor.model.PropuestaCambioXml;
import com.orbeon.editor.util.OrbeonXmlUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Interpreta anotaciones de un PDF de instrucciones y las traduce a cambios XML Orbeon.
 * Usa reglas lingüísticas + catálogo de referencia del formulario 684 (v39→PRE).
 */
@Service
public class OrbeonInstructionsInterpreterService {

    private static final String CATALOGO_684 = "datos/instrucciones-684-mapeo.json";

    private final OrbeonPdfInstructionsService pdfInstructionsService;
    private final OrbeonModificationService modificationService;
    private final ObjectMapper objectMapper;

    public OrbeonInstructionsInterpreterService(OrbeonPdfInstructionsService pdfInstructionsService,
                                                 OrbeonModificationService modificationService,
                                                 ObjectMapper objectMapper) {
        this.pdfInstructionsService = pdfInstructionsService;
        this.modificationService = modificationService;
        this.objectMapper = objectMapper;
    }

    public AnalisisInstruccionesResponse analizar(byte[] pdfBytes, String nombrePdf, String xml, boolean aplicar) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("El XML del formulario es obligatorio");
        }

        List<AnotacionInstruccionPdf> anotaciones = pdfInstructionsService.extraerAnotaciones(pdfBytes);
        JsonNode catalogo = cargarCatalogo684();

        List<PropuestaCambioXml> propuestas = new ArrayList<>();
        Set<String> cambiosVistos = new HashSet<>();

        propuestas.addAll(resolverApartados(anotaciones, catalogo, xml, cambiosVistos));
        propuestas.addAll(resolverDeclaraciones(anotaciones, catalogo, xml, cambiosVistos));
        propuestas.addAll(resolverAnexos(anotaciones, catalogo, xml, cambiosVistos));
        propuestas.addAll(resolverTextos(anotaciones, catalogo, xml, cambiosVistos));
        propuestas.addAll(resolverEliminacionesGenericas(anotaciones, xml, cambiosVistos));

        List<Map<String, Object>> agregados = new ArrayList<>();
        Set<String> clavesCambio = new HashSet<>();
        for (PropuestaCambioXml propuesta : propuestas) {
            if (!propuesta.isAplicableAutomaticamente()) {
                continue;
            }
            for (Map<String, Object> cambio : propuesta.getCambios()) {
                String clave = claveCambio(cambio);
                if (clavesCambio.add(clave)) {
                    agregados.add(cambio);
                }
            }
        }

        AnalisisInstruccionesResponse resp = new AnalisisInstruccionesResponse();
        resp.setNombrePdf(nombrePdf);
        resp.setTotalPaginas(pdfInstructionsService.contarPaginas(pdfBytes));
        resp.setTotalAnotaciones(anotaciones.size());
        resp.setAnotaciones(anotaciones);
        resp.setPropuestas(propuestas);
        resp.setCambiosAgregados(agregados);
        resp.setResumen(construirResumen(propuestas, agregados));
        resp.setXml(xml);

        if (aplicar && !agregados.isEmpty()) {
            ModificacionResponse mod = modificationService.aplicarCambios(xml, agregados);
            resp.setXml(mod.getXml());
            resp.setLogAplicados(mod.getChangeLog());
        }
        return resp;
    }

    private String claveCambio(Map<String, Object> cambio) {
        String type = String.valueOf(cambio.get("type"));
        if (cambio.containsKey("fieldId")) {
            return type + ":" + cambio.get("fieldId");
        }
        if (cambio.containsKey("bindId")) {
            return type + ":" + cambio.get("bindId");
        }
        if (cambio.containsKey("elementId")) {
            return type + ":" + cambio.get("elementId");
        }
        return type + ":" + cambio;
    }

    private List<PropuestaCambioXml> resolverApartados(List<AnotacionInstruccionPdf> anotaciones,
                                                        JsonNode catalogo, String xml,
                                                        Set<String> cambiosVistos) {
        List<PropuestaCambioXml> resultado = new ArrayList<>();
        if (catalogo == null || !catalogo.has("reglasApartado")) {
            return resultado;
        }

        String textoAnotaciones = textoConsolidado(anotaciones);
        for (JsonNode regla : catalogo.get("reglasApartado")) {
            if (!coincideRegla(textoAnotaciones, regla)) {
                continue;
            }
            PropuestaCambioXml propuesta = new PropuestaCambioXml();
            propuesta.setId(regla.path("id").asText());
            propuesta.setIntencion(regla.path("intencion").asText("eliminar-apartado"));
            propuesta.setDescripcion(regla.path("descripcion").asText());
            propuesta.setConfianza("alta");
            propuesta.setAplicableAutomaticamente(true);
            propuesta.setPagina(regla.path("paginas").get(0).asInt());

            List<Map<String, Object>> cambios = new ArrayList<>();
            List<String> campos = new ArrayList<>();
            if (regla.has("camposEliminar")) {
                for (JsonNode campo : regla.get("camposEliminar")) {
                    String fieldId = campo.asText();
                    if (existeControl(xml, fieldId)) {
                        cambios.add(Map.of("type", "remove-field", "fieldId", fieldId));
                        campos.add(fieldId);
                    }
                }
            }
            if (regla.has("actualizarBinds")) {
                for (JsonNode bind : regla.get("actualizarBinds")) {
                    Map<String, Object> change = new LinkedHashMap<>();
                    change.put("type", "update-bind");
                    change.put("bindId", bind.path("bindId").asText());
                    if (bind.path("removeRelevant").asBoolean(false)) {
                        change.put("removeRelevant", true);
                    }
                    cambios.add(change);
                    campos.add(bind.path("bindId").asText());
                }
            }
            if (cambios.isEmpty()) {
                continue;
            }
            propuesta.setCamposAfectados(campos);
            propuesta.setCambios(cambios);
            propuesta.setTextoInstruccion(buscarTextoAnotacion(anotaciones, regla));
            resultado.add(propuesta);
        }
        return resultado;
    }

    private List<PropuestaCambioXml> resolverDeclaraciones(List<AnotacionInstruccionPdf> anotaciones,
                                                             JsonNode catalogo, String xml,
                                                             Set<String> cambiosVistos) {
        List<PropuestaCambioXml> resultado = new ArrayList<>();
        if (catalogo == null || !catalogo.has("reglasDeclaracion")) {
            return resultado;
        }

        boolean hayEliminar = anotaciones.stream().anyMatch(this::esEliminarDeclaracion);
        if (!hayEliminar) {
            return resultado;
        }

        // Solo reglas del catálogo con campos concretos (p. ej. ROAC, plazos de pago).
        // No inferir una eliminación por cada anotación genérica «eliminar declaración».
        for (JsonNode regla : catalogo.get("reglasDeclaracion")) {
            if (!regla.has("camposEliminar") || regla.get("camposEliminar").isEmpty()) {
                continue;
            }
            PropuestaCambioXml propuesta = construirEliminacionCatalogo(regla, xml);
            if (!propuesta.getCambios().isEmpty()) {
                resultado.add(propuesta);
            }
        }
        return resultado;
    }

    private List<PropuestaCambioXml> resolverAnexos(List<AnotacionInstruccionPdf> anotaciones,
                                                     JsonNode catalogo, String xml,
                                                     Set<String> cambiosVistos) {
        List<PropuestaCambioXml> resultado = new ArrayList<>();
        if (catalogo == null) {
            return resultado;
        }

        String textoAnotaciones = textoConsolidado(anotaciones);

        if (catalogo.has("reglasAnexo")) {
            for (JsonNode regla : catalogo.get("reglasAnexo")) {
                if (regla.has("patrones") && coincideRegla(textoAnotaciones, regla)) {
                    PropuestaCambioXml propuesta = new PropuestaCambioXml();
                    propuesta.setId(regla.path("id").asText());
                    propuesta.setIntencion("sustituir-texto-anexo");
                    propuesta.setDescripcion("Actualizar texto de anexo");
                    propuesta.setConfianza("alta");
                    propuesta.setAplicableAutomaticamente(true);
                    String fieldId = regla.path("fieldId").asText();
                    propuesta.setCamposAfectados(List.of(fieldId));
                    propuesta.setCambios(List.of(Map.of(
                            "type", "update-resource",
                            "fieldId", fieldId,
                            "resourceType", "text",
                            "value", regla.path("nuevoTexto").asText()
                    )));
                    propuesta.setTextoInstruccion(buscarTextoAnotacion(anotaciones, regla));
                    resultado.add(propuesta);
                }

                if (regla.has("camposEliminar") && regla.has("fragmentoTexto")) {
                    String fragmento = normalizar(regla.path("fragmentoTexto").asText());
                    if (!textoAnexoPresente(xml, fragmento)) {
                        continue;
                    }
                    boolean pedido = anotaciones.stream().anyMatch(a -> {
                        String c = normalizar(a.getContenido());
                        return (c.contains("eliminar") && c.contains("documento"))
                                || (a.getSubtipo().toLowerCase(Locale.ROOT).contains("strike")
                                && a.getPagina() == 5);
                    });
                    if (!pedido) {
                        continue;
                    }
                    PropuestaCambioXml propuesta = construirEliminacionAnexo(regla, anotaciones, xml);
                    if (!propuesta.getCambios().isEmpty()) {
                        resultado.add(propuesta);
                    }
                }

                if (regla.has("marcador") && textoAnotaciones.contains(normalizar(regla.path("marcador").asText()))) {
                    PropuestaCambioXml propuesta = new PropuestaCambioXml();
                    propuesta.setId(regla.path("id").asText());
                    propuesta.setIntencion("insertar-anexo");
                    propuesta.setDescripcion(regla.path("descripcion").asText());
                    propuesta.setConfianza("media");
                    propuesta.setAplicableAutomaticamente(regla.path("aplicableAutomaticamente").asBoolean(false));
                    propuesta.setNota("Alta de control nueva requiere copiar estructura desde XML objetivo (PRE)");
                    propuesta.setTextoInstruccion(regla.path("marcador").asText());
                    resultado.add(propuesta);
                }
            }
        }
        return resultado;
    }

    private PropuestaCambioXml construirEliminacionAnexo(JsonNode regla,
                                                          List<AnotacionInstruccionPdf> anotaciones,
                                                          String xml) {
        PropuestaCambioXml propuesta = new PropuestaCambioXml();
        propuesta.setId(regla.path("id").asText());
        propuesta.setIntencion("eliminar-anexo");
        propuesta.setDescripcion("Eliminar documento del apartado de anexos");
        propuesta.setConfianza("alta");
        propuesta.setAplicableAutomaticamente(true);
        propuesta.setTextoInstruccion(buscarTextoAnotacion(anotaciones, regla));

        List<Map<String, Object>> cambios = new ArrayList<>();
        List<String> campos = new ArrayList<>();
        for (JsonNode campo : regla.get("camposEliminar")) {
            String fieldId = campo.asText();
            if (existeControl(xml, fieldId)) {
                cambios.add(Map.of("type", "remove-field", "fieldId", fieldId));
                campos.add(fieldId);
            }
        }
        propuesta.setCamposAfectados(campos);
        propuesta.setCambios(cambios);
        return propuesta;
    }

    private List<PropuestaCambioXml> resolverTextos(List<AnotacionInstruccionPdf> anotaciones,
                                                     JsonNode catalogo, String xml,
                                                     Set<String> cambiosVistos) {
        List<PropuestaCambioXml> resultado = new ArrayList<>();
        if (catalogo == null || !catalogo.has("reglasTexto")) {
            return resultado;
        }

        String textoAnotaciones = textoConsolidado(anotaciones);
        for (JsonNode regla : catalogo.get("reglasTexto")) {
            if (regla.has("fragmentoDeteccion")
                    && textoAnotaciones.contains(normalizar(regla.path("fragmentoDeteccion").asText()))) {
                PropuestaCambioXml propuesta = construirInsercionTexto(regla, xml);
                if (propuesta != null) {
                    resultado.add(propuesta);
                }
            } else if (regla.has("marcador")
                    && textoAnotaciones.contains(normalizar(regla.path("marcador").asText()))) {
                PropuestaCambioXml propuesta = construirInsercionTexto(regla, xml);
                if (propuesta != null) {
                    resultado.add(propuesta);
                }
            } else if (regla.has("camposEliminar")) {
                boolean pedido = anotaciones.stream().anyMatch(a ->
                        normalizar(a.getContenido()).contains("eliminar"));
                if (!pedido) {
                    continue;
                }
                PropuestaCambioXml propuesta = new PropuestaCambioXml();
                propuesta.setId(regla.path("id").asText());
                propuesta.setIntencion("eliminar-campo");
                propuesta.setDescripcion(regla.path("descripcion").asText("Eliminar campo"));
                propuesta.setConfianza("media");
                propuesta.setAplicableAutomaticamente(true);
                List<Map<String, Object>> cambios = new ArrayList<>();
                List<String> campos = new ArrayList<>();
                for (JsonNode campo : regla.get("camposEliminar")) {
                    String fieldId = campo.asText();
                    if (existeControl(xml, fieldId)) {
                        cambios.add(Map.of("type", "remove-field", "fieldId", fieldId));
                        campos.add(fieldId);
                    }
                }
                if (!cambios.isEmpty()) {
                    propuesta.setCamposAfectados(campos);
                    propuesta.setCambios(cambios);
                    resultado.add(propuesta);
                }
            } else if (regla.has("fieldId") && regla.has("descripcion")) {
                String fieldId = regla.path("fieldId").asText();
                if (existeControl(xml, fieldId) && textoAnotaciones.contains("eliminar")) {
                    PropuestaCambioXml propuesta = new PropuestaCambioXml();
                    propuesta.setId(regla.path("id").asText());
                    propuesta.setIntencion("eliminar-campo");
                    propuesta.setDescripcion(regla.path("descripcion").asText());
                    propuesta.setConfianza("media");
                    propuesta.setAplicableAutomaticamente(true);
                    propuesta.setCamposAfectados(List.of(fieldId));
                    propuesta.setCambios(List.of(Map.of("type", "remove-field", "fieldId", fieldId)));
                    resultado.add(propuesta);
                }
            }
        }
        return resultado;
    }

    private PropuestaCambioXml construirInsercionTexto(JsonNode regla, String xml) {
        String fieldId = regla.path("fieldId").asText();
        String parrafo = regla.path("parrafoAnadir").asText();
        if (fieldId.isBlank() || parrafo.isBlank()) {
            return null;
        }
        String actual = leerTextoResource(xml, fieldId);
        if (actual != null && normalizar(actual).contains(normalizar(parrafo))) {
            return null;
        }

        String nuevo = actual == null ? parrafo : actual + parrafo;

        PropuestaCambioXml propuesta = new PropuestaCambioXml();
        propuesta.setId(regla.path("id").asText());
        propuesta.setIntencion("insertar-texto");
        propuesta.setDescripcion("Añadir párrafo en " + fieldId);
        propuesta.setConfianza("alta");
        propuesta.setAplicableAutomaticamente(true);
        propuesta.setCamposAfectados(List.of(fieldId));
        propuesta.setCambios(List.of(Map.of(
                "type", "update-resource",
                "fieldId", fieldId,
                "resourceType", "text",
                "value", nuevo
        )));
        return propuesta;
    }

    private List<PropuestaCambioXml> resolverEliminacionesGenericas(List<AnotacionInstruccionPdf> anotaciones,
                                                                     String xml, Set<String> cambiosVistos) {
        List<PropuestaCambioXml> resultado = new ArrayList<>();
        for (AnotacionInstruccionPdf anot : anotaciones) {
            String norm = normalizar(anot.getContenido());
            if (norm.equals("eliminar") || norm.equals("eliminar.")) {
                continue;
            }
            if (norm.contains("eliminar") && norm.contains("apartado") && norm.contains("documento")) {
                continue;
            }
            if (norm.contains("eliminar") && norm.contains("documento") && !norm.contains("declaracion")) {
                String campo = resolverCampoPorProximidadTexto(xml, anot);
                if (campo != null && existeControl(xml, campo)) {
                    PropuestaCambioXml propuesta = new PropuestaCambioXml();
                    propuesta.setId("eliminar-doc-" + campo);
                    propuesta.setIntencion("eliminar-documento");
                    propuesta.setDescripcion("Eliminar documento/anexo señalado en PDF");
                    propuesta.setTextoInstruccion(anot.getContenido());
                    propuesta.setPagina(anot.getPagina());
                    propuesta.setConfianza("media");
                    propuesta.setAplicableAutomaticamente(true);
                    propuesta.setCamposAfectados(List.of(campo));
                    propuesta.setCambios(List.of(Map.of("type", "remove-field", "fieldId", campo)));
                    resultado.add(propuesta);
                }
            }
        }
        return resultado;
    }

    private String resolverCampoPorProximidadTexto(String xml, AnotacionInstruccionPdf anot) {
        Map<String, String> textosAnexo = extraerTextosResources(xml, "anexos-texto");
        String mejor = null;
        float mejorDist = Float.MAX_VALUE;
        for (Map.Entry<String, String> entry : textosAnexo.entrySet()) {
            if (anotacionCercaDeTexto(anot, entry.getValue())) {
                float dist = Math.abs(anot.getPosicionVertical() - 300);
                if (dist < mejorDist) {
                    mejorDist = dist;
                    mejor = entry.getKey().replace("anexos-texto", "anexos-") + "-control";
                    if (!mejor.contains("-control")) {
                        mejor = entry.getKey() + "-control";
                    }
                    String checkbox = entry.getKey().replace("anexos-texto", "anexos-") + "-control";
                    if (existeControl(xml, checkbox)) {
                        mejor = checkbox;
                    }
                }
            }
        }
        return mejor;
    }

    private boolean anotacionCercaDeTexto(AnotacionInstruccionPdf anot, String textoXml) {
        if (textoXml == null || textoXml.isBlank()) {
            return false;
        }
        String limpio = normalizar(desescaparHtml(textoXml));
        if (limpio.length() < 20) {
            return false;
        }
        String fragmento = limpio.substring(0, Math.min(40, limpio.length()));
        String contenido = normalizar(anot.getContenido());
        return contenido.contains(fragmento)
                || (contenido.length() > 25 && limpio.contains(contenido.substring(0, Math.min(25, contenido.length()))));
    }

    private Map<String, String> extraerTextosResources(String xml, String prefijo) {
        Map<String, String> mapa = new LinkedHashMap<>();
        try {
            Document doc = OrbeonXmlUtil.parsear(xml);
            NodeList instances = doc.getElementsByTagNameNS("*", "instance");
            for (int i = 0; i < instances.getLength(); i++) {
                Element inst = (Element) instances.item(i);
                if (!"fr-form-resources".equals(inst.getAttribute("id"))) {
                    continue;
                }
                NodeList all = inst.getElementsByTagNameNS("*", "*");
                for (int j = 0; j < all.getLength(); j++) {
                    if (!(all.item(j) instanceof Element el)) {
                        continue;
                    }
                    String name = el.getLocalName();
                    if (!name.startsWith(prefijo)) {
                        continue;
                    }
                    Element textEl = primerHijo(el, "text");
                    if (textEl != null) {
                        mapa.put(name, textEl.getTextContent());
                    }
                }
            }
        } catch (Exception ignored) {
            // sin resources parseables
        }
        return mapa;
    }

    private String leerTextoResource(String xml, String fieldId) {
        try {
            Document doc = OrbeonXmlUtil.parsear(xml);
            NodeList instances = doc.getElementsByTagNameNS("*", "instance");
            for (int i = 0; i < instances.getLength(); i++) {
                Element inst = (Element) instances.item(i);
                if (!"fr-form-resources".equals(inst.getAttribute("id"))) {
                    continue;
                }
                NodeList all = inst.getElementsByTagNameNS("*", fieldId);
                for (int j = 0; j < all.getLength(); j++) {
                    if (all.item(j) instanceof Element el) {
                        Element textEl = primerHijo(el, "text");
                        if (textEl != null) {
                            return textEl.getTextContent();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private PropuestaCambioXml construirEliminacionCatalogo(JsonNode regla, String xml) {
        PropuestaCambioXml propuesta = new PropuestaCambioXml();
        propuesta.setId(regla.path("id").asText());
        propuesta.setIntencion("eliminar-declaracion");
        propuesta.setDescripcion("Eliminar declaración: " + regla.path("fragmentoTexto").asText(""));
        propuesta.setConfianza("alta");
        propuesta.setAplicableAutomaticamente(true);
        List<Map<String, Object>> cambios = new ArrayList<>();
        List<String> campos = new ArrayList<>();
        for (JsonNode campo : regla.get("camposEliminar")) {
            String fieldId = campo.asText();
            if (existeControl(xml, fieldId)) {
                cambios.add(Map.of("type", "remove-field", "fieldId", fieldId));
                campos.add(fieldId);
            }
        }
        propuesta.setCamposAfectados(campos);
        propuesta.setCambios(cambios);
        return propuesta;
    }

    private boolean esEliminarDeclaracion(AnotacionInstruccionPdf anot) {
        String norm = normalizar(anot.getContenido());
        if (norm.contains("eliminar") && norm.contains("declaracion")) {
            return true;
        }
        return anot.getSubtipo().toLowerCase(Locale.ROOT).contains("strike")
                && anot.getPagina() == 4
                && norm.contains("eliminar");
    }

    private boolean existeControl(String xml, String fieldId) {
        return xml.contains("id=\"" + fieldId + "\"");
    }

    private boolean textoAnexoPresente(String xml, String fragmento) {
        return normalizar(xml).contains(fragmento);
    }

    private JsonNode cargarCatalogo684() {
        try (InputStream in = new ClassPathResource(CATALOGO_684).getInputStream()) {
            return objectMapper.readTree(in);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean coincideRegla(String textoAnotaciones, JsonNode regla) {
        if (!regla.has("patrones")) {
            return false;
        }
        for (JsonNode patron : regla.get("patrones")) {
            if (textoAnotaciones.contains(normalizar(patron.asText()))) {
                return true;
            }
        }
        return false;
    }

    private String buscarTextoAnotacion(List<AnotacionInstruccionPdf> anotaciones, JsonNode regla) {
        if (regla.has("patrones")) {
            for (AnotacionInstruccionPdf anot : anotaciones) {
                String norm = normalizar(anot.getContenido());
                for (JsonNode patron : regla.get("patrones")) {
                    if (norm.contains(normalizar(patron.asText()))) {
                        return anot.getContenido();
                    }
                }
            }
        }
        return null;
    }

    private String textoConsolidado(List<AnotacionInstruccionPdf> anotaciones) {
        StringBuilder sb = new StringBuilder();
        for (AnotacionInstruccionPdf anot : anotaciones) {
            sb.append(normalizar(anot.getContenido())).append(' ');
        }
        return sb.toString().trim();
    }

    private String construirResumen(List<PropuestaCambioXml> propuestas, List<Map<String, Object>> agregados) {
        long automaticas = propuestas.stream().filter(PropuestaCambioXml::isAplicableAutomaticamente).count();
        long manuales = propuestas.size() - automaticas;
        return propuestas.size() + " propuestas interpretadas ("
                + automaticas + " automáticas, " + manuales + " manuales). "
                + agregados.size() + " cambios XML agregados.";
    }

    private Element primerHijo(Element padre, String localName) {
        NodeList children = padre.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && localName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }

    private String desescaparHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replaceAll("<[^>]+>", " ");
    }

    private String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
