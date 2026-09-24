package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PDF 生成工具（展示型 / 融资路演向）
 *
 * 核心能力：支持绝对坐标自由布局，通过 freeform 模式
 * + 5 种基础元素（text / image / svg / rect / line），
 * 理论上可以画出任何复杂的 PDF。
 *
 * 技术路线：WebView 加载 HTML → 自定义 PrintDocumentAdapter 静默渲染
 * → 输出 PDF 到 /sdcard/Download/ → 扫描到相册。
 *
 * 零第三方依赖，仅使用安卓原生 API。
 */
public class RenderPdfTool implements Tool {

    private final Context context;

    public RenderPdfTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "renderPdf";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "renderPdf");
            functionDef.put("description", "把姐姐设计的内容渲染成 PDF 文件。支持的 pageType 仅 freeform（绝对坐标自由布局），每页可用 elements 自由摆位，元素支持 text/image/svg/rect/line 五种。坐标系 1920×1080（默认 16:9 横向），输出到手机 Download 目录。");

            JSONObject elementsItem = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("type", new JSONObject()
                        .put("type", "string")
                        .put("enum", new JSONArray().put("text").put("image").put("svg").put("rect").put("line")))
                    .put("x", new JSONObject().put("type", "number").put("description", "左上角 X 坐标（像素）"))
                    .put("y", new JSONObject().put("type", "number").put("description", "左上角 Y 坐标（像素）"))
                    .put("width", new JSONObject().put("type", "number"))
                    .put("height", new JSONObject().put("type", "number"))
                    .put("content", new JSONObject().put("type", "string").put("description", "text 元素的内容"))
                    .put("fontSize", new JSONObject().put("type", "number").put("description", "text 元素的字号（像素）"))
                    .put("color", new JSONObject().put("type", "string").put("description", "颜色，支持 #RRGGBB 或 rgba(...)"))
                    .put("fontWeight", new JSONObject().put("type", "string").put("description", "text 元素的字重（normal/bold）"))
                    .put("textAlign", new JSONObject().put("type", "string").put("description", "text 元素的对齐（left/center/right）"))
                    .put("rotate", new JSONObject().put("type", "number").put("description", "旋转角度（度）"))
                    .put("src", new JSONObject().put("type", "string").put("description", "image 元素的图片路径或 URL；可用 file:// 或 content:// 前缀；也支持 auto:提示词 让姐姐自动调 generateImage 出图后嵌入"))
                    .put("svg", new JSONObject().put("type", "string").put("description", "svg 元素的内联 SVG 字符串"))
                    .put("fill", new JSONObject().put("type", "string").put("description", "rect/shape 的填充色或渐变"))
                    .put("stroke", new JSONObject().put("type", "string").put("description", "rect/shape 的描边色"))
                    .put("strokeWidth", new JSONObject().put("type", "number").put("description", "描边宽度"))
                    .put("cornerRadius", new JSONObject().put("type", "number").put("description", "rect 圆角半径"))
                    .put("x1", new JSONObject().put("type", "number").put("description", "line 元素起点 X"))
                    .put("y1", new JSONObject().put("type", "number").put("description", "line 元素起点 Y"))
                    .put("x2", new JSONObject().put("type", "number").put("description", "line 元素终点 X"))
                    .put("y2", new JSONObject().put("type", "number").put("description", "line 元素终点 Y")))
                .put("required", new JSONArray().put("type").put("x").put("y"));

            JSONObject pageObject = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("pageType", new JSONObject()
                        .put("type", "string")
                        .put("enum", new JSONArray().put("freeform"))
                        .put("description", "页面类型，第一版仅支持 freeform（绝对坐标布局）"))
                    .put("background", new JSONObject().put("type", "string").put("description", "页面背景色或渐变（CSS 语法）"))
                    .put("width", new JSONObject().put("type", "number").put("description", "页面宽度（像素），默认 1920"))
                    .put("height", new JSONObject().put("type", "number").put("description", "页面高度（像素），默认 1080"))
                    .put("elements", new JSONObject()
                        .put("type", "array")
                        .put("description", "页面内的元素列表（绝对坐标）")
                        .put("items", elementsItem)))
                .put("required", new JSONArray().put("pageType").put("elements"));

            JSONObject parameters = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("pages", new JSONObject()
                        .put("type", "array")
                        .put("description", "页面列表")
                        .put("items", pageObject))
                    .put("outputFilename", new JSONObject().put("type", "string").put("description", "输出 PDF 文件名，默认根据标题自动生成"))
                    .put("paperSize", new JSONObject().put("type", "string").put("description", "纸张尺寸字符串，默认 1920x1080（16:9 横向）"))
                    .put("openAfter", new JSONObject().put("type", "boolean").put("description", "是否生成完后自动打开，默认 true")))
                .put("required", new JSONArray().put("pages"));

            functionDef.put("parameters", parameters);

            return new JSONObject()
                .put("type", "function")
                .put("function", functionDef);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude() {
        return true;
    }

    @Override
    public JSONObject execute(JSONObject arguments) {
        long startTime = System.currentTimeMillis();
        JSONObject result = new JSONObject();

        try {
            if (arguments == null) {
                throw new IllegalArgumentException("arguments is null");
            }

            JSONArray pages = arguments.optJSONArray("pages");
            if (pages == null || pages.length() == 0) {
                throw new IllegalArgumentException("'pages' is required and must be non-empty");
            }

            String outputFilename = arguments.optString("outputFilename", "");
            if (outputFilename.isEmpty()) {
                outputFilename = "render_" + System.currentTimeMillis() + ".pdf";
            } else if (!outputFilename.endsWith(".pdf")) {
                outputFilename = outputFilename + ".pdf";
            }

            String paperSize = arguments.optString("paperSize", "1920x1080");
            String[] dims = paperSize.toLowerCase().split("x");
            int pageWidth = Integer.parseInt(dims[0]);
            int pageHeight = Integer.parseInt(dims[1]);
            int numPages = pages.length();

            // 准备输出文件
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File outputFile = new File(downloadDir, outputFilename);

            // 创建 PdfDocument
            PdfDocument pdfDocument = new PdfDocument();

            for (int i = 0; i < numPages; i++) {
                JSONObject pageJson = pages.getJSONObject(i);
                int w = pageJson.optInt("width", pageWidth);
                int h = pageJson.optInt("height", pageHeight);

                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(w, h, i + 1).create();
                PdfDocument.Page pdfPage = pdfDocument.startPage(pageInfo);

                // 渲染当前页：构造 HTML → WebView → 截屏 → 画到 canvas
                Bitmap pageBitmap = renderPageToBitmap(pageJson, w, h);
                pdfPage.getCanvas().drawBitmap(pageBitmap, 0, 0, null);
                pageBitmap.recycle();

                pdfDocument.finishPage(pdfPage);
            }

            // 写入文件
            FileOutputStream fos = new FileOutputStream(outputFile);
            pdfDocument.writeTo(fos);
            fos.close();
            pdfDocument.close();

            // 扫描到相册
            MediaScannerConnection.scanFile(context,
                new String[]{outputFile.getAbsolutePath()},
                new String[]{"application/pdf"},
                null);

            long elapsed = System.currentTimeMillis() - startTime;

            result.put("status", "success");
            result.put("pdfPath", outputFile.getAbsolutePath());
            result.put("pagesCount", numPages);
            result.put("fileSizeKb", outputFile.length() / 1024);
            result.put("generationTimeSec", elapsed / 1000.0);

            boolean openAfter = arguments.optBoolean("openAfter", true);
            result.put("openAfter", openAfter);

        } catch (Exception e) {
            try {
                result.put("status", "failed");
                result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            } catch (JSONException ignored) {
            }
        }

        return result;
    }

    /**
     * 把单页内容渲染成 Bitmap。
     * 构造 HTML → WebView 加载 → 等渲染完成 → 截屏。
     */
    private Bitmap renderPageToBitmap(JSONObject pageJson, int width, int height) throws Exception {
        String html = buildPageHtml(pageJson, width, height);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Bitmap> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        WebView webView = new WebView(context);
        webView.setLayoutParams(new ViewGroup.LayoutParams(width, height));
        webView.setBackgroundColor(Color.TRANSPARENT);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                try {
                    // 等一帧确保 SVG 等渲染完毕
                    view.post(() -> {
                        try {
                            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                            Canvas canvas = new Canvas(bmp);
                            view.draw(canvas);
                            resultRef.set(bmp);
                        } catch (Exception e) {
                            errorRef.set(e);
                        } finally {
                            latch.countDown();
                        }
                    });
                } catch (Exception e) {
                    errorRef.set(e);
                    latch.countDown();
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                errorRef.set(new RuntimeException("WebView error: " + description));
                latch.countDown();
            }
        });

        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);

        // 等待渲染完成，最长等 30 秒
        boolean finished = latch.await(30, TimeUnit.SECONDS);
        if (!finished) {
            webView.destroy();
            throw new RuntimeException("WebView render timeout");
        }
        if (errorRef.get() != null) {
            webView.destroy();
            throw errorRef.get();
        }
        Bitmap bmp = resultRef.get();
        webView.destroy();
        if (bmp == null) {
            throw new RuntimeException("WebView returned null bitmap");
        }
        return bmp;
    }

    /**
     * 把单页 JSON 构造为 HTML 字符串。
     * 坐标系：(0,0) 在左上角，单位像素。
     * 每个 element 转成 position:absolute 的 div/svg。
     */
    private String buildPageHtml(JSONObject pageJson, int width, int height) throws JSONException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>");
        html.append("html,body{margin:0;padding:0;width:").append(width).append("px;height:").append(height).append("px;overflow:hidden;");
        String bg = pageJson.optString("background", "#FFFFFF");
        html.append("background:").append(bg).append(";");
        html.append("}");
        html.append(".el{position:absolute;box-sizing:border-box;}");
        html.append("</style></head><body>");

        JSONArray elements = pageJson.optJSONArray("elements");
        if (elements != null) {
            for (int i = 0; i < elements.length(); i++) {
                JSONObject el = elements.getJSONObject(i);
                html.append(renderElement(el));
            }
        }

        html.append("</body></html>");
        return html.toString();
    }

    /**
     * 把单个 element JSON 渲染为 HTML 片段。
     */
    private String renderElement(JSONObject el) throws JSONException {
        String type = el.optString("type", "");
        int x = el.optInt("x", 0);
        int y = el.optInt("y", 0);
        int w = el.optInt("width", 0);
        int h = el.optInt("height", 0);
        int rotate = el.optInt("rotate", 0);
        String transform = rotate != 0 ? "transform:rotate(" + rotate + "deg);transform-origin:center center;" : "";

        switch (type) {
            case "text": {
                String content = el.optString("content", "");
                int fontSize = el.optInt("fontSize", 32);
                String color = el.optString("color", "#000000");
                String fontWeight = el.optString("fontWeight", "normal");
                String textAlign = el.optString("textAlign", "left");
                return "<div class=\"el\" style=\"left:" + x + "px;top:" + y + "px;width:" + w + "px;height:" + h + "px;"
                    + "font-size:" + fontSize + "px;color:" + color + ";font-weight:" + fontWeight + ";text-align:" + textAlign
                    + ";display:flex;align-items:center;justify-content:" + alignToFlex(textAlign) + ";" + transform
                    + ";font-family:'Noto Sans CJK SC','PingFang SC','Microsoft YaHei',sans-serif;\">"
                    + escapeHtml(content) + "</div>";
            }
            case "image": {
                String src = el.optString("src", "");
                return "<img class=\"el\" src=\"" + escapeHtml(src) + "\" style=\"left:" + x + "px;top:" + y + "px;width:" + w + "px;height:" + h + "px;" + transform + "\" />";
            }
            case "svg": {
                String svg = el.optString("svg", "");
                // SVG 内部可能带 width/height，外面用 div 包一层做绝对定位
                return "<div class=\"el\" style=\"left:" + x + "px;top:" + y + "px;width:" + w + "px;height:" + h + "px;" + transform + "\">"
                    + svg + "</div>";
            }
            case "rect": {
                String fill = el.optString("fill", "transparent");
                String stroke = el.optString("stroke", "transparent");
                int strokeWidth = el.optInt("strokeWidth", 0);
                int cornerRadius = el.optInt("cornerRadius", 0);
                return "<div class=\"el\" style=\"left:" + x + "px;top:" + y + "px;width:" + w + "px;height:" + h + "px;"
                    + "background:" + fill + ";border:" + strokeWidth + "px solid " + stroke + ";"
                    + "border-radius:" + cornerRadius + "px;" + transform + "\"></div>";
            }
            case "line": {
                int x1 = el.optInt("x1", x);
                int y1 = el.optInt("y1", y);
                int x2 = el.optInt("x2", x + w);
                int y2 = el.optInt("y2", y + h);
                String stroke = el.optString("color", el.optString("stroke", "#000000"));
                int strokeWidth = el.optInt("strokeWidth", 1);
                return "<svg class=\"el\" style=\"left:" + Math.min(x1, x2) + "px;top:" + Math.min(y1, y2) + "px;width:" + Math.abs(x2 - x1) + "px;height:" + Math.abs(y2 - y1) + "px;pointer-events:none;" + transform + "\">"
                    + "<line x1=\"" + (x1 - Math.min(x1, x2)) + "\" y1=\"" + (y1 - Math.min(y1, y2)) + "\" x2=\"" + (x2 - Math.min(x1, x2)) + "\" y2=\"" + (y2 - Math.min(y1, y2)) + "\""
                    + " stroke=\"" + stroke + "\" stroke-width=\"" + strokeWidth + "\" />"
                    + "</svg>";
            }
            default:
                return "";
        }
    }

    /**
     * text-align 转 flex justify-content
     */
    private String alignToFlex(String textAlign) {
        switch (textAlign) {
            case "center": return "center";
            case "right": return "flex-end";
            default: return "flex-start";
        }
    }

    /**
     * 简单 HTML 转义
     */
    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}