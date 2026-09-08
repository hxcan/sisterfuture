package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import androidx.annotation.NonNull;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Extracts deterministic metadata and a coarse, timestamped shot timeline from a local video. */
public class AnalyzeVideoTimelineTool implements Tool {
    private static final String TAG = "AnalyzeVideoTimeline";
    private static final int DEFAULT_SAMPLE_FPS = 5;
    private static final int DEFAULT_MAX_FRAMES = 300;
    private static final int MAX_SAMPLE_FPS = 10;
    private static final int MAX_OUTPUT_KEYFRAMES = 24;
    private static final double CUT_THRESHOLD = 0.32;

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AnalyzeVideoTimelineTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String getName() {
        return "analyzeVideoTimeline";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject properties = new JSONObject();
            properties.put("localPath", new JSONObject()
                .put("type", "string")
                .put("description", "视频本地绝对路径，优先使用聊天上下文中的“视频本地路径”"));
            properties.put("sampleFps", new JSONObject()
                .put("type", "integer").put("minimum", 1).put("maximum", MAX_SAMPLE_FPS)
                .put("default", DEFAULT_SAMPLE_FPS)
                .put("description", "工程化抽帧率，默认 5 fps，最高 10 fps"));
            properties.put("maxFrames", new JSONObject()
                .put("type", "integer").put("minimum", 10).put("maximum", 600)
                .put("default", DEFAULT_MAX_FRAMES)
                .put("description", "最多分析的帧数；长视频会均匀降低实际采样率"));

            JSONObject function = new JSONObject();
            function.put("name", getName());
            function.put("description", "分析本地视频并生成结构化时间轴。返回真实时长、尺寸、旋转、原始帧率、音轨信息，以及带精确时间戳的疑似镜头分段、画面变化强度和代表帧路径。适合在视频复刻前调用；本工具不臆测画面语义或音频节拍。工程化抽帧率最高 10 fps。" );
            function.put("parameters", new JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", new JSONArray().put("localPath")));
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
        executor.execute(() -> {
            try {
                callback.onResult(analyze(arguments));
            } catch (Exception e) {
                FileLogger.e(TAG, "视频时间轴分析失败", e);
                callback.onError(e);
            }
        });
    }

    private JSONObject analyze(JSONObject arguments) throws Exception {
        String localPath = arguments.optString("localPath", "").trim();
        File video = new File(localPath).getCanonicalFile();
        if (!video.isFile()) throw new IllegalArgumentException("视频文件不存在：" + localPath);

        int requestedFps = clamp(arguments.optInt("sampleFps", DEFAULT_SAMPLE_FPS), 1, MAX_SAMPLE_FPS);
        int maxFrames = clamp(arguments.optInt("maxFrames", DEFAULT_MAX_FRAMES), 10, 600);
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(video.getAbsolutePath());
            long durationMs = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (durationMs <= 0) throw new IllegalArgumentException("无法读取视频时长：" + localPath);

            int width = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int rotation = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            String sourceFpsText = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
            double sourceFps = parseDouble(sourceFpsText);
            boolean hasAudio = "yes".equalsIgnoreCase(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));

            int desiredFrames = Math.max(2, (int) Math.ceil(durationMs * requestedFps / 1000.0));
            int sampleCount = Math.min(maxFrames, desiredFrames);
            double intervalMs = sampleCount <= 1 ? durationMs : durationMs / (double) (sampleCount - 1);
            double effectiveFps = sampleCount * 1000.0 / durationMs;

            File outputDir = new File(context.getCacheDir(), "video_timeline/" + System.currentTimeMillis());
            if (!outputDir.mkdirs() && !outputDir.isDirectory()) {
                throw new IllegalStateException("无法创建关键帧目录：" + outputDir);
            }

            List<Sample> samples = new ArrayList<>();
            int[] previousSignature = null;
            for (int i = 0; i < sampleCount; i++) {
                long timestampMs = Math.min(durationMs - 1, Math.round(i * intervalMs));
                Bitmap frame = retriever.getFrameAtTime(timestampMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST);
                if (frame == null) continue;
                int[] signature = signature(frame);
                double change = previousSignature == null ? 0 : difference(previousSignature, signature);
                samples.add(new Sample(timestampMs, change));
                previousSignature = signature;
                frame.recycle();
            }
            if (samples.isEmpty()) throw new IllegalStateException("视频未能抽取到任何画面");

            List<Segment> segments = splitSegments(samples, durationMs);
            JSONArray timeline = new JSONArray();
            JSONArray attachments = new JSONArray();
            int attachmentCount = 0;
            for (int i = 0; i < segments.size(); i++) {
                Segment segment = segments.get(i);
                Sample representative = segment.representative;
                Bitmap representativeFrame = retriever.getFrameAtTime(representative.timestampMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST);
                if (representativeFrame == null) continue;
                Bitmap outputFrame = scaleDown(representativeFrame, 960);
                File keyframe = new File(outputDir, String.format(Locale.US,
                    "shot_%03d_%dms.jpg", i + 1, representative.timestampMs));
                saveJpeg(outputFrame, keyframe);

                JSONObject shot = new JSONObject();
                shot.put("index", i + 1);
                shot.put("startMs", segment.startMs);
                shot.put("endMs", segment.endMs);
                shot.put("start", formatTime(segment.startMs));
                shot.put("end", formatTime(segment.endMs));
                shot.put("representativeFrameMs", representative.timestampMs);
                shot.put("representativeFramePath", keyframe.getAbsolutePath());
                shot.put("averageChange", round(segment.averageChange));
                shot.put("peakChange", round(segment.peakChange));
                timeline.put(shot);

                if (attachmentCount < MAX_OUTPUT_KEYFRAMES) {
                    attachments.put(new JSONObject().put("type", "image")
                        .put("url", "file://" + keyframe.getAbsolutePath())
                        .put("metadata", new JSONObject()
                            .put("width", outputFrame.getWidth())
                            .put("height", outputFrame.getHeight())
                            .put("mimeType", "image/jpeg")));
                    attachmentCount++;
                }
                if (outputFrame != representativeFrame) outputFrame.recycle();
                representativeFrame.recycle();
            }

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("localPath", video.getAbsolutePath());
            result.put("durationMs", durationMs);
            result.put("duration", formatTime(durationMs));
            result.put("width", width);
            result.put("height", height);
            result.put("rotationDegrees", rotation);
            result.put("sourceFps", sourceFps > 0 ? round(sourceFps) : JSONObject.NULL);
            result.put("hasAudio", hasAudio);
            result.put("requestedSampleFps", requestedFps);
            result.put("effectiveSampleFps", round(effectiveFps));
            result.put("analyzedFrameCount", samples.size());
            result.put("cutDetectionThreshold", CUT_THRESHOLD);
            result.put("timeline", timeline);
            result.put("attachments", attachments);
            result.put("note", "镜头边界由相邻采样帧的画面变化估算；代表帧用于后续视觉判断。音频节拍尚未分析。" );
            return result;
        } finally {
            retriever.release();
        }
    }

    private static List<Segment> splitSegments(List<Sample> samples, long durationMs) {
        List<Segment> result = new ArrayList<>();
        int start = 0;
        for (int i = 1; i < samples.size(); i++) {
            if (samples.get(i).change >= CUT_THRESHOLD) {
                result.add(makeSegment(samples, start, i - 1, samples.get(i).timestampMs));
                start = i;
            }
        }
        result.add(makeSegment(samples, start, samples.size() - 1, durationMs));
        return result;
    }

    private static Segment makeSegment(List<Sample> samples, int start, int end, long endMs) {
        double total = 0;
        double peak = 0;
        Sample representative = samples.get(start + (end - start) / 2);
        for (int i = start; i <= end; i++) {
            total += samples.get(i).change;
            peak = Math.max(peak, samples.get(i).change);
        }
        return new Segment(samples.get(start).timestampMs, endMs, representative,
            total / Math.max(1, end - start + 1), peak);
    }

    private static int[] signature(Bitmap source) {
        Bitmap small = Bitmap.createScaledBitmap(source, 24, 24, true);
        int[] pixels = new int[24 * 24];
        small.getPixels(pixels, 0, 24, 0, 0, 24, 24);
        if (small != source) small.recycle();
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            pixels[i] = ((color >> 16) & 0xff) * 30 / 100
                + ((color >> 8) & 0xff) * 59 / 100 + (color & 0xff) * 11 / 100;
        }
        return pixels;
    }

    private static double difference(int[] first, int[] second) {
        long total = 0;
        for (int i = 0; i < first.length; i++) total += Math.abs(first[i] - second[i]);
        return total / (255.0 * first.length);
    }

    private static void saveJpeg(Bitmap bitmap, File target) throws Exception {
        try (FileOutputStream output = new FileOutputStream(target)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                throw new IllegalStateException("关键帧编码失败：" + target);
            }
        }
    }

    private static Bitmap scaleDown(Bitmap source, int maximumSide) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maximumSide) return source;
        double scale = maximumSide / (double) longest;
        return Bitmap.createScaledBitmap(source, Math.max(1, (int) Math.round(width * scale)),
            Math.max(1, (int) Math.round(height * scale)), true);
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "当用户要求复刻、拆解或精细理解视频，并且上下文中存在“视频本地路径”时，"
            + "先调用 analyzeVideoTimeline 获取真实时长、镜头边界、运动变化和带时间戳的代表帧。"
            + "描述分镜时引用工具返回的时间戳，不要把抽样帧数量误当作原视频总帧数或时长。";
    }

    private static String formatTime(long milliseconds) {
        long minutes = milliseconds / 60000;
        double seconds = (milliseconds % 60000) / 1000.0;
        return String.format(Locale.US, "%02d:%06.3f", minutes, seconds);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Exception ignored) { return 0; }
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; }
    }

    private static double parseDouble(String value) {
        try { return Double.parseDouble(value); } catch (Exception ignored) { return 0; }
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static class Sample {
        final long timestampMs;
        final double change;

        Sample(long timestampMs, double change) {
            this.timestampMs = timestampMs;
            this.change = change;
        }
    }

    private static class Segment {
        final long startMs;
        final long endMs;
        final Sample representative;
        final double averageChange;
        final double peakChange;

        Segment(long startMs, long endMs, Sample representative, double averageChange, double peakChange) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.representative = representative;
            this.averageChange = averageChange;
            this.peakChange = peakChange;
        }
    }
}
