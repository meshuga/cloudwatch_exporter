package io.prometheus.cloudwatch.servlet;

import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HomePageServlet extends HttpServlet {

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html");
        resp.getWriter().print("<html>\n"
                + "<head><title>CloudWatch Exporter</title></head>\n"
                + "<body>\n"
                + "<h1>CloudWatch Exporter</h1>\n"
                + "<p><a href=\"/metrics\">Metrics</a></p>\n"
                + "</body>\n"
                + "</html>");
    }
}