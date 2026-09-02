package com.empik.couponservice.dto;

/**
 * Response body for a failed request: a stable, machine-readable error code alongside a
 * human-readable message, so a client can tell distinct failures apart even when they share the
 * same HTTP status.
 *
 * @param error stable machine-readable error code (e.g. {@code INVALID_REQUEST})
 * @param message human-readable description of the failure
 */
public record ErrorResponse(String error, String message) {
}
