              if (toolManager.isToolAsync(toolName))
              {
                \/\/ #4895 更新通知状态：正在执行工具
                SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "正在执行：" + toolName);
                
                final String finalToolCallId = toolCallId;  \/\/ 🔥 保留 toolCallId 用于回调
                toolManager.executeToolAsync(finalToolCallId, toolName, args, new Tool.OnResultCallback()
                {
                  @Override
                  public void onResult(JSONObject result)
                  {
                    synchronized (pendingResults)
                    {
                      try
                      {
                        JSONObject wrapper = new JSONObject();
                        wrapper.put("id", finalToolCallId);
                        wrapper.put("name", toolName);
                        wrapper.put("result", result);
                        pendingResults.put(finalToolCallId, wrapper);
                      }
                      catch (Exception e)
                      {
                        Log.e(TAG, "封装异步结果失败", e);
                      }

                      if (pendingResults.size() == toolCallsArray.length())
                      {
                        postProcessToolResults(pendingResults, assistantMessage, toolCallsArray);
                      }
                    }
                  }

                  @Override
                  public void onError(Exception e)
                  {
                    Log.e(TAG, "异步工具失败：" + toolName + ", toolCallId=" + finalToolCallId, e);
                    \/\/ 🔥 即使出错也尝试记录，让幂等检查处理重复
                    synchronized (pendingResults)
                    {
                      if (!pendingResults.containsKey(finalToolCallId))
                      {
                        try
                        {
                          JSONObject errorResult = new JSONObject();
                          errorResult.put("error", e.getMessage());
                          errorResult.put("tool_name", toolName);
                          JSONObject wrapper = new JSONObject();
                          wrapper.put("id", finalToolCallId);
                          wrapper.put("name", toolName);
                          wrapper.put("result", errorResult);
                          pendingResults.put(finalToolCallId, wrapper);
                        }
                        catch (Exception ex)
                        {
                          Log.e(TAG, "封装错误结果失败", ex);
                        }

                        if (pendingResults.size() == toolCallsArray.length())
                        {
                          postProcessToolResults(pendingResults, assistantMessage, toolCallsArray);
                        }
                      }
                    }
                  }
                });
              }