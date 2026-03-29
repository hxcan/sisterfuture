/**
 * 按行文件编辑工具
 * 
 * 功能：
 * - 支持直接传入文件内容进行编辑
 * - 支持传入文件路径读取后编辑
 * - 支持输出为文件内容或写入到文件路径
 * - 支持多种行编辑操作：插入、删除、修改、替换
 * 
 * @module tools/edit_file_by_line
 */

export interface EditFileByLineInput {
  /** 文件内容（直接传入文本）或文件路径（以 / 或字母盘符开头） */
  source: string;
  
  /** 输入类型：'content' (直接内容) | 'filepath' (文件路径) */
  inputType: 'content' | 'filepath';
  
  /** 输出类型：'content' (返回内容) | 'filepath' (写入文件) */
  outputType: 'content' | 'filepath';
  
  /** 输出文件路径（当 outputType='filepath' 时必需） */
  outputPath?: string;
  
  /** 编码方式（仅当 inputType='filepath' 或 outputType='filepath' 时使用） */
  encoding?: 'utf-8' | 'base64';
  
  /** 要执行的编辑操作列表 */
  operations: LineEditOperation[];
}

export type LineEditOperation = 
  | InsertLinesOp
  | DeleteLinesOp
  | UpdateLinesOp
  | ReplaceLinesOp;

/** 插入行操作 */
export interface InsertLinesOp {
  type: 'insert';
  /** 插入位置（行号，从 0 开始）。在该行之前插入 */
  lineNumber: number;
  /** 要插入的行内容数组 */
  lines: string[];
}

/** 删除行操作 */
export interface DeleteLinesOp {
  type: 'delete';
  /** 起始行号（包含） */
  startLine: number;
  /** 结束行号（不包含），若不传则删除到文件末尾 */
  endLine?: number;
}

/** 修改单行操作 */
export interface UpdateLinesOp {
  type: 'update';
  /** 行号（从 0 开始） */
  lineNumber: number;
  /** 新的行内容 */
  newLine: string;
}

/** 批量替换行操作 */
export interface ReplaceLinesOp {
  type: 'replace';
  /** 起始行号（包含） */
  startLine: number;
  /** 结束行号（不包含） */
  endLine: number;
  /** 替换后的行内容数组 */
  newLines: string[];
}

export interface EditFileByLineOutput {
  /** 操作是否成功 */
  success: boolean;
  
  /** 错误信息（如果失败） */
  error?: string;
  
  /** 编辑后的文件内容（当 outputType='content' 时） */
  content?: string;
  
  /** 写入的文件路径（当 outputType='filepath' 时） */
  outputPath?: string;
  
  /** 原始行数 */
  originalLineCount?: number;
  
  /** 编辑后的行数 */
  finalLineCount?: number;
  
  /** 执行的操作日志 */
  operationLog?: string[];
}

/**
 * 按行编辑文件内容
 */
export async function editFileByLine(input: EditFileByLineInput): Promise<EditFileByLineOutput> {
  const operationLog: string[] = [];
  
  try {
    // 1. 获取原始内容
    let content: string;
    if (input.inputType === 'filepath') {
      content = await readFileContent(input.source, input.encoding || 'utf-8');
      operationLog.push(`✓ 读取文件：${input.source}`);
    } else {
      content = input.source;
      operationLog.push('✓ 使用传入的内容');
    }
    
    const originalLines = splitIntoLines(content);
    const originalLineCount = originalLines.length;
    operationLog.push(`✓ 原始行数：${originalLineCount}`);
    
    // 2. 执行编辑操作
    let lines = [...originalLines];
    for (const op of input.operations) {
      switch (op.type) {
        case 'insert':
          lines = applyInsert(lines, op);
          operationLog.push(`✓ 插入 ${op.lines.length} 行到位置 ${op.lineNumber}`);
          break;
        case 'delete':
          lines = applyDelete(lines, op);
          operationLog.push(`✓ 删除行 ${op.startLine}-${op.endLine ?? 'EOF'}`);
          break;
        case 'update':
          lines = applyUpdate(lines, op);
          operationLog.push(`✓ 更新行 ${op.lineNumber}`);
          break;
        case 'replace':
          lines = applyReplace(lines, op);
          operationLog.push(`✓ 替换行 ${op.startLine}-${op.endLine} 为 ${op.newLines.length} 行`);
          break;
      }
    }
    
    const finalContent = lines.join('\n');
    const finalLineCount = lines.length;
    
    // 3. 输出结果
    if (input.outputType === 'filepath') {
      if (!input.outputPath) {
        throw new Error('outputPath is required when outputType is "filepath"');
      }
      await writeFileContent(input.outputPath, finalContent, input.encoding || 'utf-8');
      operationLog.push(`✓ 写入文件：${input.outputPath}`);
      
      return {
        success: true,
        outputPath: input.outputPath,
        originalLineCount,
        finalLineCount,
        operationLog
      };
    } else {
      operationLog.push('✓ 返回编辑后的内容');
      
      return {
        success: true,
        content: finalContent,
        originalLineCount,
        finalLineCount,
        operationLog
      };
    }
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    operationLog.push(`✗ 错误：${errorMessage}`);
    
    return {
      success: false,
      error: errorMessage,
      operationLog
    };
  }
}

function splitIntoLines(content: string): string[] {
  if (content.length === 0) return [];
  return content.split('\n');
}

function applyInsert(lines: string[], op: InsertLinesOp): string[] {
  const targetLine = Math.max(0, Math.min(op.lineNumber, lines.length));
  const result = [...lines];
  result.splice(targetLine, 0, ...op.lines);
  return result;
}

function applyDelete(lines: string[], op: DeleteLinesOp): string[] {
  const start = Math.max(0, op.startLine);
  const end = op.endLine !== undefined 
    ? Math.min(op.endLine, lines.length)
    : lines.length;
  
  if (start >= end) return lines;
  
  const result = [...lines];
  result.splice(start, end - start);
  return result;
}

function applyUpdate(lines: string[], op: UpdateLinesOp): string[] {
  if (op.lineNumber < 0 || op.lineNumber >= lines.length) {
    throw new Error(`Invalid line number: ${op.lineNumber}. File has ${lines.length} lines.`);
  }
  
  const result = [...lines];
  result[op.lineNumber] = op.newLine;
  return result;
}

function applyReplace(lines: string[], op: ReplaceLinesOp): string[] {
  const start = Math.max(0, op.startLine);
  const end = Math.min(op.endLine, lines.length);
  
  if (start >= end) {
    throw new Error(`Invalid range: ${start}-${end}. File has ${lines.length} lines.`);
  }
  
  const result = [...lines];
  result.splice(start, end - start, ...op.newLines);
  return result;
}

async function readFileContent(path: string, encoding: string): Promise<string> {
  throw new Error('TODO: Integrate with read_phone_file API.');
}

async function writeFileContent(path: string, content: string, encoding: string): Promise<void> {
  throw new Error('TODO: Integrate with ftp_file_write or create_github_commit API.');
}

export default editFileByLine;
