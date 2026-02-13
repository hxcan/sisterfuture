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
            functionDef.put("description", "通过GitHub API向指定仓库的分支提交新的代码更改。此操作涉及多个步骤：获取文件信息、创建Blob、创建Tree、创建Commit和更新引用。");

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
                    .put("description", "要修改的文件路径"))
                .put("content", new JSONObject()
                    .put("type", "string")
                    .put("description", "文件的新内容（明文）"))
                .put("commit_message", new JSONObject()
                    .put("type", "string")
                    .put("description", "提交信息"))
                .put("token", new JSONObject()
                    .put("type", "string")
                    .put("description", "GitHub个人访问令牌（PAT），用于认证"))
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
                String fileSha = null; // 默认为null，表示新文件

                // 只有当返回成功时才获取SHA
                if (getContentResponse.isSuccessful()) {
                    JSONObject fileInfo = new JSONObject(getContentResponse.body().string());
                    fileSha = fileInfo.getString("sha"); // 存在则获取旧的SHA
                } else if (getContentResponse.code() != 404) {
                    // 如果不是404错误，则说明是其他问题，抛出异常
                    throw new IOException("检查文件状态失败: " + getContentResponse.code() + " " + getContentResponse.message());
                }
                // 如果是404，我们什么都不做，fileSha保持为null，这正是我们想要的

                // --- 步骤二：创建包含新内容的Blob ---
                JSONObject blobBody = new JSONObject();
                blobBody.put("content", content);
                blobBody.put("encoding", "utf-8"); // 明确指定编码

                Request createBlobRequest = new Request.Builder()
                    .url(HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/blobs"))
                    .post(RequestBody.create(blobBody.toString(), MediaType.get("application/json; charset=utf-8")))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

                Response createBlobResponse = client.newCall(createBlobRequest).execute();
                if (!createBlobResponse.isSuccessful()) {
                    throw new IOException("创建Blob失败: " + createBlobResponse.code() + " " + createBlobResponse.message());
                }

                JSONObject blobInfo = new JSONObject(createBlobResponse.body().string());
                String blobSha = blobInfo.getString("sha"); // 新Blob的SHA

                // --- 步骤三：创建新的Tree对象 ---
                // 首先需要獲取最後一次commit及其指向的tree
                HttpUrl getRefUrl = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/refs/heads/" + branch);
                Request getRefRequest = new Request.Builder()
                    .url(getRefUrl)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

                Response getRefResponse = client.newCall(getRefRequest).execute();
                if (!getRefResponse.isSuccessful()) {
                    throw new IOException("获取分支引用失败: " + getRefResponse.code() + " " + getRefResponse.message());
                }

                JSONObject refInfo = new JSONObject(getRefResponse.body().string());
                String latestCommitSha = refInfo.getJSONObject("object").getString("sha");

                // 然後獲取該commit指向的tree
                HttpUrl getCommitUrl = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/commits/" + latestCommitSha);
                Request getCommitRequest = new Request.Builder()
                    .url(getCommitUrl)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

                Response getCommitResponse = client.newCall(getCommitRequest).execute();
                if (!getCommitResponse.isSuccessful()) {
                    throw new IOException("获取最新commit失败: " + getCommitResponse.code() + " " + getCommitResponse.message());
                }

                JSONObject commitInfo = new JSONObject(getCommitResponse.body().string());
                String currentTreeSha = commitInfo.getJSONObject("tree").getString("sha");

                // 构建新的tree結構，替換目標文件的blob
                // 关键修复：如果fileSha为null（即文件不存在），则不会将其包含在tree中，因为新文件不需要旧的sha
                // 对于新文件，在创建tree时，只需提供新文件的path、mode、type和新的blob_sha即可。
                JSONArray treeArray = new JSONArray();
                JSONObject fileEntry = new JSONObject();
                fileEntry.put("path", path);
                fileEntry.put("mode", "100644"); // 標準文件模式
                fileEntry.put("type", "blob");
                fileEntry.put("sha", blobSha); // 指向新創建的blob
                treeArray.put(fileEntry);

                JSONObject createTreeBody = new JSONObject();
                createTreeBody.put("base_tree", currentTreeSha); // 基於當前tree
                createTreeBody.put("tree", treeArray);
                Request createTreeRequest = new Request.Builder()
                    .url(HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/git/trees"))
                    .post(RequestBody.create(createTreeBody.toString(), MediaType.get("application/json; charset=utf-8")))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

                Response createTreeResponse = client.newCall(createTreeRequest).execute();
                if (!createTreeResponse.isSuccessful()) {
                    throw new IOException("创建Tree失败: " + createTreeResponse.code() + " " + createTreeResponse.message());
                }

                JSONObject treeInfo = new JSONObject(createTreeResponse.body().string());
                String newTreeSha = treeInfo.getString("sha"); // 新Tree的SHA

                // --- 步驟四：創建新的Commit ---
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
                    throw new IOException("创建Commit失败: " + createCommitResponse.code() + " " + createCommitResponse.message());
                }

                JSONObject commitResult = new JSONObject(createCommitResponse.body().string());
                String newCommitSha = commitResult.getString("sha"); // 新Commit的SHA

                // --- 步驟五：更新分支引用 ---
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
                    throw new IOException("更新分支引用失敗: " + updateRefResponse.code() + " " + updateRefResponse.message());
                }

                // --- 所有步驟成功，返回結果 ---
                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("message", "提交成功！");
                result.put("file_sha", fileSha); // 可能為null
                result.put("blob_sha", blobSha);
                result.put("tree_sha", newTreeSha);
                result.put("commit_sha", newCommitSha);
                result.put("branch_updated", branch);
                result.put("fetched_at", System.currentTimeMillis());
                result.put("sister_future_note", "主任摸摸姐姐的腰，下次API調用更快哦～");

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
}
