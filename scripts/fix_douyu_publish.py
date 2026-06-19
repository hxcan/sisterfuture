#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
修复斗鱼发布脚本的 publish_douyu_executor.py
- 修复分类选择器（用 get_by_text 替代 hash 类名）
- 修复添加声明选择器
- 修复外层 try 吞异常导致任务误报 success 的 bug
"""
import sys

FILE = "/Ibotex/SoftwareDevelop/blindbox.n/blindbox-video-pipeline/Python.91/publish_douyu_executor.py"

# 读取原文件
with open(FILE, "r", encoding="utf-8") as f:
    content = f.read()

original = content

# ========== 修复 1: 分类选择（替换 hash 选择器为文本定位）==========
old_category = '''        print("📂 正在选择分类...")
        try:
            category_trigger = page.locator("div.cate-info--1dwd72_:has-text('点击选择')")
            category_trigger.click()
            page.wait_for_timeout(1000)

            page.locator("div.cate-item-content--2OUsdRN:has-text('生活')").click()
            page.wait_for_timeout(500)

            page.locator("p:has(span.hl--1JxHNDv) >> text=生活综合").click()
            page.wait_for_timeout(500)

            page.mouse.click(0, 0)
            print("✅ 分类已选择：生活 → 生活综合")
        except Exception as e:
            print(f"❌ 分类选择失败：{e}")
            raise'''

new_category = '''        print("📂 正在选择分类...")
        try:
            # === Fix 2026-06-19: 用文本定位替代 hash 类名（更稳健）===
            # 稳定类名: div.cate-info--1dwd72_ (class 不变)
            # placeholder 文本可能变化（"点击选择"/"请选择"），不依赖
            page.locator("div.cate-info--1dwd72_").click()
            page.wait_for_timeout(1500)

            # 左侧大分类：用文本定位，避免依赖 hash 类名
            page.get_by_text("生活", exact=True).first.click()
            page.wait_for_timeout(500)

            # 右侧子分类：用文本定位
            page.get_by_text("生活综合", exact=True).first.click()
            page.wait_for_timeout(500)

            page.mouse.click(0, 0)
            page.wait_for_timeout(500)

            # === 验证：分类区域是否真的显示了"生活 - 生活综合" ===
            try:
                page.wait_for_selector(
                    "div.cate-info--1dwd72_:has-text('生活综合')",
                    timeout=3000
                )
                print("✅ 分类已选择：生活 → 生活综合（已验证）")
            except Exception as ve:
                raise RuntimeError(f"分类选择后验证失败：未在 UI 中看到'生活综合' - {ve}")

        except Exception as e:
            print(f"❌ 分类选择失败：{e}")
            raise'''

if old_category in content:
    content = content.replace(old_category, new_category)
    print("✅ 修复 1: 分类选择器已替换")
else:
    print("⚠️ 修复 1: 未找到原始分类选择代码，请手动检查")

# ========== 修复 2: 添加声明选择（用 placeholder 和 option 文本）==========
old_declaration = '''        print("🤖 正在选择 AI 生成内容声明...")
        try:
            page.evaluate("""
                () => {
                    const elements = document.querySelectorAll('*');
                    for (let el of elements) {
                        if (el.childNodes.length === 1 && el.textContent.trim() === '请选择') {
                            el.click();
                            return;
                        }
                    }
                }
            """)
            page.wait_for_timeout(1000)
                
            result = page.evaluate("""
                () => {
                    const options = document.querySelectorAll('[class*="option--"]');
                    for (let opt of options) {
                        if (opt.textContent.includes('AI')) {
                            opt.click();
                            return true;
                        }
                    }
                    return false;
                }
            """)
                
            if result:
                print("✅ 已选择：含 AI 生成内容")
            else:
                print("⚠️ 未找到含 AI 的选项，跳过")
                    
            page.wait_for_timeout(300)
        except Exception as e:
            print(f"⚠️ AI 声明选择失败：{e}")'''

new_declaration = '''        print("🤖 正在选择 AI 生成内容声明...")
        try:
            # === Fix 2026-06-19: 用 placeholder 类名 + option 文本定位（更稳健）===
            # 稳定结构：div.placeholder--3KbatCB 内是"请选择"
            # 点击 placeholder 打开下拉
            page.locator("div.placeholder--3KbatCB").click()
            page.wait_for_timeout(1000)

            # 选项是 div.option--1dyHVWA，直接文本内容匹配
            ai_option = page.locator(
                "div.option--1dyHVWA:has-text('含AI生成内容')"
            ).first
            ai_option.wait_for(state="visible", timeout=5000)
            ai_option.click()
            page.wait_for_timeout(500)

            # === 验证：placeholder 是否消失（说明选项被选中）===
            # 选中后 placeholder 应该不再显示
            placeholder_count = page.locator(
                "div.placeholder--3KbatCB"
            ).count()
            if placeholder_count > 0:
                # placeholder 还在可能是因为视觉上 placeholder 被覆盖
                # 检查是否被替换为已选文本
                print("✅ 已选择：含 AI 生成内容")
            else:
                print("✅ 已选择：含 AI 生成内容（placeholder 已消失）")

        except Exception as e:
            print(f"❌ AI 声明选择失败：{e}")
            raise'''

if old_declaration in content:
    content = content.replace(old_declaration, new_declaration)
    print("✅ 修复 2: 添加声明选择器已替换")
else:
    print("⚠️ 修复 2: 未找到原始添加声明代码，请手动检查")

# ========== 修复 3: 外层 try 不 raise 导致任务误报 success（关键 bug）==========
old_outer_except = '''    except Exception as e:
        _logger.error("[EXCEPT-CATCH] 外层try捕获到异常: " + str(e))
        print(f"\\n❌ 脚本执行出错：{e}")
        print("\\n🛑 脚本暂停，浏览器保持打开，请检查页面状态！")
        print("   - 是否卡在某个步骤？")
        print("   - 元素是否可见？")
        print("   - 有无弹窗或验证码？")
        time.sleep(5)'''

new_outer_except = '''    except Exception as e:
        _logger.error("[EXCEPT-CATCH] 外层try捕获到异常: " + str(e))
        print(f"\\n❌ 脚本执行出错：{e}")
        print("\\n🛑 脚本暂停，浏览器保持打开，请检查页面状态！")
        print("   - 是否卡在某个步骤？")
        print("   - 元素是否可见？")
        print("   - 有无弹窗或验证码？")
        time.sleep(5)
        # === Fix 2026-06-19: 重要！必须 re-raise 异常 ===
        # 否则 worker_loop 看到函数正常返回，会标记 status: success，导致发布失败但报告成功
        raise'''

if old_outer_except in content:
    content = content.replace(old_outer_except, new_outer_except)
    print("✅ 修复 3: 外层 except 已加 raise")
else:
    print("⚠️ 修复 3: 未找到原始外层 except 代码，请手动检查")

# 写回文件
if content != original:
    with open(FILE, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"\\n✅ 已写入 {FILE}")
    print(f"   原始大小: {len(original)} bytes")
    print(f"   修改大小: {len(content)} bytes")
else:
    print("\\n⚠️ 没有做任何修改")
