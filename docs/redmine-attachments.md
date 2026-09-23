# 手机文件上传为 Redmine 任务附件

工具名称：`uploadRedmineAttachment`，异步执行，每次上传一个文件。

```json
{
  "taskId": 123,
  "filePath": "/storage/emulated/0/Download/report.pdf",
  "redmineUrl": "https://redmine.example.com",
  "description": "测试报告"
}
```

认证配置在该工具自己的备注中：`redmineUrl` 和 `apiKey`，或
`username` / `password`。也可以通过参数传入。不会自动读取其他工具的备注。
可选 `fileName` 指定显示名称、`contentType` 指定 MIME 类型。
小驼峰参数兼容对应的下划线别名，小驼峰优先，原始参数对象不被修改。

文件须是应用有权读取的绝对路径；不支持 `content://`、目录或自动申请权限。
流式上传原始字节，不进行 Base64 编码，不删除本地文件。
附件大小限制由 Redmine 服务端决定。

流程遵循 https://www.redmine.org/projects/redmine/wiki/Rest_api#Attaching-files ：

1. `POST /uploads.json?filename=...`，Content-Type 为 `application/octet-stream`。
2. 使用返回的令牌 `PUT /issues/{taskId}.json`，仅设置 `issue.uploads`，保留原附件及其他字段。

仅第二步返回成功才报告 `status=success`。失败返回 `status=error`、
`stage`（upload/attach）和 `uploaded`（是否收到上传成功响应）。
`uploaded=false` 不保证服务端没有收到文件；超时等情况可能结果不确定。
两步不是原子操作，关联失败可能留下未关联的上传文件。
不自动重试或跟随重定向；先查看目标任务附件再决定是否重传，避免重复。
上传令牌、认证信息和服务端响应正文不写入结果或日志。

测试使用 MockWebServer，不会向真实 Redmine 上传文件。
