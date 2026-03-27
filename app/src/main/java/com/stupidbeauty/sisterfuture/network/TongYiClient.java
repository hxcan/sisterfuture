    FileLogger.d(TAG, "🔒 [QUEUE_SUBMIT] 请求 #" + totalRequests + " 提交到队列 | 当前队列长度：" + queueSizeBefore + " | 线程：" + Thread.currentThread().getName());
    
    // === 🔒 #5028 将请求提交到队列 ===
    boolean queued = requestQueue.offer(() -> {
      final long startTime = System.currentTimeMillis();
      final long waitTime = startTime - submitTime;
      final int queueSizeNow = requestQueue.size();
      
      totalWaitTimeMs.addAndGet(waitTime);
      
      // 更新高水位标记
      int currentQueueSize = queueSizeBefore;
      int oldHighWaterMark = queueSizeHighWaterMark.get();
      while (currentQueueSize > oldHighWaterMark) {
        if (queueSizeHighWaterMark.compareAndSet(oldHighWaterMark, currentQueueSize)) {
          break;
        }
        oldHighWaterMark = queueSizeHighWaterMark.get();
        currentQueueSize = queueSizeBefore;
      }
      
      FileLogger.d(TAG, "🔒 [QUEUE_EXEC] 请求 #" + totalRequests + " 开始执行 | 等待时间：" + waitTime + "ms | 当前队列长度：" + queueSizeNow + " | 线程：" + Thread.currentThread().getName());
      
      try {
        // 执行实际的网络请求
        networkRequester.sendRequest(messages, includeTools, listener, onStreamComplete);
        
        final long endTime = System.currentTimeMillis();
        final long executionTime = endTime - startTime;
        
        FileLogger.d(TAG, "🔒 [QUEUE_DONE] 请求 #" + totalRequests + " 完成 | 执行时间：" + executionTime + "ms | 总耗时：" + (waitTime + executionTime) + "ms");
        
        // 每 10 个请求输出一次统计
        if (totalRequests % 10 == 0) {
          long avgWaitTime = totalWaitTimeMs.get() / totalRequests;
          int highWaterMark = queueSizeHighWaterMark.get();
          FileLogger.i(TAG, "🔒 [QUEUE_STATS] 队列统计 | 总请求数：" + totalRequests + " | 平均等待时间：" + avgWaitTime + "ms | 队列最大长度：" + highWaterMark);
        }
      } catch (Exception e) {
        FileLogger.e(TAG, "🔒 [QUEUE_ERROR] 请求 #" + totalRequests + " 执行失败", e);
        throw e; // 重新抛出异常，让调用者处理
      }
    });
    
    if (!queued) {
      FileLogger.e(TAG, "🔒 [QUEUE_REJECTED] 请求 #" + totalRequests + " 被队列拒绝（队列已满）");
      listener.onError(new IllegalStateException("请求队列已满，无法接受新请求"));
    }
    
    FileLogger.d(TAG, "🔒 [QUEUE_ENQUEUED] 请求 #" + totalRequests + " 已加入队列 | 提交后队列长度：" + requestQueue.size());