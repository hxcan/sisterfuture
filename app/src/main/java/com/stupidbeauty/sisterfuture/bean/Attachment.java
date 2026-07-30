package com.stupidbeauty.sisterfuture.bean;

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
}