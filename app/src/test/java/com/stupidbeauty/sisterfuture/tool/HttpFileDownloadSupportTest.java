package com.stupidbeauty.sisterfuture.tool;

import com.sun.net.httpserver.HttpServer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import okhttp3.HttpUrl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HttpFileDownloadSupportTest
{
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void preservesBinaryPayloadAndCommitsCompletedFile() throws Exception
  {
    byte[] payload = new byte[]{0x00, 0x01, (byte) 0xff, (byte) 0x80, 0x7f};
    File target = new File(temporaryFolder.getRoot(), "payload.bin");

    long size = HttpFileDownloadSupport.writeAtomically(
      new ByteArrayInputStream(payload), payload.length, target, payload.length);

    assertEquals(payload.length, size);
    assertArrayEquals(payload, readBytes(target));
    assertNoPartialFiles();
  }

  @Test
  public void rejectsDeclaredLengthAboveLimitWithoutCreatingFiles() throws Exception
  {
    File target = new File(temporaryFolder.getRoot(), "too-large.bin");

    assertThrows(IOException.class, () -> HttpFileDownloadSupport.writeAtomically(
      new ByteArrayInputStream(new byte[0]), 11L, target, 10L));

    assertFalse(target.exists());
    assertNoPartialFiles();
  }

  @Test
  public void enforcesLimitWhileStreamingUnknownLength() throws Exception
  {
    File target = new File(temporaryFolder.getRoot(), "unknown-length.bin");

    assertThrows(IOException.class, () -> HttpFileDownloadSupport.writeAtomically(
      new ByteArrayInputStream(new byte[11]), -1L, target, 10L));

    assertFalse(target.exists());
    assertNoPartialFiles();
  }

  @Test
  public void acceptsPayloadExactlyAtLimit() throws Exception
  {
    byte[] payload = new byte[10];
    File target = new File(temporaryFolder.getRoot(), "at-limit.bin");

    long size = HttpFileDownloadSupport.writeAtomically(
      new ByteArrayInputStream(payload), -1L, target, payload.length);

    assertEquals(payload.length, size);
    assertTrue(target.exists());
    assertNoPartialFiles();
  }

  @Test
  public void removesPartialFileWhenInputFails() throws Exception
  {
    File target = new File(temporaryFolder.getRoot(), "interrupted.bin");
    InputStream interrupted = new InputStream()
    {
      private boolean deliveredFirstChunk;

      @Override
      public int read() throws IOException
      {
        throw new IOException("simulated disconnect");
      }

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException
      {
        if (!deliveredFirstChunk)
        {
          deliveredFirstChunk = true;
          buffer[offset] = 1;
          buffer[offset + 1] = 2;
          return 2;
        }
        throw new IOException("simulated disconnect");
      }
    };

    assertThrows(IOException.class, () -> HttpFileDownloadSupport.writeAtomically(
      interrupted, -1L, target, 100L));

    assertFalse(target.exists());
    assertNoPartialFiles();
  }

  @Test
  public void lengthMismatchDoesNotCommitFile() throws Exception
  {
    File target = new File(temporaryFolder.getRoot(), "truncated.bin");

    assertThrows(IOException.class, () -> HttpFileDownloadSupport.writeAtomically(
      new ByteArrayInputStream(new byte[3]), 4L, target, 100L));

    assertFalse(target.exists());
    assertNoPartialFiles();
  }

  @Test
  public void refusesToOverwriteExistingTarget() throws Exception
  {
    File target = new File(temporaryFolder.getRoot(), "existing.bin");
    byte[] original = new byte[]{9, 8, 7};
    writeBytes(target, original);

    assertThrows(IOException.class, () -> HttpFileDownloadSupport.writeAtomically(
      new ByteArrayInputStream(new byte[]{1, 2, 3}), 3L, target, 100L));

    assertArrayEquals(original, readBytes(target));
    assertNoPartialFiles();
  }

  @Test
  public void sanitizesUntrustedFileNames() throws Exception
  {
    String[] unsafeNames = new String[]{
      "../../evil.bin",
      "..\\evil.bin",
      "nested/name.bin",
      "line\r\nbreak.bin",
      "nul\u0000byte.bin",
      "safe\u202Egnp.exe"
    };

    for (String unsafeName : unsafeNames)
    {
      String sanitized = HttpFileDownloadSupport.sanitizeFileName(unsafeName);
      assertFalse(sanitized.isEmpty());
      assertFalse(sanitized.contains("/"));
      assertFalse(sanitized.contains("\\"));
      assertFalse(sanitized.contains(".."));
      for (int index = 0; index < sanitized.length(); index++)
      {
        assertFalse(Character.isISOControl(sanitized.charAt(index)));
        assertNotEquals(Character.FORMAT, Character.getType(sanitized.charAt(index)));
      }

      File target = new File(temporaryFolder.getRoot(), sanitized).getCanonicalFile();
      assertEquals(temporaryFolder.getRoot().getCanonicalPath(),
        target.getParentFile().getCanonicalPath());
    }

    assertEquals("中文文件.bin", HttpFileDownloadSupport.sanitizeFileName("中文文件.bin"));
    assertEquals("", HttpFileDownloadSupport.sanitizeFileName("."));
    assertEquals("_nomedia", HttpFileDownloadSupport.sanitizeFileName(".nomedia"));
    String longFileName = HttpFileDownloadSupport.sanitizeFileName(
      "很长的文件名很长的文件名很长的文件名很长的文件名很长的文件名很长的文件名"
        + "很长的文件名很长的文件名.bin");
    assertTrue(longFileName.getBytes("UTF-8").length <= 180);
    assertTrue(longFileName.endsWith(".bin"));
  }

  @Test
  public void reservesFreeSpaceWhenCalculatingStreamingLimit()
  {
    long reserve = HttpFileDownloadSupport.RESERVED_FREE_SPACE_BYTES;

    assertEquals(100L, HttpFileDownloadSupport.calculateEffectiveLimit(100L, 1000L + reserve));
    assertEquals(80L, HttpFileDownloadSupport.calculateEffectiveLimit(100L, 80L + reserve));
    assertEquals(0L, HttpFileDownloadSupport.calculateEffectiveLimit(100L, reserve - 1L));
    assertEquals(0L, HttpFileDownloadSupport.calculateEffectiveLimit(100L, 0L));
  }

  @Test
  public void rejectsHttpsToHttpDowngradeButAllowsUpgrade() throws Exception
  {
    HttpUrl https = HttpUrl.parse("https://example.com/file.bin");
    HttpUrl http = HttpUrl.parse("http://example.com/file.bin");

    assertThrows(IOException.class,
      () -> HttpFileDownloadTool.enforceNoHttpsDowngrade(https, http));
    HttpFileDownloadTool.enforceNoHttpsDowngrade(https, https);
    HttpFileDownloadTool.enforceNoHttpsDowngrade(http, https);
    HttpFileDownloadTool.enforceNoHttpsDowngrade(http, http);
  }

  @Test
  public void choosesNonCollidingDefaultName() throws Exception
  {
    File existing = new File(temporaryFolder.getRoot(), "report.pdf");
    writeBytes(existing, new byte[]{1});

    File available = HttpFileDownloadSupport.findAvailableTarget(
      temporaryFolder.getRoot(), "report.pdf");

    assertEquals("report (1).pdf", available.getName());
    assertNotEquals(existing.getCanonicalPath(), available.getCanonicalPath());
  }

  @Test
  public void toolDefinitionExposesOnlyMinimalDownloadParameters() throws Exception
  {
    JSONObject definition = new HttpFileDownloadTool(null).getDefinition()
      .getJSONObject("function");
    JSONObject properties = definition.getJSONObject("parameters")
      .getJSONObject("properties");
    JSONArray required = definition.getJSONObject("parameters").getJSONArray("required");

    assertEquals("downloadHttpFile", definition.getString("name"));
    assertEquals(3, properties.length());
    assertTrue(properties.has("url"));
    assertTrue(properties.has("phone_path"));
    assertTrue(properties.has("timeout_sec"));
    assertEquals(1, required.length());
    assertEquals("url", required.getString(0));
    assertFalse(new HttpFileDownloadTool(null).shouldRecordParameterHistory());
  }

  @Test
  public void downloadsBinaryResponseAndFollowsRedirect() throws Exception
  {
    byte[] payload = new byte[]{0x00, (byte) 0xff, 0x01, 0x02};
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/file.bin", exchange ->
    {
      exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
      exchange.sendResponseHeaders(200, payload.length);
      try (OutputStream responseBody = exchange.getResponseBody())
      {
        responseBody.write(payload);
      }
    });
    server.createContext("/redirect", exchange ->
    {
      exchange.getResponseHeaders().add("Location", "/file.bin");
      exchange.sendResponseHeaders(302, -1L);
      exchange.close();
    });
    server.start();

    try
    {
      File target = new File(temporaryFolder.getRoot(), "downloaded.bin");
      HttpUrl url = HttpUrl.parse(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/redirect");

      HttpFileDownloadTool.DownloadResult result = new HttpFileDownloadTool(null)
        .download(url, target, 30);

      assertArrayEquals(payload, readBytes(target));
      assertEquals(payload.length, result.sizeBytes);
      assertEquals(200, result.statusCode);
      assertEquals(1, result.redirectCount);
      assertEquals("file.bin", result.finalUrl.pathSegments()
        .get(result.finalUrl.pathSegments().size() - 1));
      assertEquals("application/octet-stream", result.contentType);
      assertNoPartialFiles();
    }
    finally
    {
      server.stop(0);
    }
  }

  @Test
  public void httpErrorDoesNotCreateTargetFile() throws Exception
  {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/missing", exchange ->
    {
      exchange.sendResponseHeaders(404, -1L);
      exchange.close();
    });
    server.start();

    try
    {
      File target = new File(temporaryFolder.getRoot(), "error-page.bin");
      HttpUrl url = HttpUrl.parse(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/missing");

      assertThrows(IOException.class, () -> new HttpFileDownloadTool(null)
        .download(url, target, 30));

      assertFalse(target.exists());
      assertNoPartialFiles();
    }
    finally
    {
      server.stop(0);
    }
  }

  private void assertNoPartialFiles()
  {
    File[] partialFiles = temporaryFolder.getRoot().listFiles(
      (directory, name) -> name.endsWith(".part"));
    assertTrue(partialFiles == null || partialFiles.length == 0);
  }

  private static void writeBytes(File file, byte[] content) throws IOException
  {
    try (FileOutputStream output = new FileOutputStream(file))
    {
      output.write(content);
    }
  }

  private static byte[] readBytes(File file) throws IOException
  {
    try (FileInputStream input = new FileInputStream(file);
         ByteArrayOutputStream output = new ByteArrayOutputStream())
    {
      byte[] buffer = new byte[1024];
      int bytesRead;
      while ((bytesRead = input.read(buffer)) != -1)
      {
        output.write(buffer, 0, bytesRead);
      }
      return output.toByteArray();
    }
  }
}
