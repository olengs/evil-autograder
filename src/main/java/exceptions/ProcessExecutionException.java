package exceptions;

public class ProcessExecutionException extends RuntimeException { //reports a problem starting java \ reading its output
    private final String code; //error code > null if not supplied
    
    public ProcessExecutionException(String message) { //message only
        super(message);
        this.code = null;
    }

    public ProcessExecutionException(String message, Throwable cause) { //message & original cause
        super(message, cause);
        this.code = null;
    }

    public ProcessExecutionException(String code, String message) { //error code & message
        super(message);
        this.code = code;
    }

    
    public ProcessExecutionException(String code, String message, Throwable cause) { //error code & message & original cause
        super(message, cause);
        this.code = code;
    }

    public String getCode() { //return error code
        return code;
    }
}
