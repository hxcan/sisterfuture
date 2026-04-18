// com.stupidbeauty.sisterfuture.tool.EditFileByLineTool.java
package com.stupidbeauty.sisterfuture.tool;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
/**
 * 按行文件编辑工具
 *
 * 功能：
 * - 支持直接传入文件内容进行编辑
 * - 支持传入文件路径读取后编辑
 * - 支持创建新文件（不提供源内容）
 * - 支持输出为文件内容或写入到文件路径
 * - 支持多种行编辑操作：插入、删除、修改、替换
 */
public class EditFileByLineTool implements Tool {
    private final Context context;
    public EditFileByLineTool(Context context) {
        this.context = context;
    }
    @Override
    public String getName() {
        return "editFileByLine";
    }
    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "editFileByLine");
            functionDef.put("description", "按行编辑文件内容或创建新文件。支持插入、删除、修改、替换行操作。");
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();
            // source 参数（可选）
            properties.put("source", new JSONObject()
                .put("type", "string")
                .put("description", "文件内容（直接传入文本）或文件路径。当 inputType='create' 时可不提供"));
            // inputType 参数（必填）
            properties.put("inputType", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray(new String[]{"content", "filepath", "create"}))
                .put("description", "输入类型：'content' (直接内容) | 'filepath' (文件路径) | 'create' (创建新文件)"));
            // outputType 参数（必填）
            properties.put("outputType", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray(new String[]{"content", "filepath"}))
                .put("description", "输出类型：'content' (返回内容) | 'filepath' (写入文件)。当 inputType='create' 时必须为 'filepath'"));
            // outputPath 参数（条件必填）
            properties.put("outputPath", new JSONObject()
                .put("type", "string")
                .put("description", "输出文件路径。当 outputType='filepath' 或 inputType='create' 时必需"));
            // encoding 参数（可选）
            properties.put("encoding", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray(new String[]{"utf-8", "base64"}))
                .put("description", "编码方式，默认 utf-8"));
            // operations 参数（必填）
            properties.put("operations", new JSONObject()
                .put("type", "array")
                .put("description", "要执行的编辑操作列表")
                .put("items", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("type", new JSONObject()
                        .put("type", "string")
                        .put("enum", new JSONArray(new String[]{"insert", "delete", "update", "replace"})))
                    .put("lineNumber", new JSONObject()
                        .put("type", "integer")
                        .put("description", "行号（用于 insert/update 操作），从 1 开始计数"))
                    .put("lines", new JSONObject()
                        .put("type", "array")
                        .put("items", new JSONObject().put("type", "string"))
                        .put("description", "要插入的行内容数组（用于 insert 操作）"))
                    .put("startLine", new JSONObject()
                        .put("type", "integer")
                        .put("description", "起始行号（用于 delete/replace 操作），从 1 开始计数"))
                    .put("endLine", new JSONObject()
                        .put("type", "integer")
                        .put("description", "结束行号（不包含，用于 delete/replace 操作），从 1 开始计数"))
                    .put("newLine", new JSONObject()
                        .put("type", "string")
                        .put("description", "新的行内容（用于 update 操作）"))
                    .put("newLines", new JSONObject()
                        .put("type", "array")
                        .put("items", new JSONObject().put("type", "string"))
                        .put("description", "替换后的行内容数组（用于 replace 操作）"))
                )));
            parameters.put("properties", properties);
            parameters.put("required", new JSONArray(new String[]{"inputType", "outputType", "operations"}));
            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            return new JSONObject();
        }
    }
    @Override
    public boolean shouldInclude() {
        return true;
    }
    @Override
    public JSONObject execute(JSONObject arguments) throws Exception {
        String source = arguments.optString("source", null);
        String inputType = arguments.getString("inputType");
        String outputType = arguments.getString("outputType");
        String outputPath = arguments.optString("outputPath", null);
        String encoding = arguments.optString("encoding", "utf-8");
        JSONArray operations = arguments.getJSONArray("operations");
        List operationLog = new ArrayList<>();
        try {
            // 1. 获取原始内容
            List lines;
            if ("create".equals(inputType)) {
                // 创建新文件模式
                if (outputPath == null || outputPath.isEmpty()) {
                    throw new IllegalArgumentException("创建新文件时必须指定 outputPath 参数");
                }
                if (!"filepath".equals(outputType)) {
                    throw new IllegalArgumentException("创建新文件时 outputType 必须为 'filepath'");
                }
                lines = new ArrayList<>();
                operationLog.add("✓ 创建新文件：" + outputPath);
            } else if ("filepath".equals(inputType)) {
                if (source == null || source.isEmpty()) {
                    throw new IllegalArgumentException("inputType 为 'filepath' 时必须提供 source 参数（文件路径）");
                }
                lines = readFileLines(source, encoding);
                operationLog.add("✓ 读取文件：" + source);
            } else if ("content".equals(inputType)) {
                if (source == null) {
                    throw new IllegalArgumentException("inputType 为 'content' 时必须提供 source 参数（文件内容）");
                }
                lines = splitIntoLines(source);
                operationLog.add("✓ 使用传入的内容");
            } else {
                throw new IllegalArgumentException("无效的 inputType: " + inputType);
            }
            int originalLineCount = lines.size();
            operationLog.add("✓ 原始行数：" + originalLineCount);
            // 2. 执行编辑操作
            for (int i = 0; i < operations.length(); i++) {
                JSONObject op = operations.getJSONObject(i);
                String opType = op.getString("type");
                switch (opType) {
                    case "insert":
                        lines = applyInsert(lines, op);
                        operationLog.add("✓ 插入行 " + op.optInt("lineNumber"));
                        break;
                    case "delete":
                        lines = applyDelete(lines, op);
                        int start = op.optInt("startLine");
                        int end = op.optInt("endLine", lines.size() + 1);
                        operationLog.add("✓ 删除行 " + start + "-" + (end - 1));
                        break;
                    case "update":
                        lines = applyUpdate(lines, op);
                        operationLog.add("✓ 修改行 " + op.optInt("lineNumber"));
                        break;
                    case "replace":
                        lines = applyReplace(lines, op);
                        int rStart = op.optInt("startLine");
                        int rEnd = op.optInt("endLine");
                        operationLog.add("✓ 替换行 " + rStart + "-" + (rEnd - 1));
                        break;
                    default:
                        throw new IllegalArgumentException("未知操作类型: " + opType);
                }
            }
            int finalLineCount = lines.size();
            operationLog.add("✓ 最终行数：" + finalLineCount);
            // 3. 输出结果
            String resultContent = joinLines(lines);
            if ("content".equals(outputType)) {
                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("content", resultContent);
                result.put("line_count", finalLineCount);
                result.put("operation_log", new JSONArray(operationLog));
                return result;
            } else if ("filepath".equals(outputType)) {
                if (outputPath == null || outputPath.isEmpty()) {
                    throw new IllegalArgumentException("outputType 为 'filepath' 时必须指定 outputPath 参数");
                }
                writeFileContent(outputPath, resultContent, encoding);
                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("output_path", outputPath);
                result.put("line_count", finalLineCount);
                result.put("operation_log", new JSONArray(operationLog));
                return result;
            } else {
                throw new IllegalArgumentException("无效的 outputType: " + outputType);
            }
        } catch (Exception e) {
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", e.getMessage());
            errorResult.put("operation_log", new JSONArray(operationLog));
            return errorResult;
        }
    }
    /**
     * 将字符串分割成行数组
     */
    private List splitIntoLines(String content) {
        List lines = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return lines;
        }
        String[] arr = content.split("\n", -1);
        for (String line : arr) {
            lines.add(line);
        }
        return lines;
    }
    /**
     * 将行数组连接成字符串
     */
    private String joinLines(List lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i));
            if (i < lines.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
    /**
     * 应用插入操作
     * 行号从 1 开始计数
     */
    private List applyInsert(List lines, JSONObject op) throws Exception {
        int lineNumber = op.getInt("lineNumber");
        JSONArray newLines = op.getJSONArray("lines");
        // 行号从 1 开始，转换为内部索引（从 0 开始）
        int targetIndex = lineNumber - 1;
        // 边界处理：如果行号大于总行数，插入到末尾；如果小于 1，插入到开头
        int insertPosition = Math.max(0, Math.min(targetIndex, lines.size()));
        for (int i = 0; i < newLines.length(); i++) {
            lines.add(insertPosition + i, newLines.getString(i));
        }
        return lines;
    }
    /**
     * 应用删除操作
     * 行号从 1 开始计数
     */
    private List applyDelete(List lines, JSONObject op) throws Exception {
        int startLine = op.getInt("startLine");
        int endLine = op.optInt("endLine", lines.size() + 1);
        // 行号从 1 开始，转换为内部索引（从 0 开始）
        int startIndex = startLine - 1;
        int endIndex = endLine - 1;
        // 边界处理
        int start = Math.max(0, startIndex);
        int end = Math.min(endIndex, lines.size());
        if (start >= end) {
            return lines;
        }
        lines.subList(start, end).clear();
        return lines;
    }
    /**
     * 应用更新操作
     * 行号从 1 开始计数
     */
    private List applyUpdate(List lines, JSONObject op) throws Exception {
        int lineNumber = op.getInt("lineNumber");
        String newLine = op.getString("newLine");
        // 行号从 1 开始，转换为内部索引（从 0 开始）
        int index = lineNumber - 1;
        if (index < 0 || index >= lines.size()) {
            throw new IllegalArgumentException("Invalid line number: " + lineNumber + ". File has " + lines.size() + " lines. Line numbers start from 1.");
        }
        lines.set(index, newLine);
        return lines;
    }
    /**
     * 应用替换操作
     * 行号从 1 开始计数
     */
    private List applyReplace(List lines, JSONObject op) throws Exception {
        int startLine = op.getInt("startLine");
        int endLine = op.getInt("endLine");
        JSONArray newLines = op.getJSONArray("newLines");
        // 行号从 1 开始，转换为内部索引（从 0 开始）
        int startIndex = startLine - 1;
        int endIndex = endLine - 1;
        // 边界处理
        int start = Math.max(0, startIndex);
        int end = Math.min(endIndex, lines.size());
        if (start >= end) {
            throw new IllegalArgumentException("Invalid range: " + startLine + "-" + endLine + ". File has " + lines.size() + " lines. Line numbers start from 1.");
        }
        List replacement = new ArrayList<>();
        for (int i = 0; i < newLines.length(); i++) {
            replacement.add(newLines.getString(i));
        }
        lines.subList(start, end).clear();
        lines.addAll(start, replacement);
        return lines;
    }
    /**
     * 读取文件内容
     */
    private List readFileLines(String path, String encoding) throws IOException {
        List lines = new ArrayList<>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(path));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return lines;
    }
    /**
     * 写入文件内容
     */
    private void writeFileContent(String path, String content, String encoding) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(path);
            writer.write(content);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}