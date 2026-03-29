  // === 内置 FTP 服务器方法实现 ===
  
  /**
   * 启动内置 FTP 服务器（用于数据备份）
   */
  private void startBuiltinFtpServer() {
    // 🔍 FTP 调试：输出根目录信息
    File rootDir = getFilesDir();
    File parentDir = rootDir.getParentFile();
    
    Log.d(TAG, "📁 [FTP_DEBUG] 应用 files 目录：" + rootDir.getAbsolutePath());
    Log.d(TAG, "📂 [FTP_DEBUG] 应用私有目录（FTP 根目录）：" + parentDir.getAbsolutePath());
    Log.d(TAG, "📂 [FTP_DEBUG] 根目录是否存在：" + (parentDir != null ? parentDir.exists() : "null"));
    
    if (parentDir != null && parentDir.exists()) {
      File[] files = parentDir.listFiles();
      Log.d(TAG, "📋 [FTP_DEBUG] 根目录下文件/目录数量：" + (files != null ? files.length : "null"));
      
      if (files != null) {
        for (File file : files) {
          Log.d(TAG, "  - 📄 [FTP_DEBUG] " + (file.isDirectory() ? "DIR" : "FILE") + ": " + file.getName());
        }
      }
      
      // 检查关键子目录
      Log.d(TAG, "📂 [FTP_DEBUG] databases/ 存在：" + new File(parentDir, "databases").exists());
      Log.d(TAG, "📂 [FTP_DEBUG] shared_prefs/ 存在：" + new File(parentDir, "shared_prefs").exists());
      Log.d(TAG, "📂 [FTP_DEBUG] files/ 存在：" + new File(parentDir, "files").exists());
      Log.d(TAG, "📂 [FTP_DEBUG] code_cache/ 存在：" + new File(parentDir, "code_cache").exists());
    }
    
    builtinFtpServer = new BuiltinFtpServer(this);
    builtinFtpServerErrorListener = new BuiltinFtpServerErrorListener();
    
    builtinFtpServer.setPort(FTP_SERVER_PORT);
    builtinFtpServer.setAllowActiveMode(false);
    builtinFtpServer.setErrorListener(builtinFtpServerErrorListener);
    builtinFtpServer.start();
    
    Log.d(TAG, "🚀 [FTP_DEBUG] 内置 FTP 服务器已启动，端口：" + FTP_SERVER_PORT);
  }

  /**
   * 计划启动内置 FTP 服务器（延时 2 秒）
   */
  private void scheduleStartBuiltinFtpServer() {
    Timer timerObj = new Timer();
    TimerTask timerTaskObj = new TimerTask() {
      public void run() {
        startBuiltinFtpServer();
      }
    };
    timerObj.schedule(timerTaskObj, 2000); // 延时 2 秒启动
  }