# Generic Web Request Tool - Specification v1.0

## 🎯 Overview
Universal HTTP request helper supporting GET/POST/PUT/DELETE/PATCH methods with customizable headers, authentication, and body content. Designed for temporary API validation and debugging scenarios.

## 🔧 Interface Definition

### Function Signature
```typescript
generic_web_request(
    method: "GET" | "POST" | "PUT" | "DELETE" | "PATCH", // Required
    url: string,                                         // Required
    headers?: Record<string, string>,                    // Optional
    body?: string,                                       // Optional (for POST/PUT)
    params?: Record<string, string>,                     // Optional (query params)
    auth_type?: "none" | "basic" | "bearer" | "api_key", // Optional, default: "none"
    auth_value?: string,                                 // Optional (credential based on auth_type)
    timeout_sec?: number                                 // Optional, default: 30
): Promise<{
    status_code: number;
    headers: string;
    body: string;
    duration_ms: number;
    success: boolean;
    url: string;
    method: string;
    timestamp: number;
}>
```

## 📋 Parameter Details

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `method` | Enum | ✅ Yes | - | HTTP method to use |
| `url` | String | ✅ Yes | - | Target URL (no restrictions) |
| `headers` | Object | ❌ No | {} | Custom HTTP headers |
| `body` | String | ❌ No | null | Request body (JSON/text/form) |
| `params` | Object | ❌ No | {} | Query parameters |
| `auth_type` | Enum | ❌ No | "none" | Authentication type |
| `auth_value` | String | ❌ No | "" | Credential based on auth_type |
| `timeout_sec` | Integer | ❌ No | 30 | Request timeout in seconds |

## 🔒 Safety Constraints

### ✅ Allowed
- Access any URL (including localhost/127.0.0.1/internal IPs)
- Custom headers and authentication
- Configurable timeouts
- Structured JSON responses

### ❌ Forbidden
- JavaScript execution (HTTP-only)
- Persistent credential storage
- Binary file uploads/downloads
- Redirect chains > 5 times
- Internal network scanning without explicit approval

## 💡 Usage Scenarios

### Scenario 1: Verify External API
```json
{
  "method": "GET",
  "url": "https://api.github.com/users/hxcan",
  "headers": {},
  "params": {}
}
// → Returns GitHub user info JSON
```

### Scenario 2: Test Redmine Endpoint
```json
{
  "method": "GET",
  "url": "https://glzquuktdzuk.gzg.sealos.run/projects.json",
  "auth_type": "basic",
  "auth_value": "sisterfuture:Sf.Slut.123"
}
// → Debug Redmine bug #4615 directly
```

### Scenario 3: Custom Webhook Callback
```json
{
  "method": "POST",
  "url": "https://discord.com/api/webhooks/xxx/yyy",
  "headers": { "Content-Type": "application/json" },
  "body": "{\"content\": \"未来姐姐上线了!\"}"
}
// → Send message to Discord channel
```

### Scenario 4: Simulate OAuth Flow
```json
{
  "method": "POST",
  "url": "https://oauth.example.com/token",
  "body": "grant_type=authorization_code&code=xyz&client_id=abc&client_secret=secret"
}
// → Get access token for subsequent requests
```

### Scenario 5: Dynamic Token Generation
```json
{
  "method": "POST",
  "url": "https://github.com/login/oauth/access_token",
  "headers": { "Accept": "application/json" },
  "body": "{\"client_id\":\"xxx\",\"client_secret\":\"yyy\",\"code\":\"zzz\"}"
}
// → Get GitHub Token for future use
```

## ⚠️ Error Handling

### Response Structure on Failure
```json
{
  "status": "error",
  "message": "HTTP请求失败：401 Unauthorized",
  "raw_body": "<HTML>...</HTML>",
  "status_code": 401
}
```

### Common Errors
| Status Code | Error Message | Solution |
|-------------|---------------|----------|
| 401 | Authentication failed | Check auth_value format |
| 404 | Resource not found | Verify URL path |
| 403 | Permission denied | Review scope permissions |
| 500 | Server error | Retry or contact service provider |
| Timeout | Request timed out | Increase timeout_sec parameter |

## 📊 Success Criteria

| Level | Metric | Description |
|-------|--------|-------------|
| 🥉 **及格** | Complete core functionality | Can initiate any external HTTP request |
| 🥈 **良好** | ≥3 uses per week | Called autonomously by AI for new validations |
| 🥇 **优秀** | Derive specialized tools | Abstract into `brave_search_api`, `github_oauth_api` etc. |

## 🔄 Evolution Path

### v1.0 (Current - Temporary Tool)
- [x] Basic HTTP methods support (GET/POST/PUT/DELETE)
- [x] Simple authentication (Basic/Bearer/API Key)
- [x] JSON/Text response parsing
- [x] Timeout and error handling

### v2.0 (If high frequency usage)
- [ ] Async request support
- [ ] Cookie session management
- [ ] Request retry mechanism
- [ ] SSL certificate fingerprint verification

### v3.0 (If becomes critical need)
- [ ] Abstract into dedicated tools
- [ ] Integrate into tool registry
- [ ] Add preset templates (GitHub API/OAuth/API Key management)
- [ ] Support batch concurrent requests

## 🔗 Related Tasks
- **#4616**: Parent task for this tool development
- **#4615**: Redmine Bug Report (use case for debugging)
- **#4611**: list_redmine_projects tool (pre-validation step)

---

**Version**: 1.0  
**Created**: 2026-03-09  
**Developer**: Future Sister (AI Agent) 🚀🇺🇸  
**License**: MIT