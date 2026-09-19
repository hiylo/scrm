/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : imeHelpers.ts
 * Date : 2026/07/29
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import type { KeyboardEvent } from 'react';

/**
 * 判断键盘事件是否发生在输入法组合（IME composition）过程中。
 *
 * 背景：
 *   中文/日文等 IME 用户在候选词选择阶段按下 Enter 时，浏览器的 keydown
 *   事件会带 isComposing=true（部分老浏览器以 keyCode=229 标记）。
 *   此时 Enter 的语义是"确认候选词"，而不是"提交表单"。
 *
 * 不加判断直接提交会导致：
 *   1) inputText 状态尚未同步——onChange 在 compositionend 之后才触发，
 *      而 onPressEnter 在同一帧的 keydown 中先于 state 更新执行；
 *   2) handleSendInput 因 inputText.trim() 为空而提前 return，
 *      表现为"输入中文按 Enter 没反应"。
 *
 * 因此所有文本输入框的 onPressEnter / onKeyDown 都应先调用此函数过滤组合态事件，
 * 让浏览器把这次 Enter 交给 IME 处理候选词确认。
 *
 * @param e React.KeyboardEvent
 * @returns true 表示当前处于 IME 组合态，调用方应跳过提交逻辑
 */
export function isImeComposing(e: KeyboardEvent): boolean {
  return e.nativeEvent.isComposing === true || e.keyCode === 229;
}
