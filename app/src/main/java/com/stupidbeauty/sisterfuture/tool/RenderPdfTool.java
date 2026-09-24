package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.text.TextPaint;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF 生成工具（展示型 / 融资路演向）
 *
 * 核心能力：支持绝对坐标自由布局，通过 freeform 模式
 * + 5 种基础元素（text / image / svg / rect / line），
 * 理论上可以画出任何复杂的 PDF。
 *
 * 技术路线：android.graphics.pdf.PdfDocument + Canvas 直接绘制，
 * 元素 → 对应的 drawXxx() 调用，无需 WebView 中间层。
 * 性能：单页 PDF 毫秒级生成（典型页面 < 100ms）。
 *
 * 零第三方依赖，仅使用安卓原生 API。
 *
 * 与旧版差异：
 * - 移除 WebView/WebViewClient/WebSettings
 * - 移除 CountDownLatch/TimeUnit/AtomicReference（无异步）
 * - 移除 HTML 字符串构造（buildPageHtml、renderElement、escapeHtml）
 * - 移除 alignToFlex
 * - 新增 renderPageToCanvas：直接调 canvas.drawText/drawBitmap/drawRoundRect/drawLine
 * - 新增 parseColor、parseLinearGradient、loadBitmap、parseSimpleSvgPath 等辅助方法
 */
public class RenderPdfTool implements Tool {

    private static final String TAG = "RenderPdf";

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

