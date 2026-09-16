# 批准 GitHub Pull Request

工具名：`approvePullRequest`。沿用 GitHub 工具的异步执行方式，通过
`POST /repos/{owner}/{repo}/pulls/{pull_number}/reviews` 提交 `event: APPROVE`。
它只批准，不创建 PR、不合并，也不判断 CI 是否通过。

必填参数：`owner`、`repo`、正整数 `pull_number`。
可选参数：`body`（审核说明）、`commit_id`（实际审核的提交 SHA）、`token`。
省略 `commit_id` 时 GitHub 使用 PR 最新提交；建议提供已审核的 SHA。

认证与已有工具一致：显式 `token` 优先，否则读取**本工具自己的备注**：

```json
{"github_token":"YOUR_GITHUB_TOKEN"}
```

不会自动读取其他工具备注。细粒度 token 需要目标仓库的 Pull requests 写权限，
审批身份是 token 所属账号，GitHub 不允许批准自己创建的 PR。

调用示例（会产生真实审批，仅在授权且完成审核后执行）：

```json
{"owner":"example-owner","repo":"example-repo","pull_number":123,"body":"已审核"}
```

成功返回 `success: true`、`status_code`、`pr_number`、`review_id`、
`state: APPROVED`、`review_url`、`commit_id`。只有 HTTP 200 且状态确认为
APPROVED 才报告成功。API 拒绝返回 `success: false`、状态码和错误信息；
参数或网络异常交给工具错误回调。网络失败可能发生在服务器已处理审批之后，
应先核对审核记录再决定是否重试。

不保存本工具的参数历史，不主动记录 token 或审核正文；不跟随重定向，
不自动重试连接失败。此约束不表示应用其他通用会话日志必然不包含工具参数。

测试使用 MockWebServer，不在真实 GitHub 仓库提交审批。
参考：https://docs.github.com/en/rest/pulls/reviews#create-a-review-for-a-pull-request
