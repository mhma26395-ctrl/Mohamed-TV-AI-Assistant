package com.tvai.assistant;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Strict validation boundary for the future local phone companion transport.
 *
 * This class does not authenticate a phone and does not execute commands.
 * Callers must supply a phone ID obtained from a successfully authenticated,
 * non-revoked pairing record. Never pass a phoneId copied from an untrusted
 * request as verifiedPhoneId.
 */
final class CompanionCommandValidator {
    static final int PROTOCOL_VERSION = 1;
    static final int MAX_REQUEST_BYTES = 8 * 1024;
    private static final long MAX_CLOCK_SKEW_MS = 30_000L;
    private static final int MAX_TRACKED_REQUESTS = 512;

    private static final Set<String> ENVELOPE_KEYS = setOf(
            "version", "phoneId", "requestId", "counter",
            "issuedAt", "command", "args", "signature");

    private static final Set<String> NAVIGATION = setOf(
            "up", "down", "left", "right", "ok", "back", "home");
    private static final Set<String> MEDIA = setOf(
            "volume_up", "volume_down", "mute");

    private final Map<String, Long> lastCounters = new HashMap<>();
    private final Map<String, Set<String>> seenRequestIds = new HashMap<>();

    static final class Result {
        final boolean accepted;
        final String error;
        final String command;
        final JSONObject args;
        final String requestId;

        private Result(boolean accepted, String error, String command,
                       JSONObject args, String requestId) {
            this.accepted = accepted;
            this.error = error;
            this.command = command;
            this.args = args;
            this.requestId = requestId;
        }

        static Result reject(String error) {
            return new Result(false, error, null, null, null);
        }

        static Result accept(String command, JSONObject args, String requestId) {
            return new Result(true, "accepted_for_validation_only", command, args, requestId);
        }
    }

    synchronized Result validate(byte[] rawBody, JSONObject request,
                                  String verifiedPhoneId, long nowMs) {
        if (rawBody == null || rawBody.length == 0 || rawBody.length > MAX_REQUEST_BYTES)
            return Result.reject("invalid_body_size");
        if (verifiedPhoneId == null || verifiedPhoneId.trim().isEmpty())
            return Result.reject("unauthenticated_phone");
        if (request == null) return Result.reject("invalid_json");

        try {
            if (!hasExactKeys(request, ENVELOPE_KEYS)) return Result.reject("invalid_fields");
            if (request.getInt("version") != PROTOCOL_VERSION)
                return Result.reject("unsupported_version");

            String phoneId = request.getString("phoneId");
            if (!verifiedPhoneId.equals(phoneId)) return Result.reject("identity_mismatch");

            String requestId = request.getString("requestId");
            try { UUID.fromString(requestId); }
            catch (Exception invalid) { return Result.reject("invalid_request_id"); }

            long counter = request.getLong("counter");
            long issuedAt = request.getLong("issuedAt");
            if (counter < 1) return Result.reject("invalid_counter");
            // Range-check before subtraction to avoid overflow on hostile Long values.
            if (issuedAt <= 0L || nowMs <= 0L
                    || issuedAt < nowMs - MAX_CLOCK_SKEW_MS
                    || issuedAt > nowMs + MAX_CLOCK_SKEW_MS)
                return Result.reject("request_expired");

            String command = request.getString("command");
            JSONObject args = request.getJSONObject("args");
            String signature = request.getString("signature");
            if (signature.length() < 80 || signature.length() > 160
                    || !signature.matches("[A-Za-z0-9_-]+"))
                return Result.reject("invalid_signature_format");

            String argsError = validateCommand(command, args);
            if (argsError != null) return Result.reject(argsError);

            long previous = lastCounters.containsKey(phoneId) ? lastCounters.get(phoneId) : 0L;
            if (counter <= previous) return Result.reject("replayed_counter");

            Set<String> ids = seenRequestIds.get(phoneId);
            if (ids != null && ids.contains(requestId)) return Result.reject("replayed_request");

            // Authentication/signature verification is deliberately NOT performed here.
            // A future transport must verify the signature over a canonical envelope
            // before calling this method, and only then commit the counter/request ID.
            return Result.accept(command, args, requestId);
        } catch (JSONException | ClassCastException e) {
            return Result.reject("malformed_request");
        }
    }

    synchronized boolean commitAuthenticatedRequest(String verifiedPhoneId,
                                                     long counter, String requestId) {
        if (verifiedPhoneId == null || requestId == null || counter < 1) return false;
        long previous = lastCounters.containsKey(verifiedPhoneId)
                ? lastCounters.get(verifiedPhoneId) : 0L;
        if (counter <= previous) return false;

        Set<String> ids = seenRequestIds.get(verifiedPhoneId);
        if (ids == null) {
            ids = new HashSet<>();
            seenRequestIds.put(verifiedPhoneId, ids);
        }
        if (ids.contains(requestId)) return false;
        // Fail closed rather than clearing replay history and reopening old IDs.
        if (ids.size() >= MAX_TRACKED_REQUESTS) return false;
        ids.add(requestId);
        lastCounters.put(verifiedPhoneId, counter);
        return true;
    }

    private static String validateCommand(String command, JSONObject args) {
        if (command == null || args == null) return "invalid_command";
        if (NAVIGATION.contains(command) || MEDIA.contains(command))
            return args.length() == 0 ? null : "unexpected_command_arguments";

        if ("ask_text".equals(command) || "ask_voice_transcript".equals(command)) {
            if (!hasExactKeys(args, setOf("text"))) return "invalid_assistant_arguments";
            String text = args.optString("text", "");
            if (text.trim().isEmpty() || text.length() > 4000)
                return "invalid_assistant_text";
            return null;
        }
        return "unsupported_command";
    }

    private static boolean hasExactKeys(JSONObject object, Set<String> expected) {
        if (object.length() != expected.size()) return false;
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) if (!expected.contains(keys.next())) return false;
        return true;
    }

    private static Set<String> setOf(String... values) {
        Set<String> result = new HashSet<>();
        for (String value : values) result.add(value);
        return result;
    }
}
