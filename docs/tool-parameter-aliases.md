# 工具参数小驼峰与旧参数别名

`createRedmineTask` 的参数定义、必填字段和默认增强提示词使用小驼峰。
已有单词参数 `username`、`password`、`subject`、`description`、`priority` 不变。

| 对外参数 | 兼容的旧名称 |
|---|---|
| redmineUrl | redmine_url |
| apiKey | api_key |
| projectId | project_id |
| parentIssueId | parent_issue_id |
| trackerId | tracker_id |
| assignedToId | assigned_to_id |

例如，认证信息已保存在本工具备注时，可用：

```json
{"projectId":750160066086,"subject":"子任务","parentIssueId":889546934309}
```

旧下划线参数仍然有效，也允许混用。新旧键同时存在时，新名称优先，
即使其值为空字符串或 JSON null，也不会重新使用旧键的值。
正常的必填校验、默认值和备注回退逻辑仍由各工具负责。
不会修改调用方传入的参数对象、已保存的工具备注或用户自定义增强提示词。
备注认证继续由 RedmineAuth 处理，原有备注格式不变。

发送给 Redmine REST API 的请求字段仍为 `project_id`、`parent_issue_id` 等；
工具对模型展示的命名与远端 API 协议是两层接口，不能一起重命名。

## 其他工具复用

在工具执行入口、认证和业务解析之前调用：

```java
JSONObject arguments = ToolParameterAliases.normalize(suppliedArguments,
        "projectId", "parentIssueId");
```

然后在参数定义、`required`、业务读取和默认提示词中使用相同的小驼峰名称。
同步工具在 `execute` 中调用；异步工具在执行任务内部调用，使异常进入原有错误回调。
直接调用工具和通过 ToolManager 调用均可兼容。

转换仅针对显式声明的顶层参数，自动接受相应 snake_case 别名并删除副本中的旧键。
不改变未知参数、嵌套 JSON、远端 API 字段或其他工具的现有行为。
已接入 createRedmineTask 和 createGithubCommit，未全局批量改变工具接口。
`parent_task_id` 不是历史参数，也不是 parentIssueId 的风格别名，故不额外接受。

验证包括定义一致性、新旧请求等价、长整型、混用与冲突优先级、空值、输入不变性、
嵌套 JSON 不改写，以及两种备注命名和 Basic/API Key 认证兼容。
所有请求测试使用 MockWebServer，不创建真实任务。

## 在线代码提交工具

`createGithubCommit`（工具名保持不变）也复用同一个别名类：

| 对外参数 | 兼容旧名称 |
|---|---|
| commitMessage | commit_message |
| readFromPhone | read_from_phone |
| phonePath | phone_path |

执行入口在文本、手机文件、二进制和删除处理之前统一归一化。其他参数不变，
文件内容不做字符串替换；token 和工具备注 `github_token` 的认证方式不变。
原有返回字段（包括 `read_from_phone`）保留，不改变结果协议。
该工具的测试覆盖 schema、实际入口使用的归一化方法、新旧混用与优先级、
false/空字符串及内容和删除参数透传；未在 GitHub 提交真实代码，也未实机测试手机文件读取。
