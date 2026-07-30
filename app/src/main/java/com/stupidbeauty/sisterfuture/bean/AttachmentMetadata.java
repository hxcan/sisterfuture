package com.stupidbeauty.sisterfuture.bean;

/**
 * 附件元数据
 * 记录多媒体文件的详细信息（尺寸、大小、时长、MIME 类型等）
 */
public class AttachmentMetadata
{
  private Integer width;
  private Integer height;
  private Long duration;
  private Long size;
  private String mimeType;

  public Integer getWidth()
  {
    return width;
  }

  public void setWidth(Integer width)
  {
    this.width = width;
  }

  public Integer getHeight()
  {
    return height;
  }

  public void setHeight(Integer height)
  {
    this.height = height;
  }

  public Long getDuration()
  {
    return duration;
  }

  public void setDuration(Long duration)
  {
    this.duration = duration;
  }

  public Long getSize()
  {
    return size;
  }

  public void setSize(Long size)
  {
    this.size = size;
  }

  public String getMimeType()
  {
    return mimeType;
  }

  public void setMimeType(String mimeType)
  {
    this.mimeType = mimeType;
  }
}