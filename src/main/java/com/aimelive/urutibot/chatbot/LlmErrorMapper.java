package com.aimelive.urutibot.chatbot;

import dev.langchain4j.model.anthropic.internal.client.AnthropicHttpException;

final class LlmErrorMapper {

    static final String DEFAULT_MESSAGE = "An error occurred while generating a response.";

    private static final String UNAVAILABLE = "The assistant is temporarily unavailable. Please try again later.";
    private static final String RATE_LIMITED = "I'm receiving too many requests right now - please try again in a moment.";
    private static final String OVERLOADED = "The assistant is busy at the moment. Please try again shortly.";
    private static final String BAD_REQUEST = "Sorry, I couldn't process that request. Could you rephrase and try again?";

    private LlmErrorMapper() {
    }

    static String toClientMessage(Throwable throwable) {
        AnthropicHttpException anthropic = findCause(throwable, AnthropicHttpException.class);
        if (anthropic != null) {
            return mapAnthropic(anthropic);
        }
        return DEFAULT_MESSAGE;
    }

    private static String mapAnthropic(AnthropicHttpException ex) {
        Integer status = ex.statusCode();
        String body = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();

        // Billing / quota / auth issues - operator must intervene; from the
        // user's perspective this is "service is down right now".
        if (body.contains("credit balance")
                || body.contains("billing")
                || body.contains("quota")
                || body.contains("authentication_error")
                || body.contains("invalid x-api-key")
                || (status != null && (status == 401 || status == 403))) {
            return UNAVAILABLE;
        }

        if (status != null && status == 429)
            return RATE_LIMITED;
        if (body.contains("rate_limit"))
            return RATE_LIMITED;

        if (status != null && status == 529)
            return OVERLOADED;
        if (body.contains("overloaded_error"))
            return OVERLOADED;

        if (status != null && status >= 400 && status < 500)
            return BAD_REQUEST;
        if (status != null && status >= 500)
            return UNAVAILABLE;

        return UNAVAILABLE;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        while (t != null) {
            if (type.isInstance(t))
                return (T) t;
            Throwable next = t.getCause();
            if (next == t)
                return null;
            t = next;
        }
        return null;
    }
}
