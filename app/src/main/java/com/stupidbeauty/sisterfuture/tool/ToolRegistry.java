package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.ContextManager;
import com.stupidbeauty.sisterfuture.manager.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.manager.MemoryManager;
import com.stupidbeauty.sisterfuture.SisterFutureApplication;
import com.stupidbeauty.sisterfuture.tool.ListPhoneDirectoryTool;
import com.stupidbeauty.sisterfuture.tool.ReadPhoneFileTool;
import android.content.Context;

public class ToolRegistry {
    public static void registerAll(
        ToolManager toolManager,
        ContextManager contextManager,
        ModelAccessPointManager modelAccessPointManager,
        MemoryManager memoryManager,
        Context context) {

        toolManager.registerTool(new ResetConversationContextTool(contextManager, toolManager));
        toolManager.registerTool(new GetCurrentTimeTool());
        toolManager.registerTool(new GetLocationTool(context));
        toolManager.registerTool(new PlanRouteTool(context));
        toolManager.registerTool(new SearchNearbyTool(context));

        toolManager.registerTool(new SwitchLargeLanguageModelTool(modelAccessPointManager));
        toolManager.registerTool(new GetCurrentAccessPointInfoTool(modelAccessPointManager));
        toolManager.registerTool(new DeveloperInfoTool());
        toolManager.registerTool(new SummaryAndShareTool(context, modelAccessPointManager, toolManager, contextManager));
        toolManager.registerTool(new DelayedReplyTool(context));

        toolManager.registerTool(new QueryToolEnhancementTool(toolManager, context));
        toolManager.registerTool(new SetToolEnhancementTool(toolManager, context));

        toolManager.registerTool(new GetToolRemarkTool(toolManager, context));
        toolManager.registerTool(new SetToolRemarkTool(toolManager, context));

        toolManager.registerTool(new GetRedmineTaskInfoTool(context));
        toolManager.registerTool(new CreateRedmineTaskTool(context));
        toolManager.registerTool(new UpdateRedmineIssueTool(context));
        toolManager.registerTool(new SearchRedmineTasksTool(context));
        toolManager.registerTool(new GetIssuesListTool(context));
        toolManager.registerTool(new ListRedmineProjectsTool(context));
        toolManager.registerTool(new EstablishTaskRelationshipTool(context));
        toolManager.registerTool(new RemoveTaskRelationshipTool(context));

        toolManager.registerTool(new BasicWebRequestTool(context));
        toolManager.registerTool(new GenericWebRequestTool(context));
        toolManager.registerTool(new GenerateImageTool(context));
        toolManager.registerTool(new WanxiangTool(context));
        toolManager.registerTool(new KlingVideoGenerationTool(context));

        toolManager.registerTool(new OssUploadTool(context));
        toolManager.registerTool(new OssGetSignedUrlTool(context));

        toolManager.registerTool(new GetContactListTool(context));
        toolManager.registerTool(new AddContactTool(context));

        toolManager.registerTool(new FtpFileRequestTool(context));
        toolManager.registerTool(new ListFtpDirectoryTool(context));
        toolManager.registerTool(new FtpFileWriteTool(context));

        toolManager.registerTool(new WriteMemoryTool(memoryManager, context));
        toolManager.registerTool(new SearchMemoryTool(memoryManager, context));
        toolManager.registerTool(new ListAllMemoriesTool(memoryManager, context));
        toolManager.registerTool(new RemoveMemoryTool(memoryManager, context));

        toolManager.registerTool(new AddModelAccessPointTool(modelAccessPointManager, context));

        toolManager.registerTool(new AddNoteTool(context));
        toolManager.registerTool(new RemoveNoteTool(context));
        toolManager.registerTool(new ListNotesTool(context));

        toolManager.registerTool(new GetGitHubFileTool(context));
        toolManager.registerTool(new CreateGitHubCommitTool(context));
        toolManager.registerTool(new GetGitHubActionsLogsTool(context));
        toolManager.registerTool(new CreatePullRequestTool(context));

        toolManager.registerTool(new FuseSystemPromptTool(context));
        toolManager.registerTool(new GetCurrentSystemPromptTool((SisterFutureApplication) SisterFutureApplication.getAppContext()));

        toolManager.registerTool(new CreateGitBranchTool(context));

        toolManager.registerTool(new ListShoppingItemsTool(context));
        toolManager.registerTool(new AddShoppingItemTool(context));

        toolManager.registerTool(new RemoveAccessPointTool(modelAccessPointManager, context));
        toolManager.registerTool(new ListAccessPointsTool(modelAccessPointManager, context));

        toolManager.registerTool(new SearchWithBraveTool(context));
        toolManager.registerTool(new SearchWithBaiduTool(context));

        toolManager.registerTool(new RemoveShoppingItemTool(context));

        toolManager.registerTool(new ExecuteRemoteCommandTool(context));

        toolManager.registerTool(new SearchFileInRepoTool(context));

        toolManager.registerTool(new NetworkInfoTool(context));

        toolManager.registerTool(new ListPhoneDirectoryTool(context));
        toolManager.registerTool(new ReadPhoneFileTool(context));
        toolManager.registerTool(new EditFileByLineTool(context));
        toolManager.registerTool(new WriteClipboardTool(context));
        toolManager.registerTool(new LaunchAppTool(context));
        toolManager.registerTool(new GetInstalledAppsTool(context));
        toolManager.registerTool(new CreateCalendarEventTool(context));
        toolManager.registerTool(new ListNotificationsTool(context));
        toolManager.registerTool(new GetSmsListTool(context));
        toolManager.registerTool(new MakeCallTool(context));
        toolManager.registerTool(new SendSmsTool(context));

        // 飞书工具组
        toolManager.registerTool(new AddFeishuBitableRecordTool(context));
        toolManager.registerTool(new GetFeishuUserIdByMobileTool(context));
    }
}