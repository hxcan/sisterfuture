  @Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.sister_future);

    initServices();
    initData();
    initTools();
    initView();
    checkPermission();
    connectSignals();
    displayExistingContext();
    
    // #4713 冷启动时自动滚动到聊天记录最底部
    if (savedInstanceState == null)
    {
      articleListmyRecyclerView.post(() -> 
      {
        scrollToBottom();
        Log.d(TAG, "#4713 冷启动完成，已自动滚动到最新消息");
      });
    }
	}