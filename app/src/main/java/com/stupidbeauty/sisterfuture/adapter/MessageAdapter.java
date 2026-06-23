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
    private static final int MAX_TOOL_RESULT_DISPLAY_LENGTH = 10000;
    private static final String TAG = "MessageAdapter";

    private List<MessageItem> messages = new ArrayList<>();
    
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

    public MessageItem addMessage(MessageItem message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
        return message;
    }

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

    // 🆕 #821166321034 修复 RecyclerView IndexOutOfBoundsException 崩溃
    // 原因：notifyItemRemoved + notifyItemRangeChanged 在异步预取时导致 GapWorker 访问过时 position
    // 最小化修改：改用 notifyDataSetChanged
    public boolean removeMessageById(String messageId) {
        int position = getMessagePositionById(messageId);
        if (position >= 0) {
            FileLogger.i(TAG, "🗑️ [REMOVE_BY_ID] 删除消息 | position=" + position + " | messageId=" + messageId);
            MessageItem removed = messages.remove(position);
            notifyDataSetChanged();
            if (deleteListener != null) {
                deleteListener.onMessageDeleted(removed, position, messageId);
            }
            return true;
        }
        return false;
    }
    
    // 🆕 #821166321034 修复 RecyclerView IndexOutOfBoundsException 崩溃
    public boolean removeMessage(int position) {
        if (position >= 0 && position < messages.size()) {
            FileLogger.i(TAG, "🗑️ [REMOVE_BY_POS] 删除消息 | position=" + position);
            MessageItem removed = messages.remove(position);
            notifyDataSetChanged();
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
    
    private static void showLongPressMenuStatic(View anchorView, MessageItem message, int position, TextView textView, List<MessageItem> messagesList, OnMessageDeleteListener listener) {
        FileLogger.i(TAG, "🗑️ [LONG_PRESS_MENU] 长按菜单触发 | position=" + position + " | messageId=" + message.getMessageId());
        
        PopupMenu popup = new PopupMenu(anchorView.getContext(), anchorView);
        popup.getMenu().add(0, 1, 0, "删除");
        popup.getMenu().add(0, 2, 1, "复制");
        
        String messageId = message.getMessageId();
        
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                FileLogger.i(TAG, "🗑️ [DELETE_CLICK] 用户点击删除 | position=" + position + " | messageId=" + messageId);
                if (position >= 0 && position < messagesList.size()) {
                    MessageItem removed = messagesList.remove(position);
                    if (listener != null) {
                        listener.onMessageDeleted(removed, position, messageId);
                    }
                }
                return true;
            } else if (item.getItemId() == 2) {
                String selectedText = textView.getText().toString();
                ClipboardManager clipboard = (ClipboardManager)anchorView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("selected text", selectedText);
                clipboard.setPrimaryClip(clip);
                FileLogger.i(TAG, "📋 [COPY_CLICK] 已复制到剪贴板 | 长度=" + selectedText.length());
                return true;
            }
            return false;
        });
        
        popup.show();
        FileLogger.i(TAG, "🗑️ [POPUP_SHOWN] PopupMenu 已显示");
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
        
        itemView.setOnLongClickListener(v -> {
            int position = getBindingAdapterPosition();
            FileLogger.i(TAG, "👆 [USER_LONG_CLICK] User 消息长按 | position=" + position);
            if (position != RecyclerView.NO_POSITION && messagesRef != null) {
                MessageItem msg = messagesRef.get(position);
                showLongPressMenuStatic(v, msg, position, textView, messagesRef, deleteListenerRef);
            }
            return true;
        });
        
        textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() 
        {
          @Override
          public boolean onCreateActionMode(ActionMode mode, Menu menu) { return true; }
          @Override
          public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
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

      public void bind(MessageItem message) 
      {
        if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) 
        {
          try 
          {
            String base64Data = message.getImageUrl();
            if (base64Data.startsWith("data:image")) {
              int commaIndex = base64Data.indexOf(',');
              if (commaIndex > 0) {
                base64Data = base64Data.substring(commaIndex + 1);
              }
            }
            base64Data = base64Data.trim();
            if (base64Data.isEmpty()) {
              imageView.setImageBitmap(null);
              imageView.setVisibility(View.GONE);
              return;
            }
            byte[] decodedString = Base64.decode(base64Data, Base64.NO_WRAP);
            Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            if (decodedBitmap != null) {
              imageView.setImageBitmap(decodedBitmap);
              imageView.setVisibility(View.VISIBLE);
            } else {
              imageView.setImageBitmap(null);
              imageView.setVisibility(View.GONE);
            }
            textView.setText(message.getText());
          }
          catch (Exception e) {
            imageView.setImageBitmap(null);
            imageView.setVisibility(View.GONE);
            textView.setText(message.getText());
          }
        }
        else 
        {
          imageView.setImageBitmap(null);
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
            
            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                FileLogger.i(TAG, "👆 [AI_LONG_CLICK] AI 消息长按 | position=" + position);
                if (position != RecyclerView.NO_POSITION && messagesRef != null) {
                    MessageItem msg = messagesRef.get(position);
                    showLongPressMenuStatic(v, msg, position, textView, messagesRef, deleteListenerRef);
                }
                return true;
            });
            
            textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) { return true; }
                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
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
            
            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                FileLogger.i(TAG, "👆 [TOOL_LONG_CLICK] 工具结果消息长按 | position=" + position);
                if (position != RecyclerView.NO_POSITION && messagesRef != null) {
                    MessageItem msg = messagesRef.get(position);
                    showLongPressMenuStatic(v, msg, position, textView, messagesRef, deleteListenerRef);
                }
                return true;
            });
            
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
                public boolean onCreateActionMode(ActionMode mode, Menu menu) { return true; }
                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
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
            String text = limitToolResultDisplayLength(message.getText());
            textView.setText(text);
            isExpanded = false;
        }
    }
}