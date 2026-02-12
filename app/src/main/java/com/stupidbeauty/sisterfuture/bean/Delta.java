package com.stupidbeauty.sisterfuture.bean;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Delta
{
  private String content;
  private String role;
  @SerializedName("tool_calls")
  private List<ToolCall> toolCalls;

  public String getContent()
  {
    return content;
  }

  public void setContent(String content)
  {
    this.content = content;
  }

  public String getRole()
  {
    return role;
  }

  public void setRole(String role)
  {
    this.role = role;
  }

  public List<ToolCall> getToolCalls()
  {
    return toolCalls;
  }

  public void setToolCalls(List<ToolCall> toolCalls)
  {
    this.toolCalls = toolCalls;
  }
}
