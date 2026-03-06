// ✅ 新增：注册 RemoteCommandTool
    toolManager.registerTool(new RemoteCommandTool(this));

    // ✅ 新增：注册 SearchFileInRepoTool
    toolManager.registerTool(new SearchFileInRepoTool(this));

    // 初始化通义千问客户端
    tongYiClient = new TongYiClient(modelAccessPointManager, toolManager);