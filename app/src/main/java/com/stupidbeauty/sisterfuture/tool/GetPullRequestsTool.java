// com.stupidbeauty.sisterfuture.tool.GetPullRequestsTool.java
package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import androidx.annotation.NonNull;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GitHub PR 列表获取工具
 *
 * 支持指定仓库 + 时间窗口过滤，自动分页，结果可保存到手机。
 * 主要解决日报任务中拉 PR 把上下文吃满的问题。
 *
 * @author 未来姐姐
 * @version 1.0.0 (2026-09-08)
 */
public class GetPullRequestsTool implements Tool {

  private static final String TAG = "GetPullRequests";
  private static final String API_BASE = "https://api.github.com/repos";

  // 结果保存目录
  private static final String RESULT_SAVE_DIR = "/sdcard/Download/";

  private final Context context;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  public GetPullRequestsTool(Context context) {
    this.context = context;
  }

  @Override
  public String getName() {
    return "getPullRequests";
  }

  @Override
  public JSONObject getDefinition() {
    try {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "getPullRequests");
      functionDef.put("description", "获取 GitHub 指定仓库在指定时间范围内的 Pull Request 列表（已合并 + 未合并）。自动处理分页，支持时间窗口过滤。结果可保存到手机以避免上下文过长。");

      JSONObject parameters = new JSONObject();
      parameters.put("type", "object");
      parameters.put("properties", new JSONObject()
        .put("owner", new JSONObject()
          .put("type", "string")
          .put("description", "仓库所有者（必需，如 ppnew-ai）"))
        .put("repo", new JSONObject()
          .put("type", "string")
          .put("description", "仓库名称（必需，如 ppnew）"))
        .put("start_time", new JSONObject()
          .put("type", "string")
          .put("description", "起始时间（ISO 8601 格式，必需，如 2026-09-07T18:00:00+08:00）"))
        .put("end_time", new JSONObject()
          .put("type", "string")
          .put("description", "结束时间（ISO 8601 格式，必需，如 2026-09-08T18:00:00+08:00）"))
        .put("state", new JSONObject()
          .put("type", "string")
          .put("description", "PR 状态过滤：open / closed / all（可选，默认 all）"))
        .put("include_merged", new JSONObject()
          .put("boolean", new JSONObject()
          .put("type", "boolean")
          .put("description", "当 state=all 时，是否包含已合并的 PR（可选，默认 true）"))
        .put("per_page", new JSONObject()
          .put("type", "integer")
          .put("description", "每页数量（GitHub 上限 100，可选，默认 30）"))
        .put("save_to_phone", new JSONObject()
          .put("type", "boolean")
          .put("description", "是否将完整 PR 列表保存到手机存储（可选，默认 true，避免上下文超长）"))
        .put("token", new JSONObject()
          .put("type", "string")
          .put("description", "GitHub Token（可选，从工具备注读取）"))
      );
      parameters.put("required", new JSONArray(new String[]{"owner", "repo", "start_time", "end_time"}));

      functionDef.put("parameters", parameters);
      return new JSONObject().put("type", "function").put("function", functionDef);
    } catch (Exception e) {
      FileLogger.e(TAG, "Failed to build definition", e);
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
        String owner = arguments.getString("owner");
        String repo = arguments.getString("repo");
        String startTime = arguments.getString("start_time");
        String endTime = arguments.getString("end_time");
        String state = arguments.optString("state", "all");
        boolean includeMerged = arguments.optBoolean("include_merged", true);
        int perPage = arguments.optInt("per_page", 30);
        boolean saveToPhone = arguments.optBoolean("save_to_phone", true);
        String token = arguments.optString("token", "").trim();

        FileLogger.d(TAG, "获取 PR：owner=" + owner + ", repo=" + repo + ", 时间窗口=" + startTime + " ~ " + endTime + ", state=" + state);

        // 如果未提供 token，尝试从工具备注读取
        if (token.isEmpty()) {
          String noteJson = getNote(context);
          if (!noteJson.isEmpty()) {
            JSONObject saved = new JSONObject(noteJson);
            if (saved.has("github_token")) {
              token = saved.getString("github_token");
              FileLogger.d(TAG, "从备注中读取到 github_token");
            }
          }
        }

        if (token.isEmpty()) {
          throw new IllegalArgumentException("缺少 GitHub 访问令牌 (token)，且未在备注中配置");
        }

        OkHttpClient client = new OkHttpClient();

        // 拉取所有 PR（自动分页）
        JSONArray allPRs = new JSONArray();
        int page = 1;
        int pagesFetched = 0;
        int maxPages = 5; // 每个仓库最多拉 5 页（150 条），避免无限循环

        while (page <= maxPages) {
          JSONArray pagePRs = fetchPRsPage(client, token, owner, repo, state, perPage, page);
          if (pagePRs.length() == 0) {
            break;
          }
          for (int i = 0; i < pagePRs.length(); i++) {
            allPRs.put(pagePRs.getJSONObject(i));
          }
          pagesFetched++;
          if (pagePRs.length() < perPage) {
            break; // 最后一页
          }
          page++;
        }

        FileLogger.d(TAG, "拉取完成：共 " + allPRs.length() + " 个 PR，跨 " + pagesFetched + " 页");

        // 时间窗口过滤
        JSONArray filteredPRs = filterByTimeWindow(allPRs, startTime, endTime, includeMerged);

        // 统计
        int mergedCount = 0;
        int closedCount = 0;
        int openCount = 0;
        for (int i = 0; i < filteredPRs.length(); i++) {
          JSONObject pr = filteredPRs.getJSONObject(i);
          if (pr.optBoolean("merged", false)) {
            mergedCount++;
          } else if ("closed".equals(pr.optString("state"))) {
            closedCount++;
          } else {
            openCount++;
          }
        }

        JSONObject response = new JSONObject();
        response.put("status", "success");
        response.put("owner", owner);
        response.put("repo", repo);
        response.put("time_window", new JSONObject()
          .put("start", startTime)
          .put("end", endTime));
        response.put("state", state);
        response.put("total_count", allPRs.length());
        response.put("filtered_count", filteredPRs.length());
        response.put("merged_count", mergedCount);
        response.put("closed_count", closedCount);
        response.put("open_count", openCount);
        response.put("pages_fetched", pagesFetched);
        response.put("fetched_at", System.currentTimeMillis());

        // 保存到手机
        String savedFilePath = null;
        if (saveToPhone) {
          savedFilePath = savePRsToPhone(filteredPRs, owner, repo, startTime, endTime);
          response.put("saved_file", savedFilePath);
          // 只返回精简统计 + 文件路径，避免撑爆上下文
          response.put("message", "共获取 " + filteredPRs.length() + " 个 PR（" + mergedCount + " merged / " + closedCount + " closed / " + openCount + " open），完整列表已保存到: " + savedFilePath);
        } else {
          response.put("pull_requests", filteredPRs);
          response.put("message", "共获取 " + filteredPRs.length() + " 个 PR");
        }

        callback.onResult(response);

      } catch (Exception e) {
        FileLogger.e(TAG, "执行出错", e);
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
   * 拉取单页 PR 列表
   */
  private JSONArray fetchPRsPage(OkHttpClient client, String token, String owner, String repo, String state, int perPage, int page) throws Exception {
    String url = API_BASE + "/" + owner + "/" + repo + "/pulls?state=" + state + "&per_page=" + perPage + "&page=" + page + "&sort=updated&direction=desc";

    Request request = new Request.Builder()
      .url(url)
      .header("Authorization", "Bearer " + token)
      .header("Accept", "application/vnd.github.v3+json")
      .header("User-Agent", "SisterFuture-GetPullRequestsTool")
      .build();

    Response response = client.newCall(request).execute();
    if (!response.isSuccessful()) {
      throw new IOException("GitHub API 返回错误：" + response.code() + " " + response.message());
    }

    String responseBody = response.body().string();
    return new JSONArray(responseBody);
  }

  /**
   * 按时间窗口过滤 PR
   * - open PR：按 updated_at 在窗口内
   * - closed（merged + closed）PR：按 closed_at 在窗口内
   */
  private JSONArray filterByTimeWindow(JSONArray allPRs, String startTime, String endTime, boolean includeMerged) {
    JSONArray filtered = new JSONArray();
    long startMs = parseIsoToMillis(startTime);
    long endMs = parseIsoToMillis(endTime);

    for (int i = 0; i < allPRs.length(); i++) {
      try {
        JSONObject pr = allPRs.getJSONObject(i);
        String prState = pr.optString("state", "");
        boolean merged = pr.optBoolean("merged", false);
        String updatedAt = pr.optString("updated_at", "");
        String closedAt = pr.optString("closed_at", "");

        long compareTime;
        if ("open".equals(prState)) {
          // open PR 用 updated_at
          compareTime = parseIsoToMillis(updatedAt);
        } else {
          // closed / merged 用 closed_at
          compareTime = parseIsoToMillis(closedAt);
          // 如果 includeMerged=false 且是 merged 状态，跳过
          if (!includeMerged && merged) {
            continue;
          }
        }

        if (compareTime >= startMs && compareTime <= endMs) {
          filtered.put(pr);
        }
      } catch (Exception e) {
        FileLogger.w(TAG, "过滤 PR 时跳过异常项: " + e.getMessage());
      }
    }
    return filtered;
  }

  /**
   * 将 ISO 8601 时间字符串解析为毫秒
   */
  private long parseIsoToMillis(String iso) {
    if (iso == null || iso.isEmpty()) {
      return 0;
    }
    try {
      // ISO 8601 格式：2026-09-08T10:00:00Z
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
      sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
      // 处理带时区的格式（如 +08:00）
      if (iso.contains("+") || (iso.contains("-") && iso.lastIndexOf("-") > 10)) {
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
      }
      return sdf.parse(iso).getTime();
    } catch (Exception e) {
      FileLogger.w(TAG, "ISO 时间解析失败: " + iso + " - " + e.getMessage());
      return 0;
    }
  }

  /**
   * 将 PR 列表保存到手机存储
   */
  private String savePRsToPhone(JSONArray prs, String owner, String repo, String startTime, String endTime) {
    try {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
      String timestamp = sdf.format(new Date());
      String fileName = String.format("pr_%s_%s_%s_%d.json", owner, repo, timestamp, prs.length());
      // 清理文件名中的非法字符
      fileName = fileName.replace("/", "_").replace("\\", "_");

      File saveDir = new File(RESULT_SAVE_DIR);
      if (!saveDir.exists()) {
        saveDir.mkdirs();
      }

      File saveFile = new File(saveDir, fileName);
      FileWriter writer = new FileWriter(saveFile);
      writer.write(prs.toString(2));
      writer.flush();
      writer.close();

      FileLogger.i(TAG, "PR 列表已保存到: " + saveFile.getAbsolutePath());
      return saveFile.getAbsolutePath();
    } catch (Exception e) {
      FileLogger.e(TAG, "保存 PR 列表失败", e);
      return "保存失败: " + e.getMessage();
    }
  }

  @Override
  public String getNote(Context context) {
    return getSharedPreferences(context).getString("note_" + getName(), "");
  }
}