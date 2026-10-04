private JSONObject buildLocalVideoAttachment(String path, String mimeType, String ossObjectKey,
                                               long ossUrlExpiresAt) throws JSONException
  {
    File videoFile = new File(path);
    JSONObject metadata = new JSONObject();
    metadata.put("size", videoFile.length());
    metadata.put("mimeType", mimeType != null ? mimeType : "video/mp4");

    JSONObject attachment = new JSONObject();
    attachment.put("type", "video");
    attachment.put("url", Uri.fromFile(videoFile).toString());
    attachment.put("ossObjectKey", ossObjectKey);
    attachment.put("ossUrlExpiresAt", ossUrlExpiresAt);
    attachment.put("metadata", metadata);
    return attachment;
  }

  // 🔥 新增（任务 #910050720382）：仿照 buildLocalVideoAttachment，为图片创建 local_attachments
  private JSONObject buildLocalImageAttachment(String path, String ossObjectKey,
                                               long ossUrlExpiresAt) throws JSONException
  {
    File imageFile = path != null ? new File(path) : null;
    JSONObject metadata = new JSONObject();
    if (imageFile != null && imageFile.exists())
    {
      metadata.put("size", imageFile.length());
    }
    metadata.put("mimeType", "image/jpeg");

    JSONObject attachment = new JSONObject();
    attachment.put("type", "image");
    if (imageFile != null)
    {
      attachment.put("url", Uri.fromFile(imageFile).toString());
    }
    attachment.put("ossObjectKey", ossObjectKey);
    attachment.put("ossUrlExpiresAt", ossUrlExpiresAt);
    attachment.put("metadata", metadata);
    return attachment;
  }