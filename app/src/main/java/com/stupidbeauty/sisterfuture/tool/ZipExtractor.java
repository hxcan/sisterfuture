package com.stupidbeauty.sisterfuture.tool;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.*;

/** Streaming extraction into a new directory only. Never modifies the archive. */
final class ZipExtractor {
    static final long MAX_BYTES = 2L * 1024 * 1024 * 1024;
    static final int MAX_ENTRIES = 10000;
    static final class Result {
        long bytes;
        int files;
    }

    static Result extract(File archive, File destination) throws IOException {
        return extract(archive, destination, MAX_BYTES, MAX_ENTRIES);
    }

    static Result extract(File archive, File destination, long maxBytes, int maxEntries) throws IOException {
        if (!archive.isAbsolute() || !archive.isFile() || !archive.canRead())
            throw new IOException("zipPath 必须是可读的本地 ZIP 绝对路径");
        if (!destination.isAbsolute()) throw new IOException("outputDirectory 必须是绝对路径");
        destination = destination.getCanonicalFile();
        // Opening the central directory rejects non-ZIP/truncated archives before creating output.
        try (ZipFile zip = new ZipFile(archive)) {
            if (!destination.mkdir()) throw new IOException("输出目录已存在或无法创建，请指定新的 outputDirectory（父目录必须存在）");
            boolean complete = false;
            try {
                File root = destination.getCanonicalFile();
                String prefix = root.getPath() + File.separator;
                Result result = new Result();
                int count = 0;
                byte[] buffer = new byte[32768];
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (++count > maxEntries) throw new IOException("ZIP 条目超过安全上限 " + maxEntries);
                    String name = entry.getName().replace('\\', '/');
                    if (name.startsWith("/") || name.matches("^[A-Za-z]:.*") || name.indexOf(0) >= 0)
                        throw new IOException("ZIP 包含不安全路径");
                    File target = new File(root, name).getCanonicalFile();
                    if (!target.getPath().startsWith(prefix)) throw new IOException("ZIP 路径越出输出目录");
                    if (entry.isDirectory()) {
                        if (!target.isDirectory() && !target.mkdirs()) throw new IOException("无法创建子目录");
                        continue;
                    }
                    File parent = target.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建子目录");
                    if (!target.createNewFile()) throw new IOException("ZIP 内存在重复或冲突路径");
                    CRC32 crc = new CRC32();
                    long size = 0;
                    try (InputStream in = zip.getInputStream(entry);
                         OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                        int n;
                        while ((n = in.read(buffer)) != -1) {
                            if (n > maxBytes - result.bytes) throw new IOException("解压数据超过安全上限 " + maxBytes + " 字节");
                            out.write(buffer, 0, n);
                            crc.update(buffer, 0, n);
                            size += n;
                            result.bytes += n;
                        }
                    }
                    if (size != entry.getSize() || crc.getValue() != entry.getCrc())
                        throw new IOException("ZIP 数据损坏：长度或 CRC 校验失败");
                    result.files++;
                }
                complete = true;
                return result;
            } finally {
                // Only this invocation's newly created tree; source and existing directories are untouched.
                if (!complete && !removeTree(destination))
                    throw new IOException("解压失败，部分文件未能清理；请检查 " + destination.getAbsolutePath());
            }
        }
    }

    private static boolean removeTree(File file) throws IOException {
        boolean ok = true;
        if (file.getCanonicalFile().equals(file.getAbsoluteFile()) && file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return false;
            for (File child : children) ok = removeTree(child) && ok;
        }
        return file.delete() && ok;
    }
}
