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