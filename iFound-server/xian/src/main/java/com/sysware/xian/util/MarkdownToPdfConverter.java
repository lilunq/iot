package com.sysware.xian.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.WaitUntilState;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * md文档转换为pdf的服务
 *
 * @Author: LiYanRu
 * @date: 2026-07-21
 **/
public class MarkdownToPdfConverter {
    /**
     * 把 Markdown 中的 ```echarts 和 ```table 转为 HTML 可执行片段，其余用 flexmark 渲染
     */
    public static String mdToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        // 先处理 ```echarts 代码块（在 flexmark 解析之前替换，避免被转义）
        markdown = replaceEchartsBlocks(markdown);
        // 再处理 ```table 代码块
        markdown = replaceTableBlocks(markdown);
        // 其余 Markdown 用 flexmark 渲染
        MutableDataSet options = new MutableDataSet();
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        return renderer.render(parser.parse(markdown));
    }

    /**
     * 把 ```echarts { json } ``` 替换为 <div> + <script> 可执行片段
     */
    private static String replaceEchartsBlocks(String md) {
        StringBuilder result = new StringBuilder();
        Pattern pattern = Pattern.compile("```echarts\\s*\\n([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(md);
        int chartIdx = 0;
        while (matcher.find()) {
            String jsonOption = matcher.group(1).trim();
            String chartId = "chart_" + (chartIdx++);
            String htmlBlock = String.format(
                    "<div id=\"%s\" style=\"width:100%%;max-width:700px;height:400px;\"></div>\n"
                            + "<script>\n"
                            + "(function(){\n"
                            + "  var chart = echarts.init(document.getElementById('%s'));\n"
                            + "  chart.setOption(%s);\n"
                            + "})();\n"
                            + "</script>",
                    chartId, chartId, jsonOption
            );
            matcher.appendReplacement(result, Matcher.quoteReplacement(htmlBlock));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 把 ```table { columns, data } ``` 替换为 HTML <table> 表格
     */
    private static String replaceTableBlocks(String md) {
        StringBuilder result = new StringBuilder();
        Pattern pattern = Pattern.compile("```table\\s*\\n([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(md);
        ObjectMapper mapper = new ObjectMapper();
        while (matcher.find()) {
            String jsonStr = matcher.group(1).trim();
            try {
                JsonNode root = mapper.readTree(jsonStr);
                // 解析 columns 和 data
                JsonNode columnsNode = root.get("columns");
                JsonNode dataNode = root.get("data");
                if (columnsNode == null || dataNode == null) {
                    continue;
                }
                // 构造 HTML 表格
                StringBuilder tableHtml = new StringBuilder();
                tableHtml.append("<table>\n<thead>\n<tr>\n");
                for (JsonNode col : columnsNode) {
                    tableHtml.append("<th>").append(escapeHtml(col.asText())).append("</th>\n");
                }
                tableHtml.append("</tr>\n</thead>\n<tbody>\n");
                for (JsonNode row : dataNode) {
                    tableHtml.append("<tr>\n");
                    for (JsonNode cell : row) {
                        tableHtml.append("<td>").append(escapeHtml(cell.asText())).append("</td>\n");
                    }
                    tableHtml.append("</tr>\n");
                }
                tableHtml.append("</tbody>\n</table>");
                matcher.appendReplacement(result, Matcher.quoteReplacement(tableHtml.toString()));
            } catch (Exception e) {
                // JSON 解析失败时跳过该块，保留原文
                System.err.println("表格 JSON 解析失败: " + e.getMessage());
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * HTML 实体转义，防止表格数据中的特殊字符破坏结构
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 构造完整 HTML 页面（嵌入 ECharts CDN + 打印样式）
     */
    public static String wrapFullHtml(String bodyHtml) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <script src="https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js"></script>
                  <style>
                    body {
                      font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
                      padding: 40px 50px;
                      line-height: 1.8;
                      color: #333;
                    }
                    table { border-collapse: collapse; width: 100%%; margin: 16px 0; }
                    th, td { border: 1px solid #ddd; padding: 10px; text-align: left; }
                    th { background: #f5f5f5; }
                    img { max-width: 100%%; }
                  </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(bodyHtml);
    }

    /**
     * 用 Playwright 无头浏览器渲染完整 HTML 并导出为 PDF
     */
    public static void htmlToPdf(String html, Path outputPath) {
        // 1. 确保输出目录存在
        Path parentDir = outputPath.getParent();
        if (parentDir != null) {
            try {
                Files.createDirectories(parentDir);
            } catch (Exception e) {
                throw new RuntimeException("无法创建输出目录: " + parentDir, e);
            }
        }

        try (Playwright playwright = Playwright.create()) {
            // 2. 启动无头 Chromium
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true)
            );
            Page page = browser.newPage();

            // 3. 设置 HTML 内容，等待 ECharts CDN 和所有网络请求完成
            page.setContent(html, new Page.SetContentOptions()
                    .setWaitUntil(WaitUntilState.NETWORKIDLE)
            );

            // 4. 额外等待 2 秒确保所有图表初始化完成
            page.waitForTimeout(2000);

            // 5. 导出 PDF
            page.pdf(new Page.PdfOptions()
                    .setPath(outputPath)
                    .setFormat("A4")
                    .setPrintBackground(true) // 保留图表背景色
                    .setMargin(new Margin()
                            .setTop("20mm")
                            .setBottom("20mm")
                            .setLeft("15mm")
                            .setRight("15mm"))
            );

            browser.close();
        }
    }
}
