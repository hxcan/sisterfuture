package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.ContextManager;
import com.stupidbeauty.sisterfuture.manager.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.manager.MemoryManager;
import com.stupidbeauty.sisterfuture.SisterFutureApplication;
import com.stupidbeauty.sisterfuture.tools.ListPhoneDirectoryTool;
import com.stupidbeauty.sisterfuture.tools.ReadPhoneFileTool;
import android.content.Context;

/**
 * 工具注册中心 - 集中管理所有工具的注册逻辑
 * 
 * 重构自 SisterFutureActivity.initTools()
 * 关联任务：#4670
 */
public class ToolRegistry
{
  /**
   * 注册所有工具到 ToolManager
   * 
   * @param toolManager 目标工具管理器
   * @param contextManager 上下文管理器（部分工具需要）
   * @param modelAccessPointManager 接入点管理器（部分工具需要）
   * @param memoryManager 记忆管理器（部分工具需要）
   * @param context Android 上下文（部分工具需要）
   */
  public static void registerAll(
    ToolManager toolManager,
    ContextManager contextManager,
    ModelAccessPointManager modelAccessPointManager,
    MemoryManager memoryManager,
    Context context)
  {
    // === 基础工具 ===
    // � #4791 修改：传入 toolManager 参数
    toolManager.registerTool(new ConversationResetTool(contextManager, toolManager));
    toolManager.registerTool(new GetCurrentTimeTool());
    toolManager.registerTool(new GetLocationTool(context));
    toolManager.registerTool(new PlanRouteTool(context));

    // === 接入点管理工具 ===
    // 🔥 #4824 重命名：switch_access_point → switch_large_language_model
    toolManager.registerTool(new SwitchLargeLanguageModelTool(modelAccessPointManager));
    toolManager.registerTool(new GetCurrentAccessPointInfoTool(modelAccessPointManager));
    toolManager.registerTool(new DeveloperInfoTool());
    toolManager.registerTool(new SummaryAndShareTool(context, modelAccessPointManager, toolManager, contextManager));
    toolManager.registerTool(new DelayedReplyTool(context));

    // === 工具增强管理工具 ===
    toolManager.registerTool(new QueryToolEnhancementTool(toolManager, context));
    toolManager.registerTool(new SetToolEnhancementTool(toolManager, context));

    // === 工具备注管理工具 ===
    toolManager.registerTool(new GetToolRemarkTool(toolManager, context));
    toolManager.registerTool(new SetToolRemarkTool(toolManager, context));

    // === Redmine 相关工具 ===
    toolManager.registerTool(new GetRedmineTaskInfoTool(context));
    toolManager.registerTool(new CreateRedmineTaskTool(context));
    toolManager.registerTool(new UpdateRedmineIssueTool(context));
    toolManager.registerTool(new SearchRedmineTasksTool(context));
    toolManager.registerTool(new GetIssuesListTool(context));
    toolManager.registerTool(new ListRedmineProjectsTool(context));
    toolManager.registerTool(new EstablishTaskRelationshipTool(context));
    toolManager.registerTool(new RemoveTaskRelationshipTool(context));

    // === 网络请求工具 ===
    toolManager.registerTool(new BasicWebRequestTool(context));
    toolManager.registerTool(new GenericWebRequestTool(context));

    // === 系统工具 ===
    toolManager.registerTool(new GetContactListTool(context));

    // === FTP 相关工具 ===
    toolManager.registerTool(new FtpFileRequestTool(context));
    toolManager.registerTool(new ListFtpDirectoryTool(context));
    toolManager.registerTool(new FtpFileWriteTool(context));

    // === 记忆管理工具 ===
    toolManager.registerTool(new WriteMemoryTool(memoryManager, context));
    toolManager.registerTool(new SearchMemoryTool(memoryManager, context));
    toolManager.registerTool(new ListAllMemoriesTool(memoryManager, context));
    toolManager.registerTool(new RemoveMemoryTool(memoryManager, context));

    // === 接入点配置工具 ===
    toolManager.registerTool(new AddModelAccessPointTool(modelAccessPointManager, context));

    // === 记事本工具 ===
    toolManager.registerTool(new AddNoteTool(context));
    toolManager.registerTool(new RemoveNoteTool(context));
    toolManager.registerTool(new ListNotesTool(context));

    // === GitHub 相关工具 ===
    toolManager.registerTool(new GetGitHubFileTool(context));
    toolManager.registerTool(new CreateGitHubCommitTool(context));
    
    // 🔥 新增：GitHub Actions 日志获取工具（异步版本，需要 context）
    toolManager.registerTool(new GetGitHubActionsLogsTool(context));
    
    // 🔥 新增：GitHub Pull Request 创建工具
    toolManager.registerTool(new CreatePullRequestTool(context));

    // === 系统提示词管理工具 ===
    toolManager.registerTool(new FuseSystemPromptTool(context));
    toolManager.registerTool(new GetCurrentSystemPromptTool((SisterFutureApplication) SisterFutureApplication.getAppContext()));

    // === Git 分支管理工具 ===
    toolManager.registerTool(new CreateGitBranchTool(context));

    // === 购物清单工具 ===
    toolManager.registerTool(new ListShoppingItemsTool(context));
    toolManager.registerTool(new AddShoppingItemTool(context));

    // === 接入点维护工具 ===
    toolManager.registerTool(new RemoveAccessPointTool(modelAccessPointManager, context));
    toolManager.registerTool(new ListAccessPointsTool(modelAccessPointManager, context));

    // === 网页搜索工具 ===
    toolManager.registerTool(new SearchWithBraveTool(context));

    // === 购物清单维护工具 ===
    toolManager.registerTool(new RemoveShoppingItemTool(context));

    // === SSH 远程命令工具 ===
    toolManager.registerTool(new RemoteCommandTool(context));

    // === GitHub 文件搜索工具 ===
    toolManager.registerTool(new SearchFileInRepoTool(context));

    // === 网络信息工具 ===
    toolManager.registerTool(new NetworkInfoTool(context));

    // === 手机文件访问工具（新增） ===
    toolManager.registerTool(new ListPhoneDirectoryTool(context));
    toolManager.registerTool(new ReadPhoneFileTool(context));

    // 🔥 新增：按行文件编辑工具
    toolManager.registerTool(new EditFileByLineTool(context));

    // 🔥 新增：写剪贴板工具
    toolManager.registerTool(new WriteClipboardTool(context));
  }
}
