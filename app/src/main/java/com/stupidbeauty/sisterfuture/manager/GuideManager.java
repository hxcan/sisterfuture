            \/\/ 异步执行：先创建百炼接入点
            toolManager.executeToolAsync(null, "add_model_access_point", args1, new Tool.OnResultCallback() {
                @Override
                public void onResult(JSONObject result1) {
                    \/\/ 百炼接入点创建成功，继续创建 Code Plan 接入点
                    toolManager.executeToolAsync(null, "add_model_access_point", args2, new Tool.OnResultCallback() {
                        @Override
                        public void onResult(JSONObject result2) {
                            \/\/ ✅ 两个接入点都创建成功
                            if (isBackupMode) {
                                \/\/ 备用模式
                                callback.onResponse(
                                    "✅ **备用接入点配置成功！**\n\n" +
                                    "🔹 已添加两个新接入点：\n" +
                                    "  1. Qwen-百炼标准 -397B" + nameSuffix + "\n" +
                                    "  2. Qwen-CodePlan" + nameSuffix + "\n\n" +
                                    "📊 当前共有 " + (existingCount + 2) + " 个接入点\n" +
                                    "🚀 系统会自动在新旧接入点间切换，优先使用可用的接入点\n\n" +
                                    "💡 原有接入点已保留，恢复后可继续使用！"
                                );
                            } else {
                                \/\/ 普通模式（首次配置）
                                callback.onResponse(
                                    "✅ 接入点配置成功！\n\n" +
                                    "🔹 已创建两个接入点：\n" +
                                    "  1. Qwen-百炼标准 -397B\n" +
                                    "  2. Qwen-CodePlan\n\n" +
                                    "🚀 系统会自动使用有效的接入点，现在可以享受完整功能了！"
                                );
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            \/\/ Code Plan 创建失败，但百炼已成功
                            if (isBackupMode) {
                                callback.onResponse(
                                    "⚠️ 部分配置成功：\n" +
                                    "✅ Qwen-百炼标准 -397B" + nameSuffix + " 已创建\n" +
                                    "❌ Qwen-CodePlan" + nameSuffix + " 配置失败：" + e.getMessage() + "\n\n" +
                                    "仍可正常使用新创建的百炼接入点。"
                                );
                            } else {
                                callback.onResponse(
                                    "⚠️ 部分配置成功：\n" +
                                    "✅ Qwen-百炼标准 -397B 已创建\n" +
                                    "❌ Qwen-CodePlan 配置失败：" + e.getMessage() + "\n\n" +
                                    "仍可正常使用百炼接入点。"
                                );
                            }
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    \/\/ 百炼接入点创建失败
                    callback.onError("❌ " + (isBackupMode ? "备用" : "百炼") + "接入点配置失败：" + e.getMessage());
                }
            });