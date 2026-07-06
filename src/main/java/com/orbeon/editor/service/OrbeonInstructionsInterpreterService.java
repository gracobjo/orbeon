package com.orbeon.editor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orbeon.editor.dto.AnalisisInstruccionesResponse;
import com.orbeon.editor.dto.ComparacionInstruccionesResponse;
import com.orbeon.editor.dto.ModificacionResponse;
import com.orbeon.editor.model.AnotacionInstruccionPdf;
import com.orbeon.editor.model.EstructuraFormulario;
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
 * El catálogo de reglas se genera desde el XML cargado (cualquier formulario).
 * Opcionalmente fusiona reglas estáticas del 684 si existen en classpath.
 */
@Service
public class OrbeonInstructionsInterpreterService {

    private static final String CATALOGO_684 = "datos/instrucciones-684-mapeo.json";
    private static final int COINCIDENCIA_MIN = 12;

    private final OrbeonPdfInstructionsService pdfInstructionsService;
    private final OrbeonModificationService modificationService;
    private final OrbeonInstructionsCatalogBuilder catalogBuilder;
    private final OrbeonInstructionsStructureService structureService;
    private final OrbeonStructureService orbeonStructureService;
    private final ObjectMapper objectMapper;

    public OrbeonInstructionsInterpreterService(OrbeonPdfInstructionsService pdfInstructionsService,
                                                 OrbeonModificationService modificationService,
                                                 OrbeonInstructionsCatalogBuilder catalogBuilder,
                                                 OrbeonInstructionsStructureService structureService,
                                                 OrbeonStructureService orbeonStructureService,
                                                 ObjectMapper objectMapper) {
        this.pdfInstructionsService = pdfInstructionsService;
        this.modificationService = modificationService;
        this.catalogBuilder = catalogBuilder;
        this.structureService = structureService;
        this.orbeonStructureService = orbeonStructureService;
        this.objectMapper = objectMapper;
    }

