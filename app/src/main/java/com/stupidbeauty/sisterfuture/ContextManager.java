  // ✅ 从 JSON 文件加载历史到内存（同步读取），支持向下兼容
  private void loadHistoryFromFile()
  {
    boolean shouldFallbackToSP = false;
    
    if (!contextFile.exists())
    {
      FileLogger.d(TAG, "📥 [LOAD] 从 JSON 文件加载历史：文件不存在");
      shouldFallbackToSP = true;
    }
    else
    {
      try
      {
        BufferedReader reader = new BufferedReader(new FileReader(contextFile));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null)
        {
          sb.append(line);
        }
        reader.close();
        
        String fileContent = sb.toString();
        if (fileContent.isEmpty())
        {
          FileLogger.d(TAG, "📥 [LOAD] 从 JSON 文件加载历史：空文件");
          shouldFallbackToSP = true;
        }
        else
        {
          JSONObject rootObj = new JSONObject(fileContent);
          
          // 读取 history
          if (rootObj.has(KEY_HISTORY))
          {
            JSONArray array = rootObj.getJSONArray(KEY_HISTORY);
            memoryHistory = new ArrayList<>();
            
            for (int i = 0; i < array.length(); i++)
            {
              memoryHistory.add(array.getJSONObject(i));
            }
            
            FileLogger.d(TAG, "📥 [LOAD] 从 JSON 文件加载历史：" + memoryHistory.size() + " 条");
            return; // ✅ 成功加载，直接返回
          }
          else
          {
            FileLogger.d(TAG, "📥 [LOAD] 从 JSON 文件加载历史：无 history 字段");
            shouldFallbackToSP = true;
          }
        }
      }
      catch (Exception e)
      {
        FileLogger.e(TAG, "❌ [LOAD] 加载 JSON 文件失败：" + e.getMessage() + "，尝试回退到 SP", e);
        shouldFallbackToSP = true;
      }
    }
    
    // 🔙 向下兼容：从 SharedPreferences 读取旧数据
    if (shouldFallbackToSP)
    {
      FileLogger.d(TAG, "🔙 [FALLBACK] JSON 文件不可用，尝试从 SharedPreferences 读取旧历史");
      try
      {
        String spHistoryJson = sharedPreferences.getString(KEY_HISTORY, null);
        if (spHistoryJson != null && !spHistoryJson.isEmpty())
        {
          JSONArray array = new JSONArray(spHistoryJson);
          memoryHistory = new ArrayList<>();
          
          for (int i = 0; i < array.length(); i++)
          {
            memoryHistory.add(array.getJSONObject(i));
          }
          
          FileLogger.i(TAG, "🔙 [FALLBACK] 从 SP 成功加载历史：" + memoryHistory.size() + " 条（下次将自动使用 JSON 格式）");
          return;
        }
        else
        {
          FileLogger.d(TAG, "🔙 [FALLBACK] SP 中也无历史数据");
        }
      }
      catch (Exception e)
      {
        FileLogger.e(TAG, "❌ [FALLBACK] 从 SP 加载历史失败：" + e.getMessage(), e);
      }
      
      // 最终方案：空历史
      memoryHistory = new ArrayList<>();
      FileLogger.d(TAG, "📥 [LOAD] 最终结果：空历史");
    }
  }