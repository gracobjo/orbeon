package com.orbeon.editor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbeon.editor.model.ResultadoCumplimentacion;
import com.orbeon.editor.util.OrbeonXmlUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cumplimenta nodos de {@code fr-form-instance} y recalcula campos derivados para vista PDF.
 */
@Service
public class OrbeonInstanceService {

    private static final Pattern CIF = Pattern.compile("^([ABEH]\\d{8}|[PQS]\\d{7}[A-J]|[CDFGJNRUVW]\\d{7}[0-9A-J])$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DNI = Pattern.compile("^\\d{8}[TRWAGMYFPDXBNJZSQVHLCKE]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NIE = Pattern.compile("^[XYZ]\\d{7}[TRWAGMYFPDXBNJZSQVHLCKE]$", Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> CENTRO_GESTOR_POR_PROVINCIA = Map.of(
            "05", "GERENCIA PROVINCIAL DEL SERVICIO PÚBLICO DE EMPLEO DE ÁVILA",
            "09", "GERENCIA PROVINCIAL DEL SERVICIO PÚBLICO DE EMPLEO DE BURGOS",
            "24", "GERENCIA PROVINCIAL DEL SERVICIO PÚBLICO DE EMPLEO DE LEÓN",
            "34", "GERENCIA PROVINCIAL DEL SERVICIO PÚBLICO DE EMPLEO DE PALENCIA",
            "37", "GERENCIA PROVINCIAL DEL SERVICIO PÚBLICO DE EMPLEO DE SALAMANCA",
            "40", "GERENCIA PROVINCIAL DEL SERVICIO PÚBLICO DE EMPLEO DE SEGOVIA",
            "42", "GERENCIA PROVINCIAL DEL SERVICIO PÚBLICO DE EMPLEO DE SORIA",
            "47", "GERENCIA PROVINCIAL DEL SERVICIO PÚBLICO DE EMPLEO DE VALLADOLID",
            "49", "GERENCIA PROVINCIAL DEL SERVICIO PÚBLICO DE EMPLEO DE ZAMORA"
    );

    private static final Map<String, String> CODIGO_CENTRO_POR_PROVINCIA = Map.of(
            "05", "00015214", "09", "00015234", "24", "00015262", "34", "00015293",
            "37", "00015315", "40", "00015342", "42", "00015361", "47", "00015378", "49", "00015409"
    );

    private final ObjectMapper objectMapper;

    public OrbeonInstanceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ResultadoCumplimentacion aplicarPreset(String xml, String preset) {
        try (InputStream in = new ClassPathResource("datos/instancia-ejemplo-" + preset + ".json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            Map<String, String> valores = leerMapa(root.get("valores"));
            Map<String, String> etiquetas = leerMapa(root.get("etiquetas"));
            return aplicarValores(xml, valores, etiquetas);
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo cargar el preset de instancia '" + preset + "': " + e.getMessage(), e);
        }
    }

    public ResultadoCumplimentacion aplicarValores(String xml, Map<String, String> valores,
                                                    Map<String, String> etiquetas) {
        if (valores == null || valores.isEmpty()) {
            ResultadoCumplimentacion vacio = new ResultadoCumplimentacion();
            vacio.setXml(xml);
            vacio.setEtiquetas(etiquetas != null ? etiquetas : Map.of());
            return vacio;
        }
        try {
            Map<String, String> datos = new LinkedHashMap<>(valores);
            recalcularCamposDerivados(datos);

            Document doc = OrbeonXmlUtil.parsear(xml);
            Element instancia = buscarInstancia(doc);
            if (instancia == null) {
                throw new IllegalArgumentException("No se encontró fr-form-instance en el XML");
            }

            int aplicados = 0;
            for (Map.Entry<String, String> e : datos.entrySet()) {
                if (establecerValorHoja(instancia, e.getKey(), e.getValue())) {
                    aplicados++;
                }
            }

            ResultadoCumplimentacion resultado = new ResultadoCumplimentacion();
            resultado.setXml(OrbeonXmlUtil.serializar(doc));
            resultado.setEtiquetas(etiquetas != null ? etiquetas : Map.of());
            resultado.setCamposAplicados(aplicados);
            return resultado;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al cumplimentar instancia: " + e.getMessage(), e);
        }
    }

    /**
     * Recalcula tipodoc, tipo solicitante, provincializador y centro gestor si faltan.
     */
    void recalcularCamposDerivados(Map<String, String> datos) {
        String nif = datos.getOrDefault("documentoIdent-nifSol", "").trim().toUpperCase();
        if (!nif.isBlank()) {
            if (!datos.containsKey("documentoIdent-tipodoc") || datos.get("documentoIdent-tipodoc").isBlank()) {
                datos.put("documentoIdent-tipodoc", inferirTipoDocumento(nif));
            }
            if (!datos.containsKey("documentoIdent-nifLetra") || datos.get("documentoIdent-nifLetra").isBlank()) {
                datos.put("documentoIdent-nifLetra", nif.substring(0, 1));
            }
        }

        String tipodoc = datos.getOrDefault("documentoIdent-tipodoc", "");
        if (!datos.containsKey("tipoSolicitante") || datos.get("tipoSolicitante").isBlank()) {
            if ("cif".equalsIgnoreCase(tipodoc)) {
                datos.put("tipoSolicitante", "3");
            } else if ("true".equalsIgnoreCase(datos.get("documentoIdent-autonomo"))) {
                datos.put("tipoSolicitante", "1");
            } else if ("dni".equalsIgnoreCase(tipodoc) || "nie".equalsIgnoreCase(tipodoc)) {
                datos.put("tipoSolicitante", "2");
            }
        }

        if (!datos.containsKey("otrosDatosEmpresa-tipoEmpresaAuto") || datos.get("otrosDatosEmpresa-tipoEmpresaAuto").isBlank()) {
            if (!nif.isBlank() && "cif".equalsIgnoreCase(tipodoc)) {
                datos.put("otrosDatosEmpresa-tipoEmpresaAuto", nif.substring(0, 1));
            }
        }

        String provincia = datos.getOrDefault("empresa-provincia",
                datos.getOrDefault("personaFisica-provincia", datos.getOrDefault("provincializador", "")));
        if (!provincia.isBlank()) {
            String prov = provincia.length() > 2 ? provincia : provincia;
            if (!datos.containsKey("provincializador") || datos.get("provincializador").isBlank()) {
                datos.put("provincializador", prov.length() >= 2 ? prov.substring(0, 2) : prov);
            }
            String codProv = datos.get("provincializador");
            if (codProv != null && CENTRO_GESTOR_POR_PROVINCIA.containsKey(codProv)) {
                datos.putIfAbsent("centroGestor", CENTRO_GESTOR_POR_PROVINCIA.get(codProv));
                datos.putIfAbsent("centroDirectivo.descripcion", CENTRO_GESTOR_POR_PROVINCIA.get(codProv));
                datos.putIfAbsent("centroDirectivo.codigo", CODIGO_CENTRO_POR_PROVINCIA.get(codProv));
            }
        }
    }

    private String inferirTipoDocumento(String nif) {
        if (CIF.matcher(nif).matches()) {
            return "cif";
        }
        if (NIE.matcher(nif).matches()) {
            return "nie";
        }
        if (DNI.matcher(nif).matches()) {
            return "dni";
        }
        return "";
    }

    private boolean establecerValorHoja(Element instancia, String nombreLocal, String valor) {
        NodeList nodos = instancia.getElementsByTagNameNS("*", nombreLocal);
        for (int i = 0; i < nodos.getLength(); i++) {
            if (!(nodos.item(i) instanceof Element el)) {
                continue;
            }
            if (tieneHijosElemento(el)) {
                continue;
            }
            el.setTextContent(valor != null ? valor : "");
            return true;
        }
        return false;
    }

    private boolean tieneHijosElemento(Element el) {
        NodeList hijos = el.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            if (hijos.item(i) instanceof Element) {
                return true;
            }
        }
        return false;
    }

    private Element buscarInstancia(Document doc) {
        NodeList instancias = doc.getElementsByTagNameNS("*", "instance");
        for (int i = 0; i < instancias.getLength(); i++) {
            if (instancias.item(i) instanceof Element el
                    && "fr-form-instance".equals(el.getAttribute("id"))) {
                return el;
            }
        }
        return null;
    }

    private Map<String, String> leerMapa(JsonNode node) {
        Map<String, String> mapa = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return mapa;
        }
        node.fields().forEachRemaining(e -> mapa.put(e.getKey(), e.getValue().asText("")));
        return mapa;
    }
}
