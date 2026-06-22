package com.orbeon.editor.service;

import com.orbeon.editor.model.AnalisisCalculadoras;
import com.orbeon.editor.model.CalculadoraFormulario;
import com.orbeon.editor.model.RecursoFormulario;
import com.orbeon.editor.util.OrbeonResourceParser;
import com.orbeon.editor.util.OrbeonXmlUtil;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analiza campos calculados Orbeon ({@code xf:bind @calculate}).
 */
@Service
public class OrbeonCalculatorService {

    private static final Pattern REFERENCIA_BIND = Pattern.compile("\\$([a-zA-Z0-9_.-]+)");
    private static final Pattern RUTA_ABSOLUTA = Pattern.compile("(/form/[a-zA-Z0-9_./-]+)");
    private static final Pattern URL_DOC = Pattern.compile("doc\\(concat\\('([^']+)'");

    private static final Map<String, String> GLOSARIO = Map.ofEntries(
            Map.entry("documentoIdent-nifSol", "NIF del solicitante"),
            Map.entry("documentoIdent-tipodoc", "Tipo de documento (dni / nie / cif)"),
            Map.entry("documentoIdent-autonomo", "Checkbox «Tiene condición de autónomo»"),
            Map.entry("tipoSolicitante", "Tipo de solicitante (1 autónomo, 2 PF, 3 empresa)"),
            Map.entry("otrosDatosEmpresa-vinculadas", "Checkbox «Empresas vinculadas»"),
            Map.entry("control-spj", "SPJ (1.ª letra CIF = E o H)"),
            Map.entry("conRepresentante", "Actuación mediante representante"),
            Map.entry("notifica-opcion", "Modo de notificación (electronica / papel)"),
            Map.entry("notificacion-destinatario", "Destinatario notificación electrónica"),
            Map.entry("opcionImprimir", "Opción imprimir / firmar en papel"),
            Map.entry("provincializador", "Código provincia para centro gestor"),
            Map.entry("empresa-provincia", "Provincia de la empresa"),
            Map.entry("centroTrabajo-provinciaCL", "Provincia en repetición centros de trabajo"),
            Map.entry("datosEcono-numTrabajadores", "Número de trabajadores (datos económicos)"),
            Map.entry("vinculadas-numeroTrabajadores", "Número de trabajadores (vinculadas)"),
            Map.entry("vinculadas-nif", "NIF empresa vinculada"),
            Map.entry("representante-nif", "NIF del representante"),
            Map.entry("notificacion-nif", "NIF destinatario notificación"),
            Map.entry("autonomo-cnae", "Código CNAE autónomo"),
            Map.entry("empresa-cnae", "Código CNAE empresa"),
            Map.entry("autonomo-iae", "Código IAE autónomo"),
            Map.entry("autonomo-seccionIae", "Sección IAE autónomo"),
            Map.entry("permisosConsultaSufo-sufoid1", "Permiso consulta SUFO (representante)"),
            Map.entry("permisosConsultaSufo-sufoid26", "Permiso consulta SUFO (Seg. Social)")
    );

    public AnalisisCalculadoras analizar(String xml) {
        try {
            Document doc = OrbeonXmlUtil.parsear(xml);
            XPath xpath = XPathFactory.newInstance().newXPath();
            Map<String, RecursoFormulario> resources = OrbeonResourceParser.extraerRecursos(doc, xpath);
            Map<String, String> controlPorBind = construirMapaControlPorBind(doc, xpath);

            Set<String> fuentesUsadas = new LinkedHashSet<>();
            List<CalculadoraFormulario> elementos = new ArrayList<>();

            NodeList binds = (NodeList) xpath.evaluate(
                    "//*[local-name()='bind' and @id and @calculate]",
                    doc,
                    XPathConstants.NODESET
            );

            for (int i = 0; i < binds.getLength(); i++) {
                Element bind = (Element) binds.item(i);
                String bindId = bind.getAttribute("id");
                if (bindId.startsWith("validation-")) {
                    continue;
                }
                String calculate = bind.getAttribute("calculate");
                if (calculate.isBlank()) {
                    continue;
                }

                CalculadoraFormulario calc = new CalculadoraFormulario();
                calc.setBindId(bindId);
                calc.setRef(bind.getAttribute("ref"));
                calc.setNombre(bind.getAttribute("name"));
                calc.setExpresionCalculate(calculate);
                calc.setTipoCalculo(clasificarTipo(calculate));
                calc.setSoloLectura(esSoloLectura(bind.getAttribute("readonly")));
                calc.setControlId(controlPorBind.get(bindId));

                String claveRecurso = claveRecursoDesdeBind(bindId, calc.getRef());
                RecursoFormulario res = resources.get(claveRecurso);
                if (res != null && res.getLabel() != null && !res.getLabel().isBlank()) {
                    calc.setLabel(res.getLabel());
                } else {
                    calc.setLabel(formatearTitulo(claveRecurso));
                }

                List<String> fuentes = extraerFuentes(calculate);
                calc.setFuentesDatos(fuentes);
                List<String> desc = new ArrayList<>();
                for (String fuente : fuentes) {
                    fuentesUsadas.add(fuente);
                    desc.add(describirFuente(fuente));
                }
                calc.setDescripcionesFuentes(desc);
                calc.setUrlsExternas(extraerUrlsExternas(calculate));

                elementos.add(calc);
            }

            AnalisisCalculadoras analisis = new AnalisisCalculadoras();
            analisis.setElementos(elementos);
            analisis.setTotal(elementos.size());
            analisis.setTotalConApiExterna((int) elementos.stream()
                    .filter(c -> !c.getUrlsExternas().isEmpty()).count());
            analisis.setTotalSoloLectura((int) elementos.stream()
                    .filter(CalculadoraFormulario::isSoloLectura).count());

            Map<String, String> glosario = new LinkedHashMap<>();
            for (String ref : fuentesUsadas) {
                glosario.put(ref, describirFuente(ref));
            }
            analisis.setGlosarioFuentes(glosario);
            return analisis;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al analizar calculadoras: " + e.getMessage(), e);
        }
    }

