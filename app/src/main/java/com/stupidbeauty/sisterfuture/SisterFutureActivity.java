  private final RecognizerListener mRecognizerListener=new RecognizerListener()
	{
		@Override
		public void onVolumeChanged(int i, byte[] bytes)
    {
      volumeIndicatorprogressBar.setProgress(i);
		}

		@Override
		public void onBeginOfSpeech()
    {
      voiceRecognizeResultString="";
      volumeIndicatorprogressBar.setVisibility(View.VISIBLE);
		}

		@Override
		public void onEndOfSpeech()
    {
      volumeIndicatorprogressBar.setVisibility(View.INVISIBLE);
      voiceEndDetected=true;
		}

		@Override
		public void onResult(RecognizerResult recognizerResult, boolean b)
    {
      progressBar.setVisibility(View.INVISIBLE);
      commandRecognizebutton2.setVisibility(View.VISIBLE);
      commandRecognizebutton2.setEnabled(true);
      String text=recognizerResult.getResultString();

      Gson gson=new Gson();
      VoiceRecognizeResult voiceRecognizeResult=gson.fromJson(text, VoiceRecognizeResult.class);
      String saidText=voiceRecognizeResult.getSaidText();

      recognizeResulttextView.append(saidText);
      voiceRecognizeResultString=voiceRecognizeResultString+saidText;

      boolean isLast=voiceRecognizeResult.isLs();

      if (isLast) 
      {
        sendMessageToSister(voiceRecognizeResultString);
        // ✅ #4835 修复：语音输入发送后清空输入框
        recognizeResulttextView.setText("");
      }
    }

    @Override
		public void onError(SpeechError speechError)
		{
      commandRecognizebutton2.setVisibility(View.VISIBLE);
      commandRecognizebutton2.setEnabled(true);
      progressBar.setVisibility(View.INVISIBLE);
      String errorText=speechError.getErrorDescription();

      recognizeResulttextView.setText(errorText+",error code:"+speechError.getErrorCode());
		}

		@Override
		public void onEvent(int i, int i1, int i2, Bundle bundle)
		{
    }
	};