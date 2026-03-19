public ToolCallResultViewHolder(View itemView) {
        super(itemView);
        ButterKnife.bind(this, itemView);
        
        // #4794: Add click listener to toggle expand/collapse
        itemView.setOnClickListener(v => {
            MessageItem item = (MessageItem) textView.getTag();
            if (item != null) {
                item.isExpanded = !item.isExpanded;
                bind(item); // Re-bind with new state
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
        textView.setText(message.getText());
        textView.setTag(message); // #4794: Save reference for click
        
        // #4794: Apply line limit based on expanded state
        if (message.isExpanded) {
            textView.setMaxLines(Integer.MAX_VALUE);
            textView.setEllipsize(null);
        } else {
            textView.setMaxLines(MAX_LINES);
            textView.setEllipsize(TextUtils.TruncateAt.END);
        }
    }
}