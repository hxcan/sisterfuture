private void handleSelectedImage(Intent data)
  {
    final int selectionGeneration = mediaSelectionGeneration;
    isImageProcessing = true;
    sendButtonn2.setEnabled(false);
    new Thread(() -> {
      File cacheFile = null;
      try
      {
        Uri imageUri = data.getData();
        if (imageUri == null) throw new IOException("无法读取所选图片");

        InputStream inputStream = getContentResolver().openInputStream(imageUri);
        if (inputStream == null) throw new IOException("无法读取所选图片");

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1)
        {
          byteArrayOutputStream.write(buffer, 0, bytesRead);
        }
        inputStream.close();

        byte[] imageBytes = byteArrayOutputStream.toByteArray();

        // 🔥 新增（任务 #910050720382）：复制图片到缓存目录（保留旧逻辑，供 wanxiangImage 工具参考）
        try
        {
          String fileName = "temp_image_" + System.currentTimeMillis() + ".jpg";
          cacheFile = new File(getCacheDir(), fileName);
          FileOutputStream fos = new FileOutputStream(cacheFile);
          fos.write(imageBytes);
          fos.close();
        }
        catch (Exception cacheEx)
        {
          FileLogger.e(TAG, "⚠️ [CACHE_FILE_ERROR] 缓存图片失败", cacheEx);
          cacheFile = null;
        }

        // 🔥 新增（任务 #910050720382）：异步上传图片到 OSS（仿照 handleSelectedVideo 模式）
        OssManager ossManager = new OssManager(this);
        ossManager.validateConfiguration();

        String objectKey = "sisterfuture/image-messages/" + System.currentTimeMillis() + "_" + (cacheFile != null ? cacheFile.getName() : "image.jpg");
        JSONObject uploadResult = ossManager.uploadFile(cacheFile != null ? cacheFile : new File(getCacheDir(), "stub.jpg"), objectKey, false, OssManager.DEFAULT_URL_EXPIRY_SECONDS, null);

        if (selectionGeneration != mediaSelectionGeneration)
        {
          isImageProcessing = false;
          runOnUiThread(() -> sendButtonn2.setEnabled(true));
          return;
        }

        currentImageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        currentImagePath = cacheFile != null ? cacheFile.getAbsolutePath() : null;
        currentImageRemoteUrl = uploadResult.getString("signedUrl");
        currentImageOssObjectKey = uploadResult.getString("objectKey");
        currentImageUrlExpiresAt = uploadResult.getLong("expiresAt");
        isImageProcessing = false;

        runOnUiThread(() -> {
          sendButtonn2.setEnabled(true);
          Toast.makeText(this, "✅ 图片已上传，可以发送", Toast.LENGTH_SHORT).show();
        });
        FileLogger.i(TAG, "✅ [IMAGE_SELECTED] path=" + currentImagePath + " | ossObjectKey=" + currentImageOssObjectKey);
      }
      catch (Exception e)
      {
        isImageProcessing = false;
        FileLogger.e(TAG, "❌ [IMAGE_ERROR] 处理图片失败", e);
        runOnUiThread(() -> {
          sendButtonn2.setEnabled(true);
          Toast.makeText(this, "❌ 图片处理失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        });
      }
    }, "UserImageProcessor").start();
  }