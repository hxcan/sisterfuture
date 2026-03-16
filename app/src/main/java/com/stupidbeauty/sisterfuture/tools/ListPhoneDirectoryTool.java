// com.stupidbeauty.sisterfuture.tools.ListPhoneDirectoryTool.java
package com.stupidbeauty.sisterfuture.tools;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.File;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import com.stupidbeauty.sisterfuture.tool.Tool;

/**
 * 手机目录扫描工具。
 * 用于自动化扫描手机外置存储目录，返回文件列表。
 * 
 * @author 未来姐姐
 * @version 1.1
 * @since 2026-03-16
 */
public class ListPhoneDirectoryTool implements Tool
{
    private final Context context;

    public ListPhoneDirectoryTool(Context context)
    {
        this.context = context;
    }

    @Override
    public String getName()
    {
        return "list_phone_directory";
    }

    @Override
    public JSONObject getDefinition()
    {
        try
        {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "list_phone_directory");
            functionDef.put("description", "扫描手机外置存储目录，返回文件列表。支持递归扫描和文件过滤（扩展名、修改时间、关键词）");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            
            JSONObject properties = new JSONObject();
            
            // path 参数（必填）
            properties.put("path", new JSONObject()
                .put("type", "string")
                .put("description", "要扫描的目录路径，如 /sdcard/Download/"));
            
            // recursive 参数（可选）
            properties.put("recursive", new JSONObject()
                .put("type", "boolean")
                .put("description", "是否递归扫描子目录，默认 false"));
            
            // filter 参数（可选）
            JSONObject filterDef = new JSONObject();
            filterDef.put("type", "object");
            filterDef.put("description", "过滤条件");
            
            JSONObject filterProperties = new JSONObject();
            filterProperties.put("extensions", new JSONObject()
                .put("type", "array")
                .put("items", new JSONObject().put("type", "string"))
                .put("description", "文件扩展名过滤数组，如 [\".pdf\", \".doc\"]"));
            filterProperties.put("modified_after", new JSONObject()
                .put("type", "string")
                .put("description", "修改时间过滤，格式 YYYY-MM-DD"));
            filterProperties.put("keywords", new JSONObject()
                .put("type", "array")
                .put("items", new JSONObject().put("type", "string"))
                .put("description", "文件名关键词过滤数组，如 [\"交接\", \"林主明\"]"));
            
            filterDef.put("properties", filterProperties);
            properties.put("filter", filterDef);
            
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
        // 解析参数
        String path = arguments.getString("path");
        boolean recursive = arguments.optBoolean("recursive", false);
        JSONObject filter = arguments.optJSONObject("filter");

        // 检查并请求权限
        JSONObject permissionResult = checkAndRequestPermission(path);
        if (permissionResult != null)
        {
            // 权限不足，返回提示信息
            return permissionResult;
        }

        // 验证目录
        File directory = new File(path);
        if (!directory.exists())
        {
            throw new IllegalArgumentException("目录不存在：" + path);
        }
        if (!directory.isDirectory())
        {
            throw new IllegalArgumentException("路径不是目录：" + path);
        }

        // 扫描文件
        List<FileEntry> files = new ArrayList<>();
        List<FileEntry> directories = new ArrayList<>();
        scanDirectory(directory, recursive, filter, files, directories);

        // 构建返回结果
        JSONObject result = new JSONObject();
        try
        {
            result.put("status", "success");
            result.put("path", path);
            result.put("files", filesToJsonArray(files));
            result.put("directories", filesToJsonArray(directories));
            result.put("file_count", files.size());
            result.put("directory_count", directories.size());
        }
        catch (JSONException e)
        {
            // JSON 构建失败，返回错误
            JSONObject error = new JSONObject();
            error.put("status", "error");
            error.put("message", "构建返回结果失败");
            return error;
        }

        return result;
    }

    @Override
    public String getDefaultSystemPromptEnhancement()
    {
        return "必须在用户明确要求扫描手机目录时才调用此工具。需要提供要扫描的目录路径。支持可选的递归和过滤参数。注意：在 Android 11+ 上访问某些目录需要\"管理全部文件\"权限，工具会自动检查并引导用户授权。";
    }

