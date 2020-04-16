package com.github.dryabkov.mvndeps.exceptions;

public class ResultWritingException extends RuntimeException {

    public ResultWritingException(String message) {
        super(message);
    }

    public ResultWritingException(Throwable cause) {
        super(cause);
    }
}
