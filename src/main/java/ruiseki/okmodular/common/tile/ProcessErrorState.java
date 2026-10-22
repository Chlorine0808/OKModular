package ruiseki.okmodular.common.tile;

import ruiseki.okmodular.api.recipe.error.ErrorReason;

/**
 * Why the machine is not making progress, and the text that fills the reason's {@code %s}.
 */
public final class ProcessErrorState {

    private ErrorReason reason = ErrorReason.NONE;
    private String detail = null;

    public ErrorReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public void set(ErrorReason reason) {
        set(reason, null);
    }

    public void set(ErrorReason reason, String detail) {
        this.reason = reason;
        this.detail = detail;
    }

    /**
     * Replaces the reason and leaves the detail as it was.
     */
    public void setReasonKeepingDetail(ErrorReason reason) {
        this.reason = reason;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
