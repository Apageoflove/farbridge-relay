# Android 权限

运行时会检查这些能力：`RECEIVE_SMS`、`READ_SMS`、`READ_CALL_LOG`、`READ_PHONE_STATE`、网络访问和开机自启恢复。Provider 查询失败或结果不完整时必须返回 `QueryFailure`，并且不能产生 DELETE 事件。默认短信角色是可选项、随时可退；只有真机测试发现验证码延迟、非默认路径确实不够用时才需要打开。

系统或安全更新之后要重新核对授权状态，并通过心跳上报。本项目不做 Play 商店短信/通话记录政策的绕过，私有侧载就是预期的安装方式。
