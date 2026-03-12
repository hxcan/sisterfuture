package com.stupidbeauty.sisterfuture;

// ... [前面所有 import 语句保持不变] ...

  /**
   * 判断是否为"上下文长度超出限制"的错误。
  **/
  private boolean isContextLengthError(String errorMessage)
  {
    if (errorMessage == null) return false;
    // 匹配多种上下文超长错误格式
    return errorMessage.contains("Range of input length should be") ||
           errorMessage.contains("context length") ||
           errorMessage.contains("exceeds the available context size") ||
           errorMessage.contains("exceeds maximum context length") ||
           errorMessage.contains("context window exceeds limit") ||  // ✅ MiniMax 错误码 2013
           errorMessage.contains("(2013)");  // ✅ MiniMax 特定错误码
  }

// ... [文件其余部分保持不变] ...