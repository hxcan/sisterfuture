package com.stupidbeauty.sisterfuture.bean;

import com.google.gson.annotations.SerializedName;

public class ToolCall
{
  private String id;
  private String type;
  private Function function;
  private Integer index;
  
  public Integer getIndex()
  {
    return index;
  }
  
  public void setIndex(Integer index)
  {
    this.index = index;
  }

  public String getId()
  {
    return id;
  }

  public void setId(String id)
  {
    this.id = id;
  }

  public String getType()
  {
    return type;
  }

  public void setType(String type)
  {
    this.type = type;
  }

  public Function getFunction()
  {
    return function;
  }

  public void setFunction(Function function)
  {
    this.function = function;
  }
}
