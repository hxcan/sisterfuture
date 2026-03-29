// com.stupidbeauty.sisterfuture.tools.EditFileByLineTool.java
package com.stupidbeauty.sisterfuture.tools;

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
import com.stupidbeauty.sisterfuture.tool.Tool;

/**
 * 按行文件编辑工具
 * 
 * 功能：
 * - 支持直接传入文件内容进行编辑
 * - 支持传入文件路径读取后编辑
 * - 支持输出为文件内容或写入到文件路径
 * - 支持多种行编辑操作：插入、删除、修改、替换
 * - 支持创建新文件（当不提供输入内容但提供输出路径时）
 */
public class EditFileByLineTool implements Tool
{
    private final Context context;

    public EditFileByLineTool(Context context)
    {
        this.context = context;
    }

    @Override
    public String getName()
    {
        return "edit_file_by_line";
    }

    @Override
    public JSONObject getDefinition()
    {
        try
        {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "edit_file_by_line");
            functionDef.put("description", "按行编辑文件内容或创建新文件。支持插入、删除、修改、替换行操作。当不提供输入内容但提供输出路径时，将创建新文件。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            
            JSONObject properties = new JSONObject();
            
            // source 参数（可选）
            properties.put("source", new JSONObject()
                .put("type", "string")
                .put("description", "文件内容（直接传入文本）或文件路径。留空且提供 outputPath 时将创建新文件"));
            
            // inputType 参数（可选）
            properties.put("inputType", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray(new String[]{"content", "filepath", "none"}))
                .put("description", "输入类型：'content' (直接内容) | 'filepath' (文件路径) | 'none' (创建新文件，默认)"));
            
            // outputType 参数（必填）
            properties.put("outputType", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray(new String[]{"content", "filepath"}))
                .put("description", "输出类型：'content' (返回内容) | 'filepath' (写入文件)"));
            
            // outputPath 参数（当 outputType='filepath' 时必填）
            properties.put("outputPath", new JSONObject()
                .put("type", "string")
                .put("description", "输出文件路径（当 outputType='filepath' 时必需）。若 inputType='none' 则创建新文件"));
            
