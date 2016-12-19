package edu.umich.oasis.policy;

import android.content.ComponentName;
import android.content.res.Resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A class representing a request to sink classified data to an output.
 */
public class SinkRequest {
    private final String sinkName;
    private boolean rejected = false;
    private LinkedHashMap<Source, String> errorMessages = null;

    /**
     * Create a new sink request.
     * @param sinkName The name of the sink requested.
     */
    public SinkRequest(String sinkName) {
        this.sinkName = sinkName;
    }

    /**
     * Marks this request as rejected.
     *
     * Further rules will still be processed, to catch any log or message actions.
     */
    public final void reject() {
        rejected = true;
    }

    /**
     * Gets whether this request has been rejected by any source.
     * @return True if this request was rejected by at least one source; false if all sources
     *         accept the request.
     */
    public final boolean isRejected() {
        return rejected;
    }

    /**
     * Marks this request as rejected with a literal error message. Error messages will later be
     * returned to the caller as an exception. For a silent failure, use {@link #reject()} instead.
     *
     * The error message can later be retrieved using {@link #getErrorMessages}.
     *
     * @param sendingSource The qualified name of the source adding the error.
     * @param message The error message, which may be formatted according to the sink's rules.
     */
    public void addErrorMessage(Source sendingSource, String message) {
        putFormattedError(sendingSource, message);
    }

    /**
     * Marks this request as rejected with an error message from resources. Error messages will later be
     * returned to the caller as an exception. For a silent failure, use {@link #reject()} instead.
     *
     * The error message can later be retrieved using {@link #getErrorMessages}.
     *
     * @see #addErrorMessage(Source, Resources, int)
     *
     * @param sendingSource The qualified name of the source adding the error.
     * @param res The {@link Resources} in which to look up the message.
     * @param id The resource ID of the message. By default, a simple string resource; individual
     *           sinks may allow more complex resources such as formats and/or quantity strings.
     */
    public void addErrorMessage(Source sendingSource, Resources res, int id) {
        addErrorMessage(sendingSource, res.getString(id));
    }

    /**
     * Overriders of {@link #addErrorMessage}: puts the formatted message into the message map.
     * @param sendingSource The qualified name of the source adding the error.
     * @param message The formatted error message.
     */
    protected void putFormattedError(Source sendingSource, String message) {
        reject();
        if (errorMessages == null) {
            errorMessages = new LinkedHashMap<>(); // Keep messages in order.
        }
        errorMessages.put(sendingSource, message);
    }

    /**
     * Get the error messages that are associated with this request.
     *
     * If this map contains items, they should be bundled into a SecurityException and thrown to the
     * caller. If the map is empty, the sink should be aborted, but without returning an error.
     * @return A read-only {@link Map} associating sources with their error messages.
     */
    public final Map<Source, String> getErrorMessages() {
        if (errorMessages != null) {
            return Collections.unmodifiableMap(errorMessages);
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Get the name of this sink.
     * @return The name of the sink.
     */
    public final String getSinkName() {
        return sinkName;
    }

    @Override
    public String toString() {
        return String.format("SinkRequest<%s>(%s)", sinkName, parametersToString());
    }

    protected String parametersToString() {
        return "";
    }
}
