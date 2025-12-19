package com.media.sort.service;

import com.media.sort.model.ProcessingReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Service to generate reports in multiple formats (JSON, HTML, CSV)
 */
@Service
public class ReportGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(ReportGeneratorService.class);
    private final ObjectMapper objectMapper;

    public ReportGeneratorService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Generate JSON report
     */
    public void generateJsonReport(ProcessingReport report, String baseDirectory) throws IOException {
        Path reportsDir = Paths.get(baseDirectory, "reports");
        Files.createDirectories(reportsDir);

        String filename = "report_" + new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date()) + ".json";
        Path reportPath = reportsDir.resolve(filename);

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        logger.info("JSON report saved: {}", reportPath);
    }

    /**
     * Generate HTML report
     */
    public void generateHtmlReport(ProcessingReport report, String baseDirectory) throws IOException {
        Path reportsDir = Paths.get(baseDirectory, "reports");
        Files.createDirectories(reportsDir);

        String filename = "report_" + new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date()) + ".html";
        Path reportPath = reportsDir.resolve(filename);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head>\n");
        html.append("<title>Media Sorting Report</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append("h1 { color: #333; }\n");
        html.append("table { border-collapse: collapse; width: 100%; margin: 20px 0; }\n");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        html.append("th { background-color: #4CAF50; color: white; }\n");
        html.append(".success { color: green; }\n");
        html.append(".error { color: red; }\n");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");

        html.append("<h1>Media Sorting Report</h1>\n");
        html.append("<p>Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
                .append("</p>\n");
        html.append("<p>Duration: ").append(report.getDurationSeconds()).append(" seconds</p>\n");

        // Summary table
        html.append("<h2>Summary</h2>\n");
        html.append("<table>\n");
        html.append("<tr><th>Metric</th><th>Value</th></tr>\n");
        html.append("<tr><td>Total Files</td><td>").append(report.getTotalFilesProcessed()).append("</td></tr>\n");
        html.append("<tr><td class='success'>Successfully Organized</td><td>").append(report.getSuccessfullyOrganized())
                .append("</td></tr>\n");
        html.append("<tr><td class='error'>Errors</td><td>").append(report.getErrorsOccurred()).append("</td></tr>\n");
        html.append("<tr><td>Average Speed</td><td>").append(String.format("%.1f", report.getFilesPerSecond()))
                .append(" files/sec</td></tr>\n");
        html.append("</table>\n");

        // Date sources
        html.append("<h2>Date Sources</h2>\n");
        html.append("<table>\n");
        html.append("<tr><th>Source</th><th>Count</th></tr>\n");
        html.append("<tr><td>GPS</td><td>").append(report.getGpsDateCount()).append("</td></tr>\n");
        html.append("<tr><td>EXIF</td><td>").append(report.getExifDateCount()).append("</td></tr>\n");
        html.append("<tr><td>Filesystem</td><td>").append(report.getFilesystemDateCount()).append("</td></tr>\n");
        html.append("<tr><td>No Date</td><td>").append(report.getNoDateCount()).append("</td></tr>\n");
        html.append("</table>\n");

        html.append("</body>\n</html>");

        Files.writeString(reportPath, html.toString());
        logger.info("HTML report saved: {}", reportPath);
    }

    /**
     * Generate CSV report
     */
    public void generateCsvReport(ProcessingReport report, String baseDirectory) throws IOException {
        Path reportsDir = Paths.get(baseDirectory, "reports");
        Files.createDirectories(reportsDir);

        String filename = "report_" + new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date()) + ".csv";
        Path reportPath = reportsDir.resolve(filename);

        StringBuilder csv = new StringBuilder();
        csv.append("Metric,Value\n");
        csv.append("Total Files,").append(report.getTotalFilesProcessed()).append("\n");
        csv.append("Successfully Organized,").append(report.getSuccessfullyOrganized()).append("\n");
        csv.append("Errors,").append(report.getErrorsOccurred()).append("\n");
        csv.append("GPS Dated,").append(report.getGpsDateCount()).append("\n");
        csv.append("EXIF Dated,").append(report.getExifDateCount()).append("\n");
        csv.append("Filesystem Dated,").append(report.getFilesystemDateCount()).append("\n");
        csv.append("No Date,").append(report.getNoDateCount()).append("\n");
        csv.append("Duration (seconds),").append(report.getDurationSeconds()).append("\n");
        csv.append("Speed (files/sec),").append(String.format("%.2f", report.getFilesPerSecond())).append("\n");

        Files.writeString(reportPath, csv.toString());
        logger.info("CSV report saved: {}", reportPath);
    }
}
