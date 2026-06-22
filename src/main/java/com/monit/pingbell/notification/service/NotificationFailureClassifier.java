package com.monit.pingbell.notification.service;

import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class NotificationFailureClassifier {

    public NotificationFailureResult classify(Exception exception) {
        return new NotificationFailureResult(isRetryable(exception), toErrorMessage(exception));
    }

    private boolean isRetryable(Exception exception) {
        if (exception instanceof ResourceAccessException) {
            return true;
        }

        if (exception instanceof RestClientResponseException responseException) {
            HttpStatus status = HttpStatus.resolve(responseException.getStatusCode().value());
            return status == HttpStatus.TOO_MANY_REQUESTS
                    || responseException.getStatusCode().is5xxServerError();
        }

        if (exception instanceof MailParseException || exception instanceof MailAuthenticationException) {
            return false;
        }

        if (exception instanceof MailException) {
            return true;
        }

        if (exception instanceof IllegalStateException illegalStateException
                && isNotificationTargetDecryptionFailure(illegalStateException)) {
            return false;
        }

        if (exception instanceof IllegalArgumentException) {
            return false;
        }

        return false;
    }

    private boolean isNotificationTargetDecryptionFailure(IllegalStateException exception) {
        String message = exception.getMessage();
        return message != null && message.startsWith("Failed to decrypt notification target.");
    }

    private String toErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
