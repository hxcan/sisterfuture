// com.stupidbeauty.sisterfuture.tool.CompareGitHubBranchesTool.java
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
 * GitHub 分支对比工具
 *
 * 调用 GitHub Compare API 比较两个分支（ref）的差异，输出精简摘要：
 * 领先/落后关系、提交列表、变更文件列表（增删行数、状态）。
 * 完整结果可保存到手机存储，避免撑爆上下文。
 *
 * @author 未来姐姐
 * @version 1.0.0 (2026-11-16)
 */
public class CompareGitHubBranchesTool implements Tool {

  private static final String TAG = "CompareBranches";
  private static final String API_BASE = "https://api.github.com/repos";

  // 结果保存目录
  private static final String RESULT_SAVE_DIR = "/sdcard/Download/";

  /** 单个文件补丁的最大保留行数，避免大文件撑爆上下文。 */
  private static final int MAX_PATCH_LINES = 200;

  private final Context context;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  public CompareGitHubBranchesTool(Context context) {
    this.context = context;
  }

  @Override
  public String getName() {
    return "compareGitHubBranches";
  }

  @Override
  public JSONObject getDefinition() {
    try {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "compareGitHubBranches");
      functionDef.put("description", "比较 GitHub 仓库中两个分支（或任意两个 ref）的差异。返回领先/落后关系、提交列表、变更文件列表（含增删行数与状态），可选保存完整结果到手机。");

      JSONObject ownerProp = new JSONObject();
      ownerProp.put("type", "string");
      ownerProp.put("description", "仓库所有者（必需，如 hxcan）");

      JSONObject repoProp = new JSONObject();
      repoProp.put("type", "string");
      repoProp.put("description", "仓库名称（必需，如 sisterfuture）");

      JSONObject baseProp = new JSONObject();
      baseProp.put("type", "string");
      baseProp.put("description", "基准分支（必需，如 master）。用于计算 head 相对 base 的差异");

      JSONObject headProp = new JSONObject();
      headProp.put("type", "string");
      headProp.put("description", "目标分支（必需，如 feature/xxx）。将被对比的分支");

      JSONObject patchProp = new JSONObject();
      patchProp.put("type", "boolean");
      patchProp.put("description", "是否包含每个文件的逐行差异（可选，默认 false）。文件多时建议关闭或配合 save_to_phone");

      JSONObject maxFilesProp = new JSONObject();
      maxFilesProp.put("type", "integer");
      maxFilesProp.put("description", "最多返回多少个文件的详情（可选，默认 50）");

      JSONObject saveToPhoneProp = new JSONObject();
      saveToPhoneProp.put("type", "boolean");
      saveToPhoneProp.put("description", "是否将完整对比结果保存到手机存储（可选，默认 true，避免上下文超长）");

      JSONObject tokenProp = new JSONObject();
      tokenProp.put("type", "string");
      tokenProp.put("description", "GitHub Token（可选，从工具备注读取）");

      JSONObject properties = new JSONObject();
      properties.put("owner", ownerProp);
      properties.put("repo", repoProp);
      properties.put("base", baseProp);
      properties.put("head", headProp);
      properties.put("include_patch", patchProp);
      properties.put("max_files", maxFilesProp);
      properties.put("save_to_phone", saveToPhoneProp);
      properties.put("token", tokenProp);

      JSONObject parameters = new JSONObject();
      parameters.put("type", "object");
      parameters.put("properties", properties);
      parameters.put("required", new JSONArray(new String[]{"owner", "repo", "base", "head"}));

      functionDef.put("parameters", parameters);
      return new JSONObject().put("type", "function").put("function", functionDef);
    } catch (Exception e) {
      FileLogger.e(TAG, "Failed to build definition", e);
      return new JSONObject();
    }
  }

  /** Parse and validate arguments, rejecting nested objects and nulls in string slots. */
  static JSONObject parseArguments(JSONObject arguments) throws org.json.JSONException {
    JSONObject parsed = new JSONObject();
    for (String key : new String[]{"owner", "repo", "base", "head"}) {
      String value = readText(arguments, key, "");
      if (value.isEmpty()) {
        throw new IllegalArgumentException("缺少或无效的必填参数: " + key);
      }
      parsed.put(key, value);
    }
    parsed.put("include_patch", ToolArgumentUtils.safeGetBoolean(arguments, "include_patch", false));
    parsed.put("save_to_phone", ToolArgumentUtils.safeGetBoolean(arguments, "save_to_phone", true));
    int maxFiles = ToolArgumentUtils.safeGetInt(arguments, "max_files", 50);
    parsed.put("max_files", maxFiles < 1 || maxFiles > 300 ? 50 : maxFiles);
    parsed.put("token", readText(arguments, "token", ""));
    return parsed;
  }

  /** Read a string argument, tolerating case differences in the key name. */
  private static String readText(JSONObject arguments, String key, String fallback) {
    if (arguments == null) return fallback;
    String actualKey = key;
    if (!arguments.has(key)) {
      java.util.Iterator<String> keys = arguments.keys();
      while (keys.hasNext()) {
        String candidate = keys.next();
        if (candidate.equalsIgnoreCase(key)) {
          actualKey = candidate;
          break;
        }
      }
    }
    Object raw = arguments.opt(actualKey);
    // Do not turn JSON null or a nested object into a repository/branch string.
    if (raw == null || raw == JSONObject.NULL || raw instanceof JSONObject || raw instanceof JSONArray) {
      return fallback;
    }
    String value = ToolArgumentUtils.safeGetString(arguments, actualKey, fallback).trim();
    return value.isEmpty() ? fallback : value;
  }

  /** Read the GitHub token from the tool note, same fallback as GetGitHubFileTool. */
  static String tokenFromNote(String note) {
    if (note == null || note.trim().isEmpty()) return "";
    try {
      Object value = new JSONObject(note).opt("github_token");
      return value instanceof String ? ((String) value).trim() : "";
    } catch (org.json.JSONException ignored) {
      // Never expose credential-bearing notes.
      return "";
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
        JSONObject parsed = parseArguments(arguments);
        String owner = parsed.getString("owner");
        String repo = parsed.getString("repo");
        String base = parsed.getString("base");
        String head = parsed.getString("head");
        boolean includePatch = parsed.getBoolean("include_patch");
        int maxFiles = parsed.getInt("max_files");
        boolean saveToPhone = parsed.getBoolean("save_to_phone");
        String token = parsed.getString("token");

        FileLogger.d(TAG, "比较分支：" + owner + "/" + repo + " base=" + base + " head=" + head);

        // If no token was supplied, try the tool note.
        if (token.isEmpty()) {
          token = tokenFromNote(getNote(context));
        }
        if (token.isEmpty()) {
          throw new IllegalArgumentException("缺少 GitHub 访问令牌 (token)，且未在备注中配置");
        }

        OkHttpClient client = new OkHttpClient();
        JSONObject comparison = fetchComparison(client, token, owner, repo, base, head);

        // Extract the pieces worth reporting.
        String status = comparison.optString("status", "unknown");
        String aheadBy = comparison.optString("ahead_by", "0");
        String behindBy = comparison.optString("behind_by", "0");
        int totalCommits = comparison.optInt("total_commits", 0);

        JSONArray commits = comparison.optJSONArray("commits");
        JSONArray files = comparison.optJSONArray("files");

        // Build the compact commit list: short sha plus first line of the message.
        JSONArray commitSummaries = new JSONArray();
        if (commits != null) {
          for (int i = 0; i < commits.length(); i++) {
            JSONObject commit = commits.optJSONObject(i);
            if (commit == null) continue;
            JSONObject sha = commit.optJSONObject("sha");
            String shortSha = sha == null ? "" : sha.optString("sha", "");
            if (shortSha.length() > 7) shortSha = shortSha.substring(0, 7);
            JSONObject commitInfo = commit.optJSONObject("commit");
            String message = "";
            String author = "";
            if (commitInfo != null) {
              message = commitInfo.optString("message", "");
              JSONObject commitAuthor = commitInfo.optJSONObject("author");
              if (commitAuthor != null) author = commitAuthor.optString("name", "");
            }
            int newline = message.indexOf('\n');
            String firstLine = newline > 0 ? message.substring(0, newline) : message;
            JSONObject summary = new JSONObject();
            summary.put("sha", shortSha);
            summary.put("message", firstLine);
            if (!author.isEmpty()) summary.put("author", author);
            commitSummaries.put(summary);
          }
        }

        // Build the compact file list: path, status, additions, deletions.
        JSONArray fileSummaries = new JSONArray();
        int totalAdditions = 0;
        int totalDeletions = 0;
        int listedFiles = 0;
        if (files != null) {
          for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.optJSONObject(i);
            if (file == null) continue;
            int additions = file.optInt("additions", 0);
            int deletions = file.optInt("deletions", 0);
            totalAdditions += additions;
            totalDeletions += deletions;
            if (listedFiles >= maxFiles) continue;
            JSONObject summary = new JSONObject();
            summary.put("filename", file.optString("filename", ""));
            summary.put("status", file.optString("status", ""));
            summary.put("additions", additions);
            summary.put("deletions", deletions);
            if (includePatch) {
              String patch = file.optString("patch", "");
              if (!patch.isEmpty()) {
                summary.put("patch", truncatePatch(patch));
              }
            }
            fileSummaries.put(summary);
            listedFiles++;
          }
        }

        JSONObject response = new JSONObject();
        response.put("status", "success");
        response.put("owner", owner);
        response.put("repo", repo);
        response.put("base", base);
        response.put("head", head);
        response.put("comparison_status", status);
        response.put("ahead_by", aheadBy);
        response.put("behind_by", behindBy);
        response.put("total_commits", totalCommits);
        response.put("total_files", files == null ? 0 : files.length());
        response.put("listed_files", listedFiles);
        response.put("total_additions", totalAdditions);
        response.put("total_deletions", totalDeletions);
        response.put("commits", commitSummaries);
        response.put("files", fileSummaries);
        response.put("fetched_at", System.currentTimeMillis());

        String summaryText = buildSummaryText(owner, repo, base, head, status, aheadBy, behindBy,
            totalCommits, files == null ? 0 : files.length(), totalAdditions, totalDeletions);

        if (saveToPhone) {
          String savedFilePath = saveComparisonToPhone(comparison, owner, repo, base, head);
          response.put("saved_file", savedFilePath);
          response.put("message", summaryText + " 完整结果已保存到: " + savedFilePath);
        } else {
          response.put("message", summaryText);
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

  /** One-line summary suitable for a chat reply. */
  private static String buildSummaryText(String owner, String repo, String base, String head,
      String status, String aheadBy, String behindBy, int totalCommits, int totalFiles,
      int totalAdditions, int totalDeletions) {
    return owner + "/" + repo + "：" + head + " 相对 " + base
        + "（" + status + "，ahead " + aheadBy + " / behind " + behindBy + "）"
        + "，共 " + totalCommits + " 个提交，变更 " + totalFiles + " 个文件（+" + totalAdditions + " / -" + totalDeletions + "）";
  }

  /** Keep the patch short; a huge diff is what saves the phone, not the reply. */
  private static String truncatePatch(String patch) {
    String[] lines = patch.split("\n");
    if (lines.length <= MAX_PATCH_LINES) return patch;
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < MAX_PATCH_LINES; i++) {
      builder.append(lines[i]).append('\n');
    }
    builder.append("... (补丁已截断，共 ").append(lines.length).append(" 行)");
    return builder.toString();
  }

  /**
   * Fetch the comparison from GitHub.
   *
   * The compare endpoint returns a 404 when the two refs are not comparable
   * (for example when they do not share a common ancestor), so the error
   * message carries the upstream wording instead of a bare status code.
   */
  private JSONObject fetchComparison(OkHttpClient client, String token, String owner, String repo,
      String base, String head) throws Exception {
    String url = API_BASE + "/" + owner + "/" + repo + "/compare/"
        + encodeRef(base) + "..." + encodeRef(head);
    FileLogger.d(TAG, "请求 URL: " + url);

    Request request = new Request.Builder()
        .url(url)
        .header("Authorization", "Bearer " + token)
        .header("Accept", "application/vnd.github.v3+json")
        .header("User-Agent", "SisterFuture-CompareGitHubBranchesTool")
        .build();

    Response response = client.newCall(request).execute();
    String body = response.body() == null ? "" : response.body().string();
    if (!response.isSuccessful()) {
      String detail = body;
      try {
        JSONObject errorBody = new JSONObject(body);
        String message = errorBody.optString("message", "");
        if (!message.isEmpty()) detail = message;
      } catch (org.json.JSONException ignored) {
        // Keep the raw body when it is not JSON.
      }
      throw new IOException("GitHub API 返回错误：" + response.code() + " " + response.message()
          + (detail.isEmpty() ? "" : " — " + detail));
    }
    return new JSONObject(body);
  }

  /** A branch name may contain slashes; encode them so the path stays valid. */
  private static String encodeRef(String ref) {
    return ref.replace("/", "%2F");
  }

  /** Save the raw comparison to the phone so the reply can stay short. */
  private String saveComparisonToPhone(JSONObject comparison, String owner, String repo,
      String base, String head) {
    try {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
      String timestamp = sdf.format(new Date());
      String safeBase = base.replace("/", "_").replace("\\", "_");
      String safeHead = head.replace("/", "_").replace("\\", "_");
      String fileName = String.format("compare_%s_%s_%s_%s_%s.json", owner, repo, safeBase, safeHead, timestamp);

      File saveDir = new File(RESULT_SAVE_DIR);
      if (!saveDir.exists()) {
        saveDir.mkdirs();
      }

      File saveFile = new File(saveDir, fileName);
      FileWriter writer = new FileWriter(saveFile);
      writer.write(comparison.toString(2));
      writer.flush();
      writer.close();

      FileLogger.i(TAG, "对比结果已保存到: " + saveFile.getAbsolutePath());
      return saveFile.getAbsolutePath();
    } catch (Exception e) {
      FileLogger.e(TAG, "保存对比结果失败", e);
      return "保存失败: " + e.getMessage();
    }
  }

  @Override
  public String getNote(Context context) {
    return getSharedPreferences(context).getString("note_" + getName(), "");
  }
}
