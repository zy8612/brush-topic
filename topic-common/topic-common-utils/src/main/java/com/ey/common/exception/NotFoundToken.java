package com.ey.common.exception;

public class NotFoundToken extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * 构造函数，接受一个异常消息
     * @param msg 异常消息
     */
    public NotFoundToken(String msg) {
        super(msg);
    }

    /**
     * 获取异常消息
     * @return 异常消息
     */
    public String getMsg() {
        return getMessage();
    }

}
