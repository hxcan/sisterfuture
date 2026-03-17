              // ✅ #4823 新增：HTTP 400 → 检查是否上下文超长
              else if (statusCode == 400) {
                String errorBody = responseException.getCustomMessage();
                if (ContextLengthUtils.isContextLengthError(errorBody)) {
                  // ✅ #4829 使用统一处理方法（缩短后重试）
                  handleContextLengthError(errorBody, true);
                  return; // 直接返回，不继续处理
                }
              }