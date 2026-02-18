package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;

/**
 * 工具：创建 Git 分支。
 */
public class CreateGitBranchTool implements Tool {
  private static final String TAG = "CreateGitBranch";
  private final Context context;
  private final OkHttpClient httpClient = new OkHttpClient();
  private final Gson gson = new Gson();

  public CreateGitBranchTool(Context context) {
    this.context = context;
  }

  @Override
  public String getName() {
    return "create_git_branch";
  }

  @Override
  public JSONObject getDefinition() {
    try {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "create_git_branch");
      functionDef.put("description", "为未来姐姐项目开发一个专用工具 `create_git_branch`，用于自动化创建 Git 分支。\\\n\\\n## 功能要求：\\\n- 支持创建新分支（如 `release`、`feature\/x`）\\\n- 支持指定上游分支作为基线（如 `main`）\\\n- 支持自动推送至远程仓库（GitHub）\\\n- 提供错误处理与冲突检测机制（如分支已存在）\\\n- 返回成功\/失败状态及详细日志");

      JSONObject parameters = new JSONObject();
      parameters.put("type", "object");
      parameters.put("properties", new JSONObject()
          .put("owner", new JSONObject()
              .put("type", "string")
              .put("description", "仓库所有者"))
          .put("repo", new JSONObject()
              .put("type", "string")
              .put("description", "仓库名称"))
          .put("new_branch", new JSONObject()
              .put("type", "string")
              .put("description", "要创建的新分支名称"))
          .put("base_branch", new JSONObject()
              .put("type", "string")
              .put("description", "用作基线的现有分支，例如 main 或 develop"))
          .put("token", new JSONObject()
              .put("type", "string")
              .put("description", "GitHub个人访问令牌（PAT），用于认证"))
      );
      parameters.put("required", new JSONArray(new String[]{"owner", "repo", "new_branch", "base_branch"}));

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
  public boolean isAsync() {
    return true;
  }

  @Override
  public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback) {
    // 启动后台线程执行网络请求
    new Thread(() -> {
      try {
        // 1. 提取参数
        String owner = arguments.getString("owner");
        String repo = arguments.getString("repo");
        String newBranch = arguments.getString("new_branch");
        String baseBranch = arguments.getString("base_branch");
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

        // 3. 验證必要參數
        if (token.isEmpty()) {
          throw new IllegalArgumentException("缺少 GitHub 访问令牌 (token)，且未在备注中配置");
        }

        // --- 步驥一：獲取基線分支的最新提交SHA ---
        HttpUrl getBaseCommitUrl = HttpUrl.parse("https:\\/\/api.github.com\\\/repos\\\/"
            )
            .newBuilder()
            .addPathSegment(owner)
            .addPathSegment(repo)
            .addPathSegment("git")
            .addPathSegment("refs")
            .addPathSegment("heads")
            .addPathSegment(baseBranch)
            .build();

        Request getBaseCommitRequest = new Request.Builder()
            .url(getBaseCommitUrl)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application\\\/vnd.github.v3+json")
            .build();

        Response getBaseCommitResponse = httpClient.newCall(getBaseCommitRequest).execute();
        if (!getBaseCommitResponse.isSuccessful()) {
          throw new IOException("获取基线分支信息失败: " + getBaseCommitResponse.code() + " " + getBaseCommitResponse.message());
        }

        JSONObject baseRefInfo = new JSONObject(getBaseCommitResponse.body().string());
        String baseCommitSha = baseRefInfo.getJSONObject("object").getString("sha");

        // --- 步驥二：創建新的分支引用 ---
        JSONObject createRefBody = new JSONObject();
        createRefBody.put("ref", "refs\/heads\/" + newBranch);
        createRefBody.put("sha", baseCommitSha);

        HttpUrl createRefUrl = HttpUrl.parse("https:\\/\/api.github.com\\\/repos\\\/"
            )
            .newBuilder()
            .addPathSegment(owner)
            .addPathSegment(repo)
            .addPathSegment("git")
            .addPathSegment("refs")
            .build();

        Request createRefRequest = new Request.Builder()
            .url(createRefUrl)
            .post(RequestBody.create(createRefBody.toString(), MediaType.get("application\\\/json; charset=utf-8")))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application\\\/vnd.github.v3+json")
            .build();

        Response createRefResponse = httpClient.newCall(createRefRequest).execute();
        if (!createRefResponse.isSuccessful()) {
          if (createRefResponse.code() == 422) {
            throw new IOException("分支 '" + newBranch + "' 已存在。");
          } else {
            throw new IOException("创建分支引用失败: " + createRefResponse.code() + " " + createRefResponse.message());
          }
        }

        // --- 所有步骤成功，返回结果 ---
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("message", "已成功创建新分支 '" + newBranch + "'，基于分支 '" + baseBranch + "'.");
        result.put("new_branch", newBranch);
        result.put("base_branch", baseBranch);
        result.put("created_from_sha", baseCommitSha);
        result.put("fetched_at", System.currentTimeMillis());

        callback.onResult(result);

      } catch (Exception e) {
        e.printStackTrace();
        try {
          JSONObject error = new JSONObject();
          error.put("status", "error");
          error.put("message", e.getMessage());
          error.put("type", e.getClass().getSimpleName());
          callback.onResult(error);
        } catch (Exception ignored) {}
      }
    }).start();
  }
}
