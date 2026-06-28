package com.larbcorp.neuroinfogrinder2.telegram.tdlib;

public class TdlibException extends RuntimeException {
    public TdlibException(String message) {
        super(message);
    }

    public TdlibException(String message, Throwable cause) {
        super(message, cause);
    }
}
