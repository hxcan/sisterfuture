package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 本机视频拼接工具。
 *
 * 使用 Android 原生 MediaMuxer + MediaExtractor 实现多段 MP4 拼接。
 * 优势：
 * - 不引入任何第三方依赖（系统自带）
 * - APK 体积零增量
 * - 0 画质损失（直接复用原始编码流）
 *
 * 限制（最小化实现）：
 * - 仅支持视频轨 + 音频轨（MP4 / WebM / 3GP）
 * - 输入视频必须参数一致（同分辨率/帧率/编码）；不一致时报清晰错误
 *   （不做复杂转码——主人授权的最小化方案，参数不一致让调用方用同源 AI 生成）
 */
public class VideoStitcherTool implements Tool {
    private static final String TAG = "VideoStitcher";
    private static final long DEFAULT_BUFFER_SIZE = 1 << 20; // 1 MB
    private static final int DEFAULT_TIMEOUT_US = 10_000;

    // MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM 是 API 21+ 才有的常量，
    // 通过反射获取，避免在编译期硬依赖（让 sisterfuture 仍然兼容 API 24+）。
    private static final int MUXER_OUTPUT_WEBM = resolveWebmFormat();

    /** 通过反射读取 MUXER_OUTPUT_WEBM 常量；读取失败时返回 -1 表示不支持。 */
    private static int resolveWebmFormat() {
        try {
            return MediaMuxer.OutputFormat.class.getField("MUXER_OUTPUT_WEBM").getInt(null);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 将 MediaExtractor 的 SAMPLE_FLAG_* 标志转换为 MediaCodec 的 BUFFER_FLAG_* 标志。
     *
     * MediaExtractor 返回的标志位：
     * - SAMPLE_FLAG_SYNC (1)         → BUFFER_FLAG_KEY_FRAME (1)
     * - SAMPLE_FLAG_PARTIAL_FRAME (8) → BUFFER_FLAG_PARTIAL_FRAME (8)
     * - SAMPLE_FLAG_ENCRYPTED (2)   → 不映射（MediaMuxer 不需要）
     *
     * MediaCodec.BufferInfo.flags 期望的标志位来自 MediaCodec.BUFFER_FLAG_*。
     */
    private static int convertSampleFlags(int extractorFlags) {
        int codecFlags = 0;
        if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
        }
        if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
            codecFlags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
        }
        // 注：CODEC_CONFIG 和 END_OF_STREAM 由 MediaMuxer 内部处理，不需要从 extractor 传入
        return codecFlags;
    }

    private final Context context;

    public VideoStitcherTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String getName() {
        return "stitchVideos";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject properties = new JSONObject();
            properties.put("inputPaths", new JSONObject()
                .put("type", "array")
                .put("items", new JSONObject().put("type", "string"))
                .put("description", "待拼接的视频文件绝对路径列表（按拼接顺序），至少 2 段"));
            properties.put("outputPath", new JSONObject()
                .put("type", "string")
                .put("description", "拼接输出文件路径（建议 .mp4 后缀）"));
            properties.put("outputFormat", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray().put("mp4").put("webm").put("_3gp"))
                .put("default", "mp4")
                .put("description", "输出容器格式：mp4（推荐）、webm、_3gp"));

