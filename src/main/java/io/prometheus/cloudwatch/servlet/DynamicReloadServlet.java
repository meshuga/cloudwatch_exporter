package io.prometheus.cloudwatch.servlet;

import io.prometheus.cloudwatch.CloudWatchCollector;

import java.io.IOException;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DynamicReloadServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(CloudWatchCollector.class.getName());
    private CloudWatchCollector collector;

    public DynamicReloadServlet(CloudWatchCollector collector) {
        this.collector = collector;
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(405);
        resp.setContentType("text/plain");
        resp.getWriter().print("Only POST requests allowed");
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        collector.reloadConfig();

        resp.setContentType("text/plain");
        resp.getWriter().print("OK");
    }
}
