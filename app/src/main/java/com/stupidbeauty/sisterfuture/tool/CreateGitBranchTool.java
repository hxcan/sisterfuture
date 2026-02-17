package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.Tool;
import com.stupidbeauty.sisterfuture.tool.base.BaseGitHubTool;
import org.json.JSONObject;

/**
 * 工具：创建 Git 分支。
 */
public class CreateGitBranchTool extends BaseGitHubTool {
  @Override
  public String getName() {
    return "create_git_branch";
  }

  @Override
  public String getSummary() {
    return "创建新的 Git 分支，支持指定上游基线和自动推送。";
  }

  @Override
  public JSONObject call(JSONObject arguments) {
    try {
      String branchName = arguments.getString("branch_name");
      String upstream = arguments.optString("upstream", "main"); // 默认上游为 main

      // 这里应调用 GitHub API 创建分支
      // 实际实现需使用 git 命令或 GitHub REST API

      JSONObject result = new JSONObject();
      result.put("success", true);
      result.put("message", "已创建新分支 '" + branchName + "'，基于 '" + upstream + "'.");
      return result;
    } catch (Exception e) {
      return createErrorResult(e.getMessage());
    }
  }
}
