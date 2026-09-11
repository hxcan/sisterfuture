package com.stupidbeauty.sisterfuture.tool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Pure file helpers kept Android-free so download integrity can be unit-tested. */
final class HttpFileDownloadSupport
{
  private static final int BUFFER_SIZE = 8192;
  private static final int MAX_FILE_NAME_BYTES = 180;
  private static final int MAX_EXTENSION_BYTES = 32;
  static final long RESERVED_FREE_SPACE_BYTES = 32L * 1024 * 1024;

  private HttpFileDownloadSupport()
  {
  }

  static long writeAtomically(InputStream input, long declaredLength, File target,
                              long maxBytes) throws IOException
  {
    if (input == null)
    {
      throw new IllegalArgumentException("input 不能为空");
    }
    if (target == null)
    {
      throw new IllegalArgumentException("target 不能为空");
    }
    if (maxBytes < 0L)
    {
      throw new IllegalArgumentException("maxBytes 不能为负数");
    }
    if (declaredLength > maxBytes)
    {
      throw new IOException("文件超过最大下载限制：" + maxBytes + " 字节");
    }

    File canonicalTarget = target.getCanonicalFile();
    if (canonicalTarget.exists())
    {
      throw new IOException("目标文件已存在，拒绝覆盖：" + canonicalTarget.getAbsolutePath());
    }

    File parent = canonicalTarget.getParentFile();
    if (parent == null)
    {
      throw new IOException("目标路径缺少父目录");
    }
    if (!parent.exists() && !parent.mkdirs())
    {
      throw new IOException("无法创建目标目录：" + parent.getAbsolutePath());
    }
    if (!parent.isDirectory())
    {
      throw new IOException("目标父路径不是目录：" + parent.getAbsolutePath());
    }
    long usableSpace = parent.getUsableSpace();
    long effectiveMaxBytes = calculateEffectiveLimit(maxBytes, usableSpace);
    if (declaredLength > effectiveMaxBytes)
    {
      throw new IOException("手机剩余空间不足，无法在保留安全空间的同时保存下载文件");
    }

    File partial = new File(parent,
      ".http-download-" + UUID.randomUUID() + ".part");
    if (!partial.createNewFile())
    {
      throw new IOException("无法创建下载临时文件");
    }

    boolean committed = false;
    long totalBytes = 0L;
    try
    {
      try (FileOutputStream output = new FileOutputStream(partial))
      {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1)
        {
          if (bytesRead == 0)
          {
            continue;
          }
          if (totalBytes > effectiveMaxBytes - bytesRead)
          {
            if (effectiveMaxBytes < maxBytes)
            {
              throw new IOException("手机剩余空间不足，下载已停止");
            }
            throw new IOException("文件超过最大下载限制：" + maxBytes + " 字节");
          }
          output.write(buffer, 0, bytesRead);
          totalBytes += bytesRead;
        }
      }

      if (declaredLength >= 0L && totalBytes != declaredLength)
      {
        throw new IOException(
          "下载字节数与 Content-Length 不一致：预期 " + declaredLength + "，实际 " + totalBytes);
      }
      if (canonicalTarget.exists())
      {
        throw new IOException("下载期间目标文件已被创建，拒绝覆盖");
      }
      if (!partial.renameTo(canonicalTarget))
      {
        throw new IOException("无法提交下载文件到目标路径");
      }

      committed = true;
      return totalBytes;
    }
    finally
    {
      if (!committed && partial.exists())
      {
        partial.delete();
      }
    }
  }

  static String sanitizeFileName(String value)
  {
    if (value == null)
    {
      return "";
    }

    StringBuilder result = new StringBuilder();
    for (int offset = 0; offset < value.length(); )
    {
      int current = value.codePointAt(offset);
      offset += Character.charCount(current);
      if (current == '/' || current == '\\' || Character.isISOControl(current)
        || Character.getType(current) == Character.FORMAT)
      {
        result.append('_');
      }
      else
      {
        result.appendCodePoint(current);
      }
    }

    String sanitized = result.toString().trim();
    if (".".equals(sanitized) || "..".equals(sanitized))
    {
      return "";
    }
    while (sanitized.contains(".."))
    {
      sanitized = sanitized.replace("..", "_");
    }
    while (sanitized.startsWith("."))
    {
      sanitized = "_" + sanitized.substring(1);
    }
    return truncateFileNameUtf8(sanitized, MAX_FILE_NAME_BYTES);
  }

  static long calculateEffectiveLimit(long maxBytes, long usableSpace)
  {
    if (maxBytes < 0L)
    {
      throw new IllegalArgumentException("maxBytes 不能为负数");
    }
    if (usableSpace <= 0L)
    {
      return 0L;
    }

    long storageBudget = Math.max(0L, usableSpace - RESERVED_FREE_SPACE_BYTES);
    return Math.min(maxBytes, storageBudget);
  }

  private static String truncateFileNameUtf8(String value, int maxBytes)
  {
    if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes)
    {
      return value;
    }

    int extensionIndex = value.lastIndexOf('.');
    if (extensionIndex > 0 && extensionIndex < value.length() - 1)
    {
      String extension = value.substring(extensionIndex);
      int extensionBytes = extension.getBytes(StandardCharsets.UTF_8).length;
      if (extensionBytes <= MAX_EXTENSION_BYTES && extensionBytes < maxBytes)
      {
        return truncateUtf8(value.substring(0, extensionIndex), maxBytes - extensionBytes)
          + extension;
      }
    }
    return truncateUtf8(value, maxBytes);
  }

  private static String truncateUtf8(String value, int maxBytes)
  {
    StringBuilder result = new StringBuilder();
    int byteCount = 0;
    for (int offset = 0; offset < value.length(); )
    {
      int current = value.codePointAt(offset);
      offset += Character.charCount(current);
      String character = new String(Character.toChars(current));
      int characterBytes = character.getBytes(StandardCharsets.UTF_8).length;
      if (byteCount + characterBytes > maxBytes)
      {
        break;
      }
      result.append(character);
      byteCount += characterBytes;
    }
    return result.toString();
  }

  static File findAvailableTarget(File directory, String requestedName) throws IOException
  {
    if (directory == null)
    {
      throw new IOException("Download 目录不可用");
    }

    String fileName = sanitizeFileName(requestedName);
    if (fileName.isEmpty())
    {
      throw new IOException("无法生成安全的下载文件名");
    }

    File candidate = new File(directory, fileName);
    if (!candidate.exists())
    {
      return candidate;
    }

    int extensionIndex = fileName.lastIndexOf('.');
    String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
    String extension = extensionIndex > 0 ? fileName.substring(extensionIndex) : "";
    for (int index = 1; index <= 9999; index++)
    {
      candidate = new File(directory, baseName + " (" + index + ")" + extension);
      if (!candidate.exists())
      {
        return candidate;
      }
    }

    throw new IOException("同名下载文件过多，请显式指定 phone_path");
  }
}
