// com.stupidbeauty.sisterfuture.tools.ReadPhoneFileTool.java
package com.stupidbeauty.sisterfuture.tools;

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
        return "read_phone_file";
    }

    @Override
    public JSONObject getDefinition()
    {
        try
        {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "read_phone_file");
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
            List<String> lines = readFileWithLineNumbers(path);
            JSONArray linesArray = new JSONArray();
            
            for (int i = 0; i < lines.size(); i++)
            {
                JSONObject lineObj = new JSONObject();
                lineObj.put("lineNumber", i);
                lineObj.put("content", lines.get(i));
                linesArray.put(lineObj);
            }
            
            result.put("lines", linesArray);
            result.put("totalLines", lines.size());
            result.put("encoding", "utf-8");
            result.put("mode", "line-numbered");
        }
        else if ("utf-8".equalsIgnoreCase(encoding))
        {
            byte[] fileBytes = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file))
            {
                fis.read(fileBytes);
            }
            String content = new String(fileBytes, StandardCharsets.UTF_8);
            
            result.put("content", content);
            result.put("encoding", "utf-8");
            result.put("mode", "text");
        }
        else
        {
            byte[] fileBytes = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file))
            {
                fis.read(fileBytes);
            }
            String content = Base64.encodeToString(fileBytes, Base64.NO_WRAP);
            
            result.put("content", content);
            result.put("encoding", "base64");
            result.put("mode", "binary");
        }

        return result;
    }

    @Override
    public String getDefaultSystemPromptEnhancement()
    {
        return "必须在用户明确要求读取手机文件时才调用此工具。需要提供文件路径。对于二进制文件（如图片、PDF）使用 base64 编码，对于文本文件可以使用 utf-8 编码。如果需要精确知道每行的行号以便后续编辑操作，可以设置 includeLineNumbers=true，此时将返回带行号的列表格式。";
    }

    private List<String> readFileWithLineNumbers(String path) throws Exception
    {
        List<String> lines = new ArrayList<>();
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
