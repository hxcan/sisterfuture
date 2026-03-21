              if (toolManager.isToolAsync(toolName))
              {
                // #4895 更新通知状态：正在执行工具
                SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "正在执行：" + toolName);
                
                // 🔥 #4790 修改：传入 toolCallId 参数实现幂等性检查
                toolManager.executeToolAsync(toolCallId, toolName, args, new Tool.OnResultCallback()
                {
                  @Override
                  public void onResult(JSONObject result)
                  {
                    synchronized (pendingResults)
                    {
                      try
                      {
                        JSONObject wrapper = new JSONObject();
                        wrapper.put("id", toolCallId);
                        wrapper.put("name", toolName);
                        wrapper.put("result", result);
                        pendingResults.put(toolCallId, wrapper);
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
                    Log.e(TAG, "异步工具失败：" + toolName + ", toolCallId=" + toolCallId, e);
                    postProcessToolResults(pendingResults, assistantMessage, toolCallsArray);
                  }
                });
              }