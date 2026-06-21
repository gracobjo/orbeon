package com.orbeon.editor;

import javax.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Sirve la interfaz HTML embebida directamente desde el JAR.
 * Devuelve el index.html para cualquier ruta que no sea /api/*.
 */
public class StaticHtmlServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI();

        // Servir recursos estáticos embebidos
        if (path.endsWith(".css") || path.endsWith(".js")) {
            InputStream is = getClass().getResourceAsStream("/static" + path);
            if (is != null) {
                resp.setContentType(path.endsWith(".css") ? "text/css" : "application/javascript");
                is.transferTo(resp.getOutputStream());
                return;
            }
        }

        // Para cualquier otra ruta, devolver el index.html embebido
        resp.setContentType("text/html; charset=UTF-8");
        InputStream is = getClass().getResourceAsStream("/static/index.html");
        if (is != null) {
            is.transferTo(resp.getOutputStream());
        } else {
            // Fallback inline si no hay recurso estático
            resp.getWriter().write(getFallbackHtml());
        }
    }

    private String getFallbackHtml() {
        return "<!DOCTYPE html><html><body>" +
               "<h2>Orbeon Editor</h2>" +
               "<p>Coloca el fichero index.html en src/main/resources/static/</p>" +
               "</body></html>";
    }
}
