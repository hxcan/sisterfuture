package com.stupidbeauty.sisterfuture.bean;

import com.google.gson.annotations.SerializedName;

public class Choice
{
  private Delta delta;
  @SerializedName("finish_reason")
  private String finishReason;

  public Delta getDelta()
  {
    return delta;
  }

  public void setDelta(Delta delta)
  {
    this.delta = delta;
  }

  public String getFinishReason()
  {
    return finishReason;
  }

  public void setFinishReason(String finishReason)
  {
    this.finishReason = finishReason;
  }
}
