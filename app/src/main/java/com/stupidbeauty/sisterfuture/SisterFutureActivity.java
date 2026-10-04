accumulatedAnswer.setLength(0);
    partialToolArgs.clear();
    indexToOriginalIdMap.clear();
    currentImageBase64 = null;
    currentImagePath = null;
    currentImageRemoteUrl = null; // 🔥 新增（任务 #910050720382）：清图片 OSS URL（切换会话时）
    currentImageOssObjectKey = null; // 🔥 新增（任务 #910050720382）：清图片 OSS 对象 key
    currentImageUrlExpiresAt = 0L; // 🔥 新增（任务 #910050720382）：清 URL 过期时间
    isImageProcessing = false; // 🔥 新增（任务 #910050720382）：清上传中标志
    deletePendingVideo();
    currentVideoPath = null;
    currentVideoMimeType = null;
    currentVideoRemoteUrl = null;
    currentVideoOssObjectKey = null;
    currentVideoUrlExpiresAt = 0L;
    isVideoProcessing = false;
    sendButtonn2.setEnabled(true);