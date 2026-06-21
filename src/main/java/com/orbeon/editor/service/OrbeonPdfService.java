package com.orbeon.editor.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.model.SeccionFormulario;
import org.springframework.stereotype.Service;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Genera una vista previa PDF del formulario Orbeon aplicando las reglas de impresión:
 * excluye nodos con class="noprintinpdf" e incluye secciones relevantes para modo PDF.
 */
@Service
public class OrbeonPdfService {

    private static final Set<String> TIPOS_CONTROL = Set.of(
            "input", "select", "select1", "textarea", "upload", "secret",
            "output", "image", "number", "checkbox-input", "explanation",
            "date", "time", "currency", "email", "phone", "static-attachment"
    );

    private static final Set<String> TIPOS_CONTENEDOR = Set.of(
            "section", "grid", "c", "body", "view", "repeat", "iteration", "template"
    );

    private static final Pattern REF_RECURSO = Pattern.compile("\\$form-resources/([^/]+)/");

    private final OrbeonFormService orbeonFormService;

    public OrbeonPdfService(OrbeonFormService orbeonFormService) {
        this.orbeonFormService = orbeonFormService;
    }

    /**
     * Aplica modificaciones al XML y genera el PDF resultante como array de bytes.
     */
    public byte[] generarPdf(String xml, List<ComponenteFormulario> modificaciones) {
        String xmlProcesado = orbeonFormService.aplicarModificacionesDesdeString(xml, modificaciones);
        try {
            org.w3c.dom.Document doc = parsearDocumento(xmlProcesado);
            XPath xpath = XPathFactory.newInstance().newXPath();
            String tituloFormulario = obtenerTituloDocumento(doc, xpath);
            List<SeccionFormulario> secciones = parsearSeccionesPdf(doc, xpath);
            return construirPdf(tituloFormulario, secciones);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al generar el PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Recorre fr:section dentro de fr:view y agrupa controles visibles en PDF.
     * XPath: //*[local-name()='view']//*[local-name()='section' and @id]
     */
    List<SeccionFormulario> parsearSeccionesPdf(org.w3c.dom.Document documento, XPath xpath) throws Exception {
        Map<String, String> bindToRef = construirMapaBindARef(documento, xpath);
        Map<String, String> bindRelevant = construirMapaBindRelevant(documento, xpath);

        NodeList seccionesNodo = (NodeList) xpath.evaluate(
                "//*[local-name()='view']//*[local-name()='section' and @id]",
                documento,
                XPathConstants.NODESET
        );

        List<SeccionFormulario> secciones = new ArrayList<>();

        for (int i = 0; i < seccionesNodo.getLength(); i++) {
            org.w3c.dom.Element seccion = (org.w3c.dom.Element) seccionesNodo.item(i);

            if (tieneClase(seccion, "noprintinpdf") || esAncestroNoPrint(seccion)) {
                continue;
            }

            String bindSeccion = seccion.getAttribute("bind");
            if (esSoloModoWeb(bindSeccion, bindRelevant)) {
                continue;
            }

            String idSeccion = seccion.getAttribute("id");
            String tituloSeccion = resolverTituloSeccion(seccion, documento, xpath);
            SeccionFormulario seccionFormulario = new SeccionFormulario(idSeccion, tituloSeccion);

            Set<String> idsProcesados = new HashSet<>();
            List<org.w3c.dom.Element> controles = listarControlesDescendientes(seccion);

            for (org.w3c.dom.Element control : controles) {
                if (tieneClase(control, "noprintinpdf") || esAncestroNoPrint(control)) {
                    continue;
                }

                String id = control.getAttribute("id");
                if (id.isBlank() || idsProcesados.contains(id)) {
                    continue;
                }

                String tipoLocal = control.getLocalName();
                if (!esControlFormulario(tipoLocal, control)) {
                    continue;
                }

                idsProcesados.add(id);
                ComponenteFormulario componente = construirComponente(
                        control, tipoLocal, documento, xpath, bindToRef
                );
                seccionFormulario.getComponentes().add(componente);
            }

            if (!seccionFormulario.getComponentes().isEmpty()) {
                secciones.add(seccionFormulario);
            }
        }

        return secciones;
    }

    private byte[] construirPdf(String tituloFormulario, List<SeccionFormulario> secciones)
            throws DocumentException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        com.lowagie.text.Document pdf = new com.lowagie.text.Document(PageSize.A4, 40, 40, 50, 50);
        PdfWriter.getInstance(pdf, salida);
        pdf.open();

        Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.DARK_GRAY);
        Font subtituloFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
        Font seccionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(37, 99, 235));
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
        Font hintFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, new Color(180, 120, 0));
        Font valorFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

        Paragraph cabecera = new Paragraph(tituloFormulario, tituloFont);
        cabecera.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
        cabecera.setSpacingAfter(4);
        pdf.add(cabecera);

        Paragraph aviso = new Paragraph(
                "Vista previa PDF generada desde plantilla Orbeon Form Runner (modo impresión)",
                subtituloFont
        );
        aviso.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
        aviso.setSpacingAfter(16);
        pdf.add(aviso);

        for (SeccionFormulario seccion : secciones) {
            if (seccion.getTitulo() != null && !seccion.getTitulo().isBlank()) {
                Paragraph tituloSec = new Paragraph(seccion.getTitulo(), seccionFont);
                tituloSec.setSpacingBefore(10);
                tituloSec.setSpacingAfter(6);
                pdf.add(tituloSec);
            }

            for (ComponenteFormulario comp : seccion.getComponentes()) {
                agregarComponentePdf(pdf, comp, labelFont, hintFont, valorFont, metaFont);
            }
        }

        if (secciones.isEmpty()) {
            pdf.add(new Paragraph("No se detectaron secciones imprimibles en el XML.", valorFont));
        }

        pdf.close();
        return salida.toByteArray();
    }

    private void agregarComponentePdf(com.lowagie.text.Document pdf, ComponenteFormulario comp,
                                      Font labelFont, Font hintFont, Font valorFont, Font metaFont)
            throws DocumentException {
        String tipo = comp.getTipo();

        if ("image".equals(tipo) || "static-attachment".equals(tipo)) {
            PdfPTable tablaImagen = new PdfPTable(1);
            tablaImagen.setWidthPercentage(100);
            tablaImagen.setSpacingBefore(4);
            tablaImagen.setSpacingAfter(6);

            PdfPCell celda = new PdfPCell();
            celda.setBorder(Rectangle.BOX);
            celda.setBorderColor(new Color(200, 200, 200));
            celda.setBackgroundColor(new Color(245, 245, 245));
            celda.setPadding(12);
            celda.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);

            String ruta = comp.getMetadatos() != null ? comp.getMetadatos().getOrDefault("ref", "") : "";
            celda.addElement(new Paragraph("[ LOGO / IMAGEN ]", labelFont));
            if (ruta != null && !ruta.isBlank()) {
                celda.addElement(new Paragraph(ruta, metaFont));
            }
            tablaImagen.addCell(celda);
            pdf.add(tablaImagen);
            return;
        }

        if ("explanation".equals(tipo)) {
            Paragraph explicacion = new Paragraph(comp.getLabel() != null ? comp.getLabel() : "", valorFont);
            explicacion.setSpacingBefore(4);
            explicacion.setSpacingAfter(6);
            explicacion.setIndentationLeft(8);
            pdf.add(explicacion);
            return;
        }

        if ("checkbox-input".equals(tipo)) {
            Paragraph check = new Paragraph();
            check.add(new Chunk("[ ] ", labelFont));
            check.add(new Chunk(comp.getLabel() != null ? comp.getLabel() : comp.getId(), valorFont));
            check.setSpacingBefore(3);
            check.setSpacingAfter(2);
            pdf.add(check);
        } else {
            String etiqueta = comp.getLabel() != null && !comp.getLabel().isBlank()
                    ? comp.getLabel() : comp.getId();
            Paragraph campo = new Paragraph(etiqueta, labelFont);
            campo.setSpacingBefore(4);
            pdf.add(campo);

            PdfPTable linea = new PdfPTable(1);
            linea.setWidthPercentage(100);
            PdfPCell celdaValor = new PdfPCell(new Phrase(
                    obtenerValorEjemplo(comp), valorFont
            ));
            celdaValor.setBorder(Rectangle.BOTTOM);
            celdaValor.setBorderWidthBottom(0.8f);
            celdaValor.setBorderColor(new Color(150, 150, 150));
            celdaValor.setPaddingBottom(6);
            celdaValor.setPaddingTop(2);
            celdaValor.setBackgroundColor(Color.WHITE);
            linea.addCell(celdaValor);
            linea.setSpacingAfter(2);
            pdf.add(linea);
        }

        if (comp.getHint() != null && !comp.getHint().isBlank()) {
            Paragraph hint = new Paragraph("Pista: " + comp.getHint(), hintFont);
            hint.setSpacingAfter(4);
            pdf.add(hint);
        }
    }

    private String obtenerValorEjemplo(ComponenteFormulario comp) {
        if (comp.getMetadatos() != null && comp.getMetadatos().containsKey("valorInstancia")) {
            String valor = comp.getMetadatos().get("valorInstancia");
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        if ("select1".equals(comp.getTipo()) || "select".equals(comp.getTipo())) {
            return "— Seleccionar —";
        }
        return " ";
    }

    private ComponenteFormulario construirComponente(org.w3c.dom.Element control, String tipoLocal,
                                                       org.w3c.dom.Document documento,
                                                       XPath xpath, Map<String, String> bindToRef)
            throws Exception {
        String id = control.getAttribute("id");
        String claveRecurso = extraerClaveRecurso(id, control);
        String label = obtenerTextoRecurso(documento, xpath, claveRecurso, "label");
        String hint = obtenerTextoRecurso(documento, xpath, claveRecurso, "hint");

        ComponenteFormulario componente = new ComponenteFormulario(id, tipoLocal, label, hint);
        Map<String, String> metadatos = new HashMap<>();
        metadatos.put("resourceKey", claveRecurso);

        String bind = control.getAttribute("bind");
        if (!bind.isBlank()) {
            metadatos.put("bind", bind);
            String refInstancia = bindToRef.get(bind);
            if (refInstancia != null) {
                metadatos.put("rutaInstancia", refInstancia);
                metadatos.put("valorInstancia", obtenerValorInstancia(documento, xpath, refInstancia));
            }
        }

        if ("image".equals(tipoLocal) || "static-attachment".equals(tipoLocal)) {
            String ref = control.getAttribute("ref");
            if (ref.isBlank() && !bind.isBlank()) {
                String refBind = bindToRef.get(bind);
                if (refBind != null) {
                    ref = obtenerValorInstancia(documento, xpath, refBind);
                }
            }
            metadatos.put("ref", ref != null ? ref : "");
        }

        componente.setMetadatos(metadatos);
        return componente;
    }

    private String resolverTituloSeccion(org.w3c.dom.Element seccion, org.w3c.dom.Document documento, XPath xpath)
            throws Exception {
        NodeList labels = seccion.getElementsByTagNameNS("*", "label");
        for (int i = 0; i < labels.getLength(); i++) {
            org.w3c.dom.Element labelEl = (org.w3c.dom.Element) labels.item(i);
            if (!"label".equals(labelEl.getLocalName())) {
                continue;
            }
            String ref = labelEl.getAttribute("ref");
            if (ref.isBlank()) {
                continue;
            }
            Matcher matcher = REF_RECURSO.matcher(ref);
            if (matcher.find()) {
                String clave = matcher.group(1);
                String texto = obtenerTextoRecurso(documento, xpath, clave, "label");
                if (!texto.isBlank()) {
                    return texto;
                }
            }
        }
        String id = seccion.getAttribute("id");
        return id.replace("-section", "").replace("-", " ");
    }

    private List<org.w3c.dom.Element> listarControlesDescendientes(org.w3c.dom.Element seccion) {
        List<org.w3c.dom.Element> resultado = new ArrayList<>();
        NodeList todos = seccion.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < todos.getLength(); i++) {
            Node nodo = todos.item(i);
            if (nodo instanceof org.w3c.dom.Element el && el.hasAttribute("id")) {
                resultado.add(el);
            }
        }
        return resultado;
    }

    private boolean tieneClase(org.w3c.dom.Element elemento, String clase) {
        String clases = elemento.getAttribute("class");
        return clases != null && clases.contains(clase);
    }

    private boolean esAncestroNoPrint(org.w3c.dom.Element elemento) {
        Node padre = elemento.getParentNode();
        while (padre instanceof org.w3c.dom.Element) {
            org.w3c.dom.Element elPadre = (org.w3c.dom.Element) padre;
            if ("section".equals(elPadre.getLocalName()) && tieneClase(elPadre, "noprintinpdf")) {
                return true;
            }
            padre = padre.getParentNode();
        }
        return false;
    }

    /**
     * Binds con relevant distinto de modo PDF se ocultan en la salida impresa.
     * Ejemplo web-only: relevant="fr:mode()!='pdf'"
     */
    private boolean esSoloModoWeb(String bindId, Map<String, String> bindRelevant) {
        if (bindId == null || bindId.isBlank()) {
            return false;
        }
        String relevant = bindRelevant.get(bindId);
        if (relevant == null) {
            return false;
        }
        return relevant.contains("!='pdf'") || relevant.contains("!= 'pdf'");
    }

    private String obtenerTituloDocumento(org.w3c.dom.Document documento, XPath xpath) throws Exception {
        Node titulo = (Node) xpath.evaluate("//*[local-name()='title']", documento, XPathConstants.NODE);
        if (titulo != null && !titulo.getTextContent().isBlank()) {
            return titulo.getTextContent().trim();
        }
        return "Formulario Orbeon";
    }

    private org.w3c.dom.Document parsearDocumento(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private boolean esControlFormulario(String tipoLocal, org.w3c.dom.Element control) {
        if (TIPOS_CONTENEDOR.contains(tipoLocal)) {
            return false;
        }
        if (TIPOS_CONTROL.contains(tipoLocal)) {
            return true;
        }
        return control.hasAttribute("bind") && control.hasAttribute("id");
    }

    private String extraerClaveRecurso(String idControl, org.w3c.dom.Element control) {
        if (idControl.endsWith("-control")) {
            return idControl.substring(0, idControl.length() - "-control".length());
        }
        if (control != null) {
            String bind = control.getAttribute("bind");
            if (bind.endsWith("-bind")) {
                return bind.substring(0, bind.length() - "-bind".length());
            }
        }
        return idControl;
    }

    private Map<String, String> construirMapaBindARef(org.w3c.dom.Document documento, XPath xpath)
            throws Exception {
        Map<String, String> mapa = new LinkedHashMap<>();
        NodeList binds = (NodeList) xpath.evaluate(
                "//*[local-name()='bind' and @id and @ref]", documento, XPathConstants.NODESET
        );
        for (int i = 0; i < binds.getLength(); i++) {
            org.w3c.dom.Element bind = (org.w3c.dom.Element) binds.item(i);
            mapa.put(bind.getAttribute("id"), bind.getAttribute("ref"));
        }
        return mapa;
    }

    private Map<String, String> construirMapaBindRelevant(org.w3c.dom.Document documento, XPath xpath)
            throws Exception {
        Map<String, String> mapa = new LinkedHashMap<>();
        NodeList binds = (NodeList) xpath.evaluate(
                "//*[local-name()='bind' and @id and @relevant]", documento, XPathConstants.NODESET
        );
        for (int i = 0; i < binds.getLength(); i++) {
            org.w3c.dom.Element bind = (org.w3c.dom.Element) binds.item(i);
            mapa.put(bind.getAttribute("id"), bind.getAttribute("relevant"));
        }
        return mapa;
    }

    private String obtenerTextoRecurso(org.w3c.dom.Document documento, XPath xpath,
                                       String claveRecurso, String campo) throws Exception {
        String expresion = String.format(
                "//*[local-name()='instance' and @id='fr-form-resources']" +
                        "//*[local-name()='resource']/*[local-name()='%s']/*[local-name()='%s']",
                escaparXPathLiteral(claveRecurso), escaparXPathLiteral(campo)
        );
        Node nodo = (Node) xpath.evaluate(expresion, documento, XPathConstants.NODE);
        return nodo != null ? nodo.getTextContent().trim() : "";
    }

    private String obtenerValorInstancia(org.w3c.dom.Document documento, XPath xpath,
                                           String refRelativo) throws Exception {
        String expresion = String.format(
                "//*[local-name()='instance' and @id='fr-form-instance']//*[local-name()='%s']",
                escaparXPathLiteral(refRelativo)
        );
        Node nodo = (Node) xpath.evaluate(expresion, documento, XPathConstants.NODE);
        return nodo != null ? nodo.getTextContent().trim() : "";
    }

    private String escaparXPathLiteral(String valor) {
        if (valor == null) {
            return "";
        }
        if (!valor.contains("'")) {
            return valor;
        }
        return valor.replace("'", "', \"'\", '");
    }
}
