package com.orbeon.editor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;

import javax.servlet.MultipartConfigElement;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Servlet REST que expone la API de edición del formulario Orbeon.
 *
 * GET  /api/status        → estado del servidor (fichero cargado, etc.)
 * GET  /api/structure     → estructura completa del formulario en JSON
 * POST /api/load          → cargar XML (multipart o JSON con path)
 * POST /api/modify        → aplicar fichero de modificaciones JSON
 * GET  /api/export        → descargar el XML modificado
 * GET  /api/changelog     → log de cambios aplicados
 */
@MultipartConfig(maxFileSize = 20 * 1024 * 1024, maxRequestSize = 25 * 1024 * 1024)
public class ApiServlet extends HttpServlet {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final OrbeonXmlService service = OrbeonXmlService.getInstance();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // CORS para desarrollo local
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) { resp.setStatus(200); return; }
        super.service(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        if (path == null) path = "/";

        try {
            switch (path) {
                case "/status":    handleStatus(resp);    break;
                case "/structure": handleStructure(resp); break;
                case "/export":    handleExport(req, resp);   break;
                case "/changelog": handleChangelog(resp); break;
                case "/schema":    handleSchema(resp);    break;
                default:
                    sendError(resp, 404, "Endpoint no encontrado: " + path);
            }
        } catch (Exception e) {
            sendError(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        if (path == null) path = "/";

        try {
            switch (path) {
                case "/load":   handleLoad(req, resp);   break;
                case "/modify": handleModify(req, resp); break;
                case "/save":   handleSave(req, resp);   break;
                default:
                    sendError(resp, 404, "Endpoint no encontrado: " + path);
            }
        } catch (Exception e) {
            sendError(resp, 500, "Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Handlers ───────────────────────────────────────────────────────────

    private void handleStatus(HttpServletResponse resp) throws Exception {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("loaded", service.isLoaded());
        status.put("fileName", service.getCurrentFileName());
        status.put("changeCount", service.getChangeLog().size());
        sendJson(resp, 200, status);
    }

    private void handleStructure(HttpServletResponse resp) throws Exception {
        if (!service.isLoaded()) {
            sendError(resp, 400, "No hay XML cargado. Use POST /api/load primero.");
            return;
        }
        Map<String, Object> structure = service.getFormStructure();
        sendJson(resp, 200, structure);
    }

    private void handleLoad(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String contentType = req.getContentType();

        if (contentType != null && contentType.startsWith("multipart/")) {
            // Fichero subido via multipart
            try {
                req.setAttribute("org.eclipse.jetty.multipartConfig",
                    new MultipartConfigElement(System.getProperty("java.io.tmpdir"), 20*1024*1024, 25*1024*1024, 1024*1024));
                Part filePart = req.getPart("file");
                if (filePart == null) { sendError(resp, 400, "No se recibió el fichero (campo 'file')"); return; }

                byte[] bytes = IOUtils.toByteArray(filePart.getInputStream());
                String filename = filePart.getSubmittedFileName();
                service.loadXmlFromBytes(bytes, filename != null ? filename : "formulario.xml");

            } catch (Exception e) {
                sendError(resp, 500, "Error al procesar el fichero: " + e.getMessage());
                return;
            }
        } else {
            // JSON con ruta local
            Map<?, ?> body = MAPPER.readValue(req.getInputStream(), Map.class);
            String filePath = (String) body.get("path");
            if (filePath == null) { sendError(resp, 400, "Falta el campo 'path' en el JSON"); return; }

            File file = new File(filePath);
            if (!file.exists()) { sendError(resp, 400, "Fichero no encontrado: " + filePath); return; }
            service.loadXml(file);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "XML cargado correctamente: " + service.getCurrentFileName());
        sendJson(resp, 200, result);
    }

    private void handleModify(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (!service.isLoaded()) { sendError(resp, 400, "No hay XML cargado"); return; }

        String contentType = req.getContentType();
        byte[] bodyBytes;

        if (contentType != null && contentType.startsWith("multipart/")) {
            // Fichero de modificaciones subido
            req.setAttribute("org.eclipse.jetty.multipartConfig",
                new MultipartConfigElement(System.getProperty("java.io.tmpdir"), 5*1024*1024, 10*1024*1024, 1024*1024));
            Part filePart = req.getPart("file");
            if (filePart == null) { sendError(resp, 400, "No se recibió fichero de modificaciones"); return; }
            bodyBytes = IOUtils.toByteArray(filePart.getInputStream());
        } else {
            bodyBytes = IOUtils.toByteArray(req.getInputStream());
        }

        Map<String, Object> modifications = MAPPER.readValue(bodyBytes, Map.class);
        List<String> applied = service.applyModifications(modifications);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("applied", applied);
        result.put("totalChanges", applied.size());
        sendJson(resp, 200, result);
    }

    private void handleExport(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (!service.isLoaded()) { sendError(resp, 400, "No hay XML cargado"); return; }

        byte[] xmlBytes = service.exportXml();
        String filename = service.getCurrentFileName();
        if (filename == null) filename = "formulario_modificado.xml";
        else if (!filename.contains("_mod")) {
            int dot = filename.lastIndexOf('.');
            filename = (dot > 0 ? filename.substring(0, dot) : filename) + "_modificado.xml";
        }

        resp.setContentType("application/xml; charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        resp.setContentLength(xmlBytes.length);
        resp.getOutputStream().write(xmlBytes);
    }

    private void handleSave(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (!service.isLoaded()) { sendError(resp, 400, "No hay XML cargado"); return; }

        Map<?, ?> body = MAPPER.readValue(req.getInputStream(), Map.class);
        String outputPath = (String) body.get("outputPath");
        if (outputPath == null) { sendError(resp, 400, "Falta 'outputPath'"); return; }

        service.saveXml(new File(outputPath));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("savedTo", outputPath);
        sendJson(resp, 200, result);
    }

    private void handleChangelog(HttpServletResponse resp) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changes", service.getChangeLog());
        sendJson(resp, 200, result);
    }

    private void handleSchema(HttpServletResponse resp) throws Exception {
        // Devuelve el esquema del fichero de modificaciones
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("description", "Esquema del fichero de modificaciones Orbeon");
        schema.put("format", "JSON");

        List<Map<String, Object>> examples = new ArrayList<>();

        Map<String, Object> ex1 = new LinkedHashMap<>();
        ex1.put("type", "update-label");
        ex1.put("fieldId", "personaFisica-nombre");
        ex1.put("label", "Nombre completo");
        examples.add(ex1);

        Map<String, Object> ex2 = new LinkedHashMap<>();
        ex2.put("type", "update-hint");
        ex2.put("fieldId", "representante-nif");
        ex2.put("hint", "Introduzca el NIF sin espacios");
        examples.add(ex2);

        Map<String, Object> ex3 = new LinkedHashMap<>();
        ex3.put("type", "hide-section");
        ex3.put("sectionId", "datosEcono-section");
        examples.add(ex3);

        Map<String, Object> ex4 = new LinkedHashMap<>();
        ex4.put("type", "update-image");
        ex4.put("imageTag", "iapa-img");
        ex4.put("filename", "nuevo_logo.png");
        ex4.put("mediatype", "image/png");
        ex4.put("src", "/fr/service/persistence/crud/orbeon/builder/data/nuevo_logo.bin");
        examples.add(ex4);

        Map<String, Object> ex5 = new LinkedHashMap<>();
        ex5.put("type", "update-bind");
        ex5.put("bindId", "datosBancarios-iban-bind");
        Map<String, String> attribs = new LinkedHashMap<>();
        attribs.put("required", "false()");
        ex5.put("attributes", attribs);
        examples.add(ex5);

        Map<String, Object> fullExample = new LinkedHashMap<>();
        fullExample.put("changes", examples);
        schema.put("exampleFile", fullExample);

        List<String> types = Arrays.asList(
            "update-label     → Cambia el label de un campo (fieldId, label)",
            "update-hint      → Cambia el hint de un campo (fieldId, hint)",
            "update-text      → Cambia texto de un elemento (elementId, value)",
            "update-image     → Cambia una imagen (imageTag, filename, mediatype, src)",
            "hide-section     → Oculta una sección (sectionId)",
            "show-section     → Muestra una sección (sectionId)",
            "update-resource  → Actualiza resource (fieldId, resourceType, value)",
            "update-bind      → Modifica atributos de un bind (bindId, attributes{})",
            "remove-field     → Elimina un campo del view (fieldId)"
        );
        schema.put("availableTypes", types);
        sendJson(resp, 200, schema);
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    private void sendJson(HttpServletResponse resp, int status, Object data) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=UTF-8");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(resp.getWriter(), data);
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", true);
        error.put("message", message);
        sendJson(resp, status, error);
    }
}