    public AnalisisInstruccionesResponse analizar(byte[] pdfBytes, String nombrePdf, String xml, boolean aplicar) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("El XML del formulario es obligatorio");
        }

        List<AnotacionInstruccionPdf> anotaciones = pdfInstructionsService.extraerAnotaciones(pdfBytes);
        ObjectNode catalogo = catalogBuilder.construirDesdeXml(xml);
        fusionarCatalogoEstatico(catalogo, cargarCatalogo684());

        List<PropuestaCambioXml> propuestas = new ArrayList<>();
        Set<String> cambiosVistos = new HashSet<>();

        propuestas.addAll(resolverApartados(anotaciones, catalogo, xml, cambiosVistos));
        propuestas.addAll(resolverDeclaraciones(anotaciones, catalogo, xml, cambiosVistos));
        propuestas.addAll(resolverAnexos(anotaciones, catalogo, xml, cambiosVistos));
        propuestas.addAll(resolverTextos(anotaciones, catalogo, xml, cambiosVistos));
        propuestas.addAll(resolverSustitucionesGenericas(anotaciones, xml, cambiosVistos));
        propuestas.addAll(resolverInsercionesGenericas(anotaciones, xml, cambiosVistos));
        propuestas.addAll(resolverEliminacionesGenericas(anotaciones, xml, cambiosVistos));
        propuestas = deduplicarPropuestas(propuestas);

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
        resp.setResumen(construirResumen(propuestas, agregados, catalogo));
        resp.setNombreFormulario(catalogo.path("formulario").asText("formulario"));
        resp.setXml(xml);
        try {
            EstructuraFormulario estructura = orbeonStructureService.parsearEstructuraCompleta(xml);
            resp.setEstructura(estructura);
            resp.setEstructuraInstrucciones(structureService.construir(
                    xml, resp.getNombreFormulario(), anotaciones, propuestas));
        } catch (Exception ignored) {
            // estructura opcional si el XML no es parseable
        }

        if (aplicar && !agregados.isEmpty()) {
            ModificacionResponse mod = modificationService.aplicarCambios(xml, agregados);
            resp.setXml(mod.getXml());
            resp.setLogAplicados(mod.getChangeLog());
            try {
                EstructuraFormulario estructura = orbeonStructureService.parsearEstructuraCompleta(mod.getXml());
                resp.setEstructura(estructura);
            } catch (Exception ignored) {
                // mantener estructura previa
            }
        }
        return resp;
    }

    public ComparacionInstruccionesResponse compararPdfs(byte[] pdfBase, String nombreBase,
                                                          byte[] pdfNuevo, String nombreNuevo,
                                                          String xml) {
        AnalisisInstruccionesResponse base = analizar(pdfBase, nombreBase, xml, false);
        AnalisisInstruccionesResponse nuevo = analizar(pdfNuevo, nombreNuevo, xml, false);

        ComparacionInstruccionesResponse resp = new ComparacionInstruccionesResponse();
        resp.setAnalisisBase(base);
        resp.setAnalisisNuevo(nuevo);

        Map<String, AnotacionInstruccionPdf> mapaBase = indexarAnotaciones(base.getAnotaciones());
        Map<String, AnotacionInstruccionPdf> mapaNuevo = indexarAnotaciones(nuevo.getAnotaciones());

        for (Map.Entry<String, AnotacionInstruccionPdf> e : mapaBase.entrySet()) {
            if (mapaNuevo.containsKey(e.getKey())) {
                resp.getAnotacionesComunes().add(e.getValue());
            } else {
                resp.getAnotacionesSoloBase().add(e.getValue());
            }
        }
        for (Map.Entry<String, AnotacionInstruccionPdf> e : mapaNuevo.entrySet()) {
            if (!mapaBase.containsKey(e.getKey())) {
                resp.getAnotacionesSoloNuevo().add(e.getValue());
            }
        }

        Map<String, PropuestaCambioXml> propBase = indexarPropuestas(base.getPropuestas());
        Map<String, PropuestaCambioXml> propNuevo = indexarPropuestas(nuevo.getPropuestas());
        for (Map.Entry<String, PropuestaCambioXml> e : propBase.entrySet()) {
            if (propNuevo.containsKey(e.getKey())) {
                // común por id
            } else {
                resp.getPropuestasSoloBase().add(e.getValue());
            }
        }
        for (Map.Entry<String, PropuestaCambioXml> e : propNuevo.entrySet()) {
            if (!propBase.containsKey(e.getKey())) {
                resp.getPropuestasSoloNuevo().add(e.getValue());
            }
        }

        Set<String> camposBase = camposDePropuestas(base.getPropuestas());
        Set<String> camposNuevo = camposDePropuestas(nuevo.getPropuestas());
        for (String c : camposBase) {
            if (camposNuevo.contains(c)) {
                resp.getCamposComunes().add(c);
            } else {
                resp.getCamposSoloBase().add(c);
            }
        }
        for (String c : camposNuevo) {
            if (!camposBase.contains(c)) {
                resp.getCamposSoloNuevo().add(c);
            }
        }

        resp.setResumen(
                "PDF base: " + base.getNombrePdf() + " (" + base.getTotalAnotaciones() + " anot., "
                        + base.getPropuestas().size() + " prop.) · "
                        + "PDF nuevo: " + nuevo.getNombrePdf() + " (" + nuevo.getTotalAnotaciones() + " anot., "
                        + nuevo.getPropuestas().size() + " prop.) · "
                        + "Anot. solo base: " + resp.getAnotacionesSoloBase().size()
                        + ", solo nuevo: " + resp.getAnotacionesSoloNuevo().size()
                        + ", comunes: " + resp.getAnotacionesComunes().size()
                        + " · Campos solo base: " + resp.getCamposSoloBase().size()
                        + ", solo nuevo: " + resp.getCamposSoloNuevo().size()
                        + ", comunes: " + resp.getCamposComunes().size());
        return resp;
    }

    private Map<String, AnotacionInstruccionPdf> indexarAnotaciones(List<AnotacionInstruccionPdf> anotaciones) {
        Map<String, AnotacionInstruccionPdf> mapa = new LinkedHashMap<>();
        for (AnotacionInstruccionPdf a : anotaciones) {
            mapa.put(claveAnotacion(a), a);
        }
        return mapa;
    }

    private String claveAnotacion(AnotacionInstruccionPdf a) {
        return a.getPagina() + "|" + normalizar(a.getContenido());
    }

    private Map<String, PropuestaCambioXml> indexarPropuestas(List<PropuestaCambioXml> propuestas) {
        Map<String, PropuestaCambioXml> mapa = new LinkedHashMap<>();
        for (PropuestaCambioXml p : propuestas) {
            String id = p.getId() != null ? p.getId() : p.getDescripcion();
            mapa.put(id, p);
        }
        return mapa;
    }

    private Set<String> camposDePropuestas(List<PropuestaCambioXml> propuestas) {
        Set<String> campos = new HashSet<>();
        for (PropuestaCambioXml p : propuestas) {
            if (p.getCamposAfectados() != null) {
                campos.addAll(p.getCamposAfectados());
            }
        }
        return campos;
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
            boolean esEstatica = "estatico".equals(regla.path("origen").asText());
            if (!esEstatica && !anotaciones.stream().anyMatch(this::anotacionSolicitaEliminacionApartado)) {
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

        for (JsonNode regla : catalogo.get("reglasDeclaracion")) {
            if (!regla.has("camposEliminar") || regla.get("camposEliminar").isEmpty()) {
                continue;
            }
            String fragmento = regla.path("fragmentoTexto").asText("");
            if (fragmento.isBlank()) {
                continue;
            }
            boolean esEstatica = "estatico".equals(regla.path("origen").asText());
            if (esEstatica) {
                if (!anotaciones.stream().anyMatch(this::esEliminarDeclaracion)) {
                    continue;
                }
            } else if (!hayAnotacionParaEliminarFragmento(anotaciones, fragmento)) {
                continue;
            }
            PropuestaCambioXml propuesta = construirEliminacionCatalogo(regla, xml, anotaciones);
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
                    if (!hayAnotacionParaEliminarFragmento(anotaciones, regla.path("fragmentoTexto").asText())) {
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
                String fragmento = regla.path("fragmentoDeteccion").asText(
                        regla.path("fragmentoTexto").asText(""));
                if (fragmento.isBlank() || !hayAnotacionParaEliminarFragmento(anotaciones, fragmento)) {
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
                String fragmento = regla.path("fragmentoDeteccion").asText(
                        regla.path("descripcion").asText(""));
                if (existeControl(xml, fieldId)
                        && hayAnotacionParaEliminarFragmento(anotaciones, fragmento)) {
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
            if (anotacionSolicitaEliminacion(anot) || esStrike(anot)) {
                String campo = resolverCampoPorCoincidenciaTexto(xml, anot);
                if (campo != null && existeControl(xml, campo) && cambiosVistos.add("remove-field:" + campo)) {
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

    private String resolverCampoPorCoincidenciaTexto(String xml, AnotacionInstruccionPdf anot) {
        Map<String, String> textos = extraerTodosTextosResources(xml);
        String mejor = null;
        int mejorPuntuacion = 0;
        for (Map.Entry<String, String> entry : textos.entrySet()) {
            if (!anotacionCercaDeTexto(anot, entry.getValue())) {
                continue;
            }
            int score = puntuacionCoincidencia(anot.getContenido(), entry.getValue());
            if (score > mejorPuntuacion) {
                mejorPuntuacion = score;
                mejor = resolverControlParaRecurso(entry.getKey(), xml);
            }
        }
        return mejor;
    }

    private String resolverControlParaRecurso(String resourceId, String xml) {
        List<String> candidatos = List.of(
                resourceId + "-control",
                resourceId.replace("-texto", "-") + "-control"
        );
        for (String c : candidatos) {
            if (existeControl(xml, c)) {
                return c;
            }
        }
        if (resourceId.startsWith("anexos-texto")) {
            String base = resourceId.substring("anexos-texto".length());
            String anexo = "anexos-" + base + "-control";
            if (existeControl(xml, anexo)) {
                return anexo;
            }
        }
        int dash = resourceId.indexOf("-texto");
        if (dash > 0) {
            String sufijo = resourceId.substring(dash + 5);
            String prefijo = resourceId.substring(0, dash);
            String control = prefijo + "-" + sufijo + "-control";
            if (existeControl(xml, control)) {
                return control;
            }
        }
        return null;
    }

    private int puntuacionCoincidencia(String anotacion, String textoXml) {
        String a = normalizar(anotacion);
        String t = normalizar(desescaparHtml(textoXml));
        if (a.isBlank() || t.isBlank()) {
            return 0;
        }
        if (a.contains(t.substring(0, Math.min(30, t.length()))) || t.contains(a.substring(0, Math.min(30, a.length())))) {
            return 100;
        }
        return Math.min(a.length(), t.length());
    }

    @SuppressWarnings("unused")
    private String resolverCampoPorProximidadTexto(String xml, AnotacionInstruccionPdf anot) {
        return resolverCampoPorCoincidenciaTexto(xml, anot);
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

    private Map<String, String> extraerTodosTextosResources(String xml) {
        return extraerTextosResources(xml, null);
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
                    if (prefijo != null && !name.startsWith(prefijo)) {
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

    private PropuestaCambioXml construirEliminacionCatalogo(JsonNode regla, String xml,
                                                           List<AnotacionInstruccionPdf> anotaciones) {
        PropuestaCambioXml propuesta = new PropuestaCambioXml();
        propuesta.setId(regla.path("id").asText());
        propuesta.setIntencion("eliminar-declaracion");
        propuesta.setDescripcion("Eliminar declaración: " + regla.path("fragmentoTexto").asText(""));
        propuesta.setConfianza("alta");
        propuesta.setAplicableAutomaticamente(true);
        propuesta.setTextoInstruccion(buscarTextoAnotacionPorFragmento(anotaciones, regla.path("fragmentoTexto").asText()));
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
        return esStrike(anot) && norm.contains("eliminar");
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

    private String construirResumen(List<PropuestaCambioXml> propuestas,
                                    List<Map<String, Object>> agregados,
                                    JsonNode catalogo) {
        long automaticas = propuestas.stream().filter(PropuestaCambioXml::isAplicableAutomaticamente).count();
        long manuales = propuestas.size() - automaticas;
        String formulario = catalogo != null ? catalogo.path("formulario").asText("formulario") : "formulario";
        return "Formulario «" + formulario + "» (catálogo derivado del XML). "
                + propuestas.size() + " propuestas interpretadas ("
                + automaticas + " automáticas, " + manuales + " manuales). "
                + agregados.size() + " cambios XML agregados.";
    }

    private void fusionarCatalogoEstatico(ObjectNode destino, JsonNode estatico) {
        if (estatico == null || destino == null) {
            return;
        }
        fusionarArrayReglas(destino, estatico, "reglasApartado");
        fusionarArrayReglas(destino, estatico, "reglasDeclaracion");
        fusionarArrayReglas(destino, estatico, "reglasAnexo");
        fusionarArrayReglas(destino, estatico, "reglasTexto");
    }

    private void fusionarArrayReglas(ObjectNode destino, JsonNode estatico, String clave) {
        if (!estatico.has(clave)) {
            return;
        }
        ArrayNode arrayDest = destino.has(clave) ? (ArrayNode) destino.get(clave) : destino.putArray(clave);
        Set<String> ids = new HashSet<>();
        arrayDest.forEach(n -> ids.add(n.path("id").asText()));
        for (JsonNode regla : estatico.get(clave)) {
            String id = regla.path("id").asText();
            if (!id.isBlank() && ids.add(id)) {
                if (regla instanceof ObjectNode obj) {
                    ObjectNode copia = obj.deepCopy();
                    copia.put("origen", "estatico");
                    arrayDest.add(copia);
                } else {
                    arrayDest.add(regla);
                }
            }
        }
    }

    private List<PropuestaCambioXml> deduplicarPropuestas(List<PropuestaCambioXml> propuestas) {
        List<PropuestaCambioXml> unicas = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        for (PropuestaCambioXml p : propuestas) {
            String clave = p.getId() != null ? p.getId() : p.getDescripcion();
            for (Map<String, Object> c : p.getCambios()) {
                clave = clave + "|" + claveCambio(c);
            }
            if (vistas.add(clave)) {
                unicas.add(p);
            }
        }
        return unicas;
    }

    private List<PropuestaCambioXml> resolverSustitucionesGenericas(List<AnotacionInstruccionPdf> anotaciones,
                                                                     String xml, Set<String> cambiosVistos) {
        List<PropuestaCambioXml> resultado = new ArrayList<>();
        Map<String, String> textos = extraerTodosTextosResources(xml);
        for (AnotacionInstruccionPdf anot : anotaciones) {
            String norm = normalizar(anot.getContenido());
            if (!norm.contains("sustituir") && !norm.contains("cambiar por") && !norm.contains("reemplazar")) {
                continue;
            }
            for (Map.Entry<String, String> entry : textos.entrySet()) {
                if (!anotacionCercaDeTexto(anot, entry.getValue())) {
                    continue;
                }
                String nuevoTexto = extraerTextoSustitucion(anot.getContenido());
                if (nuevoTexto == null || nuevoTexto.isBlank()) {
                    continue;
                }
                String fieldId = entry.getKey();
                String clave = "sustituir:" + fieldId;
                if (!cambiosVistos.add(clave)) {
                    continue;
                }
                PropuestaCambioXml propuesta = new PropuestaCambioXml();
                propuesta.setId(clave);
                propuesta.setIntencion("sustituir-texto");
                propuesta.setDescripcion("Sustituir texto en " + fieldId);
                propuesta.setTextoInstruccion(anot.getContenido());
                propuesta.setPagina(anot.getPagina());
                propuesta.setConfianza("media");
                propuesta.setAplicableAutomaticamente(true);
                propuesta.setCamposAfectados(List.of(fieldId));
                propuesta.setCambios(List.of(Map.of(
                        "type", "update-resource",
                        "fieldId", fieldId,
                        "resourceType", "text",
                        "value", nuevoTexto
                )));
                resultado.add(propuesta);
            }
        }
        return resultado;
    }

    private String extraerTextoSustitucion(String contenido) {
        if (contenido == null) {
            return null;
        }
        String lower = contenido.toLowerCase(Locale.ROOT);
        int por = lower.indexOf(" por ");
        if (por >= 0 && por + 4 < contenido.length()) {
            return contenido.substring(por + 4).trim();
        }
        if (contenido.contains("«") && contenido.contains("»")) {
            int ini = contenido.indexOf('«');
            int fin = contenido.indexOf('»', ini + 1);
            if (fin > ini) {
                return contenido.substring(ini + 1, fin).trim();
            }
        }
        if (contenido.contains("<div") || contenido.contains("<p")) {
            return contenido.trim();
        }
        return null;
    }

    private List<PropuestaCambioXml> resolverInsercionesGenericas(List<AnotacionInstruccionPdf> anotaciones,
                                                                     String xml, Set<String> cambiosVistos) {
        List<PropuestaCambioXml> resultado = new ArrayList<>();
        for (AnotacionInstruccionPdf anot : anotaciones) {
            String norm = normalizar(anot.getContenido());
            boolean marcador = norm.contains("insertar") || norm.contains("anadir") || norm.contains("añadir")
                    || norm.matches(".*\\*\\s*\\d+.*") || norm.contains("anexo ");
            if (!marcador) {
                continue;
            }
            String clave = "insertar:" + normalizar(anot.getContenido()).hashCode();
            if (!cambiosVistos.add(clave)) {
                continue;
            }
            PropuestaCambioXml propuesta = new PropuestaCambioXml();
            propuesta.setId(clave);
            propuesta.setIntencion("insertar-campo");
            propuesta.setDescripcion("Alta o inserción indicada en PDF (revisar manualmente)");
            propuesta.setTextoInstruccion(anot.getContenido());
            propuesta.setPagina(anot.getPagina());
            propuesta.setConfianza("media");
            propuesta.setAplicableAutomaticamente(false);
            propuesta.setNota("Copie la estructura del control desde el XML objetivo o Form Builder.");
            resultado.add(propuesta);
        }
        return resultado;
    }

    private boolean hayAnotacionParaEliminarFragmento(List<AnotacionInstruccionPdf> anotaciones, String fragmento) {
        if (fragmento == null || fragmento.isBlank()) {
            return false;
        }
        return anotaciones.stream().anyMatch(a ->
                (anotacionSolicitaEliminacion(a) || esStrike(a))
                        && coincideFragmento(a.getContenido(), fragmento));
    }

    private boolean coincideFragmento(String anotacion, String fragmento) {
        String a = normalizar(anotacion);
        String f = normalizar(desescaparHtml(fragmento));
        if (a.isBlank() || f.isBlank()) {
            return false;
        }
        if (a.contains(f) || f.contains(a)) {
            return Math.min(a.length(), f.length()) >= COINCIDENCIA_MIN;
        }
        String fCorto = f.substring(0, Math.min(f.length(), 50));
        String aCorto = a.substring(0, Math.min(a.length(), 50));
        return a.contains(fCorto) || f.contains(aCorto);
    }

    private boolean anotacionSolicitaEliminacion(AnotacionInstruccionPdf anot) {
        String norm = normalizar(anot.getContenido());
        return norm.contains("eliminar") || norm.contains("quitar") || norm.contains("suprimir")
                || norm.contains("tachar");
    }

    private boolean anotacionSolicitaEliminacionApartado(AnotacionInstruccionPdf anot) {
        String norm = normalizar(anot.getContenido());
        return norm.contains("eliminar") && (norm.contains("apartado") || norm.contains("seccion") || norm.contains("sección"));
    }

    private boolean esStrike(AnotacionInstruccionPdf anot) {
        return anot.getSubtipo() != null
                && anot.getSubtipo().toLowerCase(Locale.ROOT).contains("strike");
    }

    private String buscarTextoAnotacionPorFragmento(List<AnotacionInstruccionPdf> anotaciones, String fragmento) {
        for (AnotacionInstruccionPdf anot : anotaciones) {
            if (coincideFragmento(anot.getContenido(), fragmento)) {
                return anot.getContenido();
            }
        }
        return null;
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
