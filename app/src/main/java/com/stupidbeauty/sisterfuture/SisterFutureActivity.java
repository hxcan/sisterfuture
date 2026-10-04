@OnClick(R.id.uploadImageButton)
  public void onUploadImageButton()
  {
    if (isVideoProcessing)
    {
      Toast.makeText(this, "视频仍在处理中，请稍候", Toast.LENGTH_SHORT).show();
      return;
    }
    deletePendingVideo();
    mediaSelectionGeneration++;
    if (currentImageBase64 != null)
    {
      currentImageBase64 = null;
    }
    currentImagePath = null;
    currentImageRemoteUrl = null; // 🔥 新增（任务 #910050720382）：清图片 OSS URL
    currentImageOssObjectKey = null; // 🔥 新增（任务 #910050720382）：清图片 OSS 对象 key
    currentImageUrlExpiresAt = 0L; // 🔥 新增（任务 #910050720382）：清 URL 过期时间
    isImageProcessing = false; // 🔥 新增（任务 #910050720382）：清上传中标志
    currentVideoPath = null;
    currentVideoMimeType = null;
    currentVideoRemoteUrl = null;
    currentVideoOssObjectKey = null;
    currentVideoUrlExpiresAt = 0L;
    openMediaPicker();
  }