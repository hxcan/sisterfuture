package com.stupidbeauty.sisterfuture.adapter;

import com.stupidbeauty.sisterfuture.bean.MessageItem;
import com.stupidbeauty.sisterfuture.bean.MessageType;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Spannable;
import android.text.Selection;
import butterknife.ButterKnife;
import com.stupidbeauty.sisterfuture.R;
import java.util.List;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.widget.ImageView;
import java.util.ArrayList;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
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
    
    // 🗑️ 删除消息回调接口 - 传递 messageId 以便精确删除
    public interface OnMessageDeleteListener {
        void onMessageDeleted(MessageItem message, int position, String messageId);
    }
    
    private OnMessageDeleteListener deleteListener;
    
    public void setOnMessageDeleteListener(OnMessageDeleteListener listener) {
        this.deleteListener = listener;
    }

    public MessageItem getItem(int position) {
        return messages.get(position);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            View itemView = inflater.inflate(R.layout.item_user_message, parent, false);
            return new UserMessageViewHolder(itemView, messages, deleteListener);
        } else if (viewType == TYPE_AI) {
            View itemView = inflater.inflate(R.layout.item_ai_message, parent, false);
            return new AIMessageViewHolder(itemView, messages, deleteListener);
        } else {
            View itemView = inflater.inflate(R.layout.item_tool_call_result_message, parent, false);
            return new ToolCallResultViewHolder(itemView, messages, deleteListener);
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

    // 🔗 新增：添加消息并返回消息项，方便后续关联
    public MessageItem addMessage(MessageItem message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
        return message;
    }

    // 🔗 新增：根据消息 ID 查找位置
    public int getMessagePositionById(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return -1;
        }
        
        for (int i = 0; i < messages.size(); i++) {
            MessageItem item = messages.get(i);
            if (item.getMessageId() != null && item.getMessageId().equals(messageId)) {
                return i;
            }
        }
        return -1;
    }

    // 🔗 新增：根据消息 ID 移除消息条目
    public boolean removeMessageById(String messageId) {
        int position = getMessagePositionById(messageId);
        if (position >= 0) {
            MessageItem removed = messages.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, messages.size() - position);
            
            // 回调删除监听器，传递 messageId
            if (deleteListener != null) {
                deleteListener.onMessageDeleted(removed, position, messageId);
            }
            return true;
        }
        return false;
    }
    
    // 🗑️ 根据位置移除消息
    public boolean removeMessage(int position) {
        if (position >= 0 && position < messages.size()) {
            MessageItem removed = messages.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, messages.size() - position);
            
            // 回调删除监听器，传递 messageId
            if (deleteListener != null) {
                String messageId = removed.getMessageId();
                deleteListener.onMessageDeleted(removed, position, messageId);
            }
            return true;
        }
        return false;
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
    
    // 🗑️ 显示长按菜单（同时包含"删除"和"复制"选项）- 传递 messageId
    private static void showLongPressMenuStatic(View anchorView, MessageItem message, int position, TextView textView, List<MessageItem> messagesList, OnMessageDeleteListener listener) {
        PopupMenu popup = new PopupMenu(anchorView.getContext(), anchorView);
        popup.getMenu().add(0, 1, 0, "删除");
        popup.getMenu().add(0, 2, 1, "复制");
        
        // 获取 messageId
        String messageId = message.getMessageId();
        
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                // 删除消息 - 传递 messageId 以便精确删除
                if (position >= 0 && position < messagesList.size()) {
                    MessageItem removed = messagesList.remove(position);
                    // 回调删除监听器，传递 messageId
                    if (listener != null) {
                        listener.onMessageDeleted(removed, position, messageId);
                    }
                }
                return true;
            } else if (item.getItemId() == 2) {
                // 复制文本
                String selectedText = textView.getText().toString();
                ClipboardManager clipboard = (ClipboardManager)anchorView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("selected text", selectedText);
                clipboard.setPrimaryClip(clip);
                return true;
            }
            return false;
        });
        popup.show();
    }

    public static class UserMessageViewHolder extends RecyclerView.ViewHolder 
    {
      @BindView(R.id.user_text) TextView textView;
      @BindView(R.id.user_image) ImageView imageView;
      private List<MessageItem> messagesRef;
      private MessageAdapter.OnMessageDeleteListener deleteListenerRef;

      public UserMessageViewHolder(View itemView, List<MessageItem> messages, MessageAdapter.OnMessageDeleteListener deleteListener) 
      {
        super(itemView);
        ButterKnife.bind(this, itemView);
        this.messagesRef = messages;
        this.deleteListenerRef = deleteListener;
        
        // 🗑️ 长按显示菜单
        itemView.setOnLongClickListener(v -> {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION && messagesRef != null)
                showLongPressMenuStatic(v, messagesRef.get(position), position, textView, messagesRef, deleteListenerRef);
            return true;
        });
        
        // 保留原有文本选择功能
        textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() 
        {
          @Override
          public boolean onCreateActionMode(ActionMode mode, Menu menu) 
          {
            return true;
          }

          @Override
          public boolean onPrepareActionMode(ActionMode mode, Menu menu) 
          {
            return false;
          }

          @Override
          public boolean onActionItemClicked(ActionMode mode, MenuItem item) 
          {
            if (item.getItemId() == android.R.id.copy) 
            {
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
          public void onDestroyActionMode(ActionMode mode) 
          {
            Spannable spannable = (Spannable)textView.getText();
            Selection.setSelection(spannable, 0, 0);
          }
        });
      }

      public void bind(MessageItem message) 
      {
        // 🖼️ 检测是否有图片
        if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) 
        {
          FileLogger.d(TAG, "🖼️ [IMAGE_FOUND] 检测到图片，开始解码");
          try 
          {
            // 处理 Base64 前缀 - 支持多种格式
            String base64Data = message.getImageUrl();
            
            // 检查并去除 data:image/...;base64, 前缀
            if (base64Data.startsWith("data:image")) 
            {
              int commaIndex = base64Data.indexOf(',');
              if (commaIndex > 0) 
              {
                String prefix = base64Data.substring(0, commaIndex);
                base64Data = base64Data.substring(commaIndex + 1);
                FileLogger.d(TAG, "✂️ [PREFIX_REMOVED] 已去除 Base64 前缀：" + prefix);
              }
            }
            
            // 清理可能存在的空白字符
            base64Data = base64Data.trim();
            
            // 验证 Base64 字符串是否有效
            if (base64Data.isEmpty()) 
            {
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
            
            if (decodedBitmap != null) 
            {
              FileLogger.d(TAG, "✅ [BITMAP_DECODED] 图片解码成功，尺寸：" + decodedBitmap.getWidth() + "x" + decodedBitmap.getHeight());
              // 显示图片
              imageView.setImageBitmap(decodedBitmap);
              imageView.setVisibility(View.VISIBLE);
            }
            else 
            {
              FileLogger.e(TAG, "❌ [BITMAP_NULL] BitmapFactory.decodeByteArray 返回 null");
              imageView.setImageBitmap(null);
              imageView.setVisibility(View.GONE);
            }
            
            // 文字部分只显示非图片内容（如果有）
            textView.setText(message.getText());
            FileLogger.d(TAG, "📝 [TEXT_SET] 文字已设置，长度：" + (message.getText() != null ? message.getText().length() : 0));
          }
          catch (IllegalArgumentException e) 
          {
            FileLogger.e(TAG, "❌ [DECODE_ERROR] Base64 格式错误", e);
            FileLogger.e(TAG, "   📋 [RAW_DATA] Base64 前 100 字符：" + (message.getImageUrl().length() > 100 ? message.getImageUrl().substring(0, 100) + "..." : message.getImageUrl()));
            imageView.setImageBitmap(null);
            imageView.setVisibility(View.GONE);
            textView.setText(message.getText());
          }
          catch (Exception e) 
          {
            FileLogger.e(TAG, "❌ [DECODE_ERROR] 图片解码失败", e);
            imageView.setImageBitmap(null);
            imageView.setVisibility(View.GONE);
            textView.setText(message.getText());
          }
        }
        else 
        {
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
        private List<MessageItem> messagesRef;
        private MessageAdapter.OnMessageDeleteListener deleteListenerRef;

        public AIMessageViewHolder(View itemView, List<MessageItem> messages, MessageAdapter.OnMessageDeleteListener deleteListener) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            this.messagesRef = messages;
            this.deleteListenerRef = deleteListener;
            
            markwon = Markwon.builder(itemView.getContext())
                .usePlugin(CorePlugin.create())
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(itemView.getContext()))
                .build();
            
            // 🗑️ 长按显示菜单
            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && messagesRef != null)
                    showLongPressMenuStatic(v, messagesRef.get(position), position, textView, messagesRef, deleteListenerRef);
                return true;
            });
            
            // 保留原有文本选择功能
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
            markwon.setMarkdown(textView, message.getText());
        }
    }

    public static class ToolCallResultViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.tool_call_result_text) TextView textView;
        private boolean isExpanded = false;
        private List<MessageItem> messagesRef;
        private MessageAdapter.OnMessageDeleteListener deleteListenerRef;

        public ToolCallResultViewHolder(View itemView, List<MessageItem> messages, MessageAdapter.OnMessageDeleteListener deleteListener) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            this.messagesRef = messages;
            this.deleteListenerRef = deleteListener;
            
            // 🗑️ 长按显示菜单
            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && messagesRef != null)
                    showLongPressMenuStatic(v, messagesRef.get(position), position, textView, messagesRef, deleteListenerRef);
                return true;
            });
            
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
            
            // 保留原有文本选择功能
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
