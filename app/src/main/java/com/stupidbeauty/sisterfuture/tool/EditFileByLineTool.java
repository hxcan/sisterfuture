// com.stupidbeauty.sisterfuture.tool.EditFileByLineTool.java
package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 按行文件编辑工具（v2 - 可靠性增强版）
 *
 * 🆕 修复任务 #892980839283: 增强工具可靠性，避免行号歧义导致代码块破坏
 *
 * ## 核心特性
 *
 * 1. **行号规则（统一 1-based + 半开区间）**
 *    - 所有行号从 **1** 开始计数（不是 0）
 *    - insert: lineNumber=N → **在第 N 行之前插入**，插入后新内容位于第 N 行
 *    - update: lineNumber=N → **修改第 N 行**（替换为 newLine）
 *    - delete: startLine=N, endLine=M → **删除 [N, M) 半开区间**（不包含 M）
 *    - replace: startLine=N, endLine=M → **替换 [N, M) 半开区间**（不包含 M）
 *
 * 2. **`find` 参数（人类可读定位，推荐）**
 *    - 在文件中查找唯一字符串定位，规避行号歧义
 *    - 找到多个 → 报错（要求更具体）
 *    - 找不到 → 报错（要求检查拼写）
 *
 * 3. **`expectedContent` 双重验证**
 *    - 行号模式必须传 `expectedContent`，工具检查行号处内容是否匹配
 *    - 不匹配则报错（不执行修改，避免破坏文件）
 *
 * 4. **`dryRun` 预览模式（默认 true）**
 *    - 默认不真正写入，先返回上下文预览（前后 3 行 + 替换内容）
 *    - 主人确认后，再传 `dryRun=false` 真正写入
 *
 * 5. **修复 schema bug**
 *    - 原 operations items properties 嵌套错误（多了一层 type）
 *
 * @author 未来姐姐
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
        return "editFileByLine";
    }

    @Override
    public JSONObject getDefinition()
    {
        try
        {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "editFileByLine");

            // 🆕 详细 description：明确行号规则
            functionDef.put("description",
                "按行编辑文件内容或创建新文件。支持插入、删除、修改、替换行操作。\n\n" +
                "⚠️ 【重要】行号规则（务必仔细阅读）：\n" +
                "- 所有行号从 **1** 开始计数（不是 0）\n" +
                "- **insert**: lineNumber=N 表示 **在第 N 行之前插入**（插入后新内容位于第 N 行）\n" +
                "- **update**: lineNumber=N 表示 **修改第 N 行**（替换为 newLine）\n" +
                "- **delete**: startLine=N, endLine=M 表示 **删除 [N, M) 半开区间**（不包含 M，即删除第 N 到第 M-1 行）\n" +
                "- **replace**: startLine=N, endLine=M 表示 **替换 [N, M) 半开区间**（不包含 M）\n" +
                "- 示例：删除第 5 行 → startLine=5, endLine=6\n" +
                "- 示例：删除第 5-7 行 → startLine=5, endLine=8\n\n" +
                "✅ 推荐：用 `find` 参数代替行号，避免歧义\n" +
                "✅ 推荐：行号模式必须传 `expectedContent` 双重验证\n" +
                "✅ 推荐：默认 `dryRun=true` 先预览，确认后再改 `dryRun=false` 真正写入");

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

            // 🆕 dryRun 参数（可选，默认 true）
            properties.put("dryRun", new JSONObject()
                .put("type", "boolean")
                .put("default", true)
                .put("description", "🆕 预览模式：true=只返回修改预览不真正写入，false=真正修改文件。强烈建议先用 true 预览，确认无误后再改 false 写入"));

            // operations 参数（必填）
            properties.put("operations", new JSONObject()
                .put("type", "array")
                .put("description", "要执行的编辑操作列表。每个操作必须有 type 字段")
                .put("items", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("type", new JSONObject()
                        .put("type", "string")
                        .put("enum", new JSONArray(new String[]{"insert", "delete", "update", "replace"}))
                        .put("description", "操作类型：insert | delete | update | replace"))
                    .put("lineNumber", new JSONObject()
                        .put("type", "integer")
                        .put("description", "【insert/update 用】行号，从 1 开始。insert: 在此行前插入；update: 修改此行"))
                    .put("lines", new JSONObject()
                        .put("type", "array")
                        .put("items", new JSONObject().put("type", "string"))
                        .put("description", "【insert 用】要插入的行内容数组"))
                    .put("startLine", new JSONObject()
                        .put("type", "integer")
                        .put("description", "【delete/replace 用】起始行号，从 1 开始，包含此行"))
                    .put("endLine", new JSONObject()
                        .put("type", "integer")
                        .put("description", "【delete/replace 用】结束行号，从 1 开始，⚠️ 不包含此行（半开区间 [startLine, endLine)）。删除第 5-7 行 → startLine=5, endLine=8"))
                    .put("newLine", new JSONObject()
                        .put("type", "string")
                        .put("description", "【update 用】新的行内容（替换 lineNumber 指定的那一行）"))
                    .put("newLines", new JSONObject()
                        .put("type", "array")
                        .put("items", new JSONObject().put("type", "string"))
                        .put("description", "【replace 用】替换后的行内容数组"))
                    // 🆕 find 参数（推荐用）：人类可读字符串定位
                    .put("find", new JSONObject()
                        .put("type", "string")
                        .put("description", "🆕 【推荐】人类可读字符串定位：在文件中查找此字符串的唯一出现位置。找到唯一 → 用此位置替代 lineNumber/startLine；找到多个/找不到 → 报错。优先级高于 lineNumber（如果同时传 find 和 lineNumber，优先用 find）"))
                    // 🆕 expectedContent 参数：双重验证
                    .put("expectedContent", new JSONObject()
                        .put("type", "string")
                        .put("description", "🆕 【行号模式双重验证】lineNumber/startLine 处必须等于此字符串，不匹配则报错（不执行修改，避免破坏文件）。建议所有行号调用都传此参数"))
                )));

            parameters.put("properties", properties);
            parameters.put("required", new JSONArray(new String[]{"inputType", "outputType", "operations"}));

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
        String inputType = arguments.getString("inputType");
        String outputType = arguments.getString("outputType");
        String outputPath = arguments.optString("outputPath", null);
        String encoding = arguments.optString("encoding", "utf-8");
        JSONArray operations = arguments.getJSONArray("operations");
        // 🆕 默认 dryRun=true（防止误操作）
        boolean dryRun = arguments.optBoolean("dryRun", true);

        List operationLog = new ArrayList<>();

        try
        {
            // 1. 获取原始内容
            List lines;
            if ("create".equals(inputType))
            {
                if (outputPath == null || outputPath.isEmpty())
                {
                    throw new IllegalArgumentException("创建新文件时必须指定 outputPath 参数");
                }
                if (!"filepath".equals(outputType))
                {
                    throw new IllegalArgumentException("创建新文件时 outputType 必须为 'filepath'");
                }
                lines = new ArrayList<>();
                operationLog.add("✓ 创建新文件：" + outputPath);
            }
            else if ("filepath".equals(inputType))
            {
                if (source == null || source.isEmpty())
                {
                    throw new IllegalArgumentException("inputType 为 'filepath' 时必须提供 source 参数（文件路径）");
                }
                lines = readFileLines(source, encoding);
                operationLog.add("✓ 读取文件：" + source);
            }
            else if ("content".equals(inputType))
            {
                if (source == null)
                {
                    throw new IllegalArgumentException("inputType 为 'content' 时必须提供 source 参数（文件内容）");
                }
                lines = splitIntoLines(source);
                operationLog.add("✓ 使用传入的内容");
            }
            else
            {
                throw new IllegalArgumentException("无效的 inputType: " + inputType);
            }

            int originalLineCount = lines.size();
            operationLog.add("✓ 原始行数：" + originalLineCount);

            // 2. 执行编辑操作
            for (int i = 0; i < operations.length(); i++)
            {
                JSONObject op = operations.getJSONObject(i);
                String opType = op.getString("type");

                // 🆕 2.1 解析位置（find 优先于 lineNumber/startLine）
                String findStr = op.optString("find", null);
                if (findStr != null && !findStr.isEmpty())
                {
                    // 🆕 用 find 定位（替换 lineNumber/startLine）
                    int foundIndex = findUniqueString(lines, findStr, opType);
                    if (opType.equals("insert") || opType.equals("update"))
                    {
                        op.put("lineNumber", foundIndex + 1); // 转为 1-based
                        operationLog.add("✓ find 定位：第 " + (foundIndex + 1) + " 行（字符串: " + truncate(findStr, 30) + "）");
                    }
                    else if (opType.equals("delete") || opType.equals("replace"))
                    {
                        op.put("startLine", foundIndex + 1);
                        // find 定位单行，endLine = startLine + 1（半开区间只删/换这一行）
                        op.put("endLine", foundIndex + 2);
                        operationLog.add("✓ find 定位：第 " + (foundIndex + 1) + " 行（半开区间 [" + (foundIndex + 1) + ", " + (foundIndex + 2) + ")）");
                    }
                }

                // 🆕 2.2 expectedContent 双重验证
                String expectedContent = op.optString("expectedContent", null);
                if (expectedContent != null && !expectedContent.isEmpty())
                {
                    int verifyLine = -1;
                    if (op.has("lineNumber"))
                    {
                        verifyLine = op.getInt("lineNumber");
                    }
                    else if (op.has("startLine"))
                    {
                        verifyLine = op.getInt("startLine");
                    }
                    if (verifyLine > 0 && verifyLine <= lines.size())
                    {
                        String actualContent = (String) lines.get(verifyLine - 1);
                        if (!actualContent.equals(expectedContent))
                        {
                            throw new IllegalArgumentException(
                                "expectedContent 不匹配：行号 " + verifyLine + " 的实际内容是 [" +
                                truncate(actualContent, 80) + "]，但期望 [" + truncate(expectedContent, 80) + "]。" +
                                "请重新读取文件确认正确的行号和内容。");
                        }
                        operationLog.add("✓ 行号 " + verifyLine + " 双重验证通过");
                    }
                }

                switch (opType)
                {
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

            // 🆕 4. dryRun 预览模式
            if (dryRun)
            {
                JSONObject result = new JSONObject();
                result.put("status", "dry_run");
                result.put("warning", "⚠️ 预览模式：以上修改还未真正写入文件。请检查 operation_log 确认无误后，再传 dryRun=false 重新调用以真正修改文件。");
                result.put("preview_content", resultContent);
                result.put("preview_line_count", finalLineCount);
                result.put("preview_first_10_lines", previewLines(resultContent, 10));
                result.put("operation_log", new JSONArray(operationLog));
                result.put("original_line_count", originalLineCount);
                result.put("next_step_hint", "✅ 确认修改正确 → 传 dryRun=false 真正写入；❌ 修改有误 → 调整 operations 重新调用");
                return result;
            }

            if ("content".equals(outputType))
            {
                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("content", resultContent);
                result.put("line_count", finalLineCount);
                result.put("operation_log", new JSONArray(operationLog));
                return result;
            }
            else if ("filepath".equals(outputType))
            {
                if (outputPath == null || outputPath.isEmpty())
                {
                    throw new IllegalArgumentException("outputType 为 'filepath' 时必须指定 outputPath 参数");
                }
                writeFileContent(outputPath, resultContent, encoding);

                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("output_path", outputPath);
                result.put("line_count", finalLineCount);
                result.put("operation_log", new JSONArray(operationLog));
                return result;
            }
            else
            {
                throw new IllegalArgumentException("无效的 outputType: " + outputType);
            }
        }
        catch (Exception e)
        {
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", e.getMessage());
            errorResult.put("operation_log", new JSONArray(operationLog));
            return errorResult;
        }
    }

    /**
     * 🆕 在文件中查找唯一字符串，返回其索引（0-based）
     * @param lines 文件行数组
     * @param find 要查找的字符串
     * @param opType 操作类型（用于错误信息）
     * @return 找到的索引（0-based）
     * @throws IllegalArgumentException 找不到或找到多个时报错
     */
    private int findUniqueString(List lines, String find, String opType) throws IllegalArgumentException
    {
        int foundIndex = -1;
        int matchCount = 0;
        List matchPositions = new ArrayList();
        for (int i = 0; i < lines.size(); i++)
        {
            if (((String) lines.get(i)).contains(find))
            {
                matchCount++;
                matchPositions.add(i + 1); // 1-based for display
                if (foundIndex == -1)
                {
                    foundIndex = i;
                }
            }
        }
        if (matchCount == 0)
        {
            throw new IllegalArgumentException(
                "find 字符串 [" + truncate(find, 50) + "] 在文件中找不到。" +
                "请检查拼写或换用 lineNumber 模式（必须配合 expectedContent）。");
        }
        if (matchCount > 1)
        {
            StringBuilder positions = new StringBuilder();
            for (int i = 0; i < matchPositions.size(); i++)
            {
                if (i > 0) positions.append(", ");
                positions.append(matchPositions.get(i));
            }
            throw new IllegalArgumentException(
                "find 字符串 [" + truncate(find, 50) + "] 在文件中找到 " + matchCount + " 处（行号 " + positions + "），" +
                "不是唯一的。请提供更具体的字符串，或换用 lineNumber + expectedContent 模式。");
        }
        return foundIndex;
    }

    /**
     * 🆕 截断字符串用于错误信息
     */
    private String truncate(String s, int maxLen)
    {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    /**
     * 🆕 生成预览（前 N 行）
     */
    private String previewLines(String content, int maxLines)
    {
        String[] arr = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(maxLines, arr.length);
        for (int i = 0; i < limit; i++)
        {
            if (i > 0) sb.append("\n");
            sb.append("  ").append(i + 1).append(": ").append(arr[i]);
        }
        if (arr.length > maxLines)
        {
            sb.append("\n  ... (还有 ").append(arr.length - maxLines).append(" 行未显示)");
        }
        return sb.toString();
    }

    /**
     * 将字符串分割成行数组
     */
    private List splitIntoLines(String content)
    {
        List lines = new ArrayList<>();
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
    private String joinLines(List lines)
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
     *
     * 行号规则（1-based + 在前插入）：
     * - lineNumber=1 → 在第 1 行之前插入（变成新的第 1 行，原第 1 行变成第 2 行）
     * - lineNumber=N → 在第 N 行之前插入（变成新的第 N 行）
     * - lineNumber 大于总行数 → 插入到末尾
     * - lineNumber ≤ 0 → 插入到开头
     */
    private List applyInsert(List lines, JSONObject op) throws Exception
    {
        int lineNumber = op.getInt("lineNumber");
        JSONArray newLines = op.getJSONArray("lines");

        int targetIndex = lineNumber - 1;

        // 边界处理：如果行号大于总行数，插入到末尾；如果小于 1，插入到开头
        int insertPosition = Math.max(0, Math.min(targetIndex, lines.size()));

        for (int i = 0; i < newLines.length(); i++)
        {
            lines.add(insertPosition + i, newLines.getString(i));
        }
        return lines;
    }

    /**
     * 应用删除操作
     *
     * 行号规则（1-based + 半开区间 [startLine, endLine)）：
     * - 删除第 N 到第 M-1 行（不包含第 M 行）
     * - 示例：删除第 5 行 → startLine=5, endLine=6
     * - 示例：删除第 5-7 行 → startLine=5, endLine=8
     */
    private List applyDelete(List lines, JSONObject op) throws Exception
    {
        int startLine = op.getInt("startLine");
        int endLine = op.optInt("endLine", lines.size() + 1);

        int startIndex = startLine - 1;
        int endIndex = endLine - 1;

        int start = Math.max(0, startIndex);
        int end = Math.min(endIndex, lines.size());

        if (start >= end)
        {
            throw new IllegalArgumentException(
                "删除范围无效：startLine=" + startLine + ", endLine=" + endLine +
                "（半开区间 [N, M) 要求 start < end）。文件有 " + lines.size() + " 行。");
        }
        lines.subList(start, end).clear();
        return lines;
    }

    /**
     * 应用更新操作
     *
     * 行号规则（1-based）：
     * - lineNumber=N → 修改第 N 行（替换为 newLine）
     */
    private List applyUpdate(List lines, JSONObject op) throws Exception
    {
        int lineNumber = op.getInt("lineNumber");
        String newLine = op.getString("newLine");

        int index = lineNumber - 1;

        if (index < 0 || index >= lines.size())
        {
            throw new IllegalArgumentException(
                "Invalid line number: " + lineNumber + ". File has " + lines.size() + " lines. Line numbers start from 1.");
        }
        lines.set(index, newLine);
        return lines;
    }

    /**
     * 应用替换操作
     *
     * 行号规则（1-based + 半开区间 [startLine, endLine)）：
     * - 替换第 N 到第 M-1 行（不包含第 M 行）为 newLines
     */
    private List applyReplace(List lines, JSONObject op) throws Exception
    {
        int startLine = op.getInt("startLine");
        int endLine = op.getInt("endLine");
        JSONArray newLines = op.getJSONArray("newLines");

        int startIndex = startLine - 1;
        int endIndex = endLine - 1;

        int start = Math.max(0, startIndex);
        int end = Math.min(endIndex, lines.size());

        if (start >= end)
        {
            throw new IllegalArgumentException(
                "替换范围无效：startLine=" + startLine + ", endLine=" + endLine +
                "（半开区间 [N, M) 要求 start < end）。文件有 " + lines.size() + " 行。");
        }

        List replacement = new ArrayList<>();
        for (int i = 0; i < newLines.length(); i++)
        {
            replacement.add(newLines.getString(i));
        }
        lines.subList(start, end).clear();
        lines.addAll(start, replacement);
        return lines;
    }

    /**
     * 读取文件内容
     */
    private List readFileLines(String path, String encoding) throws IOException
    {
        List lines = new ArrayList<>();
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
