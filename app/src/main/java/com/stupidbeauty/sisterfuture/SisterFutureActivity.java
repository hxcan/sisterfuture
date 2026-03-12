package com.stupidbeauty.sisterfuture;

import com.stupidbeauty.sisterfuture.tool.ToolRegistry;
import com.stupidbeauty.sisterfuture.tool.ToolManager;
import com.stupidbeauty.sisterfuture.manager.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.manager.MemoryManager;
import com.stupidbeauty.sisterfuture.ContextManager;

// ... 其他已有导入保持不变 ...

public class SisterFutureActivity extends Activity implements TextToSpeech.OnInitListener
{
  // ... 变量声明保持不变 ...

  /**
   * 初始化工具管理器
   * // 重构后：委托给 ToolRegistry 处理
   */
  private void initTools()
  {
    toolManager = new ToolManager();
    
    // 委托给 ToolRegistry 集中管理工具注册
    ToolRegistry.registerAll(
      toolManager,
      contextManager,
      modelAccessPointManager,
      memoryManager,
      this
    );
  }

  // ... 其他方法保持不变 ...
}