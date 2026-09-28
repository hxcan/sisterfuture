# 手机本地合并 PDF

先调用 renderPdf 逐页或分批生成，收集成功结果中的 pdfPath，再调用异步工具 mergePdf：

```json
{
  "filePaths": ["/storage/emulated/0/Download/page1.pdf", "/storage/emulated/0/Download/page2.pdf"],
  "outputFilename": "report.pdf"
}
```

按数组顺序合并每个文件的全部页面，返回 status、pdfPath、filesCount、pagesCount、
fileSizeKb、generationTimeSec。支持 file_paths / output_filename 别名，以及 JSON 字符串形式的路径数组。
输入必须为应用已能读取的本地绝对路径，不下载文件，不支持 content URI、加密 PDF 或密码参数。

使用 PDFBox-Android 2.0.27.0（Apache-2.0）直接合并页面，不栅格化。
https://github.com/TomRoush/PdfBox-Android
在专用共享后台单线程处理，临时流使用缓存目录；每次只打开一个源文档。
先写完整临时文件，再发布结果；不会覆盖同名文件，不修改或删除源 PDF。
输出默认在 Download，省略文件名时使用 UUID；不额外申请存储权限。
这是静态页面合并工具，不承诺保留数字签名有效性或所有交互表单行为。

验证：JVM 测试覆盖参数与别名、顺序、文件名限制及无效输入。
Android 测试覆盖页序、页面尺寸、可提取文字、源文件不变、损坏输入清理和拒绝覆盖。
需在设备上执行 Android 测试，并用 renderPdf 生成的中文/图片页面验收；单靠源码编译不能验证 PDF 内容。