    /**
     * 检查并请求存储权限。
     * @param path 要访问的路径
     * @return 如果权限不足返回提示信息，否则返回 null
     */
    private JSONObject checkAndRequestPermission(String path)
    {
        // 检查是否需要 MANAGE_EXTERNAL_STORAGE 权限
        if (needsManageStoragePermission(path))
        {
            if (!hasManageStoragePermission())
            {
                // 权限不足，引导用户授权
                requestManageStoragePermission();
                
                // 返回提示信息
                try
                {
                    JSONObject result = new JSONObject();
                    result.put("status", "permission_required");
                    result.put("message", "⚠️ 需要\"管理全部文件\"权限才能访问该目录。\n\n已为您打开权限设置页面，请按以下步骤操作：\n\n1. 找到\"未来姐姐\"应用\n2. 开启\"允许管理所有文件\"权限\n3. 返回后重试操作\n\n或者，您可以尝试访问公共目录（如 /sdcard/Download/），这些目录只需要普通读取权限。");
                    result.put("action", "open_permission_settings");
                    return result;
                }
                catch (JSONException e)
                {
                    return new JSONObject();
                }
            }
        }
        else
        {
            // 检查普通读取权限
            if (!hasReadExternalStoragePermission())
            {
                try
                {
                    JSONObject result = new JSONObject();
                    result.put("status", "permission_required");
                    result.put("message", "⚠️ 需要\"读取手机存储\"权限才能访问该目录。\n\n请在应用权限设置中开启\"读取手机存储\"权限，然后重试。");
                    result.put("action", "open_permission_settings");
                    return result;
                }
                catch (JSONException e)
                {
                    return new JSONObject();
                }
            }
        }
        
        return null; // 权限充足
    }

    /**
     * 检查路径是否需要 MANAGE_EXTERNAL_STORAGE 权限。
     */
    private boolean needsManageStoragePermission(String path)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
        {
            // Android 10 及以下不需要
            return false;
        }
        
        // Android 11+ 检查是否在受限目录
        if (path.startsWith("/sdcard/Android/data/") || 
            path.startsWith("/sdcard/Android/obb/") ||
            path.startsWith("/Android/data/") ||
            path.startsWith("/Android/obb/"))
        {
            return true;
        }
        
        // 检查是否是应用私有目录
        try
        {
            File file = new File(path);
            File externalStorage = Environment.getExternalStorageDirectory();
            String relativePath = file.getAbsolutePath().substring(externalStorage.getAbsolutePath().length());
            
            if (relativePath.startsWith("/Android/data/") || 
                relativePath.startsWith("/Android/obb/"))
            {
                return true;
            }
        }
        catch (Exception e)
        {
            // 解析失败，保守起见要求权限
            return true;
        }
        
        return false;
    }

    /**
     * 检查是否有 MANAGE_EXTERNAL_STORAGE 权限。
     */
    private boolean hasManageStoragePermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            return Environment.isExternalStorageManager();
        }
        return true; // 低版本不需要此权限
    }

    /**
     * 检查是否有 READ_EXTERNAL_STORAGE 权限。
     */
    private boolean hasReadExternalStoragePermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            return context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) 
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true; // 低版本默认有权限
    }

    /**
     * 请求 MANAGE_EXTERNAL_STORAGE 权限。
     */
    private void requestManageStoragePermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            try
            {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            catch (Exception e)
            {
                // 如果特定包名方式失败，尝试通用方式
                try
                {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
                catch (Exception e2)
                {
                    // 无法打开权限页面，记录日志
                    e2.printStackTrace();
                }
            }
        }
    }

    /**
     * 扫描目录。
     */
    private void scanDirectory(File dir, boolean recursive, JSONObject filter, 
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
     */
    private boolean matchesFilter(File file, JSONObject filter)
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
     */
    private long parseDate(String dateStr)
    {
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
     */
    private JSONArray filesToJsonArray(List<FileEntry> entries)
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
            try
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
            catch (JSONException e)
            {
                return new JSONObject();
            }
        }
    }
}
