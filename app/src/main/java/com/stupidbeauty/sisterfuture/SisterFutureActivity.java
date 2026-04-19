  private void scrollToBottom()
  {
    if (messageAdapter.getItemCount() > 0)
    {
      // ✅ 修复 #753566214831：使用 post() + scrollToPosition() 消除震荡
      // ❌ 原 smoothScrollToPosition() 会与布局重算冲突，导致界面抖动
      articleListmyRecyclerView.post(() -> {
        articleListmyRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
      });
    }
  }