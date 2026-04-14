package com.stupidbeauty.sisterfuture.adapter;

import com.stupidbeauty.sisterfuture.bean.MessageItem;
import com.stupidbeauty.sisterfuture.bean.MessageType;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Spannable;
import android.text.Selection;
import butterknife.ButterKnife;
import com.stupidbeauty.sisterfuture.network.TongYiClient.OnResponseListener;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.stupidbeauty.sisterfuture.R;
import java.util.List;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import com.stupidbeauty.sisterfuture.R;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.annotation.NonNull;
import butterknife.BindView;
import butterknife.ButterKnife;
import io.noties.markwon.Markwon;
import io.noties.markwon.core.CorePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import android.util.Base64;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_USER = 0;
    private static final int TYPE_AI = 1;
    private static final int TYPE_TOOL_CALL_RESULT = 2;
    
    // 🔥 #4881 救援模式：仅限制 TOOL_CALL_RESULT 类型消息的最大显示长度
    private static final int MAX_TOOL_RESULT_DISPLAY_LENGTH = 10000; // 10KB 显示限制
    private static final String TAG = "MessageAdapter";

    private List<MessageItem> messages = new ArrayList<>();

    public MessageItem getItem(int position) {
        return messages.get(position);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            View itemView = inflater.inflate(R.layout.item_user_message, parent, false);
            return new UserMessageViewHolder(itemView);
        } else if (viewType == TYPE_AI) {
            View itemView = inflater.inflate(R.layout.item_ai_message, parent, false);
            return new AIMessageViewHolder(itemView);
        } else {
            View itemView = inflater.inflate(R.layout.item_tool_call_result_message, parent, false);
            return new ToolCallResultViewHolder(itemView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageItem message = messages.get(position);
        if (holder instanceof UserMessageViewHolder) {
            ((UserMessageViewHolder) holder).bind(message);
        } else if (holder instanceof AIMessageViewHolder) {
            ((AIMessageViewHolder) holder).bind(message);
        } else if (holder instanceof ToolCallResultViewHolder) {
            ((ToolCallResultViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemViewType(int position) {
        MessageType type = messages.get(position).getType();
        switch (type) {
            case USER: return TYPE_USER;
            case AI: return TYPE_AI;
            case TOOL_CALL_RESULT: return TYPE_TOOL_CALL_RESULT;
            default: throw new IllegalArgumentException("Unknown message type");
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(MessageItem message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateAiMessage(int position, String newText) {
        MessageItem item = messages.get(position);
        if (item.getType() == MessageType.AI) {
            item.text = newText;
            notifyItemChanged(position);
        }
    }

    // 🔥 #4881 救援：仅截断工具调用结果消息的超长文本
    private static String limitToolResultDisplayLength(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() > MAX_TOOL_RESULT_DISPLAY_LENGTH) {
            FileLogger.w(TAG, "🔥 工具结果文本超长，截断显示：" + text.length() + " → " + MAX_TOOL_RESULT_DISPLAY_LENGTH + " 字符");
            return text.substring(0, MAX_TOOL_RESULT_DISPLAY_LENGTH) + "\n\n... [内容过长，已截断显示 " + (text.length() - MAX_TOOL_RESULT_DISPLAY_LENGTH) + " 字符] ...";
        }
        return text;
    }

    public static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.user_text) TextView textView;
        @BindView(R.id.user_image) ImageView imageView; // 🖼️ 新增

        public UserMessageViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    if (item.getItemId() == android.R.id.copy) {
                        String selectedText = textView.getText().toString();
                        ClipboardManager clipboard = (ClipboardManager)itemView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("selected text", selectedText);
                        clipboard.setPrimaryClip(clip);
                        mode.finish();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    Spannable spannable = (Spannable)textView.getText();
                    Selection.setSelection(spannable, 0, 0);
                }
            });
        }

        public void bind(MessageItem message) {
            FileLogger.d(TAG, "🔍 [BIND] 绑定消息 | position=" + getAdapterPosition() + " | hasImage=" + (message.getImageUrl() != null));
            
            // 🖼️ 检测是否有图片
            if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
                FileLogger.d(TAG, "🖼️ [IMAGE_FOUND] 检测到图片，开始解码");
                try {
                    // 处理 Base64 前缀 - 支持多种格式
                    String base64Data = message.getImageUrl();
                    
                    // 检查并去除 data:image/...;base64, 前缀
                    if (base64Data.startsWith("data:image")) {
                        int commaIndex = base64Data.indexOf(',');
                        if (commaIndex > 0) {
                            String prefix = base64Data.substring(0, commaIndex);
                            base64Data = base64Data.substring(commaIndex + 1);
                            FileLogger.d(TAG, "✂️ [PREFIX_REMOVED] 已去除 Base64 前缀：" + prefix);
                        }
                    }
                    
                    // 清理可能存在的空白字符
                    base64Data = base64Data.trim();
                    
                    // 验证 Base64 字符串是否有效
                    if (base64Data.isEmpty()) {
                        FileLogger.e(TAG, "❌ [BASE64_EMPTY] Base64 数据为空");
                        imageView.setImageBitmap(null);
                        imageView.setVisibility(View.GONE);
                        return;
                    }
                    
                    FileLogger.d(TAG, "📦 [DECODE_START] 开始 Base64 解码 | 数据长度=" + base64Data.length());
                    
                    // 解码 Base64 图片 - 使用 NO_WRAP 标志
                    byte[] decodedString = Base64.decode(base64Data, Base64.NO_WRAP);
                    FileLogger.d(TAG, "✅ [DECODED] Base64 解码完成 | 字节数组长度=" + decodedString.length);
                    
                    Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    
                    if (decodedBitmap != null) {
                        FileLogger.d(TAG, "✅ [BITMAP_DECODED] 图片解码成功，尺寸：" + decodedBitmap.getWidth() + "x" + decodedBitmap.getHeight());
                        // 显示图片
                        imageView.setImageBitmap(decodedBitmap);
                        imageView.setVisibility(View.VISIBLE);
                    } else {
                        FileLogger.e(TAG, "❌ [BITMAP_NULL] BitmapFactory.decodeByteArray 返回 null");
                        imageView.setImageBitmap(null);
                        imageView.setVisibility(View.GONE);
                    }
                    
                    // 文字部分只显示非图片内容（如果有）
                    textView.setText(message.getText());
                    FileLogger.d(TAG, "📝 [TEXT_SET] 文字已设置，长度：" + (message.getText() != null ? message.getText().length() : 0));
                } catch (IllegalArgumentException e) {
                    FileLogger.e(TAG, "❌ [DECODE_ERROR] Base64 格式错误", e);
                    FileLogger.e(TAG, "   📋 [RAW_DATA] Base64 前 100 字符：" + (message.getImageUrl().length() > 100 ? message.getImageUrl().substring(0, 100) + "..." : message.getImageUrl()));
                    imageView.setImageBitmap(null);
                    imageView.setVisibility(View.GONE);
                    textView.setText(message.getText());
                } catch (Exception e) {
                    FileLogger.e(TAG, "❌ [DECODE_ERROR] 图片解码失败", e);
                    imageView.setImageBitmap(null);
                    imageView.setVisibility(View.GONE);
                    textView.setText(message.getText());
                }
            } else {
                FileLogger.d(TAG, "🚫 [NO_IMAGE] 没有图片数据");
                // 没有图片，隐藏 ImageView，只显示文字
                imageView.setImageBitmap(null); // 清除旧图片，防止复用
                imageView.setVisibility(View.GONE);
                textView.setText(message.getText());
            }
        }
    }

    public static class AIMessageViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.ai_text) TextView textView;

        private final Markwon markwon;

        public AIMessageViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            markwon = Markwon.builder(itemView.getContext())
                .usePlugin(CorePlugin.create())
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(itemView.getContext()))
                .build();
            textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    if (item.getItemId() == android.R.id.copy) {
                        String selectedText = textView.getText().toString();
                        ClipboardManager clipboard = (ClipboardManager)itemView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("selected text", selectedText);
                        clipboard.setPrimaryClip(clip);
                        mode.finish();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    Spannable spannable = (Spannable)textView.getText();
                    Selection.setSelection(spannable, 0, 0);
                }
            });
        }

        public void bind(MessageItem message) {
            // AI 消息不限制长度
            markwon.setMarkdown(textView, message.getText());
        }
    }

    public static class ToolCallResultViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.tool_call_result_text) TextView textView;
        private boolean isExpanded = false;

        public ToolCallResultViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            
            // 点击切换展开/收起状态
            itemView.setOnClickListener(v -> {
                isExpanded = !isExpanded;
                if (isExpanded) {
                    textView.setMaxLines(Integer.MAX_VALUE);
                    textView.setEllipsize(null);
                } else {
                    textView.setMaxLines(5);
                    textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
                }
            });
            
            textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    if (item.getItemId() == android.R.id.copy) {
                        String selectedText = textView.getText().toString();
                        ClipboardManager clipboard = (ClipboardManager)itemView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("selected text", selectedText);
                        clipboard.setPrimaryClip(clip);
                        mode.finish();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    Spannable spannable = (Spannable)textView.getText();
                    Selection.setSelection(spannable, 0, 0);
                }
            });
        }

        public void bind(MessageItem message) {
            // 🔥 #4881 仅工具调用结果消息限制显示长度
            String text = limitToolResultDisplayLength(message.getText());
            textView.setText(text);
            // 重置状态 - 依赖布局文件中的 maxLines 和 ellipsize 设置
            isExpanded = false;
        }
    }
}