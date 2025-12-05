package ai.timefold.wasm.service;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkus.logging.Log;

@Provider
public class RequestExceptionHandler implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        exception.printStackTrace();
        // Include full cause chain for better debugging
        StringBuilder msg = new StringBuilder();
        Throwable t = exception;
        while (t != null) {
            if (msg.length() > 0) msg.append(" | ");
            msg.append(t.getClass().getName());
            if (t.getMessage() != null) {
                msg.append(": ").append(t.getMessage());
            }
            // Also include first stack frame for context
            if (t.getStackTrace().length > 0) {
                var frame = t.getStackTrace()[0];
                msg.append(" at ").append(frame.getClassName()).append(".").append(frame.getMethodName())
                   .append("(").append(frame.getFileName()).append(":").append(frame.getLineNumber()).append(")");
            }
            t = t.getCause();
        }
        return Response.status(Response.Status.BAD_REQUEST).entity(msg.toString()).build();
    }
}
