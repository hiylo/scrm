/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Groups.tsx
 * Date : 2026/08/02
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import Wework from './Wework';

/**
 * 群管理页面
 * <p>
 * 整合企业微信客户群管理功能, 通过企微开放 API 查询客户群列表。
 * 后续可扩展支持微信个人号群聊管理。
 * </p>
 *
 * @author hiylo
 */
function Groups() {
  return (
    <div style={{ padding: '0 0 24px' }}>
      <Wework singleView defaultTab="group-chats" />
    </div>
  );
}

export default Groups;
