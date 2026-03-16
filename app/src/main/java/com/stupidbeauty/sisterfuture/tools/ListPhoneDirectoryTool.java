package com.stupidbeauty.sisterfuture.tools;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * 手机目录扫描工具。
 * 用于自动化扫描手机外置存储目录，返回文件列表。
 * 
 * @author 未来姐姐
 * @version 1.0
 * @since 2026-03-16
 */
public class ListPhoneDirectoryTool
{
    /**
     * 执行目录扫描。
     * 
     * @param params 工具参数，包含：
     *               - path: 要扫描的目录路径（必填）
     *               - recursive: 是否递归扫描子目录（可选，默认 false）
     *               - filter: 过滤条件（可选），包含：
     *                 - extensions: 文件扩展名过滤数组，如 [".pdf", ".doc"]
     *                 - modified_after: 修改时间过滤，如 "2026-03-01"
     *                 - keywords: 文件名关键词过滤数组，如 ["交接", "林主明"]
     * @return ToolResult 包含扫描结果或错误信息
     */
    public static ToolResult execute(ToolParams params)
    {
        try
        {
            // 获取参数
            String path = params.getString("path");
            boolean recursive = params.getBoolean("recursive", false);
            JSONObject filter = params.optJSONObject("filter");
            
            // 验证目录
            File directory = new File(path);
            if (!directory.exists())
            {
                return ToolResult.error("目录不存在：" + path);
            }
            if (!directory.isDirectory())
            {
                return ToolResult.error("路径不是目录：" + path);
            }
            
            // 检查读取权限
            if (!directory.canRead())
            {
                return ToolResult.error("无权限读取目录：" + path);
            }
            
            // 扫描文件
            List<FileEntry> files = new ArrayList<>();
            List<FileEntry> directories = new ArrayList<>();
            scanDirectory(directory, recursive, filter, files, directories);
            
            // 构建返回结果
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("path", path);
            result.put("files", filesToJsonArray(files));
            result.put("directories", filesToJsonArray(directories));
            result.put("file_count", files.size());
            result.put("directory_count", directories.size());
            
            return ToolResult.success(result);
        }
        catch (Exception e)
        {
            return ToolResult.error("扫描失败：" + e.getMessage());
        }
    }
    
    /**
     * 扫描目录。
     * 
     * @param dir 要扫描的目录
     * @param recursive 是否递归
     * @param filter 过滤条件
     * @param files 文件列表（输出）
     * @param directories 目录列表（输出）
     */
    private static void scanDirectory(File dir, boolean recursive, JSONObject filter, 
                                      List<FileEntry> files, List<FileEntry> directories)
    {
        File[] listFiles = dir.listFiles();
        if (listFiles == null)
        {
            return;
        }
        
        for (File file : listFiles)
        {
            if (file.isDirectory())
            {
                // 跳过隐藏目录和系统目录
                if (file.isHidden() || file.getName().startsWith("."))
                {
                    continue;
                }
                
                directories.add(FileEntry.fromFile(file));
                
                // 递归扫描子目录
                if (recursive)
                {
                    scanDirectory(file, recursive, filter, files, directories);
                }
            }
            else if (file.isFile())
            {
                // 跳过隐藏文件
                if (file.isHidden() || file.getName().startsWith("."))
                {
                    continue;
                }
                
                // 应用过滤条件
                if (matchesFilter(file, filter))
                {
                    files.add(FileEntry.fromFile(file));
                }
            }
        }
    }
    
    /**
     * 检查文件是否匹配过滤条件。
     * 
     * @param file 要检查的文件
     * @param filter 过滤条件
     * @return true 如果匹配，false 如果不匹配
     */
    private static boolean matchesFilter(File file, JSONObject filter)
    {
        if (filter == null)
        {
            return true; // 无过滤条件，全部匹配
        }
        
        String fileName = file.getName();
        
        // 1. 扩展名过滤
        JSONArray extensions = filter.optJSONArray("extensions");
        if (extensions != null && extensions.length() > 0)
        {
            boolean match = false;
            for (int i = 0; i < extensions.length(); i++)
            {
                String ext = extensions.optString(i);
                if (fileName.toLowerCase().endsWith(ext.toLowerCase()))
                {
                    match = true;
                    break;
                }
            }
            if (!match)
            {
                return false;
            }
        }
        
        // 2. 修改时间过滤
        String modifiedAfter = filter.optString("modified_after", null);
        if (modifiedAfter != null)
        {
            try
            {
                long fileTime = file.lastModified();
                long filterTime = parseDate(modifiedAfter);
                if (fileTime < filterTime)
                {
                    return false;
                }
            }
            catch (Exception e)
            {
                // 日期解析失败，忽略此过滤条件
            }
        }
        
        // 3. 关键词过滤
        JSONArray keywords = filter.optJSONArray("keywords");
        if (keywords != null && keywords.length() > 0)
        {
            boolean match = false;
            for (int i = 0; i < keywords.length(); i++)
            {
                String keyword = keywords.optString(i);
                if (fileName.contains(keyword))
                {
                    match = true;
                    break;
                }
            }
            if (!match)
            {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 解析日期字符串为时间戳。
     * 
     * @param dateStr 日期字符串，格式：YYYY-MM-DD
     * @return 时间戳
     */
    private static long parseDate(String dateStr)
    {
        // 简单实现，假设格式为 YYYY-MM-DD
        String[] parts = dateStr.split("-");
        if (parts.length == 3)
        {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]) - 1; // Calendar 月份从 0 开始
            int day = Integer.parseInt(parts[2]);
            
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(year, month, day, 0, 0, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        }
        throw new IllegalArgumentException("无效的日期格式：" + dateStr);
    }
    
    /**
     * 将文件列表转换为 JSON 数组。
     * 
     * @param entries 文件条目列表
     * @return JSON 数组
     */
    private static JSONArray filesToJsonArray(List<FileEntry> entries)
    {
        JSONArray array = new JSONArray();
        for (FileEntry entry : entries)
        {
            array.put(entry.toJson());
        }
        return array;
    }
    
    /**
     * 文件条目内部类。
     */
    private static class FileEntry
    {
        public String path;
        public String name;
        public long size;
        public long modified;
        public String type; // "file" or "directory"
        public String mimeType;
        
        public static FileEntry fromFile(File file)
        {
            FileEntry entry = new FileEntry();
            entry.path = file.getAbsolutePath();
            entry.name = file.getName();
            entry.size = file.isDirectory() ? 0 : file.length();
            entry.modified = file.lastModified();
            entry.type = file.isDirectory() ? "directory" : "file";
            
            // 检测 MIME 类型
            if (file.isFile())
            {
                entry.mimeType = URLConnection.guessContentTypeFromName(file.getName());
            }
            else
            {
                entry.mimeType = null;
            }
            
            return entry;
        }
        
        public JSONObject toJson()
        {
            JSONObject json = new JSONObject();
            json.put("path", path);
            json.put("name", name);
            json.put("size", size);
            json.put("modified", modified);
            json.put("type", type);
            if (mimeType != null)
            {
                json.put("mime_type", mimeType);
            }
            return json;
        }
    }
}