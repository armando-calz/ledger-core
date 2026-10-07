package io.github.armandocalz.ledger.web;

import io.github.armandocalz.ledger.application.AccountNotFoundException;
import io.github.armandocalz.ledger.domain.InsufficientFundsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps failures to RFC 9457 problem details. Malformed requests are 400 (handled by the
 * superclass); requests that are well-formed but break a ledger rule are 422.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail notFound(AccountNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Account not found", e);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail insufficientFunds(InsufficientFundsException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Insufficient funds", e);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, ArithmeticException.class})
    ProblemDetail ruleViolation(RuntimeException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Ledger rule violated", e);
    }

    private static ProblemDetail problem(HttpStatus status, String title, Exception e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
