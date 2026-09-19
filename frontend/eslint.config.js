// Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
// Project : scrm
// File : eslint.config.js
// Date : 2026-09-19 00:00:00
// Author : Hsi Chu
// Contact : hiylo@live.com

import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'coverage', 'node_modules'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // 以下两条按 warn 而非 error: 存量各 11 处 any 与 5 处未使用变量, 需逐处判断是补类型还是删死代码,
      // 不适合一次性改; 保持 warn 让 npm run lint 退出码可用作增量门禁, 数字仍在输出里可见。
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    },
  },
);
