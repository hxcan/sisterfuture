package com.stupidbeauty.sisterfuture.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.json.JSONTokener;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tolerant parser for model-generated tool arguments.
 *
 * It only accepts a complete JSON object after removing common transport noise.
 * When recovery is impossible, callers receive an empty object so the existing
 * tool parameter validation can report which required value is missing.
 */
public final class ToolArgumentsParser
{
  private ToolArgumentsParser()
  {
  }

  public static JSONObject parseOrEmpty(String rawArguments)
  {
    JSONObject recovered = tryParse(rawArguments);
    return recovered != null ? recovered : new JSONObject();
  }

  static JSONObject tryParse(String rawArguments)
  {
    if (rawArguments == null || rawArguments.trim().isEmpty())
    {
      return new JSONObject();
    }

    Set<String> candidates = new LinkedHashSet<>();
    String trimmed = rawArguments.trim();
    candidates.add(trimmed);

    String withoutFence = stripMarkdownFence(trimmed);
    candidates.add(withoutFence);

    String extracted = extractFirstJsonObject(withoutFence);
    if (extracted != null)
    {
      candidates.add(extracted);
    }

    for (String candidate : candidates)
    {
      JSONObject parsed = parseCandidate(candidate);
      if (parsed != null)
      {
        return parsed;
      }
    }

    return null;
  }

  private static JSONObject parseCandidate(String candidate)
  {
    if (candidate == null || candidate.trim().isEmpty())
    {
      return null;
    }

    try
    {
      return new JSONObject(candidate);
    }
    catch (Exception ignored)
    {
    }

    // Some providers double-encode the arguments object as a JSON string.
    try
    {
      Object parsed = new JSONTokener(candidate).nextValue();
      if (parsed instanceof JSONObject)
      {
        return (JSONObject) parsed;
      }
      if (parsed instanceof String)
      {
        return new JSONObject(((String) parsed).trim());
      }
    }
    catch (Exception ignored)
    {
    }

    // Gson accepts a few harmless model variations such as unquoted keys.
    try
    {
      JsonElement element = new JsonParser().parse(candidate);
      if (element != null && element.isJsonObject())
      {
        return new JSONObject(element.toString());
      }
      if (element != null && element.isJsonPrimitive()
        && element.getAsJsonPrimitive().isString())
      {
        return new JSONObject(element.getAsString().trim());
      }
    }
    catch (Exception ignored)
    {
    }

    return null;
  }

  private static String stripMarkdownFence(String value)
  {
    if (!value.startsWith("```"))
    {
      return value;
    }

    int firstLineEnd = value.indexOf('\n');
    int closingFence = value.lastIndexOf("```");
    if (firstLineEnd < 0 || closingFence <= firstLineEnd)
    {
      return value;
    }
    return value.substring(firstLineEnd + 1, closingFence).trim();
  }

  /** Extracts the first balanced object while respecting braces inside strings. */
  private static String extractFirstJsonObject(String value)
  {
    int start = value.indexOf('{');
    if (start < 0)
    {
      return null;
    }

    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    char quote = 0;

    for (int i = start; i < value.length(); i++)
    {
      char current = value.charAt(i);
      if (inString)
      {
        if (escaped)
        {
          escaped = false;
        }
        else if (current == '\\')
        {
          escaped = true;
        }
        else if (current == quote)
        {
          inString = false;
        }
        continue;
      }

      if (current == '"' || current == '\'')
      {
        inString = true;
        quote = current;
      }
      else if (current == '{')
      {
        depth++;
      }
      else if (current == '}')
      {
        depth--;
        if (depth == 0)
        {
          return value.substring(start, i + 1);
        }
      }
    }

    return null;
  }
}
