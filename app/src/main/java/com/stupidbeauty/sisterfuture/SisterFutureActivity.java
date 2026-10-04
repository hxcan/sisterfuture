else if ("user".equals(role))
      {
        if (contentObj instanceof JSONArray)
        {
          JSONArray contentArray = (JSONArray) contentObj;
          StringBuilder textBuilder = new StringBuilder();
          String imageUrl = null;
          boolean imageUrlIsRemote = false; // 🔥 新增（任务 #910050720382）：标记 imageUrl 是否为远程 URL

          for (int i = 0; i < contentArray.length(); i++)
          {
            try
            {
              JSONObject item = contentArray.optJSONObject(i);
              if (item == null) continue;

              String type = item.optString("type");
              if ("text".equals(type))
              {
                textBuilder.append(item.optString("text"));
              }
              else if ("image_url".equals(type))
              {
                JSONObject imageUrlObj = item.optJSONObject("image_url");
                if (imageUrlObj != null)
                {
                  String url = imageUrlObj.optString("url");
                  if (url != null)
                  {
                    // 🔥 双格式识别（任务 #910050720382）
                    if (url.startsWith("https://") || url.startsWith("http://"))
                    {
                      imageUrl = url;
                      imageUrlIsRemote = true;
                    }
                    else if (url.startsWith("data:image/jpeg;base64,"))
                    {
                      int commaIndex = url.lastIndexOf(',');
                      if (commaIndex > 0) {
                        imageUrl = url.substring(commaIndex + 1);
                      } else {
                        imageUrl = url;
                      }
                    }
                  }
                }
              }
            }
            catch (Exception e)
            {
              Log.e(TAG, "解析多模态消息失败", e);
            }
          }

          MessageItem item = new MessageItem(textBuilder.toString(), MessageType.USER, imageUrl);
          // 🔥 新增（任务 #910050720382）：标记 imageUrl 是否为远程 URL（与 MessageAdapter 匹配）
          if (imageUrlIsRemote)
          {
            item.setImageUrlRemote(true);
          }
          item.setAttachments(Attachment.fromJsonArray(msg.optJSONArray("local_attachments")));
          if (messageId != null && !messageId.isEmpty()) {
            item.setMessageId(messageId);
          }
          messageAdapter.addMessage(item);
        }