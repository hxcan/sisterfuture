        public void bind(MessageItem message) {
            Log.d(TAG, "🔍 [BIND] 绑定消息 | position=" + getAdapterPosition() + " | hasImage=" + (message.getImageUrl() != null));
            
            // 🖼️ 检测是否有图片
            if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
                Log.d(TAG, "🖼️ [IMAGE_FOUND] 检测到图片，开始解码");
                try {
                    // 解码 Base64 图片
                    byte[] decodedString = Base64.decode(message.getImageUrl(), Base64.DEFAULT);
                    Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    
                    if (decodedBitmap != null) {
                        Log.d(TAG, "✅ [BITMAP_DECODED] 图片解码成功，尺寸：" + decodedBitmap.getWidth() + "x" + decodedBitmap.getHeight());
                        // 显示图片
                        imageView.setImageBitmap(decodedBitmap);
                        imageView.setVisibility(View.VISIBLE);
                    } else {
                        Log.e(TAG, "❌ [BITMAP_NULL] BitmapFactory 返回 null");
                        imageView.setImageBitmap(null);
                        imageView.setVisibility(View.GONE);
                    }
                    
                    // 文字部分只显示非图片内容（如果有）
                    textView.setText(message.getText());
                    Log.d(TAG, "📝 [TEXT_SET] 文字已设置，长度：" + (message.getText() != null ? message.getText().length() : 0));
                } catch (Exception e) {
                    Log.e(TAG, "❌ [DECODE_ERROR] 图片解码失败", e);
                    imageView.setImageBitmap(null);
                    imageView.setVisibility(View.GONE);
                    textView.setText(message.getText());
                }
            } else {
                Log.d(TAG, "🚫 [NO_IMAGE] 没有图片，隐藏 ImageView");
                // 没有图片，隐藏 ImageView，只显示文字
                imageView.setImageBitmap(null); // 清除旧图片，防止复用
                imageView.setVisibility(View.GONE);
                textView.setText(message.getText());
            }
        }