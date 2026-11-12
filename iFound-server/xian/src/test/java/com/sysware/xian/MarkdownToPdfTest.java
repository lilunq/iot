package com.sysware.xian;

import com.sysware.xian.service.ReportService;
import com.sysware.xian.util.MarkdownToPdfConverter;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Markdown → HTML（含 ECharts 图表） → PDF 转换工具
 * <p>
 * 使用方式：直接运行 main() 方法，会读取工作目录下的 input/report.md 并输出 output/report.pdf
 */
@SpringBootTest
public class MarkdownToPdfTest {
    @Resource
    private ReportService reportService;

    /**
     * 完整入口：AI 生成 Markdown → 落盘 md → 转 HTML → 导出 PDF
     */
    @Test
    public void tes01() throws IOException {
        Path outputDir = Paths.get("output");
        Path mdPath = outputDir.resolve("report.md");
        Path pdfPath = outputDir.resolve("report.pdf");

        // 1. AI 生成报表 Markdown
        String content = reportService.reportChat("请生成西安东站的情况报表");
        if (content == null) {
            content = "";
        }

        // 2. 把 content 写入 md 文件，
        Files.createDirectories(outputDir);
        Files.writeString(mdPath, content);
        System.out.println("Markdown 已导出: " + mdPath.toAbsolutePath());

        // 3. Markdown → HTML（含图表/表格占位替换）
        String bodyHtml = MarkdownToPdfConverter.mdToHtml(content);

        // 4. 包装为完整 HTML 页面
        String fullHtml = MarkdownToPdfConverter.wrapFullHtml(bodyHtml);

        // 5. HTML → PDF
        MarkdownToPdfConverter.htmlToPdf(fullHtml, pdfPath);
        System.out.println("PDF 生成成功: " + pdfPath.toAbsolutePath());
    }

    @Test
    public void test02() throws IOException {
        // 输入：已有的测试 markdown 文件
        Path inputPath = Paths.get( "src", "test", "resources", "后期如果支持图表.md");
        // 输出 PDF 保存到当前模块根目录下
        Path outputPath = Paths.get("output", "report.pdf");

        // 1. 读取 Markdown 源文件
        if (!Files.exists(inputPath)) {
            System.err.println("错误: 找不到输入文件 " + inputPath.toAbsolutePath());
            System.err.println("请确保 " + inputPath.toAbsolutePath() + " 存在");
            System.exit(1);
        }
        String mdContent = Files.readString(inputPath);

        // 2. Markdown → HTML（含图表/表格占位替换）
        String bodyHtml = MarkdownToPdfConverter.mdToHtml(mdContent);

        // 3. 包装为完整 HTML 页面
        String fullHtml = MarkdownToPdfConverter.wrapFullHtml(bodyHtml);

        // 4. HTML → PDF
        MarkdownToPdfConverter.htmlToPdf(fullHtml, outputPath);



        System.out.println("PDF 生成成功: " + outputPath.toAbsolutePath());

    }
}
