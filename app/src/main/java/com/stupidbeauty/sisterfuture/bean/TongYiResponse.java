package com.stupidbeauty.sisterfuture.bean;

import java.util.List;

public class TongYiResponse
{
  private List<Choice> choices;
  private Error error; // 新增：用于捕获错误响应

  public List<Choice> getChoices()
  {
    return choices;
  }

  public void setChoices(List<Choice> choices)
  {
    this.choices = choices;
  }

  public Error getError()
  {
    return error;
  }

  public void setError(Error error)
  {
    this.error = error;
  }

  // 内部类：错误信息结构
  public static class Error
  {
    private String code;
    private String message;
    private String type;
    private Object param; // 可为 null，用 Object 或 String 都行

    public String getCode()
    {
      return code;
    }

    public void setCode(String code)
    {
      this.code = code;
    }

    public String getMessage()
    {
      return message != null ? message : "未知错误";
    }

    public void setMessage(String message)
    {
      this.message = message;
    }

    public String getType()
    {
      return type;
    }

    public void setType(String type)
    {
      this.type = type;
    }

    public Object getParam()
    {
      return param;
    }

    public void setParam(Object param)
    {
      this.param = param;
    }
  }
}