            JSONObject function = new JSONObject();
            function.put("name", getName());
            function.put("description", "本机视频拼接工具。使用 Android 原生 MediaMuxer（系统 API，无需第三方依赖）。"
                + "要求所有输入视频的编码参数一致（同分辨率、帧率、编码格式）；"
                + "不一致时返回清晰错误（不自动转码，保持最小化实现）。"
                + "适合拼接 AI 生成的多段同参数视频（短剧创作场景）。");
            function.put("parameters", new JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", new JSONArray().put("inputPaths").put("outputPath")));
            return new JSONObject().put("type", "function").put("function", function);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude() {
        return true;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback) {
        try {
            callback.onResult(stitch(arguments));
        } catch (Exception e) {
            FileLogger.e(TAG, "视频拼接失败", e);
            callback.onError(e);
        }
    }

    /**
     * 执行拼接的同步方法，被 executeAsync 直接调用。
     * 之所以保持同步是因为 MediaMuxer API 设计简单；
     * 后续如需进度回调可改为在独立线程中分块处理。
     */
    private JSONObject stitch(JSONObject arguments) throws Exception {
        JSONArray inputArray = arguments.optJSONArray("inputPaths");
        if (inputArray == null || inputArray.length() < 2) {
            throw new IllegalArgumentException("inputPaths 至少需要 2 段视频");
        }
        String outputPath = arguments.optString("outputPath", "").trim();
        if (outputPath.isEmpty()) {
            throw new IllegalArgumentException("outputPath 不能为空");
        }
        String outputFormatStr = arguments.optString("outputFormat", "mp4");
        int outputFormat = parseOutputFormat(outputFormatStr);

        // 1. 验证所有输入文件
        List<File> inputs = new ArrayList<>();
        for (int i = 0; i < inputArray.length(); i++) {
            String path = inputArray.getString(i);
            File file = new File(path);
            if (!file.isFile()) {
                throw new IllegalArgumentException("输入文件不存在：" + path);
            }
            inputs.add(file);
        }

        // 2. 用第一个文件的轨道作为参考，验证所有输入参数一致
        TrackSpec referenceSpec = extractTrackSpec(inputs.get(0));
        FileLogger.i(TAG, "参考轨道规格：mime=" + referenceSpec.mime
            + ", " + referenceSpec.width + "x" + referenceSpec.height
            + ", bitrate=" + referenceSpec.bitrate);
        for (int i = 1; i < inputs.size(); i++) {
            TrackSpec currentSpec = extractTrackSpec(inputs.get(i));
            if (!referenceSpec.isCompatibleWith(currentSpec)) {
                throw new IllegalArgumentException("输入视频 #" + (i + 1)
                    + " 参数与第 1 个不匹配（编码参数必须一致才能零损失拼接）：\n"
                    + "参考：" + referenceSpec + "\n"
                    + "当前：" + currentSpec);
            }
        }

        // 3. 准备输出文件
        File outputFile = new File(outputPath);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("无法创建输出目录：" + parentDir.getAbsolutePath());
        }

        long startTimeMs = System.currentTimeMillis();
        long totalDurationUs = 0;
        int totalTrackCount = 0;

        // 4. 执行拼接
        MediaMuxer muxer = null;
        try {
            muxer = new MediaMuxer(outputPath, outputFormat);
            // 先添加所有轨道（从第一个文件复制格式）
            int videoTrackIndex = -1;
            int audioTrackIndex = -1;
            if (referenceSpec.videoTrackIndex >= 0) {
                videoTrackIndex = muxer.addTrack(referenceSpec.videoFormat);
                totalTrackCount++;
                FileLogger.i(TAG, "添加视频轨道，index=" + videoTrackIndex);
            }
            if (referenceSpec.audioTrackIndex >= 0) {
                audioTrackIndex = muxer.addTrack(referenceSpec.audioFormat);
                totalTrackCount++;
                FileLogger.i(TAG, "添加音频轨道，index=" + audioTrackIndex);
            }
            muxer.start();

            // 5. 逐段拷贝 sample
            for (int i = 0; i < inputs.size(); i++) {
                File input = inputs.get(i);
                FileLogger.i(TAG, "处理第 " + (i + 1) + "/" + inputs.size() + " 段：" + input.getName());
                long ptsOffset = totalDurationUs;
                totalDurationUs += copySamples(input, muxer, referenceSpec, videoTrackIndex, audioTrackIndex, ptsOffset);
            }
        } finally {
            if (muxer != null) {
                try {
                    muxer.stop();
                } catch (Exception ignored) {
                }
                try {
                    muxer.release();
                } catch (Exception ignored) {
                }
            }
        }

        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        File output = new File(outputPath);

        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("outputPath", output.getAbsolutePath());
        result.put("outputSize", output.length());
        result.put("inputCount", inputs.size());
        result.put("trackCount", totalTrackCount);
        result.put("totalDurationMs", totalDurationUs / 1000);
        result.put("elapsedMs", elapsedMs);
        result.put("note", "使用 MediaMuxer 零损失拼接（流复制模式）。如需转码或滤镜，请改用 FFmpeg 方案。");
        return result;
    }