    public String obtenerCalculateActual(String xml, String bindId) {
        try {
            Document doc = OrbeonXmlUtil.parsear(xml);
            Element bind = OrbeonXmlUtil.buscarPorId(doc, bindId);
            if (bind == null) {
                throw new IllegalArgumentException("Bind no encontrado: " + bindId);
            }
            return bind.hasAttribute("calculate") ? bind.getAttribute("calculate") : "";
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private Map<String, String> construirMapaControlPorBind(Document doc, XPath xpath) throws Exception {
        Map<String, String> mapa = new LinkedHashMap<>();
        NodeList controles = (NodeList) xpath.evaluate(
                "//*[local-name()='view']//*[@bind and @id]",
                doc,
                XPathConstants.NODESET
        );
        for (int i = 0; i < controles.getLength(); i++) {
            Element control = (Element) controles.item(i);
            String bind = control.getAttribute("bind");
            if (!bind.isBlank()) {
                mapa.putIfAbsent(bind, control.getAttribute("id"));
            }
        }
        return mapa;
    }

    private String claveRecursoDesdeBind(String bindId, String ref) {
        if (ref != null && !ref.isBlank()) {
            return ref;
        }
        if (bindId.endsWith("-bind")) {
            return bindId.substring(0, bindId.length() - 5);
        }
        return bindId;
    }

    private String formatearTitulo(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return key.replace("-bind", "").replace(".", " · ").replace("-", " ");
    }

    private boolean esSoloLectura(String readonly) {
        if (readonly == null || readonly.isBlank()) {
            return false;
        }
        String norm = readonly.replaceAll("\\s+", "");
        return "true()".equals(norm) || "true".equalsIgnoreCase(norm);
    }

    List<String> extraerFuentes(String expresion) {
        List<String> fuentes = new ArrayList<>();
        if (expresion == null || expresion.isBlank()) {
            return fuentes;
        }
        Set<String> vistos = new LinkedHashSet<>();

        Matcher mBind = REFERENCIA_BIND.matcher(expresion);
        while (mBind.find()) {
            String ref = mBind.group(1);
            if (!vistos.contains(ref)) {
                vistos.add(ref);
                fuentes.add(ref);
            }
        }

        Matcher mRuta = RUTA_ABSOLUTA.matcher(expresion);
        while (mRuta.find()) {
            String ruta = mRuta.group(1);
            if (!vistos.contains(ruta)) {
                vistos.add(ruta);
                fuentes.add(ruta);
            }
        }

        if (expresion.contains("string(.)") || expresion.matches("(?s).*\\b\\.\\s*$")
                || expresion.contains(" upper-case(.)") || expresion.contains("upper-case(.)")) {
            if (!vistos.contains(".")) {
                fuentes.add(0, ".");
            }
        }

        return fuentes;
    }

    List<String> extraerUrlsExternas(String expresion) {
        List<String> urls = new ArrayList<>();
        if (expresion == null) {
            return urls;
        }
        Matcher m = URL_DOC.matcher(expresion);
        while (m.find()) {
            urls.add(m.group(1));
        }
        if (expresion.contains("https://") || expresion.contains("http://")) {
            Pattern urlCompleta = Pattern.compile("(https?://[^'\"\\s)]+)");
            Matcher mu = urlCompleta.matcher(expresion);
            while (mu.find()) {
                String url = mu.group(1);
                if (!urls.contains(url)) {
                    urls.add(url);
                }
            }
        }
        return urls;
    }

    String clasificarTipo(String calculate) {
        if (calculate == null || calculate.isBlank()) {
            return "otro";
        }
        String norm = calculate.replaceAll("\\s+", " ").trim();
        if (norm.contains("doc(concat")) {
            return "api-externa";
        }
        if (norm.matches("(?s).*\\$provincializador.*") || norm.contains("provincializador")) {
            if (norm.contains("GERENCIA") || norm.contains("00015")) {
                return "centro-directivo";
            }
        }
        if (norm.startsWith("count (") || norm.startsWith("count(") || norm.startsWith("max (")) {
            return "contador";
        }
        if (norm.contains("matches($id") && norm.contains("dni")) {
            return "inferir-documento";
        }
        if (norm.contains("replace(upper-case") || norm.contains("xxf:trim")) {
            return "normalizar";
        }
        if (norm.contains("$documentoIdent-autonomo")) {
            return "vaciar-autonomo";
        }
        if (norm.contains("$otrosDatosEmpresa-vinculadas")) {
            return "vaciar-vinculadas";
        }
        if (norm.contains("numTrabajadores") && norm.contains("then '")) {
            return "tamano-empresa";
        }
        if (norm.contains("then ('') else .") || norm.contains("then '' else")) {
            return "vaciar-condicional";
        }
        if (norm.equals("$empresa-provincia") || norm.equals("/form/destinatario/centroGestor")) {
            return "copia-campo";
        }
        return "otro";
    }

    private String describirFuente(String fuente) {
        if (".".equals(fuente)) {
            return "Valor del propio campo (nodo actual)";
        }
        if (fuente.startsWith("/form/")) {
            return "Ruta instancia: " + fuente;
        }
        return GLOSARIO.getOrDefault(fuente, "Variable: $" + fuente);
    }
}
