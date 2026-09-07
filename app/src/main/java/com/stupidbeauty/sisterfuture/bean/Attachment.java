package com.stupidbeauty.sisterfuture.bean;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 工具返回的多媒体附件
 * 支持图片、音频、视频、文件等多种类型
 */
public class Attachment
{
  private String type;
  private String url;
  private AttachmentMetadata metadata;

  public String getType()
  {
    return type;
  }

  public void setType(String type)
  {
    this.type = type;
  }

  public String getUrl()
  {
    return url;
  }

  public void setUrl(String url)
  {
    this.url = url;
  }

  public AttachmentMetadata getMetadata()
  {
    return metadata;
  }

  public void setMetadata(AttachmentMetadata metadata)
  {
    this.metadata = metadata;
  }

  public static List<Attachment> fromJsonArray(JSONArray array)
  {
    List<Attachment> result = new ArrayList<>();
    if (array == null) return result;

    for (int i = 0; i < array.length(); i++)
    {
      JSONObject item = array.optJSONObject(i);
      if (item == null) continue;

      Attachment attachment = new Attachment();
      attachment.setType(item.optString("type", ""));
      attachment.setUrl(item.optString("url", ""));

      JSONObject metadataJson = item.optJSONObject("metadata");
      if (metadataJson != null)
      {
        AttachmentMetadata parsedMetadata = new AttachmentMetadata();
        if (metadataJson.has("width")) parsedMetadata.setWidth(metadataJson.optInt("width"));
        if (metadataJson.has("height")) parsedMetadata.setHeight(metadataJson.optInt("height"));
        if (metadataJson.has("duration")) parsedMetadata.setDuration(metadataJson.optLong("duration"));
        if (metadataJson.has("size")) parsedMetadata.setSize(metadataJson.optLong("size"));
        if (metadataJson.has("mimeType")) parsedMetadata.setMimeType(metadataJson.optString("mimeType"));
        attachment.setMetadata(parsedMetadata);
      }
      result.add(attachment);
    }
    return result;
  }
}
