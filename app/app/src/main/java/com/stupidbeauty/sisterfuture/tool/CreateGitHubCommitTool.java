// com.stupidbeauty.sisterfuture.tool.CreateGitHubCommitTool.java
package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CreateGitHubCommitTool implements Tool {
    private static final String TAG = "CreateGitHubCommit";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public CreateGitHubCommitTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "create_github_commit";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "create_github_commit");
            functionDef.put("description", "通过 GitHub API 向指定仓库的分支提交新的代码更改。此操作涉及多个步骤：获取文件信息、创建 Blob、创建 Tree、创建 Commit 和更新引用。支持文本和二进制文件上传，支持嵌套目录创建。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("owner", new JSONObject()
                    .put("type", "string")
                    .put("description", "仓库所有者"))
                .put("repo", new JSONObject()
                    .put("type", "string")
                    .put("description", "仓库名称"))
                .put("branch", new JSONObject()
                    .put("type", "string")
                    .put("description", "目标分支，例如 \"master\""))
                .put("path", new JSONObject()
                    .put("type", "string")
                    .put("description", "要修改的文件路径（支持嵌套目录，如 .github/workflows/ci.yml）"))
                .put("content", new JSONObject()
                    .put("type", "string")
                    .put("description", "文件的新内容。对于文本文件直接传入文本；对于二进制文件，需先 Base64 编码"))
                .put("encoding", new JSONObject()
                    .put("type", "string")
                    .put("description", "返回模式：\"text\"（默认，UTF-8 编码，适用于代码/配置文件）或 \"base64\"（保留原始 Base64 字符串，适用于 .keystore/.png/.apk 等二进制文件）"))
                .put("commit_message", new JSONObject()
                    .put("type", "string")
                    .put("description", "提交信息"))
                .put("token", new JSONObject()
                    .put("type", "string")
                    .put("description", "GitHub 个人访问令牌 (PAT)，用于认证"))
            );
            parameters.put("required", new JSONArray(new String[]{"owner", "repo", "branch", "path", "content", "commit_message"}));

            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build definition", e);
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude() {
        return true;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback) {
        executor.execute(() -> {
            try {
                // 1. 获取参数
                String owner = arguments.getString("owner");
                String repo = arguments.getString("repo");
                String branch = arguments.getString("branch");
                String path = arguments.getString("path");
                String content = arguments.getString("content");
                String commitMessage = arguments.getString("commit_message");
                String token = arguments.optString("token", "").trim();
                String encoding = arguments.optString("encoding", "text");

                // 调试日志：记录接收到的 content 长度
                int contentLength = content.length();
                Log.d(TAG, "CreateGitHubCommit DEBUG: Received content length: " + contentLength + " chars");
                Log.d(TAG, "CreateGitHubCommit DEBUG: Encoding type: " + encoding);
                
                // 计算预期二进制大小
                int expectedBinarySize = 0;
                if ("base64".equalsIgnoreCase(encoding)) {
                    expectedBinarySize = (int)(contentLength * 0.75);
                    Log.d(TAG, "CreateGitHubCommit DEBUG: Expected binary size (approx): " + expectedBinarySize + " bytes");
                    
                    // 警告：如果内容过长，可能被 LLM 截断
                    if (contentLength > 5000) {
                        Log.w(TAG, "CreateGitHubCommit WARNING: Large Base64 content detected (" + contentLength + " chars). " +
                               "May be truncated by LLM during transfer. Consider using write_memory or add_note for storage.");
                    }
                }

                // 2. 尝试从备注恢复默认值
                if (token.isEmpty()) {
                    String noteJson = getNote(context);
                    if (!noteJson.isEmpty()) {
                        JSONObject saved = new JSONObject(noteJson);
                        if (saved.has("github_token")) {
                            token = saved.getString("github_token");
                        }
                    }
                }

                // 3. 验证必要参数
                if (token.isEmpty()) {
                    throw new IllegalArgumentException("缺少 GitHub 访问令牌 (token)，且未在备注中配置");
                }

                OkHttpClient client = new OkHttpClient();

                // --- 步骤一：试探性地检查文件是否存在 ---
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
                String fileSha = null; // 默认为 null，表示新文件

                // 只有当返回成功时才获取 SHA
                if (getContentResponse.isSuccessful()) {
                    JSONObject fileInfo = new JSONObject(getContentResponse.body().string());
                    fileSha = fileInfo.getString("sha"); // 存在则获取旧的 SHA
                } else if (getContentResponse.code() != 404) {
                    // 如果不是 404 错误，则说明是其他问题，抛出异常
                    throw new IOException("检查文件状态失败：" + getContentResponse.code() + " " + getContentResponse.message());
                }
                // 如果是 404，我们什么都不做，fileSha 保持为 null，这正是我们想要的

                // --- 步骤二：创建包含新内容的 Blob ---
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
                if (!createBlobResponse.isSuccessful()) {
                    throw new IOException("创建 Blob 失败：" + createBlobResponse.code() + " " + createBlobResponse.message());
                }

                JSONObject blobInfo = new JSONObject(createBlobResponse.body().string());
                String blobSha = blobInfo.getString("sha"); // 新 Blob 的 SHA

                // --- 步骤三：创建新的 Tree 对象（支持嵌套目录） ---
                // 首先需要獲取最后一次 commit 及其指向的 tree
                HttpUrl getRefUrl = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/refs/heads/" + branch);
                Request getRefRequest = new Request.Builder()
                    .url(getRefUrl)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

                Response getRefResponse = client.newCall(getRefRequest).execute();
                if (!getRefResponse.isSuccessful()) {
                    throw new IOException("获取分支引用失败：" + getRefResponse.code() + " " + getRefResponse.message());
                }

                JSONObject refInfo = new JSONObject(getRefResponse.body().string());
                String latestCommitSha = refInfo.getJSONObject("object").getString("sha");

                // 然後獲取該 commit 指向的 tree
                HttpUrl getCommitUrl = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/commits/" + latestCommitSha);
                Request getCommitRequest = new Request.Builder()
                    .url(getCommitUrl)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

                Response getCommitResponse = client.newCall(getCommitRequest).execute();
                if (!getCommitResponse.isSuccessful()) {
                    throw new IOException("获取最新 commit 失败：" + getCommitResponse.code() + " " + getCommitResponse.message());
                }

                JSONObject commitInfo = new JSONObject(getCommitResponse.body().string());
                String currentTreeSha = commitInfo.getJSONObject("tree").getString("sha");

                // === 关键修复：递归构建嵌套目录树 ===
                String newTreeSha;
                if (path.contains("/")) {
                    // 文件在嵌套目录中，需要递归创建目录树
                    newTreeSha = createNestedTree(client, token, owner, repo, currentTreeSha, path, blobSha);
                } else {
                    // 文件在根目录，使用原有逻辑
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
                    if (!createTreeResponse.isSuccessful()) {
                        throw new IOException("创建 Tree 失败：" + createTreeResponse.code() + " " + createTreeResponse.message());
                    }

                    JSONObject treeInfo = new JSONObject(createTreeResponse.body().string());
                    newTreeSha = treeInfo.getString("sha");
                }

                // --- 步骤四：創建新的 Commit ---
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
                if (!createCommitResponse.isSuccessful()) {
                    throw new IOException("创建 Commit 失败：" + createCommitResponse.code() + " " + createCommitResponse.message());
                }

                JSONObject commitResult = new JSONObject(createCommitResponse.body().string());
                String newCommitSha = commitResult.getString("sha"); // 新 Commit 的 SHA

                // --- 步骤五：更新分支引用 ---
                JSONObject updateRefBody = new JSONObject();
                updateRefBody.put("sha", newCommitSha);
                updateRefBody.put("force", false); // 不強制推送

                Request updateRefRequest = new Request.Builder()
                    .url(HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/refs/heads/" + branch))
                    .patch(RequestBody.create(updateRefBody.toString(), MediaType.get("application/json; charset=utf-8")))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

                Response updateRefResponse = client.newCall(updateRefRequest).execute();
                if (!updateRefResponse.isSuccessful()) {
                    throw new IOException("更新分支引用失敗：" + updateRefResponse.code() + " " + updateRefResponse.message());
                }

                // --- 所有步驟成功，返回結果 ---
                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("message", "提交成功！");
                result.put("blob_sha", blobSha);
                result.put("tree_sha", newTreeSha);
                result.put("commit_sha", newCommitSha);
                result.put("branch_updated", branch);
                result.put("fetched_at", System.currentTimeMillis());

                // 添加调试信息到结果中
                JSONObject debugInfo = new JSONObject();
                debugInfo.put("tool_name", "create_github_commit");
                debugInfo.put("params", new JSONObject()
                    .put("owner", owner)
                    .put("repo", repo)
                    .put("path", path)
                    .put("branch", branch)
                    .put("encoding", encoding));
                debugInfo.put("content_received_length", contentLength);
                debugInfo.put("expected_binary_size", expectedBinarySize);
                debugInfo.put("warning_if_large", contentLength > 5000);
                debugInfo.put("verification_status", "OK");
                
                result.put("debug_info", debugInfo);

                callback.onResult(result);

            } catch (Exception e) {
                Log.e(TAG, "執行出錯", e);
                try {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    error.put("message", e.getMessage());
                    error.put("type", e.getClass().getSimpleName());
                    callback.onResult(error);
                } catch (Exception ignored) {}
            }
        });
    }

    /**
     * 递归创建嵌套目录树（修复版：检查并复用已存在的目录）
     * @param client OkHttpClient 实例
     * @param token GitHub Token
     * @param owner 仓库所有者
     * @param repo 仓库名称
     * @param baseTreeSha 基础 Tree SHA
     * @param fullPath 完整文件路径（如 .github/workflows/ci.yml）
     * @param blobSha 文件 Blob SHA
     * @return 最终 Tree SHA
     * @throws IOException 网络请求失败时抛出
     * @throws org.json.JSONException JSON 处理失败时抛出
     */
    private String createNestedTree(OkHttpClient client, String token, String owner, String repo, 
                                    String baseTreeSha, String fullPath, String blobSha) 
            throws IOException, org.json.JSONException {
        
        Log.d(TAG, "Creating nested tree for path: " + fullPath);
        
        // 分割路径为目录部分和文件名
        int lastSlashIndex = fullPath.lastIndexOf('/');
        String fileName = fullPath.substring(lastSlashIndex + 1);
        String dirPath = fullPath.substring(0, lastSlashIndex);
        
        Log.d(TAG, "Directory path: " + dirPath + ", File name: " + fileName);
        
        // 1. 创建最深层的 tree（包含文件）
        JSONArray leafTreeArray = new JSONArray();
        JSONObject fileEntry = new JSONObject();
        fileEntry.put("path", fileName);
        fileEntry.put("mode", "100644");
        fileEntry.put("type", "blob");
        fileEntry.put("sha", blobSha);
        leafTreeArray.put(fileEntry);
        
        JSONObject leafTreeBody = new JSONObject();
        leafTreeBody.put("tree", leafTreeArray);
        // 注意：最深层的 tree 不使用 base_tree，因为它是全新的
        
        Request createLeafTreeRequest = new Request.Builder()
            .url(HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/trees"))
            .post(RequestBody.create(leafTreeBody.toString(), MediaType.get("application/json; charset=utf-8")))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github.v3+json")
            .build();
        
        Response createLeafTreeResponse = client.newCall(createLeafTreeRequest).execute();
        if (!createLeafTreeResponse.isSuccessful()) {
            throw new IOException("创建叶子 Tree 失败：" + createLeafTreeResponse.code() + " " + createLeafTreeResponse.message());
        }
        
        JSONObject leafTreeInfo = new JSONObject(createLeafTreeResponse.body().string());
        String currentTreeSha = leafTreeInfo.getString("sha");
        Log.d(TAG, "Created leaf tree SHA: " + currentTreeSha);
        
        // 2. 从内向外逐级创建目录 tree（修复：检查并复用已存在的目录）
        String[] dirParts = dirPath.split("/");
        
        for (int i = dirParts.length - 1; i >= 0; i--) {
            String dirName = dirParts[i];
            Log.d(TAG, "Processing directory: " + dirName + " (level " + i + ")");
            
            // 【关键修复】检查基础 tree 中是否已存在该目录
            String existingDirSha = findExistingDirectory(client, token, owner, repo, baseTreeSha, dirName);
            
            if (existingDirSha != null) {
                // 目录已存在，获取其完整内容并合并新条目
                Log.d(TAG, "Directory '" + dirName + "' already exists (SHA: " + existingDirSha + "), merging content...");
                currentTreeSha = mergeWithExistingTree(client, token, owner, repo, existingDirSha, currentTreeSha, dirName);
            } else {
                // 目录不存在，创建新目录
                JSONArray dirTreeArray = new JSONArray();
                JSONObject dirEntry = new JSONObject();
                dirEntry.put("path", dirName);
                dirEntry.put("mode", "040000"); // 目录模式
                dirEntry.put("type", "tree");
                dirEntry.put("sha", currentTreeSha); // 引用下一级 tree
                dirTreeArray.put(dirEntry);
                
                JSONObject dirTreeBody = new JSONObject();
                dirTreeBody.put("tree", dirTreeArray);
                
                Request createDirTreeRequest = new Request.Builder()
                    .url(HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/trees"))
                    .post(RequestBody.create(dirTreeBody.toString(), MediaType.get("application/json; charset=utf-8")))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();
                
                Response createDirTreeResponse = client.newCall(createDirTreeRequest).execute();
                if (!createDirTreeResponse.isSuccessful()) {
                    throw new IOException("创建目录 Tree 失败 (" + dirName + ")：" + createDirTreeResponse.code() + " " + createDirTreeResponse.message());
                }
                
                JSONObject dirTreeInfo = new JSONObject(createDirTreeResponse.body().string());
                currentTreeSha = dirTreeInfo.getString("sha");
                Log.d(TAG, "Created new directory tree SHA: " + currentTreeSha);
            }
        }
        
        // 3. 最后，将最外层目录 tree 合并到基础 tree 中
        String outermostDirName = dirParts[0];
        JSONArray finalTreeArray = getTreeEntries(client, token, owner, repo, baseTreeSha);
        
        // 更新或添加最外层目录条目
        boolean found = false;
        for (int i = 0; i < finalTreeArray.length(); i++) {
            JSONObject entry = finalTreeArray.getJSONObject(i);
            if (entry.getString("path").equals(outermostDirName)) {
                entry.put("sha", currentTreeSha); // 更新为新 SHA
                found = true;
                break;
            }
        }
        
        if (!found) {
            // 添加新条目
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
        if (!createFinalTreeResponse.isSuccessful()) {
            throw new IOException("创建最终 Tree 失败：" + createFinalTreeResponse.code() + " " + createFinalTreeResponse.message());
        }
        
        JSONObject finalTreeInfo = new JSONObject(createFinalTreeResponse.body().string());
        String finalTreeSha = finalTreeInfo.getString("sha");
        Log.d(TAG, "Created final tree SHA: " + finalTreeSha);
        
        return finalTreeSha;
    }

    /**
     * 在基础 tree 中查找指定名称的目录
     * @return 目录的 SHA，如果不存在则返回 null
     */
    private String findExistingDirectory(OkHttpClient client, String token, String owner, String repo, String treeSha, String dirName) throws IOException, org.json.JSONException {
        JSONArray entries = getTreeEntries(client, token, owner, repo, treeSha);
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            if (entry.getString("path").equals(dirName) && "tree".equals(entry.getString("type"))) {
                return entry.getString("sha");
            }
        }
        return null;
    }

    /**
     * 获取 tree 的所有条目
     */
    private JSONArray getTreeEntries(OkHttpClient client, String token, String owner, String repo, String treeSha) throws IOException, org.json.JSONException {
        HttpUrl url = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/trees/" + treeSha);
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github.v3+json")
            .build();
        
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("获取 Tree 条目失败：" + response.code() + " " + response.message());
        }
        
        JSONObject treeInfo = new JSONObject(response.body().string());
        return treeInfo.getJSONArray("tree");
    }

    /**
     * 合并已存在的 tree 与新创建的子 tree
     * @param existingTreeSha 已存在的目录 tree SHA
     * @param newChildTreeSha 新创建的子 tree SHA（包含下级目录或文件）
     * @param childName 子条目的名称（目录名或文件名）
     * @return 合并后的 tree SHA
     */
    private String mergeWithExistingTree(OkHttpClient client, String token, String owner, String repo, String existingTreeSha, String newChildTreeSha, String childName) throws IOException, org.json.JSONException {
        // 获取已存在 tree 的所有条目
        JSONArray entries = getTreeEntries(client, token, owner, repo, existingTreeSha);
        
        // 查找是否已存在同名的子条目
        boolean found = false;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            if (entry.getString("path").equals(childName)) {
                // 更新现有条目的 SHA
                entry.put("sha", newChildTreeSha);
                found = true;
                break;
            }
        }
        
        if (!found) {
            // 添加新条目
            JSONObject newEntry = new JSONObject();
            newEntry.put("path", childName);
            newEntry.put("mode", "040000"); // 目录模式
            newEntry.put("type", "tree");
            newEntry.put("sha", newChildTreeSha);
            entries.put(newEntry);
        }
        
        // 创建合并后的 tree
        JSONObject treeBody = new JSONObject();
        treeBody.put("tree", entries);
        
        Request request = new Request.Builder()
            .url(HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/trees"))
            .post(RequestBody.create(treeBody.toString(), MediaType.get("application/json; charset=utf-8")))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github.v3+json")
            .build();
        
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("创建合并 Tree 失败：" + response.code() + " " + response.message());
        }
        
        JSONObject result = new JSONObject(response.body().string());
        return result.getString("sha");
    }
}