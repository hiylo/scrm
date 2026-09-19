/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : usePlatforms.ts
 * Date : 2026/07/29
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useState, useEffect } from 'react';

/** 平台信息 */
export interface PlatformOption {
  platformType: string;
  displayName: string;
  available: boolean;
}

/** 硬编码平台列表 (仅企业微信) */
const FALLBACK_PLATFORMS: PlatformOption[] = [
  { platformType: 'WEWORK', displayName: '企业微信', available: true },
];

/**
 * 获取可用平台列表的 Hook
 * 仅企业微信域, 直接返回静态列表
 */
export function usePlatforms(): { platforms: PlatformOption[]; loading: boolean } {
  const [platforms] = useState<PlatformOption[]>(FALLBACK_PLATFORMS);
  const [loading] = useState(false);

  useEffect(() => {
    // 仅企业微信, 无需加载
  }, []);

  return { platforms, loading };
}