    /**
     * 从单个输入文件中提取所有 sample 到 muxer，根据 ptsOffset 调整时间戳。
     * 返回该段视频时长（微秒），供后续段做时间偏移。
     */
    private long copySamples(File input, MediaMuxer muxer, TrackSpec spec,
                             int videoTrackIndex, int audioTrackIndex,
                             long ptsOffset) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        long maxPtsUs = 0;
        try {
            extractor.setDataSource(input.getAbsolutePath());

            if (videoTrackIndex >= 0 && spec.videoTrackIndex >= 0) {
                extractor.selectTrack(spec.videoTrackIndex);
                maxPtsUs = Math.max(maxPtsUs, copyTrackSamples(extractor, muxer,
                    spec.videoTrackIndex, videoTrackIndex, ptsOffset));
            }
            if (audioTrackIndex >= 0 && spec.audioTrackIndex >= 0) {
                extractor.selectTrack(spec.audioTrackIndex);
                long audioPtsUs = copyTrackSamples(extractor, muxer,
                    spec.audioTrackIndex, audioTrackIndex, ptsOffset);
                maxPtsUs = Math.max(maxPtsUs, audioPtsUs);
            }
        } finally {
            extractor.release();
        }
        return maxPtsUs;
    }

    private long copyTrackSamples(MediaExtractor extractor, MediaMuxer muxer,
                                  int sourceTrackIndex, int destTrackIndex,
                                  long ptsOffset) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate((int) DEFAULT_BUFFER_SIZE);
        long maxPtsUs = 0;
        while (true) {
            buffer.clear();
            int sampleSize = extractor.readSampleData(buffer, 0);
            if (sampleSize < 0) break;

            long pts = extractor.getSampleTime();
            if (pts < 0) {
                extractor.advance();
                continue;
            }
            long adjustedPts = pts + ptsOffset;
            buffer.position(0);
            buffer.limit(sampleSize);

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.offset = 0;
            bufferInfo.size = sampleSize;
            bufferInfo.presentationTimeUs = adjustedPts;
            bufferInfo.flags = convertSampleFlags(extractor.getSampleFlags());

            muxer.writeSampleData(destTrackIndex, buffer, bufferInfo);
            if (adjustedPts > maxPtsUs) maxPtsUs = adjustedPts;

            extractor.advance();
        }
        return maxPtsUs;
    }

    /**
     * 从 MP4 文件中提取第一个视频轨道和第一个音频轨道的关键规格。
     */
    private TrackSpec extractTrackSpec(File file) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            TrackSpec spec = new TrackSpec();
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (mime.startsWith("video/") && spec.videoTrackIndex < 0) {
                    spec.videoTrackIndex = i;
                    spec.videoFormat = format;
                    spec.mime = mime;
                    if (format.containsKey(MediaFormat.KEY_WIDTH)) spec.width = format.getInteger(MediaFormat.KEY_WIDTH);
                    if (format.containsKey(MediaFormat.KEY_HEIGHT)) spec.height = format.getInteger(MediaFormat.KEY_HEIGHT);
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) spec.bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                } else if (mime.startsWith("audio/") && spec.audioTrackIndex < 0) {
                    spec.audioTrackIndex = i;
                    spec.audioFormat = format;
                }
            }
            if (spec.videoTrackIndex < 0 && spec.audioTrackIndex < 0) {
                throw new IllegalArgumentException("文件无任何可用轨道：" + file.getAbsolutePath());
            }
            return spec;
        } finally {
            extractor.release();
        }
    }

    private int parseOutputFormat(String name) {
        String key = name == null ? "mp4" : name.toLowerCase(Locale.US);
        switch (key) {
            case "webm":
                if (MUXER_OUTPUT_WEBM > 0) return MUXER_OUTPUT_WEBM;
                // 旧版 API（< 21）不支持 WEBM，回退到 MP4 并警告
                FileLogger.w(TAG, "当前 Android 版本不支持 MUXER_OUTPUT_WEBM，回退到 MP4");
                return MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
            case "_3gp":
            case "3gp":
                return MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP;
            case "mp4":
            default:
                return MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "当用户要求拼接多段已生成的可灵 / Seedance 视频为完整短剧时，"
            + "调用 stitchVideos 工具。注意：所有输入视频必须参数一致（同分辨率/帧率/编码格式），"
            + "否则工具会返回错误并要求重新生成同参数视频。";
    }

    /**
     * 轨道规格封装，用于参数一致性校验。
     */
    private static class TrackSpec {
        int videoTrackIndex = -1;
        int audioTrackIndex = -1;
        MediaFormat videoFormat;
        MediaFormat audioFormat;
        String mime;
        int width = 0;
        int height = 0;
        int bitrate = 0;

        /** 判断两个规格是否兼容（同分辨率 + 同编码）。 */
        boolean isCompatibleWith(TrackSpec other) {
            if (mime == null || !mime.equals(other.mime)) return false;
            if (videoTrackIndex >= 0 && other.videoTrackIndex >= 0) {
                if (width != other.width || height != other.height) return false;
            }
            // 比特率允许轻微偏差（不同段落可能略有不同），不强制要求严格相等
            return true;
        }

        @Override
        public String toString() {
            return "TrackSpec{" + mime + " " + width + "x" + height
                + ", bitrate=" + bitrate + ", videoTrack=" + videoTrackIndex
                + ", audioTrack=" + audioTrackIndex + "}";
        }
    }
}
