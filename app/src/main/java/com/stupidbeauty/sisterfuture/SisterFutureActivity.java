  private void scrollToBottom()
  {
    if (messageAdapter.getItemCount() > 0)
    {
      articleListmyRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
    }
  }