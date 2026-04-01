// com.stupidbeauty.sisterfuture.tool.CreateGitHubCommitTool.java
package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CreateGitHubCommitTool implements Tool
{
  private static final String TAG = "CreateGitHubCommit";
  private final Context context;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  public CreateGitHubCommitTool(Context context)
  {
    this.context = context;
  }

  @Override
  public String getName()
  {
    return "create_github_commit";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "create_github_commit");
      functionDef.put("description", "通过 GitHub API 向指定仓库的分支提交新的代码更改。此操作涉及多个步骤：获取文件信息、创建 Blob、创建 Tree、创建 Commit 和更新引用。支持文本和二进制文件上传，支持嵌套目录创建。支持删除文件（设置 delete=true）。");

      JSONObject parameters = new JSONObject();
      parameters.put("type", "object");
      parameters.put
      (
        "properties",
        new JSONObject()
          .put
          (
            "owner",
            new JSONObject()
              .put("type", "string")
              .put("description", "仓库所有者")
          )
          .put
          (
            "repo",
            new JSONObject()
              .put("type", "string")
              .put("description", "仓库名称")
          )
          .put
          (
            "branch",
            new JSONObject()
              .put("type", "string")
              .put("description", "目标分支，例如 \"master\"")
          )
          .put
          (
            "path",
            new JSONObject()
              .put("type", "string")
              .put("description", "要修改的文件路径（支持嵌套目录，如 .github/workflows/ci.yml）")
          )
          .put
          (
            "content",
            new JSONObject()
              .put("type", "string")
              .put("description", "文件的新内容。对于文本文件直接传入文本；对于二进制文件，需先 Base64 编码。删除文件时忽略此参数。")
          )
          .put
          (
            "encoding",
            new JSONObject()
              .put("type", "string")
              .put("description", "返回模式：\"text\"（默认，UTF-8 编码，适用于代码/配置文件）或 \"base64\"（保留原始 Base64 字符串，适用于 .keystore/.png/.apk 等二进制文件）")
          )
          .put
          (
            "commit_message",
            new JSONObject()
              .put("type", "string")
              .put("description", "提交信息")
          )
          .put
          (
            "token",
            new JSONObject()
              .put("type", "string")
              .put("description", "GitHub 个人访问令牌 (PAT)，用于认证")
          )
          .put
          (
            "read_from_phone",
            new JSONObject()
              .put("type", "boolean")
              .put("description", "是否从手机读取文件内容（true 时忽略 content 参数，使用 phone_path）")
          )
          .put
          (
            "phone_path",
            new JSONObject()
              .put("type", "string")
              .put("description", "当 read_from_phone=true 时，指定要读取的手机文件路径")
          )
          .put
          (
            "delete",
            new JSONObject()
              .put("type", "boolean")
              .put("description", "是否删除文件（true 时执行删除操作，忽略 content 和 encoding 参数）")
          )
      );
      parameters.put("required", new JSONArray(new String[]{"owner", "repo", "branch", "path", "commit_message"}));

      functionDef.put("parameters", parameters);
      return new JSONObject().put("type", "function").put("function", functionDef);
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "Failed to build definition", e);
      return new JSONObject();
    }
  }

  @Override
  public boolean shouldInclude()
  {
    return true;
  }

  @Override
  public boolean isAsync()
  {
    return true;
  }

  /**
    * 兼容 API 24+ 的文件读取方法
    */
  private byte[] readAllBytesCompat(String filePath) throws IOException
  {
    File file = new File(filePath);
    long fileSize = file.length();

    if (fileSize > Integer.MAX_VALUE)
    {
      throw new IllegalArgumentException("File too large: " + fileSize + " bytes");
    }

    byte[] buffer = new byte[(int) fileSize];
    int offset = 0;
    int bytesRead;

    try (FileInputStream fis = new FileInputStream(file))
    {
      while (offset < fileSize && (bytesRead = fis.read(buffer, offset, (int) fileSize - offset)) != -1)
      {
        offset += bytesRead;
      }
    }

    if (offset < fileSize)
    {
      throw new IOException("Could not completely read file " + filePath);
    }

    return buffer;
  }

  @Override
  public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback)
  {
    executor.execute
    (
      () ->
      {
        try
        {
          String owner = arguments.getString("owner");
          String repo = arguments.getString("repo");
          String branch = arguments.getString("branch");
          String path = arguments.getString("path");
          String commitMessage = arguments.getString("commit_message");
          String token = arguments.optString("token", "").trim();
          String encoding = arguments.optString("encoding", "text");

          // 新增参数
          boolean readFromPhone = arguments.optBoolean("read_from_phone", false);
          String phonePath = arguments.optString("phone_path", "");
          boolean deleteFile = arguments.optBoolean("delete", false);

          String content = "";
                
          // 处理删除文件的逻辑
          if (deleteFile)
          {
            FileLogger.d(TAG, "CreateGitHubCommit: 执行删除文件操作，path=" + path);

            if (token.isEmpty())
            {
              String noteJson = getNote(context);
              if (!noteJson.isEmpty())
              {
                JSONObject saved = new JSONObject(noteJson);
                if (saved.has("github_token"))
                {
                  token = saved.getString("github_token");
                }
              }
            }

            if (token.isEmpty())
            {
              throw new IllegalArgumentException("缺少 GitHub 访问令牌 (token)，且未在备注中配置");
            }

            OkHttpClient client = new OkHttpClient();

            // 第一步：获取文件的 sha
            HttpUrl getContentUrl = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path)
              .newBuilder()
              .addQueryParameter("ref", branch)
              .build();

            Request getContentRequest = new Request.Builder()
              .url(getContentUrl)
              .header("Authorization", "Bearer " + token)
              .header("Accept", "application/vnd.github.v3+json")
              .build();

            Response getContentResponse = client.newCall(getContentRequest).execute();
                    
            if (!getContentResponse.isSuccessful())
            {
              if (getContentResponse.code() == 404)
              {
                throw new IOException("文件不存在，无法删除：" + path);
              }
              throw new IOException("获取文件信息失败：" + getContentResponse.code() + " " + getContentResponse.message());
            }

            JSONObject fileInfo = new JSONObject(getContentResponse.body().string());
            String fileSha = fileInfo.getString("sha");
                    
            FileLogger.d(TAG, "CreateGitHubCommit: 获取到文件 sha=" + fileSha);

            // 第二步：调用 DELETE API 删除文件
            JSONObject deleteBody = new JSONObject();
            deleteBody.put("message", commitMessage);
            deleteBody.put("sha", fileSha);
            deleteBody.put("branch", branch);

            RequestBody requestBody = RequestBody.create
              (
                deleteBody.toString(),
                MediaType.get("application/json; charset=utf-8")
              );

            Request deleteRequest = new Request.Builder()
              .url(HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path))
              .delete(requestBody)
              .header("Authorization", "Bearer " + token)
              .header("Accept", "application/vnd.github.v3+json")
              .build();

            Response deleteResponse = client.newCall(deleteRequest).execute();
                    
            if (!deleteResponse.isSuccessful())
            {
              throw new IOException("删除文件失败：" + deleteResponse.code() + " " + deleteResponse.message());
            }

            JSONObject deleteResult = new JSONObject(deleteResponse.body().string());
            String newCommitSha = deleteResult.getJSONObject("commit").getString("sha");

            FileLogger.d(TAG, "CreateGitHubCommit: 文件删除成功，新 commit sha=" + newCommitSha);

            // 返回成功结果
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "文件删除成功！");
            result.put("commit_sha", newCommitSha);
            result.put("branch_updated", branch);
            result.put("fetched_at", System.currentTimeMillis());

            JSONObject debugInfo = new JSONObject();
            debugInfo.put("tool_name", "create_github_commit");
            debugInfo.put("params", new JSONObject()
              .put("owner", owner)
              .put("repo", repo)
              .put("path", path)
              .put("branch", branch)
              .put("delete", deleteFile));
            debugInfo.put("verification_status", "OK");
                    
            result.put("debug_info", debugInfo);

            callback.onResult(result);
            return; // 删除操作完成，直接返回
          }

          // 处理从手机读取文件的逻辑（原有逻辑）
          if (readFromPhone)
          {
            if (phonePath.isEmpty())
            {
              throw new IllegalArgumentException("read_from_phone=true 时必须提供 phone_path 参数");
            }
                    
            File phoneFile = new File(phonePath);
            if (!phoneFile.exists())
            {
              throw new IOException("手机文件不存在：" + phonePath);
            }
                    
            // 使用兼容方法读取文件
            byte[] fileBytes = readAllBytesCompat(phonePath);
                    
            // 自动判断文件类型
            String lowerPath = phonePath.toLowerCase();
            boolean isBinary = lowerPath.endsWith(".jar") || lowerPath.endsWith(".apk") ||
              lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") ||
              lowerPath.endsWith(".gif") || lowerPath.endsWith(".pdf") ||
              lowerPath.endsWith(".zip") || lowerPath.endsWith(".keystore") ||
              lowerPath.endsWith(".jks");
                    
            if (isBinary)
            {
              // 二进制文件：使用 Base64 编码
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
              {
                content = Base64.getEncoder().encodeToString(fileBytes);
              }
              else
              {
                content = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP);
              }
              encoding = "base64";
              FileLogger.d(TAG, "CreateGitHubCommit: Read binary file from phone, size=" + fileBytes.length + ", encoded length=" + content.length());
            }
            else
            {
              // 文本文件：直接使用 UTF-8 字符串
              content = new String(fileBytes, StandardCharsets.UTF_8);
              encoding = "text";
              FileLogger.d(TAG, "CreateGitHubCommit: Read text file from phone, size=" + fileBytes.length);
            }
          }
          else
          {
            // 原有逻辑：使用传入的 content 参数
            content = arguments.getString("content");
          }

          int contentLength = content.length();
          FileLogger.d(TAG, "CreateGitHubCommit DEBUG: Received content length: " + contentLength + " chars");
          FileLogger.d(TAG, "CreateGitHubCommit DEBUG: Encoding type: " + encoding);
                
          if (token.isEmpty())
          {
            String noteJson = getNote(context);
            if (!noteJson.isEmpty())
            {
              JSONObject saved = new JSONObject(noteJson);
              if (saved.has("github_token"))
              {
                token = saved.getString("github_token");
              }
            }
          }

          if (token.isEmpty())
          {
            throw new IllegalArgumentException("缺少 GitHub 访问令牌 (token)，且未在备注中配置");
          }

          OkHttpClient client = new OkHttpClient();

          HttpUrl getContentUrl = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path)
            .newBuilder()
            .addQueryParameter("ref", branch)
            .build();

          Request getContentRequest = new Request.Builder()
            .url(getContentUrl)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github.v3+json")
            .build();

          Response getContentResponse = client.newCall(getContentRequest).execute();
          String fileSha = null;

          if (getContentResponse.isSuccessful())
          {
            JSONObject fileInfo = new JSONObject(getContentResponse.body().string());
            fileSha = fileInfo.getString("sha");
          }
          else if (getContentResponse.code() != 404)
          {
            throw new IOException("检查文件状态失败：" + getContentResponse.code() + " " + getContentResponse.message());
          }

          JSONObject blobBody = new JSONObject();
          blobBody.put("content", content);
          blobBody.put("encoding", encoding);

          Request createBlobRequest = new Request.Builder()
            .url(HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/blobs"))
            .post(RequestBody.create(blobBody.toString(), MediaType.get("application/json; charset=utf-8")))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github.v3+json")
            .build();

          Response createBlobResponse = client.newCall(createBlobRequest).execute();
          if (!createBlobResponse.isSuccessful())
          {
            throw new IOException("创建 Blob 失败：" + createBlobResponse.code() + " " + createBlobResponse.message());
          }

          JSONObject blobInfo = new JSONObject(createBlobResponse.body().string());
          String blobSha = blobInfo.getString("sha");

          HttpUrl getRefUrl = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/refs/heads/" + branch);
          Request getRefRequest = new Request.Builder()
            .url(getRefUrl)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github.v3+json")
            .build();

          Response getRefResponse = client.newCall(getRefRequest).execute();
          if (!getRefResponse.isSuccessful())
          {
            throw new IOException("获取分支引用失败：" + getRefResponse.code() + " " + getRefResponse.message());
          }

          JSONObject refInfo = new JSONObject(getRefResponse.body().string());
          String latestCommitSha = refInfo.getJSONObject("object").getString("sha");

          HttpUrl getCommitUrl = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/commits/" + latestCommitSha);
          Request getCommitRequest = new Request.Builder()
            .url(getCommitUrl)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github.v3+json")
            .build();

          Response getCommitResponse = client.newCall(getCommitRequest).execute();
          if (!getCommitResponse.isSuccessful())
          {
            throw new IOException("获取最新 commit 失败：" + getCommitResponse.code() + " " + getCommitResponse.message());
          }

          JSONObject commitInfo = new JSONObject(getCommitResponse.body().string());
          String currentTreeSha = commitInfo.getJSONObject("tree").getString("sha");

          String newTreeSha;
          if (path.contains("/"))
          {
            newTreeSha = createNestedTree(client, token, owner, repo, currentTreeSha, path, blobSha);
          }
          else
          {
            JSONArray treeArray = new JSONArray();
            JSONObject fileEntry = new JSONObject();
            fileEntry.put("path", path);
            fileEntry.put("mode", "100644");
            fileEntry.put("type", "blob");
            fileEntry.put("sha", blobSha);
            treeArray.put(fileEntry);

            JSONObject createTreeBody = new JSONObject();
            createTreeBody.put("base_tree", currentTreeSha);
            createTreeBody.put("tree", treeArray);
                    
            Request createTreeRequest = new Request.Builder()
              .url(HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/trees"))
              .post(RequestBody.create(createTreeBody.toString(), MediaType.get("application/json; charset=utf-8")))
              .header("Authorization", "Bearer " + token)
              .header("Accept", "application/vnd.github.v3+json")
              .build();

            Response createTreeResponse = client.newCall(createTreeRequest).execute();
            if (!createTreeResponse.isSuccessful())
            {
              throw new IOException("创建 Tree 失败：" + createTreeResponse.code() + " " + createTreeResponse.message());
            }

            JSONObject treeInfo = new JSONObject(createTreeResponse.body().string());
            newTreeSha = treeInfo.getString("sha");
          }

          JSONArray parentArray = new JSONArray();
          parentArray.put(latestCommitSha);

          JSONObject createCommitBody = new JSONObject();
          createCommitBody.put("message", commitMessage);
          createCommitBody.put("tree", newTreeSha);
          createCommitBody.put("parents", parentArray);
          Request createCommitRequest = new Request.Builder()
            .url(HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/commits"))
            .post(RequestBody.create(createCommitBody.toString(), MediaType.get("application/json; charset=utf-8")))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github.v3+json")
            .build();

          Response createCommitResponse = client.newCall(createCommitRequest).execute();
          if (!createCommitResponse.isSuccessful())
          {
            throw new IOException("创建 Commit 失败：" + createCommitResponse.code() + " " + createCommitResponse.message());
          }

          JSONObject commitResult = new JSONObject(createCommitResponse.body().string());
          String newCommitSha = commitResult.getString("sha");

          JSONObject updateRefBody = new JSONObject();
          updateRefBody.put("sha", newCommitSha);
          updateRefBody.put("force", false);

          Request updateRefRequest = new Request.Builder()
            .url(HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/refs/heads/" + branch))
            .patch(RequestBody.create(updateRefBody.toString(), MediaType.get("application/json; charset=utf-8")))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github.v3+json")
            .build();

          Response updateRefResponse = client.newCall(updateRefRequest).execute();
          if (!updateRefResponse.isSuccessful())
          {
            throw new IOException("更新分支引用失敗：" + updateRefResponse.code() + " " + updateRefResponse.message());
          }

          JSONObject result = new JSONObject();
          result.put("status", "success");
          result.put("message", "提交成功！");
          result.put("blob_sha", blobSha);
          result.put("tree_sha", newTreeSha);
          result.put("commit_sha", newCommitSha);
          result.put("branch_updated", branch);
          result.put("fetched_at", System.currentTimeMillis());

          JSONObject debugInfo = new JSONObject();
          debugInfo.put("tool_name", "create_github_commit");
          debugInfo.put("params", new JSONObject()
            .put("owner", owner)
            .put("repo", repo)
            .put("path", path)
            .put("branch", branch)
            .put("encoding", encoding)
            .put("read_from_phone", readFromPhone));
          debugInfo.put("content_received_length", contentLength);
          debugInfo.put("verification_status", "OK");
                
          result.put("debug_info", debugInfo);

          callback.onResult(result);

        }
        catch (Exception e)
        {
          FileLogger.e(TAG, "执行出错", e);
          try
          {
            JSONObject error = new JSONObject();
            error.put("status", "error");
            error.put("message", e.getMessage());
            error.put("type", e.getClass().getSimpleName());
            callback.onResult(error);
          }
          catch (Exception ignored)
          {
          }
        }
      }
    );
  }

  /**
    * v18 修复：从内向外创建嵌套目录树，正确保留每一级的现有内容
    */
  private String createNestedTree(OkHttpClient client, String token, String owner, String repo,
    String baseTreeSha, String fullPath, String blobSha)
    throws IOException, org.json.JSONException
  {
        
    FileLogger.d(TAG, "[NestedTree v18] ========== 开始创建嵌套目录树 (v18 修复版) ==========");
    FileLogger.d(TAG, "[NestedTree v18] 完整路径：" + fullPath);
    FileLogger.d(TAG, "[NestedTree v18] 基础 Tree SHA: " + baseTreeSha.substring(0, 10) + "...");

    int lastSlashIndex = fullPath.lastIndexOf('/');
    String fileName = fullPath.substring(lastSlashIndex + 1);
    String dirPath = fullPath.substring(0, lastSlashIndex);

    FileLogger.d(TAG, "[NestedTree v18] 目录路径：" + dirPath);
    FileLogger.d(TAG, "[NestedTree v18] 文件名：" + fileName);
        
    String[] dirParts = dirPath.split("/");

    FileLogger.d(TAG, "[NestedTree v18] 目录层级数：" + dirParts.length);

    // ========== 第一步：从外向内遍历，收集每一级的现有 tree SHA ==========
    String[] parentTreeShas = new String[dirParts.length];
    String currentCheckTreeSha = baseTreeSha;
        
    for (int i = 0; i < dirParts.length; i++)
    {
      String currentDirName = dirParts[i];
      FileLogger.d(TAG, "[NestedTree v18] 第 " + i + " 层：在 Tree (" + currentCheckTreeSha.substring(0, 10) + "...) 中查找 '" + currentDirName + "'");

      String existingDirSha = findExistingDirectory(client, token, owner, repo, currentCheckTreeSha, currentDirName);
            
      if (existingDirSha != null)
      {
        FileLogger.d(TAG, "[NestedTree v18]   ✓ 找到目录 '" + currentDirName + "' (SHA: " + existingDirSha.substring(0, 10) + "...)");
        parentTreeShas[i] = existingDirSha;
        currentCheckTreeSha = existingDirSha;
      }
      else
      {
        FileLogger.d(TAG, "[NestedTree v18]   ✗ 未找到目录 '" + currentDirName + "'，后续层级都不存在");
        for (int j = i; j < dirParts.length; j++)
        {
          parentTreeShas[j] = null;
        }
        break;
      }
    }
        
    // ========== 第二步：从内向外创建 tree，合并现有内容 ==========
    String currentTreeSha = blobSha;
    String currentType = "blob";
        
    for (int i = dirParts.length - 1; i >= 0; i--)
    {
      String currentDirName = dirParts[i];
      String childName = (i == dirParts.length - 1) ? fileName : dirParts[i + 1];

      FileLogger.d(TAG, "[NestedTree v18] ========== 处理第 " + i + " 层目录：" + currentDirName + " (子条目：" + childName + ") ==========");

      String existingDirSha = parentTreeShas[i];

      if (existingDirSha != null)
      {
        FileLogger.d(TAG, "[NestedTree v18] ✓ 目录 '" + currentDirName + "' 已存在，获取现有内容并合并");

        JSONArray existingEntries = getTreeEntries(client, token, owner, repo, existingDirSha);
        FileLogger.d(TAG, "[NestedTree v18]   现有目录包含 " + existingEntries.length() + " 个条目");
                
        for (int j = 0; j < existingEntries.length(); j++)
        {
          JSONObject entry = existingEntries.getJSONObject(j);
          FileLogger.d(TAG, "[NestedTree v18]   现有条目 [" + j + "]: " + entry.getString("path") + " (" + entry.getString("type") + ")");
        }
                
        boolean found = false;
        for (int j = 0; j < existingEntries.length(); j++)
        {
          JSONObject entry = existingEntries.getJSONObject(j);
          if (entry.getString("path").equals(childName))
          {
            entry.put("sha", currentTreeSha);
            entry.put("type", currentType);
            entry.put("mode", currentType.equals("blob") ? "100644" : "040000");
            found = true;
            FileLogger.d(TAG, "[NestedTree v18]   ✓ 更新现有子条目 '" + childName + "'");
            break;
          }
        }
                
        if (!found)
        {
          FileLogger.d(TAG, "[NestedTree v18]   ✗ 添加新子条目 '" + childName + "'");
          JSONObject newEntry = new JSONObject();
          newEntry.put("path", childName);
          newEntry.put("mode", currentType.equals("blob") ? "100644" : "040000");
          newEntry.put("type", currentType);
          newEntry.put("sha", currentTreeSha);
          existingEntries.put(newEntry);
        }
                
        JSONObject treeBody = new JSONObject();
        treeBody.put("tree", existingEntries);
                
        Request createTreeRequest = new Request.Builder()
          .url(HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/trees"))
          .post(RequestBody.create(treeBody.toString(), MediaType.get("application/json; charset=utf-8")))
          .header("Authorization", "Bearer " + token)
          .header("Accept", "application/vnd.github.v3+json")
          .build();
                
        Response createTreeResponse = client.newCall(createTreeRequest).execute();
        if (!createTreeResponse.isSuccessful())
        {
          throw new IOException("创建合并 Tree 失败：" + createTreeResponse.code() + " " + createTreeResponse.message());
        }
                
        JSONObject treeInfo = new JSONObject(createTreeResponse.body().string());
        currentTreeSha = treeInfo.getString("sha");
        currentType = "tree";

        FileLogger.d(TAG, "[NestedTree v18]   ✓ 合并后 Tree SHA: " + currentTreeSha.substring(0, 10) + "...");
      }
      else
      {
        FileLogger.d(TAG, "[NestedTree v18] ✗ 目录 '" + currentDirName + "' 不存在，创建新目录");

        JSONArray treeArray = new JSONArray();
        JSONObject dirEntry = new JSONObject();
        dirEntry.put("path", childName);
        dirEntry.put("mode", currentType.equals("blob") ? "100644" : "040000");
        dirEntry.put("type", currentType);
        dirEntry.put("sha", currentTreeSha);
        treeArray.put(dirEntry);

        FileLogger.d(TAG, "[NestedTree v18]   → 新目录包含子条目 '" + childName + "'");

        JSONObject treeBody = new JSONObject();
        treeBody.put("tree", treeArray);
                
        Request createTreeRequest = new Request.Builder()
          .url(HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/trees"))
          .post(RequestBody.create(treeBody.toString(), MediaType.get("application/json; charset=utf-8")))
          .header("Authorization", "Bearer " + token)
          .header("Accept", "application/vnd.github.v3+json")
          .build();
                
        Response createTreeResponse = client.newCall(createTreeRequest).execute();
        if (!createTreeResponse.isSuccessful())
        {
          throw new IOException("创建目录 Tree 失败：" + createTreeResponse.code() + " " + createTreeResponse.message());
        }
                
        JSONObject treeInfo = new JSONObject(createTreeResponse.body().string());
        currentTreeSha = treeInfo.getString("sha");
        currentType = "tree";

        FileLogger.d(TAG, "[NestedTree v18]   ✓ 创建新目录 Tree SHA: " + currentTreeSha.substring(0, 10) + "...");
      }
    }
        
    FileLogger.d(TAG, "[NestedTree v18] 所有目录层级处理完成，最终 currentTreeSha: " + currentTreeSha.substring(0, 10) + "...");

    // ========== 第三步：合并最外层目录到基础 Tree ==========
    String outermostDirName = dirParts[0];
    FileLogger.d(TAG, "[NestedTree v18] ========== 合并最外层目录到基础 Tree ==========");

    JSONArray finalTreeArray = getTreeEntries(client, token, owner, repo, baseTreeSha);

    boolean found = false;
    for (int i = 0; i < finalTreeArray.length(); i++)
    {
      JSONObject entry = finalTreeArray.getJSONObject(i);
      if (entry.getString("path").equals(outermostDirName))
      {
        entry.put("sha", currentTreeSha);
        found = true;
        FileLogger.d(TAG, "[NestedTree v18] ✓ 基础 Tree 中已存在最外层目录，更新 SHA");
        break;
      }
    }
        
    if (!found)
    {
      FileLogger.d(TAG, "[NestedTree v18] ✗ 基础 Tree 中不存在最外层目录，添加新条目");
      JSONObject outermostEntry = new JSONObject();
      outermostEntry.put("path", outermostDirName);
      outermostEntry.put("mode", "040000");
      outermostEntry.put("type", "tree");
      outermostEntry.put("sha", currentTreeSha);
      finalTreeArray.put(outermostEntry);
    }
        
    JSONObject finalTreeBody = new JSONObject();
    finalTreeBody.put("base_tree", baseTreeSha);
    finalTreeBody.put("tree", finalTreeArray);
        
    Request createFinalTreeRequest = new Request.Builder()
      .url(HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/trees"))
      .post(RequestBody.create(finalTreeBody.toString(), MediaType.get("application/json; charset=utf-8")))
      .header("Authorization", "Bearer " + token)
      .header("Accept", "application/vnd.github.v3+json")
      .build();
        
    Response createFinalTreeResponse = client.newCall(createFinalTreeRequest).execute();
    if (!createFinalTreeResponse.isSuccessful())
    {
      throw new IOException("创建最终 Tree 失败：" + createFinalTreeResponse.code() + " " + createFinalTreeResponse.message());
    }
        
    JSONObject finalTreeInfo = new JSONObject(createFinalTreeResponse.body().string());
    String finalTreeSha = finalTreeInfo.getString("sha");
    FileLogger.d(TAG, "[NestedTree v18] ✓ 创建最终 Tree SHA: " + finalTreeSha.substring(0, 10) + "...");
    FileLogger.d(TAG, "[NestedTree v18] ========== 嵌套目录树创建成功！==========");

    return finalTreeSha;
  }

  private String findExistingDirectory(OkHttpClient client, String token, String owner, String repo, String treeSha, String dirName) throws IOException, org.json.JSONException
  {
    FileLogger.d(TAG, "[FindDir] 在 Tree (" + treeSha.substring(0, 10) + "...) 中查找目录：'" + dirName + "'");

    JSONArray entries = getTreeEntries(client, token, owner, repo, treeSha);
    FileLogger.d(TAG, "[FindDir] Tree 包含 " + entries.length() + " 个条目");
        
    for (int i = 0; i < entries.length(); i++)
    {
      JSONObject entry = entries.getJSONObject(i);
      String path = entry.getString("path");
      String type = entry.getString("type");
            
      if (path.equals(dirName) && "tree".equals(type))
      {
        String sha = entry.getString("sha");
        FileLogger.d(TAG, "[FindDir] ✓ 找到目录 '" + dirName + "' (SHA: " + sha.substring(0, 10) + "...)");
        return sha;
      }
    }
        
    FileLogger.d(TAG, "[FindDir] ✗ 未找到目录 '" + dirName + "'");
    return null;
  }

  private JSONArray getTreeEntries(OkHttpClient client, String token, String owner, String repo, String treeSha) throws IOException, org.json.JSONException
  {
    FileLogger.d(TAG, "[GetTree] 获取 Tree 条目：" + treeSha.substring(0, 10) + "...");
        
    HttpUrl url = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/trees/" + treeSha);
    Request request = new Request.Builder()
      .url(url)
      .header("Authorization", "Bearer " + token)
      .header("Accept", "application/vnd.github.v3+json")
      .build();
        
      Response response = client.newCall(request).execute();
      if (!response.isSuccessful())
      {
        throw new IOException("获取 Tree 条目失败：" + response.code() + " " + response.message());
      }
        
      JSONObject treeInfo = new JSONObject(response.body().string());
      JSONArray entries = treeInfo.getJSONArray("tree");
      FileLogger.d(TAG, "[GetTree] ✓ 获取到 " + entries.length() + " 个条目");

      return entries;
  }
}
