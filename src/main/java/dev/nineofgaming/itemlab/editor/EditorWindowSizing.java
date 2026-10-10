package dev.nineofgaming.itemlab.editor;

/**
 * Responsive first-use sizing policy for the top-level Item Lab window.
 *
 * <p>The target height follows the work area while the width is capped to the proportions of the
 * original 780 x 700 editor. This keeps the editor from inheriting a wide or ultrawide viewport's
 * aspect ratio. The fixed margin becomes the limiting rule only on constrained viewports, where
 * the design minimum is reduced rather than forcing part of the window off-screen.</p>
 */
final class EditorWindowSizing {
    static final float TARGET_WIDTH_FRACTION = 0.84f;
    static final float TARGET_HEIGHT_FRACTION = 0.84f;
    static final float PREFERRED_ASPECT_RATIO = 780.0f / 700.0f;
    static final float WORK_AREA_MARGIN = 16.0f;
    static final float DESIGN_MIN_WIDTH = 680.0f;
    static final float DESIGN_MIN_HEIGHT = 540.0f;

    private EditorWindowSizing() {
    }

    static boolean hasUsableWorkArea(float workWidth, float workHeight) {
        return Float.isFinite(workWidth) && workWidth > 0.0f
            && Float.isFinite(workHeight) && workHeight > 0.0f;
    }

    static Layout forWorkArea(float workWidth, float workHeight) {
        if (!hasUsableWorkArea(workWidth, workHeight)) {
            throw new IllegalArgumentException("Viewport work area must have finite positive dimensions");
        }

        float availableWidth = Math.max(1.0f, workWidth - WORK_AREA_MARGIN * 2.0f);
        float availableHeight = Math.max(1.0f, workHeight - WORK_AREA_MARGIN * 2.0f);
        float minimumWidth = Math.min(DESIGN_MIN_WIDTH, availableWidth);
        float minimumHeight = Math.min(DESIGN_MIN_HEIGHT, availableHeight);

        float height = Math.clamp(
            workHeight * TARGET_HEIGHT_FRACTION,
            minimumHeight,
            availableHeight
        );
        float width = Math.clamp(
            Math.min(workWidth * TARGET_WIDTH_FRACTION, height * PREFERRED_ASPECT_RATIO),
            minimumWidth,
            availableWidth
        );

        return new Layout(
            width,
            height,
            minimumWidth,
            minimumHeight
        );
    }

    record Layout(float width, float height, float minimumWidth, float minimumHeight) {
    }
}
