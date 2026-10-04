if (hasImage)
        {
          JSONObject imageContent = new JSONObject();
          imageContent.put("type", "image_url");

          JSONObject imageUrl = new JSONObject();
          // 🔥 新增（任务 #910050720382）：优先用 OSS URL，没有则降级 base64
          if (currentImageRemoteUrl != null && !currentImageRemoteUrl.isEmpty())
          {
            imageUrl.put("url", currentImageRemoteUrl);
          }
          else
          {
            imageUrl.put("url", "data:image/jpeg;base64," + currentImageBase64);
          }
          imageContent.put("image_url", imageUrl);
          contentArray.put(imageContent);
        }

        if (hasVideo)
        {
          JSONObject videoContent = new JSONObject();
          videoContent.put("type", "video_url");
          videoContent.put("video_url", new JSONObject()
            .put("url", currentVideoRemoteUrl)
            .put("fps", 5));
          contentArray.put(videoContent);
        }

        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", contentArray);

        List<Attachment> uiAttachments = new ArrayList<>();
        if (hasImage && currentImageOssObjectKey != null && !currentImageOssObjectKey.isEmpty())
        {
          // 🔥 新增（任务 #910050720382）：图片走 OSS 时，加 local_attachments 用于 URL 续签
          JSONObject imageAttachmentJson = buildLocalImageAttachment(currentImagePath, currentImageOssObjectKey, currentImageUrlExpiresAt);
          userMessage.put("local_attachments", new JSONArray().put(imageAttachmentJson));
          uiAttachments = Attachment.fromJsonArray(userMessage.optJSONArray("local_attachments"));
        }
        if (hasVideo)
        {
          JSONObject videoAttachmentJson = buildLocalVideoAttachment(currentVideoPath, currentVideoMimeType,
            currentVideoOssObjectKey, currentVideoUrlExpiresAt);
          if (userMessage.has("local_attachments"))
          {
            userMessage.getJSONArray("local_attachments").put(videoAttachmentJson);
          }
          else
          {
            userMessage.put("local_attachments", new JSONArray().put(videoAttachmentJson));
          }
          uiAttachments = Attachment.fromJsonArray(userMessage.optJSONArray("local_attachments"));
        }

        contextManager.addRawMessage(userMessage);

        // 🔥 新增（任务 #910050720382）：渲染时优先用 OSS URL，isImageUrlRemote=true
        boolean hasImageRemote = currentImageRemoteUrl != null && !currentImageRemoteUrl.isEmpty();
        String displayImageUrl = hasImageRemote ? currentImageRemoteUrl : currentImageBase64;
        MessageItem displayedMessage = new MessageItem(message != null ? message : "", MessageType.USER, hasImage ? displayImageUrl : null);
        if (hasImageRemote)
        {
          displayedMessage.setImageUrlRemote(true);
        }
        displayedMessage.setAttachments(uiAttachments);
        messageAdapter.addMessage(displayedMessage);

        // 🔥 新增（任务 #910050720382）：把图片本地路径作为独立文本消息追加（供 wanxiangImage 等工具使用）
        if (currentImagePath != null)
        {
          contextManager.addUserMessage(currentImagePath);
        }
        if (currentVideoPath != null)
        {
          contextManager.addUserMessage("视频本地路径：" + currentVideoPath);
        }

        currentImageBase64 = null;
        currentImagePath = null;
        currentImageRemoteUrl = null;
        currentImageOssObjectKey = null;
        currentImageUrlExpiresAt = 0L;
        currentVideoPath = null;
        currentVideoMimeType = null;
        currentVideoRemoteUrl = null;
        currentVideoOssObjectKey = null;
        currentVideoUrlExpiresAt = 0L;