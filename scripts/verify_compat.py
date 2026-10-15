#!/usr/bin/env python3
"""
校验 RequestParamCompatibilityManager 的判定逻辑是否真的能修好 #919750362575
完全镜像 Java 实现的判定规则（不依赖 Android 运行环境）。
"""
import json
import re
import sys

PARAM_ENABLE_THINKING = "enable_thinking"
PARAM_THINKING = "thinking"
PARAM_REASONING_SPLIT = "reasoning_split"

# 全局缓存：endpoint -> 不支持
unsupported_endpoints = set()

def safe(s):
    return "" if s is None else s

def should_inject(access_point, endpoint_key):
    if access_point is None:
        return False
    if endpoint_key in unsupported_endpoints:
        return False
    base_url = safe(access_point.get("baseUrl")).lower()
    model_name = safe(access_point.get("modelName")).lower()
    name = safe(access_point.get("name")).lower()
    if "minimaxi.com" in base_url or "minimax.com" in base_url:
        return True
    if "minimax" in model_name or "minimax" in name:
        return True
    return False

def is_unsupported_response(status_code, body):
    if status_code < 400 or status_code >= 500 or not body:
        return False
    normalized = body.lower()
    names_field = (
        PARAM_ENABLE_THINKING in normalized
        or PARAM_THINKING in normalized
        or PARAM_REASONING_SPLIT in normalized
        or "reasoning_split" in normalized
        or "reasoning split" in normalized
    )
    if not names_field:
        return False
    keywords = [
        "unknown", "unsupported", "not supported", "does not support",
        "unrecognized", "not recognized", "unexpected",
        "not allowed", "not permitted", "invalid",
        "未知", "不支持", "无效",
    ]
    return any(k in normalized for k in keywords)

def mark_unsupported(endpoint_key):
    if endpoint_key:
        unsupported_endpoints.add(endpoint_key)

# ============= 真实场景用例 =============

cases = [
    {
        "name": "A. 切到 ppnew-ai（修复点 1：应直接跳过注入）",
        "access_point": {
            "name": "ppnew-ai",
            "baseUrl": "https://new-api-vxvfafio.cloud.sealos.io/v1",
            "chatEndpoint": "/chat/completions",
            "modelName": "gpt-5.6-sol",
        },
        "endpoint_key": "https://new-api-vxvfafio.cloud.sealos.io/v1/chat/completions",
        "expect_inject": False,
        "expect_unsupported_match": None,  # 不会触发，因为根本不注入
    },
    {
        "name": "B. MiniMax-M3-备用（应正常注入）",
        "access_point": {
            "name": "MiniMax-M3-备用",
            "baseUrl": "https://api.minimaxi.com/v1",
            "chatEndpoint": "/chat/completions",
            "modelName": "MiniMax-M3",
        },
        "endpoint_key": "https://api.minimaxi.com/v1/chat/completions",
        "expect_inject": True,
    },
    {
        "name": "C. 假如某代理 baseUrl 误中 minimax.com → 注入 → 收到真实 4xx → 应识别回退",
        "access_point": {
            "name": "test-proxy",
            "baseUrl": "https://api.minimaxi.com/v1",  # 假装是 minimax
            "chatEndpoint": "/chat/completions",
            "modelName": "weird-model",
        },
        "endpoint_key": "https://api.minimaxi.com/v1/chat/completions",
        "expect_inject": True,
        "fake_response": {
            "status": 400,
            "body": '{"error":{"message":"Unknown parameter: \'enable_thinking\'.","type":"invalid_request_error","param":"enable_thinking","code":"unknown_parameter"}}',
        },
        "expect_unsupported_match": True,
    },
    {
        "name": "D. 401 invalid_api_key（不应被识别为参数不兼容 → 走原 401 切点逻辑）",
        "access_point": {
            "name": "expired-key",
            "baseUrl": "https://api.minimaxi.com/v1",
            "chatEndpoint": "/chat/completions",
            "modelName": "MiniMax-M2.5",
        },
        "endpoint_key": "https://api.minimaxi.com/v1/chat/completions",
        "fake_response": {
            "status": 401,
            "body": '{"error":{"code":"invalid_api_key","message":"invalid access token or token expired","param":null,"type":"invalid_request_error"}}',
        },
        "expect_unsupported_match": False,
    },
    {
        "name": "E. 400 上下文超长（不应被识别为参数不兼容 → 走 ResponseException）",
        "access_point": {
            "name": "ctx-too-long",
            "baseUrl": "https://api.minimaxi.com/v1",
            "chatEndpoint": "/chat/completions",
            "modelName": "MiniMax-M2.5",
        },
        "endpoint_key": "https://api.minimaxi.com/v1/chat/completions",
        "fake_response": {
            "status": 400,
            "body": '{"error":{"message":"context length exceeded: max 32000 tokens","type":"invalid_request_error"}}',
        },
        "expect_unsupported_match": False,
    },
    {
        "name": "F. 429 限流（不应被识别为参数不兼容）",
        "access_point": {
            "name": "rate-limited",
            "baseUrl": "https://api.minimaxi.com/v1",
            "chatEndpoint": "/chat/completions",
            "modelName": "MiniMax-M2.5",
        },
        "endpoint_key": "https://api.minimaxi.com/v1/chat/completions",
        "fake_response": {
            "status": 429,
            "body": '{"error":{"message":"rate limit exceeded","type":"rate_limit_error"}}',
        },
        "expect_unsupported_match": False,
    },
    {
        "name": "G. 标记为已知不支持后，should_inject 立刻返回 false",
        "access_point": {
            "name": "ppnew-ai",
            "baseUrl": "https://new-api-vxvfafio.cloud.sealos.io/v1",
            "chatEndpoint": "/chat/completions",
            "modelName": "gpt-5.6-sol",
        },
        "endpoint_key": "https://new-api-vxvfafio.cloud.sealos.io/v1/chat/completions",
        "expect_inject": False,
        "pre_mark_unsupported": True,
    },
]

passed = 0
failed = 0
for i, c in enumerate(cases, 1):
    print(f"\n--- 用例 {i}: {c['name']} ---")
    if c.get("pre_mark_unsupported"):
        mark_unsupported(c["endpoint_key"])
    inject = should_inject(c["access_point"], c["endpoint_key"])
    ok_inject = (inject == c["expect_inject"])
    print(f"  should_inject = {inject}  (期望 {c['expect_inject']})  {'✅' if ok_inject else '❌'}")

    if "fake_response" in c:
        match = is_unsupported_response(c["fake_response"]["status"], c["fake_response"]["body"])
        ok_match = (match == c["expect_unsupported_match"])
        print(f"  is_unsupported_response = {match}  (期望 {c['expect_unsupported_match']})  {'✅' if ok_match else '❌'}")
        # 模拟一次命中回退 → 标记缓存
        if match:
            mark_unsupported(c["endpoint_key"])
            print(f"  ↳ 已 mark_unsupported({c['endpoint_key']})")
    else:
        ok_match = True

    if ok_inject and ok_match:
        passed += 1
    else:
        failed += 1

print(f"\n=========================================")
print(f"通过 {passed} / 失败 {failed}  （共 {len(cases)}）")
sys.exit(0 if failed == 0 else 1)
