// com.stupidbeauty.sisterfuture.tool.ReadPhoneFileTool.java
package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import com.stupidbeauty.sisterfuture.tool.Tool;

/**
 * 手机文件读取工具。
 * 用于自动化读取手机外置存储上的任意文件内容。
 *
 * @author 未来姐姐
 * @version 1.1
 * @since 2026-03-16
 */
public class ReadPhoneFileTool implements Tool
{
    private final Context context;

    public ReadPhoneFileTool(Context context)
    {
        this.context = context;
    }

    @Override
    public String getName()
    {
        return "readPhoneFile";
    }

    @Override
    public JSONObject getDefinition()
    {
        try
        {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "readPhoneFile");
            functionDef.put("description", "读取手机外置存储上的文件内容。支持 Base64 编码（二进制文件）和 UTF-8 文本（文本文件）。可选返回带行号的列表格式");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");

            JSONObject properties = new JSONObject();
            properties.put("path", new JSONObject()
                .put("type", "string")
                .put("description", "要读取的文件路径"));

            properties.put("encoding", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray(new String[]{"base64", "utf-8"}))
                .put("description", "编码方式：\"base64\"（默认，适用于二进制文件）或 \"utf-8\"（适用于文本文件）"));

            properties.put("includeLineNumbers", new JSONObject()
                .put("type", "boolean")
                .put("default", false)
                .put("description", "是否返回带行号的列表格式。当为 true 时，返回 JSON 数组，每个元素包含 lineNumber 和 content。仅对文本文件有效"));

            parameters.put("properties", properties);
            parameters.put("required", new JSONArray(new String[]{"path"}));

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
        String path = arguments.getString("path");
        String encoding = arguments.optString("encoding", "base64");
        boolean includeLineNumbers = arguments.optBoolean("includeLineNumbers", false);

        File file = new File(path);
        if (!file.exists())
        {
            throw new IllegalArgumentException("文件不存在：" + path);
        }
        if (!file.isFile())
        {
            throw new IllegalArgumentException("路径不是文件：" + path);
        }

long fileSize = file.length();
        // 🆕 #818324303966 文件大小限制：300KB
        long MAX_FILE_SIZE = 300 * 1024; // 300KB
        if (fileSize > MAX_FILE_SIZE)
        {
            throw new IllegalArgumentException(
                "❌ 拒绝读取：文件过大\n\n" +
                "📏 文件大小：" + formatFileSize(fileSize) + "\n" +
                "⚠️ 限制阈值：300 KB\n\n" +
                "💡 建议：\n" +
                "- 请选择较小的文件（≤ 300KB）\n" +
                "- 或对文件进行压缩后再读取\n" +
                "- 或分段读取文件内容\n\n" +
                "文件路径：" + path
            );
        }
        if (fileSize > 100 * 1024 * 1024)
        {
            throw new IllegalArgumentException("文件过大（最大支持 100MB）: " + formatFileSize(fileSize));
        }

        String mimeType = URLConnection.guessContentTypeFromName(file.getName());

        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("path", path);
        result.put("size", fileSize);
        result.put("file_name", file.getName());

        if (mimeType != null)
        {
            result.put("mime_type", mimeType);
        }

        if (includeLineNumbers && "utf-8".equalsIgnoreCase(encoding))
        {
            List lines = readFileWithLineNumbers(path);
            JSONArray linesArray = new JSONArray();
            for (int i = 0; i < lines.size(); i++)
            {
                JSONObject lineObj = new JSONObject();
                lineObj.put("lineNumber", i + 1);
                lineObj.put("content", lines.get(i));
                linesArray.put(lineObj);
            }
            result.put("lines", linesArray);
        }
        else
        {
            if ("base64".equalsIgnoreCase(encoding))
            {
                byte[] fileContent = readFileBytes(path);
                result.put("content", Base64.encodeToString(fileContent, Base64.NO_WRAP));
            }
            else
            {
                String content = readFileText(path);
                result.put("content", content);
            }
        }

        return result;
    }

    private byte[] readFileBytes(String path) throws Exception
    {
        FileInputStream fis = null;
        byte[] data = null;
        try
        {
            fis = new FileInputStream(path);
            data = new byte[fis.available()];
            fis.read(data);
        }
        finally
        {
            if (fis != null)
            {
                fis.close();
            }
        }
        return data;
    }

    private String readFileText(String path) throws Exception
    {
        BufferedReader reader = null;
        StringBuilder content = new StringBuilder();
        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null)
            {
                content.append(line).append("\n");
            }
        }
        finally
        {
            if (reader != null)
            {
                reader.close();
            }
        }
        return content.toString();
    }

    private List readFileWithLineNumbers(String path) throws Exception
    {
        List lines = new ArrayList<>();
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8));
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

    private String formatFileSize(long bytes)
    {
        if (bytes < 1024)
        {
            return bytes + " B";
        }
        else if (bytes < 1024 * 1024)
        {
            return String.format("%.2f KB", bytes / 1024.0);
        }
        else if (bytes < 1024 * 1024 * 1024)
        {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
        else
        {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}