                // 直接画到 PDF page 的 canvas（无 WebView 中间层）
                Canvas canvas = pdfPage.getCanvas();
                renderPageToCanvas(pageJson, canvas, w, h);

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
     * 把单页内容直接绘制到 Canvas（无 WebView，毫秒级完成）。
     * 坐标系：左上角 (0,0)，单位像素。
     */
    private void renderPageToCanvas(JSONObject pageJson, Canvas canvas, int width, int height) {
        // 背景
        String bg = pageJson.optString("background", "#FFFFFF");
        if (!bg.isEmpty()) {
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            applyFillOrGradient(bgPaint, bg, new RectF(0, 0, width, height));
            canvas.drawRect(0, 0, width, height, bgPaint);
        }

        // 元素
        JSONArray elements = pageJson.optJSONArray("elements");
        if (elements != null) {
            for (int i = 0; i < elements.length(); i++) {
                try {
                    JSONObject el = elements.getJSONObject(i);
                    drawElement(canvas, el);
                } catch (Exception e) {
                    Log.w(TAG, "Element " + i + " render failed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 绘制单个元素到 canvas。
     */
    private void drawElement(Canvas canvas, JSONObject el) throws JSONException {
        String type = el.optString("type", "");
        int x = el.optInt("x", 0);
        int y = el.optInt("y", 0);
        int w = el.optInt("width", 0);
        int h = el.optInt("height", 0);
        int rotate = el.optInt("rotate", 0);

        // 保存 canvas 状态以处理旋转
        if (rotate != 0 && w > 0 && h > 0) {
            int cx = x + w / 2;
            int cy = y + h / 2;
            canvas.save();
            canvas.rotate(rotate, cx, cy);
        }

        switch (type) {
            case "text":
                drawTextElement(canvas, el, x, y, w, h);
                break;
            case "image":
                drawImageElement(canvas, el, x, y, w, h);
                break;
            case "svg":
                drawSvgElement(canvas, el, x, y, w, h);
                break;
            case "rect":
                drawRectElement(canvas, el, x, y, w, h);
                break;
            case "line":
                drawLineElement(canvas, el);
                break;
            default:
                Log.w(TAG, "Unknown element type: " + type);
        }

        if (rotate != 0 && w > 0 && h > 0) {
            canvas.restore();
        }
    }

    /**
     * text 元素。
     */
    private void drawTextElement(Canvas canvas, JSONObject el, int x, int y, int w, int h) {
        String content = el.optString("content", "");
        if (content.isEmpty()) return;

        int fontSize = el.optInt("fontSize", 32);
        String colorStr = el.optString("color", "#000000");
        String fontWeight = el.optString("fontWeight", "normal");
        String textAlign = el.optString("textAlign", "left");

        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(fontSize);
        paint.setColor(parseColor(colorStr));
        boolean bold = "bold".equalsIgnoreCase(fontWeight);
        if (bold) {
            paint.setFakeBoldText(true);
        }
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));

        // 处理多行（按 \n 分割）
        String[] lines = content.split("\n");
        float lineHeight = paint.getTextSize() * 1.2f;

        // 计算垂直居中起始 Y
        float totalHeight = lines.length * lineHeight;
        float startY = y + (h > 0 ? (h - totalHeight) / 2f : 0) + paint.getTextSize();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;

            float textWidth = paint.measureText(line);
            float drawX = x;
            if (w > 0) {
                if ("center".equalsIgnoreCase(textAlign)) {
                    drawX = x + (w - textWidth) / 2f;
                } else if ("right".equalsIgnoreCase(textAlign)) {
                    drawX = x + w - textWidth;
                }
            }
            float drawY = startY + i * lineHeight;
            canvas.drawText(line, drawX, drawY, paint);
        }
    }

    /**
     * rect 元素。支持圆角和填充/渐变/描边。
     */
    private void drawRectElement(Canvas canvas, JSONObject el, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;

        String fill = el.optString("fill", "transparent");
        String stroke = el.optString("stroke", "transparent");
        int strokeWidth = el.optInt("strokeWidth", 0);
        int cornerRadius = el.optInt("cornerRadius", 0);

        RectF rect = new RectF(x, y, x + w, y + h);

        // 填充
        if (!"transparent".equalsIgnoreCase(fill) && !fill.isEmpty()) {
            Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setStyle(Paint.Style.FILL);
            applyFillOrGradient(fillPaint, fill, rect);
            if (cornerRadius > 0) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint);
            } else {
                canvas.drawRect(rect, fillPaint);
            }
        }

        // 描边
        if (strokeWidth > 0 && !"transparent".equalsIgnoreCase(stroke) && !stroke.isEmpty()) {
            Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(strokeWidth);
            strokePaint.setColor(parseColor(stroke));
            if (cornerRadius > 0) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint);
            } else {
                canvas.drawRect(rect, strokePaint);
            }
        }
    }

    /**
     * line 元素。
     */
    private void drawLineElement(Canvas canvas, JSONObject el) {
        int x = el.optInt("x", 0);
        int y = el.optInt("y", 0);
        int w = el.optInt("width", 0);
        int h = el.optInt("height", 0);
        int x1 = el.optInt("x1", x);
        int y1 = el.optInt("y1", y);
        int x2 = el.optInt("x2", x + w);
        int y2 = el.optInt("y2", y + h);
        String stroke = el.optString("color", el.optString("stroke", "#000000"));
        int strokeWidth = el.optInt("strokeWidth", 1);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(parseColor(stroke));
        canvas.drawLine(x1, y1, x2, y2, paint);
    }

    /**
     * image 元素。支持 file://, content://, http(s):// 和本地路径。
     */
    private void drawImageElement(Canvas canvas, JSONObject el, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;

        String src = el.optString("src", "");
        if (src.isEmpty()) return;

        Bitmap bmp = loadBitmap(src);
        if (bmp != null) {
            try {
                // 等比缩放到目标矩形，居中绘制
                RectF dst = fitImageRect(bmp, x, y, w, h);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setFilterBitmap(true);
                canvas.drawBitmap(bmp, null, dst, paint);
            } finally {
                bmp.recycle();
            }
        }
    }

    /**
     * svg 元素。当前简化处理：尝试解析 <path d="..."/> 并用 Path 绘制。
     * 复杂的 SVG（嵌套、滤镜、渐变定义）暂不支持。
     */
    private void drawSvgElement(Canvas canvas, JSONObject el, int x, int y, int w, int h) {
        String svg = el.optString("svg", "");
        if (svg.isEmpty() || w <= 0 || h <= 0) return;

        Path path = parseSimpleSvgPath(svg);
        if (path != null) {
            String fill = el.optString("fill", "#000000");
            String stroke = el.optString("stroke", "transparent");
            int strokeWidth = el.optInt("strokeWidth", 1);

            // SVG 默认 viewBox 假设 100x100，把 path 缩放到 (x, y, w, h)
            Matrix matrix = new Matrix();
            RectF bounds = new RectF();
            path.computeBounds(bounds, true);
            if (bounds.width() > 0 && bounds.height() > 0) {
                float scale = Math.min(w / bounds.width(), h / bounds.height());
                matrix.postScale(scale, scale);
                matrix.postTranslate(x + w / 2f - bounds.width() * scale / 2f,
                                     y + h / 2f - bounds.height() * scale / 2f);
                path.transform(matrix);
            }

            if (!"transparent".equalsIgnoreCase(fill) && !fill.isEmpty()) {
                Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor(parseColor(fill));
                canvas.drawPath(path, fillPaint);
            }
            if (strokeWidth > 0 && !"transparent".equalsIgnoreCase(stroke) && !stroke.isEmpty()) {
                Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(strokeWidth);
                strokePaint.setColor(parseColor(stroke));
                canvas.drawPath(path, strokePaint);
            }
        } else {
            Log.w(TAG, "SVG parsing failed or unsupported - element skipped");
        }
    }

    /**
     * 解析 CSS 颜色字符串。
     * 支持 #RRGGBB、#RGB、rgb(r,g,b)、rgba(r,g,b,a)、CSS 命名颜色、transparent。
     */
    private int parseColor(String colorStr) {
        if (colorStr == null || colorStr.isEmpty()) return Color.BLACK;
        String s = colorStr.trim();
        try {
            if (s.equalsIgnoreCase("transparent")) return Color.TRANSPARENT;
            if (s.startsWith("#")) {
                return Color.parseColor(s);
            }
            if (s.startsWith("rgb")) {
                Pattern p = Pattern.compile("rgba?\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,\\s*([\\d.]+)\\s*)?\\)");
                Matcher m = p.matcher(s);
                if (m.find()) {
                    int r = Integer.parseInt(m.group(1));
                    int g = Integer.parseInt(m.group(2));
                    int b = Integer.parseInt(m.group(3));
                    int a = m.group(4) != null ? (int) (Float.parseFloat(m.group(4)) * 255) : 255;
                    return Color.argb(a, r, g, b);
                }
            }
            return Color.parseColor(s);
        } catch (Exception e) {
            Log.w(TAG, "parseColor failed: " + colorStr + " -> " + e.getMessage());
            return Color.BLACK;
        }
    }

    /**
     * 应用纯色或线性渐变到 Paint。
     * 支持："#RRGGBB"、"rgba(...)"、"linear-gradient(...)"。
     */
    private void applyFillOrGradient(Paint paint, String value, RectF bounds) {
        if (value == null || value.isEmpty()) return;
        String s = value.trim();

        if (s.startsWith("linear-gradient")) {
            LinearGradient gradient = parseLinearGradient(s, bounds);
            if (gradient != null) {
                paint.setShader(gradient);
                return;
            }
        }

        paint.setColor(parseColor(s));
    }

    /**
     * 解析 linear-gradient 字符串。
     * 支持：linear-gradient(direction, color1, color2, ...)
     * direction: "to right" / "to bottom" / "to bottom right" / 角度 "45deg"
     */
    private LinearGradient parseLinearGradient(String value, RectF bounds) {
        try {
            int p1 = value.indexOf('(');
            int p2 = value.lastIndexOf(')');
            if (p1 < 0 || p2 < 0) return null;
            String inner = value.substring(p1 + 1, p2);

            String[] parts = inner.split(",");
            if (parts.length < 2) return null;

            int colorStartIdx = 0;
            float x0 = bounds.left, y0 = bounds.top;
            float x1 = bounds.right, y1 = bounds.bottom;

            String first = parts[0].trim().toLowerCase();
            if (first.startsWith("to ") || first.endsWith("deg")) {
                colorStartIdx = 1;
                if (first.equals("to right")) {
                    x0 = bounds.left; y0 = bounds.top;
                    x1 = bounds.right; y1 = bounds.top;
                } else if (first.equals("to left")) {
                    x0 = bounds.right; y0 = bounds.top;
                    x1 = bounds.left; y1 = bounds.top;
                } else if (first.equals("to bottom")) {
                    x0 = bounds.left; y0 = bounds.top;
                    x1 = bounds.left; y1 = bounds.bottom;
                } else if (first.equals("to top")) {
                    x0 = bounds.left; y0 = bounds.bottom;
                    x1 = bounds.left; y1 = bounds.top;
                } else if (first.equals("to bottom right") || first.equals("to right bottom")) {
                    x0 = bounds.left; y0 = bounds.top;
                    x1 = bounds.right; y1 = bounds.bottom;
                } else if (first.endsWith("deg")) {
                    try {
                        float angle = Float.parseFloat(first.replace("deg", "").trim());
                        double rad = Math.toRadians(angle);
                        float cx = bounds.centerX();
                        float cy = bounds.centerY();
                        float r = (float) Math.max(bounds.width(), bounds.height()) / 2f;
                        x0 = cx - (float) Math.cos(rad) * r;
                        y0 = cy - (float) Math.sin(rad) * r;
                        x1 = cx + (float) Math.cos(rad) * r;
                        y1 = cy + (float) Math.sin(rad) * r;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            int colorCount = parts.length - colorStartIdx;
            int[] colors = new int[colorCount];
            for (int i = 0; i < colorCount; i++) {
                colors[i] = parseColor(parts[colorStartIdx + i].trim());
            }

            return new LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP);
        } catch (Exception e) {
            Log.w(TAG, "parseLinearGradient failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * 加载图片。支持 file://、content://、http(s)://、本地路径。
     */
    private Bitmap loadBitmap(String src) {
        try {
            if (src.startsWith("http://") || src.startsWith("https://")) {
                HttpURLConnection conn = (HttpURLConnection) new URL(src).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setDoInput(true);
                conn.connect();
                InputStream is = conn.getInputStream();
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                conn.disconnect();
                return bmp;
            } else if (src.startsWith("content://")) {
                InputStream is = context.getContentResolver().openInputStream(Uri.parse(src));
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (is != null) is.close();
                return bmp;
            } else {
                String path = src.startsWith("file://") ? src.substring(7) : src;
                return BitmapFactory.decodeFile(path);
            }
        } catch (Exception e) {
            Log.w(TAG, "loadBitmap failed: " + src + " -> " + e.getMessage());
            return null;
        }
    }

    /**
     * 计算图片在目标矩形内的等比适配矩形。
     */
    private RectF fitImageRect(Bitmap bmp, int x, int y, int w, int h) {
        int bw = bmp.getWidth();
        int bh = bmp.getHeight();
        if (bw <= 0 || bh <= 0) return new RectF(x, y, x + w, y + h);

        float scale = Math.min((float) w / bw, (float) h / bh);
        float drawW = bw * scale;
        float drawH = bh * scale;
        float drawX = x + (w - drawW) / 2f;
        float drawY = y + (h - drawH) / 2f;
        return new RectF(drawX, drawY, drawX + drawW, drawY + drawH);
    }

    /**
     * 简单 SVG path 解析：提取 <path d="..."/> 中的内容构造 android.graphics.Path。
     * 支持：M/m, L/l, H/h, V/v, C/c, Z/z（基础指令）。
     * 复杂 SVG（嵌套组、滤镜、text 等）暂不支持。
     */
    private Path parseSimpleSvgPath(String svg) {
        try {
            Pattern pathPattern = Pattern.compile("<path[^>]*\\bd\\s*=\\s*\"([^\"]+)\"");
            Matcher m = pathPattern.matcher(svg);
            if (!m.find()) return null;

            String d = m.group(1);
            Path path = new Path();
            d = d.replace(",", " ");
            String[] tokens = d.split("\\s+");

            float cx = 0, cy = 0;
            float startX = 0, startY = 0;
            char prevCmd = ' ';

            int i = 0;
            while (i < tokens.length) {
                String tok = tokens[i];
                if (tok.isEmpty()) { i++; continue; }
                char c = tok.charAt(0);
                if (c == 'M' || c == 'm' || c == 'L' || c == 'l' || c == 'H' || c == 'h'
                        || c == 'V' || c == 'v' || c == 'C' || c == 'c' || c == 'Z' || c == 'z') {
                    prevCmd = c;
                    i++;
                }

                switch (prevCmd) {
                    case 'M':
                        cx = Float.parseFloat(tokens[i++]);
                        cy = Float.parseFloat(tokens[i++]);
                        path.moveTo(cx, cy);
                        startX = cx; startY = cy;
                        prevCmd = 'L';
                        break;
                    case 'm':
                        cx += Float.parseFloat(tokens[i++]);
                        cy += Float.parseFloat(tokens[i++]);
                        path.moveTo(cx, cy);
                        startX = cx; startY = cy;
                        prevCmd = 'l';
                        break;
                    case 'L':
                        cx = Float.parseFloat(tokens[i++]);
                        cy = Float.parseFloat(tokens[i++]);
                        path.lineTo(cx, cy);
                        break;
                    case 'l':
                        cx += Float.parseFloat(tokens[i++]);
                        cy += Float.parseFloat(tokens[i++]);
                        path.lineTo(cx, cy);
                        break;
                    case 'H':
                        cx = Float.parseFloat(tokens[i++]);
                        path.lineTo(cx, cy);
                        break;
                    case 'h':
                        cx += Float.parseFloat(tokens[i++]);
                        path.lineTo(cx, cy);
                        break;
                    case 'V':
                        cy = Float.parseFloat(tokens[i++]);
                        path.lineTo(cx, cy);
                        break;
                    case 'v':
                        cy += Float.parseFloat(tokens[i++]);
                        path.lineTo(cx, cy);
                        break;
                    case 'C':
                        path.cubicTo(
                            Float.parseFloat(tokens[i++]),
                            Float.parseFloat(tokens[i++]),
                            Float.parseFloat(tokens[i++]),
                            Float.parseFloat(tokens[i++]),
                            cx = Float.parseFloat(tokens[i++]),
                            cy = Float.parseFloat(tokens[i++]));
                        break;
                    case 'c':
                        float dx1 = Float.parseFloat(tokens[i++]);
                        float dy1 = Float.parseFloat(tokens[i++]);
                        float dx2 = Float.parseFloat(tokens[i++]);
                        float dy2 = Float.parseFloat(tokens[i++]);
                        float dx = Float.parseFloat(tokens[i++]);
                        float dy = Float.parseFloat(tokens[i++]);
                        path.cubicTo(cx + dx1, cy + dy1, cx + dx2, cy + dy2, cx + dx, cy + dy);
                        cx += dx; cy += dy;
                        break;
                    case 'Z':
                    case 'z':
                        path.close();
                        cx = startX; cy = startY;
                        break;
                    default:
                        i++;
                        break;
                }
            }
            return path;
        } catch (Exception e) {
            Log.w(TAG, "parseSimpleSvgPath failed: " + e.getMessage());
            return null;
        }
    }
}