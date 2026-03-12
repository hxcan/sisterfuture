  /**
   * 判断是否为"上下文长度超出限制"的错误。
  **/
  private boolean isContextLengthError(String errorMessage)
  {
    if (errorMessage == null) return false;
    // 根据你日志里的实际错误信息匹配
    return errorMessage.contains("Range of input length should be") ||
           errorMessage.contains("context length") ||
           errorMessage.contains("exceeds the available context size") ||
           errorMessage.contains("exceeds maximum context length") ||
           errorMessage.contains("context window exceeds limit") ||  // ✅ MiniMax 错误
           errorMessage.contains("(2013)");  // ✅ MiniMax 特定错误码
  }