            // encoding 参数（可选）
            properties.put("encoding", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray(new String[]{"utf-8", "base64"}))
                .put("description", "编码方式，默认 utf-8"));
            
            // operations 参数（可选）
            properties.put("operations", new JSONObject()
                .put("type", "array")
                .put("description", "要执行的编辑操作列表。创建新文件时可提供初始内容")
                .put("items", new JSONObject()
                    .put("type", "object")
                    .put("properties", new JSONObject()
                        .put("type", new JSONObject()
                            .put("type", "string")
                            .put("enum", new JSONArray(new String[]{"insert", "delete", "update", "replace"})))
                        .put("lineNumber", new JSONObject()
                            .put("type", "integer")
                            .put("description", "行号（用于 insert/update 操作）"))
                        .put("lines", new JSONObject()
                            .put("type", "array")
                            .put("items", new JSONObject().put("type", "string"))
                            .put("description", "要插入的行内容数组（用于 insert 操作）"))
                        .put("startLine", new JSONObject()
                            .put("type", "integer")
                            .put("description", "起始行号（用于 delete/replace 操作）"))
                        .put("endLine", new JSONObject()
                            .put("type", "integer")
                            .put("description", "结束行号（不包含，用于 delete/replace 操作）"))
                        .put("newLine", new JSONObject()
                            .put("type", "string")
                            .put("description", "新的行内容（用于 update 操作）"))
                        .put("newLines", new JSONObject()
                            .put("type", "array")
                            .put("items", new JSONObject().put("type", "string"))
                            .put("description", "替换后的行内容数组（用于 replace 操作）"))
                    )));
            
            parameters.put("properties", properties);
            parameters.put("required", new JSONArray(new String[]{"outputType"}));

            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        }
        catch (Exception e)
        {
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude()
    {
        return true;
    }

    @Override
    public JSONObject execute(JSONObject arguments) throws Exception
    {
        String source = arguments.optString("source", null);
        String inputType = arguments.optString("inputType", "none");
        String outputType = arguments.getString("outputType");
        String outputPath = arguments.optString("outputPath", null);
        String encoding = arguments.optString("encoding", "utf-8");
        JSONArray operations = arguments.optJSONArray("operations");

        List<String> operationLog = new ArrayList<>();

        try
        {
            // 1. 获取原始内容
            List<String> lines = new ArrayList<>();
            
            if ("filepath".equals(inputType) && source != null && !source.isEmpty())
            {
                // 从文件读取
                lines = readFileLines(source, encoding);
                operationLog.add("✓ 读取文件：" + source);
            }
            else if ("content".equals(inputType) && source != null)
            {
                // 使用传入的内容
                lines = splitIntoLines(source);
                operationLog.add("✓ 使用传入的内容");
            }
            else
            {
                // 创建新文件模式
                if ("filepath".equals(outputType) && (outputPath == null || outputPath.isEmpty()))
                {
                    throw new IllegalArgumentException("创建新文件时必须提供 outputPath 参数");
                }
                lines = new ArrayList<>();
                operationLog.add("✓ 创建新文件模式");
            }

            int originalLineCount = lines.size();
            operationLog.add("✓ 原始行数：" + originalLineCount);

            // 2. 执行编辑操作（如果有）
            if (operations != null && operations.length() > 0)
            {
                for (int i = 0; i < operations.length(); i++)
                {
                    JSONObject op = operations.getJSONObject(i);
                    String opType = op.getString("type");

                    switch (opType)
                    {
                        case "insert":
                            lines = applyInsert(lines, op);
                            operationLog.add("✓ 插入 " + op.optJSONArray("lines").length() + " 行到位置 " + op.getInt("lineNumber"));
                            break;
                        case "delete":
                            lines = applyDelete(lines, op);
                            operationLog.add("✓ 删除行 " + op.getInt("startLine") + "-" + (op.has("endLine") ? op.getInt("endLine") : "EOF"));
                            break;
                        case "update":
                            lines = applyUpdate(lines, op);
                            operationLog.add("✓ 更新行 " + op.getInt("lineNumber"));
                            break;
                        case "replace":
                            lines = applyReplace(lines, op);
                            operationLog.add("✓ 替换行 " + op.getInt("startLine") + "-" + op.getInt("endLine") + " 为 " + op.optJSONArray("newLines").length() + " 行");
                            break;
                        default:
                            throw new IllegalArgumentException("未知操作类型：" + opType);
                    }
                }
            }

            int finalLineCount = lines.size();
            String finalContent = joinLines(lines);

            // 3. 输出结果
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("originalLineCount", originalLineCount);
            result.put("finalLineCount", finalLineCount);
            result.put("operationLog", new JSONArray(operationLog));

            if ("filepath".equals(outputType))
            {
                if (outputPath == null || outputPath.isEmpty())
                {
                    throw new IllegalArgumentException("outputPath is required when outputType is 'filepath'");
                }
                
                // 创建父目录（如果不存在）
                File outputFile = new File(outputPath);
                File parentDir = outputFile.getParentFile();
                if (parentDir != null && !parentDir.exists())
                {
                    boolean mkdirsResult = parentDir.mkdirs();
                    if (!mkdirsResult)
                    {
                        throw new IOException("无法创建父目录：" + parentDir.getAbsolutePath());
                    }
                    operationLog.add("✓ 创建父目录：" + parentDir.getAbsolutePath());
                }
                
                writeFileContent(outputPath, finalContent, encoding);
                result.put("outputPath", outputPath);
                operationLog.add("✓ " + (originalLineCount == 0 ? "创建" : "写入") + " 文件：" + outputPath);
            }
            else
            {
                result.put("content", finalContent);
                operationLog.add("✓ 返回编辑后的内容");
            }

            return result;
        }
        catch (Exception e)
        {
            operationLog.add("✗ 错误：" + e.getMessage());
            JSONObject error = new JSONObject();
            error.put("success", false);
            error.put("error", e.getMessage());
            error.put("operationLog", new JSONArray(operationLog));
            return error;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement()
    {
        return "必须在用户明确要求编辑文件或创建新文件时才调用此工具。支持三种输入模式：'content'（直接内容）、'filepath'（文件路径）、'none'（创建新文件，默认）。支持两种输出模式：'content'（返回内容）、'filepath'（写入文件）。操作类型包括：insert（插入行）、delete（删除行）、update（修改单行）、replace（批量替换行）。行号从 0 开始计数。创建新文件时只需提供 outputPath 和 operations（可选），不提供 source 和 inputType。";
    }

    /**
     * 将内容分割成行数组
     */
    private List<String> splitIntoLines(String content)
    {
        List<String> lines = new ArrayList<>();
        if (content == null || content.isEmpty())
        {
            return lines;
        }
        
        String[] arr = content.split("\n", -1);
        for (String line : arr)
        {
            lines.add(line);
        }
        return lines;
    }

    /**
     * 将行数组连接成字符串
     */
    private String joinLines(List<String> lines)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++)
        {
            sb.append(lines.get(i));
            if (i < lines.size() - 1)
            {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 应用插入操作
     */
    private List<String> applyInsert(List<String> lines, JSONObject op) throws Exception
    {
        int lineNumber = op.getInt("lineNumber");
        JSONArray newLines = op.getJSONArray("lines");
        
        int targetLine = Math.max(0, Math.min(lineNumber, lines.size()));
        
        for (int i = 0; i < newLines.length(); i++)
        {
            lines.add(targetLine + i, newLines.getString(i));
        }
        
        return lines;
    }

    /**
     * 应用删除操作
     */
    private List<String> applyDelete(List<String> lines, JSONObject op) throws Exception
    {
        int startLine = op.getInt("startLine");
        int endLine = op.optInt("endLine", lines.size());
        
        int start = Math.max(0, startLine);
        int end = Math.min(endLine, lines.size());
        
        if (start >= end)
        {
            return lines;
        }
        
        lines.subList(start, end).clear();
        return lines;
    }

    /**
     * 应用更新操作
     */
    private List<String> applyUpdate(List<String> lines, JSONObject op) throws Exception
    {
        int lineNumber = op.getInt("lineNumber");
        String newLine = op.getString("newLine");
        
        if (lineNumber < 0 || lineNumber >= lines.size())
        {
            throw new IllegalArgumentException("Invalid line number: " + lineNumber + ". File has " + lines.size() + " lines.");
        }
        
        lines.set(lineNumber, newLine);
        return lines;
    }

    /**
     * 应用替换操作
     */
    private List<String> applyReplace(List<String> lines, JSONObject op) throws Exception
    {
        int startLine = op.getInt("startLine");
        int endLine = op.getInt("endLine");
        JSONArray newLines = op.getJSONArray("newLines");
        
        int start = Math.max(0, startLine);
        int end = Math.min(endLine, lines.size());
        
        if (start >= end)
        {
            throw new IllegalArgumentException("Invalid range: " + start + "-" + end + ". File has " + lines.size() + " lines.");
        }
        
        List<String> replacement = new ArrayList<>();
        for (int i = 0; i < newLines.length(); i++)
        {
            replacement.add(newLines.getString(i));
        }
        
        lines.subList(start, end).clear();
        lines.addAll(start, replacement);
        return lines;
    }

    /**
     * 读取文件行列表
     */
    private List<String> readFileLines(String path, String encoding) throws IOException
    {
        List<String> lines = new ArrayList<>();
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(path));
            String line;
            while ((line = reader.readLine()) != null)
            {
                lines.add(line);
            }
        }
        finally
        {
            if (reader != null)
            {
                reader.close();
            }
        }
        return lines;
    }

    /**
     * 写入文件内容
     */
    private void writeFileContent(String path, String content, String encoding) throws IOException
    {
        FileWriter writer = null;
        try
        {
            writer = new FileWriter(path);
            writer.write(content);
        }
        finally
        {
            if (writer != null)
            {
                writer.close();
            }
        }
    }
}
